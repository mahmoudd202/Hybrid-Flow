package com.example.hybridflow.service;

import com.example.hybridflow.dto.GeneratedScheduleEntryDTO;
import com.example.hybridflow.dto.GeneratedTeamScheduleDTO;
import com.example.hybridflow.dto.ScheduleGenerationRequestDTO;
import com.example.hybridflow.dto.ScheduleGenerationResponseDTO;
import com.example.hybridflow.entity.Office;
import com.example.hybridflow.entity.PlanningPolicy;
import com.example.hybridflow.entity.PreferredWorkDay;
import com.example.hybridflow.entity.Role;
import com.example.hybridflow.entity.Schedule;
import com.example.hybridflow.entity.ScheduleEntry;
import com.example.hybridflow.entity.Team;
import com.example.hybridflow.entity.User;
import com.example.hybridflow.entity.WorkMode;
import com.example.hybridflow.exception.BusinessValidationException;
import com.example.hybridflow.exception.ResourceNotFoundException;
import com.example.hybridflow.repository.OfficeRepository;
import com.example.hybridflow.repository.PlanningPolicyRepository;
import com.example.hybridflow.repository.PreferredWorkDayRepository;
import com.example.hybridflow.repository.ScheduleEntryRepository;
import com.example.hybridflow.repository.ScheduleRepository;
import com.example.hybridflow.repository.TeamRepository;
import com.example.hybridflow.repository.UserRepository;
import com.example.hybridflow.service.ScheduleEvaluationService.EvaluationResult;
import com.gurobi.gurobi.GRB;
import com.gurobi.gurobi.GRBEnv;
import com.gurobi.gurobi.GRBException;
import com.gurobi.gurobi.GRBLinExpr;
import com.gurobi.gurobi.GRBModel;
import com.gurobi.gurobi.GRBVar;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ScheduleGenerationService {

    private final PlanningPolicyRepository planningPolicyRepository;
    private final OfficeRepository officeRepository;
    private final TeamRepository teamRepository;
    private final UserRepository userRepository;
    private final PreferredWorkDayRepository preferredWorkDayRepository;
    private final ScheduleRepository scheduleRepository;
    private final ScheduleEntryRepository scheduleEntryRepository;
    private final ScheduleEvaluationService scheduleEvaluationService;

    /*
     * This method is intentionally transactional for your current project scope.
     *
     * Better long-term design:
     * - run Gurobi outside a transaction
     * - save results through a separate transactional persistence service
     *
     * But for this project, keeping one transaction here is simpler and avoids
     * partial persisted schedules if something fails after optimization.
     */
    @Transactional
    public ScheduleGenerationResponseDTO generateSchedule(
            ScheduleGenerationRequestDTO request,
            User currentUser) {
        validateGenerationRequest(request, currentUser);

        Long companyId = currentUser.getCompany().getId();

        Office office = officeRepository.findById(request.getOfficeId())
                .orElseThrow(() -> new ResourceNotFoundException("Office not found."));

        if (office.getCompany() == null || !office.getCompany().getId().equals(companyId)) {
            throw new AccessDeniedException("You can only generate schedules for offices in your company.");
        }

        PlanningPolicy policy = planningPolicyRepository
                .findByIdAndCompanyId(request.getPlanningPolicyId(), companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Planning policy not found for your company."));

        List<Long> selectedTeamIds = normalizeTeamIds(request.getTeamIds());

        /*
         * Correct business rule:
         * HR can choose any team in the company.
         * Do NOT require team.office.id == request.officeId.
         */
        List<Team> teams = teamRepository.findByIdInAndCompanyId(selectedTeamIds, companyId);

        if (teams.size() != selectedTeamIds.size()) {
            throw new AccessDeniedException(
                    "One or more selected teams do not exist or do not belong to your company.");
        }

        validateTeamsAreAvailableForGeneration(
                teams,
                request.getStartDate(),
                request.getEndDate());

        List<User> users = userRepository.findSchedulableUsersByTeamIds(selectedTeamIds);

        validateEveryTeamHasSchedulableUsers(teams, users);

        List<LocalDate> workDates = buildWorkDates(
                request.getStartDate(),
                request.getEndDate());

        if (workDates.isEmpty()) {
            return ScheduleGenerationResponseDTO.builder()
                    .status("FAILED")
                    .message("The selected date range contains no working days.")
                    .planningPolicyId(policy.getId())
                    .planningPolicyName(policy.getName())
                    .build();
        }

        Map<LocalDate, Long> existingOfficeUsageByDate = calculateExistingOfficeUsageByDate(
                office.getId(),
                request.getStartDate(),
                request.getEndDate());

        /*
         * Do NOT silently delete unpublished drafts here.
         * If a draft exists, validateTeamsAreAvailableForGeneration(...) blocks
         * generation.
         * HR must explicitly publish or discard first.
         */

        GRBEnv env = null;
        GRBModel model = null;

        try {
            env = new GRBEnv(true);
            env.set("LogToConsole", "0");
            env.start();

            model = new GRBModel(env);
            model.set(GRB.StringAttr.ModelName, "HybridFlowSchedule");

            /*
             * Decision variable:
             * x[userId_date] = 1 if the user is assigned OFFICE on that date.
             */
            Map<String, GRBVar> x = new HashMap<>();

            for (User user : users) {
                for (LocalDate date : workDates) {
                    String key = variableKey(user.getId(), date);

                    x.put(
                            key,
                            model.addVar(
                                    0.0,
                                    1.0,
                                    0.0,
                                    GRB.BINARY,
                                    "x_" + key));
                }
            }

            /*
             * Constraint 1:
             * Office capacity per day.
             *
             * Existing published usage is subtracted so later generation runs
             * do not overbook the same office.
             */
            for (LocalDate date : workDates) {
                long existingUsage = existingOfficeUsageByDate.getOrDefault(date, 0L);
                long remainingCapacity = office.getMaxCapacity() - existingUsage;

                if (remainingCapacity < 0) {
                    return ScheduleGenerationResponseDTO.builder()
                            .status("FAILED")
                            .message(
                                    "Office capacity is already exceeded on " + date
                                            + " by previously published schedules.")
                            .planningPolicyId(policy.getId())
                            .planningPolicyName(policy.getName())
                            .build();
                }

                GRBLinExpr capacityExpr = new GRBLinExpr();

                for (User user : users) {
                    capacityExpr.addTerm(
                            1.0,
                            x.get(variableKey(user.getId(), date)));
                }

                model.addConstr(
                        capacityExpr,
                        GRB.LESS_EQUAL,
                        remainingCapacity,
                        "Capacity_" + date);
            }

            /*
             * Constraint 2:
             * Min/max office days per ISO week.
             */
            WeekFields iso = WeekFields.ISO;

            Map<String, List<LocalDate>> datesByWeek = workDates.stream()
                    .collect(Collectors.groupingBy(
                            date -> date.get(iso.weekBasedYear())
                                    + "-W"
                                    + date.get(iso.weekOfWeekBasedYear())));

            for (User user : users) {
                for (Map.Entry<String, List<LocalDate>> weekEntry : datesByWeek.entrySet()) {
                    String weekKey = weekEntry.getKey();
                    List<LocalDate> weekDates = weekEntry.getValue();

                    GRBLinExpr weeklyOfficeDaysExpr = new GRBLinExpr();

                    for (LocalDate date : weekDates) {
                        weeklyOfficeDaysExpr.addTerm(
                                1.0,
                                x.get(variableKey(user.getId(), date)));
                    }

                    String suffix = "User_" + user.getId() + "_" + weekKey;

                    model.addConstr(
                            weeklyOfficeDaysExpr,
                            GRB.GREATER_EQUAL,
                            policy.getMinOfficeDaysPerWeek(),
                            "MinOffice_" + suffix);

                    model.addConstr(
                            weeklyOfficeDaysExpr,
                            GRB.LESS_EQUAL,
                            policy.getMaxOfficeDaysPerWeek(),
                            "MaxOffice_" + suffix);
                }
            }

            /*
             * Constraint 3:
             * Max consecutive office days.
             */
            int maxConsecutiveOfficeDays = policy.getMaxConsecutiveOfficeDays();

            for (User user : users) {
                for (int i = 0; i <= workDates.size() - (maxConsecutiveOfficeDays + 1); i++) {
                    GRBLinExpr consecutiveExpr = new GRBLinExpr();

                    for (int k = 0; k <= maxConsecutiveOfficeDays; k++) {
                        consecutiveExpr.addTerm(
                                1.0,
                                x.get(variableKey(user.getId(), workDates.get(i + k))));
                    }

                    model.addConstr(
                            consecutiveExpr,
                            GRB.LESS_EQUAL,
                            maxConsecutiveOfficeDays,
                            "MaxConsecutive_User_" + user.getId() + "_" + i);
                }
            }

            /*
             * Constraint 4:
             * Team co-presence.
             *
             * y[team,date] = 1 exactly when the co-presence threshold is reached.
             *
             * Formulation:
             * memberSum >= thresholdCount * y
             * memberSum <= (thresholdCount - 1) + teamSize * y
             */
            for (Team team : teams) {
                List<User> members = users.stream()
                        .filter(user -> user.getTeam() != null)
                        .filter(user -> user.getTeam().getId().equals(team.getId()))
                        .collect(Collectors.toList());

                if (members.isEmpty()) {
                    continue;
                }

                int teamSize = members.size();

                int thresholdCount = (int) Math.ceil(
                        (policy.getCoPresenceThresholdPercentagePerDay() / 100.0) * teamSize);

                thresholdCount = Math.max(1, thresholdCount);

                Map<LocalDate, GRBVar> y = new HashMap<>();

                for (LocalDate date : workDates) {
                    GRBVar yVar = model.addVar(
                            0.0,
                            1.0,
                            0.0,
                            GRB.BINARY,
                            "y_" + team.getId() + "_" + date);

                    y.put(date, yVar);

                    GRBLinExpr memberSum = new GRBLinExpr();

                    for (User member : members) {
                        memberSum.addTerm(
                                1.0,
                                x.get(variableKey(member.getId(), date)));
                    }

                    GRBLinExpr lowerBound = new GRBLinExpr();
                    lowerBound.addTerm(thresholdCount, yVar);

                    model.addConstr(
                            memberSum,
                            GRB.GREATER_EQUAL,
                            lowerBound,
                            "CoPresenceLower_" + team.getId() + "_" + date);

                    GRBLinExpr upperBound = new GRBLinExpr();
                    upperBound.addConstant(thresholdCount - 1);
                    upperBound.addTerm(teamSize, yVar);

                    model.addConstr(
                            memberSum,
                            GRB.LESS_EQUAL,
                            upperBound,
                            "CoPresenceUpper_" + team.getId() + "_" + date);
                }

                GRBLinExpr sharedDaysExpr = new GRBLinExpr();

                for (LocalDate date : workDates) {
                    sharedDaysExpr.addTerm(
                            1.0,
                            y.get(date));
                }

                model.addConstr(
                        sharedDaysExpr,
                        GRB.GREATER_EQUAL,
                        policy.getMinTeamSharedDays(),
                        "MinSharedDays_Team_" + team.getId());
            }

            /*
             * Objective:
             * Maximize preferred work-day satisfaction.
             */
            GRBLinExpr objective = new GRBLinExpr();

            for (User user : users) {
                Set<DayOfWeek> preferredDays = preferredWorkDayRepository
                        .findByUserId(user.getId())
                        .stream()
                        .map(PreferredWorkDay::getDayOfWeek)
                        .collect(Collectors.toSet());

                for (LocalDate date : workDates) {
                    if (preferredDays.contains(date.getDayOfWeek())) {
                        objective.addTerm(
                                1.0,
                                x.get(variableKey(user.getId(), date)));
                    }
                }
            }

            model.setObjective(objective, GRB.MAXIMIZE);
            model.optimize();

            int status = model.get(GRB.IntAttr.Status);

            if (status != GRB.OPTIMAL) {
                String message;

                if (status == GRB.INFEASIBLE) {
                    message = "Model is infeasible. The selected policy constraints cannot all be satisfied "
                            + "for the chosen teams, office capacity, and date range.";
                } else {
                    message = "Gurobi did not find an optimal solution. Status code: " + status;
                }

                return ScheduleGenerationResponseDTO.builder()
                        .status("FAILED")
                        .message(message)
                        .planningPolicyId(policy.getId())
                        .planningPolicyName(policy.getName())
                        .build();
            }

            Map<Long, Map<LocalDate, Boolean>> solution = new HashMap<>();

            for (User user : users) {
                Map<LocalDate, Boolean> userSchedule = new HashMap<>();

                for (LocalDate date : workDates) {
                    boolean inOffice = x.get(variableKey(user.getId(), date))
                            .get(GRB.DoubleAttr.X) > 0.5;

                    userSchedule.put(date, inOffice);
                }

                solution.put(user.getId(), userSchedule);
            }

            return saveResults(
                    request,
                    office,
                    teams,
                    users,
                    workDates,
                    solution,
                    policy);

        } catch (GRBException e) {
            throw new RuntimeException("Gurobi optimization failed: " + e.getMessage(), e);
        } finally {
            if (model != null) {
                model.dispose();
            }

            if (env != null) {
                try {
                    env.dispose();
                } catch (GRBException ignored) {
                }
            }
        }
    }

    private ScheduleGenerationResponseDTO saveResults(
            ScheduleGenerationRequestDTO request,
            Office office,
            List<Team> teams,
            List<User> users,
            List<LocalDate> workDates,
            Map<Long, Map<LocalDate, Boolean>> solution,
            PlanningPolicy policy) {
        Map<Long, Schedule> teamSchedules = new HashMap<>();

        for (Team team : teams) {
            Schedule schedule = new Schedule();
            schedule.setTeam(team);
            schedule.setOffice(office);
            schedule.setStartDate(request.getStartDate());
            schedule.setEndDate(request.getEndDate());
            schedule.setPublished(false);

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

        List<ScheduleEntry> savedEntries = scheduleEntryRepository.saveAll(entries);

        EvaluationResult evaluationResult = scheduleEvaluationService.evaluateSchedules(
                teamSchedules.values()
                        .stream()
                        .map(Schedule::getId)
                        .collect(Collectors.toList()),
                users,
                teams,
                policy,
                request.getStartDate(),
                request.getEndDate());

        List<GeneratedTeamScheduleDTO> generatedSchedules = buildGeneratedSchedulesResponse(
                teams,
                office,
                teamSchedules,
                savedEntries);

        List<Long> scheduleIds = teamSchedules.values()
                .stream()
                .map(Schedule::getId)
                .collect(Collectors.toList());

        return ScheduleGenerationResponseDTO.builder()
                .status("SUCCESS")
                .message("Schedules generated successfully.")
                .planningPolicyId(policy.getId())
                .planningPolicyName(policy.getName())
                .scheduleIds(scheduleIds)
                .overallFairnessScore(evaluationResult.getOverallFairnessScore())
                .teamFairnessScores(evaluationResult.getTeamFairnessScores())
                .individualFairnessScores(evaluationResult.getIndividualFairnessScores())
                .generatedSchedules(generatedSchedules)
                .build();
    }

    private List<GeneratedTeamScheduleDTO> buildGeneratedSchedulesResponse(
            List<Team> teams,
            Office office,
            Map<Long, Schedule> teamSchedules,
            List<ScheduleEntry> savedEntries) {
        Map<Long, List<GeneratedScheduleEntryDTO>> entriesByScheduleId = savedEntries.stream()
                .collect(Collectors.groupingBy(
                        entry -> entry.getSchedule().getId(),
                        Collectors.mapping(
                                entry -> GeneratedScheduleEntryDTO.builder()
                                        .userId(entry.getUser().getId())
                                        .userEmail(entry.getUser().getEmail())
                                        .date(entry.getDate())
                                        .workMode(entry.getWorkMode())
                                        .build(),
                                Collectors.toList())));

        return teams.stream()
                .map(team -> {
                    Schedule schedule = teamSchedules.get(team.getId());

                    return GeneratedTeamScheduleDTO.builder()
                            .scheduleId(schedule.getId())
                            .teamId(team.getId())
                            .teamName(team.getName())
                            .officeId(office.getId())
                            .officeName(office.getName())
                            .entries(
                                    entriesByScheduleId.getOrDefault(
                                            schedule.getId(),
                                            Collections.emptyList()))
                            .build();
                })
                .toList();
    }

    private Map<LocalDate, Long> calculateExistingOfficeUsageByDate(
            Long officeId,
            LocalDate startDate,
            LocalDate endDate) {
        return scheduleEntryRepository
                .findPublishedEntriesForOffice(officeId, startDate, endDate)
                .stream()
                .filter(entry -> entry.getWorkMode() == WorkMode.OFFICE)
                .collect(Collectors.groupingBy(
                        ScheduleEntry::getDate,
                        Collectors.counting()));
    }

    private void validateGenerationRequest(
            ScheduleGenerationRequestDTO request,
            User currentUser) {
        if (currentUser == null || currentUser.getId() == null) {
            throw new AccessDeniedException("Unauthenticated.");
        }

        if (currentUser.getRole() != Role.HR) {
            throw new AccessDeniedException("Only HR can generate schedules.");
        }

        if (currentUser.getCompany() == null) {
            throw new AccessDeniedException("HR user is not assigned to a company.");
        }

        if (request.getOfficeId() == null) {
            throw new BusinessValidationException("officeId is required.");
        }

        if (request.getPlanningPolicyId() == null) {
            throw new BusinessValidationException("planningPolicyId is required.");
        }

        if (request.getTeamIds() == null || request.getTeamIds().isEmpty()) {
            throw new BusinessValidationException("At least one team must be selected.");
        }

        if (request.getStartDate() == null || request.getEndDate() == null) {
            throw new BusinessValidationException("startDate and endDate are required.");
        }

        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new BusinessValidationException("endDate must be on or after startDate.");
        }
    }

    private List<Long> normalizeTeamIds(List<Long> teamIds) {
        List<Long> normalized = new ArrayList<>(
                new LinkedHashSet<>(
                        teamIds.stream()
                                .filter(id -> id != null)
                                .toList()));

        if (normalized.isEmpty()) {
            throw new BusinessValidationException("At least one valid team must be selected.");
        }

        return normalized;
    }

    private void validateTeamsAreAvailableForGeneration(
            List<Team> teams,
            LocalDate startDate,
            LocalDate endDate) {
        List<String> conflicts = new ArrayList<>();

        for (Team team : teams) {
            List<Schedule> publishedConflicts = scheduleRepository.findPublishedForTeamInRange(
                    team.getId(),
                    startDate,
                    endDate);

            if (!publishedConflicts.isEmpty()) {
                conflicts.add(
                        "Team '" + team.getName()
                                + "' already has a published schedule in this date range.");
            }

            List<Schedule> draftConflicts = scheduleRepository.findUnpublishedForTeamInRange(
                    team.getId(),
                    startDate,
                    endDate);

            if (!draftConflicts.isEmpty()) {
                conflicts.add(
                        "Team '" + team.getName()
                                + "' already has an unpublished generated schedule in this date range. "
                                + "Publish or discard it first.");
            }
        }

        if (!conflicts.isEmpty()) {
            throw new BusinessValidationException(String.join(" ", conflicts));
        }
    }

    private void validateEveryTeamHasSchedulableUsers(
            List<Team> teams,
            List<User> users) {
        Map<Long, Long> userCountByTeamId = users.stream()
                .filter(user -> user.getTeam() != null)
                .collect(Collectors.groupingBy(
                        user -> user.getTeam().getId(),
                        Collectors.counting()));

        List<String> invalidTeams = new ArrayList<>();

        for (Team team : teams) {
            long count = userCountByTeamId.getOrDefault(team.getId(), 0L);

            if (count == 0) {
                invalidTeams.add(team.getName());
            }
        }

        if (!invalidTeams.isEmpty()) {
            throw new BusinessValidationException(
                    "The following teams have no active schedulable employees or managers: "
                            + String.join(", ", invalidTeams));
        }
    }

    private List<LocalDate> buildWorkDates(
            LocalDate startDate,
            LocalDate endDate) {
        long numDays = ChronoUnit.DAYS.between(startDate, endDate) + 1;

        List<LocalDate> workDates = new ArrayList<>();

        for (int i = 0; i < numDays; i++) {
            LocalDate date = startDate.plusDays(i);

            if (date.getDayOfWeek().getValue() <= 5) {
                workDates.add(date);
            }
        }

        return workDates;
    }

    private String variableKey(Long userId, LocalDate date) {
        return userId + "_" + date;
    }
}