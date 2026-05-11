package com.example.hybridflow.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.RestController;

import com.example.hybridflow.dto.InvitationRequestDTO;
import com.example.hybridflow.dto.InvitationResponseDTO;
import com.example.hybridflow.entity.Company;
import com.example.hybridflow.entity.Role;
import com.example.hybridflow.entity.Team;
import com.example.hybridflow.exception.BusinessValidationException;
import com.example.hybridflow.repository.TeamRepository;
import com.example.hybridflow.security.CustomUserDetails;
import com.example.hybridflow.service.InvitationService;

import org.springframework.web.bind.annotation.RequestMapping;

@RestController
@RequestMapping("/api/invitations")
public class InvitationController {

    private final InvitationService invitationService;
    private final TeamRepository teamRepository;

    public InvitationController(InvitationService invitationService,
                                TeamRepository teamRepository) {
        this.invitationService = invitationService;
        this.teamRepository = teamRepository;
    }

    @PostMapping("/send")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<InvitationResponseDTO> sendInvitation(
            @Valid @RequestBody InvitationRequestDTO dto,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        if (userDetails == null) return ResponseEntity.status(401).build();

        Company company = userDetails.getUser().getCompany();

        if (company == null) {
            throw new BusinessValidationException("HR user is not assigned to a company.");
        }

        Team team = teamRepository.findById(dto.getTeamId())
                .orElseThrow(() -> new BusinessValidationException("Team not found."));

        if (!team.getCompany().getId().equals(company.getId())) {
            throw new BusinessValidationException("You cannot invite users to another company's team.");
        }

        
        invitationService.createAndSendInvitation(
                dto.getEmail(),
                dto.getRole(),
                team,
                company
        );

        InvitationResponseDTO response = InvitationResponseDTO.builder()
                .email(dto.getEmail())
                .role(dto.getRole())
                .teamId(team.getId())
                .teamName(team.getName())
                .companyId(company.getId())
                .companyName(company.getCompanyName())
                .message("Invitation sent successfully.")
                .build();

        return ResponseEntity.ok(response);
    }

    @GetMapping("/pending")
    @PreAuthorize("hasRole(\'HR\')")
    public ResponseEntity<List<InvitationResponseDTO>> getPendingInvitationsForCompany(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        List<InvitationResponseDTO> pendingInvitations = invitationService.getPendingInvitationsForCompany(userDetails.getUser());
        return ResponseEntity.ok(pendingInvitations);
    }

    
}
