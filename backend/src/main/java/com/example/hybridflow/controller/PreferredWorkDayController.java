package com.example.hybridflow.controller;

import com.example.hybridflow.dto.PreferredWorkDaysRequestDTO;
import com.example.hybridflow.dto.PreferredWorkDaysResponseDTO;
import com.example.hybridflow.entity.User;
import com.example.hybridflow.service.PreferredWorkDayService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/preferences")
@RequiredArgsConstructor
public class PreferredWorkDayController {

    private final PreferredWorkDayService preferredWorkDayService;

    @PostMapping("/online-days")
    public ResponseEntity<PreferredWorkDaysResponseDTO> setPreferredDays(
            @RequestBody PreferredWorkDaysRequestDTO dto,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(preferredWorkDayService.setPreferredDays(dto, user));
    }

    @GetMapping("/online-days")
    public ResponseEntity<PreferredWorkDaysResponseDTO> getMyPreferredDays(
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(preferredWorkDayService.getMyPreferredDays(user));
    }
}
