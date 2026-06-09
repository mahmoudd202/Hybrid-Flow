package com.example.hybridflow.service;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import com.example.hybridflow.dto.BacklogTaskCreateRequestDTO;
import com.example.hybridflow.dto.TaskAssignmentResponseDTO;
import com.example.hybridflow.dto.TaskCreateRequestDTO;
import com.example.hybridflow.dto.TaskDetailsResponseDTO;
import com.example.hybridflow.dto.TaskUpdateRequestDTO;
import com.example.hybridflow.entity.*;
import com.example.hybridflow.exception.BusinessValidationException;
import com.example.hybridflow.exception.ResourceNotFoundException;
import com.example.hybridflow.mapper.TaskMapper;
import com.example.hybridflow.repository.TaskAssignmentRepository;
import com.example.hybridflow.repository.TaskRepository;
import com.example.hybridflow.repository.UserRepository;

import java.time.LocalDate;
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
            TaskMapper taskMapper) {
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

        AssignmentCreationResult result;

        if (dto.getTargetType() == TaskTargetType.INDIVIDUAL) {
            result = createIndividualAssignment(dto, manager, managedTeam, savedTask);
        } else {
            result = createTeamAssignments(manager, managedTeam, savedTask);
        }

        return taskMapper.toTaskDetailsResponse(
                savedTask,
                result.getSavedAssignments(),
                result.getExcludedAssigneeEmails());
    }

    @Transactional
    public TaskAssignmentResponseDTO createBacklogTask(BacklogTaskCreateRequestDTO dto, User creator) {
        if (creator == null) {
            throw new AccessDeniedException("Unauthenticated");
        }

        if (creator.getTeam() == null) {
            throw new AccessDeniedException("You are not attached to any team");
        }

        if (creator.getCompany() == null) {
            throw new AccessDeniedException("You are not attached to any company");
        }

        Task task = new Task();
        task.setTitle(dto.getTitle().trim());
        task.setDescription(dto.getDescription());
        task.setDueDate(dto.getDueDate());
        task.setTargetType(TaskTargetType.INDIVIDUAL);
        task.setCreatedBy(creator);
        task.setCompany(creator.getCompany());
        task.setTeam(creator.getTeam());

        Task savedTask = taskRepository.save(task);

        TaskAssignment assignment = new TaskAssignment();
        assignment.setTask(savedTask);
        assignment.setAssignee(creator);
        assignment.setStatus(TaskAssignmentStatus.BACKLOG);
        assignment.setAssignedAt(LocalDateTime.now());

        TaskAssignment savedAssignment = taskAssignmentRepository.save(assignment);

        return taskMapper.toAssignmentResponse(savedAssignment);
    }

    @Transactional
    public void deleteBacklogTask(Long assignmentId, User user) {
        if (user == null) {
            throw new AccessDeniedException("Unauthenticated");
        }

        TaskAssignment assignment = taskAssignmentRepository.findDetailedById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Backlog item not found"));

        if (assignment.getTask().getTargetType() == TaskTargetType.TEAM) {
            throw new BusinessValidationException("Team tasks cannot be deleted through this endpoint");
        }

        if (!assignment.getTask().getCreatedBy().getId().equals(user.getId())) {
            throw new AccessDeniedException("You can only delete your own backlog items");
        }

        Task task = assignment.getTask();
        taskAssignmentRepository.delete(assignment);
        taskRepository.delete(task);
    }

    @Transactional
    public void deleteTask(Long taskId, User manager) {
        validateManagerContext(manager);

        Task task = taskRepository.findWithDetailsById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));

        if (!task.getCreatedBy().getId().equals(manager.getId())) {
            throw new AccessDeniedException("You can only delete tasks you created");
        }

        List<TaskAssignment> assignments = taskAssignmentRepository.findAllByTaskId(taskId);
        taskAssignmentRepository.deleteAll(assignments);

        taskRepository.delete(task);
    }

    @Transactional
    public TaskDetailsResponseDTO updateTask(Long taskId, TaskUpdateRequestDTO dto, User user) {
        if (user == null) {
            throw new AccessDeniedException("Unauthenticated");
        }

        Task task = taskRepository.findWithDetailsById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));

        if (!task.getCreatedBy().getId().equals(user.getId())) {
            throw new AccessDeniedException("You can only edit tasks you created");
        }

        if (user.getRole() == Role.MANAGER) {
            validateManagerContext(user);
        }

        List<String> excludedAssignees = List.of();

        if (!dto.getDueDate().equals(task.getDueDate())) {
            List<TaskAssignment> currentAssignments = taskAssignmentRepository.findAllByTaskId(taskId);
            List<User> assignees = currentAssignments.stream()
                    .map(TaskAssignment::getAssignee)
                    .toList();

            LocalDate newDueDate = dto.getDueDate().toLocalDate();

            if (task.getTargetType() == TaskTargetType.INDIVIDUAL) {
                if (!assignees.isEmpty()) {
                    List<String> unavailable = scheduleAvailabilityService.findUnavailableUserEmailsOnDate(assignees,
                            newDueDate);

                    if (!unavailable.isEmpty()) {
                        throw new BusinessValidationException(
                                "Cannot update task due date. The assigned employee is unavailable: "
                                        + String.join("; ", unavailable));
                    }
                }
            } else {
                List<String> unavailable = scheduleAvailabilityService.findUnavailableUserEmailsOnDate(assignees,
                        newDueDate);

                excludedAssignees = unavailable;

                if (!unavailable.isEmpty()) {
                    removeUnavailableAssignments(currentAssignments, unavailable);

                    List<TaskAssignment> remainingAssignments = taskAssignmentRepository.findAllByTaskId(taskId);

                    if (remainingAssignments.isEmpty()) {
                        throw new BusinessValidationException(
                                "Cannot update task due date. All assigned employees are unavailable on " + newDueDate);
                    }
                }
            }
        }

        task.setTitle(dto.getTitle().trim());
        task.setDescription(dto.getDescription());
        task.setDueDate(dto.getDueDate());

        Task saved = taskRepository.save(task);
        List<TaskAssignment> assignments = taskAssignmentRepository.findAllByTaskId(taskId);

        return taskMapper.toTaskDetailsResponse(saved, assignments, excludedAssignees);
    }

    @Transactional(readOnly = true)
    public List<Task> getManagerCreatedTasks(User manager) {
        validateManagerContext(manager);
        return taskRepository.findAllCreatedForManagedTeam(manager.getId());
    }

    @Transactional(readOnly = true)
    public List<TaskAssignment> getMyAssignments(User user) {
        if (user == null) {
            throw new AccessDeniedException("Unauthenticated");
        }

        return taskAssignmentRepository.findAllForAssignee(user.getId());
    }

    @Transactional(readOnly = true)
    public List<TaskAssignment> getTeamBacklog(User user) {
        if (user == null) {
            throw new AccessDeniedException("Unauthenticated");
        }

        Team team = user.getTeam();
        if (team == null) {
            throw new AccessDeniedException("You are not attached to any team");
        }

        return taskAssignmentRepository.findBacklogByTeamId(team.getId());
    }

    @Transactional(readOnly = true)
    public List<TaskAssignment> getTeamDashboard(User user) {
        if (user == null) {
            throw new AccessDeniedException("Unauthenticated");
        }

        Team team = user.getTeam();
        if (team == null) {
            throw new AccessDeniedException("You are not attached to any team");
        }

        return taskAssignmentRepository.findAllByTeamId(team.getId());
    }

    @Transactional(readOnly = true)
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
    public void handlePtoRequest(User requester, LocalDate startDate, LocalDate endDate) {
        List<TaskAssignment> conflictingAssignments = taskAssignmentRepository.findActiveAssignmentsForUserInDateRange(
                requester.getId(),
                startDate.atStartOfDay(),
                endDate.plusDays(1).atStartOfDay());

        for (TaskAssignment assignment : conflictingAssignments) {
            assignment.setStatus(TaskAssignmentStatus.PTO_UNASSIGNED);
            taskAssignmentRepository.save(assignment);
        }
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
        if (dto.getTargetType() == null) {
            throw new BusinessValidationException("targetType is required");
        }

        if (dto.getDueDate() == null) {
            throw new BusinessValidationException("dueDate is required");
        }

        if (dto.getTitle() == null || dto.getTitle().trim().isEmpty()) {
            throw new BusinessValidationException("Title is required");
        }

        if (dto.getTargetType() == TaskTargetType.INDIVIDUAL && dto.getAssigneeId() == null) {
            throw new BusinessValidationException("INDIVIDUAL task requires assigneeId");
        }

        if (dto.getTargetType() == TaskTargetType.TEAM && dto.getAssigneeId() != null) {
            throw new BusinessValidationException("TEAM task must not include assigneeId");
        }
    }

    private AssignmentCreationResult createIndividualAssignment(
            TaskCreateRequestDTO dto,
            User manager,
            Team managedTeam,
            Task task) {
        User assignee = userRepository.findById(dto.getAssigneeId())
                .orElseThrow(() -> new ResourceNotFoundException("Assignee not found"));

        validateAssigneeBelongsToManagedTeam(manager, managedTeam, assignee);

        List<String> unavailable = scheduleAvailabilityService.findUnavailableUserEmailsOnDate(
                List.of(assignee),
                dto.getDueDate().toLocalDate());

        if (!unavailable.isEmpty()) {
            throw new BusinessValidationException(
                    "Cannot assign task. The selected employee is unavailable: "
                            + String.join("; ", unavailable));
        }

        TaskAssignment assignment = new TaskAssignment();
        assignment.setTask(task);
        assignment.setAssignee(assignee);
        assignment.setStatus(TaskAssignmentStatus.TODO);
        assignment.setAssignedAt(LocalDateTime.now());

        TaskAssignment saved = taskAssignmentRepository.save(assignment);

        return new AssignmentCreationResult(List.of(saved), List.of());
    }

    private AssignmentCreationResult createTeamAssignments(User manager, Team managedTeam, Task task) {
        List<User> teamMembers = userRepository.findAllByTeamId(managedTeam.getId());

        if (teamMembers.isEmpty()) {
            throw new BusinessValidationException("No team members found for this team");
        }

        for (User member : teamMembers) {
            if (member.getCompany() == null ||
                    !member.getCompany().getId().equals(manager.getCompany().getId())) {
                throw new AccessDeniedException("Invalid cross-company team member detected");
            }

            if (member.getTeam() == null ||
                    !member.getTeam().getId().equals(managedTeam.getId())) {
                throw new AccessDeniedException("Invalid cross-team member detected");
            }
        }

        LocalDate dueDate = task.getDueDate().toLocalDate();

        List<String> unavailableUsers = scheduleAvailabilityService.findUnavailableUserEmailsOnDate(teamMembers,
                dueDate);

        List<TaskAssignment> assignments = new ArrayList<>();

        for (User member : teamMembers) {
            if (isUserExcluded(member, unavailableUsers)) {
                continue;
            }

            TaskAssignment assignment = new TaskAssignment();
            assignment.setTask(task);
            assignment.setAssignee(member);
            assignment.setStatus(TaskAssignmentStatus.TODO);
            assignment.setAssignedAt(LocalDateTime.now());

            assignments.add(assignment);
        }

        if (assignments.isEmpty()) {
            throw new BusinessValidationException(
                    "Cannot create team task. All team members are unavailable on " + dueDate);
        }

        List<TaskAssignment> savedAssignments = taskAssignmentRepository.saveAll(assignments);

        return new AssignmentCreationResult(savedAssignments, unavailableUsers);
    }

    private boolean isUserExcluded(User user, List<String> excludedMessages) {
        if (user == null || user.getEmail() == null) {
            return false;
        }

        for (String message : excludedMessages) {
            if (message.startsWith(user.getEmail() + " ")) {
                return true;
            }
        }

        return false;
    }

    private void removeUnavailableAssignments(
            List<TaskAssignment> currentAssignments,
            List<String> unavailableMessages) {
        List<TaskAssignment> toDelete = new ArrayList<>();

        for (TaskAssignment assignment : currentAssignments) {
            User assignee = assignment.getAssignee();

            if (isUserExcluded(assignee, unavailableMessages)) {
                toDelete.add(assignment);
            }
        }

        if (!toDelete.isEmpty()) {
            taskAssignmentRepository.deleteAll(toDelete);
        }
    }

    private void validateManagerContext(User manager) {
        if (manager == null) {
            throw new AccessDeniedException("Unauthenticated");
        }

        if (manager.getRole() != Role.MANAGER) {
            throw new AccessDeniedException("You do not have the MANAGER role");
        }

        if (manager.getCompany() == null) {
            throw new AccessDeniedException("Manager is not attached to a company");
        }

        if (manager.getTeam() == null) {
            throw new AccessDeniedException("Manager is not attached to a team");
        }

        if (manager.getTeam().getManager() == null ||
                !manager.getTeam().getManager().getId().equals(manager.getId())) {
            throw new AccessDeniedException("You are not the designated manager of this team");
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

    private static class AssignmentCreationResult {
        private final List<TaskAssignment> savedAssignments;
        private final List<String> excludedAssigneeEmails;

        private AssignmentCreationResult(
                List<TaskAssignment> savedAssignments,
                List<String> excludedAssigneeEmails) {
            this.savedAssignments = savedAssignments;
            this.excludedAssigneeEmails = excludedAssigneeEmails;
        }

        public List<TaskAssignment> getSavedAssignments() {
            return savedAssignments;
        }

        public List<String> getExcludedAssigneeEmails() {
            return excludedAssigneeEmails;
        }
    }
}