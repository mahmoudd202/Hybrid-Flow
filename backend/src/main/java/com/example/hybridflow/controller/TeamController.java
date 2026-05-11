package com.example.hybridflow.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.example.hybridflow.dto.TeamCreateRequestDTO;
import com.example.hybridflow.dto.TeamResponseDTO;
import com.example.hybridflow.security.CustomUserDetails;
import com.example.hybridflow.service.TeamService;

@RestController
@RequestMapping("/api/teams")
@RequiredArgsConstructor
public class TeamController {

    private final TeamService teamService;

    @PostMapping
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<TeamResponseDTO> createTeam(
            @Valid @RequestBody TeamCreateRequestDTO dto,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        if (userDetails == null) return ResponseEntity.status(401).build();

        TeamResponseDTO response = teamService.createTeam(dto, userDetails.getUser());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    @GetMapping("/office/{officeId}")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<List<TeamResponseDTO>> getTeamsByOffice(
            @PathVariable Long officeId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        if (userDetails == null) {
            return ResponseEntity.status(401).build();
        }

        List<TeamResponseDTO> teams = teamService.getTeamsByOffice(officeId, userDetails.getUser());
        return ResponseEntity.ok(teams);
    }
}