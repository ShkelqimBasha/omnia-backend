package com.omnia.backend.service.impl;

import com.omnia.backend.common.exception.InvalidRefreshTokenException;
import com.omnia.backend.common.exception.RefreshTokenExpiredException;
import com.omnia.backend.common.exception.RefreshTokenRevokedException;
import com.omnia.backend.config.RefreshTokenProperties;
import com.omnia.backend.entity.RefreshToken;
import com.omnia.backend.entity.User;
import com.omnia.backend.repository.RefreshTokenRepository;
import com.omnia.backend.security.service.SecureTokenService;
import com.omnia.backend.service.interfaces.RefreshTokenService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@Transactional
public class RefreshTokenServiceImpl
        implements RefreshTokenService {

    private static final int MAX_TOKEN_LENGTH = 512;

    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenProperties properties;
    private final Clock clock;
    private final SecureTokenService secureTokenService;

    public RefreshTokenServiceImpl(
            RefreshTokenRepository refreshTokenRepository,
            RefreshTokenProperties properties,
            Clock clock,
            SecureTokenService secureTokenService
    ) {
        this.refreshTokenRepository =
                refreshTokenRepository;
        this.properties = properties;
        this.clock = clock;
        this.secureTokenService = secureTokenService;
    }

    @Override
    public String createRefreshToken(User user) {
        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException(
                    "User must not be null and must have a valid ID"
            );
        }

        refreshTokenRepository
                .revokeAllActiveTokensByUserId(
                        user.getId()
                );

        String rawToken =
                secureTokenService.generateToken();

        RefreshToken refreshToken =
                RefreshToken.builder()
                        .user(user)
                        .tokenHash(
                                secureTokenService.hashToken(
                                        rawToken
                                )
                        )
                        .expiresAt(
                                currentDateTime().plus(
                                        properties.lifetime()
                                )
                        )
                        .revoked(false)
                        .build();

        refreshTokenRepository.save(refreshToken);

        return rawToken;
    }

    @Override
    @Transactional(readOnly = true)
    public RefreshToken verifyRefreshToken(
            String rawToken
    ) {
        validateTokenValue(rawToken);

        String tokenHash =
                secureTokenService.hashToken(rawToken);

        RefreshToken refreshToken =
                refreshTokenRepository
                        .findByTokenHash(tokenHash)
                        .orElseThrow(() ->
                                new InvalidRefreshTokenException(
                                        "Invalid refresh token"
                                )
                        );

        if (Boolean.TRUE.equals(
                refreshToken.getRevoked()
        )) {
            throw new RefreshTokenRevokedException(
                    "Refresh token has been revoked"
            );
        }

        if (!refreshToken.getExpiresAt()
                .isAfter(currentDateTime())) {
            throw new RefreshTokenExpiredException(
                    "Refresh token has expired"
            );
        }

        return refreshToken;
    }

    @Override
    public void revokeToken(String rawToken) {
        validateTokenValue(rawToken);

        String tokenHash =
                secureTokenService.hashToken(rawToken);

        RefreshToken refreshToken =
                refreshTokenRepository
                        .findByTokenHash(tokenHash)
                        .orElseThrow(() ->
                                new InvalidRefreshTokenException(
                                        "Invalid refresh token"
                                )
                        );

        if (!Boolean.TRUE.equals(
                refreshToken.getRevoked()
        )) {
            refreshToken.setRevoked(true);
            refreshTokenRepository.save(refreshToken);
        }
    }

    @Override
    public void revokeAllUserTokens(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException(
                    "User ID must not be null"
            );
        }

        refreshTokenRepository
                .revokeAllActiveTokensByUserId(userId);
    }

    @Override
    public int deleteExpiredOrRevokedTokens() {
        return refreshTokenRepository
                .deleteExpiredOrRevokedTokens(
                        currentDateTime()
                );
    }

    private void validateTokenValue(String token) {
        if (token == null || token.isBlank()) {
            throw new InvalidRefreshTokenException(
                    "Refresh token must not be blank"
            );
        }

        if (token.length() > MAX_TOKEN_LENGTH) {
            throw new InvalidRefreshTokenException(
                    "Refresh token is too long"
            );
        }
    }

    private LocalDateTime currentDateTime() {
        return LocalDateTime.ofInstant(
                clock.instant(),
                clock.getZone()
        );
    }
}