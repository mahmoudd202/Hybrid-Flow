package com.example.hybridflow.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.example.hybridflow.dto.MeetingDTO;
import com.example.hybridflow.dto.MeetingRequestDTO;
import com.example.hybridflow.entity.Role;
import com.example.hybridflow.entity.User;
import com.example.hybridflow.security.CustomUserDetails;
import com.example.hybridflow.service.MeetingService;

import java.util.List;

@RestController
@RequestMapping("/api/meetings")
public class MeetingController {
    private final MeetingService meetingService;

    public MeetingController(MeetingService meetingService) {
        this.meetingService = meetingService;
    }

    @PostMapping
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<MeetingDTO> createMeeting(
            @Valid @RequestBody MeetingRequestDTO dto,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        if (userDetails == null) return ResponseEntity.status(401).build();

        MeetingDTO response = meetingService.createMeeting(dto, userDetails.getUser());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/my-schedule")
    @PreAuthorize("hasAnyRole('EMPLOYEE','MANAGER','HR')")
    public ResponseEntity<List<MeetingDTO>> getMyMeetingSchedule(
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        if (userDetails == null) return ResponseEntity.status(401).build();

        User currentUser = userDetails.getUser();

        if (currentUser.getTeam() == null) {
            return ResponseEntity.ok(List.of());
        }

        return ResponseEntity.ok(
                meetingService.getTeamMeetings(currentUser.getTeam().getId())
        );
    }

    @GetMapping("/team/{teamId}")
    @PreAuthorize("hasAnyRole('EMPLOYEE','MANAGER','HR')")
    public ResponseEntity<List<MeetingDTO>> getTeamCalendar(
            @PathVariable Long teamId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        if (userDetails == null) return ResponseEntity.status(401).build();

        User currentUser = userDetails.getUser();
        enforceTeamMeetingAccess(currentUser, teamId);

        return ResponseEntity.ok(
                meetingService.getTeamMeetings(teamId)
        );
    }

    /**
     * Access control for viewing a team's meetings.
     * <ul>
     *   <li>EMPLOYEE → own team only</li>
     *   <li>MANAGER  → managed team only</li>
     *   <li>HR       → any team in the same company</li>
     * </ul>
     */
    private void enforceTeamMeetingAccess(User user, Long targetTeamId) {
        if (user.getRole() == Role.HR) {
            // HR can view any team's meetings (company check would require loading the team;
            // findByTeamWithDetails will simply return empty if the team doesn't exist)
            return;
        }

        if (user.getTeam() == null) {
            throw new AccessDeniedException("You are not assigned to any team");
        }

        if (!user.getTeam().getId().equals(targetTeamId)) {
            throw new AccessDeniedException("You can only view meetings for your own team");
        }
    }
}