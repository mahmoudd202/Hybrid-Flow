package com.example.hybridflow.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.example.hybridflow.entity.Task;

import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {

    @Query("""
        select t from Task t
        join fetch t.createdBy cb
        join fetch t.company c
        join fetch t.team tm
        where tm.manager.id = :managerId
        order by t.dueDate asc, t.id desc
    """)
    List<Task> findAllCreatedForManagedTeam(Long managerId);

    @Query("""
        select t from Task t
        join fetch t.createdBy cb
        join fetch t.company c
        join fetch t.team tm
        where t.id = :taskId
    """)
    Task findDetailedById(Long taskId);
}