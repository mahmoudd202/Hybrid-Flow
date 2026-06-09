package com.example.hybridflow.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "task_assignments", uniqueConstraints = {
        @UniqueConstraint(name = "uk_task_assignee", columnNames = { "task_id", "assignee_id" })
})
@Data
public class TaskAssignment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false)
    @lombok.EqualsAndHashCode.Exclude
    @lombok.ToString.Exclude
    private Task task;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignee_id", nullable = false)
    @lombok.EqualsAndHashCode.Exclude
    @lombok.ToString.Exclude
    private User assignee;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskAssignmentStatus status = TaskAssignmentStatus.TODO;
    @Column(nullable = false)
    private LocalDateTime assignedAt;
    private LocalDateTime completedAt;
}
