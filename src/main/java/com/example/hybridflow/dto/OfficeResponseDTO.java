package com.example.hybridflow.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OfficeResponseDTO {
    private Long id;
    private String name;
    private Integer maxCapacity;
    private Long companyId;
    private String companyName;
}