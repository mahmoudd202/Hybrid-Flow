package com.example.hybridflow.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleGenerationRequestDTO {

    @NotNull(message = "officeId is required")
    private Long officeId;

    @NotNull(message = "planningPolicyId is required")
    private Long planningPolicyId;

    @NotEmpty(message = "At least one team must be selected")
    private List<Long> teamIds;

    @NotNull(message = "startDate is required")
    @FutureOrPresent(message = "startDate cannot be in the past")
    private LocalDate startDate;

    @NotNull(message = "endDate is required")
    private LocalDate endDate;
}