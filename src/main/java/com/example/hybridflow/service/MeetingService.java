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
import com.example.hybridflow.repository.ScheduleEntryRepository;

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
    private final ScheduleEntryRepository scheduleEntryRepository;

    public MeetingService(
            MeetingRepository meetingRepository,
            TeamRepository teamRepository,
            OfficeRepository officeRepository,
            UserRepository userRepository,
            ScheduleAvailabilityService scheduleAvailabilityService,
            ScheduleEntryRepository scheduleEntryRepository) {
        this.meetingRepository = meetingRepository;
        this.teamRepository = teamRepository;
        this.officeRepository = officeRepository;
        this.userRepository = userRepository;
        this.scheduleAvailabilityService = scheduleAvailabilityService;
        this.scheduleEntryRepository = scheduleEntryRepository;
    }

    @Transactional
    public MeetingDTO createMeeting(MeetingRequestDTO dto, User host) {

        if (host == null) {
            throw new AccessDeniedException("Unauthenticated");
        }

        if (host.getCompany() == null) {
            throw new AccessDeniedException("You are not attached to a company");
        }

        if (host.getTeam() == null) {
            throw new AccessDeniedException("You are not attached to a team");
        }

        validateTimeRange(dto.getStartTime(), dto.getEndTime());

        Office office = officeRepository.findById(dto.getOfficeId())
                .orElseThrow(() -> new ResourceNotFoundException("Office not found"));

        if (!office.getCompany().getId().equals(host.getCompany().getId())) {
            throw new AccessDeniedException("You can only book meetings in your company's offices");
        }

        List<Team> teams = teamRepository.findAllById(dto.getParticipatingTeamIds());

        if (teams.isEmpty()) {
            throw new BusinessValidationException("No valid teams found for the given IDs");
        }

        for (Team team : teams) {
            if (!team.getCompany().getId().equals(host.getCompany().getId())) {
                throw new AccessDeniedException("Cannot invite a team from another company: " + team.getName());
            }
        }

        boolean hostTeamIncluded = teams.stream()
                .anyMatch(t -> t.getId().equals(host.getTeam().getId()));

        if (!hostTeamIncluded) {
            throw new BusinessValidationException("Your own team must be one of the participating teams");
        }

        LocalDate meetingDate = dto.getStartTime().toLocalDate();

        validateManagerAvailability(dto, host, meetingDate);

        List<User> affectedUsers = collectUniqueUsersFromTeams(teams);

        List<String> excludedUsers = scheduleAvailabilityService.findUnavailableUserEmailsOnDate(affectedUsers,
                meetingDate);

        checkForTeamSchedulingConflicts(teams, dto.getStartTime(), dto.getEndTime());
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

        if (meeting.getHost().getId().equals(requester.getId())) {
            meetingRepository.delete(meeting);
        } else {
            if (requester.getTeam() == null) {
                throw new AccessDeniedException("You are not attached to a team");
            }
            List<Team> teams = new ArrayList<>(meeting.getParticipatingTeams());
            boolean isInvited = teams.stream()
                    .anyMatch(t -> t.getId().equals(requester.getTeam().getId()));
            if (!isInvited) {
                throw new AccessDeniedException("You can only decline meetings that your team is invited to");
            }
            teams.removeIf(t -> t.getId().equals(requester.getTeam().getId()));
            meeting.setParticipatingTeams(teams);
            meetingRepository.save(meeting);
        }
    }

    @Transactional
    public MeetingDTO updateMeeting(Long meetingId, MeetingRequestDTO dto, User requester) {

        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new ResourceNotFoundException("Meeting not found"));

        if (!meeting.getHost().getId().equals(requester.getId())) {
            throw new AccessDeniedException("You can only edit meetings you created");
        }

        validateTimeRange(dto.getStartTime(), dto.getEndTime());

        Office office = officeRepository.findById(dto.getOfficeId())
                .orElseThrow(() -> new ResourceNotFoundException("Office not found"));

        if (!office.getCompany().getId().equals(requester.getCompany().getId())) {
            throw new AccessDeniedException("You can only book meetings in your company's offices");
        }

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

        LocalDate meetingDate = dto.getStartTime().toLocalDate();

        validateManagerAvailability(dto, requester, meetingDate);

        List<User> affectedUsers = collectUniqueUsersFromTeams(teams);

        List<String> excludedUsers = scheduleAvailabilityService.findUnavailableUserEmailsOnDate(affectedUsers,
                meetingDate);

        checkForTeamSchedulingConflictsExcluding(
                teams,
                dto.getStartTime(),
                dto.getEndTime(),
                meetingId);

        meeting.setTitle(dto.getTitle());
        meeting.setStartTime(dto.getStartTime());
        meeting.setEndTime(dto.getEndTime());
        meeting.setType(dto.getType());
        meeting.setOffice(office);
        meeting.setParticipatingTeams(teams);

        Meeting saved = meetingRepository.save(meeting);

        return toMeetingDto(saved, excludedUsers);
    }

    public List<MeetingDTO> getTeamMeetingsForUser(Long teamId, User requester) {
        enforceTeamMeetingAccess(requester, teamId);
        return getTeamMeetings(teamId);
    }

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
        List<Meeting> conflictingMeetings = meetingRepository.findUserMeetingsInRange(
                requester.getId(),
                startDate.atStartOfDay(),
                endDate.plusDays(1).atStartOfDay());

        for (Meeting meeting : conflictingMeetings) {
            if (meeting.getHost().getId().equals(requester.getId())) {
                meeting.setType(MeetingType.PTO_CANCELLED);
                meetingRepository.save(meeting);
            }
        }
    }

    @Transactional
    public void handleWfhRequest(User requester, LocalDate startDate, LocalDate endDate) {
        List<Meeting> conflictingMeetings = meetingRepository.findUserOfficeMeetingsInRange(
                requester.getId(),
                startDate.atStartOfDay(),
                endDate.plusDays(1).atStartOfDay());

        for (Meeting meeting : conflictingMeetings) {
            if (meeting.getHost().getId().equals(requester.getId())) {
                meeting.setType(MeetingType.ONLINE);
                meetingRepository.save(meeting);
            }
        }
    }

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
                m.getType() == MeetingType.ONLINE ? null : (m.getOffice() != null ? m.getOffice().getName() : null),
                teamNames,
                excludedUsers != null ? excludedUsers : List.of());
    }

    private void validateManagerAvailability(MeetingRequestDTO dto, User hostOrRequester, LocalDate meetingDate) {
        if (dto.getType() == MeetingType.OFFICE && hostOrRequester != null && hostOrRequester.getTeam() != null) {
            User manager = hostOrRequester.getTeam().getManager();
            if (manager != null) {
                Optional<ScheduleEntry> managerEntryOpt = scheduleEntryRepository
                        .findPublishedEntryForUserOnDate(manager.getId(), meetingDate);
                if (managerEntryOpt.isPresent() && managerEntryOpt.get().getWorkMode() == WorkMode.ONLINE) {
                    throw new BusinessValidationException(
                            "Cannot schedule an office meeting because you are working online on this day");
                }
            }
        }
    }
}