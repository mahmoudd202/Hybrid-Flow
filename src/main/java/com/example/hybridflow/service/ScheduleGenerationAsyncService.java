package com.example.hybridflow.service;

import com.example.hybridflow.dto.ScheduleGenerationRequestDTO;
import com.example.hybridflow.entity.*;
import com.example.hybridflow.repository.*;
import com.example.hybridflow.exception.ResourceNotFoundException;
import com.gurobi.gurobi.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleGenerationAsyncService {

    private final ScheduleOptimizationRunRepository runRepository;
    private final OfficeRepository officeRepository;
    private final TeamRepository teamRepository;
    private final UserRepository userRepository;
    private final PreferredWorkDayRepository preferredWorkDayRepository;
    private final ScheduleEntryRepository scheduleEntryRepository;
    private final PlanningPolicyRepository planningPolicyRepository;
    private final ScheduleGenerationPersistenceService persistenceService;

    @Async("gurobiExecutor")
    public void runAsync(
            Long runId,
            ScheduleGenerationRequestDTO request,
            Long officeId,
            List<Long> teamIds,
            Long policyId) {

        ScheduleOptimizationRun run = runRepository.findById(runId)
                .orElseThrow(() -> new ResourceNotFoundException("Optimization run not found."));

        Office office = officeRepository.findById(officeId)
                .orElseThrow(() -> new ResourceNotFoundException("Office not found."));

        List<Team> teams = teamRepository.findAllById(teamIds);

        List<User> users = userRepository.findSchedulableUsersByTeamIds(teamIds);

        PlanningPolicy policy = planningPolicyRepository.findById(policyId)
                .orElseThrow(() -> new ResourceNotFoundException("Planning policy not found."));

        run.setJobStatus(OptimizationJobStatus.RUNNING);
        runRepository.save(run);

        GRBEnv env = null;
        GRBModel model = null;

        try {
            List<LocalDate> workDates = buildWorkDates(request.getStartDate(), request.getEndDate());

            if (workDates.isEmpty()) {
                persistenceService.markFailed(run,
                        "The selected date range contains no working days.");
                return;
            }

            Map<LocalDate, Long> existingOfficeUsageByDate = calculateExistingOfficeUsageByDate(
                    office.getId(), request.getStartDate(), request.getEndDate());

            env = new GRBEnv(true);
            env.set("LogToConsole", "0");
            env.start();

            model = new GRBModel(env);
            model.set(GRB.StringAttr.ModelName, "HybridFlowSchedule");

            Map<String, GRBVar> x = new HashMap<>();
            for (User user : users) {
                for (LocalDate date : workDates) {
                    String key = variableKey(user.getId(), date);
                    x.put(key, model.addVar(0.0, 1.0, 0.0, GRB.BINARY, "x_" + key));
                }
            }

            for (LocalDate date : workDates) {
                long existingUsage = existingOfficeUsageByDate.getOrDefault(date, 0L);
                long remainingCapacity = office.getMaxCapacity() - existingUsage;

                if (remainingCapacity < 0) {
                    persistenceService.markFailed(run,
                            "Office capacity is already exceeded on " + date
                                    + " by previously published schedules.");
                    return;
                }

                GRBLinExpr capacityExpr = new GRBLinExpr();
                for (User user : users) {
                    capacityExpr.addTerm(1.0, x.get(variableKey(user.getId(), date)));
                }
                model.addConstr(capacityExpr, GRB.LESS_EQUAL, remainingCapacity,
                        "Capacity_" + date);
            }

            WeekFields iso = WeekFields.ISO;
            Map<String, List<LocalDate>> datesByWeek = workDates.stream()
                    .collect(Collectors.groupingBy(
                            date -> date.get(iso.weekBasedYear()) + "-W" + date.get(iso.weekOfWeekBasedYear())));

            for (User user : users) {
                for (Map.Entry<String, List<LocalDate>> weekEntry : datesByWeek.entrySet()) {
                    String weekKey = weekEntry.getKey();
                    List<LocalDate> weekDates = weekEntry.getValue();

                    GRBLinExpr weeklyOfficeDaysExpr = new GRBLinExpr();
                    for (LocalDate date : weekDates) {
                        weeklyOfficeDaysExpr.addTerm(1.0, x.get(variableKey(user.getId(), date)));
                    }

                    String suffix = "User_" + user.getId() + "_" + weekKey;
                    model.addConstr(weeklyOfficeDaysExpr, GRB.GREATER_EQUAL,
                            policy.getMinOfficeDaysPerWeek(), "MinOffice_" + suffix);
                    model.addConstr(weeklyOfficeDaysExpr, GRB.LESS_EQUAL,
                            policy.getMaxOfficeDaysPerWeek(), "MaxOffice_" + suffix);
                }
            }

            int maxConsecutiveOfficeDays = policy.getMaxConsecutiveOfficeDays();
            for (User user : users) {
                for (int i = 0; i <= workDates.size() - (maxConsecutiveOfficeDays + 1); i++) {
                    GRBLinExpr consecutiveExpr = new GRBLinExpr();
                    for (int k = 0; k <= maxConsecutiveOfficeDays; k++) {
                        consecutiveExpr.addTerm(1.0, x.get(variableKey(user.getId(), workDates.get(i + k))));
                    }
                    model.addConstr(consecutiveExpr, GRB.LESS_EQUAL, maxConsecutiveOfficeDays,
                            "MaxConsecutive_User_" + user.getId() + "_" + i);
                }
            }

            for (Team team : teams) {
                List<User> members = users.stream()
                        .filter(u -> u.getTeam() != null && u.getTeam().getId().equals(team.getId()))
                        .collect(Collectors.toList());

                if (members.isEmpty())
                    continue;

                int teamSize = members.size();
                int thresholdCount = (int) Math.ceil(
                        (policy.getCoPresenceThresholdPercentagePerDay() / 100.0) * teamSize);
                thresholdCount = Math.max(1, thresholdCount);

                Map<LocalDate, GRBVar> y = new HashMap<>();
                for (LocalDate date : workDates) {
                    GRBVar yVar = model.addVar(0.0, 1.0, 0.0, GRB.BINARY,
                            "y_" + team.getId() + "_" + date);
                    y.put(date, yVar);

                    GRBLinExpr memberSum = new GRBLinExpr();
                    for (User member : members) {
                        memberSum.addTerm(1.0, x.get(variableKey(member.getId(), date)));
                    }

                    GRBLinExpr lowerBound = new GRBLinExpr();
                    lowerBound.addTerm(thresholdCount, yVar);
                    model.addConstr(memberSum, GRB.GREATER_EQUAL, lowerBound,
                            "CoPresenceLower_" + team.getId() + "_" + date);

                    GRBLinExpr upperBound = new GRBLinExpr();
                    upperBound.addConstant(thresholdCount - 1);
                    upperBound.addTerm(teamSize, yVar);
                    model.addConstr(memberSum, GRB.LESS_EQUAL, upperBound,
                            "CoPresenceUpper_" + team.getId() + "_" + date);
                }

                GRBLinExpr sharedDaysExpr = new GRBLinExpr();
                for (LocalDate date : workDates) {
                    sharedDaysExpr.addTerm(1.0, y.get(date));
                }
                model.addConstr(sharedDaysExpr, GRB.GREATER_EQUAL,
                        policy.getMinTeamSharedDays(),
                        "MinSharedDays_Team_" + team.getId());
            }

            Map<Long, Set<DayOfWeek>> preferredDaysByUser = new HashMap<>();
            for (User user : users) {
                Set<DayOfWeek> preferredDays = preferredWorkDayRepository
                        .findByUserId(user.getId()).stream()
                        .map(PreferredWorkDay::getDayOfWeek)
                        .collect(Collectors.toSet());
                preferredDaysByUser.put(user.getId(), preferredDays);
            }

            List<String> orderedWeekKeys = datesByWeek.keySet().stream()
                    .sorted().collect(Collectors.toList());

            GRBLinExpr objective = new GRBLinExpr();

            for (User user : users) {
                Set<DayOfWeek> preferredDays = preferredDaysByUser.get(user.getId());

                for (LocalDate date : workDates) {
                    if (preferredDays.contains(date.getDayOfWeek())) {
                        objective.addTerm(-0.4, x.get(variableKey(user.getId(), date)));
                    }
                }

                for (int wi = 0; wi < orderedWeekKeys.size() - 1; wi++) {
                    String wk1 = orderedWeekKeys.get(wi);
                    String wk2 = orderedWeekKeys.get(wi + 1);

                    GRBLinExpr week1Sum = new GRBLinExpr();
                    for (LocalDate d : datesByWeek.get(wk1)) {
                        week1Sum.addTerm(1.0, x.get(variableKey(user.getId(), d)));
                    }

                    GRBLinExpr week2Sum = new GRBLinExpr();
                    for (LocalDate d : datesByWeek.get(wk2)) {
                        week2Sum.addTerm(1.0, x.get(variableKey(user.getId(), d)));
                    }

                    GRBVar diffVar = model.addVar(0.0, GRB.INFINITY, 0.0, GRB.CONTINUOUS,
                            "dist_" + user.getId() + "_" + wk1 + "_" + wk2);

                    GRBLinExpr lhs1 = new GRBLinExpr();
                    lhs1.add(week1Sum);
                    lhs1.multAdd(-1.0, week2Sum);
                    lhs1.addTerm(-1.0, diffVar);
                    model.addConstr(lhs1, GRB.LESS_EQUAL, 0.0,
                            "DistPos_" + user.getId() + "_" + wk1 + "_" + wk2);

                    GRBLinExpr lhs2 = new GRBLinExpr();
                    lhs2.add(week2Sum);
                    lhs2.multAdd(-1.0, week1Sum);
                    lhs2.addTerm(-1.0, diffVar);
                    model.addConstr(lhs2, GRB.LESS_EQUAL, 0.0,
                            "DistNeg_" + user.getId() + "_" + wk1 + "_" + wk2);

                    objective.addTerm(-0.2, diffVar);
                }

                for (int di = 0; di < workDates.size() - 1; di++) {
                    LocalDate today = workDates.get(di);
                    LocalDate tomorrow = workDates.get(di + 1);
                    if (ChronoUnit.DAYS.between(today, tomorrow) == 1) {
                        objective.addTerm(-0.1, x.get(variableKey(user.getId(), today)));
                        objective.addTerm(-0.1, x.get(variableKey(user.getId(), tomorrow)));
                    }
                }
            }

            model.setObjective(objective, GRB.MAXIMIZE);

            log.info("Optimization run {} — starting Gurobi solve ({} users, {} dates)",
                    run.getId(), users.size(), workDates.size());

            model.optimize();

            int status = model.get(GRB.IntAttr.Status);

            run.setGurobiStatusCode(status);
            run.setGurobiStatusLabel(resolveStatusLabel(status));

            if (status != GRB.OPTIMAL) {
                String message;
                if (status == GRB.INFEASIBLE) {
                    try {
                        model.computeIIS();
                        GRBConstr[] constrs = model.getConstrs();

                        List<String> capacityDates = new ArrayList<>();
                        Map<String, List<String>> minOfficeByWeek = new LinkedHashMap<>();
                        Map<String, List<String>> maxOfficeByWeek = new LinkedHashMap<>();
                        List<String> consecutiveUsers = new ArrayList<>();
                        List<String> teamSharedDaysIssues = new ArrayList<>();
                        Map<String, List<String>> coPresenceIssues = new LinkedHashMap<>();
                        List<String> dateRangeWarnings = new ArrayList<>();

                        Map<Long, User> userMap = users.stream().collect(Collectors.toMap(User::getId, u -> u));
                        Map<Long, Team> teamMap = teams.stream().collect(Collectors.toMap(Team::getId, t -> t));

                        for (GRBConstr constr : constrs) {
                            if (constr.get(GRB.IntAttr.IISConstr) > 0) {
                                String name = constr.get(GRB.StringAttr.ConstrName);
                                if (name == null)
                                    continue;

                                if (name.startsWith("Capacity_")) {
                                    String dateStr = name.substring("Capacity_".length());
                                    capacityDates.add(dateStr);
                                } else if (name.startsWith("MinOffice_")) {
                                    String details = name.substring("MinOffice_".length());
                                    if (details.startsWith("User_")) {
                                        String sub = details.substring("User_".length());
                                        int underscoreIdx = sub.indexOf('_');
                                        if (underscoreIdx != -1) {
                                            String userIdStr = sub.substring(0, underscoreIdx);
                                            String weekKey = sub.substring(underscoreIdx + 1);
                                            try {
                                                Long userId = Long.parseLong(userIdStr);
                                                User user = userMap.get(userId);
                                                String emailDisplay = user != null ? user.getEmail()
                                                        : "User ID " + userId;
                                                List<LocalDate> weekDates = datesByWeek.get(weekKey);
                                                int weekDatesCount = weekDates != null ? weekDates.size() : 0;

                                                if (weekDatesCount < policy.getMinOfficeDaysPerWeek()) {
                                                    dateRangeWarnings.add(String.format(
                                                            "Week %s only has %d working day(s) in the selected date range, which is less than the required %d office days per week (conflict for %s).",
                                                            weekKey, weekDatesCount, policy.getMinOfficeDaysPerWeek(),
                                                            emailDisplay));
                                                } else {
                                                    minOfficeByWeek.computeIfAbsent(weekKey, k -> new ArrayList<>())
                                                            .add(emailDisplay);
                                                }
                                            } catch (NumberFormatException ignored) {
                                            }
                                        }
                                    }
                                } else if (name.startsWith("MaxOffice_")) {
                                    String details = name.substring("MaxOffice_".length());
                                    if (details.startsWith("User_")) {
                                        String sub = details.substring("User_".length());
                                        int underscoreIdx = sub.indexOf('_');
                                        if (underscoreIdx != -1) {
                                            String userIdStr = sub.substring(0, underscoreIdx);
                                            String weekKey = sub.substring(underscoreIdx + 1);
                                            try {
                                                Long userId = Long.parseLong(userIdStr);
                                                User user = userMap.get(userId);
                                                String emailDisplay = user != null ? user.getEmail()
                                                        : "User ID " + userId;
                                                maxOfficeByWeek.computeIfAbsent(weekKey, k -> new ArrayList<>())
                                                        .add(emailDisplay);
                                            } catch (NumberFormatException ignored) {
                                            }
                                        }
                                    }
                                } else if (name.startsWith("MaxConsecutive_")) {
                                    String details = name.substring("MaxConsecutive_".length());
                                    if (details.startsWith("User_")) {
                                        String sub = details.substring("User_".length());
                                        int underscoreIdx = sub.indexOf('_');
                                        if (underscoreIdx != -1) {
                                            String userIdStr = sub.substring(0, underscoreIdx);
                                            try {
                                                Long userId = Long.parseLong(userIdStr);
                                                User user = userMap.get(userId);
                                                String emailDisplay = user != null ? user.getEmail()
                                                        : "User ID " + userId;
                                                consecutiveUsers.add(emailDisplay);
                                            } catch (NumberFormatException ignored) {
                                            }
                                        }
                                    }
                                } else if (name.startsWith("MinSharedDays_Team_")) {
                                    String teamIdStr = name.substring("MinSharedDays_Team_".length());
                                    try {
                                        Long teamId = Long.parseLong(teamIdStr);
                                        Team team = teamMap.get(teamId);
                                        String teamName = team != null ? team.getName() : "Team ID " + teamId;
                                        teamSharedDaysIssues.add(teamName);
                                    } catch (NumberFormatException ignored) {
                                    }
                                } else if (name.startsWith("CoPresenceLower_") || name.startsWith("CoPresenceUpper_")) {
                                    boolean isLower = name.startsWith("CoPresenceLower_");
                                    String details = name.substring(
                                            isLower ? "CoPresenceLower_".length() : "CoPresenceUpper_".length());
                                    int underscore = details.indexOf('_');
                                    if (underscore != -1) {
                                        String teamIdStr = details.substring(0, underscore);
                                        String dateStr = details.substring(underscore + 1);
                                        try {
                                            Long teamId = Long.parseLong(teamIdStr);
                                            Team team = teamMap.get(teamId);
                                            String teamName = team != null ? team.getName() : "Team ID " + teamId;
                                            String label = isLower ? "minimum copresence limit"
                                                    : "maximum copresence limit";
                                            coPresenceIssues.computeIfAbsent(dateStr, k -> new ArrayList<>())
                                                    .add(String.format("'%s' (%s)", teamName, label));
                                        } catch (NumberFormatException ignored) {
                                        }
                                    }
                                }
                            }
                        }

                        StringBuilder sb = new StringBuilder();
                        sb.append(
                                "Schedule generation is not possible because the requirements conflict with each other. Here is what is causing the issue:\n");
                        boolean hasIssue = false;

                        if (!dateRangeWarnings.isEmpty()) {
                            hasIssue = true;
                            sb.append("\n- Date Range / Calendar Alignment:\n");
                            List<String> uniqueWarnings = dateRangeWarnings.stream().distinct().sorted()
                                    .collect(Collectors.toList());
                            for (String warn : uniqueWarnings) {
                                sb.append("  * ").append(warn).append("\n");
                            }
                        }

                        if (!capacityDates.isEmpty()) {
                            hasIssue = true;
                            sb.append(String.format("\n- Office Capacity Limits (Max %d):\n", office.getMaxCapacity()));
                            List<String> uniqueDates = capacityDates.stream().distinct().sorted()
                                    .collect(Collectors.toList());
                            sb.append("  * Capacity constraint is too restrictive on: ")
                                    .append(String.join(", ", uniqueDates)).append("\n");
                        }

                        if (!minOfficeByWeek.isEmpty()) {
                            hasIssue = true;
                            sb.append(String.format("\n- Weekly Minimum Office Days (%d days required):\n",
                                    policy.getMinOfficeDaysPerWeek()));
                            List<String> sortedWeekKeys = minOfficeByWeek.keySet().stream().sorted()
                                    .collect(Collectors.toList());
                            for (String weekKey : sortedWeekKeys) {
                                List<String> userEmails = minOfficeByWeek.get(weekKey);
                                if (userEmails != null && !userEmails.isEmpty()) {
                                    List<String> uniqueEmails = userEmails.stream().distinct().sorted()
                                            .collect(Collectors.toList());
                                    sb.append("  * In week ").append(weekKey).append(" for: ")
                                            .append(String.join(", ", uniqueEmails)).append("\n");
                                }
                            }
                        }

                        if (!maxOfficeByWeek.isEmpty()) {
                            hasIssue = true;
                            sb.append(String.format("\n- Weekly Maximum Office Days (Max %d days allowed):\n",
                                    policy.getMaxOfficeDaysPerWeek()));
                            List<String> sortedWeekKeys = maxOfficeByWeek.keySet().stream().sorted()
                                    .collect(Collectors.toList());
                            for (String weekKey : sortedWeekKeys) {
                                List<String> userEmails = maxOfficeByWeek.get(weekKey);
                                if (userEmails != null && !userEmails.isEmpty()) {
                                    List<String> uniqueEmails = userEmails.stream().distinct().sorted()
                                            .collect(Collectors.toList());
                                    sb.append("  * In week ").append(weekKey).append(" for: ")
                                            .append(String.join(", ", uniqueEmails)).append("\n");
                                }
                            }
                        }

                        if (!consecutiveUsers.isEmpty()) {
                            hasIssue = true;
                            sb.append(String.format("\n- Maximum Consecutive Office Days (Max %d days allowed):\n",
                                    policy.getMaxConsecutiveOfficeDays()));
                            List<String> uniqueUsers = consecutiveUsers.stream().distinct().sorted()
                                    .collect(Collectors.toList());
                            sb.append("  * Constraint violated for: ").append(String.join(", ", uniqueUsers))
                                    .append("\n");
                        }

                        if (!teamSharedDaysIssues.isEmpty()) {
                            hasIssue = true;
                            sb.append(String.format("\n- Minimum Team Shared Days (%d days required):\n",
                                    policy.getMinTeamSharedDays()));
                            List<String> uniqueTeams = teamSharedDaysIssues.stream().distinct().sorted()
                                    .collect(Collectors.toList());
                            sb.append("  * The following teams could not meet this requirement: ")
                                    .append(String.join(", ", uniqueTeams)).append("\n");
                        }

                        if (!coPresenceIssues.isEmpty()) {
                            hasIssue = true;
                            sb.append("\n- Team Co-Presence Thresholds:\n");
                            List<String> sortedDates = coPresenceIssues.keySet().stream().sorted()
                                    .collect(Collectors.toList());
                            for (String d : sortedDates) {
                                List<String> issues = coPresenceIssues.get(d).stream().distinct().sorted()
                                        .collect(Collectors.toList());
                                sb.append("  * On ").append(d).append(": ").append(String.join(", ", issues))
                                        .append("\n");
                            }
                        }

                        if (!hasIssue) {
                            message = "Model is infeasible. The selected policy constraints cannot all be satisfied for the chosen teams, office capacity, and date range.";
                        } else {
                            message = sb.toString().trim();
                        }
                    } catch (Exception iisEx) {
                        log.error("Failed to compute or parse Gurobi IIS: {}", iisEx.getMessage(), iisEx);
                        message = "Model is infeasible. The selected policy constraints cannot all be satisfied. (IIS computation failed: "
                                + iisEx.getMessage() + ")";
                    }
                } else {
                    message = "Gurobi did not find an optimal solution. Status code: " + status;
                }

                runRepository.save(run);
                persistenceService.markFailed(run, message);
                return;
            }

            run.setObjectiveValue(model.get(GRB.DoubleAttr.ObjVal));
            run.setObjectiveBound(model.get(GRB.DoubleAttr.ObjBound));
            run.setMipGap(model.get(GRB.DoubleAttr.MIPGap));
            run.setRuntimeSeconds(model.get(GRB.DoubleAttr.Runtime));
            run.setNumVariables(model.get(GRB.IntAttr.NumVars));
            run.setNumConstraints(model.get(GRB.IntAttr.NumConstrs));
            run.setNumIterations(model.get(GRB.DoubleAttr.IterCount));
            run.setNumNodes(model.get(GRB.DoubleAttr.NodeCount));

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

            model.dispose();
            model = null;
            env.dispose();
            env = null;
            persistenceService.saveResults(
                    run, office, teams, users, workDates,
                    solution, policy,
                    request.getStartDate(), request.getEndDate());

        } catch (GRBException e) {
            log.error("Gurobi exception in optimization run {}: {}", run.getId(), e.getMessage(), e);
            persistenceService.markFailed(run, "Gurobi error: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error in optimization run {}: {}", run.getId(), e.getMessage(), e);
            persistenceService.markFailed(run, "Unexpected error: " + e.getMessage());
        } finally {
            if (model != null) {
                try {
                    model.dispose();
                } catch (Exception ignored) {
                }
            }
            if (env != null) {
                try {
                    env.dispose();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private String variableKey(Long userId, LocalDate date) {
        return userId + "_" + date;
    }

    private List<LocalDate> buildWorkDates(LocalDate startDate, LocalDate endDate) {
        long numDays = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        List<LocalDate> workDates = new ArrayList<>();
        for (int i = 0; i < numDays; i++) {
            LocalDate date = startDate.plusDays(i);
            DayOfWeek dow = date.getDayOfWeek();
            if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) {
                workDates.add(date);
            }
        }
        return workDates;
    }

    private Map<LocalDate, Long> calculateExistingOfficeUsageByDate(
            Long officeId, LocalDate startDate, LocalDate endDate) {
        return scheduleEntryRepository
                .findPublishedEntriesForOffice(officeId, startDate, endDate)
                .stream()
                .filter(entry -> entry.getWorkMode() == WorkMode.OFFICE)
                .collect(Collectors.groupingBy(ScheduleEntry::getDate, Collectors.counting()));
    }

    private String resolveStatusLabel(int status) {
        return switch (status) {
            case GRB.OPTIMAL -> "OPTIMAL";
            case GRB.INFEASIBLE -> "INFEASIBLE";
            case GRB.INF_OR_UNBD -> "INF_OR_UNBD";
            case GRB.UNBOUNDED -> "UNBOUNDED";
            case GRB.TIME_LIMIT -> "TIME_LIMIT";
            case GRB.ITERATION_LIMIT -> "ITERATION_LIMIT";
            case GRB.NODE_LIMIT -> "NODE_LIMIT";
            default -> "UNKNOWN_" + status;
        };
    }
}
