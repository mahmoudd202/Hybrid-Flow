package com.example.hybridflow.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EmailCheckResponseDTO {
    private String status;
    private String redirectPath;
    private String message;
}