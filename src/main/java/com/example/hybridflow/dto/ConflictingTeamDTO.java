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
public class ConflictingTeamDTO {
    private Long teamId;
    private String teamName;
    private List<Long> conflictingScheduleIds;
}
