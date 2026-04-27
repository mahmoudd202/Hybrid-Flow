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
    //UserProfile table uses user_id as FK
    private User user;

    @Column(unique = true, nullable = false)
    private String username;

    private LocalDate dateOfBirth;
    private String nationality;
}