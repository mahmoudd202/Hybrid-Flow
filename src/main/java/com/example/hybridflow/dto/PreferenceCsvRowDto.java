package com.example.hybridflow.dto;

import lombok.Data;
import java.util.List;

@Data
public class PreferenceCsvRowDto {
    private int rowNumber;
    private String email;
    private List<String> preferredDays;
    private boolean valid;
    private boolean saved;
    private String errorMessage;
}
