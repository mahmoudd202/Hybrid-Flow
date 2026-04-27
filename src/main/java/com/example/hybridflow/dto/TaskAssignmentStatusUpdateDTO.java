package com.example.hybridflow.dto;

import com.example.hybridflow.entity.TaskAssignmentStatus;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TaskAssignmentStatusUpdateDTO {

    @NotNull(message = "status is required")
    private TaskAssignmentStatus status;
}
