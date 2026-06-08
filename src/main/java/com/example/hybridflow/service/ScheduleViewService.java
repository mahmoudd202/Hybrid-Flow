package com.example.hybridflow.service;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.hybridflow.dto.*;
import com.example.hybridflow.entity.*;
import com.example.hybridflow.exception.ResourceNotFoundException;
import com.example.hybridflow.repository.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Central service for the schedule-viewing feature.
 * <p>
 * Access rules:
 * <ul>
 * <li><b>EMPLOYEE</b> – can view their own team's schedule (or personal
 * only)</li>
 * <li><b>MANAGER</b> – can view their managed team's schedule</li>
 * <li><b>HR</b> – can view every team's schedule across the entire company</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class ScheduleViewService {

    private final ScheduleEntryRepository scheduleEntryRepository;
    private final MeetingRepository meetingRepository;
    private final TeamRepository teamRepository;
    private final UserRepository userRepository;
    private final OfficeRepository officeRepository;

    public ScheduleViewService(
            ScheduleEntryRepository scheduleEntryRepository,
            MeetingRepository meetingRepository,
            TeamRepository teamRepository,
            UserRepository userRepository,
            OfficeRepository officeRepository) {
        this.scheduleEntryRepository = scheduleEntryRepository;
        this.meetingRepository = meetingRepository;
        this.teamRepository = teamRepository;
        this.userRepository = userRepository;
        this.officeRepository = officeRepository;
    }

    public ScheduleViewResponseDTO getMySchedule(LocalDate from, LocalDate to, User requester) {
        validateDateRange(from, to);
        User currentUser = getFreshUser(requester);
        validateUserHasTeam(currentUser);

        List<ScheduleEntry> entries = scheduleEntryRepository.findPublishedEntriesForUser(
                currentUser.getId(), from, to);

        UserScheduleDTO userRow = buildUserRow(currentUser, entries);

        // Meetings are scoped to the user's team for the date range
        List<MeetingDTO> meetings = fetchTeamMeetings(currentUser.getTeam().getId(), from, to);

        TeamScheduleDTO teamDto = new TeamScheduleDTO();
        teamDto.setTeamId(currentUser.getTeam().getId());
        teamDto.setTeamName(currentUser.getTeam().getName());
        teamDto.setOfficeName(resolveOfficeName(currentUser.getTeam()));
        teamDto.setRangeStart(from);
        teamDto.setRangeEnd(to);
        teamDto.setMembers(List.of(userRow));
        teamDto.setMeetings(meetings);

        return buildResponse(from, to, List.of(teamDto));
    }

    public ScheduleViewResponseDTO getEmployeeSchedule(Long targetEmployeeId, LocalDate from, LocalDate to,
            User requester) {
        validateDateRange(from, to);
        User currentUser = getFreshUser(requester);
        User targetEmployee = userRepository.findById(targetEmployeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));

        enforceEmployeeAccess(currentUser, targetEmployee);

        List<ScheduleEntry> entries = scheduleEntryRepository.findPublishedEntriesForUser(
                targetEmployee.getId(), from, to);

        UserScheduleDTO userRow = buildUserRow(targetEmployee, entries);

        // Meetings scoped to the target employee's team
        List<MeetingDTO> meetings = targetEmployee.getTeam() != null
                ? fetchTeamMeetings(targetEmployee.getTeam().getId(), from, to)
                : List.of();

        TeamScheduleDTO teamDto = new TeamScheduleDTO();
        teamDto.setTeamId(targetEmployee.getTeam() != null ? targetEmployee.getTeam().getId() : null);
        teamDto.setTeamName(targetEmployee.getTeam() != null ? targetEmployee.getTeam().getName() : null);
        teamDto.setOfficeName(targetEmployee.getTeam() != null ? resolveOfficeName(targetEmployee.getTeam()) : "N/A");
        teamDto.setRangeStart(from);
        teamDto.setRangeEnd(to);
        teamDto.setMembers(List.of(userRow));
        teamDto.setMeetings(meetings);

        return buildResponse(from, to, List.of(teamDto));
    }

    public ScheduleViewResponseDTO getTeamSchedule(Long teamId, LocalDate from, LocalDate to, User requester) {
        validateDateRange(from, to);
        User currentUser = getFreshUser(requester);

        Team targetTeam = teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found"));

        enforceTeamAccess(currentUser, targetTeam);

        TeamScheduleDTO teamDto = buildTeamSchedule(targetTeam, from, to);
        return buildResponse(from, to, List.of(teamDto));
    }

    public ScheduleViewResponseDTO getCompanySchedule(LocalDate from, LocalDate to, User requester) {
        validateDateRange(from, to);
        User currentUser = getFreshUser(requester);

        if (currentUser.getRole() != Role.HR && currentUser.getRole() != Role.MANAGER) {
            throw new AccessDeniedException("Only HR or managers can view the company-wide schedule");
        }
        if (currentUser.getCompany() == null) {
            throw new AccessDeniedException("You are not attached to a company");
        }

        Long companyId = currentUser.getCompany().getId();

        // Fetch all teams in the company
        List<Team> teams = teamRepository.findByCompanyId(companyId);

        // Fetch all entries for the company in one query
        List<ScheduleEntry> allEntries = scheduleEntryRepository.findPublishedEntriesForCompany(
                companyId, from, to);

        // Fetch all meetings for the company in one query
        List<Meeting> allMeetings = meetingRepository.findCompanyMeetingsInRange(
                companyId,
                from.atStartOfDay(),
                to.plusDays(1).atStartOfDay());

        // Group entries by team
        Map<Long, List<ScheduleEntry>> entriesByTeam = allEntries.stream()
                .collect(Collectors.groupingBy(
                        se -> se.getSchedule().getTeam().getId()));

        // Group meetings by team (a meeting can belong to multiple teams)
        Map<Long, List<Meeting>> meetingsByTeam = new HashMap<>();
        for (Meeting m : allMeetings) {
            for (Team t : m.getParticipatingTeams()) {
                meetingsByTeam.computeIfAbsent(t.getId(), k -> new ArrayList<>()).add(m);
            }
        }

        List<TeamScheduleDTO> teamDtos = new ArrayList<>();
        for (Team team : teams) {
            List<ScheduleEntry> teamEntries = entriesByTeam.getOrDefault(team.getId(), List.of());
            List<Meeting> teamMeetings = meetingsByTeam.getOrDefault(team.getId(), List.of());

            TeamScheduleDTO dto = buildTeamScheduleFromData(team, from, to, teamEntries, teamMeetings);
            teamDtos.add(dto);
        }

        return buildResponse(from, to, teamDtos);
    }

    public ScheduleViewResponseDTO getOfficeSchedule(Long officeId, LocalDate from, LocalDate to, User requester) {
        validateDateRange(from, to);
        User currentUser = getFreshUser(requester);

        if (currentUser.getRole() != Role.HR) {
            throw new AccessDeniedException("Only HR can view the office-wide schedule");
        }
        if (currentUser.getCompany() == null) {
            throw new AccessDeniedException("You are not attached to a company");
        }

        Office office = officeRepository.findById(officeId)
                .orElseThrow(() -> new ResourceNotFoundException("Office not found"));

        // Ensure the office belongs to the HR's company
        if (!office.getCompany().getId().equals(currentUser.getCompany().getId())) {
            throw new AccessDeniedException("You cannot view schedules of an office outside your company");
        }

        // All teams in this office
        List<Team> officeTeams = teamRepository.findByOfficeId(officeId);

        // Batch-fetch entries and meetings for this office
        List<ScheduleEntry> allEntries = scheduleEntryRepository.findPublishedEntriesForOffice(
                officeId, from, to);

        List<Meeting> allMeetings = meetingRepository.findOfficeMeetingsInRange(
                officeId,
                from.atStartOfDay(),
                to.plusDays(1).atStartOfDay());

        // Group entries by team
        Map<Long, List<ScheduleEntry>> entriesByTeam = allEntries.stream()
                .collect(Collectors.groupingBy(
                        se -> se.getSchedule().getTeam().getId()));

        // Group meetings by team
        Map<Long, List<Meeting>> meetingsByTeam = new HashMap<>();
        for (Meeting m : allMeetings) {
            for (Team t : m.getParticipatingTeams()) {
                // Only include if the team is actually in this office
                if (officeTeams.stream().anyMatch(ot -> ot.getId().equals(t.getId()))) {
                    meetingsByTeam.computeIfAbsent(t.getId(), k -> new ArrayList<>()).add(m);
                }
            }
        }

        List<TeamScheduleDTO> teamDtos = new ArrayList<>();
        for (Team team : officeTeams) {
            List<ScheduleEntry> teamEntries = entriesByTeam.getOrDefault(team.getId(), List.of());
            List<Meeting> teamMeetings = meetingsByTeam.getOrDefault(team.getId(), List.of());

            TeamScheduleDTO dto = buildTeamScheduleFromData(team, from, to, teamEntries, teamMeetings);
            teamDtos.add(dto);
        }

        return buildResponse(from, to, teamDtos);
    }

    private TeamScheduleDTO buildTeamSchedule(Team team, LocalDate from, LocalDate to) {
        List<ScheduleEntry> entries = scheduleEntryRepository.findPublishedEntriesForTeam(
                team.getId(), from, to);

        List<Meeting> meetings = meetingRepository.findTeamMeetingsInRange(
                team.getId(),
                from.atStartOfDay(),
                to.plusDays(1).atStartOfDay());

        return buildTeamScheduleFromData(team, from, to, entries, meetings);
    }

    private TeamScheduleDTO buildTeamScheduleFromData(
            Team team, LocalDate from, LocalDate to,
            List<ScheduleEntry> entries, List<Meeting> meetings) {
        // Group entries by user
        Map<Long, List<ScheduleEntry>> entriesByUser = entries.stream()
                .collect(Collectors.groupingBy(se -> se.getUser().getId()));

        // Build user rows — also include team members with no entries (so the grid
        // shows them)
        List<User> teamMembers = userRepository.findAllByTeamId(team.getId());
        List<UserScheduleDTO> memberDtos = new ArrayList<>();
        for (User member : teamMembers) {
            List<ScheduleEntry> userEntries = entriesByUser.getOrDefault(member.getId(), List.of());
            memberDtos.add(buildUserRow(member, userEntries));
        }

        // Sort: manager first, then alphabetically by email
        memberDtos.sort(Comparator
                .comparing((UserScheduleDTO u) -> !"MANAGER".equals(u.getRoleName()))
                .thenComparing(UserScheduleDTO::getEmail));

        List<MeetingDTO> meetingDtos = meetings.stream()
                .map(this::toMeetingDto)
                .collect(Collectors.toList());

        TeamScheduleDTO dto = new TeamScheduleDTO();
        dto.setTeamId(team.getId());
        dto.setTeamName(team.getName());
        dto.setOfficeName(resolveOfficeName(team));
        dto.setRangeStart(from);
        dto.setRangeEnd(to);
        dto.setMembers(memberDtos);
        dto.setMeetings(meetingDtos);
        return dto;
    }

    private UserScheduleDTO buildUserRow(User user, List<ScheduleEntry> entries) {
        List<ScheduleEntryDTO> entryDtos = entries.stream()
                .map(se -> {
                    String officeName = null;
                    if (se.getSchedule() != null && se.getSchedule().getOffice() != null) {
                        officeName = se.getSchedule().getOffice().getName();
                    }
                    return new ScheduleEntryDTO(se.getId(), se.getDate(), se.getWorkMode(), officeName);
                })
                .sorted(Comparator.comparing(ScheduleEntryDTO::getDate))
                .collect(Collectors.toList());

        String firstName = user.getProfile() != null ? user.getProfile().getFirstName() : null;
        String lastName = user.getProfile() != null ? user.getProfile().getLastName() : null;

        return new UserScheduleDTO(
                user.getId(),
                user.getEmail(),
                firstName,
                lastName,
                user.getRole().name(),
                entryDtos);
    }

    private MeetingDTO toMeetingDto(Meeting m) {
        List<String> teamNames = m.getParticipatingTeams() != null
                ? m.getParticipatingTeams()
                        .stream()
                        .map(Team::getName)
                        .collect(Collectors.toList())
                : List.of();

        return new MeetingDTO(
                m.getId(),
                m.getTitle(),
                m.getStartTime(),
                m.getEndTime(),
                m.getType(),
                m.getHost() != null ? m.getHost().getEmail() : null,
                m.getOffice() != null ? m.getOffice().getName() : null,
                teamNames,
                List.of());
    }

    private void validateDateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("'from' and 'to' date parameters are required");
        }
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("'to' must be on or after 'from'");
        }
    }

    private User getFreshUser(User requester) {
        if (requester == null || requester.getId() == null) {
            throw new AccessDeniedException("Unauthenticated");
        }
        return userRepository.findById(requester.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private void enforceEmployeeAccess(User requester, User targetEmployee) {
        Role role = requester.getRole();

        if (role == Role.HR) {
            if (requester.getCompany() == null || targetEmployee.getCompany() == null ||
                    !requester.getCompany().getId().equals(targetEmployee.getCompany().getId())) {
                throw new AccessDeniedException("HR can only view employees in the same company");
            }
            return;
        }

        if (role == Role.MANAGER) {
            validateManagerOwnsTeam(requester);
            if (targetEmployee.getTeam() == null ||
                    !targetEmployee.getTeam().getId().equals(requester.getTeam().getId())) {
                throw new AccessDeniedException("Manager can only view employees in the managed team");
            }
            return;
        }

        if (role == Role.EMPLOYEE) {
            // Self-view is always allowed
            if (targetEmployee.getId().equals(requester.getId())) {
                return;
            }
            // Same-team only
            if (requester.getTeam() == null || targetEmployee.getTeam() == null ||
                    !requester.getTeam().getId().equals(targetEmployee.getTeam().getId())) {
                throw new AccessDeniedException("Employee can only view own or same-team schedules");
            }
            return;
        }

        throw new AccessDeniedException("Role not allowed");
    }

    private void enforceTeamAccess(User user, Team targetTeam) {
        Role role = user.getRole();

        if (role == Role.HR) {
            // HR may view any team, but only within their own company
            if (user.getCompany() == null || targetTeam.getCompany() == null ||
                    !targetTeam.getCompany().getId().equals(user.getCompany().getId())) {
                throw new AccessDeniedException("You cannot view schedules of another company");
            }
            return;
        }

        if (role == Role.MANAGER) {
            validateManagerOwnsTeam(user);
            if (user.getTeam() == null ||
                    !user.getTeam().getId().equals(targetTeam.getId())) {
                throw new AccessDeniedException("You can only view your managed team's schedule");
            }
            return;
        }

        if (role == Role.EMPLOYEE) {
            if (user.getTeam() == null ||
                    !user.getTeam().getId().equals(targetTeam.getId())) {
                throw new AccessDeniedException("You can only view your own team's schedule");
            }
            return;
        }

        throw new AccessDeniedException("Role not allowed");
    }

    private void validateManagerOwnsTeam(User manager) {
        if (manager.getTeam() == null) {
            throw new AccessDeniedException("Manager is not attached to a team");
        }
        if (manager.getTeam().getManager() == null ||
                !manager.getTeam().getManager().getId().equals(manager.getId())) {
            throw new AccessDeniedException("You are not the actual manager of this team");
        }
    }

    private void validateUserHasTeam(User user) {
        if (user.getTeam() == null) {
            throw new AccessDeniedException("You are not assigned to any team yet");
        }
    }

    private String resolveOfficeName(Team team) {
        return team.getOffice() != null ? team.getOffice().getName() : "N/A";
    }

    private List<MeetingDTO> fetchTeamMeetings(Long teamId, LocalDate from, LocalDate to) {
        LocalDateTime rangeStart = from.atStartOfDay();
        LocalDateTime rangeEnd = to.plusDays(1).atStartOfDay();
        List<Meeting> meetings = meetingRepository.findTeamMeetingsInRange(teamId, rangeStart, rangeEnd);
        return meetings.stream().map(this::toMeetingDto).collect(Collectors.toList());
    }

    private ScheduleViewResponseDTO buildResponse(
            LocalDate from, LocalDate to, List<TeamScheduleDTO> teams) {
        return new ScheduleViewResponseDTO(from, to, teams);
    }
}
