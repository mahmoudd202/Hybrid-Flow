package com.example.hybridflow.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "teams")
@Data
public class Team {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    @lombok.EqualsAndHashCode.Exclude
    @lombok.ToString.Exclude
    private Company company;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "office_id")
    @lombok.EqualsAndHashCode.Exclude
    @lombok.ToString.Exclude
    private Office office;
    @Column(nullable = false, length = 150)
    private String name;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_id")
    @lombok.EqualsAndHashCode.Exclude
    @lombok.ToString.Exclude
    private User manager; // Team table owns the manager FK
}
