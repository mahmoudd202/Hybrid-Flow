package com.example.hybridflow.service;

import com.example.hybridflow.entity.*;
import com.example.hybridflow.repository.ScheduleEntryRepository;
import com.example.hybridflow.repository.ScheduleOptimizationRunRepository;
import com.example.hybridflow.repository.ScheduleRepository;
import com.example.hybridflow.service.ScheduleEvaluationService.EvaluationResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleGenerationPersistenceService {

    private final ScheduleRepository scheduleRepository;
    private final ScheduleEntryRepository scheduleEntryRepository;
    private final ScheduleOptimizationRunRepository runRepository;
    private final ScheduleEvaluationService scheduleEvaluationService;
    private final ObjectMapper objectMapper;

    @Transactional
    public void saveResults(
            ScheduleOptimizationRun run,
            Office office,
            List<Team> teams,
            List<User> users,
            List<LocalDate> workDates,
            Map<Long, Map<LocalDate, Boolean>> solution,
            PlanningPolicy policy,
            LocalDate startDate,
            LocalDate endDate) {

        Map<Long, Schedule> teamSchedules = new HashMap<>();

        for (Team team : teams) {
            Schedule schedule = new Schedule();
            schedule.setTeam(team);
            schedule.setOffice(office);
            schedule.setStartDate(startDate);
            schedule.setEndDate(endDate);
            schedule.setPublished(false);
            schedule.setOptimizationRun(run);

            Schedule savedSchedule = scheduleRepository.save(schedule);
            teamSchedules.put(team.getId(), savedSchedule);
        }

        List<ScheduleEntry> entries = new ArrayList<>();

        for (User user : users) {
            Map<LocalDate, Boolean> userSolution = solution.get(user.getId());

            for (LocalDate date : workDates) {
                boolean inOffice = userSolution.getOrDefault(date, false);

                ScheduleEntry entry = new ScheduleEntry();
                entry.setSchedule(teamSchedules.get(user.getTeam().getId()));
                entry.setUser(user);
                entry.setDate(date);
                entry.setWorkMode(inOffice ? WorkMode.OFFICE : WorkMode.ONLINE);

                entries.add(entry);
            }
        }

        scheduleEntryRepository.saveAll(entries);

        List<Long> scheduleIds = teamSchedules.values().stream()
                .map(Schedule::getId)
                .toList();

        EvaluationResult evaluation = scheduleEvaluationService.evaluateSchedules(
                scheduleIds, users, teams, policy, startDate, endDate);

        String teamJson = serializeToJson(evaluation.getTeamFairnessScores());
        String individualJson = serializeToJson(evaluation.getIndividualFairnessScores());

        run.setOverallFairnessScore(evaluation.getOverallFairnessScore());
        run.setTeamFairnessScoresJson(teamJson);
        run.setIndividualFairnessScoresJson(individualJson);
        run.setJobStatus(OptimizationJobStatus.COMPLETED);
        run.setCompletedAt(LocalDateTime.now());

        runRepository.save(run);

        log.info("Optimization run {} completed. Schedules: {}. Overall fairness: {:.1f}",
                run.getId(), scheduleIds, evaluation.getOverallFairnessScore());
    }

    @Transactional
    public void markFailed(ScheduleOptimizationRun run, String errorMessage) {
        run.setJobStatus(OptimizationJobStatus.FAILED);
        if (errorMessage != null && errorMessage.length() > 1000) {
            errorMessage = errorMessage.substring(0, 997) + "...";
        }
        run.setErrorMessage(errorMessage);
        run.setCompletedAt(LocalDateTime.now());
        runRepository.save(run);
        log.warn("Optimization run {} marked FAILED: {}", run.getId(), errorMessage);
    }

    private String serializeToJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("Failed to serialize fairness scores to JSON: {}", e.getMessage());
            return "[]";
        }
    }
}
