package com.example.hybridflow.service;

import jakarta.transaction.Transactional;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import com.example.hybridflow.dto.PersonalTaskCreateRequestDTO;
import com.example.hybridflow.dto.PersonalTaskResponseDTO;
import com.example.hybridflow.entity.PersonalTask;
import com.example.hybridflow.entity.PersonalTaskStatus;
import com.example.hybridflow.entity.User;
import com.example.hybridflow.exception.ResourceNotFoundException;
import com.example.hybridflow.repository.PersonalTaskRepository;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class PersonalTaskService {

    private final PersonalTaskRepository personalTaskRepository;

    public PersonalTaskService(PersonalTaskRepository personalTaskRepository) {
        this.personalTaskRepository = personalTaskRepository;
    }

    @Transactional
    public PersonalTaskResponseDTO createPersonalTask(PersonalTaskCreateRequestDTO dto, User currentUser) {
        validateAuthenticatedUser(currentUser);

        PersonalTask task = new PersonalTask();
        task.setTitle(dto.getTitle().trim());
        task.setDescription(dto.getDescription());
        task.setDueDate(dto.getDueDate());
        task.setStatus(PersonalTaskStatus.TODO);
        task.setCreatedAt(LocalDateTime.now());
        task.setOwner(currentUser);

        PersonalTask savedTask = personalTaskRepository.save(task);
        return toResponse(savedTask);
    }

    public List<PersonalTaskResponseDTO> getMyPersonalTasks(User currentUser) {
        validateAuthenticatedUser(currentUser);

        return personalTaskRepository.findByOwnerIdOrderByDueDateAsc(currentUser.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public PersonalTaskResponseDTO updateMyPersonalTaskStatus(Long taskId, PersonalTaskStatus newStatus, User currentUser) {
        validateAuthenticatedUser(currentUser);

        if (newStatus == null) {
            throw new IllegalArgumentException("status is required");
        }

        PersonalTask task = personalTaskRepository.findByIdAndOwnerId(taskId, currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Personal task not found"));

        task.setStatus(newStatus);

        PersonalTask updatedTask = personalTaskRepository.save(task);
        return toResponse(updatedTask);
    }

    @Transactional
    public void deleteMyPersonalTask(Long taskId, User currentUser) {
        validateAuthenticatedUser(currentUser);

        PersonalTask task = personalTaskRepository.findByIdAndOwnerId(taskId, currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Personal task not found"));

        personalTaskRepository.delete(task);
    }

    private void validateAuthenticatedUser(User currentUser) {
        if (currentUser == null) {
            throw new AccessDeniedException("Unauthenticated");
        }
        if (currentUser.getId() == null) {
            throw new AccessDeniedException("Invalid authenticated user");
        }
    }

    private PersonalTaskResponseDTO toResponse(PersonalTask task) {
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