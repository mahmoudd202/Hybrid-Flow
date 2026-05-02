package com.example.hybridflow.dto;

import java.time.Instant;

import com.example.hybridflow.entity.Role;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class InvitationResponseDTO {
    private String email;
    private Role role;
    private Long teamId;
    private String teamName;
    private Long companyId;
    private String companyName;
    private String message;
    private Instant expiryDate;
}