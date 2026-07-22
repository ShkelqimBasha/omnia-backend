package com.omnia.backend.service.interfaces;

import com.omnia.backend.entity.RefreshToken;
import com.omnia.backend.entity.User;

public interface RefreshTokenService {

    String createRefreshToken(
            User user
    );

    RefreshToken verifyRefreshToken(
            String rawToken
    );

    void revokeToken(
            String rawToken
    );

    void revokeAllUserTokens(
            Long userId
    );

    int deleteExpiredOrRevokedTokens();
}