package com.example.hybridflow.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "meetings")
@Data
public class Meeting {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "host_id", nullable = false)
    private User host; // Many Meetings to One Host

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "office_id", nullable = false)
    private Office office;

    @Enumerated(EnumType.STRING)
    private MeetingType type;

    // Restricted to teams within the same office but not enforced by code,, can be
    // handled by the service layer
    @ManyToMany
    @JoinTable(name = "meeting_teams", joinColumns = @JoinColumn(name = "meeting_id"), inverseJoinColumns = @JoinColumn(name = "team_id"))
    private List<Team> participatingTeams = new ArrayList<>();
}