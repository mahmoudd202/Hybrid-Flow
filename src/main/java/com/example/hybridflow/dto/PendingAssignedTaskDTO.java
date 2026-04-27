package com.example.hybridflow.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class PendingAssignedTaskDTO {

    private Long assignmentId;
    private Long taskId;
    private String taskTitle;
    private String taskDescription;
    private LocalDateTime dueDate;
    private String status;
}