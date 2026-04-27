package com.example.hybridflow.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class CsvActivationRequest {
    private String email;
    private String password;
    private String username;
    private LocalDate dateOfBirth;
    private String nationality;
}