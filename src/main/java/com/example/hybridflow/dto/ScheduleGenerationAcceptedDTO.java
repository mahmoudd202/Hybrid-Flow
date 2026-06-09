package com.example.hybridflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleGenerationAcceptedDTO {

    private Long runId;

    private String status;

    private String message;

    private Long planningPolicyId;
    private String planningPolicyName;
}
