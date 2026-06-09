package com.example.hybridflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OptimizationRunDTO {

    private Long runId;
    private String jobStatus;
    private String errorMessage;

    private String gurobiStatusLabel;
    private Integer gurobiStatusCode;
    private Double objectiveValue;
    private Double objectiveBound;
    private Double mipGap;
    private Double runtimeSeconds;
    private Integer numVariables;
    private Integer numConstraints;
    private Double numIterations;
    private Double numNodes;

    private Double overallFairnessScore;
    private List<TeamFairnessDTO> teamFairnessScores;
    private List<IndividualFairnessDTO> individualFairnessScores;
    private List<Long> scheduleIds;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer numUsers;
    private Integer numTeams;
    private Long planningPolicyId;
    private String planningPolicyName;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
}
