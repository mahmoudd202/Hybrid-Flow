package com.example.hybridflow.controller;

import com.example.hybridflow.dto.AvailableScheduleTeamsResponseDTO;
import com.example.hybridflow.dto.DeleteUnpublishedSchedulesResponseDTO;
import com.example.hybridflow.dto.ScheduleConflictCheckRequestDTO;
import com.example.hybridflow.dto.ScheduleConflictCheckResponseDTO;
import com.example.hybridflow.dto.ScheduleDiscardRequestDTO;
import com.example.hybridflow.dto.ScheduleDiscardResponseDTO;
import com.example.hybridflow.dto.ScheduleGenerationRequestDTO;
import com.example.hybridflow.dto.ScheduleGenerationResponseDTO;
import com.example.hybridflow.dto.SchedulePublishRequestDTO;
import com.example.hybridflow.dto.SchedulePublishResponseDTO;
import com.example.hybridflow.dto.UnpublishedSchedulesResponseDTO;
import com.example.hybridflow.entity.User;
import com.example.hybridflow.security.CustomUserDetails;
import com.example.hybridflow.service.ScheduleGenerationService;
import com.example.hybridflow.service.ScheduleManagementService;
import com.example.hybridflow.service.ScheduleTeamAvailabilityService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
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

    // ── PRE-GENERATION SEQUENCE ───────────────────────────────────────────────

    /**
     * STEP 1 — Review existing unpublished (draft) schedules.
     *
     * GET /api/schedules/unpublished
     *
     * Returns all draft schedules for the authenticated HR user's company,
     * together with their fairness scores. Use this to decide whether to
     * publish them or clear them before starting a new generation run.
     *
     * Response includes:
     * - count total draft schedules
     * - overallFairnessScore aggregate score (0–100)
     * - schedules[] one entry per team/schedule with:
     * scheduleId, teamId, teamName, officeId, officeName,
     * startDate, endDate, createdAt,
     * teamFairnessScore { score, breakdown }
     * individualFairnessScores[] { userId, userEmail, score, breakdown }
     */
    @GetMapping("/unpublished")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<UnpublishedSchedulesResponseDTO> getUnpublishedSchedules(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        if (userDetails == null)
            return ResponseEntity.status(401).build();

        return ResponseEntity.ok(
                scheduleManagementService.getUnpublishedSchedules(userDetails.getUser()));
    }

    /**
     * STEP 2 — Clear ALL unpublished schedules (optional, but recommended before
     * generating a fresh set).
     *
     * DELETE /api/schedules/unpublished
     *
     * Permanently deletes every draft schedule and its entries for the HR user's
     * company. Published schedules are never affected.
     *
     * Returns:
     * - status "SUCCESS" | "NO_OP"
     * - message human-readable description
     * - deletedScheduleIds[] IDs of the removed schedule records
     */
    @DeleteMapping("/unpublished")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<DeleteUnpublishedSchedulesResponseDTO> deleteUnpublishedSchedules(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        if (userDetails == null)
            return ResponseEntity.status(401).build();

        return ResponseEntity.ok(
                scheduleManagementService.deleteAllUnpublishedSchedules(userDetails.getUser()));
    }

    /**
     * STEP 3 — Check which teams are available for a new generation run.
     *
     * GET /api/schedules/available-teams
     */
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
                        officeId,
                        startDate,
                        endDate,
                        currentUser));
    }

    // ── GENERATION ────────────────────────────────────────────────────────────

    /**
     * STEP 4 — Generate schedules.
     *
     * POST /api/schedules/generate
     */
    @PostMapping("/generate")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<ScheduleGenerationResponseDTO> generateSchedule(
            @Valid @RequestBody ScheduleGenerationRequestDTO request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        User currentUser = userDetails.getUser();
        return ResponseEntity.ok(scheduleGenerationService.generateSchedule(request, currentUser));
    }

    // ── EXISTING ENDPOINTS (unchanged) ───────────────────────────────────────

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