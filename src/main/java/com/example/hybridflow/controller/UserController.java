package com.example.hybridflow.controller;

import com.example.hybridflow.dto.CurrentUserResponseDTO;
import com.example.hybridflow.dto.EmployeeDetailsResponseDTO;
import com.example.hybridflow.dto.MoveEmployeeRequestDTO;
import com.example.hybridflow.dto.UpdateRoleRequestDTO;
import com.example.hybridflow.dto.UpdateProfileRequest;
import com.example.hybridflow.security.CustomUserDetails;
import com.example.hybridflow.service.UserService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/{employeeId}")
    @PreAuthorize("hasAnyRole('HR', 'MANAGER', 'EMPLOYEE')")
    public ResponseEntity<EmployeeDetailsResponseDTO> getEmployeeDetails(
            @PathVariable Long employeeId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(401).build();
        }

        if (userDetails.getUser().getRole().name().equals("EMPLOYEE")
                && !userDetails.getUser().getId().equals(employeeId)) {
            return ResponseEntity.status(403).build(); // Forbidden
        }

        EmployeeDetailsResponseDTO employeeDetails = userService.getEmployeeDetails(employeeId, userDetails.getUser());
        return ResponseEntity.ok(employeeDetails);
    }

    @PatchMapping("/{employeeId}/role")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<EmployeeDetailsResponseDTO> updateEmployeeRole(
            @PathVariable Long employeeId,
            @Valid @RequestBody UpdateRoleRequestDTO requestDTO,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(401).build();
        }

        EmployeeDetailsResponseDTO updatedEmployee = userService.updateEmployeeRole(employeeId, requestDTO.getNewRole(),
                userDetails.getUser());
        return ResponseEntity.ok(updatedEmployee);
    }

    @PatchMapping("/{employeeId}/team")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<EmployeeDetailsResponseDTO> moveEmployeeToTeam(
            @PathVariable Long employeeId,
            @Valid @RequestBody MoveEmployeeRequestDTO dto,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null)
            return ResponseEntity.status(401).build();

        EmployeeDetailsResponseDTO response = userService.moveEmployeeToTeam(
                employeeId, dto.getNewTeamId(), userDetails.getUser());
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{employeeId}/deactivate")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<EmployeeDetailsResponseDTO> deactivateEmployee(
            @PathVariable Long employeeId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(401).build();
        }

        EmployeeDetailsResponseDTO deactivatedEmployee = userService.deactivateEmployee(employeeId,
                userDetails.getUser());
        return ResponseEntity.ok(deactivatedEmployee);
    }

    @PatchMapping("/{employeeId}/activate")
    @PreAuthorize("hasRole('HR')")
    public ResponseEntity<EmployeeDetailsResponseDTO> activateEmployee(
            @PathVariable Long employeeId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(401).build();
        }

        EmployeeDetailsResponseDTO activatedEmployee = userService.activateEmployee(employeeId,
                userDetails.getUser());
        return ResponseEntity.ok(activatedEmployee);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('HR', 'MANAGER', 'EMPLOYEE')")
    public ResponseEntity<List<EmployeeDetailsResponseDTO>> getAllEmployees(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(401).build();
        }

        List<EmployeeDetailsResponseDTO> employees = userService.getAllEmployees(userDetails.getUser());
        return ResponseEntity.ok(employees);
    }

    @GetMapping("/me")
    public ResponseEntity<CurrentUserResponseDTO> getCurrentUser(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(userService.getMe(userDetails.getUser()));
    }

    @PatchMapping("/me/profile")
    public ResponseEntity<CurrentUserResponseDTO> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(userService.updateProfile(userDetails.getUser(), request));
    }
}
