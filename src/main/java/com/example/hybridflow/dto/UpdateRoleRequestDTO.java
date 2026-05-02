package com.example.hybridflow.dto;

import com.example.hybridflow.entity.Role;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateRoleRequestDTO {
    @NotNull(message = "New role is required")
    private Role newRole;
}