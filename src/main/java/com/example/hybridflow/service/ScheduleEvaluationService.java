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
    private final PlanningPolicyRepository planningPolicyRepository;
    private final PreferredWorkDayRepository preferredWorkDayRepository;

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

    public EvaluationResult evaluateForUnpublished(
            List<Long> scheduleIds,
            List<User> users,
            List<Team> teams,
            LocalDate startDate,
            LocalDate endDate) {

        if (teams.isEmpty() || users.isEmpty()) {
            return new EvaluationResult(0.0, List.of(), List.of());
        }

        Long companyId = teams.get(0).getCompany().getId();
        PlanningPolicy policy = planningPolicyRepository
                .findByCompanyIdOrderByCreatedAtDesc(companyId)
                .stream()
                .findFirst()
                .orElseGet(() -> buildPermissiveDefaultPolicy(companyId));

        return evaluateSchedules(scheduleIds, users, teams, policy, startDate, endDate);
    }

    private List<IndividualFairnessDTO> calculateIndividualFairness(List<User> users,
            Map<Long, List<ScheduleEntry>> userScheduleEntries, PlanningPolicy policy, LocalDate startDate,
            LocalDate endDate) {

        List<IndividualFairnessDTO> scores = new ArrayList<>();

        WeekFields iso = WeekFields.ISO;
        Map<String, List<LocalDate>> allDatesByWeek = buildDatesByWeek(startDate, endDate, iso);

        for (User user : users) {
            List<ScheduleEntry> entries = userScheduleEntries.getOrDefault(user.getId(), Collections.emptyList());
            Map<String, String> breakdown = new HashMap<>();

            Map<LocalDate, WorkMode> modeByDate = entries.stream()
                    .collect(Collectors.toMap(ScheduleEntry::getDate, ScheduleEntry::getWorkMode));

            long officeDays = entries.stream().filter(e -> e.getWorkMode() == WorkMode.OFFICE).count();
            long totalWorkDays = entries.size();

            double officeDayBalanceScore = 0.0;
            if (totalWorkDays > 0) {
                double targetMin = policy.getMinOfficeDaysPerWeek();
                double targetMax = policy.getMaxOfficeDaysPerWeek();

                List<Double> weeklyBalanceScores = new ArrayList<>();
                for (List<LocalDate> weekDates : allDatesByWeek.values()) {
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

            double distributionBalanceScore = 1.0;
            if (totalWorkDays > 0 && officeDays > 0) {
                List<LocalDate> officeDatesSorted = entries.stream()
                        .filter(e -> e.getWorkMode() == WorkMode.OFFICE)
                        .map(ScheduleEntry::getDate)
                        .sorted()
                        .collect(Collectors.toList());

                LocalDate firstOfficeDate = officeDatesSorted.get(0);
                LocalDate lastOfficeDate = officeDatesSorted.get(officeDatesSorted.size() - 1);

                long workDaySpan = countWeekdays(firstOfficeDate, lastOfficeDate);

                if (workDaySpan > 0) {
                    distributionBalanceScore = (double) officeDays / workDaySpan;
                }
            }
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

    @Data
    @AllArgsConstructor
    public static class EvaluationResult {
        private double overallFairnessScore;
        private List<TeamFairnessDTO> teamFairnessScores;
        private List<IndividualFairnessDTO> individualFairnessScores;
    }
}