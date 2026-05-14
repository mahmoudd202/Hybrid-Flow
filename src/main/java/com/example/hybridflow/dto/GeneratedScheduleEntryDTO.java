package com.example.hybridflow.dto;

import com.example.hybridflow.entity.WorkMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeneratedScheduleEntryDTO {

    private Long userId;
    private String userEmail;
    private LocalDate date;
    private WorkMode workMode;
}