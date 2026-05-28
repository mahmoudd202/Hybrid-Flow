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

/**
 * Runs the Gurobi optimisation in a dedicated background thread.
 *
 * Must be a separate Spring bean from ScheduleGenerationService so that
 * Spring's @Async proxy intercepts the call. Self-invocation inside the
 * same bean bypasses the proxy and executes synchronously.
 *
 * The Gurobi model is built and solved OUTSIDE any database transaction
 * (no @Transactional here) so no DB connection is held during the solve.
 * Persistence is delegated to ScheduleGenerationPersistenceService which
 * opens a short-lived transaction only when the solution is ready.
 *
 * FAILED / INFEASIBLE handling:
 *   Any exception or non-OPTIMAL status calls persistenceService.markFailed()
 *   with a human-readable message. The polling endpoint returns:
 *     { jobStatus: "FAILED", errorMessage: "<reason>" }
 *   — identical semantics to the old synchronous status="FAILED" response.
 */
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

    /**
     * Async entry point — called by ScheduleGenerationService after creating
     * the PENDING run row and returning 202 to the client.
     *
     * All parameters are primitive-safe (IDs, not JPA-managed entities) so
     * they can safely cross the thread boundary without a detached-entity issue.
     */
    @Async("gurobiExecutor")
    public void runAsync(
            Long runId,
            ScheduleGenerationRequestDTO request,
            Long officeId,
            List<Long> teamIds,
            Long policyId) {

        // Load entities inside async thread to avoid detached entity & lazy loading issues
        ScheduleOptimizationRun run = runRepository.findById(runId)
                .orElseThrow(() -> new ResourceNotFoundException("Optimization run not found."));

        Office office = officeRepository.findById(officeId)
                .orElseThrow(() -> new ResourceNotFoundException("Office not found."));

        List<Team> teams = teamRepository.findAllById(teamIds);

        List<User> users = userRepository.findSchedulableUsersByTeamIds(teamIds);

        PlanningPolicy policy = planningPolicyRepository.findById(policyId)
                .orElseThrow(() -> new ResourceNotFoundException("Planning policy not found."));

        // ── Mark RUNNING ──────────────────────────────────────────────────────
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

            Map<LocalDate, Long> existingOfficeUsageByDate =
                    calculateExistingOfficeUsageByDate(
                            office.getId(), request.getStartDate(), request.getEndDate());

            // ── Build Gurobi model ────────────────────────────────────────────
            env = new GRBEnv(true);
            env.set("LogToConsole", "0");
            env.start();

            model = new GRBModel(env);
            model.set(GRB.StringAttr.ModelName, "HybridFlowSchedule");

            // Decision variable: x[userId_date] = 1 → user is OFFICE on that date
            Map<String, GRBVar> x = new HashMap<>();
            for (User user : users) {
                for (LocalDate date : workDates) {
                    String key = variableKey(user.getId(), date);
                    x.put(key, model.addVar(0.0, 1.0, 0.0, GRB.BINARY, "x_" + key));
                }
            }

            // Constraint 1: Office capacity per day (minus existing published usage)
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

            // Constraint 2: Min/max office days per ISO week
            WeekFields iso = WeekFields.ISO;
            Map<String, List<LocalDate>> datesByWeek = workDates.stream()
                    .collect(Collectors.groupingBy(date ->
                            date.get(iso.weekBasedYear()) + "-W" + date.get(iso.weekOfWeekBasedYear())));

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

            // Constraint 3: Max consecutive office days
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

            // Constraint 4: Team co-presence
            for (Team team : teams) {
                List<User> members = users.stream()
                        .filter(u -> u.getTeam() != null && u.getTeam().getId().equals(team.getId()))
                        .collect(Collectors.toList());

                if (members.isEmpty()) continue;

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

            // Objective: weighted multi-term (preference 0.4, distribution 0.2, clustering 0.2)
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

                // Term A: preference satisfaction (weight 0.4)
                for (LocalDate date : workDates) {
                    if (preferredDays.contains(date.getDayOfWeek())) {
                        objective.addTerm(-0.4, x.get(variableKey(user.getId(), date)));
                    }
                }

                // Term B: weekly distribution evenness (weight 0.2)
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

                // Term C: penalise consecutive office-day clusters (weight 0.2)
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

            // ── Capture raw stats before dispose() ───────────────────────────
            run.setGurobiStatusCode(status);
            run.setGurobiStatusLabel(resolveStatusLabel(status));

            if (status != GRB.OPTIMAL) {
                String message = status == GRB.INFEASIBLE
                        ? "Model is infeasible. The selected policy constraints cannot all be "
                        + "satisfied for the chosen teams, office capacity, and date range."
                        : "Gurobi did not find an optimal solution. Status code: " + status;

                // Persist stat we have (status) before marking failed
                runRepository.save(run);
                persistenceService.markFailed(run, message);
                return;
            }

            // OPTIMAL — capture all stats
            run.setObjectiveValue(model.get(GRB.DoubleAttr.ObjVal));
            run.setObjectiveBound(model.get(GRB.DoubleAttr.ObjBound));
            run.setMipGap(model.get(GRB.DoubleAttr.MIPGap));
            run.setRuntimeSeconds(model.get(GRB.DoubleAttr.Runtime));
            run.setNumVariables(model.get(GRB.IntAttr.NumVars));
            run.setNumConstraints(model.get(GRB.IntAttr.NumConstrs));
            run.setNumIterations(model.get(GRB.DoubleAttr.IterCount));
            run.setNumNodes(model.get(GRB.DoubleAttr.NodeCount));

            // Extract solution matrix BEFORE dispose()
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

            // ── Dispose Gurobi resources ──────────────────────────────────────
            model.dispose();
            model = null;
            env.dispose();
            env = null;

            // ── Hand off to persistence service (opens its own transaction) ──
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
                try { model.dispose(); } catch (Exception ignored) {}
            }
            if (env != null) {
                try { env.dispose(); } catch (Exception ignored) {}
            }
        }
    }

    // ── Helpers (duplicated from original service — kept local to avoid coupling) ──

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
            case GRB.OPTIMAL    -> "OPTIMAL";
            case GRB.INFEASIBLE -> "INFEASIBLE";
            case GRB.INF_OR_UNBD -> "INF_OR_UNBD";
            case GRB.UNBOUNDED  -> "UNBOUNDED";
            case GRB.TIME_LIMIT -> "TIME_LIMIT";
            case GRB.ITERATION_LIMIT -> "ITERATION_LIMIT";
            case GRB.NODE_LIMIT -> "NODE_LIMIT";
            default             -> "UNKNOWN_" + status;
        };
    }
}
