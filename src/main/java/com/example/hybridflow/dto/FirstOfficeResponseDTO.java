package com.example.hybridflow.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FirstOfficeResponseDTO {
    private boolean hasOffice;
    private OfficeResponseDTO office;
}