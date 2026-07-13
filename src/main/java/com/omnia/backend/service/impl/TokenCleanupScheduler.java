package com.omnia.backend.service.impl;

import com.omnia.backend.service.interfaces.EmailVerificationService;
import com.omnia.backend.service.interfaces.PasswordResetService;
import com.omnia.backend.service.interfaces.RefreshTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TokenCleanupScheduler {

    private static final Logger log =
            LoggerFactory.getLogger(TokenCleanupScheduler.class);

    private final RefreshTokenService refreshTokenService;
    private final EmailVerificationService emailVerificationService;
    private final PasswordResetService passwordResetService;

    public TokenCleanupScheduler(
            RefreshTokenService refreshTokenService,
            EmailVerificationService emailVerificationService,
            PasswordResetService passwordResetService
    ) {
        this.refreshTokenService = refreshTokenService;
        this.emailVerificationService = emailVerificationService;
        this.passwordResetService = passwordResetService;
    }

    @Scheduled(cron = "0 0 3 * * *")
    public void cleanupExpiredTokens() {

        int deletedRefreshTokens =
                refreshTokenService.deleteExpiredTokens();

        int deletedEmailVerificationTokens =
                emailVerificationService.deleteExpiredTokens();

        int deletedPasswordResetTokens =
                passwordResetService.deleteExpiredTokens();

        log.info(
                "Expired token cleanup completed: {} refresh tokens, "
                        + "{} email verification tokens and "
                        + "{} password reset tokens deleted",
                deletedRefreshTokens,
                deletedEmailVerificationTokens,
                deletedPasswordResetTokens
        );
    }
}