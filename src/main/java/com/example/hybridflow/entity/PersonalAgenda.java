package com.example.hybridflow.entity;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
@Entity
@Table(name = "personal_agendas")
@Data
public class PersonalAgenda {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 200)
    private String title;
    @Column(length = 2000)
    private String description;
    @Column(nullable = false)
    private LocalDateTime dueDate;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PersonalAgendaStatus status;
    @Column(nullable = false)
    private LocalDateTime createdAt;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
@lombok.EqualsAndHashCode.Exclude @lombok.ToString.Exclude
    private User owner;
}
