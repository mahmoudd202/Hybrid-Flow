package com.example.hybridflow.service;

import com.example.hybridflow.dto.ConflictingTeamDTO;
import com.example.hybridflow.dto.DeleteUnpublishedSchedulesResponseDTO;
import com.example.hybridflow.dto.IndividualFairnessDTO;
import com.example.hybridflow.dto.ScheduleConflictCheckRequestDTO;
import com.example.hybridflow.dto.ScheduleConflictCheckResponseDTO;
import com.example.hybridflow.dto.ScheduleDiscardRequestDTO;
import com.example.hybridflow.dto.ScheduleDiscardResponseDTO;
import com.example.hybridflow.dto.SchedulePublishRequestDTO;
import com.example.hybridflow.dto.SchedulePublishResponseDTO;
import com.example.hybridflow.dto.TeamFairnessDTO;
import com.example.hybridflow.dto.UnpublishedScheduleDTO;
import com.example.hybridflow.dto.UnpublishedSchedulesResponseDTO;
import com.example.hybridflow.entity.Role;
import com.example.hybridflow.entity.Schedule;
import com.example.hybridflow.entity.ScheduleEntry;
import com.example.hybridflow.entity.Team;
import com.example.hybridflow.entity.User;
import com.example.hybridflow.entity.WorkMode;
import com.example.hybridflow.exception.BusinessValidationException;
import com.example.hybridflow.exception.ResourceNotFoundException;
import com.example.hybridflow.repository.ScheduleEntryRepository;
import com.example.hybridflow.repository.ScheduleRepository;
import com.example.hybridflow.repository.TeamRepository;
import com.example.hybridflow.repository.UserRepository;
import com.example.hybridflow.service.ScheduleEvaluationService.EvaluationResult;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ScheduleManagementService {

        private final ScheduleRepository scheduleRepository;
        private final ScheduleEntryRepository scheduleEntryRepository;
        private final ScheduleEvaluationService scheduleEvaluationService;
        private final UserRepository userRepository;
        private final TeamRepository teamRepository;

        // -------------------------------------------------------------------------
        // NEW: GET /api/schedules/unpublished
        // -------------------------------------------------------------------------

        /**
         * Returns all unpublished (draft) schedules for the HR user's company,
         * together with their fairness scores.
         *
         * This is step 1 in the generation pre-flight sequence:
         * 1. GET /api/schedules/unpublished ← HERE (review + see scores)
         * 2. DELETE /api/schedules/unpublished ← clean slate if needed
         * 3. GET /api/schedules/available-teams
         * 4. POST /api/schedules/generate
         */
        @Transactional(readOnly = true)
        public UnpublishedSchedulesResponseDTO getUnpublishedSchedules(User hrUser) {
                validateHrContext(hrUser);

                Long companyId = hrUser.getCompany().getId();

                List<Schedule> unpublished = scheduleRepository.findAllUnpublishedByCompanyId(companyId);

                if (unpublished.isEmpty()) {
                        return UnpublishedSchedulesResponseDTO.builder()
                                        .count(0)
                                        .overallFairnessScore(0.0)
                                        .schedules(List.of())
                                        .build();
                }

                // ── Gather supporting data for fairness evaluation ────────────────────

                // All distinct team IDs referenced by the unpublished schedules
                List<Long> teamIds = unpublished.stream()
                                .map(s -> s.getTeam().getId())
                                .distinct()
                                .toList();

                List<Team> teams = teamRepository.findAllById(teamIds);

                // All active users that belong to those teams
                List<User> users = userRepository.findByTeamIdIn(teamIds);

                // Schedule IDs needed for the evaluation service
                List<Long> scheduleIds = unpublished.stream()
                                .map(Schedule::getId)
                                .toList();

                // The date range spans from the earliest startDate to the latest endDate
                LocalDate minStart = unpublished.stream()
                                .map(Schedule::getStartDate)
                                .min(LocalDate::compareTo)
                                .orElseThrow();
                LocalDate maxEnd = unpublished.stream()
                                .map(Schedule::getEndDate)
                                .max(LocalDate::compareTo)
                                .orElseThrow();

                // Because PlanningPolicy is not stored on Schedule, ScheduleEvaluationService
                // looks up the most-recently-created policy for the company automatically.
                // See evaluateForUnpublished() for the fallback logic.
                EvaluationResult evaluation = scheduleEvaluationService
                                .evaluateForUnpublished(scheduleIds, users, teams, minStart, maxEnd);

                // ── Build per-schedule DTOs ────────────────────────────────────────────

                // Map individual scores by userId for quick lookup
                Map<Long, IndividualFairnessDTO> individualByUserId = evaluation
                                .getIndividualFairnessScores()
                                .stream()
                                .collect(Collectors.toMap(
                                                IndividualFairnessDTO::getUserId,
                                                dto -> dto));

                // Map team scores by teamId
                Map<Long, TeamFairnessDTO> teamScoreByTeamId = evaluation
                                .getTeamFairnessScores()
                                .stream()
                                .collect(Collectors.toMap(
                                                TeamFairnessDTO::getTeamId,
                                                dto -> dto));

                // Map users by teamId for individual score lookup per schedule
                Map<Long, List<User>> usersByTeamId = users.stream()
                                .filter(u -> u.getTeam() != null)
                                .collect(Collectors.groupingBy(u -> u.getTeam().getId()));

                List<UnpublishedScheduleDTO> scheduleDTOs = unpublished.stream()
                                .map(schedule -> {
                                        Long teamId = schedule.getTeam().getId();

                                        List<IndividualFairnessDTO> individualScoresForTeam = usersByTeamId
                                                        .getOrDefault(teamId, List.of())
                                                        .stream()
                                                        .map(u -> individualByUserId.get(u.getId()))
                                                        .filter(dto -> dto != null)
                                                        .toList();

                                        return UnpublishedScheduleDTO.builder()
                                                        .scheduleId(schedule.getId())
                                                        .teamId(teamId)
                                                        .teamName(schedule.getTeam().getName())
                                                        .officeId(schedule.getOffice().getId())
                                                        .officeName(schedule.getOffice().getName())
                                                        .startDate(schedule.getStartDate())
                                                        .endDate(schedule.getEndDate())
                                                        .createdAt(schedule.getCreatedAt())
                                                        .overallFairnessScore(evaluation.getOverallFairnessScore())
                                                        .teamFairnessScore(teamScoreByTeamId.get(teamId))
                                                        .individualFairnessScores(individualScoresForTeam)
                                                        .build();
                                })
                                .toList();

                return UnpublishedSchedulesResponseDTO.builder()
                                .count(scheduleDTOs.size())
                                .overallFairnessScore(evaluation.getOverallFairnessScore())
                                .schedules(scheduleDTOs)
                                .build();
        }

        // -------------------------------------------------------------------------
        // NEW: DELETE /api/schedules/unpublished
        // -------------------------------------------------------------------------

        /**
         * Permanently deletes ALL unpublished (draft) schedules and their entries
         * for the HR user's company.
         *
         * This is step 2 in the generation pre-flight sequence, called when the HR
         * user decides to discard all pending drafts before generating fresh ones.
         *
         * Only unpublished schedules are affected; published schedules are untouched.
         */
        @Transactional
        public DeleteUnpublishedSchedulesResponseDTO deleteAllUnpublishedSchedules(User hrUser) {
                validateHrContext(hrUser);

                Long companyId = hrUser.getCompany().getId();

                List<Schedule> unpublished = scheduleRepository.findAllUnpublishedByCompanyId(companyId);

                if (unpublished.isEmpty()) {
                        return DeleteUnpublishedSchedulesResponseDTO.builder()
                                        .status("NO_OP")
                                        .message("No unpublished schedules found. Nothing was deleted.")
                                        .deletedScheduleIds(List.of())
                                        .build();
                }

                List<Long> scheduleIds = unpublished.stream()
                                .map(Schedule::getId)
                                .toList();

                // Delete entries first to respect FK constraints
                scheduleEntryRepository.deleteByScheduleIdIn(scheduleIds);
                scheduleRepository.deleteAll(unpublished);

                return DeleteUnpublishedSchedulesResponseDTO.builder()
                                .status("SUCCESS")
                                .message("All " + scheduleIds.size()
                                                + " unpublished schedule(s) and their entries have been deleted.")
                                .deletedScheduleIds(scheduleIds)
                                .build();
        }

        // -------------------------------------------------------------------------
        // Batch publish endpoint logic:
        // POST /api/schedules/publish
        // -------------------------------------------------------------------------

        @Transactional
        public SchedulePublishResponseDTO publishSchedules(
                        SchedulePublishRequestDTO request,
                        User hrUser) {
                validatePublishRequest(request, hrUser);

                List<Long> scheduleIds = normalizeScheduleIds(request.getScheduleIds());

                List<Schedule> schedules = scheduleRepository.findWithTeamOfficeCompanyByIdIn(scheduleIds);

                if (schedules.size() != scheduleIds.size()) {
                        throw new ResourceNotFoundException("One or more schedules were not found.");
                }

                Long companyId = hrUser.getCompany().getId();

                validateSchedulesBelongToCompany(schedules, companyId);
                validateSchedulesAreUnpublished(schedules);
                validateNoDuplicateTeamSchedulesInRequest(schedules);
                validateNoPublishedOverlap(schedules);
                validateOfficeCapacityForPublishing(schedules, scheduleIds);

                for (Schedule schedule : schedules) {
                        schedule.setPublished(true);

                        // Sync the team's office assignment so that getTeamsByOffice
                        // (which queries teams.office_id) reflects the published office.
                        Team team = schedule.getTeam();
                        if (team != null && schedule.getOffice() != null) {
                                team.setOffice(schedule.getOffice());
                                teamRepository.save(team);
                        }
                }

                scheduleRepository.saveAll(schedules);

                return SchedulePublishResponseDTO.builder()
                                .status("SUCCESS")
                                .message("Schedules published successfully.")
                                .publishedScheduleIds(scheduleIds)
                                .build();
        }

        // -------------------------------------------------------------------------
        // Existing conflict-check endpoint logic:
        // POST /api/schedules/check-conflicts
        // -------------------------------------------------------------------------

        @Transactional(readOnly = true)
        public ScheduleConflictCheckResponseDTO checkConflicts(
                        ScheduleConflictCheckRequestDTO request) {
                if (request.getTeamIds() == null || request.getTeamIds().isEmpty()) {
                        throw new BusinessValidationException("At least one team must be selected.");
                }

                if (request.getStartDate() == null || request.getEndDate() == null) {
                        throw new BusinessValidationException("startDate and endDate are required.");
                }

                if (request.getEndDate().isBefore(request.getStartDate())) {
                        throw new BusinessValidationException("endDate must be on or after startDate.");
                }

                List<Long> availableTeamIds = new ArrayList<>();
                List<ConflictingTeamDTO> conflictingTeams = new ArrayList<>();

                for (Long teamId : request.getTeamIds()) {
                        List<Schedule> conflicts = scheduleRepository.findPublishedForTeamInRange(
                                        teamId,
                                        request.getStartDate(),
                                        request.getEndDate());

                        if (conflicts.isEmpty()) {
                                availableTeamIds.add(teamId);
                        } else {
                                Schedule firstConflict = conflicts.get(0);

                                conflictingTeams.add(
                                                ConflictingTeamDTO.builder()
                                                                .teamId(teamId)
                                                                .teamName(
                                                                                firstConflict.getTeam() != null
                                                                                                ? firstConflict.getTeam()
                                                                                                                .getName()
                                                                                                : null)
                                                                .conflictingScheduleIds(
                                                                                conflicts.stream()
                                                                                                .map(Schedule::getId)
                                                                                                .toList())
                                                                .build());
                        }
                }

                return ScheduleConflictCheckResponseDTO.builder()
                                .availableTeamIds(availableTeamIds)
                                .conflictingTeams(conflictingTeams)
                                .build();
        }

        // -------------------------------------------------------------------------
        // Existing single schedule publish logic:
        // POST /api/schedules/{id}/publish
        // -------------------------------------------------------------------------

        @Transactional
        public void publishSchedule(Long scheduleId) {
                Schedule schedule = scheduleRepository.findById(scheduleId)
                                .orElseThrow(() -> new ResourceNotFoundException("Schedule not found."));

                if (schedule.isPublished()) {
                        throw new BusinessValidationException("Schedule is already published.");
                }

                validateSingleScheduleHasNoPublishedOverlap(schedule);
                validateSingleScheduleOfficeCapacity(schedule);

                schedule.setPublished(true);
                scheduleRepository.save(schedule);

                // Sync the team's office assignment so that getTeamsByOffice
                // (which queries teams.office_id) reflects the published office.
                Team team = schedule.getTeam();
                if (team != null && schedule.getOffice() != null) {
                        team.setOffice(schedule.getOffice());
                        teamRepository.save(team);
                }
        }

        // -------------------------------------------------------------------------
        // Existing delete logic:
        // DELETE /api/schedules/{id}
        // -------------------------------------------------------------------------

        @Transactional
        public void deleteSchedule(Long scheduleId) {
                Schedule schedule = scheduleRepository.findById(scheduleId)
                                .orElseThrow(() -> new ResourceNotFoundException("Schedule not found."));

                if (schedule.isPublished()) {
                        throw new BusinessValidationException(
                                        "Published schedules cannot be deleted with this endpoint.");
                }

                scheduleEntryRepository.deleteByScheduleId(scheduleId);
                scheduleRepository.delete(schedule);
        }

        @Transactional
        public ScheduleDiscardResponseDTO discardSchedules(
                        ScheduleDiscardRequestDTO request,
                        User hrUser) {
                validateDiscardRequest(request, hrUser);

                List<Long> scheduleIds = normalizeScheduleIds(request.getScheduleIds());

                List<Schedule> schedules = scheduleRepository.findWithTeamOfficeCompanyByIdIn(scheduleIds);

                if (schedules.size() != scheduleIds.size()) {
                        throw new ResourceNotFoundException("One or more schedules were not found.");
                }

                Long companyId = hrUser.getCompany().getId();

                validateSchedulesBelongToCompany(schedules, companyId);
                validateSchedulesAreUnpublishedForDiscard(schedules);

                scheduleEntryRepository.deleteByScheduleIdIn(scheduleIds);
                scheduleRepository.deleteAll(schedules);

                return ScheduleDiscardResponseDTO.builder()
                                .status("SUCCESS")
                                .message("Generated schedules discarded successfully.")
                                .discardedScheduleIds(scheduleIds)
                                .build();
        }

        // -------------------------------------------------------------------------
        // Batch publish validation helpers
        // -------------------------------------------------------------------------

        private void validatePublishRequest(
                        SchedulePublishRequestDTO request,
                        User hrUser) {
                if (hrUser == null || hrUser.getId() == null) {
                        throw new AccessDeniedException("Unauthenticated.");
                }

                if (hrUser.getRole() != Role.HR) {
                        throw new AccessDeniedException("Only HR can publish schedules.");
                }

                if (hrUser.getCompany() == null) {
                        throw new AccessDeniedException("HR user is not assigned to a company.");
                }

                if (request.getScheduleIds() == null || request.getScheduleIds().isEmpty()) {
                        throw new BusinessValidationException("At least one schedule must be selected.");
                }
        }

        private List<Long> normalizeScheduleIds(List<Long> scheduleIds) {
                List<Long> normalized = new ArrayList<>(
                                new LinkedHashSet<>(
                                                scheduleIds.stream()
                                                                .filter(id -> id != null)
                                                                .toList()));

                if (normalized.isEmpty()) {
                        throw new BusinessValidationException("At least one valid schedule must be selected.");
                }

                return normalized;
        }

        private void validateSchedulesBelongToCompany(
                        List<Schedule> schedules,
                        Long companyId) {
                for (Schedule schedule : schedules) {
                        if (schedule.getTeam() == null || schedule.getTeam().getCompany() == null) {
                                throw new BusinessValidationException(
                                                "Schedule " + schedule.getId()
                                                                + " is not linked to a valid team/company.");
                        }

                        if (schedule.getOffice() == null || schedule.getOffice().getCompany() == null) {
                                throw new BusinessValidationException(
                                                "Schedule " + schedule.getId()
                                                                + " is not linked to a valid office/company.");
                        }

                        boolean teamBelongsToCompany = schedule.getTeam().getCompany().getId().equals(companyId);
                        boolean officeBelongsToCompany = schedule.getOffice().getCompany().getId().equals(companyId);

                        if (!teamBelongsToCompany || !officeBelongsToCompany) {
                                throw new AccessDeniedException(
                                                "Schedule " + schedule.getId() + " does not belong to your company.");
                        }
                }
        }

        private void validateSchedulesAreUnpublished(List<Schedule> schedules) {
                List<Long> alreadyPublishedIds = schedules.stream()
                                .filter(Schedule::isPublished)
                                .map(Schedule::getId)
                                .toList();

                if (!alreadyPublishedIds.isEmpty()) {
                        throw new BusinessValidationException(
                                        "The following schedules are already published: " + alreadyPublishedIds);
                }
        }

        private void validateNoDuplicateTeamSchedulesInRequest(List<Schedule> schedules) {
                Map<Long, List<Schedule>> schedulesByTeamId = schedules.stream()
                                .collect(Collectors.groupingBy(schedule -> schedule.getTeam().getId()));

                List<String> duplicateTeamNames = new ArrayList<>();

                for (Map.Entry<Long, List<Schedule>> entry : schedulesByTeamId.entrySet()) {
                        List<Schedule> teamSchedules = entry.getValue();

                        if (teamSchedules.size() <= 1) {
                                continue;
                        }

                        if (hasInternalOverlap(teamSchedules)) {
                                duplicateTeamNames.add(teamSchedules.get(0).getTeam().getName());
                        }
                }

                if (!duplicateTeamNames.isEmpty()) {
                        throw new BusinessValidationException(
                                        "Multiple selected schedules overlap for the same team: "
                                                        + String.join(", ", duplicateTeamNames));
                }
        }

        private boolean hasInternalOverlap(List<Schedule> schedules) {
                List<Schedule> sortedSchedules = schedules.stream()
                                .sorted((a, b) -> a.getStartDate().compareTo(b.getStartDate()))
                                .toList();

                for (int i = 0; i < sortedSchedules.size() - 1; i++) {
                        Schedule current = sortedSchedules.get(i);
                        Schedule next = sortedSchedules.get(i + 1);

                        boolean overlaps = !current.getEndDate().isBefore(next.getStartDate());

                        if (overlaps) {
                                return true;
                        }
                }

                return false;
        }

        private void validateNoPublishedOverlap(List<Schedule> schedules) {
                List<String> conflicts = new ArrayList<>();

                for (Schedule schedule : schedules) {
                        List<Schedule> publishedConflicts = scheduleRepository.findPublishedForTeamInRange(
                                        schedule.getTeam().getId(),
                                        schedule.getStartDate(),
                                        schedule.getEndDate());

                        if (!publishedConflicts.isEmpty()) {
                                conflicts.add(
                                                "Team '" + schedule.getTeam().getName()
                                                                + "' already has a published schedule overlapping "
                                                                + schedule.getStartDate()
                                                                + " to "
                                                                + schedule.getEndDate()
                                                                + ".");
                        }
                }

                if (!conflicts.isEmpty()) {
                        throw new BusinessValidationException(String.join(" ", conflicts));
                }
        }

        private void validateOfficeCapacityForPublishing(
                        List<Schedule> schedules,
                        List<Long> scheduleIds) {
                List<ScheduleEntry> candidateEntries = scheduleEntryRepository
                                .findByScheduleIdInWithScheduleAndOffice(scheduleIds);

                if (candidateEntries.isEmpty()) {
                        throw new BusinessValidationException("Selected schedules have no schedule entries.");
                }

                Map<Long, List<Schedule>> schedulesByOfficeId = schedules.stream()
                                .collect(Collectors.groupingBy(schedule -> schedule.getOffice().getId()));

                for (Map.Entry<Long, List<Schedule>> officeEntry : schedulesByOfficeId.entrySet()) {
                        Long officeId = officeEntry.getKey();
                        List<Schedule> officeSchedules = officeEntry.getValue();

                        int maxCapacity = officeSchedules.get(0).getOffice().getMaxCapacity();

                        LocalDate minDate = officeSchedules.stream()
                                        .map(Schedule::getStartDate)
                                        .min(LocalDate::compareTo)
                                        .orElseThrow();

                        LocalDate maxDate = officeSchedules.stream()
                                        .map(Schedule::getEndDate)
                                        .max(LocalDate::compareTo)
                                        .orElseThrow();

                        Map<LocalDate, Long> existingPublishedUsageByDate = scheduleEntryRepository
                                        .findPublishedEntriesForOffice(officeId, minDate, maxDate)
                                        .stream()
                                        .filter(entry -> entry.getWorkMode() == WorkMode.OFFICE)
                                        .collect(Collectors.groupingBy(
                                                        ScheduleEntry::getDate,
                                                        Collectors.counting()));

                        Map<LocalDate, Long> candidateUsageByDate = candidateEntries.stream()
                                        .filter(entry -> entry.getSchedule() != null)
                                        .filter(entry -> entry.getSchedule().getOffice() != null)
                                        .filter(entry -> entry.getSchedule().getOffice().getId().equals(officeId))
                                        .filter(entry -> entry.getWorkMode() == WorkMode.OFFICE)
                                        .collect(Collectors.groupingBy(
                                                        ScheduleEntry::getDate,
                                                        Collectors.counting()));

                        for (Map.Entry<LocalDate, Long> candidateUsage : candidateUsageByDate.entrySet()) {
                                LocalDate date = candidateUsage.getKey();

                                long totalUsage = existingPublishedUsageByDate.getOrDefault(date, 0L)
                                                + candidateUsage.getValue();

                                if (totalUsage > maxCapacity) {
                                        throw new BusinessValidationException(
                                                        "Publishing would exceed office capacity for office "
                                                                        + officeId
                                                                        + " on "
                                                                        + date
                                                                        + ". Capacity: "
                                                                        + maxCapacity
                                                                        + ", usage after publishing: "
                                                                        + totalUsage
                                                                        + ".");
                                }
                        }
                }
        }

        private void validateDiscardRequest(
                        ScheduleDiscardRequestDTO request,
                        User hrUser) {
                if (hrUser == null || hrUser.getId() == null) {
                        throw new AccessDeniedException("Unauthenticated.");
                }

                if (hrUser.getRole() != Role.HR) {
                        throw new AccessDeniedException("Only HR can discard schedules.");
                }

                if (hrUser.getCompany() == null) {
                        throw new AccessDeniedException("HR user is not assigned to a company.");
                }

                if (request.getScheduleIds() == null || request.getScheduleIds().isEmpty()) {
                        throw new BusinessValidationException("At least one schedule must be selected.");
                }
        }

        private void validateSchedulesAreUnpublishedForDiscard(List<Schedule> schedules) {
                List<Long> publishedIds = schedules.stream()
                                .filter(Schedule::isPublished)
                                .map(Schedule::getId)
                                .toList();

                if (!publishedIds.isEmpty()) {
                        throw new BusinessValidationException(
                                        "Published schedules cannot be discarded. Invalid schedule IDs: "
                                                        + publishedIds);
                }
        }

        // -------------------------------------------------------------------------
        // Single publish validation helpers
        // -------------------------------------------------------------------------

        private void validateSingleScheduleHasNoPublishedOverlap(Schedule schedule) {
                List<Schedule> conflicts = scheduleRepository.findPublishedForTeamInRange(
                                schedule.getTeam().getId(),
                                schedule.getStartDate(),
                                schedule.getEndDate());

                if (!conflicts.isEmpty()) {
                        throw new BusinessValidationException(
                                        "Team '" + schedule.getTeam().getName()
                                                        + "' already has a published schedule in this date range.");
                }
        }

        private void validateSingleScheduleOfficeCapacity(Schedule schedule) {
                List<ScheduleEntry> candidateEntries = scheduleEntryRepository.findByScheduleIdInWithScheduleAndOffice(
                                List.of(schedule.getId()));

                if (candidateEntries.isEmpty()) {
                        throw new BusinessValidationException("Schedule has no schedule entries.");
                }

                Long officeId = schedule.getOffice().getId();
                int maxCapacity = schedule.getOffice().getMaxCapacity();

                Map<LocalDate, Long> existingPublishedUsageByDate = scheduleEntryRepository
                                .findPublishedEntriesForOffice(
                                                officeId,
                                                schedule.getStartDate(),
                                                schedule.getEndDate())
                                .stream()
                                .filter(entry -> entry.getWorkMode() == WorkMode.OFFICE)
                                .collect(Collectors.groupingBy(
                                                ScheduleEntry::getDate,
                                                Collectors.counting()));

                Map<LocalDate, Long> candidateUsageByDate = candidateEntries.stream()
                                .filter(entry -> entry.getWorkMode() == WorkMode.OFFICE)
                                .collect(Collectors.groupingBy(
                                                ScheduleEntry::getDate,
                                                Collectors.counting()));

                for (Map.Entry<LocalDate, Long> candidateUsage : candidateUsageByDate.entrySet()) {
                        LocalDate date = candidateUsage.getKey();

                        long totalUsage = existingPublishedUsageByDate.getOrDefault(date, 0L)
                                        + candidateUsage.getValue();

                        if (totalUsage > maxCapacity) {
                                throw new BusinessValidationException(
                                                "Publishing would exceed office capacity on "
                                                                + date
                                                                + ". Capacity: "
                                                                + maxCapacity
                                                                + ", usage after publishing: "
                                                                + totalUsage
                                                                + ".");
                        }
                }
        }

        // ── Common guard ──────────────────────────────────────────────────────────

        private void validateHrContext(User hrUser) {
                if (hrUser == null || hrUser.getId() == null) {
                        throw new AccessDeniedException("Unauthenticated.");
                }
                if (hrUser.getRole() != Role.HR) {
                        throw new AccessDeniedException("Only HR can manage schedules.");
                }
                if (hrUser.getCompany() == null) {
                        throw new AccessDeniedException("HR user is not assigned to a company.");
                }
        }
}