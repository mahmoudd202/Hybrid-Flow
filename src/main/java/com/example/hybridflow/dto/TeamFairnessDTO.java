package com.example.hybridflow.dto;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeamFairnessDTO {
    private Long teamId;
    private String teamName;
    private double score;
    private Map<String, String> breakdown; // Optional: details like average individual score, penalty
}