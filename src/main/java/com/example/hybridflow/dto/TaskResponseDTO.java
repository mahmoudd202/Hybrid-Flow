package com.example.hybridflow.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

import com.example.hybridflow.entity.TaskTargetType;

@Data
@Builder
public class TaskResponseDTO {
    private Long id;
    private String title;
    private String description;
    private LocalDateTime dueDate;
    private TaskTargetType targetType;

    private Long createdById;
    private String createdByEmail;

    private Long companyId;

    private Long teamId;
    private String teamName;
}