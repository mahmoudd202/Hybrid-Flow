package com.example.hybridflow.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class OfficeCreateRequestDTO {

    @NotBlank(message = "Office name is required")
    @Size(max = 150, message = "Office name must be at most 150 characters")
    private String name;

    @NotNull(message = "maxCapacity is required")
    @Min(value = 1, message = "maxCapacity must be at least 1")
    private Integer maxCapacity;
}