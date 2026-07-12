package com.omnia.backend.service.impl;

import com.omnia.backend.common.exception.InvalidRefreshTokenException;
import com.omnia.backend.common.exception.RefreshTokenExpiredException;
import com.omnia.backend.common.exception.RefreshTokenRevokedException;
import com.omnia.backend.entity.RefreshToken;
import com.omnia.backend.entity.User;
import com.omnia.backend.repository.RefreshTokenRepository;
import com.omnia.backend.service.interfaces.RefreshTokenService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Transactional
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private static final long REFRESH_TOKEN_DAYS = 30;

    private final RefreshTokenRepository refreshTokenRepository;

    public RefreshTokenServiceImpl(
            RefreshTokenRepository refreshTokenRepository
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Override
    public RefreshToken createRefreshToken(User user) {

        if (user == null || user.getId() == null) {
            throw new IllegalArgumentException(
                    "User must not be null and must have a valid ID"
            );
        }

        refreshTokenRepository
                .revokeAllActiveTokensByUserId(user.getId());

        LocalDateTime now = LocalDateTime.now();

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .token(UUID.randomUUID().toString())
                .expiresAt(now.plusDays(REFRESH_TOKEN_DAYS))
                .revoked(false)
                .build();

        return refreshTokenRepository.save(refreshToken);
    }

    @Override
    @Transactional(readOnly = true)
    public RefreshToken verifyRefreshToken(String token) {

        validateTokenValue(token);

        RefreshToken refreshToken =
                refreshTokenRepository.findByToken(token)
                        .orElseThrow(() ->
                                new InvalidRefreshTokenException(
                                        "Invalid refresh token"
                                )
                        );

        if (Boolean.TRUE.equals(refreshToken.getRevoked())) {
            throw new RefreshTokenRevokedException(
                    "Refresh token has been revoked"
            );
        }

        if (!refreshToken.getExpiresAt()
                .isAfter(LocalDateTime.now())) {
            throw new RefreshTokenExpiredException(
                    "Refresh token has expired"
            );
        }

        return refreshToken;
    }

    @Override
    public void revokeToken(String token) {

        validateTokenValue(token);

        RefreshToken refreshToken =
                refreshTokenRepository.findByToken(token)
                        .orElseThrow(() ->
                                new InvalidRefreshTokenException(
                                        "Invalid refresh token"
                                )
                        );

        if (!Boolean.TRUE.equals(refreshToken.getRevoked())) {
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
    public int deleteExpiredTokens() {

        return refreshTokenRepository
                .deleteExpiredTokens(LocalDateTime.now());
    }

    private void validateTokenValue(String token) {

        if (token == null || token.isBlank()) {
            throw new InvalidRefreshTokenException(
                    "Refresh token must not be blank"
            );
        }
    }
}