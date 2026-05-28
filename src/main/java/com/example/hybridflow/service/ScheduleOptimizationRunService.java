package com.example.hybridflow.service;

import com.example.hybridflow.dto.IndividualFairnessDTO;
import com.example.hybridflow.dto.OptimizationRunDTO;
import com.example.hybridflow.dto.TeamFairnessDTO;
import com.example.hybridflow.entity.OptimizationJobStatus;
import com.example.hybridflow.entity.ScheduleOptimizationRun;
import com.example.hybridflow.exception.ResourceNotFoundException;
import com.example.hybridflow.repository.ScheduleOptimizationRunRepository;
import com.example.hybridflow.repository.ScheduleRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

/**
 * Handles reading and mapping of ScheduleOptimizationRun records.
 *
 * Responsibilities:
 *   - Fetch a single run (for polling) — any status
 *   - Fetch all COMPLETED runs for a company (for history view)
 *   - Convert entity → OptimizationRunDTO, including JSON deserialization
 *     of the stored fairness scores
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleOptimizationRunService {

    private final ScheduleOptimizationRunRepository runRepository;
    private final ScheduleRepository scheduleRepository;
    private final ObjectMapper objectMapper;

    // ── Polling endpoint ──────────────────────────────────────────────────────

    /**
     * Returns the current state of a run (any status).
     * The frontend polls this until jobStatus = COMPLETED or FAILED.
     *
     * FAILED maps to the old synchronous "status=FAILED" response —
     * errorMessage contains the same human-readable reason.
     */
    @Transactional(readOnly = true)
    public OptimizationRunDTO getRunById(Long runId, Long companyId) {
        ScheduleOptimizationRun run = runRepository.findById(runId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Optimization run not found: " + runId));

        if (!run.getCompany().getId().equals(companyId)) {
            throw new AccessDeniedException(
                    "This optimization run does not belong to your company.");
        }

        return toDTO(run);
    }

    // ── History endpoint ──────────────────────────────────────────────────────

    /**
     * Returns all COMPLETED runs for a company, newest first.
     * PENDING/RUNNING/FAILED runs are excluded (history = successful solves only).
     */
    @Transactional(readOnly = true)
    public List<OptimizationRunDTO> getCompletedRunsForCompany(Long companyId) {
        return runRepository
                .findByCompanyIdAndJobStatusOrderByCreatedAtDesc(
                        companyId, OptimizationJobStatus.COMPLETED)
                .stream()
                .map(this::toDTO)
                .toList();
    }

    // ── Mapping ───────────────────────────────────────────────────────────────

    /**
     * Converts a ScheduleOptimizationRun entity to OptimizationRunDTO.
     * Deserializes the stored JSON fairness arrays back to typed DTOs.
     * Attaches the schedule IDs linked to this run.
     */
    public OptimizationRunDTO toDTO(ScheduleOptimizationRun run) {
        List<TeamFairnessDTO> teamScores = deserializeTeamScores(run.getTeamFairnessScoresJson());
        List<IndividualFairnessDTO> individualScores = deserializeIndividualScores(
                run.getIndividualFairnessScoresJson());

        // Collect all schedule IDs linked to this run
        List<Long> scheduleIds = scheduleRepository
                .findByOptimizationRunId(run.getId())
                .stream()
                .map(s -> s.getId())
                .toList();

        String policyName = null;
        Long policyId = null;
        if (run.getPlanningPolicy() != null) {
            policyId = run.getPlanningPolicy().getId();
            policyName = run.getPlanningPolicy().getName();
        }

        return OptimizationRunDTO.builder()
                .runId(run.getId())
                .jobStatus(run.getJobStatus().name())
                .errorMessage(run.getErrorMessage())
                .gurobiStatusLabel(run.getGurobiStatusLabel())
                .gurobiStatusCode(run.getGurobiStatusCode())
                .objectiveValue(run.getObjectiveValue())
                .objectiveBound(run.getObjectiveBound())
                .mipGap(run.getMipGap())
                .runtimeSeconds(run.getRuntimeSeconds())
                .numVariables(run.getNumVariables())
                .numConstraints(run.getNumConstraints())
                .numIterations(run.getNumIterations())
                .numNodes(run.getNumNodes())
                .overallFairnessScore(run.getOverallFairnessScore())
                .teamFairnessScores(teamScores)
                .individualFairnessScores(individualScores)
                .scheduleIds(scheduleIds.isEmpty() ? null : scheduleIds)
                .startDate(run.getStartDate())
                .endDate(run.getEndDate())
                .numUsers(run.getNumUsers())
                .numTeams(run.getNumTeams())
                .planningPolicyId(policyId)
                .planningPolicyName(policyName)
                .createdAt(run.getCreatedAt())
                .completedAt(run.getCompletedAt())
                .build();
    }

    // ── JSON helpers ──────────────────────────────────────────────────────────

    private List<TeamFairnessDTO> deserializeTeamScores(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Failed to deserialize team fairness scores JSON: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<IndividualFairnessDTO> deserializeIndividualScores(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Failed to deserialize individual fairness scores JSON: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
