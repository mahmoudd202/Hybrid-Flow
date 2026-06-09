package com.example.hybridflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IndividualFairnessDTO {
    private Long userId;
    private String userEmail;
    private double score;
    private Map<String, String> breakdown;
}