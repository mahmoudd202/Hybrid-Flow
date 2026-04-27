package com.example.hybridflow.dto;

import lombok.Data;
import java.time.LocalDate;

@Data
public class InvitedRegistrationRequest {
    private String token;      // The OTP/Code received via email
    private String password;
    private String username;
    private LocalDate dateOfBirth;
    private String nationality;
}