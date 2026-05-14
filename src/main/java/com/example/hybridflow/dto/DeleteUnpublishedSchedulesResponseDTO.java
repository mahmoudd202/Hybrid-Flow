package com.example.hybridflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response returned by DELETE /api/schedules/unpublished.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeleteUnpublishedSchedulesResponseDTO {

    private String status;
    private String message;

    /** IDs of the schedule records that were permanently deleted. */
    private List<Long> deletedScheduleIds;
}