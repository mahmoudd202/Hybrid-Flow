package com.example.hybridflow.dto;

import lombok.Data;

@Data
public class CsvRowDto {
    private int rowNumber;
    private String email;
    private String role;
    private String teamName;
    private boolean valid;
    private boolean saved;
    private String errorMessage;

}
