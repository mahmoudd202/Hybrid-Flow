package com.example.hybridflow.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.hybridflow.dto.AuthActionResponse;
import com.example.hybridflow.dto.AuthResponse;
import com.example.hybridflow.dto.CompanyRegistrationRequest;
import com.example.hybridflow.dto.CsvActivationRequest;
import com.example.hybridflow.dto.EmailCheckRequestDTO;
import com.example.hybridflow.dto.EmailCheckResponseDTO;
import com.example.hybridflow.dto.InvitedRegistrationRequest;
import com.example.hybridflow.dto.LoginRequest;
import com.example.hybridflow.dto.VerifyOtpRequest;
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
                    AuthActionResponse.builder()
                            .message("Unauthorized.")
                            .build());
        }

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(401).body(
                    AuthActionResponse.builder()
                            .message("Missing or invalid Authorization header.")
                            .build());
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
        EmailCheckResponseDTO response = authService.checkEmail(requestDTO.getEmail());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/register-invited")
    public ResponseEntity<AuthActionResponse> registerInvited(
            @RequestBody InvitedRegistrationRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(authService.registerInvited(request));
    }

    @PostMapping("/activate") //csv inserted users
    public ResponseEntity<AuthActionResponse> activate(
            @RequestBody CsvActivationRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(authService.activate(request));
    }

    @PostMapping("/verify")
    public ResponseEntity<AuthActionResponse> verify(@RequestBody VerifyOtpRequest request) {
        return ResponseEntity.ok(authService.verify(request));
    }
}