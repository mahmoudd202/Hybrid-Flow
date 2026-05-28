package com.example.hybridflow.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.hybridflow.entity.ScheduleEntry;
import com.example.hybridflow.entity.WorkMode;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ScheduleEntryRepository extends JpaRepository<ScheduleEntry, Long> {

  List<ScheduleEntry> findByUserIdAndDateBetween(Long userId, LocalDate start, LocalDate end);

  List<ScheduleEntry> findByScheduleId(Long scheduleId);

  @Modifying
  @Query("DELETE FROM ScheduleEntry se WHERE se.schedule.id = :scheduleId")
  void deleteByScheduleId(@Param("scheduleId") Long scheduleId);

  @Modifying
  @Query("DELETE FROM ScheduleEntry se WHERE se.schedule.id IN :scheduleIds")
  void deleteByScheduleIdIn(@Param("scheduleIds") List<Long> scheduleIds);

  long countBySchedule_Office_IdAndDateAndWorkMode(Long officeId, LocalDate date, WorkMode mode);

  @Query("""
          select se from ScheduleEntry se
          join fetch se.schedule s
          join fetch se.user u
          join fetch s.team t
          join fetch s.office o
          where u.id = :userId
            and s.published = true
            and se.date between :from and :to
          order by se.date asc
      """)
  List<ScheduleEntry> findPublishedEntriesForUser(Long userId, LocalDate from, LocalDate to);

  @Query("""
          select se from ScheduleEntry se
          join fetch se.schedule s
          join fetch se.user u
          join fetch s.team t
          join fetch s.office o
          where u.team.id = :teamId
            and s.published = true
            and se.date between :from and :to
          order by u.id asc, se.date asc
      """)
  List<ScheduleEntry> findPublishedEntriesForTeam(Long teamId, LocalDate from, LocalDate to);

  @Query("""
          select se from ScheduleEntry se
          join fetch se.schedule s
          join fetch se.user u
          where u.id = :userId
            and se.date = :date
            and s.published = true
      """)
  Optional<ScheduleEntry> findPublishedEntryForUserOnDate(Long userId, LocalDate date);

  @Query("""
          select se from ScheduleEntry se
          join fetch se.schedule s
          join fetch se.user u
          where u.team.id = :teamId
            and se.date = :date
            and s.published = true
      """)
  List<ScheduleEntry> findPublishedEntriesForTeamOnDate(Long teamId, LocalDate date);

  // HR view: all published entries across all teams in a company
  @Query("""
          select se from ScheduleEntry se
          join fetch se.schedule s
          join fetch se.user u
          join fetch s.team t
          join fetch s.office o
          where t.company.id = :companyId
            and s.published = true
            and se.date between :from and :to
          order by t.name asc, u.id asc, se.date asc
      """)
  List<ScheduleEntry> findPublishedEntriesForCompany(Long companyId, LocalDate from, LocalDate to);

  // HR office view: all published entries for teams assigned to a specific office
  @Query("""
          select se from ScheduleEntry se
          join fetch se.schedule s
          join fetch se.user u
          join fetch s.team t
          join fetch s.office o
          where o.id = :officeId
            and s.published = true
            and se.date between :from and :to
          order by t.name asc, u.id asc, se.date asc
      """)
  List<ScheduleEntry> findPublishedEntriesForOffice(Long officeId, LocalDate from, LocalDate to);

  @Query("""
          select e
          from ScheduleEntry e
          join fetch e.schedule s
          join fetch s.office o
          where s.id in :scheduleIds
      """)
  List<ScheduleEntry> findByScheduleIdInWithScheduleAndOffice(
      @Param("scheduleIds") List<Long> scheduleIds);

  @Query("""
          select se from ScheduleEntry se
          join fetch se.schedule s
          join fetch se.user u
          left join fetch u.profile p
          where s.id in :scheduleIds
          order by u.id asc, se.date asc
      """)
  List<ScheduleEntry> findDraftEntriesByScheduleIds(@Param("scheduleIds") List<Long> scheduleIds);
}
