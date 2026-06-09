package com.example.hybridflow.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.hybridflow.repository.InvalidatedTokenRepository;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class TokenCleanupService {

    private final InvalidatedTokenRepository invalidatedTokenRepository;

    @Scheduled(fixedRate = 3_600_000)
    @Transactional
    public void purgeExpiredTokens() {
        invalidatedTokenRepository.deleteAllExpiredBefore(Instant.now());
    }
}