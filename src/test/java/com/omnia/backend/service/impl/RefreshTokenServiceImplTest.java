package com.omnia.backend.service.impl;

import com.omnia.backend.security.service.SecureTokenService;
import com.omnia.backend.common.exception.InvalidRefreshTokenException;
import com.omnia.backend.common.exception.RefreshTokenExpiredException;
import com.omnia.backend.common.exception.RefreshTokenRevokedException;
import com.omnia.backend.config.RefreshTokenProperties;
import com.omnia.backend.entity.RefreshToken;
import com.omnia.backend.entity.User;
import com.omnia.backend.repository.RefreshTokenRepository;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceImplTest {

    private static final Instant FIXED_INSTANT =
            Instant.parse("2026-07-19T18:00:00Z");

    private static final LocalDateTime FIXED_DATE_TIME =
            LocalDateTime.ofInstant(
                    FIXED_INSTANT,
                    ZoneOffset.UTC
            );

    private static final String RAW_TOKEN =
            "valid-refresh-token";

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private RefreshTokenServiceImpl refreshTokenService;

    private User user;
    private RefreshToken activeToken;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(
                FIXED_INSTANT,
                ZoneOffset.UTC
        );

        RefreshTokenProperties properties =
                new RefreshTokenProperties(
                        Duration.ofDays(30)
                );

        SecureTokenService secureTokenService =
                new SecureTokenService();

        refreshTokenService =
                new RefreshTokenServiceImpl(
                        refreshTokenRepository,
                        properties,
                        clock,
                        secureTokenService
                );

        user = User.builder()
                .id(1L)
                .username("shkelqim")
                .email("shkelqim@example.com")
                .build();

        activeToken = RefreshToken.builder()
                .id(10L)
                .user(user)
                .tokenHash(hashToken(RAW_TOKEN))
                .expiresAt(
                        FIXED_DATE_TIME.plusDays(10)
                )
                .revoked(false)
                .build();
    }

    @Test
    void createRefreshToken_ShouldReturnRawTokenAndStoreOnlyHash() {

        String rawToken =
                refreshTokenService.createRefreshToken(user);

        assertNotNull(rawToken);
        assertFalse(rawToken.isBlank());
        assertEquals(43, rawToken.length());

        ArgumentCaptor<RefreshToken> captor =
                ArgumentCaptor.forClass(RefreshToken.class);

        verify(refreshTokenRepository)
                .save(captor.capture());

        RefreshToken savedToken = captor.getValue();

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
                FIXED_DATE_TIME.plusDays(30),
                savedToken.getExpiresAt()
        );
        assertFalse(savedToken.getRevoked());

        verify(refreshTokenRepository)
                .revokeAllActiveTokensByUserId(1L);
    }

    @Test
    void createRefreshToken_ShouldGenerateDifferentTokens() {

        String firstToken =
                refreshTokenService.createRefreshToken(user);

        String secondToken =
                refreshTokenService.createRefreshToken(user);

        assertNotEquals(firstToken, secondToken);

        verify(refreshTokenRepository, times(2))
                .revokeAllActiveTokensByUserId(1L);

        verify(refreshTokenRepository, times(2))
                .save(any(RefreshToken.class));
    }

    @Test
    void createRefreshToken_WithNullUser_ShouldThrow() {

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> refreshTokenService
                                .createRefreshToken(null)
                );

        assertEquals(
                "User must not be null and must have a valid ID",
                exception.getMessage()
        );

        verifyNoInteractions(refreshTokenRepository);
    }

    @Test
    void createRefreshToken_WithUserWithoutId_ShouldThrow() {

        User userWithoutId = User.builder()
                .username("user")
                .email("user@example.com")
                .build();

        assertThrows(
                IllegalArgumentException.class,
                () -> refreshTokenService
                        .createRefreshToken(userWithoutId)
        );

        verifyNoInteractions(refreshTokenRepository);
    }

    @Test
    void verifyRefreshToken_WithValidToken_ShouldReturnToken() {

        when(refreshTokenRepository.findByTokenHash(
                hashToken(RAW_TOKEN)
        )).thenReturn(Optional.of(activeToken));

        RefreshToken result =
                refreshTokenService.verifyRefreshToken(
                        RAW_TOKEN
                );

        assertSame(activeToken, result);

        verify(refreshTokenRepository)
                .findByTokenHash(hashToken(RAW_TOKEN));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   "})
    void verifyRefreshToken_WithBlankToken_ShouldThrow(
            String invalidToken
    ) {
        InvalidRefreshTokenException exception =
                assertThrows(
                        InvalidRefreshTokenException.class,
                        () -> refreshTokenService
                                .verifyRefreshToken(invalidToken)
                );

        assertEquals(
                "Refresh token must not be blank",
                exception.getMessage()
        );

        verifyNoInteractions(refreshTokenRepository);
    }

    @Test
    void verifyRefreshToken_WithOversizedToken_ShouldThrow() {

        String oversizedToken = "a".repeat(513);

        InvalidRefreshTokenException exception =
                assertThrows(
                        InvalidRefreshTokenException.class,
                        () -> refreshTokenService
                                .verifyRefreshToken(
                                        oversizedToken
                                )
                );

        assertEquals(
                "Refresh token is too long",
                exception.getMessage()
        );

        verifyNoInteractions(refreshTokenRepository);
    }

    @Test
    void verifyRefreshToken_WithUnknownToken_ShouldThrow() {

        when(refreshTokenRepository.findByTokenHash(
                hashToken("unknown-token")
        )).thenReturn(Optional.empty());

        InvalidRefreshTokenException exception =
                assertThrows(
                        InvalidRefreshTokenException.class,
                        () -> refreshTokenService
                                .verifyRefreshToken(
                                        "unknown-token"
                                )
                );

        assertEquals(
                "Invalid refresh token",
                exception.getMessage()
        );
    }

    @Test
    void verifyRefreshToken_WithRevokedToken_ShouldThrow() {

        activeToken.setRevoked(true);

        when(refreshTokenRepository.findByTokenHash(
                hashToken(RAW_TOKEN)
        )).thenReturn(Optional.of(activeToken));

        RefreshTokenRevokedException exception =
                assertThrows(
                        RefreshTokenRevokedException.class,
                        () -> refreshTokenService
                                .verifyRefreshToken(RAW_TOKEN)
                );

        assertEquals(
                "Refresh token has been revoked",
                exception.getMessage()
        );
    }

    @Test
    void verifyRefreshToken_WithExpiredToken_ShouldThrow() {

        activeToken.setExpiresAt(
                FIXED_DATE_TIME.minusSeconds(1)
        );

        when(refreshTokenRepository.findByTokenHash(
                hashToken(RAW_TOKEN)
        )).thenReturn(Optional.of(activeToken));

        assertThrows(
                RefreshTokenExpiredException.class,
                () -> refreshTokenService
                        .verifyRefreshToken(RAW_TOKEN)
        );
    }

    @Test
    void verifyRefreshToken_ExpiringNow_ShouldThrow() {

        activeToken.setExpiresAt(FIXED_DATE_TIME);

        when(refreshTokenRepository.findByTokenHash(
                hashToken(RAW_TOKEN)
        )).thenReturn(Optional.of(activeToken));

        assertThrows(
                RefreshTokenExpiredException.class,
                () -> refreshTokenService
                        .verifyRefreshToken(RAW_TOKEN)
        );
    }

    @Test
    void revokeToken_WithActiveToken_ShouldRevokeIt() {

        when(refreshTokenRepository.findByTokenHash(
                hashToken(RAW_TOKEN)
        )).thenReturn(Optional.of(activeToken));

        refreshTokenService.revokeToken(RAW_TOKEN);

        assertTrue(activeToken.getRevoked());

        verify(refreshTokenRepository).save(activeToken);
    }

    @Test
    void revokeToken_WithAlreadyRevokedToken_ShouldBeIdempotent() {

        activeToken.setRevoked(true);

        when(refreshTokenRepository.findByTokenHash(
                hashToken(RAW_TOKEN)
        )).thenReturn(Optional.of(activeToken));

        refreshTokenService.revokeToken(RAW_TOKEN);

        verify(refreshTokenRepository, never())
                .save(any(RefreshToken.class));
    }

    @Test
    void revokeAllUserTokens_WithValidId_ShouldRevokeTokens() {

        refreshTokenService.revokeAllUserTokens(1L);

        verify(refreshTokenRepository)
                .revokeAllActiveTokensByUserId(1L);
    }

    @Test
    void revokeAllUserTokens_WithNullId_ShouldThrow() {

        assertThrows(
                IllegalArgumentException.class,
                () -> refreshTokenService
                        .revokeAllUserTokens(null)
        );

        verifyNoInteractions(refreshTokenRepository);
    }

    @Test
    void deleteExpiredOrRevokedTokens_ShouldUseCurrentTime() {

        when(refreshTokenRepository
                .deleteExpiredOrRevokedTokens(
                        FIXED_DATE_TIME
                ))
                .thenReturn(3);

        int deleted =
                refreshTokenService
                        .deleteExpiredOrRevokedTokens();

        assertEquals(3, deleted);

        verify(refreshTokenRepository)
                .deleteExpiredOrRevokedTokens(
                        FIXED_DATE_TIME
                );
    }

    private String hashToken(
            String rawToken
    ) {
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