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
public class UnpublishedSchedulesResponseDTO {

    private int count;
    private double overallFairnessScore;
    private List<UnpublishedScheduleDTO> schedules;
}