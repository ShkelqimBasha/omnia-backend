package com.omnia.backend.service.impl;

import com.omnia.backend.service.interfaces.EmailService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.frontend.base-url}")
    private String frontendBaseUrl;

    @Value("${spring.mail.username}")
    private String senderEmail;

    public EmailServiceImpl(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public void sendEmailVerification(
            String recipientEmail,
            String recipientName,
            String verificationToken
    ) {

        String verificationUrl =
                frontendBaseUrl
                        + "/verify-email?token="
                        + verificationToken;

        SimpleMailMessage message = new SimpleMailMessage();

        message.setFrom(senderEmail);
        message.setTo(recipientEmail);
        message.setSubject("Verify your Omnia account");

        message.setText(
                """
                Hello %s,

                Thank you for registering with Omnia.

                Please verify your email address by opening the link below:

                %s

                This verification link will expire soon.

                If you did not create this account, you can ignore this email.

                Omnia Team
                """.formatted(
                        recipientName,
                        verificationUrl
                )
        );

        mailSender.send(message);
    }
}