package com.example.hybridflow.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.hybridflow.entity.PersonalAgenda;

import java.util.List;
import java.util.Optional;

public interface PersonalAgendaRepository extends JpaRepository<PersonalAgenda, Long> {

    List<PersonalAgenda> findByOwnerIdOrderByDueDateAsc(Long ownerId);

    Optional<PersonalAgenda> findByIdAndOwnerId(Long id, Long ownerId);
}