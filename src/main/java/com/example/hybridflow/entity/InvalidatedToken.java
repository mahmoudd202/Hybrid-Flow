package com.example.hybridflow.entity;
import jakarta.persistence.*;
import lombok.Data;
import java.time.Instant;
@Entity
@Table(name = "invalidated_tokens")
@Data
public class InvalidatedToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 2048)
    private String token;
    @Column(nullable = false)
    private Instant expiresAt;
}
