package com.example.hybridflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Full representation of one Gurobi optimization run.
 *
 * Used by:
 *   GET /api/schedules/optimization-runs/{runId}   — polling endpoint
 *   GET /api/schedules/optimization-runs           — history (COMPLETED only)
 *   GET /api/schedules/unpublished                 — embedded per schedule
 *
 * jobStatus lifecycle:
 *   PENDING   → job accepted, solver not started yet
 *   RUNNING   → Gurobi model is being solved
 *   COMPLETED → solution found; all stat fields are populated
 *   FAILED    → model infeasible, validation error, or unexpected exception;
 *               check errorMessage — handle just like the old status="FAILED"
 *               synchronous response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OptimizationRunDTO {

    private Long runId;

    /**
     * One of: PENDING, RUNNING, COMPLETED, FAILED.
     *
     * FAILED maps to the old synchronous status="FAILED" — errorMessage
     * contains the same human-readable reason (e.g. "Model is infeasible...").
     */
    private String jobStatus;

    /** Non-null only when jobStatus = FAILED. */
    private String errorMessage;

    // ── Gurobi raw stats (null until COMPLETED) ───────────────────────────────
    private String  gurobiStatusLabel;   // e.g. "OPTIMAL"
    private Integer gurobiStatusCode;
    private Double  objectiveValue;
    private Double  objectiveBound;
    private Double  mipGap;
    private Double  runtimeSeconds;
    private Integer numVariables;
    private Integer numConstraints;
    private Double  numIterations;
    private Double  numNodes;

    // ── Fairness snapshot (null until COMPLETED) ──────────────────────────────
    private Double overallFairnessScore;
    private List<TeamFairnessDTO> teamFairnessScores;
    private List<IndividualFairnessDTO> individualFairnessScores;

    // ── Linked schedule IDs (null until COMPLETED) ────────────────────────────
    private List<Long> scheduleIds;

    // ── Input snapshot ────────────────────────────────────────────────────────
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer   numUsers;
    private Integer   numTeams;
    private Long      planningPolicyId;
    private String    planningPolicyName;

    // ── Timestamps ────────────────────────────────────────────────────────────
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
}
