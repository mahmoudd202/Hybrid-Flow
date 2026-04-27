package com.example.hybridflow.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.example.hybridflow.entity.TaskAssignment;
import com.example.hybridflow.entity.TaskAssignmentStatus;

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

}