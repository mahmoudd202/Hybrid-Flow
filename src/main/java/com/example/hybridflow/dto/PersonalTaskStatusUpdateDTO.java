package com.example.hybridflow.dto;

import com.example.hybridflow.entity.PersonalTaskStatus;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PersonalTaskStatusUpdateDTO {

    @NotNull(message = "status is required")
    private PersonalTaskStatus status;
}