package com.example.hybridflow.service;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

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

    public void sendInvitationEmail(String to, String role) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject("Invitation to join the Hybrid Work System");
        message.setText(
                "You have been invited as a " + role + ".\n\n" +
                        "Please go to the system and sign in with your email to complete your registration.\n" +
                        "An OTP will be sent to you during the sign-in process for verification.");
        mailSender.send(message);
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
