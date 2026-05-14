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
public class UnavailableScheduleTeamDTO {

    private Long teamId;
    private String teamName;
    private int memberCount;
    private String reason;
    private List<Long> conflictingScheduleIds;
}