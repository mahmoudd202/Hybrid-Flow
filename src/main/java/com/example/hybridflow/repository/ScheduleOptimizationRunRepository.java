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

        List<ScheduleOptimizationRun> findByCompanyIdAndJobStatusOrderByCreatedAtDesc(
                        Long companyId, OptimizationJobStatus status);

        List<ScheduleOptimizationRun> findByCompanyIdOrderByCreatedAtDesc(Long companyId);

        @Query("""
                        SELECT DISTINCT s.optimizationRun
                        FROM Schedule s
                        WHERE s.id IN :scheduleIds
                          AND s.optimizationRun IS NOT NULL
                        """)
        List<ScheduleOptimizationRun> findByLinkedScheduleIds(
                        @Param("scheduleIds") List<Long> scheduleIds);
}
