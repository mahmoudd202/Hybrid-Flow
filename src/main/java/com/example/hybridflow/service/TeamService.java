package com.example.hybridflow.service;

import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.example.hybridflow.dto.TeamCreateRequestDTO;
import com.example.hybridflow.dto.TeamResponseDTO;
import com.example.hybridflow.entity.Company;
import com.example.hybridflow.entity.Office;
import com.example.hybridflow.entity.Team;
import com.example.hybridflow.entity.User;
import com.example.hybridflow.exception.BusinessValidationException;
import com.example.hybridflow.repository.OfficeRepository;
import com.example.hybridflow.repository.TeamRepository;

@Service
@RequiredArgsConstructor
public class TeamService {

    private final TeamRepository teamRepository;
    private final OfficeRepository officeRepository;

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
