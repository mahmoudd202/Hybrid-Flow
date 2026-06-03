package com.example.hybridflow.service;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class EmailService {

    private final JavaMailSender mailSender;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Async("emailExecutor")
    public void sendOtpEmail(String to, String otp) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject("Verify your account");
        message.setText(
                "Your verification code is: " + otp +
                        "\nThis code expires in 10 minutes.");
        sendEmailWithRetry(message, "verification OTP to " + to);
    }

    @Async("emailExecutor")
    public void sendInvitationEmail(String to, String role) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject("Invitation to join the Hybrid Work System");
        message.setText(
                "You have been invited as a " + role + ".\n\n" +
                        "Please go to the system and sign in with your email to complete your registration.\n" +
                        "An OTP will be sent to you during the sign-in process for verification.");
        sendEmailWithRetry(message, "invitation email to " + to + " as role " + role);
    }

    @Async("emailExecutor")
    public void sendForgotPasswordEmail(String to, String otp) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject("Password Reset Request");
        message.setText(
                "You requested a password reset.\n" +
                        "Your verification code is: " + otp + "\n" +
                        "This code expires in 10 minutes.");
        sendEmailWithRetry(message, "password reset OTP to " + to);
    }

    private void sendEmailWithRetry(SimpleMailMessage message, String description) {
        int maxRetries = 3;
        long delayMs = 1000;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                mailSender.send(message);
                log.info("Successfully sent {}", description);
                return;
            } catch (Exception e) {
                log.warn("Failed to send {} (attempt {}/{}): {}", description, attempt, maxRetries, e.getMessage());
                if (attempt == maxRetries) {
                    log.error("Failed to send {} after {} attempts", description, maxRetries, e);
                } else {
                    try {
                        Thread.sleep(delayMs * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.error("Email sending interrupted for {}", description, ie);
                        return;
                    }
                }
            }
        }
    }
}
