package com.example.hybridflow.dto;

import lombok.Data;
import java.time.DayOfWeek;
import java.util.Set;

@Data
public class PreferredWorkDaysRequestDTO {
    private Set<DayOfWeek> preferredDays;
}
