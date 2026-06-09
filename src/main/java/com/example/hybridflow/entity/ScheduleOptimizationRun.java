package com.example.hybridflow.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "schedule_optimization_runs")
@Data
public class ScheduleOptimizationRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_status", nullable = false)
    private OptimizationJobStatus jobStatus;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    @lombok.EqualsAndHashCode.Exclude
    @lombok.ToString.Exclude
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "planning_policy_id")
    @lombok.EqualsAndHashCode.Exclude
    @lombok.ToString.Exclude
    private PlanningPolicy planningPolicy;

    @Column(name = "gurobi_status_code")
    private Integer gurobiStatusCode;

    @Column(name = "gurobi_status_label")
    private String gurobiStatusLabel;

    @Column(name = "objective_value")
    private Double objectiveValue;

    @Column(name = "objective_bound")
    private Double objectiveBound;

    @Column(name = "mip_gap")
    private Double mipGap;

    @Column(name = "runtime_seconds")
    private Double runtimeSeconds;

    @Column(name = "num_variables")
    private Integer numVariables;

    @Column(name = "num_constraints")
    private Integer numConstraints;

    @Column(name = "num_iterations")
    private Double numIterations;
    @Column(name = "num_nodes")
    private Double numNodes;

    @Column(name = "overall_fairness_score")
    private Double overallFairnessScore;

    @Column(name = "team_fairness_scores_json", columnDefinition = "TEXT")
    private String teamFairnessScoresJson;

    @Column(name = "individual_fairness_scores_json", columnDefinition = "TEXT")
    private String individualFairnessScoresJson;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "num_users")
    private Integer numUsers;

    @Column(name = "num_teams")
    private Integer numTeams;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
