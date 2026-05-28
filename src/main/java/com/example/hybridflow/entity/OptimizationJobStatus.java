package com.example.hybridflow.entity;

/**
 * Lifecycle states of a single Gurobi schedule-generation job.
 *
 * PENDING  – job accepted, not yet picked up by the async thread
 * RUNNING  – Gurobi model is being built / solved right now
 * COMPLETED – optimal solution found, schedules saved, stats persisted
 * FAILED   – validation failure, INFEASIBLE model, or unexpected exception
 */
public enum OptimizationJobStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED
}
