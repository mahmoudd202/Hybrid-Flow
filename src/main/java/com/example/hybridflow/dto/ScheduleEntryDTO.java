package com.example.hybridflow.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

import com.example.hybridflow.entity.WorkMode;

/**
 * A single day-cell in the schedule grid.
 * Tells the frontend: "on this date, this user works in this mode."
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ScheduleEntryDTO {
    private Long entryId;
    private LocalDate date;
    private WorkMode workMode;   // OFFICE, ONLINE, OFF, SICK_LEAVE
}
