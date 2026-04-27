package com.example.hybridflow.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.example.hybridflow.dto.TaskAssignmentResponseDTO;
import com.example.hybridflow.dto.TaskAssignmentStatusUpdateDTO;
import com.example.hybridflow.dto.TaskCreateRequestDTO;
import com.example.hybridflow.dto.TaskDetailsResponseDTO;
import com.example.hybridflow.dto.TaskResponseDTO;
import com.example.hybridflow.mapper.TaskMapper;
import com.example.hybridflow.security.CustomUserDetails;
import com.example.hybridflow.service.TaskService;

import java.util.List;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService taskService;
    private final TaskMapper taskMapper;

    public TaskController(TaskService taskService, TaskMapper taskMapper) {
        this.taskService = taskService;
        this.taskMapper = taskMapper;
    }

    @PostMapping
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<TaskDetailsResponseDTO> createTask(
            @Valid @RequestBody TaskCreateRequestDTO dto,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        if (userDetails == null) {
            return ResponseEntity.status(401).build();
        }

        TaskDetailsResponseDTO response = taskService.createTask(dto, userDetails.getUser());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/manager/created")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<List<TaskResponseDTO>> getManagerCreatedTasks(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        if (userDetails == null) {
            return ResponseEntity.status(401).build();
        }

        return ResponseEntity.ok(
                taskMapper.toTaskResponseList(taskService.getManagerCreatedTasks(userDetails.getUser()))
        );
    }

    @GetMapping("/my-assignments")
    @PreAuthorize("hasAnyRole('EMPLOYEE','MANAGER')")
    public ResponseEntity<List<TaskAssignmentResponseDTO>> getMyAssignments(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        if (userDetails == null) {
            return ResponseEntity.status(401).build();
        }

        return ResponseEntity.ok(
                taskMapper.toAssignmentResponseList(taskService.getMyAssignments(userDetails.getUser()))
        );
    }

    @GetMapping("/{taskId}/assignments")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<List<TaskAssignmentResponseDTO>> getAssignmentsForTask(
            @PathVariable Long taskId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        if (userDetails == null) {
            return ResponseEntity.status(401).build();
        }

        return ResponseEntity.ok(
                taskMapper.toAssignmentResponseList(
                        taskService.getAssignmentsForTask(taskId, userDetails.getUser())
                )
        );
    }

    @PatchMapping("/assignments/{assignmentId}/status")
    @PreAuthorize("hasAnyRole('EMPLOYEE','MANAGER')")
    public ResponseEntity<TaskAssignmentResponseDTO> updateMyAssignmentStatus(
            @PathVariable Long assignmentId,
            @Valid @RequestBody TaskAssignmentStatusUpdateDTO dto,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        if (userDetails == null) {
            return ResponseEntity.status(401).build();
        }

        return ResponseEntity.ok(
                taskMapper.toAssignmentResponse(
                        taskService.updateMyAssignmentStatus(
                                assignmentId,
                                dto.getStatus(),
                                userDetails.getUser()
                        )
                )
        );
    }
}