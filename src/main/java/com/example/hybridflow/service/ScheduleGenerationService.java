package com.example.hybridflow.service;

import com.example.hybridflow.dto.ScheduleGenerationAcceptedDTO;
import com.example.hybridflow.dto.ScheduleGenerationRequestDTO;
import com.example.hybridflow.entity.*;
import com.example.hybridflow.exception.BusinessValidationException;
import com.example.hybridflow.exception.ResourceNotFoundException;
import com.example.hybridflow.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Synchronous phase of schedule generation.
 *
 * Responsibilities (all done on the HTTP request thread, completes in ~50ms):
 *   1. Validate the incoming request and HR user context
 *   2. Validate team availability (no draft or published conflicts)
 *   3. Create a ScheduleOptimizationRun row with status = PENDING
 *   4. Fire-and-forget the async Gurobi solve via ScheduleGenerationAsyncService
 *   5. Return HTTP 202 Accepted with the runId so the frontend can poll
 *
 * The actual Gurobi solve and all persistence happen in the background threads
 * managed by the "gurobiExecutor" ThreadPoolTaskExecutor.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleGenerationService {

    private final PlanningPolicyRepository planningPolicyRepository;
    private final OfficeRepository officeRepository;
    private final TeamRepository teamRepository;
    private final UserRepository userRepository;
    private final ScheduleRepository scheduleRepository;
    private final ScheduleOptimizationRunRepository runRepository;
    private final ScheduleGenerationAsyncService asyncService;

    /**
     * Validates the request and immediately returns a run ID.
     * The actual Gurobi solve runs asynchronously on a background thread.
     *
     * @return HTTP 202 payload with runId and status = "PENDING"
     */
    @Transactional
    public ScheduleGenerationAcceptedDTO generateSchedule(
            ScheduleGenerationRequestDTO request,
            User currentUser) {

        // ── Validate request and user ─────────────────────────────────────────
        validateGenerationRequest(request, currentUser);

        Long companyId = currentUser.getCompany().getId();

        Office office = officeRepository.findById(request.getOfficeId())
                .orElseThrow(() -> new ResourceNotFoundException("Office not found."));

        if (office.getCompany() == null || !office.getCompany().getId().equals(companyId)) {
            throw new AccessDeniedException(
                    "You can only generate schedules for offices in your company.");
        }

        PlanningPolicy policy = planningPolicyRepository
                .findByIdAndCompanyId(request.getPlanningPolicyId(), companyId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Planning policy not found for your company."));

        List<Long> selectedTeamIds = normalizeTeamIds(request.getTeamIds());

        List<Team> teams = teamRepository.findByIdInAndCompanyId(selectedTeamIds, companyId);
        if (teams.size() != selectedTeamIds.size()) {
            throw new AccessDeniedException(
                    "One or more selected teams do not exist or do not belong to your company.");
        }

        validateTeamsAreAvailableForGeneration(teams, request.getStartDate(), request.getEndDate());

        List<User> users = userRepository.findSchedulableUsersByTeamIds(selectedTeamIds);
        validateEveryTeamHasSchedulableUsers(teams, users);

        // ── Create PENDING run row ────────────────────────────────────────────
        ScheduleOptimizationRun run = new ScheduleOptimizationRun();
        run.setJobStatus(OptimizationJobStatus.PENDING);
        run.setCompany(currentUser.getCompany());
        run.setPlanningPolicy(policy);
        run.setStartDate(request.getStartDate());
        run.setEndDate(request.getEndDate());
        run.setNumUsers(users.size());
        run.setNumTeams(teams.size());

        ScheduleOptimizationRun savedRun = runRepository.save(run);

        log.info("Optimization run {} created (PENDING) — {} teams, {} users, {} to {}",
                savedRun.getId(), teams.size(), users.size(),
                request.getStartDate(), request.getEndDate());

        // ── Fire and forget ───────────────────────────────────────────────────
        // Pass primitive IDs and invoke only AFTER the current transaction commits.
        // This ensures the background thread can load the PENDING run row from the database.
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        asyncService.runAsync(
                                savedRun.getId(),
                                request,
                                office.getId(),
                                selectedTeamIds,
                                policy.getId()
                        );
                    }
                }
        );

        // ── Return 202 immediately ────────────────────────────────────────────
        return ScheduleGenerationAcceptedDTO.builder()
                .runId(savedRun.getId())
                .status("PENDING")
                .message("Schedule generation started. Poll GET /api/schedules/optimization-runs/"
                        + savedRun.getId() + " to check progress.")
                .planningPolicyId(policy.getId())
                .planningPolicyName(policy.getName())
                .build();
    }

    // ── Validation helpers (unchanged from original) ──────────────────────────

    private void validateGenerationRequest(ScheduleGenerationRequestDTO request, User currentUser) {
        if (currentUser == null || currentUser.getId() == null) {
            throw new AccessDeniedException("Unauthenticated.");
        }
        if (currentUser.getRole() != Role.HR) {
            throw new AccessDeniedException("Only HR can generate schedules.");
        }
        if (currentUser.getCompany() == null) {
            throw new AccessDeniedException("HR user is not assigned to a company.");
        }
        if (request.getOfficeId() == null) {
            throw new BusinessValidationException("officeId is required.");
        }
        if (request.getPlanningPolicyId() == null) {
            throw new BusinessValidationException("planningPolicyId is required.");
        }
        if (request.getTeamIds() == null || request.getTeamIds().isEmpty()) {
            throw new BusinessValidationException("At least one team must be selected.");
        }
        if (request.getStartDate() == null || request.getEndDate() == null) {
            throw new BusinessValidationException("startDate and endDate are required.");
        }
        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new BusinessValidationException("endDate must be on or after startDate.");
        }
    }

    private List<Long> normalizeTeamIds(List<Long> teamIds) {
        List<Long> normalized = new ArrayList<>(
                new LinkedHashSet<>(teamIds.stream().filter(id -> id != null).toList()));
        if (normalized.isEmpty()) {
            throw new BusinessValidationException("At least one valid team must be selected.");
        }
        return normalized;
    }

    private void validateTeamsAreAvailableForGeneration(
            List<Team> teams, LocalDate startDate, LocalDate endDate) {
        List<String> conflicts = new ArrayList<>();

        for (Team team : teams) {
            List<Schedule> publishedConflicts =
                    scheduleRepository.findPublishedForTeamInRange(team.getId(), startDate, endDate);
            if (!publishedConflicts.isEmpty()) {
                conflicts.add("Team '" + team.getName()
                        + "' already has a published schedule in this date range.");
            }

            List<Schedule> draftConflicts =
                    scheduleRepository.findUnpublishedForTeamInRange(team.getId(), startDate, endDate);
            if (!draftConflicts.isEmpty()) {
                conflicts.add("Team '" + team.getName()
                        + "' already has an unpublished generated schedule in this date range. "
                        + "Publish or discard it first.");
            }
        }

        if (!conflicts.isEmpty()) {
            throw new BusinessValidationException(String.join(" ", conflicts));
        }
    }

    private void validateEveryTeamHasSchedulableUsers(List<Team> teams, List<User> users) {
        Map<Long, Long> userCountByTeamId = users.stream()
                .filter(u -> u.getTeam() != null)
                .collect(Collectors.groupingBy(
                        u -> u.getTeam().getId(),
                        Collectors.counting()));

        List<String> invalidTeams = new ArrayList<>();
        for (Team team : teams) {
            if (userCountByTeamId.getOrDefault(team.getId(), 0L) == 0) {
                invalidTeams.add(team.getName());
            }
        }
        if (!invalidTeams.isEmpty()) {
            throw new BusinessValidationException(
                    "The following teams have no active schedulable employees or managers: "
                            + String.join(", ", invalidTeams));
        }
    }
}