package com.example.hybridflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

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
    private String nationality;
    private LocalDate dateOfBirth;
}