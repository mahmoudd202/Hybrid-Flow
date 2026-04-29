package com.example.hybridflow.service;

import jakarta.transaction.Transactional;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import com.example.hybridflow.dto.MeetingDTO;
import com.example.hybridflow.dto.MeetingRequestDTO;
import com.example.hybridflow.entity.*;
import com.example.hybridflow.exception.BusinessValidationException;
import com.example.hybridflow.exception.ResourceNotFoundException;
import com.example.hybridflow.repository.MeetingRepository;
import com.example.hybridflow.repository.OfficeRepository;
import com.example.hybridflow.repository.TeamRepository;
import com.example.hybridflow.repository.UserRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MeetingService {

    private final MeetingRepository meetingRepository;
    private final TeamRepository teamRepository;
    private final OfficeRepository officeRepository;
    private final UserRepository userRepository;
    private final ScheduleAvailabilityService scheduleAvailabilityService;

    public MeetingService(
            MeetingRepository meetingRepository,
            TeamRepository teamRepository,
            OfficeRepository officeRepository,
            UserRepository userRepository,
            ScheduleAvailabilityService scheduleAvailabilityService) {
        this.meetingRepository = meetingRepository;
        this.teamRepository = teamRepository;
        this.officeRepository = officeRepository;
        this.userRepository = userRepository;
        this.scheduleAvailabilityService = scheduleAvailabilityService;
    }

    @Transactional
    public MeetingDTO createMeeting(MeetingRequestDTO dto, User host) {

        // --- Validate host context ---
        if (host == null) {
            throw new AccessDeniedException("Unauthenticated");
        }

        if (host.getCompany() == null) {
            throw new AccessDeniedException("You are not attached to a company");
        }

        if (host.getTeam() == null) {
            throw new AccessDeniedException("You are not attached to a team");
        }

        // --- Validate time range ---
        validateTimeRange(dto.getStartTime(), dto.getEndTime());

        // --- Validate office belongs to host's company ---
        Office office = officeRepository.findById(dto.getOfficeId())
                .orElseThrow(() -> new ResourceNotFoundException("Office not found"));

        if (!office.getCompany().getId().equals(host.getCompany().getId())) {
            throw new AccessDeniedException("You can only book meetings in your company's offices");
        }

        // --- Validate participating teams ---
        List<Team> teams = teamRepository.findAllById(dto.getParticipatingTeamIds());

        if (teams.isEmpty()) {
            throw new BusinessValidationException("No valid teams found for the given IDs");
        }

        for (Team team : teams) {
            if (!team.getCompany().getId().equals(host.getCompany().getId())) {
                throw new AccessDeniedException("Cannot invite a team from another company: " + team.getName());
            }
        }

        // Host's team must be one of the participating teams
        boolean hostTeamIncluded = teams.stream()
                .anyMatch(t -> t.getId().equals(host.getTeam().getId()));

        if (!hostTeamIncluded) {
            throw new BusinessValidationException("Your own team must be one of the participating teams");
        }

        // --- Collect affected users from participating teams ---
        LocalDate meetingDate = dto.getStartTime().toLocalDate();

        List<User> affectedUsers = collectUniqueUsersFromTeams(teams);

        /*
         * Option 2:
         * Do not block meeting creation if users are OFF or SICK_LEAVE.
         * Instead, collect them and return them in the response.
         */
        List<String> excludedUsers = scheduleAvailabilityService.findUnavailableUserEmailsOnDate(affectedUsers,
                meetingDate);

        // --- Check for time overlap with existing meetings for participating teams ---
        checkForTeamSchedulingConflicts(teams, dto.getStartTime(), dto.getEndTime());

        // --- Save meeting ---
        Meeting meeting = new Meeting();
        meeting.setTitle(dto.getTitle());
        meeting.setStartTime(dto.getStartTime());
        meeting.setEndTime(dto.getEndTime());
        meeting.setType(dto.getType());
        meeting.setHost(host);
        meeting.setOffice(office);
        meeting.setParticipatingTeams(teams);

        Meeting saved = meetingRepository.save(meeting);

        return toMeetingDto(saved, excludedUsers);
    }

    @Transactional
    public void deleteMeeting(Long meetingId, User requester) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new ResourceNotFoundException("Meeting not found"));

        if (!meeting.getHost().getId().equals(requester.getId())) {
            throw new AccessDeniedException("You can only delete meetings you created");
        }

        meetingRepository.delete(meeting);
    }

    @Transactional
    public MeetingDTO updateMeeting(Long meetingId, MeetingRequestDTO dto, User requester) {

        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new ResourceNotFoundException("Meeting not found"));

        if (!meeting.getHost().getId().equals(requester.getId())) {
            throw new AccessDeniedException("You can only edit meetings you created");
        }

        // --- Validate time range ---
        validateTimeRange(dto.getStartTime(), dto.getEndTime());

        // --- Validate office ---
        Office office = officeRepository.findById(dto.getOfficeId())
                .orElseThrow(() -> new ResourceNotFoundException("Office not found"));

        if (!office.getCompany().getId().equals(requester.getCompany().getId())) {
            throw new AccessDeniedException("You can only book meetings in your company's offices");
        }

        // --- Validate participating teams ---
        List<Team> teams = teamRepository.findAllById(dto.getParticipatingTeamIds());

        if (teams.isEmpty()) {
            throw new BusinessValidationException("No valid teams found for the given IDs");
        }

        for (Team team : teams) {
            if (!team.getCompany().getId().equals(requester.getCompany().getId())) {
                throw new AccessDeniedException("Cannot invite a team from another company: " + team.getName());
            }
        }

        boolean hostTeamIncluded = teams.stream()
                .anyMatch(t -> t.getId().equals(requester.getTeam().getId()));

        if (!hostTeamIncluded) {
            throw new BusinessValidationException("Your own team must be one of the participating teams");
        }

        // --- Collect unavailable users without blocking update ---
        LocalDate meetingDate = dto.getStartTime().toLocalDate();

        List<User> affectedUsers = collectUniqueUsersFromTeams(teams);

        List<String> excludedUsers = scheduleAvailabilityService.findUnavailableUserEmailsOnDate(affectedUsers,
                meetingDate);

        // --- Conflict check, excluding the meeting being updated ---
        checkForTeamSchedulingConflictsExcluding(
                teams,
                dto.getStartTime(),
                dto.getEndTime(),
                meetingId);

        // --- Apply changes ---
        meeting.setTitle(dto.getTitle());
        meeting.setStartTime(dto.getStartTime());
        meeting.setEndTime(dto.getEndTime());
        meeting.setType(dto.getType());
        meeting.setOffice(office);
        meeting.setParticipatingTeams(teams);

        Meeting saved = meetingRepository.save(meeting);

        return toMeetingDto(saved, excludedUsers);
    }

    /**
     * Fetch meetings for a specific team.
     * Access control is enforced by the caller.
     */
    public List<MeetingDTO> getTeamMeetingsForUser(Long teamId, User requester) {
        enforceTeamMeetingAccess(requester, teamId);
        return getTeamMeetings(teamId);
    }

    /**
     * Fetch meetings for a specific team.
     * Access control is enforced by the caller.
     */
    private List<MeetingDTO> getTeamMeetings(Long teamId) {
        List<Meeting> meetings = meetingRepository.findByTeamWithDetails(teamId);

        return meetings.stream()
                .map(this::toMeetingDto)
                .collect(Collectors.toList());
    }

    private void enforceTeamMeetingAccess(User user, Long targetTeamId) {
        if (user == null) {
            throw new AccessDeniedException("Unauthenticated");
        }

        Team targetTeam = teamRepository.findById(targetTeamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team not found"));

        if (user.getCompany() == null) {
            throw new AccessDeniedException("You are not attached to a company");
        }

        if (targetTeam.getCompany() == null) {
            throw new AccessDeniedException("Target team is not attached to a company");
        }

        if (user.getRole() == Role.HR) {
            if (!targetTeam.getCompany().getId().equals(user.getCompany().getId())) {
                throw new AccessDeniedException("You cannot view meetings for another company");
            }
            return;
        }

        if (user.getTeam() == null) {
            throw new AccessDeniedException("You are not assigned to any team");
        }

        if (!user.getTeam().getId().equals(targetTeamId)) {
            throw new AccessDeniedException("You can only view meetings for your own team");
        }
    }

    @Transactional
    public void handlePtoRequest(User requester, LocalDate startDate, LocalDate endDate) {
        // Find all meetings where the requester is a participant during the PTO period
        List<Meeting> conflictingMeetings = meetingRepository.findUserMeetingsInRange(
                requester.getId(),
                startDate.atStartOfDay(),
                endDate.plusDays(1).atStartOfDay());

        for (Meeting meeting : conflictingMeetings) {
            /*
             * Automatically handle PTO conflicts:
             * 1. If the requester is the host, the meeting is cancelled (deleted).
             * 2. If the requester is a participant (via team), they are effectively
             * "declined".
             * In this system, meetings are team-based, so we don't remove the user from the
             * team,
             * but the ScheduleViewService will already show them as OFF, which is the
             * "user-friendly" way
             * to show they won't attend.
             */
            if (meeting.getHost().getId().equals(requester.getId())) {
                meetingRepository.delete(meeting);
            }
            // For team participants, the ScheduleView already handles the "visual decline"
            // by showing the user as OFF.
        }
    }

    @Transactional
    public void handleWfhRequest(User requester, LocalDate startDate, LocalDate endDate) {
        // Find all OFFICE meetings where the requester is a participant during the WFH
        // period
        List<Meeting> conflictingMeetings = meetingRepository.findUserOfficeMeetingsInRange(
                requester.getId(),
                startDate.atStartOfDay(),
                endDate.plusDays(1).atStartOfDay());

        for (Meeting meeting : conflictingMeetings) {
            /*
             * Automatically handle WFH conflicts for OFFICE meetings:
             * 1. If the requester is the HOST: The meeting type is automatically changed to
             * ONLINE.
             * This is logical because the host won't be in the office to conduct it.
             * 2. If the requester is a PARTICIPANT: We keep the meeting as is.
             * The ScheduleViewService will show the user as ONLINE/WFH, which informs the
             * host
             * that this specific participant will be joining remotely.
             */
            if (meeting.getHost().getId().equals(requester.getId())) {
                meeting.setType(MeetingType.ONLINE);
                meetingRepository.save(meeting);
            }
        }
    }

    // ────────────────────────────────────────────────────────────────
    // VALIDATION HELPERS
    // ────────────────────────────────────────────────────────────────

    private void validateTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime == null || endTime == null) {
            throw new BusinessValidationException("startTime and endTime are required");
        }

        if (!endTime.isAfter(startTime)) {
            throw new BusinessValidationException("endTime must be after startTime");
        }
    }

    private List<User> collectUniqueUsersFromTeams(List<Team> teams) {
        Set<Long> seenUserIds = new HashSet<>();
        List<User> affectedUsers = new ArrayList<>();

        for (Team team : teams) {
            for (User member : userRepository.findAllByTeamId(team.getId())) {
                if (member.getId() != null && seenUserIds.add(member.getId())) {
                    affectedUsers.add(member);
                }
            }
        }

        return affectedUsers;
    }

    /**
     * Checks that none of the participating teams already have a meeting
     * that overlaps with the proposed time window.
     */
    private void checkForTeamSchedulingConflicts(
            List<Team> teams,
            LocalDateTime startTime,
            LocalDateTime endTime) {
        List<String> conflicts = new ArrayList<>();

        for (Team team : teams) {
            List<Meeting> overlapping = meetingRepository.findTeamMeetingsInRange(
                    team.getId(),
                    startTime,
                    endTime);

            if (!overlapping.isEmpty()) {
                String titles = overlapping.stream()
                        .map(Meeting::getTitle)
                        .collect(Collectors.joining(", "));

                conflicts.add("Team \"" + team.getName() + "\" already has a meeting in this time slot: " + titles);
            }
        }

        if (!conflicts.isEmpty()) {
            throw new BusinessValidationException(String.join("; ", conflicts));
        }
    }

    private void checkForTeamSchedulingConflictsExcluding(
            List<Team> teams,
            LocalDateTime startTime,
            LocalDateTime endTime,
            Long excludeMeetingId) {
        List<String> conflicts = new ArrayList<>();

        for (Team team : teams) {
            List<Meeting> overlapping = meetingRepository.findTeamMeetingsInRangeExcluding(
                    team.getId(),
                    startTime,
                    endTime,
                    excludeMeetingId);

            if (!overlapping.isEmpty()) {
                String titles = overlapping.stream()
                        .map(Meeting::getTitle)
                        .collect(Collectors.joining(", "));

                conflicts.add("Team \"" + team.getName() + "\" already has a meeting in this time slot: " + titles);
            }
        }

        if (!conflicts.isEmpty()) {
            throw new BusinessValidationException(String.join("; ", conflicts));
        }
    }

    // ────────────────────────────────────────────────────────────────
    // DTO CONVERSION
    // ────────────────────────────────────────────────────────────────

    private MeetingDTO toMeetingDto(Meeting m) {
        return toMeetingDto(m, List.of());
    }

    private MeetingDTO toMeetingDto(Meeting m, List<String> excludedUsers) {
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
                excludedUsers != null ? excludedUsers : List.of());
    }
}
