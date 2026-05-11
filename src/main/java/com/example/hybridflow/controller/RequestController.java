package com.example.hybridflow.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.example.hybridflow.dto.RequestResponseDTO;
import com.example.hybridflow.dto.RequestSubmissionDTO;
import com.example.hybridflow.entity.RequestStatus;
import com.example.hybridflow.security.CustomUserDetails;
import com.example.hybridflow.service.RequestService;

import java.util.List;

@RestController
@RequestMapping("/api/requests")
public class RequestController {
    private final RequestService requestService;

    public RequestController(RequestService requestService) {
        this.requestService = requestService;
    }

    // ──────────────────────────────────────────────────────────
    //  EMPLOYEE / MANAGER ENDPOINTS
    // ──────────────────────────────────────────────────────────

    @PostMapping
    @PreAuthorize("hasAnyRole('EMPLOYEE','MANAGER')")
    public ResponseEntity<RequestResponseDTO> submitRequest(
            @Valid @RequestBody RequestSubmissionDTO dto,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        if (userDetails == null) return ResponseEntity.status(401).build();

        RequestResponseDTO response = requestService.createRequest(dto, userDetails.getUser());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/my-requests")
    @PreAuthorize("hasAnyRole('EMPLOYEE','MANAGER')")
    public ResponseEntity<List<RequestResponseDTO>> getMyRequests(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        if (userDetails == null) return ResponseEntity.status(401).build();

        return ResponseEntity.ok(
                requestService.getRequestsByUser(userDetails.getUser())
        );
    }

    @DeleteMapping("/{requestId}")
    @PreAuthorize("hasAnyRole('EMPLOYEE','MANAGER')")
    public ResponseEntity<Void> deleteRequest(
            @PathVariable Long requestId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        if (userDetails == null) return ResponseEntity.status(401).build();

        requestService.deleteRequest(requestId, userDetails.getUser());
        return ResponseEntity.noContent().build();
    }

    // ──────────────────────────────────────────────────────────
    //  HR ENDPOINTS
    // ──────────────────────────────────────────────────────────

    @GetMapping("/pending")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<List<RequestResponseDTO>> getPendingRequests(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        if (userDetails == null) return ResponseEntity.status(401).build();

        return ResponseEntity.ok(
                requestService.getPendingRequestsByCompany(userDetails.getUser())
        );
    }

    @PatchMapping("/{requestId}/approve")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<RequestResponseDTO> approveRequest(
            @PathVariable Long requestId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        if (userDetails == null) return ResponseEntity.status(401).build();

        return ResponseEntity.ok(
                requestService.updateRequestStatus(requestId, RequestStatus.APPROVED, userDetails.getUser())
        );
    }

    @PatchMapping("/{requestId}/reject")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<RequestResponseDTO> rejectRequest(
            @PathVariable Long requestId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        if (userDetails == null) return ResponseEntity.status(401).build();

        return ResponseEntity.ok(
                requestService.updateRequestStatus(requestId, RequestStatus.REJECTED, userDetails.getUser())
        );
    }
}