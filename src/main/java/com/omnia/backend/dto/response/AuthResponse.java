package com.omnia.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class AuthResponse {

    private String accessToken;

    private String refreshToken;

    private String message;

    /** True when the password was right but a 2FA code is still needed (no tokens yet). */
    private Boolean twoFactorRequired;

    /** Short-lived token for POST /api/auth/2fa/verify; only with twoFactorRequired. */
    private String challengeToken;

}