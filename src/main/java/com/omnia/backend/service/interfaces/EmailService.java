package com.omnia.backend.service.interfaces;

public interface EmailService {

    void sendEmailVerification(
            String recipientEmail,
            String recipientName,
            String verificationToken
    );
}