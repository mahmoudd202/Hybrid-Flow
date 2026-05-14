package com.example.hybridflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AvailableScheduleTeamsResponseDTO {

    private Long officeId;
    private LocalDate startDate;
    private LocalDate endDate;

    private List<AvailableScheduleTeamDTO> availableTeams;
    private List<UnavailableScheduleTeamDTO> unavailableTeams;
}