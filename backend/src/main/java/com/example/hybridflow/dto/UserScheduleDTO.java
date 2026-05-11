package com.example.hybridflow.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * One row in the schedule grid — all entries for a single user.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserScheduleDTO {
    private Long userId;
    private String email;
    private String firstName; // from UserProfile
    private String lastName; // from UserProfile
    private String roleName; // HR / MANAGER / EMPLOYEE
    private List<ScheduleEntryDTO> entries;
}
