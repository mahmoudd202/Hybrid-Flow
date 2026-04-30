package com.example.hybridflow.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class CompanyRegistrationRequest {
    private String companyName;
    private String firstName;
    private String lastName;
    private String email;
    private String password;
    private LocalDate dateOfBirth;
    private String nationality;

}