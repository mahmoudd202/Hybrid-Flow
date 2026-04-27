package com.example.hybridflow.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import com.example.hybridflow.entity.InvalidatedToken;

import java.time.Instant;

public interface InvalidatedTokenRepository extends JpaRepository<InvalidatedToken, Long> {

    boolean existsByToken(String token);

    @Modifying
    @Query("DELETE FROM InvalidatedToken t WHERE t.expiresAt < :now")
    void deleteAllExpiredBefore(Instant now);
}