package com.example.hybridflow.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.hybridflow.entity.Meeting;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import com.example.hybridflow.entity.MeetingType;

@Repository
public interface MeetingRepository extends JpaRepository<Meeting, Long> {

  // Find all meetings a specific team is participating in
  List<Meeting> findByParticipatingTeams_Id(Long teamId);

  // Find meetings hosted by a user
  List<Meeting> findByHostId(Long userId);

  // Find all meetings happening in an office on a specific day
  // I think this is a shitty method but again,, AI suggested and I don't think it
  // is useful
  // @Query("SELECT m FROM Meeting m WHERE m.office.id = :officeId AND m.startTime
  // >= :start AND m.endTime <= :end")
  // List<Meeting> findMeetingsInOffice(Long officeId, LocalDateTime start,
  // LocalDateTime end);

  // This one query handles both the "Team View" and the "Individual View"
  // while preventing N+1 and LazyInitializationExceptions.
  @Query("""
          select distinct m from Meeting m
          join fetch m.office o
          join fetch m.host h
          join fetch m.participatingTeams t
          where t.id = :teamId
          order by m.startTime asc
      """)
  List<Meeting> findByTeamWithDetails(@Param("teamId") Long teamId);

  @Query("""
          select distinct m from Meeting m
          join fetch m.office o
          join fetch m.host h
          join fetch m.participatingTeams pt
          where pt.id = :teamId
            and m.startTime < :rangeEnd
            and m.endTime > :rangeStart
          order by m.startTime asc
      """)
  List<Meeting> findTeamMeetingsInRange(Long teamId, LocalDateTime rangeStart, LocalDateTime rangeEnd);

  // HR view: all meetings across all teams in a company
  @Query("""
          select distinct m from Meeting m
          join fetch m.office o
          join fetch m.host h
          join fetch m.participatingTeams pt
          where pt.company.id = :companyId
            and m.startTime < :rangeEnd
            and m.endTime > :rangeStart
          order by m.startTime asc
      """)
  List<Meeting> findCompanyMeetingsInRange(Long companyId, LocalDateTime rangeStart, LocalDateTime rangeEnd);

  // HR office view: meetings happening at a specific office
  @Query("""
          select distinct m from Meeting m
          join fetch m.office o
          join fetch m.host h
          join fetch m.participatingTeams pt
          where o.id = :officeId
            and m.startTime < :rangeEnd
            and m.endTime > :rangeStart
          order by m.startTime asc
      """)
  List<Meeting> findOfficeMeetingsInRange(Long officeId, LocalDateTime rangeStart, LocalDateTime rangeEnd);

  @Query("""
          select distinct m from Meeting m
          join fetch m.office o
          join fetch m.host h
          join fetch m.participatingTeams pt
          where pt.id = :teamId
            and m.id != :excludeId
            and m.startTime < :rangeEnd
            and m.endTime > :rangeStart
          order by m.startTime asc
      """)
  List<Meeting> findTeamMeetingsInRangeExcluding(
      Long teamId, LocalDateTime rangeStart, LocalDateTime rangeEnd, Long excludeId);

  @Query("""
          select distinct m from Meeting m
          join fetch m.office o
          join fetch m.host h
          join fetch m.participatingTeams pt
          join User u on u.team = pt
          where u.id = :userId
            and m.startTime < :rangeEnd
            and m.endTime > :rangeStart
          order by m.startTime asc
      """)
  List<Meeting> findUserMeetingsInRange(
      Long userId,
      LocalDateTime rangeStart,
      LocalDateTime rangeEnd);

  @Query("""
          select distinct m from Meeting m
          join fetch m.office o
          join fetch m.host h
          join fetch m.participatingTeams pt
          join User u on u.team = pt
          where u.id = :userId
            and m.type = com.example.hybridflow.entity.MeetingType.OFFICE
            and m.startTime < :rangeEnd
            and m.endTime > :rangeStart
          order by m.startTime asc
      """)
  List<Meeting> findUserOfficeMeetingsInRange(
      Long userId,
      LocalDateTime rangeStart,
      LocalDateTime rangeEnd);
}
