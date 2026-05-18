package com.example.hybridflow.entity;
import jakarta.persistence.*;
import lombok.Data;
@Entity
@Table(name = "users")
@Data
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(unique = true, nullable = false)
    private String email;
    private String password;
    private boolean enabled; // email verified or not (will be saved in 0 and 1 in the database)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;
    @Enumerated(EnumType.STRING)
    private AuthProvider provider;
    private String providerId;
    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL)
    // UserProfile table references user
@lombok.EqualsAndHashCode.Exclude @lombok.ToString.Exclude
    private UserProfile profile;
    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
@lombok.EqualsAndHashCode.Exclude @lombok.ToString.Exclude
    private UserVerification verification;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
@lombok.EqualsAndHashCode.Exclude @lombok.ToString.Exclude
    private Team team;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
@lombok.EqualsAndHashCode.Exclude @lombok.ToString.Exclude
    private Company company;
}
