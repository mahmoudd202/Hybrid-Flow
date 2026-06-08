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
import com.example.hybridflow.exception.ResourceNotFoundException;
import com.example.hybridflow.repository.InvitationRepository;
import com.example.hybridflow.repository.UserRepository;
import com.example.hybridflow.repository.TeamRepository;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class InvitationService {

    private final InvitationRepository invitationRepository;
    private final EmailService emailService;
    private final UserRepository userRepository;
    private final TeamRepository teamRepository;

    public InvitationService(
            InvitationRepository invitationRepository,
            EmailService emailService,
            UserRepository userRepository,
            TeamRepository teamRepository) {
        this.invitationRepository = invitationRepository;
        this.emailService = emailService;
        this.userRepository = userRepository;
        this.teamRepository = teamRepository;
    }

    public Invitation createAndSendInvitation(String email, Role role, Team team, Company company) {
        if (userRepository.existsByEmail(email)) {
            throw new BusinessValidationException("A user with this email is already registered.");
        }

        if (invitationRepository.existsByEmailAndUsedFalse(email)) {
            throw new BusinessValidationException("An active invitation has already been sent to this email.");
        }

        // Strict Manager Check: A team can only have one manager.
        // If a new manager is invited to a team that already has one, demote the old manager to an employee.
        if (role == Role.MANAGER && team != null) {
            if (team.getManager() != null) {
                User oldManager = team.getManager();
                oldManager.setRole(Role.EMPLOYEE);
                userRepository.save(oldManager);

                team.setManager(null);
                teamRepository.save(team);
            }

            // Also demote any pending manager invitations for this team to EMPLOYEE
            List<Invitation> pendingManagerInvites = invitationRepository
                    .findByTeamIdAndRoleAndUsedFalseAndExpiryDateAfter(team.getId(), Role.MANAGER, Instant.now());
            for (Invitation pendingInvite : pendingManagerInvites) {
                pendingInvite.setRole(Role.EMPLOYEE);
                invitationRepository.save(pendingInvite);
            }
        }

        Invitation invitation = new Invitation();
        invitation.setEmail(email);
        invitation.setRole(role);
        invitation.setTeam(team);
        invitation.setCompany(company);
        invitation.setExpiryDate(Instant.now().plusSeconds(86400)); // 24 hours

        Invitation saved = invitationRepository.save(invitation);
        emailService.sendInvitationEmail(email, role.name());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<InvitationResponseDTO> getPendingInvitationsForCompany(User hrUser) {
        Company company = hrUser.getCompany();
        if (company == null) {
            throw new BusinessValidationException("HR user is not assigned to a company.");
        }

        List<Invitation> pendingInvitations = invitationRepository.findByCompanyIdAndUsedFalseAndExpiryDateAfter(
                company.getId(),
                Instant.now());

        return pendingInvitations.stream()
                .map(this::toResponseDTO)
                .collect(Collectors.toList());
    }

    public void markAsUsed(Invitation invitation) {
        invitation.setUsed(true);
        invitationRepository.save(invitation);
    }

    @Transactional
    public InvitationResponseDTO resendInvitation(Long id, User hrUser) {
        Company company = hrUser.getCompany();
        if (company == null) {
            throw new BusinessValidationException("HR user is not assigned to a company.");
        }

        Invitation invitation = invitationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Invitation not found."));

        if (!invitation.getCompany().getId().equals(company.getId())) {
            throw new BusinessValidationException("You cannot manage an invitation belonging to another company.");
        }

        if (invitation.isUsed()) {
            throw new BusinessValidationException("Invitation has already been used.");
        }

        invitation.setExpiryDate(Instant.now().plusSeconds(86400)); // 24 hours
        Invitation saved = invitationRepository.save(invitation);

        emailService.sendInvitationEmail(saved.getEmail(), saved.getRole().name());

        InvitationResponseDTO dto = toResponseDTO(saved);
        dto.setMessage("Invitation resent successfully.");
        return dto;
    }

    @Transactional
    public void cancelInvitation(Long id, User hrUser) {
        Company company = hrUser.getCompany();
        if (company == null) {
            throw new BusinessValidationException("HR user is not assigned to a company.");
        }

        Invitation invitation = invitationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Invitation not found."));

        if (!invitation.getCompany().getId().equals(company.getId())) {
            throw new BusinessValidationException("You cannot manage an invitation belonging to another company.");
        }

        invitationRepository.delete(invitation);
    }

    @Transactional
    public InvitationResponseDTO expireInvitation(Long id, User hrUser) {
        Company company = hrUser.getCompany();
        if (company == null) {
            throw new BusinessValidationException("HR user is not assigned to a company.");
        }

        Invitation invitation = invitationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Invitation not found."));

        if (!invitation.getCompany().getId().equals(company.getId())) {
            throw new BusinessValidationException("You cannot manage an invitation belonging to another company.");
        }

        invitation.setExpiryDate(Instant.now().minusSeconds(1)); // Expire immediately
        Invitation saved = invitationRepository.save(invitation);

        InvitationResponseDTO dto = toResponseDTO(saved);
        dto.setMessage("Invitation expired successfully.");
        return dto;
    }

    private InvitationResponseDTO toResponseDTO(Invitation invitation) {
        return InvitationResponseDTO.builder()
                .id(invitation.getId())
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
