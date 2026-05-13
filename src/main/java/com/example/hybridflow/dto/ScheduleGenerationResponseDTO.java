package com.example.hybridflow.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleGenerationResponseDTO {
    private List<Long> scheduleIds;
    private double overallFairnessScore;
    private List<TeamFairnessDTO> teamFairnessScores;
    private List<IndividualFairnessDTO> individualFairnessScores;
    private String status;
    private String message;
}