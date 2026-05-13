package com.example.hybridflow.dto;

import lombok.Builder;
import lombok.Data;
 
import java.time.LocalDateTime;
 
@Data
@Builder
public class PlanningPolicyResponseDTO {
 
    private Long id;
    private Long companyId;
    private String name;
    private Integer workingDaysPerWeek;
    private Integer minOfficeDaysPerWeek;
    private Integer maxOfficeDaysPerWeek;
    private Integer maxConsecutiveOfficeDays;
    private Integer minTeamSharedDays;
    private Integer coPresenceThresholdPercentagePerDay;
    private LocalDateTime createdAt;
}
 