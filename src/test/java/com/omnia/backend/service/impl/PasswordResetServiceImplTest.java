package com.omnia.backend.service.impl;

import com.omnia.backend.common.exception.InvalidPasswordResetTokenException;
import com.omnia.backend.common.exception.PasswordResetTokenExpiredException;
import com.omnia.backend.common.exception.PasswordResetTokenUsedException;
import com.omnia.backend.dto.request.ForgotPasswordRequest;
import com.omnia.backend.dto.request.ResetPasswordRequest;
import com.omnia.backend.entity.PasswordResetToken;
import com.omnia.backend.entity.User;
import com.omnia.backend.repository.PasswordResetTokenRepository;
import com.omnia.backend.repository.UserRepository;
import com.omnia.backend.service.interfaces.EmailService;
import com.omnia.backend.service.interfaces.RefreshTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceImplTest {

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

    @InjectMocks
    private PasswordResetServiceImpl passwordResetService;

    private User user;
    private ForgotPasswordRequest forgotPasswordRequest;
    private ResetPasswordRequest resetPasswordRequest;
    private PasswordResetToken activeToken;

    @BeforeEach
    void setUp() {

        user = User.builder()
                .id(1L)
                .firstName("Shkelqim")
                .lastName("Basha")
                .username("shkelqim")
                .email("shkelqim@example.com")
                .passwordHash("old-encoded-password")
                .build();

        forgotPasswordRequest = ForgotPasswordRequest.builder()
                .email(" SHKELQIM@EXAMPLE.COM ")
                .build();

        resetPasswordRequest = ResetPasswordRequest.builder()
                .token("valid-reset-token")
                .newPassword("NewPassword@123")
                .confirmPassword("NewPassword@123")
                .build();

        activeToken = PasswordResetToken.builder()
                .id(10L)
                .user(user)
                .token("valid-reset-token")
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(20))
                .used(false)
                .build();
    }

    @Test
    void requestPasswordReset_shouldCreateTokenAndSendEmail() {

        when(userRepository.findByEmail("shkelqim@example.com"))
                .thenReturn(Optional.of(user));

        when(tokenRepository.save(any(PasswordResetToken.class)))
                .thenAnswer(invocation -> {
                    PasswordResetToken token = invocation.getArgument(0);
                    token.setId(10L);
                    return token;
                });

        passwordResetService.requestPasswordReset(
                forgotPasswordRequest
        );

        verify(userRepository)
                .findByEmail("shkelqim@example.com");

        verify(tokenRepository)
                .deleteByUserId(1L);

        ArgumentCaptor<PasswordResetToken> tokenCaptor =
                ArgumentCaptor.forClass(
                        PasswordResetToken.class
                );

        verify(tokenRepository)
                .save(tokenCaptor.capture());

        PasswordResetToken savedToken =
                tokenCaptor.getValue();

        assertEquals(user, savedToken.getUser());
        assertNotNull(savedToken.getToken());
        assertFalse(savedToken.getToken().isBlank());
        assertFalse(savedToken.getUsed());
        assertNotNull(savedToken.getCreatedAt());
        assertNotNull(savedToken.getExpiresAt());

        assertTrue(
                savedToken.getExpiresAt().isAfter(
                        savedToken.getCreatedAt()
                                .plusMinutes(29)
                )
        );

        verify(emailService)
                .sendPasswordResetEmail(
                        "shkelqim@example.com",
                        "Shkelqim Basha",
                        savedToken.getToken()
                );
    }

    @Test
    void requestPasswordReset_shouldNormalizeEmail() {

        when(userRepository.findByEmail("shkelqim@example.com"))
                .thenReturn(Optional.of(user));

        when(tokenRepository.save(any(PasswordResetToken.class)))
                .thenAnswer(invocation ->
                        invocation.getArgument(0)
                );

        passwordResetService.requestPasswordReset(
                forgotPasswordRequest
        );

        verify(userRepository)
                .findByEmail("shkelqim@example.com");
    }

    @Test
    void requestPasswordReset_shouldReturnSilentlyWhenUserDoesNotExist() {

        when(userRepository.findByEmail("missing@example.com"))
                .thenReturn(Optional.empty());

        ForgotPasswordRequest request =
                ForgotPasswordRequest.builder()
                        .email("missing@example.com")
                        .build();

        assertDoesNotThrow(
                () -> passwordResetService
                        .requestPasswordReset(request)
        );

        verifyNoInteractions(
                tokenRepository,
                passwordEncoder,
                emailService,
                refreshTokenService
        );
    }

    @Test
    void requestPasswordReset_shouldUseUsernameWhenNameIsMissing() {

        user.setFirstName(null);
        user.setLastName("   ");

        when(userRepository.findByEmail("shkelqim@example.com"))
                .thenReturn(Optional.of(user));

        when(tokenRepository.save(any(PasswordResetToken.class)))
                .thenAnswer(invocation ->
                        invocation.getArgument(0)
                );

        passwordResetService.requestPasswordReset(
                forgotPasswordRequest
        );

        ArgumentCaptor<PasswordResetToken> tokenCaptor =
                ArgumentCaptor.forClass(
                        PasswordResetToken.class
                );

        verify(tokenRepository)
                .save(tokenCaptor.capture());

        verify(emailService)
                .sendPasswordResetEmail(
                        "shkelqim@example.com",
                        "shkelqim",
                        tokenCaptor.getValue().getToken()
                );
    }

    @Test
    void requestPasswordReset_shouldTrimRecipientName() {

        user.setFirstName("  Shkelqim ");
        user.setLastName(" Basha  ");

        when(userRepository.findByEmail("shkelqim@example.com"))
                .thenReturn(Optional.of(user));

        when(tokenRepository.save(any(PasswordResetToken.class)))
                .thenAnswer(invocation ->
                        invocation.getArgument(0)
                );

        passwordResetService.requestPasswordReset(
                forgotPasswordRequest
        );

        ArgumentCaptor<PasswordResetToken> tokenCaptor =
                ArgumentCaptor.forClass(
                        PasswordResetToken.class
                );

        verify(tokenRepository)
                .save(tokenCaptor.capture());

        verify(emailService)
                .sendPasswordResetEmail(
                        "shkelqim@example.com",
                        "Shkelqim Basha",
                        tokenCaptor.getValue().getToken()
                );
    }

    @Test
    void resetPassword_shouldResetPasswordSuccessfully() {

        when(tokenRepository.findByToken("valid-reset-token"))
                .thenReturn(Optional.of(activeToken));

        when(passwordEncoder.encode("NewPassword@123"))
                .thenReturn("new-encoded-password");

        when(userRepository.save(user))
                .thenReturn(user);

        when(tokenRepository.save(activeToken))
                .thenReturn(activeToken);

        passwordResetService.resetPassword(
                resetPasswordRequest
        );

        assertEquals(
                "new-encoded-password",
                user.getPasswordHash()
        );

        assertTrue(activeToken.getUsed());

        verify(passwordEncoder)
                .encode("NewPassword@123");

        verify(userRepository).save(user);
        verify(tokenRepository).save(activeToken);

        verify(refreshTokenService)
                .revokeAllUserTokens(1L);
    }

    @Test
    void resetPassword_shouldThrowWhenPasswordsDoNotMatch() {

        resetPasswordRequest.setConfirmPassword(
                "DifferentPassword@123"
        );

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> passwordResetService.resetPassword(
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
                emailService,
                refreshTokenService
        );
    }

    @Test
    void resetPassword_shouldThrowWhenTokenDoesNotExist() {

        when(tokenRepository.findByToken("invalid-token"))
                .thenReturn(Optional.empty());

        resetPasswordRequest.setToken("invalid-token");

        InvalidPasswordResetTokenException exception =
                assertThrows(
                        InvalidPasswordResetTokenException.class,
                        () -> passwordResetService.resetPassword(
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
    void resetPassword_shouldThrowWhenTokenWasAlreadyUsed() {

        activeToken.setUsed(true);

        when(tokenRepository.findByToken("valid-reset-token"))
                .thenReturn(Optional.of(activeToken));

        PasswordResetTokenUsedException exception =
                assertThrows(
                        PasswordResetTokenUsedException.class,
                        () -> passwordResetService.resetPassword(
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
    void resetPassword_shouldThrowWhenTokenIsExpired() {

        activeToken.setExpiresAt(
                LocalDateTime.now().minusMinutes(1)
        );

        when(tokenRepository.findByToken("valid-reset-token"))
                .thenReturn(Optional.of(activeToken));

        PasswordResetTokenExpiredException exception =
                assertThrows(
                        PasswordResetTokenExpiredException.class,
                        () -> passwordResetService.resetPassword(
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
    void resetPassword_shouldRevokeAllActiveRefreshTokens() {

        when(tokenRepository.findByToken("valid-reset-token"))
                .thenReturn(Optional.of(activeToken));

        when(passwordEncoder.encode("NewPassword@123"))
                .thenReturn("new-encoded-password");

        passwordResetService.resetPassword(
                resetPasswordRequest
        );

        verify(refreshTokenService)
                .revokeAllUserTokens(1L);
    }

    @Test
    void resetPassword_shouldMarkTokenAsUsed() {

        when(tokenRepository.findByToken("valid-reset-token"))
                .thenReturn(Optional.of(activeToken));

        when(passwordEncoder.encode("NewPassword@123"))
                .thenReturn("new-encoded-password");

        passwordResetService.resetPassword(
                resetPasswordRequest
        );

        assertTrue(activeToken.getUsed());

        verify(tokenRepository)
                .save(activeToken);
    }

    @Test
    void resetPassword_shouldEncodeNewPassword() {

        when(tokenRepository.findByToken("valid-reset-token"))
                .thenReturn(Optional.of(activeToken));

        when(passwordEncoder.encode("NewPassword@123"))
                .thenReturn("new-encoded-password");

        passwordResetService.resetPassword(
                resetPasswordRequest
        );

        verify(passwordEncoder)
                .encode("NewPassword@123");

        assertEquals(
                "new-encoded-password",
                user.getPasswordHash()
        );
    }

    @Test
    void deleteExpiredTokens_shouldReturnDeletedCount() {

        when(tokenRepository.deleteExpiredTokens(
                any(LocalDateTime.class)
        )).thenReturn(4);

        int result =
                passwordResetService.deleteExpiredTokens();

        assertEquals(4, result);

        ArgumentCaptor<LocalDateTime> timeCaptor =
                ArgumentCaptor.forClass(LocalDateTime.class);

        verify(tokenRepository)
                .deleteExpiredTokens(timeCaptor.capture());

        assertNotNull(timeCaptor.getValue());

        assertTrue(
                timeCaptor.getValue().isBefore(
                        LocalDateTime.now().plusSeconds(1)
                )
        );
    }

    @Test
    void deleteExpiredTokens_shouldReturnZeroWhenNothingDeleted() {

        when(tokenRepository.deleteExpiredTokens(
                any(LocalDateTime.class)
        )).thenReturn(0);

        int result =
                passwordResetService.deleteExpiredTokens();

        assertEquals(0, result);
    }
}