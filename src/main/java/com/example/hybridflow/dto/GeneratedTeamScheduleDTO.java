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
public class GeneratedTeamScheduleDTO {

    private Long scheduleId;

    private Long teamId;
    private String teamName;

    private Long officeId;
    private String officeName;

    private List<GeneratedScheduleEntryDTO> entries;
}