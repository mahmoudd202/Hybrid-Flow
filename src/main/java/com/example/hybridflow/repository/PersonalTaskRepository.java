package com.example.hybridflow.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.hybridflow.entity.PersonalTask;

import java.util.List;
import java.util.Optional;

public interface PersonalTaskRepository extends JpaRepository<PersonalTask, Long> {

    List<PersonalTask> findByOwnerIdOrderByDueDateAsc(Long ownerId);

    Optional<PersonalTask> findByIdAndOwnerId(Long id, Long ownerId);
}