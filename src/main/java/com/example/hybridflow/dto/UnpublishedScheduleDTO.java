package com.example.hybridflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Represents a single unpublished (draft) schedule, including its per-team
 * fairness score and the individual member scores that make it up.
 *
 * Returned by GET /api/schedules/unpublished.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnpublishedScheduleDTO {

    private Long scheduleId;

    private Long teamId;
    private String teamName;

    private Long officeId;
    private String officeName;

    private LocalDate startDate;
    private LocalDate endDate;

    private LocalDateTime createdAt;

    // ── Fairness scores (re-evaluated on demand) ──────────────────────────────
    /** Overall fairness score across all teams in this batch (0–100). */
    private double overallFairnessScore;

    /** Per-team fairness breakdown for this schedule's team. */
    private TeamFairnessDTO teamFairnessScore;

    /** Per-member fairness scores for the team. */
    private List<IndividualFairnessDTO> individualFairnessScores;
}