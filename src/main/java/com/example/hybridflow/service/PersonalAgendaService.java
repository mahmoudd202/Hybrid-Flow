package com.example.hybridflow.service;

import jakarta.transaction.Transactional;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import com.example.hybridflow.dto.PersonalTaskCreateRequestDTO;
import com.example.hybridflow.dto.PersonalTaskResponseDTO;
import com.example.hybridflow.dto.PersonalTaskUpdateRequestDTO;
import com.example.hybridflow.entity.PersonalAgenda;
import com.example.hybridflow.entity.PersonalAgendaStatus;
import com.example.hybridflow.entity.User;
import com.example.hybridflow.exception.ResourceNotFoundException;
import com.example.hybridflow.repository.PersonalAgendaRepository;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class PersonalAgendaService {

    private final PersonalAgendaRepository personalAgendaRepository;

    public PersonalAgendaService(PersonalAgendaRepository personalAgendaRepository) {
        this.personalAgendaRepository = personalAgendaRepository;
    }

    @Transactional
    public PersonalTaskResponseDTO createPersonalTask(PersonalTaskCreateRequestDTO dto, User currentUser) {
        validateAuthenticatedUser(currentUser);

        PersonalAgenda task = new PersonalAgenda();
        task.setTitle(dto.getTitle().trim());
        task.setDescription(dto.getDescription());
        task.setDueDate(dto.getDueDate());
        task.setStatus(PersonalAgendaStatus.NOT_STARTED);
        task.setCreatedAt(LocalDateTime.now());
        task.setOwner(currentUser);

        PersonalAgenda savedTask = personalAgendaRepository.save(task);
        return toResponse(savedTask);
    }

    public List<PersonalTaskResponseDTO> getMyPersonalTasks(User currentUser) {
        validateAuthenticatedUser(currentUser);

        return personalAgendaRepository.findByOwnerIdOrderByDueDateAsc(currentUser.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public PersonalTaskResponseDTO updateMyPersonalTaskStatus(Long taskId, PersonalAgendaStatus newStatus,
            User currentUser) {
        validateAuthenticatedUser(currentUser);

        if (newStatus == null) {
            throw new IllegalArgumentException("status is required");
        }

        PersonalAgenda task = personalAgendaRepository.findByIdAndOwnerId(taskId, currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Personal task not found"));

        task.setStatus(newStatus);

        PersonalAgenda updatedTask = personalAgendaRepository.save(task);
        return toResponse(updatedTask);
    }

    @Transactional
    public void deleteMyPersonalTask(Long taskId, User currentUser) {
        validateAuthenticatedUser(currentUser);

        PersonalAgenda task = personalAgendaRepository.findByIdAndOwnerId(taskId, currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Personal task not found"));

        personalAgendaRepository.delete(task);
    }

    @Transactional
    public PersonalTaskResponseDTO updateMyPersonalTask(Long taskId, PersonalTaskUpdateRequestDTO dto,
            User currentUser) {
        validateAuthenticatedUser(currentUser);

        PersonalAgenda task = personalAgendaRepository.findByIdAndOwnerId(taskId, currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Personal task not found"));

        task.setTitle(dto.getTitle().trim());
        task.setDescription(dto.getDescription());
        task.setDueDate(dto.getDueDate());

        PersonalAgenda updatedTask = personalAgendaRepository.save(task);
        return toResponse(updatedTask);
    }

    private void validateAuthenticatedUser(User currentUser) {
        if (currentUser == null) {
            throw new AccessDeniedException("Unauthenticated");
        }
        if (currentUser.getId() == null) {
            throw new AccessDeniedException("Invalid authenticated user");
        }
    }

    private PersonalTaskResponseDTO toResponse(PersonalAgenda task) {
        return PersonalTaskResponseDTO.builder()
                .id(task.getId())
                .title(task.getTitle())
                .description(task.getDescription())
                .dueDate(task.getDueDate())
                .status(task.getStatus())
                .createdAt(task.getCreatedAt())
                .ownerId(task.getOwner().getId())
                .ownerEmail(task.getOwner().getEmail())
                .build();
    }
}