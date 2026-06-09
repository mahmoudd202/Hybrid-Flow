package com.example.hybridflow.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.hybridflow.entity.Meeting;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MeetingRepository extends JpaRepository<Meeting, Long> {

  List<Meeting> findByParticipatingTeams_Id(Long teamId);

  List<Meeting> findByHostId(Long userId);

  @Query("""
          select distinct m from Meeting m
          join fetch m.office o
          join fetch m.host h
          join fetch m.participatingTeams pt
          join m.participatingTeams t
          where t.id = :teamId
          order by m.startTime asc
      """)
  List<Meeting> findByTeamWithDetails(@Param("teamId") Long teamId);

  @Query("""
          select distinct m from Meeting m
          join fetch m.office o
          join fetch m.host h
          join fetch m.participatingTeams pt
          join m.participatingTeams t
          where t.id = :teamId
            and m.startTime < :rangeEnd
            and m.endTime > :rangeStart
          order by m.startTime asc
      """)
  List<Meeting> findTeamMeetingsInRange(Long teamId, LocalDateTime rangeStart, LocalDateTime rangeEnd);

  @Query("""
          select distinct m from Meeting m
          join fetch m.office o
          join fetch m.host h
          join fetch m.participatingTeams pt
          join m.participatingTeams t
          where t.company.id = :companyId
            and m.startTime < :rangeEnd
            and m.endTime > :rangeStart
          order by m.startTime asc
      """)
  List<Meeting> findCompanyMeetingsInRange(Long companyId, LocalDateTime rangeStart, LocalDateTime rangeEnd);

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
          join m.participatingTeams t
          where t.id = :teamId
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
          join m.participatingTeams t
          join User u on u.team = t
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
          join m.participatingTeams t
          join User u on u.team = t
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
