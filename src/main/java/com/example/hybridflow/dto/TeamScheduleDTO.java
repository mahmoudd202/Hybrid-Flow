package com.example.hybridflow.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TeamScheduleDTO {
    private Long teamId;
    private String teamName;
    private String officeName;

    private LocalDate rangeStart;
    private LocalDate rangeEnd;

    private List<UserScheduleDTO> members;
    private List<MeetingDTO> meetings;
}
