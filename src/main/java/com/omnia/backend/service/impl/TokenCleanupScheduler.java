package com.omnia.backend.service.impl;

import com.omnia.backend.service.interfaces.EmailVerificationService;
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

    public TokenCleanupScheduler(
            RefreshTokenService refreshTokenService,
            EmailVerificationService emailVerificationService
    ) {
        this.refreshTokenService = refreshTokenService;
        this.emailVerificationService = emailVerificationService;
    }

    @Scheduled(cron = "0 0 3 * * *")
    public void cleanupExpiredTokens() {

        int deletedRefreshTokens =
                refreshTokenService.deleteExpiredTokens();

        int deletedEmailTokens =
                emailVerificationService.deleteExpiredTokens();

        log.info(
                "Expired token cleanup completed: {} refresh tokens and {} email verification tokens deleted",
                deletedRefreshTokens,
                deletedEmailTokens
        );
    }
}