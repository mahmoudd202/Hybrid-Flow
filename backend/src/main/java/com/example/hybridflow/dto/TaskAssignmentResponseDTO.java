package com.example.hybridflow.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

import com.example.hybridflow.entity.TaskAssignmentStatus;
import com.example.hybridflow.entity.TaskTargetType;

@Data
@Builder
public class TaskAssignmentResponseDTO {

    private Long assignmentId;
    private TaskAssignmentStatus status;
    private LocalDateTime assignedAt;
    private LocalDateTime completedAt;

    private Long taskId;
    private String taskTitle;
    private String taskDescription;
    private LocalDateTime dueDate;
    private TaskTargetType targetType;

    private Long assigneeId;
    private String assigneeEmail;

    private Long createdById;
    private String createdByEmail;

    private Long teamId;
    private String teamName;
}