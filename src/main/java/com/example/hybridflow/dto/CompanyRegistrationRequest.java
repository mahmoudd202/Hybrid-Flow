package com.example.hybridflow.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class CompanyRegistrationRequest {
    private String companyName;
    private String username;
    private String email;
    private String password;
    private LocalDate dateOfBirth;
    private String nationality;

}