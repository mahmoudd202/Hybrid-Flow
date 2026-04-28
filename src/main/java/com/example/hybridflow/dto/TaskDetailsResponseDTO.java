package com.example.hybridflow.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class TaskDetailsResponseDTO {
    private TaskResponseDTO task;
    private List<TaskAssignmentResponseDTO> assignments;
    private List<String> excludedAssigneeEmails;

}