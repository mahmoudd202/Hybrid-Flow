package com.example.hybridflow.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

import com.example.hybridflow.entity.PersonalTaskStatus;

@Data
@Builder
public class PersonalTaskResponseDTO {

    private Long id;
    private String title;
    private String description;
    private LocalDateTime dueDate;
    private PersonalTaskStatus status;
    private LocalDateTime createdAt;

    private Long ownerId;
    private String ownerEmail;
}