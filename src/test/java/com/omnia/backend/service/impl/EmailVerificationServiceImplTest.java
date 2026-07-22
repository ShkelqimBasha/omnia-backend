package com.omnia.backend.service.impl;

import com.omnia.backend.common.exception.EmailAlreadyVerifiedException;
import com.omnia.backend.common.exception.EmailVerificationTokenExpiredException;
import com.omnia.backend.common.exception.InvalidEmailVerificationTokenException;
import com.omnia.backend.config.EmailVerificationProperties;
import com.omnia.backend.entity.EmailVerificationToken;
import com.omnia.backend.entity.User;
import com.omnia.backend.repository.EmailVerificationTokenRepository;
import com.omnia.backend.repository.UserRepository;
import com.omnia.backend.security.service.SecureTokenService;
import com.omnia.backend.service.interfaces.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
class EmailVerificationServiceImplTest {

    private static final Instant FIXED_INSTANT =
            Instant.parse("2026-07-21T18:00:00Z");

    private static final LocalDateTime FIXED_DATE_TIME =
            LocalDateTime.ofInstant(
                    FIXED_INSTANT,
                    ZoneOffset.UTC
            );

    private static final String RAW_TOKEN =
            "valid-verification-token";

    @Mock
    private EmailVerificationTokenRepository tokenRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmailService emailService;

    private EmailVerificationServiceImpl verificationService;

    private User user;
    private EmailVerificationToken activeToken;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(
                FIXED_INSTANT,
                ZoneOffset.UTC
        );

        EmailVerificationProperties properties =
                new EmailVerificationProperties(
                        Duration.ofHours(24)
                );

        SecureTokenService secureTokenService =
                new SecureTokenService();

        verificationService =
                new EmailVerificationServiceImpl(
                        tokenRepository,
                        userRepository,
                        emailService,
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
                .emailVerified(false)
                .build();

        activeToken =
                EmailVerificationToken.builder()
                        .id(10L)
                        .user(user)
                        .tokenHash(hashToken(RAW_TOKEN))
                        .expiresAt(
                                FIXED_DATE_TIME.plusHours(12)
                        )
                        .used(false)
                        .createdAt(FIXED_DATE_TIME)
                        .build();
    }

    @Test
    void createVerificationToken_ShouldStoreHashAndSendRawToken() {
        verificationService.createVerificationToken(user);

        verify(tokenRepository).deleteByUserId(1L);

        ArgumentCaptor<EmailVerificationToken> tokenCaptor =
                ArgumentCaptor.forClass(
                        EmailVerificationToken.class
                );

        verify(tokenRepository).save(
                tokenCaptor.capture()
        );

        ArgumentCaptor<String> rawTokenCaptor =
                ArgumentCaptor.forClass(String.class);

        verify(emailService).sendEmailVerification(
                eq("shkelqim@example.com"),
                eq("Shkelqim Basha"),
                rawTokenCaptor.capture()
        );

        EmailVerificationToken savedToken =
                tokenCaptor.getValue();

        String rawToken = rawTokenCaptor.getValue();

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
                FIXED_DATE_TIME.plusHours(24),
                savedToken.getExpiresAt()
        );
        assertFalse(savedToken.getUsed());
    }

    @Test
    void createVerificationToken_ShouldReplaceExistingToken() {
        verificationService.createVerificationToken(user);

        verify(tokenRepository)
                .deleteByUserId(user.getId());

        verify(tokenRepository)
                .save(any(EmailVerificationToken.class));
    }

    @Test
    void createVerificationToken_ShouldUseUsernameWhenNameMissing() {
        user.setFirstName(null);
        user.setLastName("   ");

        verificationService.createVerificationToken(user);

        verify(emailService).sendEmailVerification(
                eq("shkelqim@example.com"),
                eq("shkelqim"),
                any(String.class)
        );
    }

    @Test
    void createVerificationToken_ShouldTrimRecipientName() {
        user.setFirstName("  Shkelqim  ");
        user.setLastName("  Basha  ");

        verificationService.createVerificationToken(user);

        verify(emailService).sendEmailVerification(
                eq("shkelqim@example.com"),
                eq("Shkelqim Basha"),
                any(String.class)
        );
    }

    @Test
    void createVerificationToken_WithNullUser_ShouldThrow() {
        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> verificationService
                                .createVerificationToken(null)
                );

        assertEquals(
                "User must not be null and must have a valid ID",
                exception.getMessage()
        );

        verifyNoInteractions(
                tokenRepository,
                userRepository,
                emailService
        );
    }

    @Test
    void createVerificationToken_WithUserWithoutId_ShouldThrow() {
        user.setId(null);

        assertThrows(
                IllegalArgumentException.class,
                () -> verificationService
                        .createVerificationToken(user)
        );

        verifyNoInteractions(
                tokenRepository,
                userRepository,
                emailService
        );
    }

    @Test
    void createVerificationToken_WithBlankEmail_ShouldThrow() {
        user.setEmail("   ");

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> verificationService
                                .createVerificationToken(user)
                );

        assertEquals(
                "User email must not be blank",
                exception.getMessage()
        );

        verifyNoInteractions(
                tokenRepository,
                userRepository,
                emailService
        );
    }

    @Test
    void createVerificationToken_WithVerifiedUser_ShouldThrow() {
        user.setEmailVerified(true);

        EmailAlreadyVerifiedException exception =
                assertThrows(
                        EmailAlreadyVerifiedException.class,
                        () -> verificationService
                                .createVerificationToken(user)
                );

        assertEquals(
                "Email address is already verified",
                exception.getMessage()
        );

        verifyNoInteractions(
                tokenRepository,
                userRepository,
                emailService
        );
    }

    @Test
    void verifyEmail_WithValidToken_ShouldVerifyUserAndUseToken() {
        when(tokenRepository.findByTokenHash(
                hashToken(RAW_TOKEN)
        )).thenReturn(Optional.of(activeToken));

        verificationService.verifyEmail(RAW_TOKEN);

        assertTrue(user.getEmailVerified());
        assertTrue(activeToken.getUsed());

        verify(userRepository).save(user);
        verify(tokenRepository).save(activeToken);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   "})
    void verifyEmail_WithBlankToken_ShouldThrow(
            String invalidToken
    ) {
        InvalidEmailVerificationTokenException exception =
                assertThrows(
                        InvalidEmailVerificationTokenException.class,
                        () -> verificationService
                                .verifyEmail(invalidToken)
                );

        assertEquals(
                "Email verification token must not be blank",
                exception.getMessage()
        );

        verifyNoInteractions(
                tokenRepository,
                userRepository
        );
    }

    @Test
    void verifyEmail_WithOversizedToken_ShouldThrow() {
        String oversizedToken = "a".repeat(513);

        InvalidEmailVerificationTokenException exception =
                assertThrows(
                        InvalidEmailVerificationTokenException.class,
                        () -> verificationService
                                .verifyEmail(oversizedToken)
                );

        assertEquals(
                "Email verification token is too long",
                exception.getMessage()
        );

        verifyNoInteractions(
                tokenRepository,
                userRepository
        );
    }

    @Test
    void verifyEmail_WithUnknownToken_ShouldThrow() {
        when(tokenRepository.findByTokenHash(
                hashToken("invalid-token")
        )).thenReturn(Optional.empty());

        InvalidEmailVerificationTokenException exception =
                assertThrows(
                        InvalidEmailVerificationTokenException.class,
                        () -> verificationService
                                .verifyEmail("invalid-token")
                );

        assertEquals(
                "Invalid email verification token",
                exception.getMessage()
        );

        verifyNoInteractions(userRepository);
    }

    @Test
    void verifyEmail_WithUsedToken_ShouldThrow() {
        activeToken.setUsed(true);

        when(tokenRepository.findByTokenHash(
                hashToken(RAW_TOKEN)
        )).thenReturn(Optional.of(activeToken));

        InvalidEmailVerificationTokenException exception =
                assertThrows(
                        InvalidEmailVerificationTokenException.class,
                        () -> verificationService
                                .verifyEmail(RAW_TOKEN)
                );

        assertEquals(
                "Email verification token has already been used",
                exception.getMessage()
        );

        assertFalse(user.getEmailVerified());

        verifyNoInteractions(userRepository);
        verify(tokenRepository, never())
                .save(any(EmailVerificationToken.class));
    }

    @Test
    void verifyEmail_WithExpiredToken_ShouldThrow() {
        activeToken.setExpiresAt(
                FIXED_DATE_TIME.minusSeconds(1)
        );

        when(tokenRepository.findByTokenHash(
                hashToken(RAW_TOKEN)
        )).thenReturn(Optional.of(activeToken));

        EmailVerificationTokenExpiredException exception =
                assertThrows(
                        EmailVerificationTokenExpiredException.class,
                        () -> verificationService
                                .verifyEmail(RAW_TOKEN)
                );

        assertEquals(
                "Email verification token has expired",
                exception.getMessage()
        );

        verifyNoInteractions(userRepository);
        verify(tokenRepository, never())
                .save(any(EmailVerificationToken.class));
    }

    @Test
    void verifyEmail_WithTokenExpiringNow_ShouldThrow() {
        activeToken.setExpiresAt(FIXED_DATE_TIME);

        when(tokenRepository.findByTokenHash(
                hashToken(RAW_TOKEN)
        )).thenReturn(Optional.of(activeToken));

        assertThrows(
                EmailVerificationTokenExpiredException.class,
                () -> verificationService
                        .verifyEmail(RAW_TOKEN)
        );

        verifyNoInteractions(userRepository);
    }

    @Test
    void verifyEmail_WithAlreadyVerifiedUser_ShouldThrow() {
        user.setEmailVerified(true);

        when(tokenRepository.findByTokenHash(
                hashToken(RAW_TOKEN)
        )).thenReturn(Optional.of(activeToken));

        EmailAlreadyVerifiedException exception =
                assertThrows(
                        EmailAlreadyVerifiedException.class,
                        () -> verificationService
                                .verifyEmail(RAW_TOKEN)
                );

        assertEquals(
                "Email address is already verified",
                exception.getMessage()
        );

        assertFalse(activeToken.getUsed());

        verifyNoInteractions(userRepository);
        verify(tokenRepository, never())
                .save(any(EmailVerificationToken.class));
    }

    @Test
    void resendVerificationEmail_WithValidEmail_ShouldSendToken() {
        when(userRepository.findByEmail(
                "shkelqim@example.com"
        )).thenReturn(Optional.of(user));

        verificationService.resendVerificationEmail(
                "  SHKELQIM@EXAMPLE.COM  "
        );

        verify(userRepository)
                .findByEmail("shkelqim@example.com");

        verify(tokenRepository)
                .deleteByUserId(1L);

        verify(tokenRepository)
                .save(any(EmailVerificationToken.class));

        verify(emailService)
                .sendEmailVerification(
                        eq("shkelqim@example.com"),
                        eq("Shkelqim Basha"),
                        any(String.class)
                );
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   "})
    void resendVerificationEmail_WithBlankEmail_ShouldThrow(
            String invalidEmail
    ) {
        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> verificationService
                                .resendVerificationEmail(
                                        invalidEmail
                                )
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
    void resendVerificationEmail_WithUnknownEmail_ShouldReturnSilently() {
        when(userRepository.findByEmail(
                "missing@example.com"
        )).thenReturn(Optional.empty());

        assertDoesNotThrow(
                () -> verificationService
                        .resendVerificationEmail(
                                "missing@example.com"
                        )
        );

        verifyNoInteractions(
                tokenRepository,
                emailService
        );
    }

    @Test
    void resendVerificationEmail_WithVerifiedUser_ShouldReturnSilently() {
        user.setEmailVerified(true);

        when(userRepository.findByEmail(
                "shkelqim@example.com"
        )).thenReturn(Optional.of(user));

        assertDoesNotThrow(
                () -> verificationService
                        .resendVerificationEmail(
                                "shkelqim@example.com"
                        )
        );

        verifyNoInteractions(
                tokenRepository,
                emailService
        );
    }

    @Test
    void deleteExpiredOrUsedTokens_ShouldUseCurrentTime() {
        when(tokenRepository.deleteExpiredOrUsedTokens(
                FIXED_DATE_TIME
        )).thenReturn(5);

        int result =
                verificationService
                        .deleteExpiredOrUsedTokens();

        assertEquals(5, result);

        verify(tokenRepository)
                .deleteExpiredOrUsedTokens(
                        FIXED_DATE_TIME
                );
    }

    @Test
    void deleteExpiredOrUsedTokens_WhenNothingDeleted_ShouldReturnZero() {
        when(tokenRepository.deleteExpiredOrUsedTokens(
                FIXED_DATE_TIME
        )).thenReturn(0);

        int result =
                verificationService
                        .deleteExpiredOrUsedTokens();

        assertEquals(0, result);
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(
                    rawToken.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }
}