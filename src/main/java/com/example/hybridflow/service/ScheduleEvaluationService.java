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

    // ── Internal helpers (unchanged from original) ────────────────────────────

    private List<IndividualFairnessDTO> calculateIndividualFairness(List<User> users,
            Map<Long, List<ScheduleEntry>> userScheduleEntries, PlanningPolicy policy, LocalDate startDate,
            LocalDate endDate) {
        List<IndividualFairnessDTO> scores = new ArrayList<>();
        for (User user : users) {
            List<ScheduleEntry> entries = userScheduleEntries.getOrDefault(user.getId(), Collections.emptyList());
            Map<String, String> breakdown = new HashMap<>();
            double score = 0.0;

            // 1. Office/Online Day Balance & Difference from Target
            long officeDays = entries.stream().filter(e -> e.getWorkMode() == WorkMode.OFFICE).count();
            long totalWorkDays = entries.size();

            double officeDayBalanceScore = 0.0;
            if (totalWorkDays > 0) {
                double targetMin = policy.getMinOfficeDaysPerWeek();
                double targetMax = policy.getMaxOfficeDaysPerWeek();

                if (officeDays >= targetMin && officeDays <= targetMax) {
                    officeDayBalanceScore = 1.0;
                } else if (officeDays < targetMin) {
                    officeDayBalanceScore = 1.0 - (targetMin - officeDays) / targetMin;
                } else {
                    officeDayBalanceScore = 1.0 - (officeDays - targetMax) / targetMax;
                }
                officeDayBalanceScore = Math.max(0, officeDayBalanceScore);
            }
            breakdown.put("officeDayBalance", String.format("%.2f", officeDayBalanceScore));

            // 2. Preferred Work Days Satisfaction
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

            // 3. Weekly Distribution Balance
            double distributionBalanceScore = 1.0;
            if (totalWorkDays > 0 && officeDays > 0) {
                long minDay = entries.stream().filter(e -> e.getWorkMode() == WorkMode.OFFICE)
                        .map(ScheduleEntry::getDate).mapToLong(d -> d.toEpochDay()).min().orElse(0);
                long maxDay = entries.stream().filter(e -> e.getWorkMode() == WorkMode.OFFICE)
                        .map(ScheduleEntry::getDate).mapToLong(d -> d.toEpochDay()).max().orElse(0);
                long span = maxDay - minDay + 1;
                if (span > 0) {
                    distributionBalanceScore = (double) officeDays / span;
                }
            }
            breakdown.put("distributionBalance", String.format("%.2f", distributionBalanceScore));

            score = (officeDayBalanceScore * 0.4 + preferenceSatisfactionScore * 0.4 + distributionBalanceScore * 0.2)
                    * 100;
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