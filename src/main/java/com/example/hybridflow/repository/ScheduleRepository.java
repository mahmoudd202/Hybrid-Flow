package com.example.hybridflow.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.hybridflow.entity.Schedule;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ScheduleRepository extends JpaRepository<Schedule, Long> {

  // ── Existing queries (unchanged) ─────────────────────────────────────────

  /** All published schedules for a team overlapping [rangeFrom, rangeTo]. */
  @Query("""
          select s from Schedule s
          join fetch s.team t
          join fetch s.office o
          where t.id = :teamId
            and s.published = true
            and s.startDate <= :rangeTo
            and s.endDate >= :rangeFrom
          order by s.startDate asc
      """)
  List<Schedule> findPublishedForTeamInRange(
      @Param("teamId") Long teamId,
      @Param("rangeFrom") LocalDate rangeFrom,
      @Param("rangeTo") LocalDate rangeTo);

  /**
   * All unpublished/draft schedules for a team overlapping [rangeFrom, rangeTo].
   */
  @Query("""
          select s from Schedule s
          join fetch s.team t
          join fetch s.office o
          where t.id = :teamId
            and s.published = false
            and s.startDate <= :rangeTo
            and s.endDate >= :rangeFrom
          order by s.startDate asc
      """)
  List<Schedule> findUnpublishedForTeamInRange(
      Long teamId,
      LocalDate rangeFrom,
      LocalDate rangeTo);

  /**
   * All published schedules across all teams in a company overlapping [rangeFrom,
   * rangeTo].
   */
  @Query("""
          select s from Schedule s
          join fetch s.team t
          join fetch s.office o
          where t.company.id = :companyId
            and s.published = true
            and s.startDate <= :rangeTo
            and s.endDate >= :rangeFrom
          order by t.name asc, s.startDate asc
      """)
  List<Schedule> findPublishedForCompanyInRange(
      Long companyId,
      LocalDate rangeFrom,
      LocalDate rangeTo);

  /** Fetch schedules by IDs with their full team/office/company graph. */
  @Query("""
          select distinct s
          from Schedule s
          join fetch s.team t
          join fetch s.office o
          join fetch t.company tc
          join fetch o.company oc
          where s.id in :ids
      """)
  List<Schedule> findWithTeamOfficeCompanyByIdIn(@Param("ids") List<Long> ids);

  // ── New query ─────────────────────────────────────────────────────────────

  /**
   * Returns every unpublished (draft) schedule that belongs to the given
   * company, ordered by team name then start date.
   *
   * Used by:
   * GET /api/schedules/unpublished – list + fairness review
   * DELETE /api/schedules/unpublished – bulk-clear before new generation
   */
  @Query("""
          select s from Schedule s
          join fetch s.team t
          join fetch s.office o
          join fetch t.company tc
          where tc.id = :companyId
            and s.published = false
          order by t.name asc, s.startDate asc
      """)
  List<Schedule> findAllUnpublishedByCompanyId(@Param("companyId") Long companyId);

  // ── Optimization run link ─────────────────────────────────────────────────

  /**
   * Finds all schedules produced by a given optimization run.
   * Used by ScheduleOptimizationRunService to build the scheduleIds list in
   * OptimizationRunDTO.
   */
  List<Schedule> findByOptimizationRunId(Long optimizationRunId);
}