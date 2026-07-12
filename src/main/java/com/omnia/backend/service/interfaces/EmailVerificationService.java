package com.omnia.backend.service.interfaces;

import com.omnia.backend.entity.User;

public interface EmailVerificationService {

    void createVerificationToken(User user);

    void verifyEmail(String token);

    void resendVerificationEmail(String email);

    int deleteExpiredTokens();
}