package com.example.hybridflow.controller;

import com.example.hybridflow.dto.AvailableScheduleTeamsResponseDTO;
import com.example.hybridflow.dto.DeleteUnpublishedSchedulesResponseDTO;
import com.example.hybridflow.dto.OptimizationRunDTO;
import com.example.hybridflow.dto.ScheduleConflictCheckRequestDTO;
import com.example.hybridflow.dto.ScheduleConflictCheckResponseDTO;
import com.example.hybridflow.dto.ScheduleDiscardRequestDTO;
import com.example.hybridflow.dto.ScheduleDiscardResponseDTO;
import com.example.hybridflow.dto.ScheduleGenerationAcceptedDTO;
import com.example.hybridflow.dto.ScheduleGenerationRequestDTO;
import com.example.hybridflow.dto.SchedulePublishRequestDTO;
import com.example.hybridflow.dto.SchedulePublishResponseDTO;
import com.example.hybridflow.dto.UnpublishedSchedulesResponseDTO;
import com.example.hybridflow.entity.User;
import com.example.hybridflow.security.CustomUserDetails;
import com.example.hybridflow.service.ScheduleGenerationService;
import com.example.hybridflow.service.ScheduleManagementService;
import com.example.hybridflow.service.ScheduleOptimizationRunService;
import com.example.hybridflow.service.ScheduleTeamAvailabilityService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
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
    private final ScheduleTeamAvailabilityService scheduleTeamAvailabilityService;
    private final ScheduleOptimizationRunService optimizationRunService;

    @GetMapping("/unpublished")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<UnpublishedSchedulesResponseDTO> getUnpublishedSchedules(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        if (userDetails == null)
            return ResponseEntity.status(401).build();
        return ResponseEntity.ok(
                scheduleManagementService.getUnpublishedSchedules(userDetails.getUser()));
    }

    @DeleteMapping("/unpublished")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<DeleteUnpublishedSchedulesResponseDTO> deleteUnpublishedSchedules(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        if (userDetails == null)
            return ResponseEntity.status(401).build();
        return ResponseEntity.ok(
                scheduleManagementService.deleteAllUnpublishedSchedules(userDetails.getUser()));
    }

    @GetMapping("/available-teams")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<AvailableScheduleTeamsResponseDTO> getAvailableTeams(
            @RequestParam Long officeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        User currentUser = userDetails.getUser();
        return ResponseEntity.ok(
                scheduleTeamAvailabilityService.getAvailableTeams(
                        officeId, startDate, endDate, currentUser));
    }

    @PostMapping("/generate")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<ScheduleGenerationAcceptedDTO> generateSchedule(
            @Valid @RequestBody ScheduleGenerationRequestDTO request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        User currentUser = userDetails.getUser();
        ScheduleGenerationAcceptedDTO accepted = scheduleGenerationService.generateSchedule(request, currentUser);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(accepted);
    }

    @GetMapping("/optimization-runs/{runId}")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<OptimizationRunDTO> getOptimizationRun(
            @PathVariable Long runId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long companyId = userDetails.getUser().getCompany().getId();
        return ResponseEntity.ok(optimizationRunService.getRunById(runId, companyId));
    }

    @GetMapping("/optimization-runs")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<List<OptimizationRunDTO>> getOptimizationRunHistory(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long companyId = userDetails.getUser().getCompany().getId();
        return ResponseEntity.ok(optimizationRunService.getCompletedRunsForCompany(companyId));
    }

    @PostMapping("/check-conflicts")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<ScheduleConflictCheckResponseDTO> checkConflicts(
            @Valid @RequestBody ScheduleConflictCheckRequestDTO request) {
        return ResponseEntity.ok(scheduleManagementService.checkConflicts(request));
    }

    @PostMapping("/publish")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<SchedulePublishResponseDTO> publishSchedules(
            @Valid @RequestBody SchedulePublishRequestDTO request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        User currentUser = userDetails.getUser();
        return ResponseEntity.ok(
                scheduleManagementService.publishSchedules(request, currentUser));
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<Void> publishSchedule(@PathVariable Long id) {
        scheduleManagementService.publishSchedule(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/discard")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<ScheduleDiscardResponseDTO> discardSchedules(
            @Valid @RequestBody ScheduleDiscardRequestDTO request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        User currentUser = userDetails.getUser();
        return ResponseEntity.ok(
                scheduleManagementService.discardSchedules(request, currentUser));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<Void> deleteSchedule(@PathVariable Long id) {
        scheduleManagementService.deleteSchedule(id);
        return ResponseEntity.noContent().build();
    }
}