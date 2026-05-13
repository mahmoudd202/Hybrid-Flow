package com.example.hybridflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleConflictCheckRequestDTO {
    private Long officeId;
    private List<Long> teamIds;
    private LocalDate startDate;
    private LocalDate endDate;
}