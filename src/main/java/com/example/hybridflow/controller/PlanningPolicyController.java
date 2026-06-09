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

        @PostMapping
        @PreAuthorize("hasRole('HR')")
        public ResponseEntity<PlanningPolicyResponseDTO> createPolicy(
                        @Valid @RequestBody PlanningPolicyRequestDTO dto,
                        @AuthenticationPrincipal CustomUserDetails userDetails) {
                if (userDetails == null)
                        return ResponseEntity.status(401).build();

                PlanningPolicyResponseDTO response = planningPolicyService.createPolicy(dto, userDetails.getUser());

                return ResponseEntity.status(HttpStatus.CREATED).body(response);
        }

        @GetMapping
        @PreAuthorize("hasRole('HR')")
        public ResponseEntity<List<PlanningPolicyResponseDTO>> getPolicies(
                        @AuthenticationPrincipal CustomUserDetails userDetails) {
                if (userDetails == null)
                        return ResponseEntity.status(401).build();

                return ResponseEntity.ok(
                                planningPolicyService.getPoliciesByCompany(userDetails.getUser()));
        }

        @GetMapping("/{policyId}")
        @PreAuthorize("hasRole('HR')")
        public ResponseEntity<PlanningPolicyResponseDTO> getPolicyById(
                        @PathVariable Long policyId,
                        @AuthenticationPrincipal CustomUserDetails userDetails) {
                if (userDetails == null)
                        return ResponseEntity.status(401).build();

                return ResponseEntity.ok(
                                planningPolicyService.getPolicyById(policyId, userDetails.getUser()));
        }

        @PutMapping("/{policyId}")
        @PreAuthorize("hasRole('HR')")
        public ResponseEntity<PlanningPolicyResponseDTO> updatePolicy(
                        @PathVariable Long policyId,
                        @Valid @RequestBody PlanningPolicyRequestDTO dto,
                        @AuthenticationPrincipal CustomUserDetails userDetails) {
                if (userDetails == null)
                        return ResponseEntity.status(401).build();

                return ResponseEntity.ok(
                                planningPolicyService.updatePolicy(policyId, dto, userDetails.getUser()));
        }

        @DeleteMapping("/{policyId}")
        @PreAuthorize("hasRole('HR')")
        public ResponseEntity<Void> deletePolicy(
                        @PathVariable Long policyId,
                        @AuthenticationPrincipal CustomUserDetails userDetails) {
                if (userDetails == null)
                        return ResponseEntity.status(401).build();

                planningPolicyService.deletePolicy(policyId, userDetails.getUser());
                return ResponseEntity.noContent().build();
        }
}
