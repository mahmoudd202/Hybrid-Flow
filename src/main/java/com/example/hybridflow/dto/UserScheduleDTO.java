package com.example.hybridflow.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;


@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserScheduleDTO {
    private Long userId;
    private String email;
    private String firstName;
    private String lastName;
    private String roleName;
    private List<ScheduleEntryDTO> entries;
}
