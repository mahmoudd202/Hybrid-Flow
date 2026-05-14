package com.example.hybridflow.service;

import com.example.hybridflow.dto.AvailableScheduleTeamDTO;
import com.example.hybridflow.dto.AvailableScheduleTeamsResponseDTO;
import com.example.hybridflow.dto.UnavailableScheduleTeamDTO;
import com.example.hybridflow.entity.Office;
import com.example.hybridflow.entity.Schedule;
import com.example.hybridflow.entity.Team;
import com.example.hybridflow.entity.User;
import com.example.hybridflow.exception.BusinessValidationException;
import com.example.hybridflow.exception.ResourceNotFoundException;
import com.example.hybridflow.repository.OfficeRepository;
import com.example.hybridflow.repository.ScheduleRepository;
import com.example.hybridflow.repository.TeamRepository;
import com.example.hybridflow.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ScheduleTeamAvailabilityService {

    private final OfficeRepository officeRepository;
    private final TeamRepository teamRepository;
    private final ScheduleRepository scheduleRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public AvailableScheduleTeamsResponseDTO getAvailableTeams(
            Long officeId,
            LocalDate startDate,
            LocalDate endDate,
            User hrUser) {
        validateRequest(officeId, startDate, endDate, hrUser);

        Long companyId = hrUser.getCompany().getId();

        Office office = officeRepository.findById(officeId)
                .orElseThrow(() -> new ResourceNotFoundException("Office not found."));

        if (office.getCompany() == null || !office.getCompany().getId().equals(companyId)) {
            throw new AccessDeniedException("You can only generate schedules for offices in your company.");
        }

        /*
         * Correct business rule:
         * HR can choose ANY team in the company.
         * Do not filter teams by officeId.
         */
        List<Team> teams = teamRepository.findByCompanyIdOrderByNameAsc(companyId);

        List<AvailableScheduleTeamDTO> availableTeams = new ArrayList<>();
        List<UnavailableScheduleTeamDTO> unavailableTeams = new ArrayList<>();

        for (Team team : teams) {
            int memberCount = userRepository.countSchedulableUsersByTeamId(team.getId());

            List<Schedule> conflictingSchedules = scheduleRepository.findPublishedForTeamInRange(
                    team.getId(),
                    startDate,
                    endDate);

            if (memberCount == 0) {
                unavailableTeams.add(
                        UnavailableScheduleTeamDTO.builder()
                                .teamId(team.getId())
                                .teamName(team.getName())
                                .memberCount(memberCount)
                                .reason("Team has no active schedulable employees or managers.")
                                .conflictingScheduleIds(List.of())
                                .build());
                continue;
            }

            if (!conflictingSchedules.isEmpty()) {
                unavailableTeams.add(
                        UnavailableScheduleTeamDTO.builder()
                                .teamId(team.getId())
                                .teamName(team.getName())
                                .memberCount(memberCount)
                                .reason("Team already has a published schedule in this date range.")
                                .conflictingScheduleIds(
                                        conflictingSchedules.stream()
                                                .map(Schedule::getId)
                                                .toList())
                                .build());
                continue;
            }

            availableTeams.add(
                    AvailableScheduleTeamDTO.builder()
                            .teamId(team.getId())
                            .teamName(team.getName())
                            .memberCount(memberCount)
                            .build());
        }

        return AvailableScheduleTeamsResponseDTO.builder()
                .officeId(officeId)
                .startDate(startDate)
                .endDate(endDate)
                .availableTeams(availableTeams)
                .unavailableTeams(unavailableTeams)
                .build();
    }

    private void validateRequest(
            Long officeId,
            LocalDate startDate,
            LocalDate endDate,
            User hrUser) {
        if (hrUser == null) {
            throw new AccessDeniedException("Unauthenticated.");
        }

        if (hrUser.getCompany() == null) {
            throw new AccessDeniedException("HR user is not assigned to a company.");
        }

        if (officeId == null) {
            throw new BusinessValidationException("officeId is required.");
        }

        if (startDate == null || endDate == null) {
            throw new BusinessValidationException("startDate and endDate are required.");
        }

        if (endDate.isBefore(startDate)) {
            throw new BusinessValidationException("endDate must be on or after startDate.");
        }
    }
}