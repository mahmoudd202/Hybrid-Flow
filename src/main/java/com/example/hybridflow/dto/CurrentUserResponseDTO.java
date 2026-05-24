package com.example.hybridflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CurrentUserResponseDTO {
    private Long id;
    private String email;
    private String role;
    private Long companyId;
    private String companyName;
    private Long teamId;
    private String teamName;
    private String firstName;
    private String lastName;
}