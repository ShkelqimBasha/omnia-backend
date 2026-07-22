package com.omnia.backend.service.impl;

import com.omnia.backend.service.interfaces.EmailVerificationService;
import com.omnia.backend.service.interfaces.PasswordResetService;
import com.omnia.backend.service.interfaces.RefreshTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenCleanupSchedulerTest {

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private EmailVerificationService
            emailVerificationService;

    @Mock
    private PasswordResetService passwordResetService;

    private TokenCleanupScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new TokenCleanupScheduler(
                refreshTokenService,
                emailVerificationService,
                passwordResetService
        );
    }

    @Test
    void cleanupExpiredTokens_WhenAllOperationsSucceed_ShouldRunAll() {

        when(
                refreshTokenService
                        .deleteExpiredOrRevokedTokens()
        ).thenReturn(3);

        when(
                emailVerificationService
                        .deleteExpiredOrUsedTokens()
        ).thenReturn(2);

        when(
                passwordResetService
                        .deleteExpiredOrUsedTokens()
        ).thenReturn(1);

        assertThatCode(
                scheduler::cleanupExpiredTokens
        ).doesNotThrowAnyException();

        verify(refreshTokenService)
                .deleteExpiredOrRevokedTokens();

        verify(emailVerificationService)
                .deleteExpiredOrUsedTokens();

        verify(passwordResetService)
                .deleteExpiredOrUsedTokens();
    }

    @Test
    void cleanupExpiredTokens_WhenRefreshCleanupFails_ShouldContinue() {

        when(
                refreshTokenService
                        .deleteExpiredOrRevokedTokens()
        ).thenThrow(
                new RuntimeException(
                        "Refresh cleanup failed"
                )
        );

        when(
                emailVerificationService
                        .deleteExpiredOrUsedTokens()
        ).thenReturn(2);

        when(
                passwordResetService
                        .deleteExpiredOrUsedTokens()
        ).thenReturn(1);

        assertThatCode(
                scheduler::cleanupExpiredTokens
        ).doesNotThrowAnyException();

        verify(refreshTokenService)
                .deleteExpiredOrRevokedTokens();

        verify(emailVerificationService)
                .deleteExpiredOrUsedTokens();

        verify(passwordResetService)
                .deleteExpiredOrUsedTokens();
    }

    @Test
    void cleanupExpiredTokens_WhenEmailCleanupFails_ShouldContinue() {

        when(
                refreshTokenService
                        .deleteExpiredOrRevokedTokens()
        ).thenReturn(3);

        when(
                emailVerificationService
                        .deleteExpiredOrUsedTokens()
        ).thenThrow(
                new RuntimeException(
                        "Email verification cleanup failed"
                )
        );

        when(
                passwordResetService
                        .deleteExpiredOrUsedTokens()
        ).thenReturn(1);

        assertThatCode(
                scheduler::cleanupExpiredTokens
        ).doesNotThrowAnyException();

        verify(refreshTokenService)
                .deleteExpiredOrRevokedTokens();

        verify(emailVerificationService)
                .deleteExpiredOrUsedTokens();

        verify(passwordResetService)
                .deleteExpiredOrUsedTokens();
    }

    @Test
    void cleanupExpiredTokens_WhenPasswordCleanupFails_ShouldNotThrow() {

        when(
                refreshTokenService
                        .deleteExpiredOrRevokedTokens()
        ).thenReturn(3);

        when(
                emailVerificationService
                        .deleteExpiredOrUsedTokens()
        ).thenReturn(2);

        when(
                passwordResetService
                        .deleteExpiredOrUsedTokens()
        ).thenThrow(
                new RuntimeException(
                        "Password reset cleanup failed"
                )
        );

        assertThatCode(
                scheduler::cleanupExpiredTokens
        ).doesNotThrowAnyException();

        verify(refreshTokenService)
                .deleteExpiredOrRevokedTokens();

        verify(emailVerificationService)
                .deleteExpiredOrUsedTokens();

        verify(passwordResetService)
                .deleteExpiredOrUsedTokens();
    }

    @Test
    void cleanupExpiredTokens_WhenAllOperationsFail_ShouldAttemptAll() {

        when(
                refreshTokenService
                        .deleteExpiredOrRevokedTokens()
        ).thenThrow(new RuntimeException("Refresh failure"));

        when(
                emailVerificationService
                        .deleteExpiredOrUsedTokens()
        ).thenThrow(new RuntimeException("Email failure"));

        when(
                passwordResetService
                        .deleteExpiredOrUsedTokens()
        ).thenThrow(new RuntimeException("Password failure"));

        assertThatCode(
                scheduler::cleanupExpiredTokens
        ).doesNotThrowAnyException();

        verify(refreshTokenService)
                .deleteExpiredOrRevokedTokens();

        verify(emailVerificationService)
                .deleteExpiredOrUsedTokens();

        verify(passwordResetService)
                .deleteExpiredOrUsedTokens();
    }
}