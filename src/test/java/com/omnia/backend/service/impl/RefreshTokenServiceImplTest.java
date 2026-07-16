package com.omnia.backend.service.impl;

import com.omnia.backend.common.exception.InvalidRefreshTokenException;
import com.omnia.backend.common.exception.RefreshTokenExpiredException;
import com.omnia.backend.common.exception.RefreshTokenRevokedException;
import com.omnia.backend.entity.RefreshToken;
import com.omnia.backend.entity.User;
import com.omnia.backend.repository.RefreshTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceImplTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @InjectMocks
    private RefreshTokenServiceImpl refreshTokenService;

    private User user;
    private RefreshToken activeToken;

    @BeforeEach
    void setUp() {

        user = User.builder()
                .id(1L)
                .username("shkelqim")
                .email("shkelqim@example.com")
                .build();

        activeToken = RefreshToken.builder()
                .id(10L)
                .user(user)
                .token("valid-refresh-token")
                .expiresAt(LocalDateTime.now().plusDays(10))
                .revoked(false)
                .build();
    }

    @Test
    void createRefreshToken_shouldCreateTokenSuccessfully() {

        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(invocation -> {
                    RefreshToken token = invocation.getArgument(0);
                    token.setId(10L);
                    return token;
                });

        RefreshToken result =
                refreshTokenService.createRefreshToken(user);

        assertNotNull(result);
        assertEquals(10L, result.getId());
        assertEquals(user, result.getUser());
        assertNotNull(result.getToken());
        assertFalse(result.getToken().isBlank());
        assertFalse(result.getRevoked());
        assertNotNull(result.getExpiresAt());
        assertTrue(
                result.getExpiresAt()
                        .isAfter(LocalDateTime.now().plusDays(29))
        );

        verify(refreshTokenRepository)
                .revokeAllActiveTokensByUserId(1L);

        verify(refreshTokenRepository)
                .save(any(RefreshToken.class));
    }

    @Test
    void createRefreshToken_shouldGenerateDifferentTokens() {

        when(refreshTokenRepository.save(any(RefreshToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RefreshToken first =
                refreshTokenService.createRefreshToken(user);

        RefreshToken second =
                refreshTokenService.createRefreshToken(user);

        assertNotNull(first.getToken());
        assertNotNull(second.getToken());
        assertNotEquals(
                first.getToken(),
                second.getToken()
        );

        verify(refreshTokenRepository, times(2))
                .revokeAllActiveTokensByUserId(1L);

        verify(refreshTokenRepository, times(2))
                .save(any(RefreshToken.class));
    }

    @Test
    void createRefreshToken_shouldThrowWhenUserIsNull() {

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> refreshTokenService.createRefreshToken(null)
                );

        assertEquals(
                "User must not be null and must have a valid ID",
                exception.getMessage()
        );

        verifyNoInteractions(refreshTokenRepository);
    }

    @Test
    void createRefreshToken_shouldThrowWhenUserIdIsNull() {

        User userWithoutId = User.builder()
                .username("shkelqim")
                .email("shkelqim@example.com")
                .build();

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> refreshTokenService.createRefreshToken(
                                userWithoutId
                        )
                );

        assertEquals(
                "User must not be null and must have a valid ID",
                exception.getMessage()
        );

        verifyNoInteractions(refreshTokenRepository);
    }

    @Test
    void verifyRefreshToken_shouldReturnValidToken() {

        when(refreshTokenRepository.findByToken(
                "valid-refresh-token"
        )).thenReturn(Optional.of(activeToken));

        RefreshToken result =
                refreshTokenService.verifyRefreshToken(
                        "valid-refresh-token"
                );

        assertSame(activeToken, result);

        verify(refreshTokenRepository)
                .findByToken("valid-refresh-token");
    }

    @Test
    void verifyRefreshToken_shouldThrowWhenTokenIsNull() {

        InvalidRefreshTokenException exception =
                assertThrows(
                        InvalidRefreshTokenException.class,
                        () -> refreshTokenService.verifyRefreshToken(null)
                );

        assertEquals(
                "Refresh token must not be blank",
                exception.getMessage()
        );

        verifyNoInteractions(refreshTokenRepository);
    }

    @Test
    void verifyRefreshToken_shouldThrowWhenTokenIsBlank() {

        InvalidRefreshTokenException exception =
                assertThrows(
                        InvalidRefreshTokenException.class,
                        () -> refreshTokenService.verifyRefreshToken("   ")
                );

        assertEquals(
                "Refresh token must not be blank",
                exception.getMessage()
        );

        verifyNoInteractions(refreshTokenRepository);
    }

    @Test
    void verifyRefreshToken_shouldThrowWhenTokenDoesNotExist() {

        when(refreshTokenRepository.findByToken("invalid-token"))
                .thenReturn(Optional.empty());

        InvalidRefreshTokenException exception =
                assertThrows(
                        InvalidRefreshTokenException.class,
                        () -> refreshTokenService.verifyRefreshToken(
                                "invalid-token"
                        )
                );

        assertEquals(
                "Invalid refresh token",
                exception.getMessage()
        );
    }

    @Test
    void verifyRefreshToken_shouldThrowWhenTokenIsRevoked() {

        activeToken.setRevoked(true);

        when(refreshTokenRepository.findByToken(
                "valid-refresh-token"
        )).thenReturn(Optional.of(activeToken));

        RefreshTokenRevokedException exception =
                assertThrows(
                        RefreshTokenRevokedException.class,
                        () -> refreshTokenService.verifyRefreshToken(
                                "valid-refresh-token"
                        )
                );

        assertEquals(
                "Refresh token has been revoked",
                exception.getMessage()
        );
    }

    @Test
    void verifyRefreshToken_shouldThrowWhenTokenIsExpired() {

        activeToken.setExpiresAt(
                LocalDateTime.now().minusSeconds(1)
        );

        when(refreshTokenRepository.findByToken(
                "valid-refresh-token"
        )).thenReturn(Optional.of(activeToken));

        RefreshTokenExpiredException exception =
                assertThrows(
                        RefreshTokenExpiredException.class,
                        () -> refreshTokenService.verifyRefreshToken(
                                "valid-refresh-token"
                        )
                );

        assertEquals(
                "Refresh token has expired",
                exception.getMessage()
        );
    }

    @Test
    void verifyRefreshToken_shouldThrowWhenExpiryIsNow() {

        activeToken.setExpiresAt(LocalDateTime.now());

        when(refreshTokenRepository.findByToken(
                "valid-refresh-token"
        )).thenReturn(Optional.of(activeToken));

        assertThrows(
                RefreshTokenExpiredException.class,
                () -> refreshTokenService.verifyRefreshToken(
                        "valid-refresh-token"
                )
        );
    }

    @Test
    void revokeToken_shouldRevokeActiveToken() {

        when(refreshTokenRepository.findByToken(
                "valid-refresh-token"
        )).thenReturn(Optional.of(activeToken));

        when(refreshTokenRepository.save(activeToken))
                .thenReturn(activeToken);

        refreshTokenService.revokeToken(
                "valid-refresh-token"
        );

        assertTrue(activeToken.getRevoked());

        verify(refreshTokenRepository)
                .save(activeToken);
    }

    @Test
    void revokeToken_shouldNotSaveWhenAlreadyRevoked() {

        activeToken.setRevoked(true);

        when(refreshTokenRepository.findByToken(
                "valid-refresh-token"
        )).thenReturn(Optional.of(activeToken));

        refreshTokenService.revokeToken(
                "valid-refresh-token"
        );

        verify(refreshTokenRepository, never())
                .save(any(RefreshToken.class));
    }

    @Test
    void revokeToken_shouldThrowWhenTokenDoesNotExist() {

        when(refreshTokenRepository.findByToken(
                "invalid-token"
        )).thenReturn(Optional.empty());

        InvalidRefreshTokenException exception =
                assertThrows(
                        InvalidRefreshTokenException.class,
                        () -> refreshTokenService.revokeToken(
                                "invalid-token"
                        )
                );

        assertEquals(
                "Invalid refresh token",
                exception.getMessage()
        );

        verify(refreshTokenRepository, never())
                .save(any(RefreshToken.class));
    }

    @Test
    void revokeToken_shouldThrowWhenTokenIsBlank() {

        InvalidRefreshTokenException exception =
                assertThrows(
                        InvalidRefreshTokenException.class,
                        () -> refreshTokenService.revokeToken("")
                );

        assertEquals(
                "Refresh token must not be blank",
                exception.getMessage()
        );

        verifyNoInteractions(refreshTokenRepository);
    }

    @Test
    void revokeAllUserTokens_shouldRevokeTokensSuccessfully() {

        refreshTokenService.revokeAllUserTokens(1L);

        verify(refreshTokenRepository)
                .revokeAllActiveTokensByUserId(1L);
    }

    @Test
    void revokeAllUserTokens_shouldThrowWhenUserIdIsNull() {

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> refreshTokenService
                                .revokeAllUserTokens(null)
                );

        assertEquals(
                "User ID must not be null",
                exception.getMessage()
        );

        verifyNoInteractions(refreshTokenRepository);
    }

    @Test
    void deleteExpiredTokens_shouldReturnDeletedCount() {

        when(refreshTokenRepository.deleteExpiredTokens(
                any(LocalDateTime.class)
        )).thenReturn(4);

        int result =
                refreshTokenService.deleteExpiredTokens();

        assertEquals(4, result);

        ArgumentCaptor<LocalDateTime> timeCaptor =
                ArgumentCaptor.forClass(LocalDateTime.class);

        verify(refreshTokenRepository)
                .deleteExpiredTokens(timeCaptor.capture());

        LocalDateTime suppliedTime =
                timeCaptor.getValue();

        assertNotNull(suppliedTime);
        assertTrue(
                suppliedTime.isBefore(
                        LocalDateTime.now().plusSeconds(1)
                )
        );
    }

    @Test
    void deleteExpiredTokens_shouldReturnZeroWhenNothingWasDeleted() {

        when(refreshTokenRepository.deleteExpiredTokens(
                any(LocalDateTime.class)
        )).thenReturn(0);

        int result =
                refreshTokenService.deleteExpiredTokens();

        assertEquals(0, result);
    }
}