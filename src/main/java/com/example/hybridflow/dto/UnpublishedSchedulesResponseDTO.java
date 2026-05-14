package com.example.hybridflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Top-level response for GET /api/schedules/unpublished.
 *
 * Contains every draft schedule for the HR user's company together with
 * their fairness scores so the HR user can review them before deciding
 * whether to publish or delete them all.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnpublishedSchedulesResponseDTO {

    /** Total number of unpublished schedules found. */
    private int count;

    /**
     * Aggregate overall fairness score across ALL unpublished schedules
     * for the company (0–100).  Mirrors the value that would be shown
     * right after generation.
     */
    private double overallFairnessScore;

    /** One entry per unpublished schedule (one per team). */
    private List<UnpublishedScheduleDTO> schedules;
}