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
public class ScheduleConflictCheckResponseDTO {
    private List<Long> availableTeamIds;
    private List<ConflictingTeamDTO> conflictingTeams;
}