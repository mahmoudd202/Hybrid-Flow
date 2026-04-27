package com.example.hybridflow.controller;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.example.hybridflow.dto.ScheduleViewResponseDTO;
import com.example.hybridflow.security.CustomUserDetails;
import com.example.hybridflow.service.ScheduleViewService;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/schedules")
public class ScheduleViewController {

    private final ScheduleViewService scheduleViewService;

    public ScheduleViewController(ScheduleViewService scheduleViewService) {
        this.scheduleViewService = scheduleViewService;
    }

    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('EMPLOYEE','MANAGER','HR')")
    public ResponseEntity<ScheduleViewResponseDTO> getMySchedule(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        if (userDetails == null) return ResponseEntity.status(401).build();

        return ResponseEntity.ok(
                scheduleViewService.getMySchedule(from, to, userDetails.getUser())
        );
    }

    @GetMapping("/employees/{employeeId}")
    @PreAuthorize("hasAnyRole('EMPLOYEE','MANAGER','HR')")
    public ResponseEntity<ScheduleViewResponseDTO> getEmployeeSchedule(
            @PathVariable Long employeeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        if (userDetails == null) return ResponseEntity.status(401).build();

        return ResponseEntity.ok(
                scheduleViewService.getEmployeeSchedule(employeeId, from, to, userDetails.getUser())
        );
    }

    @GetMapping("/team/{teamId}")
    @PreAuthorize("hasAnyRole('EMPLOYEE','MANAGER','HR')")
    public ResponseEntity<ScheduleViewResponseDTO> getTeamSchedule(
            @PathVariable Long teamId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        if (userDetails == null) return ResponseEntity.status(401).build();

        return ResponseEntity.ok(
                scheduleViewService.getTeamSchedule(teamId, from, to, userDetails.getUser())
        );
    }

    @GetMapping("/company")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<ScheduleViewResponseDTO> getCompanySchedule(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        if (userDetails == null) return ResponseEntity.status(401).build();

        return ResponseEntity.ok(
                scheduleViewService.getCompanySchedule(from, to, userDetails.getUser())
        );
    }

    @GetMapping("/office/{officeId}")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<ScheduleViewResponseDTO> getOfficeSchedule(
            @PathVariable Long officeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        if (userDetails == null) return ResponseEntity.status(401).build();

        return ResponseEntity.ok(
                scheduleViewService.getOfficeSchedule(officeId, from, to, userDetails.getUser())
        );
    }
}
