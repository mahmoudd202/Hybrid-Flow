package com.example.hybridflow.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ScheduleViewResponseDTO {
    private LocalDate rangeStart;
    private LocalDate rangeEnd;
    private List<TeamScheduleDTO> teams;
}
