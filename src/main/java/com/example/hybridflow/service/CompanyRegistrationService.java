package com.example.hybridflow.service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.example.hybridflow.dto.CompanyRegistrationRequest;
import com.example.hybridflow.entity.*;
import com.example.hybridflow.exception.BusinessValidationException;
import com.example.hybridflow.repository.CompanyRepository;
import com.example.hybridflow.repository.UserProfileRepository;
import com.example.hybridflow.repository.UserRepository;
import com.example.hybridflow.repository.UserVerificationRepository;

@Service
@RequiredArgsConstructor
public class CompanyRegistrationService {

    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserVerificationRepository verificationRepository;
    private final PasswordEncoder passwordEncoder;
    private final OtpService otpService;
    private final EmailService emailService;

    @Transactional
    public void registerCompanyAndAdmin(CompanyRegistrationRequest request) {

        if (companyRepository.findByCompanyName(request.getCompanyName()).isPresent()) {
            throw new BusinessValidationException("A company with this name already exists.");
        }

        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new BusinessValidationException("An account with this email already exists.");
        }

        // --- 1. Create and save the Company ---
        Company company = new Company();
        company.setCompanyName(request.getCompanyName());
        companyRepository.save(company);

        // --- 2. Create the User (HR Role) ---
        User user = new User();
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(Role.HR);
        user.setEnabled(false); // disabled until OTP verified via POST /auth/verify
        user.setProvider(AuthProvider.LOCAL);
        user.setCompany(company);
        user = userRepository.save(user);

        // --- 3. Create the User Profile ---
        UserProfile profile = new UserProfile();
        profile.setUser(user);
        profile.setFirstName(request.getFirstName());
        profile.setLastName(request.getLastName());
        profile.setDateOfBirth(request.getDateOfBirth());
        profile.setNationality(request.getNationality());
        userProfileRepository.save(profile);

        // --- 4. Generate OTP and create verification record ---
        String otp = otpService.generateOtp();

        UserVerification verification = new UserVerification();
        verification.setUser(user);
        verification.setOtpHash(passwordEncoder.encode(otp));
        verification.setOtpExpiry(otpService.expiryTime());
        verificationRepository.save(verification);

        // --- 5. Send OTP email ---
        emailService.sendOtpEmail(user.getEmail(), otp);
    }
}