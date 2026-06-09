package com.example.hybridflow.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.example.hybridflow.entity.RequestStatus;
import com.example.hybridflow.entity.RequestType;

@Data
@Builder
public class RequestResponseDTO {

    private Long id;
    private RequestType type;
    private RequestStatus status;
    private LocalDate startDate;
    private LocalDate endDate;
    private String reason;
    private LocalDateTime createdAt;

    private Long requesterId;
    private String requesterEmail;
    private Long companyId;
    private Long handledById;
    private String handledByEmail;
}
