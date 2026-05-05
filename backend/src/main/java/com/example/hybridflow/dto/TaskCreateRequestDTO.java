package com.example.hybridflow.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

import com.example.hybridflow.entity.TaskTargetType;

@Data
public class TaskCreateRequestDTO {

    @NotBlank(message = "Title is required")
    private String title;

    private String description;

    @NotNull(message = "targetType is required")
    private TaskTargetType targetType;

    // required only for INDIVIDUAL
    private Long assigneeId;

    @NotNull(message = "dueDate is required")
    @Future(message = "dueDate must be in the future")
    private LocalDateTime dueDate;
}