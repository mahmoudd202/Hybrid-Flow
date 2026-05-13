package com.example.hybridflow.service;

import com.example.hybridflow.dto.ConflictingTeamDTO;
import com.example.hybridflow.dto.ScheduleConflictCheckRequestDTO;
import com.example.hybridflow.dto.ScheduleConflictCheckResponseDTO;
import com.example.hybridflow.entity.Office;
import com.example.hybridflow.entity.Schedule;
import com.example.hybridflow.entity.ScheduleEntry;
import com.example.hybridflow.entity.Team;
import com.example.hybridflow.entity.WorkMode;
import com.example.hybridflow.exception.BusinessValidationException;
import com.example.hybridflow.exception.ResourceNotFoundException;
import com.example.hybridflow.repository.ScheduleEntryRepository;
import com.example.hybridflow.repository.ScheduleRepository;
import com.example.hybridflow.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ScheduleManagementService {

    private final ScheduleRepository scheduleRepository;
    private final ScheduleEntryRepository scheduleEntryRepository;
    private final TeamRepository teamRepository;

    public ScheduleConflictCheckResponseDTO checkConflicts(ScheduleConflictCheckRequestDTO request) {
        List<Long> availableTeamIds = new ArrayList<>();
        List<ConflictingTeamDTO> conflictingTeams = new ArrayList<>();

        for (Long teamId : request.getTeamIds()) {
            List<Schedule> conflicts = scheduleRepository.findPublishedForTeamInRange(
                    teamId,
                    request.getStartDate(),
                    request.getEndDate());

            if (conflicts.isEmpty()) {
                availableTeamIds.add(teamId);
            } else {
                Team team = teamRepository.findById(teamId)
                        .orElseThrow(() -> new ResourceNotFoundException("Team not found: " + teamId));

                conflictingTeams.add(
                        ConflictingTeamDTO.builder()
                                .teamId(teamId)
                                .teamName(team.getName())
                                .conflictingScheduleIds(
                                        conflicts.stream()
                                                .map(Schedule::getId)
                                                .collect(Collectors.toList()))
                                .build());
            }
        }

        return ScheduleConflictCheckResponseDTO.builder()
                .availableTeamIds(availableTeamIds)
                .conflictingTeams(conflictingTeams)
                .build();
    }

    @Transactional
    public void publishSchedule(Long scheduleId) {
        Schedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Schedule not found"));

        if (schedule.isPublished()) {
            throw new BusinessValidationException("Schedule is already published.");
        }

        // Guard 1: The same team must not already have a published schedule
        // overlapping this schedule's date range.
        List<Schedule> teamConflicts = scheduleRepository.findPublishedForTeamInRange(
                schedule.getTeam().getId(),
                schedule.getStartDate(),
                schedule.getEndDate());

        if (!teamConflicts.isEmpty()) {
            throw new BusinessValidationException(
                    "Cannot publish: conflicting published schedules exist for this team in the selected period.");
        }

        // Guard 2: Publishing this schedule must not exceed office capacity.
        Office office = schedule.getOffice();

        Map<LocalDate, Long> existingUsage = scheduleEntryRepository
                .findPublishedEntriesForOffice(
                        office.getId(),
                        schedule.getStartDate(),
                        schedule.getEndDate())
                .stream()
                .filter(entry -> entry.getWorkMode() == WorkMode.OFFICE)
                .collect(Collectors.groupingBy(
                        ScheduleEntry::getDate,
                        Collectors.counting()));

        Map<LocalDate, Long> newUsage = scheduleEntryRepository
                .findByScheduleId(scheduleId)
                .stream()
                .filter(entry -> entry.getWorkMode() == WorkMode.OFFICE)
                .collect(Collectors.groupingBy(
                        ScheduleEntry::getDate,
                        Collectors.counting()));

        for (Map.Entry<LocalDate, Long> entry : newUsage.entrySet()) {
            LocalDate date = entry.getKey();
            long alreadyBooked = existingUsage.getOrDefault(date, 0L);
            long afterPublish = alreadyBooked + entry.getValue();

            if (afterPublish > office.getMaxCapacity()) {
                throw new BusinessValidationException(
                        "Cannot publish: office '" + office.getName() + "' would exceed capacity on "
                                + date + " (" + afterPublish + " / " + office.getMaxCapacity() + ").");
            }
        }

        schedule.setPublished(true);
        scheduleRepository.save(schedule);
    }

    @Transactional
    public void deleteSchedule(Long scheduleId) {
        Schedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new ResourceNotFoundException("Schedule not found"));

        if (schedule.isPublished()) {
            throw new BusinessValidationException(
                    "Cannot delete a published schedule. Only unpublished generated schedules can be deleted.");
        }

        scheduleEntryRepository.deleteByScheduleId(scheduleId);
        scheduleRepository.delete(schedule);
    }
}