package com.example.hybridflow.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

import com.example.hybridflow.entity.MeetingType;


@Data
public class MeetingRequestDTO {

    @NotBlank(message = "Title is required")
    private String title;

    @NotNull(message = "startTime is required")
    @Future(message = "startTime must be in the future")
    private LocalDateTime startTime;

    @NotNull(message = "endTime is required")
    @Future(message = "endTime must be in the future")
    private LocalDateTime endTime;

    @NotNull(message = "officeId is required")
    private Long officeId;

    @NotNull(message = "type is required")
    private MeetingType type;

    @NotEmpty(message = "At least one participating team is required")
    private List<Long> participatingTeamIds;
}