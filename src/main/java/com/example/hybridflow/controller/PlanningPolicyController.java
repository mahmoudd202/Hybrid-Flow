package com.example.hybridflow.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
 
import com.example.hybridflow.dto.PlanningPolicyRequestDTO;
import com.example.hybridflow.dto.PlanningPolicyResponseDTO;
import com.example.hybridflow.security.CustomUserDetails;
import com.example.hybridflow.service.PlanningPolicyService;
 
import java.util.List;
 
@RestController
@RequestMapping("/api/planning-policies")
@RequiredArgsConstructor
public class PlanningPolicyController {
 
    private final PlanningPolicyService planningPolicyService;
 
    /**
     * POST /api/planning-policies
     * HR creates a new planning policy for their company.
     */
    @PostMapping
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<PlanningPolicyResponseDTO> createPolicy(
            @Valid @RequestBody PlanningPolicyRequestDTO dto,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        if (userDetails == null) return ResponseEntity.status(401).build();
 
        PlanningPolicyResponseDTO response =
                planningPolicyService.createPolicy(dto, userDetails.getUser());
 
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
 
    /**
     * GET /api/planning-policies
     * HR retrieves all planning policies for their company.
     */
    @GetMapping
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<List<PlanningPolicyResponseDTO>> getPolicies(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        if (userDetails == null) return ResponseEntity.status(401).build();
 
        return ResponseEntity.ok(
                planningPolicyService.getPoliciesByCompany(userDetails.getUser()));
    }
 
    /**
     * GET /api/planning-policies/{policyId}
     * HR retrieves a specific planning policy by id.
     * I actually don't want to use it
     */
    @GetMapping("/{policyId}")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<PlanningPolicyResponseDTO> getPolicyById(
            @PathVariable Long policyId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        if (userDetails == null) return ResponseEntity.status(401).build();
 
        return ResponseEntity.ok(
                planningPolicyService.getPolicyById(policyId, userDetails.getUser()));
    }
 
    /**
     * PUT /api/planning-policies/{policyId}
     * HR updates an existing planning policy.
     */
    @PutMapping("/{policyId}")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<PlanningPolicyResponseDTO> updatePolicy(
            @PathVariable Long policyId,
            @Valid @RequestBody PlanningPolicyRequestDTO dto,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        if (userDetails == null) return ResponseEntity.status(401).build();
 
        return ResponseEntity.ok(
                planningPolicyService.updatePolicy(policyId, dto, userDetails.getUser()));
    }
 
    /**
     * DELETE /api/planning-policies/{policyId}
     * HR deletes a planning policy.
     */
    @DeleteMapping("/{policyId}")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<Void> deletePolicy(
            @PathVariable Long policyId,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        if (userDetails == null) return ResponseEntity.status(401).build();
 
        planningPolicyService.deletePolicy(policyId, userDetails.getUser());
        return ResponseEntity.noContent().build();
    }
}
