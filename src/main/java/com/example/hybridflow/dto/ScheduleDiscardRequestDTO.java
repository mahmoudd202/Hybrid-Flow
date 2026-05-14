package com.example.hybridflow.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleDiscardRequestDTO {

    @NotEmpty(message = "At least one schedule must be selected")
    private List<Long> scheduleIds;
}