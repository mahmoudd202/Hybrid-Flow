package com.example.hybridflow.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

import com.example.hybridflow.entity.WorkMode;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ScheduleEntryDTO {
    private Long entryId;
    private LocalDate date;
    private WorkMode workMode;
    private String officeName;

    public ScheduleEntryDTO(Long entryId, LocalDate date, WorkMode workMode) {
        this.entryId = entryId;
        this.date = date;
        this.workMode = workMode;
    }
}