package com.example.hybridflow.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.example.hybridflow.dto.PersonalTaskCreateRequestDTO;
import com.example.hybridflow.dto.PersonalTaskResponseDTO;
import com.example.hybridflow.dto.PersonalTaskStatusUpdateDTO;
import com.example.hybridflow.security.CustomUserDetails;
import com.example.hybridflow.service.PersonalTaskService;

import java.util.List;

@RestController
@RequestMapping("/api/personal-tasks")
public class PersonalTaskController {

    private final PersonalTaskService personalTaskService;

    public PersonalTaskController(PersonalTaskService personalTaskService) {
        this.personalTaskService = personalTaskService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('MANAGER','EMPLOYEE')")
    public ResponseEntity<PersonalTaskResponseDTO> createPersonalTask(
            @Valid @RequestBody PersonalTaskCreateRequestDTO dto,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        if (userDetails == null) {
            return ResponseEntity.status(401).build();
        }

        PersonalTaskResponseDTO response =
                personalTaskService.createPersonalTask(dto, userDetails.getUser());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/my")
    @PreAuthorize("hasAnyRole('MANAGER','EMPLOYEE')")
    public ResponseEntity<List<PersonalTaskResponseDTO>> getMyPersonalTasks(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        if (userDetails == null) {
            return ResponseEntity.status(401).build();
        }

        return ResponseEntity.ok(
                personalTaskService.getMyPersonalTasks(userDetails.getUser())
        );
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('MANAGER','EMPLOYEE')")
    public ResponseEntity<PersonalTaskResponseDTO> updateMyPersonalTaskStatus(
            @PathVariable Long id,
            @Valid @RequestBody PersonalTaskStatusUpdateDTO dto,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        if (userDetails == null) {
            return ResponseEntity.status(401).build();
        }

        PersonalTaskResponseDTO response =
                personalTaskService.updateMyPersonalTaskStatus(id, dto.getStatus(), userDetails.getUser());

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('MANAGER','EMPLOYEE')")
    public ResponseEntity<Void> deleteMyPersonalTask(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        if (userDetails == null) {
            return ResponseEntity.status(401).build();
        }

        personalTaskService.deleteMyPersonalTask(id, userDetails.getUser());
        return ResponseEntity.noContent().build();
    }
}