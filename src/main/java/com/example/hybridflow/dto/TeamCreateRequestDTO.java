package com.example.hybridflow.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TeamCreateRequestDTO {

    @NotBlank(message = "Team name is required")
    private String name;

    private Long officeId; // optional — HR can assign office later
}