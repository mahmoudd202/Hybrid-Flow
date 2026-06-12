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
import com.example.hybridflow.repository.TeamRepository;
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
    private final TeamRepository teamRepository;

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

        UserProfile profile = profileRepository.findByUserId(user.getId()).orElse(null);

        CurrentUserResponseDTO userDto = CurrentUserResponseDTO.builder()
                .id(user.getId())
                .email(user.getEmail())
                .role(user.getRole().name())
                .companyId(user.getCompany() != null ? user.getCompany().getId() : null)
                .companyName(user.getCompany() != null ? user.getCompany().getCompanyName() : null)
                .teamId(user.getTeam() != null ? user.getTeam().getId() : null)
                .teamName(user.getTeam() != null ? user.getTeam().getName() : null)
                .firstName(profile != null ? profile.getFirstName() : null)
                .lastName(profile != null ? profile.getLastName() : null)
                .build();

        return new AuthResponse(jwtService.generateToken(user), userDto);
    }

    @Transactional
    public AuthActionResponse logout(String token) {
        if (!jwtService.isTokenValid(token)) {
            return AuthActionResponse.builder().message("Logged out successfully.").build();
        }

        if (invalidatedTokenRepository.existsByToken(token)) {
            return AuthActionResponse.builder().message("Logged out successfully.").build();
        }

        InvalidatedToken invalidated = new InvalidatedToken();
        invalidated.setToken(token);
        invalidated.setExpiresAt(jwtService.extractExpiry(token));
        invalidatedTokenRepository.save(invalidated);

        return AuthActionResponse.builder().message("Logged out successfully.").build();
    }

    @Transactional(readOnly = true)
    public EmailCheckResponseDTO checkEmail(String email) {
        Instant now = Instant.now();

        Optional<Invitation> invitationOptional = invitationRepository
                .findFirstByEmailAndUsedFalseAndExpiryDateAfter(email, now);

        if (invitationOptional.isPresent()) {
            return EmailCheckResponseDTO.builder()
                    .status("NEEDS_REGISTRATION")
                    .redirectPath("/auth/register")
                    .message("Invitation found. Please complete your registration.")
                    .build();
        }

        Optional<User> userOptional = userRepository.findByEmail(email);

        if (userOptional.isPresent()) {
            User user = userOptional.get();
            if (!user.isEnabled()) {
                return EmailCheckResponseDTO.builder()
                        .status("NEEDS_REGISTRATION")
                        .redirectPath("/auth/register")
                        .message("Account found but not active. Please complete your registration.")
                        .build();
            } else {
                return EmailCheckResponseDTO.builder()
                        .status("ACTIVE")
                        .redirectPath("/auth/login")
                        .message("Account found and active. Please log in.")
                        .build();
            }
        }

        return EmailCheckResponseDTO.builder()
                .status("NOT_AUTHORIZED")
                .redirectPath(null)
                .message("Email not found in our system. Please contact your HR.")
                .build();
    }

    @Transactional
    public AuthActionResponse register(InvitedRegistrationRequest request) {
        String email = request.getEmail();
        Instant now = Instant.now();

        Optional<User> existingUserOpt = userRepository.findByEmail(email);
        if (existingUserOpt.isPresent() && existingUserOpt.get().isEnabled()) {
            throw new BusinessValidationException("A user with this email is already registered and active.");
        }

        User user;
        Invitation invitation = null;

        if (existingUserOpt.isPresent()) {
            user = existingUserOpt.get();
            user.setPassword(passwordEncoder.encode(request.getPassword()));
            user = userRepository.save(user);
        } else {
            invitation = invitationRepository.findFirstByEmailAndUsedFalseAndExpiryDateAfter(email, now)
                    .orElseThrow(() -> new BusinessValidationException("No valid invitation found for this email."));

            user = new User();
            user.setEmail(email);
            user.setPassword(passwordEncoder.encode(request.getPassword()));
            user.setRole(invitation.getRole());
            user.setTeam(invitation.getTeam());
            user.setCompany(invitation.getCompany());
            user.setProvider(AuthProvider.LOCAL);
            user.setEnabled(false);
            user = userRepository.save(user);
        }

        if (user.getRole() == Role.MANAGER && user.getTeam() != null) {
            Team team = user.getTeam();
            team.setManager(user);
            teamRepository.save(team);
        }

        UserProfile profile = profileRepository.findByUserId(user.getId()).orElse(new UserProfile());
        profile.setUser(user);
        profile.setFirstName(request.getFirstName());
        profile.setLastName(request.getLastName());
        profile.setDateOfBirth(request.getDateOfBirth());
        profile.setNationality(request.getNationality());
        profileRepository.save(profile);

        if (invitation != null) {
            invitationService.markAsUsed(invitation);
        }

        String otp = otpService.generateOtp();
        UserVerification verification = verificationRepository.findByUserId(user.getId())
                .orElse(new UserVerification());
        verification.setUser(user);
        verification.setOtpHash(passwordEncoder.encode(otp));
        verification.setOtpExpiry(otpService.expiryTime());
        verificationRepository.save(verification);

        emailService.sendOtpEmail(email, otp);

        return AuthActionResponse.builder()
                .message("Registration details saved. An OTP has been sent to your email for verification.")
                .status("PENDING_VERIFICATION")
                .email(email)
                .build();
    }

    @Transactional
    public AuthActionResponse verify(VerifyOtpRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));

        if (user.isEnabled()) {
            throw new BusinessValidationException("Account is already verified. Please log in.");
        }

        UserVerification verification = verificationRepository.findByUserId(user.getId())
                .orElseThrow(() -> new BusinessValidationException("No pending verification found for this email."));

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

    @Transactional
    public AuthActionResponse forgotPassword(ForgotPasswordRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));

        String otp = otpService.generateOtp();
        UserVerification verification = verificationRepository.findByUserId(user.getId())
                .orElse(new UserVerification());
        verification.setUser(user);
        verification.setOtpHash(passwordEncoder.encode(otp));
        verification.setOtpExpiry(otpService.expiryTime());
        verificationRepository.save(verification);

        emailService.sendForgotPasswordEmail(user.getEmail(), otp);

        return AuthActionResponse.builder()
                .message("Password reset OTP sent to your email.")
                .build();
    }

    @Transactional
    public AuthActionResponse resetPassword(ResetPasswordRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));

        UserVerification verification = verificationRepository.findByUserId(user.getId())
                .orElseThrow(() -> new BusinessValidationException("No pending password reset found."));

        if (verification.getOtpExpiry().isBefore(Instant.now())) {
            throw new BusinessValidationException("OTP has expired.");
        }

        if (!passwordEncoder.matches(request.getOtp(), verification.getOtpHash())) {
            throw new BusinessValidationException("Invalid OTP.");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        verificationRepository.delete(verification);

        return AuthActionResponse.builder()
                .message("Password has been reset successfully.")
                .build();
    }

    @Transactional
    public AuthActionResponse resendOtp(ResendOtpRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));

        if (user.isEnabled()) {
            throw new BusinessValidationException("Account is already verified. Please log in.");
        }

        String otp = otpService.generateOtp();
        UserVerification verification = verificationRepository.findByUserId(user.getId())
                .orElse(new UserVerification());
        verification.setUser(user);
        verification.setOtpHash(passwordEncoder.encode(otp));
        verification.setOtpExpiry(otpService.expiryTime());
        verificationRepository.save(verification);

        emailService.sendOtpEmail(user.getEmail(), otp);

        return AuthActionResponse.builder()
                .message("A new OTP has been sent to your email for verification.")
                .status("PENDING_VERIFICATION")
                .email(user.getEmail())
                .build();
    }
}
