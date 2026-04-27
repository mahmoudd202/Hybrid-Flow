package com.example.hybridflow.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.hybridflow.entity.UserProfile;

import java.util.Optional;

public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {

    Optional<UserProfile> findByUsername(String username);

    Optional<UserProfile> findByUserId(Long userId);
}