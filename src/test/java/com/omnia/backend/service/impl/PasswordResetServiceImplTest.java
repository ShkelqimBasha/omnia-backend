package com.omnia.backend.service.impl;

import com.omnia.backend.common.exception.InvalidPasswordResetTokenException;
import com.omnia.backend.common.exception.PasswordResetTokenExpiredException;
import com.omnia.backend.common.exception.PasswordResetTokenUsedException;
import com.omnia.backend.config.PasswordResetProperties;
import com.omnia.backend.dto.request.ForgotPasswordRequest;
import com.omnia.backend.dto.request.ResetPasswordRequest;
import com.omnia.backend.entity.PasswordResetToken;
import com.omnia.backend.entity.User;
import com.omnia.backend.repository.PasswordResetTokenRepository;
import com.omnia.backend.repository.UserRepository;
import com.omnia.backend.security.service.SecureTokenService;
import com.omnia.backend.service.interfaces.EmailService;
import com.omnia.backend.service.interfaces.RefreshTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceImplTest {

    private static final Instant FIXED_INSTANT =
            Instant.parse("2026-07-22T10:00:00Z");

    private static final LocalDateTime FIXED_DATE_TIME =
            LocalDateTime.ofInstant(
                    FIXED_INSTANT,
                    ZoneOffset.UTC
            );

    private static final String RAW_TOKEN =
            "valid-reset-token";

    private static final String NEW_PASSWORD =
            "NewPassword@123";

    private static final String OLD_PASSWORD_HASH =
            "old-encoded-password";

    private static final String NEW_PASSWORD_HASH =
            "new-encoded-password";

    @Mock
    private PasswordResetTokenRepository tokenRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EmailService emailService;

    @Mock
    private RefreshTokenService refreshTokenService;

    private PasswordResetServiceImpl passwordResetService;

    private User user;
    private ForgotPasswordRequest forgotPasswordRequest;
    private ResetPasswordRequest resetPasswordRequest;
    private PasswordResetToken activeToken;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(
                FIXED_INSTANT,
                ZoneOffset.UTC
        );

        PasswordResetProperties properties =
                new PasswordResetProperties(
                        Duration.ofMinutes(30)
                );

        SecureTokenService secureTokenService =
                new SecureTokenService();

        passwordResetService =
                new PasswordResetServiceImpl(
                        tokenRepository,
                        userRepository,
                        passwordEncoder,
                        emailService,
                        refreshTokenService,
                        properties,
                        clock,
                        secureTokenService
                );

        user = User.builder()
                .id(1L)
                .firstName("Shkelqim")
                .lastName("Basha")
                .username("shkelqim")
                .email("shkelqim@example.com")
                .passwordHash(OLD_PASSWORD_HASH)
                .build();

        forgotPasswordRequest =
                ForgotPasswordRequest.builder()
                        .email(
                                " SHKELQIM@EXAMPLE.COM "
                        )
                        .build();

        resetPasswordRequest =
                ResetPasswordRequest.builder()
                        .token(RAW_TOKEN)
                        .newPassword(NEW_PASSWORD)
                        .confirmPassword(NEW_PASSWORD)
                        .build();

        activeToken =
                PasswordResetToken.builder()
                        .id(10L)
                        .user(user)
                        .tokenHash(
                                hashToken(RAW_TOKEN)
                        )
                        .createdAt(FIXED_DATE_TIME)
                        .expiresAt(
                                FIXED_DATE_TIME
                                        .plusMinutes(15)
                        )
                        .used(false)
                        .build();
    }

    @Test
    void requestPasswordReset_ShouldStoreHashAndSendRawToken() {
        when(userRepository.findByEmail(
                "shkelqim@example.com"
        )).thenReturn(Optional.of(user));

        passwordResetService.requestPasswordReset(
                forgotPasswordRequest
        );

        verify(tokenRepository)
                .deleteByUserId(1L);

        ArgumentCaptor<PasswordResetToken> tokenCaptor =
                ArgumentCaptor.forClass(
                        PasswordResetToken.class
                );

        verify(tokenRepository)
                .save(tokenCaptor.capture());

        ArgumentCaptor<String> rawTokenCaptor =
                ArgumentCaptor.forClass(String.class);

        verify(emailService)
                .sendPasswordResetEmail(
                        eq("shkelqim@example.com"),
                        eq("Shkelqim Basha"),
                        rawTokenCaptor.capture()
                );

        PasswordResetToken savedToken =
                tokenCaptor.getValue();

        String rawToken =
                rawTokenCaptor.getValue();

        assertNotNull(rawToken);
        assertFalse(rawToken.isBlank());
        assertEquals(43, rawToken.length());

        assertEquals(user, savedToken.getUser());

        assertEquals(
                hashToken(rawToken),
                savedToken.getTokenHash()
        );

        assertNotEquals(
                rawToken,
                savedToken.getTokenHash()
        );

        assertEquals(
                64,
                savedToken.getTokenHash().length()
        );

        assertEquals(
                FIXED_DATE_TIME,
                savedToken.getCreatedAt()
        );

        assertEquals(
                FIXED_DATE_TIME.plusMinutes(30),
                savedToken.getExpiresAt()
        );

        assertFalse(savedToken.getUsed());
    }

    @Test
    void requestPasswordReset_ShouldNormalizeEmail() {
        when(userRepository.findByEmail(
                "shkelqim@example.com"
        )).thenReturn(Optional.of(user));

        passwordResetService.requestPasswordReset(
                forgotPasswordRequest
        );

        verify(userRepository)
                .findByEmail("shkelqim@example.com");
    }

    @Test
    void requestPasswordReset_ShouldReplaceExistingToken() {
        when(userRepository.findByEmail(
                "shkelqim@example.com"
        )).thenReturn(Optional.of(user));

        passwordResetService.requestPasswordReset(
                forgotPasswordRequest
        );

        verify(tokenRepository)
                .deleteByUserId(user.getId());

        verify(tokenRepository)
                .save(any(PasswordResetToken.class));
    }

    @Test
    void requestPasswordReset_ShouldUseUsernameWhenNameMissing() {
        user.setFirstName(null);
        user.setLastName("   ");

        when(userRepository.findByEmail(
                "shkelqim@example.com"
        )).thenReturn(Optional.of(user));

        passwordResetService.requestPasswordReset(
                forgotPasswordRequest
        );

        verify(emailService)
                .sendPasswordResetEmail(
                        eq("shkelqim@example.com"),
                        eq("shkelqim"),
                        any(String.class)
                );
    }

    @Test
    void requestPasswordReset_ShouldTrimRecipientName() {
        user.setFirstName("  Shkelqim  ");
        user.setLastName("  Basha  ");

        when(userRepository.findByEmail(
                "shkelqim@example.com"
        )).thenReturn(Optional.of(user));

        passwordResetService.requestPasswordReset(
                forgotPasswordRequest
        );

        verify(emailService)
                .sendPasswordResetEmail(
                        eq("shkelqim@example.com"),
                        eq("Shkelqim Basha"),
                        any(String.class)
                );
    }

    @Test
    void requestPasswordReset_WithUnknownEmail_ShouldReturnSilently() {
        when(userRepository.findByEmail(
                "shkelqim@example.com"
        )).thenReturn(Optional.empty());

        assertDoesNotThrow(
                () -> passwordResetService
                        .requestPasswordReset(
                                forgotPasswordRequest
                        )
        );

        verifyNoInteractions(
                tokenRepository,
                emailService,
                refreshTokenService
        );
    }

    @Test
    void requestPasswordReset_WithNullRequest_ShouldThrow() {
        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> passwordResetService
                                .requestPasswordReset(null)
                );

        assertEquals(
                "Forgot password request must not be null",
                exception.getMessage()
        );

        verifyNoInteractions(
                userRepository,
                tokenRepository,
                emailService
        );
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   "})
    void requestPasswordReset_WithBlankEmail_ShouldThrow(
            String invalidEmail
    ) {
        ForgotPasswordRequest request =
                ForgotPasswordRequest.builder()
                        .email(invalidEmail)
                        .build();

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> passwordResetService
                                .requestPasswordReset(request)
                );

        assertEquals(
                "Email must not be blank",
                exception.getMessage()
        );

        verifyNoInteractions(
                userRepository,
                tokenRepository,
                emailService
        );
    }

    @Test
    void resetPassword_WithValidToken_ShouldResetPassword() {
        when(tokenRepository.findByTokenHash(
                hashToken(RAW_TOKEN)
        )).thenReturn(Optional.of(activeToken));

        when(passwordEncoder.matches(
                NEW_PASSWORD,
                OLD_PASSWORD_HASH
        )).thenReturn(false);

        when(passwordEncoder.encode(
                NEW_PASSWORD
        )).thenReturn(NEW_PASSWORD_HASH);

        passwordResetService.resetPassword(
                resetPasswordRequest
        );

        assertEquals(
                NEW_PASSWORD_HASH,
                user.getPasswordHash()
        );

        assertTrue(activeToken.getUsed());

        verify(userRepository).save(user);
        verify(tokenRepository).save(activeToken);

        verify(refreshTokenService)
                .revokeAllUserTokens(1L);
    }

    @Test
    void resetPassword_ShouldSearchOnlyByTokenHash() {
        when(tokenRepository.findByTokenHash(
                hashToken(RAW_TOKEN)
        )).thenReturn(Optional.of(activeToken));

        when(passwordEncoder.matches(
                NEW_PASSWORD,
                OLD_PASSWORD_HASH
        )).thenReturn(false);

        when(passwordEncoder.encode(
                NEW_PASSWORD
        )).thenReturn(NEW_PASSWORD_HASH);

        passwordResetService.resetPassword(
                resetPasswordRequest
        );

        verify(tokenRepository)
                .findByTokenHash(
                        hashToken(RAW_TOKEN)
                );

        assertNotEquals(
                RAW_TOKEN,
                activeToken.getTokenHash()
        );
    }

    @Test
    void resetPassword_ShouldEncodeNewPassword() {
        when(tokenRepository.findByTokenHash(
                hashToken(RAW_TOKEN)
        )).thenReturn(Optional.of(activeToken));

        when(passwordEncoder.matches(
                NEW_PASSWORD,
                OLD_PASSWORD_HASH
        )).thenReturn(false);

        when(passwordEncoder.encode(
                NEW_PASSWORD
        )).thenReturn(NEW_PASSWORD_HASH);

        passwordResetService.resetPassword(
                resetPasswordRequest
        );

        verify(passwordEncoder)
                .encode(NEW_PASSWORD);

        assertEquals(
                NEW_PASSWORD_HASH,
                user.getPasswordHash()
        );
    }

    @Test
    void resetPassword_ShouldMarkTokenAsUsed() {
        when(tokenRepository.findByTokenHash(
                hashToken(RAW_TOKEN)
        )).thenReturn(Optional.of(activeToken));

        when(passwordEncoder.matches(
                NEW_PASSWORD,
                OLD_PASSWORD_HASH
        )).thenReturn(false);

        when(passwordEncoder.encode(
                NEW_PASSWORD
        )).thenReturn(NEW_PASSWORD_HASH);

        passwordResetService.resetPassword(
                resetPasswordRequest
        );

        assertTrue(activeToken.getUsed());

        verify(tokenRepository)
                .save(activeToken);
    }

    @Test
    void resetPassword_ShouldRevokeAllRefreshTokens() {
        when(tokenRepository.findByTokenHash(
                hashToken(RAW_TOKEN)
        )).thenReturn(Optional.of(activeToken));

        when(passwordEncoder.matches(
                NEW_PASSWORD,
                OLD_PASSWORD_HASH
        )).thenReturn(false);

        when(passwordEncoder.encode(
                NEW_PASSWORD
        )).thenReturn(NEW_PASSWORD_HASH);

        passwordResetService.resetPassword(
                resetPasswordRequest
        );

        verify(refreshTokenService)
                .revokeAllUserTokens(
                        user.getId()
                );
    }

    @Test
    void resetPassword_WithNullRequest_ShouldThrow() {
        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> passwordResetService
                                .resetPassword(null)
                );

        assertEquals(
                "Reset password request must not be null",
                exception.getMessage()
        );

        verifyNoInteractions(
                tokenRepository,
                userRepository,
                passwordEncoder,
                refreshTokenService
        );
    }

    @Test
    void resetPassword_WithDifferentPasswords_ShouldThrow() {
        resetPasswordRequest.setConfirmPassword(
                "DifferentPassword@123"
        );

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> passwordResetService
                                .resetPassword(
                                        resetPasswordRequest
                                )
                );

        assertEquals(
                "Passwords do not match",
                exception.getMessage()
        );

        verifyNoInteractions(
                tokenRepository,
                userRepository,
                passwordEncoder,
                refreshTokenService
        );
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   "})
    void resetPassword_WithBlankToken_ShouldThrow(
            String invalidToken
    ) {
        resetPasswordRequest.setToken(invalidToken);

        InvalidPasswordResetTokenException exception =
                assertThrows(
                        InvalidPasswordResetTokenException.class,
                        () -> passwordResetService
                                .resetPassword(
                                        resetPasswordRequest
                                )
                );

        assertEquals(
                "Password reset token must not be blank",
                exception.getMessage()
        );

        verifyNoInteractions(tokenRepository);
    }

    @Test
    void resetPassword_WithOversizedToken_ShouldThrow() {
        resetPasswordRequest.setToken(
                "a".repeat(513)
        );

        InvalidPasswordResetTokenException exception =
                assertThrows(
                        InvalidPasswordResetTokenException.class,
                        () -> passwordResetService
                                .resetPassword(
                                        resetPasswordRequest
                                )
                );

        assertEquals(
                "Password reset token is too long",
                exception.getMessage()
        );

        verifyNoInteractions(tokenRepository);
    }

    @Test
    void resetPassword_WithUnknownToken_ShouldThrow() {
        when(tokenRepository.findByTokenHash(
                hashToken(RAW_TOKEN)
        )).thenReturn(Optional.empty());

        InvalidPasswordResetTokenException exception =
                assertThrows(
                        InvalidPasswordResetTokenException.class,
                        () -> passwordResetService
                                .resetPassword(
                                        resetPasswordRequest
                                )
                );

        assertEquals(
                "Invalid password reset token",
                exception.getMessage()
        );

        verifyNoInteractions(
                userRepository,
                passwordEncoder,
                refreshTokenService
        );
    }

    @Test
    void resetPassword_WithUsedToken_ShouldThrow() {
        activeToken.setUsed(true);

        when(tokenRepository.findByTokenHash(
                hashToken(RAW_TOKEN)
        )).thenReturn(Optional.of(activeToken));

        PasswordResetTokenUsedException exception =
                assertThrows(
                        PasswordResetTokenUsedException.class,
                        () -> passwordResetService
                                .resetPassword(
                                        resetPasswordRequest
                                )
                );

        assertEquals(
                "Password reset token has already been used",
                exception.getMessage()
        );

        verifyNoInteractions(
                userRepository,
                passwordEncoder,
                refreshTokenService
        );

        verify(tokenRepository, never())
                .save(any(PasswordResetToken.class));
    }

    @Test
    void resetPassword_WithExpiredToken_ShouldThrow() {
        activeToken.setExpiresAt(
                FIXED_DATE_TIME.minusSeconds(1)
        );

        when(tokenRepository.findByTokenHash(
                hashToken(RAW_TOKEN)
        )).thenReturn(Optional.of(activeToken));

        PasswordResetTokenExpiredException exception =
                assertThrows(
                        PasswordResetTokenExpiredException.class,
                        () -> passwordResetService
                                .resetPassword(
                                        resetPasswordRequest
                                )
                );

        assertEquals(
                "Password reset token has expired",
                exception.getMessage()
        );

        verifyNoInteractions(
                userRepository,
                passwordEncoder,
                refreshTokenService
        );

        verify(tokenRepository, never())
                .save(any(PasswordResetToken.class));
    }

    @Test
    void resetPassword_WithTokenExpiringNow_ShouldThrow() {
        activeToken.setExpiresAt(FIXED_DATE_TIME);

        when(tokenRepository.findByTokenHash(
                hashToken(RAW_TOKEN)
        )).thenReturn(Optional.of(activeToken));

        assertThrows(
                PasswordResetTokenExpiredException.class,
                () -> passwordResetService
                        .resetPassword(
                                resetPasswordRequest
                        )
        );

        verifyNoInteractions(
                userRepository,
                passwordEncoder,
                refreshTokenService
        );
    }

    @Test
    void resetPassword_WithCurrentPassword_ShouldThrow() {
        when(tokenRepository.findByTokenHash(
                hashToken(RAW_TOKEN)
        )).thenReturn(Optional.of(activeToken));

        when(passwordEncoder.matches(
                NEW_PASSWORD,
                OLD_PASSWORD_HASH
        )).thenReturn(true);

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> passwordResetService
                                .resetPassword(
                                        resetPasswordRequest
                                )
                );

        assertEquals(
                "New password must be different from the current password",
                exception.getMessage()
        );

        assertFalse(activeToken.getUsed());

        verify(passwordEncoder, never())
                .encode(any(String.class));

        verifyNoInteractions(
                userRepository,
                refreshTokenService
        );

        verify(tokenRepository, never())
                .save(any(PasswordResetToken.class));
    }

    @Test
    void deleteExpiredOrUsedTokens_ShouldReturnDeletedCount() {
        when(tokenRepository
                .deleteExpiredOrUsedTokens(
                        FIXED_DATE_TIME
                ))
                .thenReturn(4);

        int result =
                passwordResetService
                        .deleteExpiredOrUsedTokens();

        assertEquals(4, result);

        verify(tokenRepository)
                .deleteExpiredOrUsedTokens(
                        FIXED_DATE_TIME
                );
    }

    @Test
    void deleteExpiredOrUsedTokens_WhenNothingDeleted_ShouldReturnZero() {
        when(tokenRepository
                .deleteExpiredOrUsedTokens(
                        FIXED_DATE_TIME
                ))
                .thenReturn(0);

        int result =
                passwordResetService
                        .deleteExpiredOrUsedTokens();

        assertEquals(0, result);
    }

    private String hashToken(
            String rawToken
    ) {
        try {
            MessageDigest digest =
                    MessageDigest.getInstance(
                            "SHA-256"
                    );

            byte[] hash =
                    digest.digest(
                            rawToken.getBytes(
                                    StandardCharsets.UTF_8
                            )
                    );

            return HexFormat.of()
                    .formatHex(hash);

        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }
}