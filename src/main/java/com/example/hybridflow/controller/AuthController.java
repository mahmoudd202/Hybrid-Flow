package com.example.hybridflow.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.example.hybridflow.dto.*;
import com.example.hybridflow.security.CustomUserDetails;
import com.example.hybridflow.service.AuthService;
import com.example.hybridflow.service.CompanyRegistrationService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final CompanyRegistrationService companyRegistrationService;

    public AuthController(AuthService authService,
            CompanyRegistrationService companyRegistrationService) {
        this.authService = authService;
        this.companyRegistrationService = companyRegistrationService;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/logout")
    public ResponseEntity<AuthActionResponse> logout(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(401).body(
                    AuthActionResponse.builder().message("Unauthorized.").build());
        }

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(401).body(
                    AuthActionResponse.builder().message("Missing or invalid Authorization header.").build());
        }

        String token = authHeader.substring(7);
        return ResponseEntity.ok(authService.logout(token));
    }

    @PostMapping("/register-company")
    public ResponseEntity<AuthActionResponse> registerCompany(
            @RequestBody CompanyRegistrationRequest request) {
        companyRegistrationService.registerCompanyAndAdmin(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(AuthActionResponse.builder()
                .message("Registration successful. OTP sent to " + request.getEmail())
                .status("PENDING_VERIFICATION")
                .build());
    }

    @PostMapping("/check-email")
    public ResponseEntity<EmailCheckResponseDTO> checkEmail(@Valid @RequestBody EmailCheckRequestDTO requestDTO) {
        return ResponseEntity.ok(authService.checkEmail(requestDTO.getEmail()));
    }

    @PostMapping("/register")
    public ResponseEntity<AuthActionResponse> register(@Valid @RequestBody InvitedRegistrationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/verify")
    public ResponseEntity<AuthActionResponse> verify(@RequestBody VerifyOtpRequest request) {
        return ResponseEntity.ok(authService.verify(request));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<AuthActionResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        return ResponseEntity.ok(authService.forgotPassword(request));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<AuthActionResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        return ResponseEntity.ok(authService.resetPassword(request));
    }
}
