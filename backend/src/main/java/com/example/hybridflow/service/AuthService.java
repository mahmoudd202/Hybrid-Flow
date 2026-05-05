package com.example.hybridflow.service;
import lombok.RequiredArgsConstructor;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.hybridflow.dto.*;
import com.example.hybridflow.entity.*;
import com.example.hybridflow.exception.BusinessValidationException;
import com.example.hybridflow.exception.ResourceNotFoundException;
import com.example.hybridflow.repository.InvalidatedTokenRepository;
import com.example.hybridflow.repository.UserProfileRepository;
import com.example.hybridflow.repository.UserRepository;
import com.example.hybridflow.repository.UserVerificationRepository;
import com.example.hybridflow.repository.InvitationRepository;

import com.example.hybridflow.security.JwtService;

import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final InvitationRepository invitationRepository;
    private final UserProfileRepository profileRepository;
    private final UserVerificationRepository verificationRepository;
    private final InvalidatedTokenRepository invalidatedTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final OtpService otpService;
    private final EmailService emailService;
    private final InvitationService invitationService;

    public AuthResponse login(LoginRequest request) {

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BusinessValidationException("Invalid email or password."));

        if (user.getPassword() == null) {
            throw new BusinessValidationException(
                    "This account uses social login. Please sign in with Google or GitHub.");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessValidationException("Invalid email or password.");
        }

        if (!user.isEnabled()) {
            throw new BusinessValidationException(
                    "Account not yet verified. Please check your email for the OTP.");
        }

        return new AuthResponse(jwtService.generateToken(user));
    }

    @Transactional
    public AuthActionResponse logout(String token) {

        if (!jwtService.isTokenValid(token)) {
            return AuthActionResponse.builder()
                    .message("Logged out successfully.")
                    .build();
        }

        if (invalidatedTokenRepository.existsByToken(token)) {
            return AuthActionResponse.builder()
                    .message("Logged out successfully.")
                    .build();
        }

        InvalidatedToken invalidated = new InvalidatedToken();
        invalidated.setToken(token);
        invalidated.setExpiresAt(jwtService.extractExpiry(token));
        invalidatedTokenRepository.save(invalidated);

        return AuthActionResponse.builder()
                .message("Logged out successfully.")
                .build();
    }

    @Transactional(readOnly = true)
    public EmailCheckResponseDTO checkEmail(String email) {
        // 1. Check InvitationRepository for pending invitations
        // Assuming Invitation has a method like
        // findFirstByEmailAndUsedFalseAndExpiryDateAfter
        // And that an invitation is considered 'pending' if it's not used and not
        // expired.
        Instant now = Instant.now();
        Optional<Invitation> invitationOptional = invitationRepository
                .findFirstByEmailAndUsedFalseAndExpiryDateAfter(email, now);

        if (invitationOptional.isPresent()) {
            return EmailCheckResponseDTO.builder()
                    .status("INVITATION_PENDING")
                    .redirectPath("/auth/register-invited")
                    .message("Invitation found. Please register using the invitation.")
                    .build();
        }

        // 2. If not in invitation repo, then check the database in UserRepository
        Optional<User> userOptional = userRepository.findByEmail(email);

        if (userOptional.isPresent()) {
            User user = userOptional.get();
            // Assuming 'enabled' field indicates if the account needs activation (e.g.,
            // email verification)
            if (!user.isEnabled()) {
                return EmailCheckResponseDTO.builder()
                        .status("ACCOUNT_INACTIVE")
                        .redirectPath("/auth/activate")
                        .message("Account found but not active. Please activate your account.")
                        .build();
            } else {
                // User exists and is enabled, implies they should just log in
                return EmailCheckResponseDTO.builder()
                        .status("ACCOUNT_ACTIVE")
                        .redirectPath("/auth/login") // Or a specific login page
                        .message("Account found and active. Please log in.")
                        .build();
            }
        } else {
            // 3. If not found in either, then not authorized
            return EmailCheckResponseDTO.builder()
                    .status("NOT_AUTHORIZED")
                    .redirectPath(null)
                    .message("Email not found in our system. Please contact support or request an invitation.")
                    .build();
        }
    }

    @Transactional
    public AuthActionResponse registerInvited(InvitedRegistrationRequest request) {

        Invitation invitation = invitationService.validateToken(request.getToken());

        if (userRepository.findByEmail(invitation.getEmail()).isPresent()) {
            throw new BusinessValidationException("A user with this email is already registered.");
        }

        // Create User. Email/role/team/company come from the invitation
        User user = new User();
        user.setEmail(invitation.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(invitation.getRole());
        user.setTeam(invitation.getTeam());
        user.setCompany(invitation.getCompany());
        user.setProvider(AuthProvider.LOCAL);
        user.setEnabled(true);
        userRepository.save(user);

        // Create UserProfile.
        UserProfile profile = new UserProfile();
        profile.setUser(user);
        profile.setFirstName(request.getFirstName());
        profile.setLastName(request.getLastName());
        profile.setDateOfBirth(request.getDateOfBirth());
        profile.setNationality(request.getNationality());
        profileRepository.save(profile);

        // Mark invitation as used and persist it.
        invitationService.markAsUsed(invitation);

        return AuthActionResponse.builder()
                .message("Registration complete. You can now log in.")
                .email(user.getEmail())
                .build();
    }

    @Transactional
    public AuthActionResponse activate(CsvActivationRequest request) {

        // Email must exist in DB
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BusinessValidationException(
                        "Your email has not been authorized by your organization. " +
                                "Please contact your HR."));

        // 2. Guard against re-activation.
        if (user.isEnabled()) {
            throw new BusinessValidationException(
                    "This account has already been activated. Please log in.");
        }

        // Only CSV-planted users have password=null.
        if (user.getPassword() != null) {
            throw new BusinessValidationException(
                    "This account requires OTP verification, not activation. " +
                            "Please check your email for the OTP.");
        }

        // CSV row must have company and team assigned.
        if (user.getCompany() == null || user.getTeam() == null) {
            throw new BusinessValidationException(
                    "Your account is not fully configured. Please contact your HR.");
        }

        // Guard against double-activation calls racing before OTP is verified.
        if (profileRepository.findByUserId(user.getId()).isPresent()) {
            throw new BusinessValidationException(
                    "Your profile has already been created. Please check your email for the OTP.");
        }

        // Set password. Account stays disabled until OTP is verified.
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setProvider(AuthProvider.LOCAL);
        userRepository.save(user);

        // Create UserProfile.
        UserProfile profile = new UserProfile();
        profile.setUser(user);
        profile.setFirstName(request.getFirstName());
        profile.setLastName(request.getLastName());
        profile.setDateOfBirth(request.getDateOfBirth());
        profile.setNationality(request.getNationality());
        profileRepository.save(profile);

        // Generate OTP and create verification record.
        String otp = otpService.generateOtp();
        UserVerification verification = new UserVerification();
        verification.setUser(user);
        verification.setOtpHash(passwordEncoder.encode(otp));
        verification.setOtpExpiry(otpService.expiryTime());
        verificationRepository.save(verification);

        // Send OTP email.
        emailService.sendOtpEmail(user.getEmail(), otp);

        return AuthActionResponse.builder()
                .message("OTP sent to " + user.getEmail() + ". Please verify your email to complete activation.")
                .status("PENDING_VERIFICATION")
                .build();
    }

    @Transactional
    public AuthActionResponse verify(VerifyOtpRequest request) {

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));

        if (user.isEnabled()) {
            throw new BusinessValidationException("Account is already verified. Please log in.");
        }

        UserVerification verification = verificationRepository
                .findByUserId(user.getId())
                .orElseThrow(() -> new BusinessValidationException(
                        "No pending verification found for this email."));

        if (verification.getOtpExpiry().isBefore(Instant.now())) {
            throw new BusinessValidationException("OTP has expired. Please request a new one.");
        }

        if (!passwordEncoder.matches(request.getOtp(), verification.getOtpHash())) {
            throw new BusinessValidationException("Invalid OTP.");
        }

        user.setEnabled(true);
        userRepository.save(user);

        verificationRepository.delete(verification);

        return AuthActionResponse.builder()
                .message("Account verified successfully. You can now log in.")
                .build();
    }

}