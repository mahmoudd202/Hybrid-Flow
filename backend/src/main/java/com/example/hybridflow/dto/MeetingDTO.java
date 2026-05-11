package com.example.hybridflow.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

import com.example.hybridflow.entity.MeetingType;


@Data
@AllArgsConstructor
@NoArgsConstructor
public class MeetingDTO {
    private Long id;
    private String title;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private MeetingType type;       // OFFICE or ONLINE
    private String hostEmail;
    private String officeName;
    private List<String> participatingTeamNames;
    private List<String> excludedParticipantEmails;

}
