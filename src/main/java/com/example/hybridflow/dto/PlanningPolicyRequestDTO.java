package com.example.hybridflow.dto;


import jakarta.validation.constraints.*;
import lombok.Data;
 
@Data
public class PlanningPolicyRequestDTO {
 
    @NotBlank(message = "Policy name is required")
    @Size(max = 150, message = "Policy name must be at most 150 characters")
    private String name;
 
    @NotNull(message = "workingDaysPerWeek is required")
    @Min(value = 1, message = "workingDaysPerWeek must be at least 1")
    @Max(value = 5, message = "workingDaysPerWeek must be at most 5")
    private Integer workingDaysPerWeek;
 
    @NotNull(message = "minOfficeDaysPerWeek is required")
    @Min(value = 0, message = "minOfficeDaysPerWeek must be at least 0")
    private Integer minOfficeDaysPerWeek;
 
    @NotNull(message = "maxOfficeDaysPerWeek is required")
    @Min(value = 0, message = "maxOfficeDaysPerWeek must be at least 0")
    private Integer maxOfficeDaysPerWeek;
 
    @NotNull(message = "dailyCapacity is required")
    @Min(value = 1, message = "dailyCapacity must be at least 1")
    private Integer dailyCapacity;
 
    @NotNull(message = "maxConsecutiveOfficeDays is required")
    @Min(value = 1, message = "maxConsecutiveOfficeDays must be at least 1")
    private Integer maxConsecutiveOfficeDays;
 
    @NotNull(message = "minTeamSharedDays is required")
    @Min(value = 0, message = "minTeamSharedDays must be at least 0")
    private Integer minTeamSharedDays;
 
    @NotNull(message = "coPresenceThresholdPercentage is required")
    @Min(value = 0, message = "coPresenceThresholdPercentage must be at least 0")
    @Max(value = 100, message = "coPresenceThresholdPercentage must be at most 100")
    private Integer coPresenceThresholdPercentagePerDay;
}