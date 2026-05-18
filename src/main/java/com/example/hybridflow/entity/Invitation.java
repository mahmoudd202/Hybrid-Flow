package com.example.hybridflow.entity;
import jakarta.persistence.*;
import lombok.Data;
import java.time.Instant;
@Entity
@Table(name = "invitations")
@Data
public class Invitation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false)
    private String email;
    @Column(nullable = false, unique = true)
    private String token;  //invitation code
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
@lombok.EqualsAndHashCode.Exclude @lombok.ToString.Exclude
    private Team team;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
@lombok.EqualsAndHashCode.Exclude @lombok.ToString.Exclude
    private Company company;
    private Instant expiryDate;
    private boolean used = false;
}
