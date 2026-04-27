package com.example.hybridflow.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

import com.example.hybridflow.entity.RequestType;


@Data
public class RequestSubmissionDTO {

    @NotNull(message = "type is required")
    private RequestType type;

    @NotNull(message = "startDate is required")
    @FutureOrPresent(message = "startDate must be today or in the future")
    private LocalDate startDate;

    @NotNull(message = "endDate is required")
    @FutureOrPresent(message = "endDate must be today or in the future")
    private LocalDate endDate;

    private String reason;
}