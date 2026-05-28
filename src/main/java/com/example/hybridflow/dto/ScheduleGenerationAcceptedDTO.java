package com.example.hybridflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Returned by POST /api/schedules/generate (HTTP 202 Accepted).
 *
 * The generation now runs asynchronously. Poll GET /api/schedules/optimization-runs/{runId}
 * to check progress and retrieve the full result once jobStatus = "COMPLETED".
 *
 * If Gurobi finds the problem INFEASIBLE or any other error occurs, the polling
 * endpoint will return jobStatus = "FAILED" with an errorMessage — handle it the
 * same way you currently handle the synchronous status = "FAILED" response.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleGenerationAcceptedDTO {

    /** ID of the ScheduleOptimizationRun row — use this to poll for results. */
    private Long runId;

    /** Always "PENDING" on first response. */
    private String status;

    /** Human-readable confirmation message. */
    private String message;

    private Long planningPolicyId;
    private String planningPolicyName;
}
