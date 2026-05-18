package com.example.hybridflow.entity;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;
@Entity
@Table(name = "user_profiles")
@Data
public class UserProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @OneToOne
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    // UserProfile table uses user_id as FK
@lombok.EqualsAndHashCode.Exclude @lombok.ToString.Exclude
    private User user;
    @Column(nullable = false, length = 100)
    private String firstName;
    @Column(nullable = false, length = 100)
    private String lastName;
    private LocalDate dateOfBirth;
    private String nationality;
}
