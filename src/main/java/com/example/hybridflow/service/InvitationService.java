package com.example.hybridflow.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.hybridflow.dto.InvitationResponseDTO;
import com.example.hybridflow.entity.Company;
import com.example.hybridflow.entity.Invitation;
import com.example.hybridflow.entity.Role;
import com.example.hybridflow.entity.Team;
import com.example.hybridflow.entity.User;
import com.example.hybridflow.exception.BusinessValidationException;
import com.example.hybridflow.repository.InvitationRepository;
import com.example.hybridflow.repository.UserRepository;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class InvitationService {

    private final InvitationRepository invitationRepository;
    private final EmailService emailService;
    private final OtpService otpService;
    private final UserRepository userRepository;

    public InvitationService(
            InvitationRepository invitationRepository,
            EmailService emailService,
            OtpService otpService,
            UserRepository userRepository) {
        this.invitationRepository = invitationRepository;
        this.emailService = emailService;
        this.otpService = otpService;
        this.userRepository = userRepository;
    }

    public void createAndSendInvitation(String email, Role role, Team team, Company company) {
        if (userRepository.existsByEmail(email)) {
            throw new BusinessValidationException("A user with this email is already registered.");
        }

        if (invitationRepository.existsByEmailAndUsedFalse(email)) {
            throw new BusinessValidationException("An active invitation has already been sent to this email.");
        }

        // Strict Manager Check: A team can only have one manager
        if (role == Role.MANAGER && team != null && team.getManager() != null) {
            throw new BusinessValidationException("Team '" + team.getName() + "' already has a designated manager.");
        }

        String token = otpService.generateOtp();

        Invitation invitation = new Invitation();
        invitation.setEmail(email);
        invitation.setToken(token);
        invitation.setRole(role);
        invitation.setTeam(team);
        invitation.setCompany(company);
        invitation.setExpiryDate(Instant.now().plusSeconds(86400)); // 24 hours

        invitationRepository.save(invitation);
        emailService.sendInvitationEmail(email, token, role.name());
    }

    @Transactional(readOnly = true)
    public List<InvitationResponseDTO> getPendingInvitationsForCompany(User hrUser) {
        Company company = hrUser.getCompany();
        if (company == null) {
            throw new BusinessValidationException("HR user is not assigned to a company.");
        }

        List<Invitation> pendingInvitations = invitationRepository.findByCompanyIdAndUsedFalseAndExpiryDateAfter(
                company.getId(),
                Instant.now()
        );

        return pendingInvitations.stream()
                .map(this::toResponseDTO)
                .collect(Collectors.toList());
    }

    public Invitation validateToken(String token) {
        return invitationRepository.findByTokenAndUsedFalse(token)
                .filter(inv -> inv.getExpiryDate().isAfter(Instant.now()))
                .orElseThrow(() -> new BusinessValidationException("Invalid or expired invitation code."));
    }

    public void markAsUsed(Invitation invitation) {
        invitation.setUsed(true);
        invitationRepository.save(invitation);
    }

    private InvitationResponseDTO toResponseDTO(Invitation invitation) {
        return InvitationResponseDTO.builder()
                .email(invitation.getEmail())
                .role(invitation.getRole())
                .teamId(invitation.getTeam() != null ? invitation.getTeam().getId() : null)
                .teamName(invitation.getTeam() != null ? invitation.getTeam().getName() : null)
                .companyId(invitation.getCompany() != null ? invitation.getCompany().getId() : null)
                .companyName(invitation.getCompany() != null ? invitation.getCompany().getCompanyName() : null)
                .expiryDate(invitation.getExpiryDate())
                .build();
    }
}