package com.example.hybridflow.service;

import jakarta.transaction.Transactional;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import com.example.hybridflow.dto.TaskAssignmentStatusUpdateDTO;
import com.example.hybridflow.dto.TaskCreateRequestDTO;
import com.example.hybridflow.dto.TaskDetailsResponseDTO;
import com.example.hybridflow.entity.*;
import com.example.hybridflow.exception.BusinessValidationException;
import com.example.hybridflow.exception.ResourceNotFoundException;
import com.example.hybridflow.mapper.TaskMapper;
import com.example.hybridflow.repository.TaskAssignmentRepository;
import com.example.hybridflow.repository.TaskRepository;
import com.example.hybridflow.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class TaskService {

    private final TaskRepository taskRepository;
    private final TaskAssignmentRepository taskAssignmentRepository;
    private final UserRepository userRepository;
    private final ScheduleAvailabilityService scheduleAvailabilityService;
    private final TaskMapper taskMapper;

    public TaskService(
            TaskRepository taskRepository,
            TaskAssignmentRepository taskAssignmentRepository,
            UserRepository userRepository,
            ScheduleAvailabilityService scheduleAvailabilityService,
            TaskMapper taskMapper
    ) {
        this.taskRepository = taskRepository;
        this.taskAssignmentRepository = taskAssignmentRepository;
        this.userRepository = userRepository;
        this.scheduleAvailabilityService = scheduleAvailabilityService;
        this.taskMapper = taskMapper;
    }

    @Transactional
    public TaskDetailsResponseDTO createTask(TaskCreateRequestDTO dto, User manager) {
        validateManagerContext(manager);
        validateCreateRequest(dto);

        Team managedTeam = manager.getTeam();

        Task task = new Task();
        task.setTitle(dto.getTitle().trim());
        task.setDescription(dto.getDescription());
        task.setDueDate(dto.getDueDate());
        task.setTargetType(dto.getTargetType());
        task.setCreatedBy(manager);
        task.setCompany(manager.getCompany());
        task.setTeam(managedTeam);

        Task savedTask = taskRepository.save(task);

        List<TaskAssignment> savedAssignments;
        if (dto.getTargetType() == TaskTargetType.INDIVIDUAL) {
            savedAssignments = createIndividualAssignment(dto, manager, managedTeam, savedTask);
        } else {
            savedAssignments = createTeamAssignments(manager, managedTeam, savedTask);
        }

        return taskMapper.toTaskDetailsResponse(savedTask, savedAssignments);
    }

    public List<Task> getManagerCreatedTasks(User manager) {
        validateManagerContext(manager);
        return taskRepository.findAllCreatedForManagedTeam(manager.getId());
    }

    public List<TaskAssignment> getMyAssignments(User user) {
        if (user == null) {
            throw new AccessDeniedException("Unauthenticated");
        }
        return taskAssignmentRepository.findAllForAssignee(user.getId());
    }

    public List<TaskAssignment> getAssignmentsForTask(Long taskId, User manager) {
        validateManagerContext(manager);

        Task task = taskRepository.findDetailedById(taskId);
        if (task == null) {
            throw new ResourceNotFoundException("Task not found");
        }

        if (!task.getTeam().getId().equals(manager.getTeam().getId())) {
            throw new AccessDeniedException("You cannot access tasks outside your managed team");
        }

        return taskAssignmentRepository.findAllByTaskId(taskId);
    }

    @Transactional
    public TaskAssignment updateMyAssignmentStatus(Long assignmentId, TaskAssignmentStatus newStatus, User user) {
        if (user == null) {
            throw new AccessDeniedException("Unauthenticated");
        }

        if (newStatus == null) {
            throw new BusinessValidationException("status is required");
        }

        TaskAssignment assignment = taskAssignmentRepository.findDetailedById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment not found"));

        if (!assignment.getAssignee().getId().equals(user.getId())) {
            throw new AccessDeniedException("You can update only your own assignment");
        }

        assignment.setStatus(newStatus);

        if (newStatus == TaskAssignmentStatus.DONE) {
            assignment.setCompletedAt(LocalDateTime.now());
        } else {
            assignment.setCompletedAt(null);
        }

        return taskAssignmentRepository.save(assignment);
    }

    private void validateCreateRequest(TaskCreateRequestDTO dto) {
        if (dto.getTargetType() == TaskTargetType.INDIVIDUAL && dto.getAssigneeId() == null) {
            throw new BusinessValidationException("INDIVIDUAL task requires assigneeId");
        }

        if (dto.getTargetType() == TaskTargetType.TEAM && dto.getAssigneeId() != null) {
            throw new BusinessValidationException("TEAM task must not include assigneeId");
        }
    }

    private List<TaskAssignment> createIndividualAssignment(TaskCreateRequestDTO dto, User manager, Team managedTeam, Task task) {
        User assignee = userRepository.findById(dto.getAssigneeId())
                .orElseThrow(() -> new ResourceNotFoundException("Assignee not found"));

        validateAssigneeBelongsToManagedTeam(manager, managedTeam, assignee);

        scheduleAvailabilityService.validateUserIsSchedulableOnDate(
                assignee,
                dto.getDueDate().toLocalDate()
        );

        TaskAssignment assignment = new TaskAssignment();
        assignment.setTask(task);
        assignment.setAssignee(assignee);
        assignment.setStatus(TaskAssignmentStatus.TODO);
        assignment.setAssignedAt(LocalDateTime.now());

        TaskAssignment saved = taskAssignmentRepository.save(assignment);
        return List.of(saved);
    }

    private List<TaskAssignment> createTeamAssignments(User manager, Team managedTeam, Task task) {
        List<User> teamMembers = userRepository.findAllByTeamId(managedTeam.getId());

        if (teamMembers.isEmpty()) {
            throw new BusinessValidationException("No team members found for this team");
        }

        for (User member : teamMembers) {
            // Optional rule:
            // if (member.getId().equals(manager.getId())) continue;

            if (member.getCompany() == null || !member.getCompany().getId().equals(manager.getCompany().getId())) {
                throw new AccessDeniedException("Invalid cross-company team member detected");
            }

            if (member.getTeam() == null || !member.getTeam().getId().equals(managedTeam.getId())) {
                throw new AccessDeniedException("Invalid cross-team member detected");
            }
        }

        scheduleAvailabilityService.validateUsersAreSchedulableOnDate(
                teamMembers,
                task.getDueDate().toLocalDate()
        );

        List<TaskAssignment> assignments = new ArrayList<>();

        for (User member : teamMembers) {
            // Optional rule:
            // if (member.getId().equals(manager.getId())) continue;

            TaskAssignment assignment = new TaskAssignment();
            assignment.setTask(task);
            assignment.setAssignee(member);
            assignment.setStatus(TaskAssignmentStatus.TODO);
            assignment.setAssignedAt(LocalDateTime.now());

            assignments.add(assignment);
        }

        return taskAssignmentRepository.saveAll(assignments);
    }

    private void validateManagerContext(User manager) {
        if (manager == null) {
            throw new AccessDeniedException("Unauthenticated");
        }

        if (manager.getCompany() == null) {
            throw new AccessDeniedException("Manager is not attached to a company");
        }

        if (manager.getTeam() == null) {
            throw new AccessDeniedException("Manager is not attached to a team");
        }

        if (manager.getTeam().getManager() == null ||
                !manager.getTeam().getManager().getId().equals(manager.getId())) {
            throw new AccessDeniedException("You are not the actual manager of this team");
        }
    }

    private void validateAssigneeBelongsToManagedTeam(User manager, Team managedTeam, User assignee) {
        if (assignee.getCompany() == null ||
                !assignee.getCompany().getId().equals(manager.getCompany().getId())) {
            throw new AccessDeniedException("Cannot assign a task outside your company");
        }

        if (assignee.getTeam() == null ||
                !assignee.getTeam().getId().equals(managedTeam.getId())) {
            throw new AccessDeniedException("Cannot assign a task outside your team");
        }
    }
}