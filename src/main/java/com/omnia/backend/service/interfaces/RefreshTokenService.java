package com.omnia.backend.service.interfaces;

import com.omnia.backend.entity.RefreshToken;
import com.omnia.backend.entity.User;

public interface RefreshTokenService {

    RefreshToken createRefreshToken(User user);

    RefreshToken verifyRefreshToken(String token);

    void revokeToken(String token);

    void revokeAllUserTokens(Long userId);
}