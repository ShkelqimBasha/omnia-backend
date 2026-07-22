package com.omnia.backend.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;

@Service
public class JwtService {

    private static final int MINIMUM_SECRET_BYTES = 32;

    private final SecretKey signingKey;
    private final long expirationMilliseconds;

    public JwtService(
            @Value("${app.jwt.secret}")
            String secret,

            @Value("${app.jwt.expiration}")
            long expirationMilliseconds
    ) {
        validateConfiguration(
                secret,
                expirationMilliseconds
        );

        this.signingKey = Keys.hmacShaKeyFor(
                secret.getBytes(StandardCharsets.UTF_8)
        );

        this.expirationMilliseconds =
                expirationMilliseconds;
    }

    public String generateToken(
            String email
    ) {
        if (!StringUtils.hasText(email)) {
            throw new IllegalArgumentException(
                    "JWT subject must not be blank"
            );
        }

        Instant issuedAt = Instant.now();

        Instant expiresAt = issuedAt.plusMillis(
                expirationMilliseconds
        );

        return Jwts.builder()
                .subject(email)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(
                        signingKey,
                        Jwts.SIG.HS256
                )
                .compact();
    }

    public String extractUsername(
            String token
    ) {
        return extractClaims(token).getSubject();
    }

    public boolean isTokenValid(
            String token,
            String expectedEmail
    ) {
        if (!StringUtils.hasText(token)
                || !StringUtils.hasText(expectedEmail)) {
            return false;
        }

        try {
            Claims claims = extractClaims(token);

            return Objects.equals(
                    claims.getSubject(),
                    expectedEmail
            ) && claims.getExpiration() != null
                    && claims.getExpiration().after(
                    Date.from(Instant.now())
            );
        } catch (
                JwtException
                | IllegalArgumentException exception
        ) {
            return false;
        }
    }

    private Claims extractClaims(
            String token
    ) {
        if (!StringUtils.hasText(token)) {
            throw new IllegalArgumentException(
                    "JWT must not be blank"
            );
        }

        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private void validateConfiguration(
            String secret,
            long expirationMilliseconds
    ) {
        if (!StringUtils.hasText(secret)) {
            throw new IllegalArgumentException(
                    "JWT secret must not be blank"
            );
        }

        int secretLength = secret
                .getBytes(StandardCharsets.UTF_8)
                .length;

        if (secretLength < MINIMUM_SECRET_BYTES) {
            throw new IllegalArgumentException(
                    "JWT secret must contain at least "
                            + MINIMUM_SECRET_BYTES
                            + " bytes"
            );
        }

        if (expirationMilliseconds <= 0) {
            throw new IllegalArgumentException(
                    "JWT expiration must be greater than zero"
            );
        }
    }
}