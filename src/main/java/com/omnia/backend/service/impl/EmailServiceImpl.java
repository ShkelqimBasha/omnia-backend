package com.omnia.backend.service.impl;

import com.omnia.backend.service.interfaces.EmailService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String senderEmail;

    @Value("${app.backend.base-url}")
    private String backendBaseUrl;

    @Value("${app.frontend.reset-password-url}")
    private String resetPasswordUrl;

    public EmailServiceImpl(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public void sendEmailVerification(
            String recipientEmail,
            String recipientName,
            String token
    ) {

        String verificationLink =
                backendBaseUrl
                        + "/api/auth/verify-email?token="
                        + token;

        String subject = "Verify your Omnia account";

        String body =
                "Hello " + recipientName + ",\n\n"
                        + "Thank you for registering with Omnia.\n\n"
                        + "Please verify your email address by opening the link below:\n\n"
                        + verificationLink
                        + "\n\nThis link expires in 24 hours.\n\n"
                        + "If you did not create this account, you can ignore this email.\n\n"
                        + "Omnia Team";

        sendSimpleEmail(
                recipientEmail,
                subject,
                body
        );
    }

    @Override
    public void sendPasswordResetEmail(
            String recipientEmail,
            String recipientName,
            String token
    ) {

        String passwordResetLink =
                resetPasswordUrl
                        + "?token="
                        + token;

        String subject = "Reset your Omnia password";

        String body =
                "Hello " + recipientName + ",\n\n"
                        + "We received a request to reset your Omnia password.\n\n"
                        + "Use the following link to continue:\n\n"
                        + passwordResetLink
                        + "\n\nYour password reset token is:\n"
                        + token
                        + "\n\nThis link expires in 30 minutes and can be used only once.\n\n"
                        + "If you did not request a password reset, ignore this email.\n\n"
                        + "Omnia Team";

        sendSimpleEmail(
                recipientEmail,
                subject,
                body
        );
    }

    private void sendSimpleEmail(
            String recipientEmail,
            String subject,
            String body
    ) {

        SimpleMailMessage message = new SimpleMailMessage();

        message.setFrom(senderEmail);
        message.setTo(recipientEmail);
        message.setSubject(subject);
        message.setText(body);

        mailSender.send(message);
    }
}