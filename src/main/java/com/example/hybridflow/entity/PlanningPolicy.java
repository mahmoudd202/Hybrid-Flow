package com.example.hybridflow.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
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
    @lombok.EqualsAndHashCode.Exclude
    @lombok.ToString.Exclude
    private Company company;
    @Column(nullable = false, length = 150)
    private String name;
    @NotNull
    @Min(1)
    @Max(5)
    @Column(name = "working_days_per_week", nullable = false)
    private Integer workingDaysPerWeek;
    @NotNull
    @Min(0)
    @Column(name = "min_office_days_per_week", nullable = false)
    private Integer minOfficeDaysPerWeek;
    @NotNull
    @Min(0)
    @Column(name = "max_office_days_per_week", nullable = false)
    private Integer maxOfficeDaysPerWeek;
    @NotNull
    @Min(1)
    @Column(name = "max_consecutive_office_days", nullable = false)
    private Integer maxConsecutiveOfficeDays;
    @NotNull
    @Min(0)
    @Column(name = "min_team_shared_days", nullable = false)
    private Integer minTeamSharedDays;
    @NotNull
    @Min(0)
    @Max(100)
    @Column(name = "co_presence_threshold_percentage_per_day", nullable = false)
    private Integer coPresenceThresholdPercentagePerDay;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
