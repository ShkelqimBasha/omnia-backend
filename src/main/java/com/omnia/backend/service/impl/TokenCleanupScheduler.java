package com.omnia.backend.service.impl;

import com.omnia.backend.service.interfaces.EmailVerificationService;
import com.omnia.backend.service.interfaces.PasswordResetService;
import com.omnia.backend.service.interfaces.RefreshTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.function.IntSupplier;

@Component
public class TokenCleanupScheduler {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(
                    TokenCleanupScheduler.class
            );

    private final RefreshTokenService refreshTokenService;
    private final EmailVerificationService emailVerificationService;
    private final PasswordResetService passwordResetService;

    public TokenCleanupScheduler(
            RefreshTokenService refreshTokenService,
            EmailVerificationService emailVerificationService,
            PasswordResetService passwordResetService
    ) {
        this.refreshTokenService = refreshTokenService;
        this.emailVerificationService =
                emailVerificationService;
        this.passwordResetService = passwordResetService;
    }

    @Scheduled(
            cron = "${app.scheduling.token-cleanup.cron:"
                    + "0 0 3 * * *}",
            zone = "${app.scheduling.token-cleanup.zone:UTC}"
    )
    public void cleanupExpiredTokens() {

        LOGGER.debug("Scheduled token cleanup started");

        CleanupResult refreshResult = executeCleanup(
                "refresh",
                refreshTokenService
                        ::deleteExpiredOrRevokedTokens
        );

        CleanupResult emailVerificationResult =
                executeCleanup(
                        "email-verification",
                        emailVerificationService
                                ::deleteExpiredOrUsedTokens
                );

        CleanupResult passwordResetResult =
                executeCleanup(
                        "password-reset",
                        passwordResetService
                                ::deleteExpiredOrUsedTokens
                );

        int failureCount =
                countFailures(
                        refreshResult,
                        emailVerificationResult,
                        passwordResetResult
                );

        if (failureCount == 0) {
            LOGGER.info(
                    "Scheduled token cleanup completed: "
                            + "refreshDeleted={}, "
                            + "emailVerificationDeleted={}, "
                            + "passwordResetDeleted={}",
                    refreshResult.deletedCount(),
                    emailVerificationResult.deletedCount(),
                    passwordResetResult.deletedCount()
            );
            return;
        }

        LOGGER.warn(
                "Scheduled token cleanup completed with failures: "
                        + "failedOperations={}, "
                        + "refreshDeleted={}, "
                        + "emailVerificationDeleted={}, "
                        + "passwordResetDeleted={}",
                failureCount,
                refreshResult.deletedCount(),
                emailVerificationResult.deletedCount(),
                passwordResetResult.deletedCount()
        );
    }

    private CleanupResult executeCleanup(
            String tokenType,
            IntSupplier cleanupOperation
    ) {
        try {
            int deletedCount =
                    cleanupOperation.getAsInt();

            return CleanupResult.success(deletedCount);
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "Scheduled token cleanup operation failed: "
                            + "tokenType={}",
                    tokenType,
                    exception
            );

            return CleanupResult.failure();
        }
    }

    private int countFailures(
            CleanupResult... results
    ) {
        int failureCount = 0;

        for (CleanupResult result : results) {
            if (!result.successful()) {
                failureCount++;
            }
        }

        return failureCount;
    }

    private record CleanupResult(
            boolean successful,
            int deletedCount
    ) {

        private static CleanupResult success(
                int deletedCount
        ) {
            return new CleanupResult(
                    true,
                    deletedCount
            );
        }

        private static CleanupResult failure() {
            return new CleanupResult(
                    false,
                    0
            );
        }
    }
}