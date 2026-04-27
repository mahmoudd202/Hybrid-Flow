package com.example.hybridflow.service;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;

@Service
public class OtpService {

    public String generateOtp() {
        return String.valueOf(
                100000 + new SecureRandom().nextInt(900000)
        ); // 6-digit OTP
    }

    public Instant expiryTime() {
        return Instant.now().plusSeconds(600); // 10 minutes
    }
}