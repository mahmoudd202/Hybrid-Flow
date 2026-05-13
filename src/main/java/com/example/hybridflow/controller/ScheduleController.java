package com.example.hybridflow.controller;

import com.example.hybridflow.dto.ScheduleConflictCheckRequestDTO;
import com.example.hybridflow.dto.ScheduleConflictCheckResponseDTO;
import com.example.hybridflow.dto.ScheduleGenerationRequestDTO;
import com.example.hybridflow.dto.ScheduleGenerationResponseDTO;
import com.example.hybridflow.entity.User;
import com.example.hybridflow.security.CustomUserDetails;
import com.example.hybridflow.service.ScheduleGenerationService;
import com.example.hybridflow.service.ScheduleManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/schedules")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleGenerationService scheduleGenerationService;
    private final ScheduleManagementService scheduleManagementService;

    @PostMapping("/check-conflicts")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<ScheduleConflictCheckResponseDTO> checkConflicts(
            @Valid @RequestBody ScheduleConflictCheckRequestDTO request) {
        return ResponseEntity.ok(scheduleManagementService.checkConflicts(request));
    }

    @PostMapping("/generate")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<ScheduleGenerationResponseDTO> generateSchedule(
            @Valid @RequestBody ScheduleGenerationRequestDTO request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        User currentUser = userDetails.getUser();
        return ResponseEntity.ok(scheduleGenerationService.generateSchedule(request, currentUser));
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<Void> publishSchedule(@PathVariable Long id) {
        scheduleManagementService.publishSchedule(id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<Void> deleteSchedule(@PathVariable Long id) {
        scheduleManagementService.deleteSchedule(id);
        return ResponseEntity.noContent().build();
    }
}