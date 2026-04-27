package com.example.hybridflow.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.example.hybridflow.entity.Schedule;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ScheduleRepository extends JpaRepository<Schedule, Long> {

    // Find all published schedules for a team that overlap a date range
    @Query("""
        select s from Schedule s
        join fetch s.team t
        join fetch s.office o
        where t.id = :teamId
          and s.published = true
          and s.startDate <= :rangeTo
          and s.endDate   >= :rangeFrom
        order by s.startDate asc
    """)
    List<Schedule> findPublishedForTeamInRange(Long teamId, LocalDate rangeFrom, LocalDate rangeTo);

    // Find all published schedules across ALL teams in a company that overlap a date range (HR view)
    @Query("""
        select s from Schedule s
        join fetch s.team t
        join fetch s.office o
        where t.company.id = :companyId
          and s.published = true
          and s.startDate <= :rangeTo
          and s.endDate   >= :rangeFrom
        order by t.name asc, s.startDate asc
    """)
    List<Schedule> findPublishedForCompanyInRange(Long companyId, LocalDate rangeFrom, LocalDate rangeTo);
}
