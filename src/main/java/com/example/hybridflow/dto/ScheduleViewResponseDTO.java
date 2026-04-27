package com.example.hybridflow.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * Root response wrapper returned by the schedule-viewing endpoints.
 * <p>
 * For EMPLOYEE / MANAGER → contains exactly 1 team.<br>
 * For HR → contains all teams in the company.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ScheduleViewResponseDTO {
    private LocalDate rangeStart;
    private LocalDate rangeEnd;
    private List<TeamScheduleDTO> teams;
}
