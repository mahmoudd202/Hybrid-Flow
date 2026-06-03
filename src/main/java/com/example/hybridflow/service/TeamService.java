package com.example.hybridflow.service;

import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.example.hybridflow.dto.TeamCreateRequestDTO;
import com.example.hybridflow.dto.TeamResponseDTO;
import com.example.hybridflow.dto.EmployeeDetailsResponseDTO;
import com.example.hybridflow.entity.Company;
import com.example.hybridflow.entity.Office;
import com.example.hybridflow.entity.Team;
import com.example.hybridflow.entity.User;
import com.example.hybridflow.entity.UserProfile;
import com.example.hybridflow.exception.BusinessValidationException;
import com.example.hybridflow.exception.ResourceNotFoundException;
import com.example.hybridflow.repository.OfficeRepository;
import com.example.hybridflow.repository.TeamRepository;
import com.example.hybridflow.repository.UserRepository;

@Service
@RequiredArgsConstructor
public class TeamService {

    private final TeamRepository teamRepository;
    private final OfficeRepository officeRepository;
    private final UserRepository userRepository;

    @Transactional
    public TeamResponseDTO createTeam(TeamCreateRequestDTO dto, User hrUser) {

        Company company = hrUser.getCompany();
        if (company == null) {
            throw new BusinessValidationException("HR user is not assigned to a company.");
        }

        // Prevent duplicate team names within the same company
        if (teamRepository.findByNameAndCompanyId(dto.getName().trim(), company.getId()).isPresent()) {
            throw new BusinessValidationException(
                    "A team with the name '" + dto.getName().trim() + "' already exists in your company.");
        }

        Office office = null;
        if (dto.getOfficeId() != null) {
            office = officeRepository.findById(dto.getOfficeId())
                    .orElseThrow(() -> new BusinessValidationException("Office not found."));

            // Make sure the office belongs to the HR's company
            if (!office.getCompany().getId().equals(company.getId())) {
                throw new BusinessValidationException("You cannot assign a team to another company's office.");
            }
        }

        Team team = new Team();
        team.setName(dto.getName().trim());
        team.setCompany(company);
        team.setOffice(office); // null is fine — assign later
        Team saved = teamRepository.save(team);

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<TeamResponseDTO> getTeamsByOffice(Long officeId, User hrUser) {
        Company company = hrUser.getCompany();
        if (company == null) {
            throw new BusinessValidationException("HR user is not assigned to a company.");
        }

        // Validate that the office exists
        Office office = officeRepository.findById(officeId)
                .orElseThrow(() -> new BusinessValidationException("Office not found."));

        // Validate that the office belongs to the HR user's company
        if (!office.getCompany().getId().equals(company.getId())) {
            throw new BusinessValidationException("You cannot access teams from another company's office.");
        }

        // Fetch teams and map to DTOs
        List<Team> teams = teamRepository.findByOfficeId(officeId);

        return teams.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<TeamResponseDTO> getTeamsByCompany(User hrUser) {
        Company company = hrUser.getCompany();
        if (company == null) {
            throw new BusinessValidationException("HR user is not assigned to a company.");
        }

        List<Team> teams = teamRepository.findByCompanyIdOrderByNameAsc(company.getId());

        return teams.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public TeamResponseDTO updateTeam(Long teamId, TeamCreateRequestDTO dto, User hrUser) {
        Company company = hrUser.getCompany();
        if (company == null) {
            throw new BusinessValidationException("HR user is not assigned to a company.");
        }

        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found."));

        if (!team.getCompany().getId().equals(company.getId())) {
            throw new BusinessValidationException("You cannot update a team belonging to another company.");
        }

        String trimmedName = dto.getName().trim();

        // Check for duplicate name (excluding the current team being updated)
        teamRepository.findByNameAndCompanyId(trimmedName, company.getId())
                .ifPresent(existing -> {
                    if (!existing.getId().equals(teamId)) {
                        throw new BusinessValidationException(
                                "A team with the name '" + trimmedName + "' already exists in your company.");
                    }
                });

        Office office = null;
        if (dto.getOfficeId() != null) {
            office = officeRepository.findById(dto.getOfficeId())
                    .orElseThrow(() -> new ResourceNotFoundException("Office not found."));

            if (!office.getCompany().getId().equals(company.getId())) {
                throw new BusinessValidationException("You cannot assign a team to another company's office.");
            }
        }

        team.setName(trimmedName);
        team.setOffice(office);

        Team updated = teamRepository.save(team);
        return toResponse(updated);
    }

    @Transactional
    public void deleteTeam(Long teamId, User hrUser) {
        Company company = hrUser.getCompany();
        if (company == null) {
            throw new BusinessValidationException("HR user is not assigned to a company.");
        }

        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found."));

        if (!team.getCompany().getId().equals(company.getId())) {
            throw new BusinessValidationException("You cannot delete a team belonging to another company.");
        }

        // Check if there are employees assigned to this team
        if (!userRepository.findAllByTeamId(teamId).isEmpty()) {
            throw new BusinessValidationException("Cannot delete team because it is currently assigned to one or more employees.");
        }

        teamRepository.delete(team);
    }

    @Transactional(readOnly = true)
    public List<EmployeeDetailsResponseDTO> getTeamMembers(Long teamId, User currentUser) {
        Company company = currentUser.getCompany();
        if (company == null) {
            throw new BusinessValidationException("You are not assigned to a company.");
        }

        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found."));

        if (!team.getCompany().getId().equals(company.getId())) {
            throw new BusinessValidationException("You do not have access to this team.");
        }

        List<User> members = userRepository.findAllByTeamIdWithProfileAndTeam(teamId);

        return members.stream()
                .map(this::toEmployeeDetailsDTO)
                .toList();
    }

    private EmployeeDetailsResponseDTO toEmployeeDetailsDTO(User employee) {
        UserProfile userProfile = employee.getProfile();
        EmployeeDetailsResponseDTO.EmployeeDetailsResponseDTOBuilder builder = EmployeeDetailsResponseDTO.builder()
                .id(employee.getId())
                .email(employee.getEmail())
                .enabled(employee.isEnabled())
                .role(employee.getRole());

        if (userProfile != null) {
            builder.firstName(userProfile.getFirstName())
                    .lastName(userProfile.getLastName())
                    .dateOfBirth(userProfile.getDateOfBirth())
                    .nationality(userProfile.getNationality());
        }

        if (employee.getTeam() != null) {
            builder.team(EmployeeDetailsResponseDTO.TeamInfoDTO.builder()
                    .id(employee.getTeam().getId())
                    .name(employee.getTeam().getName())
                    .build());
        }

        if (employee.getTeam() != null && employee.getTeam().getOffice() != null) {
            builder.office(EmployeeDetailsResponseDTO.OfficeInfoDTO.builder()
                    .id(employee.getTeam().getOffice().getId())
                    .name(employee.getTeam().getOffice().getName())
                    .build());
        }

        return builder.build();
    }

    private TeamResponseDTO toResponse(Team team) {
        return TeamResponseDTO.builder()
                .id(team.getId())
                .name(team.getName())
                .companyId(team.getCompany().getId())
                .companyName(team.getCompany().getCompanyName())
                .officeId(team.getOffice() != null ? team.getOffice().getId() : null)
                .officeName(team.getOffice() != null ? team.getOffice().getName() : null)
                .build();
    }
}
