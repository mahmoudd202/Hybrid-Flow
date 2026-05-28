package com.example.hybridflow.repository;

import com.example.hybridflow.entity.OptimizationJobStatus;
import com.example.hybridflow.entity.ScheduleOptimizationRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ScheduleOptimizationRunRepository
        extends JpaRepository<ScheduleOptimizationRun, Long> {

    /**
     * Returns all runs for a company with the given status, newest first.
     * Used by GET /optimization-runs to return only COMPLETED history runs.
     */
    List<ScheduleOptimizationRun> findByCompanyIdAndJobStatusOrderByCreatedAtDesc(
            Long companyId, OptimizationJobStatus status);

    /**
     * Returns all runs for a company regardless of status, newest first.
     * Used internally if we need a full history view.
     */
    List<ScheduleOptimizationRun> findByCompanyIdOrderByCreatedAtDesc(Long companyId);

    /**
     * Finds the optimization run linked to any of the given schedule IDs.
     * Used by getUnpublishedSchedules to attach stored stats to draft schedules.
     */
    @Query("""
            SELECT DISTINCT s.optimizationRun
            FROM Schedule s
            WHERE s.id IN :scheduleIds
              AND s.optimizationRun IS NOT NULL
            """)
    List<ScheduleOptimizationRun> findByLinkedScheduleIds(
            @Param("scheduleIds") List<Long> scheduleIds);
}
