package com.example.hybridflow.dto;

import java.time.LocalDate;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleGenerationRequestDTO {
    private Long officeId;
    private List<Long> teamIds; // These should be pre-filtered available teams
    private LocalDate startDate;
    private LocalDate endDate;
}