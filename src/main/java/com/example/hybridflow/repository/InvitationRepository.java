package com.example.hybridflow.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.hybridflow.entity.Invitation;

import java.time.Instant;
import java.util.Optional;

public interface InvitationRepository extends JpaRepository<Invitation, Long> {
    Optional<Invitation> findByTokenAndUsedFalse(String token);
    boolean existsByEmailAndUsedFalse(String email);
    Optional<Invitation> findFirstByEmailAndUsedFalseAndExpiryDateAfter(String email, Instant now);}
