package com.example.hybridflow.service;

import com.example.hybridflow.dto.IndividualFairnessDTO;
import com.example.hybridflow.dto.TeamFairnessDTO;
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

/**
 * Handles the transactional persistence phase after Gurobi finishes solving.
 *
 * Must be a separate Spring bean from ScheduleGenerationAsyncService so that
 * this @Transactional boundary works correctly — Gurobi runs OUTSIDE any DB
 * transaction; once it finishes we open a new short-lived transaction here.
 *
 * On success:  saves Schedules, ScheduleEntries, links them to the run,
 *              evaluates fairness, serialises scores to JSON, marks run COMPLETED.
 * On failure:  marks run FAILED with an error message (called from the async service).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleGenerationPersistenceService {

    private final ScheduleRepository scheduleRepository;
    private final ScheduleEntryRepository scheduleEntryRepository;
    private final ScheduleOptimizationRunRepository runRepository;
    private final ScheduleEvaluationService scheduleEvaluationService;
    private final ObjectMapper objectMapper;

    /**
     * Persists all schedule/entry rows, evaluates fairness, serialises scores,
     * and marks the optimization run as COMPLETED.
     *
     * @param run          the run entity (already persisted, status = RUNNING)
     * @param office       the target office
     * @param teams        teams included in this generation
     * @param users        all schedulable users across those teams
     * @param workDates    ordered list of working dates in the schedule range
     * @param solution     map of userId → (date → true=OFFICE / false=ONLINE)
     * @param policy       the planning policy used for generation
     * @param startDate    schedule start date
     * @param endDate      schedule end date
     */
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

        // ── 1. Save one Schedule per team ─────────────────────────────────────
        Map<Long, Schedule> teamSchedules = new HashMap<>();

        for (Team team : teams) {
            Schedule schedule = new Schedule();
            schedule.setTeam(team);
            schedule.setOffice(office);
            schedule.setStartDate(startDate);
            schedule.setEndDate(endDate);
            schedule.setPublished(false);
            schedule.setOptimizationRun(run);   // ← link back to this run

            Schedule savedSchedule = scheduleRepository.save(schedule);
            teamSchedules.put(team.getId(), savedSchedule);
        }

        // ── 2. Save all ScheduleEntry rows ────────────────────────────────────
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

        // ── 3. Evaluate fairness using the actual policy ──────────────────────
        List<Long> scheduleIds = teamSchedules.values().stream()
                .map(Schedule::getId)
                .toList();

        EvaluationResult evaluation = scheduleEvaluationService.evaluateSchedules(
                scheduleIds, users, teams, policy, startDate, endDate);

        // ── 4. Serialise fairness scores to JSON ──────────────────────────────
        String teamJson = serializeToJson(evaluation.getTeamFairnessScores());
        String individualJson = serializeToJson(evaluation.getIndividualFairnessScores());

        // ── 5. Update run → COMPLETED with all stats ──────────────────────────
        run.setOverallFairnessScore(evaluation.getOverallFairnessScore());
        run.setTeamFairnessScoresJson(teamJson);
        run.setIndividualFairnessScoresJson(individualJson);
        run.setJobStatus(OptimizationJobStatus.COMPLETED);
        run.setCompletedAt(LocalDateTime.now());

        runRepository.save(run);

        log.info("Optimization run {} completed. Schedules: {}. Overall fairness: {:.1f}",
                run.getId(), scheduleIds, evaluation.getOverallFairnessScore());
    }

    /**
     * Marks the run as FAILED with a human-readable error message.
     * Called by ScheduleGenerationAsyncService on any exception.
     */
    @Transactional
    public void markFailed(ScheduleOptimizationRun run, String errorMessage) {
        run.setJobStatus(OptimizationJobStatus.FAILED);
        run.setErrorMessage(errorMessage);
        run.setCompletedAt(LocalDateTime.now());
        runRepository.save(run);
        log.warn("Optimization run {} marked FAILED: {}", run.getId(), errorMessage);
    }

    // ── JSON helper ───────────────────────────────────────────────────────────

    private String serializeToJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("Failed to serialize fairness scores to JSON: {}", e.getMessage());
            return "[]";
        }
    }
}
