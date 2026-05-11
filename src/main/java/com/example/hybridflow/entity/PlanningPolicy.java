package com.example.hybridflow.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "planning_policies")
@Data
public class PlanningPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false, length = 150)
    private String name;

    // Minimum required office days per employee in a week
    @Column(name = "min_office_days_per_week")
    private Integer minOfficeDaysPerWeek;  // used Integer so it can be null

    // Maximum allowed office days per employee in a week
    @Column(name = "max_office_days_per_week")
    private Integer maxOfficeDaysPerWeek; // used Integer so it can be null

    // Maximum number of employees allowed in office per day
    @Column(name = "daily_capacity")
    private Integer dailyCapacity; // used Integer so it can be null

    // Maximum allowed consecutive office days
    @Column(name = "max_consecutive_office_days")
    private Integer maxConsecutiveOfficeDays; // used Integer so it can be null

    // Minimum number of shared in-office days for a team
    @Column(name = "min_team_shared_days")
    private Integer minTeamSharedDays; // used Integer so it can be null

    // Simple fairness setting for now
    @Column(name = "fairness_weight")
    private Integer fairnessWeight;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}