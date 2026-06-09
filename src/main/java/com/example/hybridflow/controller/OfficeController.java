package com.example.hybridflow.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.example.hybridflow.dto.FirstOfficeResponseDTO;
import com.example.hybridflow.dto.OfficeCreateRequestDTO;
import com.example.hybridflow.dto.OfficeResponseDTO;
import com.example.hybridflow.security.CustomUserDetails;
import com.example.hybridflow.service.OfficeService;

import java.util.List;

@RestController
@RequestMapping("/api/offices")
@RequiredArgsConstructor
public class OfficeController {

    private final OfficeService officeService;

    @GetMapping("/company")
    @PreAuthorize("hasAnyRole('MANAGER','HR')")
    public ResponseEntity<List<OfficeResponseDTO>> getByCompany(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(401).build();
        }

        return ResponseEntity.ok(
                officeService.getByCompany(userDetails.getUser()));
    }

    @GetMapping("/company/first")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<FirstOfficeResponseDTO> getFirstByCompany(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(401).build();
        }

        return ResponseEntity.ok(
                officeService.getFirstByCompany(userDetails.getUser()));
    }

    @PostMapping
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<OfficeResponseDTO> createOffice(
            @Valid @RequestBody OfficeCreateRequestDTO dto,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(401).build();
        }

        OfficeResponseDTO response = officeService.createOffice(dto, userDetails.getUser());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{officeId}")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<OfficeResponseDTO> updateOffice(
            @PathVariable Long officeId,
            @Valid @RequestBody OfficeCreateRequestDTO dto,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(401).build();
        }

        OfficeResponseDTO response = officeService.updateOffice(officeId, dto, userDetails.getUser());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{officeId}")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<Void> deleteOffice(
            @PathVariable Long officeId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(401).build();
        }

        officeService.deleteOffice(officeId, userDetails.getUser());
        return ResponseEntity.noContent().build();
    }
}