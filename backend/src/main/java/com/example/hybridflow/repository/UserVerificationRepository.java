package com.example.hybridflow.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.hybridflow.entity.UserVerification;

import java.util.Optional;

public interface UserVerificationRepository
        extends JpaRepository<UserVerification, Long> {

    Optional<UserVerification> findByUserId(Long userId);
}