package com.omnia.backend.service.interfaces;

import com.omnia.backend.dto.request.ForgotPasswordRequest;
import com.omnia.backend.dto.request.ResetPasswordRequest;

public interface PasswordResetService {

    void requestPasswordReset(ForgotPasswordRequest request);

    void resetPassword(ResetPasswordRequest request);

    int deleteExpiredTokens();
}