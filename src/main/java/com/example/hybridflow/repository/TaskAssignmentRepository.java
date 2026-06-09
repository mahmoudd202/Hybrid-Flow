package com.example.hybridflow.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.example.hybridflow.entity.TaskAssignment;
import com.example.hybridflow.entity.TaskAssignmentStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TaskAssignmentRepository extends JpaRepository<TaskAssignment, Long> {

  @Query("""
          select ta from TaskAssignment ta
          join fetch ta.task t
          join fetch ta.assignee a
          join fetch t.createdBy cb
          join fetch t.company c
          join fetch t.team tm
          where a.id = :userId
          order by t.dueDate asc, ta.id desc
      """)
  List<TaskAssignment> findAllForAssignee(Long userId);

  @Query("""
          select ta from TaskAssignment ta
          join fetch ta.task t
          join fetch ta.assignee a
          join fetch t.createdBy cb
          join fetch t.company c
          join fetch t.team tm
          where t.id = :taskId
          order by a.id asc
      """)
  List<TaskAssignment> findAllByTaskId(Long taskId);

  @Query("""
          select ta from TaskAssignment ta
          join fetch ta.task t
          join fetch ta.assignee a
          join fetch t.createdBy cb
          join fetch t.company c
          join fetch t.team tm
          where ta.id = :assignmentId
      """)
  Optional<TaskAssignment> findDetailedById(Long assignmentId);

  @Query("""
          select ta from TaskAssignment ta
          join fetch ta.task t
          join fetch ta.assignee a
          where a.id = :userId
            and ta.status in :statuses
          order by t.dueDate asc, ta.id asc
      """)
  List<TaskAssignment> findPendingAssignmentsForUser(Long userId, List<TaskAssignmentStatus> statuses);

  @Query("""
          select ta from TaskAssignment ta
          join fetch ta.task t
          join fetch ta.assignee a
          join fetch t.createdBy cb
          where a.id = :userId
            and t.dueDate between :startDate and :endDate
            and ta.status not in (
              com.example.hybridflow.entity.TaskAssignmentStatus.DONE,
              com.example.hybridflow.entity.TaskAssignmentStatus.CANCELLED,
              com.example.hybridflow.entity.TaskAssignmentStatus.PTO_UNASSIGNED
            )
          order by t.dueDate asc
      """)
  List<TaskAssignment> findActiveAssignmentsForUserInDateRange(Long userId, LocalDateTime startDate,
      LocalDateTime endDate);

  @Query("""
          select ta from TaskAssignment ta
          join fetch ta.task t
          join fetch ta.assignee a
          join fetch t.createdBy cb
          join fetch t.company c
          join fetch t.team tm
          where tm.id = :teamId
            and ta.status = com.example.hybridflow.entity.TaskAssignmentStatus.BACKLOG
          order by t.dueDate asc, ta.id asc
      """)
  List<TaskAssignment> findBacklogByTeamId(Long teamId);

  @Query("""
          select ta from TaskAssignment ta
          join fetch ta.task t
          join fetch ta.assignee a
          join fetch t.createdBy cb
          join fetch t.company c
          join fetch t.team tm
          where tm.id = :teamId
          order by ta.status asc, t.dueDate asc, ta.id asc
      """)
  List<TaskAssignment> findAllByTeamId(Long teamId);
}
