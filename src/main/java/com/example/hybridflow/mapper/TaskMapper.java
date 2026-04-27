package com.example.hybridflow.mapper;

import org.springframework.stereotype.Component;

import com.example.hybridflow.dto.TaskAssignmentResponseDTO;
import com.example.hybridflow.dto.TaskDetailsResponseDTO;
import com.example.hybridflow.dto.TaskResponseDTO;
import com.example.hybridflow.entity.Task;
import com.example.hybridflow.entity.TaskAssignment;

import java.util.List;

@Component
public class TaskMapper {

    public TaskResponseDTO toTaskResponse(Task task) {
        if (task == null) return null;

        return TaskResponseDTO.builder()
                .id(task.getId())
                .title(task.getTitle())
                .description(task.getDescription())
                .dueDate(task.getDueDate())
                .targetType(task.getTargetType())
                .createdById(task.getCreatedBy().getId())
                .createdByEmail(task.getCreatedBy().getEmail())
                .companyId(task.getCompany().getId())
                .teamId(task.getTeam().getId())
                .teamName(task.getTeam().getName())
                .build();
    }

    public TaskAssignmentResponseDTO toAssignmentResponse(TaskAssignment assignment) {
        if (assignment == null) return null;

        Task task = assignment.getTask();

        return TaskAssignmentResponseDTO.builder()
                .assignmentId(assignment.getId())
                .status(assignment.getStatus())
                .assignedAt(assignment.getAssignedAt())
                .completedAt(assignment.getCompletedAt())

                .taskId(task.getId())
                .taskTitle(task.getTitle())
                .taskDescription(task.getDescription())
                .dueDate(task.getDueDate())
                .targetType(task.getTargetType())

                .assigneeId(assignment.getAssignee().getId())
                .assigneeEmail(assignment.getAssignee().getEmail())

                .createdById(task.getCreatedBy().getId())
                .createdByEmail(task.getCreatedBy().getEmail())

                .teamId(task.getTeam().getId())
                .teamName(task.getTeam().getName())
                .build();
    }

    public List<TaskResponseDTO> toTaskResponseList(List<Task> tasks) {
        return tasks.stream()
                .map(this::toTaskResponse)
                .toList();
    }

    public List<TaskAssignmentResponseDTO> toAssignmentResponseList(List<TaskAssignment> assignments) {
        return assignments.stream()
                .map(this::toAssignmentResponse)
                .toList();
    }

    public TaskDetailsResponseDTO toTaskDetailsResponse(Task task, List<TaskAssignment> assignments) {
        return TaskDetailsResponseDTO.builder()
                .task(toTaskResponse(task))
                .assignments(toAssignmentResponseList(assignments))
                .build();
    }
}