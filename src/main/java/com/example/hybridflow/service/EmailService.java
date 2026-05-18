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

    public void sendOtpEmail(String to, String otp) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject("Verify your account");
        message.setText(
                "Your verification code is: " + otp +
                        "\nThis code expires in 10 minutes.");
        mailSender.send(message);
    }

    @Async
    public void sendInvitationEmail(String to, String role) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject("Invitation to join the Hybrid Work System");
            message.setText(
                    "You have been invited as a " + role + ".\n\n" +
                            "Please go to the system and sign in with your email to complete your registration.\n" +
                            "An OTP will be sent to you during the sign-in process for verification.");
            mailSender.send(message);
            log.info("Successfully sent invitation email to {} as role {}", to, role);
        } catch (Exception e) {
            log.error("Failed to send invitation email to {} as role {}: {}", to, role, e.getMessage(), e);
        }
    }

    public void sendForgotPasswordEmail(String to, String otp) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject("Password Reset Request");
        message.setText(
                "You requested a password reset.\n" +
                        "Your verification code is: " + otp + "\n" +
                        "This code expires in 10 minutes.");
        mailSender.send(message);
    }
}
