package com.example.hybridflow.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MoveEmployeeRequestDTO {
    @NotNull(message = "New team ID is required")
    private Long newTeamId;
}
