package com.example.hybridflow.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.example.hybridflow.dto.PersonalTaskCreateRequestDTO;
import com.example.hybridflow.dto.PersonalTaskResponseDTO;
import com.example.hybridflow.dto.PersonalTaskStatusUpdateDTO;
import com.example.hybridflow.dto.PersonalTaskUpdateRequestDTO;
import com.example.hybridflow.security.CustomUserDetails;
import com.example.hybridflow.service.PersonalAgendaService;

import java.util.List;

@RestController
@RequestMapping("/api/personal-agendas")
public class PersonalAgendaController {

    private final PersonalAgendaService personalAgendaService;

    public PersonalAgendaController(PersonalAgendaService personalAgendaService) {
        this.personalAgendaService = personalAgendaService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('MANAGER','EMPLOYEE')")
    public ResponseEntity<PersonalTaskResponseDTO> createPersonalTask(
            @Valid @RequestBody PersonalTaskCreateRequestDTO dto,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(401).build();
        }

        PersonalTaskResponseDTO response = personalAgendaService.createPersonalTask(dto, userDetails.getUser());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/my")
    @PreAuthorize("hasAnyRole('MANAGER','EMPLOYEE')")
    public ResponseEntity<List<PersonalTaskResponseDTO>> getMyPersonalTasks(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(401).build();
        }

        return ResponseEntity.ok(
                personalAgendaService.getMyPersonalTasks(userDetails.getUser()));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('MANAGER','EMPLOYEE')")
    public ResponseEntity<PersonalTaskResponseDTO> updateMyPersonalTaskStatus(
            @PathVariable Long id,
            @Valid @RequestBody PersonalTaskStatusUpdateDTO dto,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(401).build();
        }

        PersonalTaskResponseDTO response = personalAgendaService.updateMyPersonalTaskStatus(id, dto.getStatus(),
                userDetails.getUser());

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('MANAGER','EMPLOYEE')")
    public ResponseEntity<Void> deleteMyPersonalTask(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(401).build();
        }

        personalAgendaService.deleteMyPersonalTask(id, userDetails.getUser());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('MANAGER','EMPLOYEE')")
    public ResponseEntity<PersonalTaskResponseDTO> updateMyPersonalTask(
            @PathVariable Long id,
            @Valid @RequestBody PersonalTaskUpdateRequestDTO dto,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(401).build();
        }

        PersonalTaskResponseDTO response = personalAgendaService.updateMyPersonalTask(id, dto, userDetails.getUser());

        return ResponseEntity.ok(response);
    }
}