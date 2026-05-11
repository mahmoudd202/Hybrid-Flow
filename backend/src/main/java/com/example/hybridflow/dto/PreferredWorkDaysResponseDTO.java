package com.example.hybridflow.dto;
import lombok.Builder;
import lombok.Data;
import java.time.DayOfWeek;
import java.util.Set;

@Data
@Builder
public class PreferredWorkDaysResponseDTO {
    private Long userId;
    private String userEmail;
    private Set<DayOfWeek> preferredDays;
}