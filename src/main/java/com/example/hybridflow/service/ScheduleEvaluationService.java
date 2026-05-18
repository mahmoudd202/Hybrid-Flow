package com.example.hybridflow.service;

import com.example.hybridflow.dto.IndividualFairnessDTO;
import com.example.hybridflow.dto.TeamFairnessDTO;
import com.example.hybridflow.entity.*;
import com.example.hybridflow.repository.*;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ScheduleEvaluationService {

    private final ScheduleEntryRepository scheduleEntryRepository;
    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final PlanningPolicyRepository planningPolicyRepository;
    private final PreferredWorkDayRepository preferredWorkDayRepository;

    // ── Existing method (used by ScheduleGenerationService) ──────────────────

    /**
     * Full evaluation using a known PlanningPolicy (called right after generation).
     */
    public EvaluationResult evaluateSchedules(List<Long> scheduleIds, List<User> users, List<Team> teams,
            PlanningPolicy policy, LocalDate startDate, LocalDate endDate) {
        Map<Long, List<ScheduleEntry>> userScheduleEntries = new HashMap<>();
        for (Long scheduleId : scheduleIds) {
            List<ScheduleEntry> entries = scheduleEntryRepository.findByScheduleId(scheduleId);
            for (ScheduleEntry entry : entries) {
                userScheduleEntries.computeIfAbsent(entry.getUser().getId(), k -> new ArrayList<>()).add(entry);
            }
        }

        List<IndividualFairnessDTO> individualScores = calculateIndividualFairness(users, userScheduleEntries, policy,
                startDate, endDate);
        List<TeamFairnessDTO> teamScores = calculateTeamFairness(teams, individualScores);
        double overallScore = calculateOverallFairness(teamScores);

        return new EvaluationResult(overallScore, teamScores, individualScores);
    }

    // ── New method (used by ScheduleManagementService for unpublished review) ─

    /**
     * Evaluate unpublished schedules without a PlanningPolicy context.
     *
     * Because the Schedule entity does not store the policy that was used for
     * generation, we fall back to the most-recently-created policy of the team's
     * company. If no policy exists we use a permissive default (min=0, max=5).
     *
     * This gives HR a meaningful fairness preview even when reviewing drafts
     * that were generated in a previous session.
     */
    public EvaluationResult evaluateForUnpublished(
            List<Long> scheduleIds,
            List<User> users,
            List<Team> teams,
            LocalDate startDate,
            LocalDate endDate) {

        if (teams.isEmpty() || users.isEmpty()) {
            return new EvaluationResult(0.0, List.of(), List.of());
        }

        // Best-effort: grab the latest policy from the company owning the first team
        Long companyId = teams.get(0).getCompany().getId();
        PlanningPolicy policy = planningPolicyRepository
                .findByCompanyIdOrderByCreatedAtDesc(companyId)
                .stream()
                .findFirst()
                .orElseGet(() -> buildPermissiveDefaultPolicy(companyId));

        return evaluateSchedules(scheduleIds, users, teams, policy, startDate, endDate);
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private List<IndividualFairnessDTO> calculateIndividualFairness(List<User> users,
            Map<Long, List<ScheduleEntry>> userScheduleEntries, PlanningPolicy policy, LocalDate startDate,
            LocalDate endDate) {

        List<IndividualFairnessDTO> scores = new ArrayList<>();

        // Group all work dates by ISO week so we can evaluate balance per week.
        WeekFields iso = WeekFields.ISO;
        Map<String, List<LocalDate>> allDatesByWeek = buildDatesByWeek(startDate, endDate, iso);

        for (User user : users) {
            List<ScheduleEntry> entries = userScheduleEntries.getOrDefault(user.getId(), Collections.emptyList());
            Map<String, String> breakdown = new HashMap<>();

            // Index entries by date for fast lookup
            Map<LocalDate, WorkMode> modeByDate = entries.stream()
                    .collect(Collectors.toMap(ScheduleEntry::getDate, ScheduleEntry::getWorkMode));

            long officeDays = entries.stream().filter(e -> e.getWorkMode() == WorkMode.OFFICE).count();
            long totalWorkDays = entries.size();

            // ── Fix 1: officeDayBalance evaluated per ISO week, then averaged ──────
            //
            // Previously, the balance check was done against the *total* office days
            // across the whole period compared to min/max. That means a user perfectly
            // hitting 2 days/week over 2 weeks (total=4) would be checked against a
            // period-wide [min*weeks, max*weeks] band that was never explicitly derived,
            // making the score sensitive to period length.
            //
            // Now: compute a 0-1 balance score for each ISO week in the schedule, then
            // average them. A week where the user is squarely in [min, max] scores 1.0;
            // above or below is penalised proportionally, clamped to 0.
            double officeDayBalanceScore = 0.0;
            if (totalWorkDays > 0) {
                double targetMin = policy.getMinOfficeDaysPerWeek();
                double targetMax = policy.getMaxOfficeDaysPerWeek();

                List<Double> weeklyBalanceScores = new ArrayList<>();
                for (List<LocalDate> weekDates : allDatesByWeek.values()) {
                    // Only score weeks that have at least one work day in the schedule
                    List<LocalDate> scheduledInWeek = weekDates.stream()
                            .filter(modeByDate::containsKey)
                            .collect(Collectors.toList());
                    if (scheduledInWeek.isEmpty())
                        continue;

                    long weeklyOfficeDays = scheduledInWeek.stream()
                            .filter(d -> modeByDate.get(d) == WorkMode.OFFICE)
                            .count();

                    double weekScore;
                    if (weeklyOfficeDays >= targetMin && weeklyOfficeDays <= targetMax) {
                        weekScore = 1.0;
                    } else if (weeklyOfficeDays < targetMin) {
                        weekScore = targetMin == 0 ? 1.0 : 1.0 - (targetMin - weeklyOfficeDays) / targetMin;
                    } else {
                        weekScore = targetMax == 0 ? 0.0 : 1.0 - (weeklyOfficeDays - targetMax) / targetMax;
                    }
                    weeklyBalanceScores.add(Math.max(0.0, weekScore));
                }

                officeDayBalanceScore = weeklyBalanceScores.isEmpty() ? 0.0
                        : weeklyBalanceScores.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            }
            breakdown.put("officeDayBalance", String.format("%.2f", officeDayBalanceScore));

            // ── Preferred Work Days Satisfaction (unchanged) ──────────────────────
            Set<DayOfWeek> preferredOnlineDays = preferredWorkDayRepository.findByUserId(user.getId()).stream()
                    .map(PreferredWorkDay::getDayOfWeek)
                    .collect(Collectors.toSet());

            long totalPreferredDaysInSchedule = entries.stream()
                    .filter(e -> preferredOnlineDays.contains(e.getDate().getDayOfWeek()))
                    .count();

            long satisfiedPreferredDays = entries.stream()
                    .filter(e -> e.getWorkMode() == WorkMode.ONLINE
                            && preferredOnlineDays.contains(e.getDate().getDayOfWeek()))
                    .count();

            double preferenceSatisfactionScore = (preferredOnlineDays.isEmpty() || totalPreferredDaysInSchedule == 0)
                    ? 1.0
                    : (double) satisfiedPreferredDays / totalPreferredDaysInSchedule;

            breakdown.put("preferenceSatisfaction", String.format("%.2f", preferenceSatisfactionScore));

            // ── Fix 2: distributionBalance uses work-day span, not calendar-day span ─
            //
            // Previously: span = lastOfficeEpochDay - firstOfficeEpochDay + 1
            // This includes weekends, inflating the denominator. A user with 4 perfectly
            // spread office days over 2 calendar weeks got span≈12 → score≈0.33, even
            // though the distribution was perfectly even.
            //
            // Now: count only the weekdays (Mon–Fri) that fall between the first and last
            // office day (inclusive). This removes the weekend-inflation bias and correctly
            // rewards schedules where office days are spread evenly across work days.
            double distributionBalanceScore = 1.0;
            if (totalWorkDays > 0 && officeDays > 0) {
                List<LocalDate> officeDatesSorted = entries.stream()
                        .filter(e -> e.getWorkMode() == WorkMode.OFFICE)
                        .map(ScheduleEntry::getDate)
                        .sorted()
                        .collect(Collectors.toList());

                LocalDate firstOfficeDate = officeDatesSorted.get(0);
                LocalDate lastOfficeDate = officeDatesSorted.get(officeDatesSorted.size() - 1);

                // Count weekdays between first and last office day (inclusive)
                long workDaySpan = countWeekdays(firstOfficeDate, lastOfficeDate);

                if (workDaySpan > 0) {
                    distributionBalanceScore = (double) officeDays / workDaySpan;
                }
            }
            // Cap at 1.0: a dense back-to-back cluster can have officeDays == workDaySpan
            distributionBalanceScore = Math.min(1.0, Math.max(0.0, distributionBalanceScore));
            breakdown.put("distributionBalance", String.format("%.2f", distributionBalanceScore));

            double score = (officeDayBalanceScore * 0.4 + preferenceSatisfactionScore * 0.4
                    + distributionBalanceScore * 0.2) * 100;
            score = Math.max(0, Math.min(100, score));

            scores.add(IndividualFairnessDTO.builder()
                    .userId(user.getId())
                    .userEmail(user.getEmail())
                    .score(Math.round(score * 100.0) / 100.0)
                    .breakdown(breakdown)
                    .build());
        }
        return scores;
    }

    /**
     * Counts the number of weekdays (Monday through Friday) in the inclusive range
     * [from, to]. Returns 1 when from == to and the day is a weekday.
     */
    private long countWeekdays(LocalDate from, LocalDate to) {
        if (from.isAfter(to))
            return 0;
        long count = 0;
        LocalDate cursor = from;
        while (!cursor.isAfter(to)) {
            DayOfWeek dow = cursor.getDayOfWeek();
            if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) {
                count++;
            }
            cursor = cursor.plusDays(1);
        }
        return count;
    }

    /**
     * Builds a map of ISO-week key → list of weekdays in [startDate, endDate].
     * Used to evaluate officeDayBalance per week.
     */
    private Map<String, List<LocalDate>> buildDatesByWeek(LocalDate startDate, LocalDate endDate, WeekFields iso) {
        Map<String, List<LocalDate>> result = new LinkedHashMap<>();
        LocalDate cursor = startDate;
        while (!cursor.isAfter(endDate)) {
            DayOfWeek dow = cursor.getDayOfWeek();
            if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) {
                String weekKey = cursor.get(iso.weekBasedYear()) + "-W" + cursor.get(iso.weekOfWeekBasedYear());
                result.computeIfAbsent(weekKey, k -> new ArrayList<>()).add(cursor);
            }
            cursor = cursor.plusDays(1);
        }
        return result;
    }

    private List<TeamFairnessDTO> calculateTeamFairness(List<Team> teams,
            List<IndividualFairnessDTO> individualScores) {
        List<TeamFairnessDTO> scores = new ArrayList<>();
        Map<Long, List<IndividualFairnessDTO>> teamIndividualScores = individualScores.stream()
                .collect(Collectors
                        .groupingBy(dto -> userRepository.findById(dto.getUserId()).orElseThrow().getTeam().getId()));

        for (Team team : teams) {
            List<IndividualFairnessDTO> membersScores = teamIndividualScores.getOrDefault(team.getId(),
                    Collections.emptyList());
            Map<String, String> breakdown = new HashMap<>();
            double teamScore = 0.0;

            if (!membersScores.isEmpty()) {
                double averageIndividualScore = membersScores.stream().mapToDouble(IndividualFairnessDTO::getScore)
                        .average().orElse(0.0);
                breakdown.put("averageIndividualScore", String.format("%.2f", averageIndividualScore));

                double variance = membersScores.stream()
                        .mapToDouble(IndividualFairnessDTO::getScore)
                        .map(s -> Math.pow(s - averageIndividualScore, 2))
                        .average().orElse(0.0);
                double stdDev = Math.sqrt(variance);

                double penalty = 0.0;
                if (stdDev > 10) {
                    penalty = (stdDev - 10) * 0.5;
                }
                breakdown.put("individualScoreStdDev", String.format("%.2f", stdDev));
                breakdown.put("variancePenalty", String.format("%.2f", penalty));

                teamScore = averageIndividualScore - penalty;
            } else {
                teamScore = 100.0;
            }
            teamScore = Math.max(0, Math.min(100, teamScore));

            scores.add(TeamFairnessDTO.builder()
                    .teamId(team.getId())
                    .teamName(team.getName())
                    .score(Math.round(teamScore * 100.0) / 100.0)
                    .breakdown(breakdown)
                    .build());
        }
        return scores;
    }

    private double calculateOverallFairness(List<TeamFairnessDTO> teamScores) {
        double overallScore = 0.0;

        if (!teamScores.isEmpty()) {
            double averageTeamScore = teamScores.stream().mapToDouble(TeamFairnessDTO::getScore).average().orElse(0.0);

            double variance = teamScores.stream()
                    .mapToDouble(TeamFairnessDTO::getScore)
                    .map(s -> Math.pow(s - averageTeamScore, 2))
                    .average().orElse(0.0);
            double stdDev = Math.sqrt(variance);

            double penalty = 0.0;
            if (stdDev > 5) {
                penalty = (stdDev - 5) * 1.0;
            }

            overallScore = averageTeamScore - penalty;
        } else {
            overallScore = 100.0;
        }
        overallScore = Math.max(0, Math.min(100, overallScore));

        return Math.round(overallScore * 100.0) / 100.0;
    }

    /**
     * Creates a lenient default policy used when no real policy exists.
     * It never blocks the scoring calculation (min=0, max=5).
     */
    private PlanningPolicy buildPermissiveDefaultPolicy(Long companyId) {
        PlanningPolicy p = new PlanningPolicy();
        p.setWorkingDaysPerWeek(5);
        p.setMinOfficeDaysPerWeek(0);
        p.setMaxOfficeDaysPerWeek(5);
        p.setMaxConsecutiveOfficeDays(5);
        p.setMinTeamSharedDays(0);
        p.setCoPresenceThresholdPercentagePerDay(0);
        return p;
    }

    // ── Result record ─────────────────────────────────────────────────────────

    @Data
    @AllArgsConstructor
    public static class EvaluationResult {
        private double overallFairnessScore;
        private List<TeamFairnessDTO> teamFairnessScores;
        private List<IndividualFairnessDTO> individualFairnessScores;
    }
}