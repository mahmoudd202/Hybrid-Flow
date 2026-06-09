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
public class UnpublishedScheduleDTO {

    private Long scheduleId;

    private Long teamId;
    private String teamName;

    private Long officeId;
    private String officeName;

    private LocalDate startDate;
    private LocalDate endDate;

    private LocalDateTime createdAt;

    private double overallFairnessScore;

    private TeamFairnessDTO teamFairnessScore;

    private List<IndividualFairnessDTO> individualFairnessScores;

    private List<UserScheduleDTO> members;

    private OptimizationRunDTO optimizationRun;
}