package com.example.hybridflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleGenerationResponseDTO {

    private String status;
    private String message;

    private Long planningPolicyId;
    private String planningPolicyName;

    private List<Long> scheduleIds;

    private double overallFairnessScore;
    private List<TeamFairnessDTO> teamFairnessScores;
    private List<IndividualFairnessDTO> individualFairnessScores;

    private List<GeneratedTeamScheduleDTO> generatedSchedules;
}