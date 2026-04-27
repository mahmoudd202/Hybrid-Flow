package com.example.hybridflow.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TeamResponseDTO {
    private Long id;
    private String name;
    private Long companyId;
    private String companyName;
    private Long officeId;
    private String officeName;
}