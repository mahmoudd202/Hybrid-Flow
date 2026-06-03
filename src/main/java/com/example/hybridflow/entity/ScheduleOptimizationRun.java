package com.example.hybridflow.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Persists the result of one Gurobi schedule-generation run.
 *
 * Dual purpose:
 *   1. Job tracker  — jobStatus tracks PENDING → RUNNING → COMPLETED/FAILED
 *                     so the frontend can poll GET /optimization-runs/{id}.
 *   2. Stats record — once COMPLETED, holds all raw Gurobi solver numbers
 *                     and JSON-serialised fairness scores so they can be
 *                     retrieved at any time without recomputing.
 *
 * Linked back from Schedule.optimizationRun (nullable FK) so every draft
 * schedule knows which run produced it.
 */
@Entity
@Table(name = "schedule_optimization_runs")
@Data
public class ScheduleOptimizationRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ── Job lifecycle ─────────────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "job_status", nullable = false)
    private OptimizationJobStatus jobStatus;

    /** Populated only when jobStatus = FAILED. */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    // ── Ownership ─────────────────────────────────────────────────────────────

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    @lombok.EqualsAndHashCode.Exclude
    @lombok.ToString.Exclude
    private Company company;

    /** The planning policy used at generation time — preserved as FK. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "planning_policy_id")
    @lombok.EqualsAndHashCode.Exclude
    @lombok.ToString.Exclude
    private PlanningPolicy planningPolicy;

    // ── Raw Gurobi stats (null until COMPLETED) ───────────────────────────────

    /** GRB.Status integer code (2 = OPTIMAL). */
    @Column(name = "gurobi_status_code")
    private Integer gurobiStatusCode;

    /** Human-readable label e.g. "OPTIMAL". */
    @Column(name = "gurobi_status_label")
    private String gurobiStatusLabel;

    /** model.get(GRB.DoubleAttr.ObjVal) */
    @Column(name = "objective_value")
    private Double objectiveValue;

    /** model.get(GRB.DoubleAttr.ObjBound) */
    @Column(name = "objective_bound")
    private Double objectiveBound;

    /** model.get(GRB.DoubleAttr.MIPGap) */
    @Column(name = "mip_gap")
    private Double mipGap;

    /** model.get(GRB.DoubleAttr.Runtime) in seconds */
    @Column(name = "runtime_seconds")
    private Double runtimeSeconds;

    /** model.get(GRB.IntAttr.NumVars) */
    @Column(name = "num_variables")
    private Integer numVariables;

    /** model.get(GRB.IntAttr.NumConstrs) */
    @Column(name = "num_constraints")
    private Integer numConstraints;

    /** model.get(GRB.DoubleAttr.IterCount) */
    @Column(name = "num_iterations")
    private Double numIterations;

    /** model.get(GRB.DoubleAttr.NodeCount) */
    @Column(name = "num_nodes")
    private Double numNodes;

    // ── Fairness snapshot (null until COMPLETED) ──────────────────────────────

    @Column(name = "overall_fairness_score")
    private Double overallFairnessScore;

    /** JSON array of TeamFairnessDTO objects. */
    @Column(name = "team_fairness_scores_json", columnDefinition = "TEXT")
    private String teamFairnessScoresJson;

    /** JSON array of IndividualFairnessDTO objects. */
    @Column(name = "individual_fairness_scores_json", columnDefinition = "TEXT")
    private String individualFairnessScoresJson;

    // ── Input snapshot ────────────────────────────────────────────────────────

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "num_users")
    private Integer numUsers;

    @Column(name = "num_teams")
    private Integer numTeams;

    // ── Timestamps ────────────────────────────────────────────────────────────

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
