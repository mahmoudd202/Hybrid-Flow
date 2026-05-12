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

    /**
     * office.getByCompany
     * Frontend usage: fetch all offices for the authenticated HR user's company.
     */
    @GetMapping("/company")
    @PreAuthorize("hasAnyRole('MANAGER','HR')")
    public ResponseEntity<List<OfficeResponseDTO>> getByCompany(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        if (userDetails == null) {
            return ResponseEntity.status(401).build();
        }

        return ResponseEntity.ok(
                officeService.getByCompany(userDetails.getUser())
        );
    }

    /**
     * office.getFirstByCompany
     * Frontend usage: first-office enforcement before allowing HR dashboard access.
     */
    @GetMapping("/company/first")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<FirstOfficeResponseDTO> getFirstByCompany(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        if (userDetails == null) {
            return ResponseEntity.status(401).build();
        }

        return ResponseEntity.ok(
                officeService.getFirstByCompany(userDetails.getUser())
        );
    }

    /**
     * office.create
     * Frontend usage: create first office or additional offices.
     */
    @PostMapping
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<OfficeResponseDTO> createOffice(
            @Valid @RequestBody OfficeCreateRequestDTO dto,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        if (userDetails == null) {
            return ResponseEntity.status(401).build();
        }

        OfficeResponseDTO response = officeService.createOffice(dto, userDetails.getUser());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}