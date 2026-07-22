package com.omnia.backend.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET =
            "0123456789abcdef0123456789abcdef";

    private static final String DIFFERENT_SECRET =
            "abcdef0123456789abcdef0123456789";

    private static final long EXPIRATION_MILLISECONDS =
            3_600_000L;

    private static final String EMAIL =
            "user@example.com";

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(
                SECRET,
                EXPIRATION_MILLISECONDS
        );
    }

    @Test
    void constructor_WithValidConfiguration_ShouldCreateService() {

        JwtService service = new JwtService(
                SECRET,
                EXPIRATION_MILLISECONDS
        );

        assertThat(service).isNotNull();
    }

    @Test
    void constructor_WithNullSecret_ShouldRejectConfiguration() {

        assertThatThrownBy(
                () -> new JwtService(
                        null,
                        EXPIRATION_MILLISECONDS
                )
        )
                .isInstanceOf(
                        IllegalArgumentException.class
                )
                .hasMessage(
                        "JWT secret must not be blank"
                );
    }

    @Test
    void constructor_WithBlankSecret_ShouldRejectConfiguration() {

        assertThatThrownBy(
                () -> new JwtService(
                        "   ",
                        EXPIRATION_MILLISECONDS
                )
        )
                .isInstanceOf(
                        IllegalArgumentException.class
                )
                .hasMessage(
                        "JWT secret must not be blank"
                );
    }

    @Test
    void constructor_WithShortSecret_ShouldRejectConfiguration() {

        String shortSecret = "a".repeat(31);

        assertThatThrownBy(
                () -> new JwtService(
                        shortSecret,
                        EXPIRATION_MILLISECONDS
                )
        )
                .isInstanceOf(
                        IllegalArgumentException.class
                )
                .hasMessageContaining(
                        "at least 32 bytes"
                );
    }

    @Test
    void constructor_WithZeroExpiration_ShouldRejectConfiguration() {

        assertThatThrownBy(
                () -> new JwtService(
                        SECRET,
                        0
                )
        )
                .isInstanceOf(
                        IllegalArgumentException.class
                )
                .hasMessage(
                        "JWT expiration must be greater than zero"
                );
    }

    @Test
    void constructor_WithNegativeExpiration_ShouldRejectConfiguration() {

        assertThatThrownBy(
                () -> new JwtService(
                        SECRET,
                        -1
                )
        )
                .isInstanceOf(
                        IllegalArgumentException.class
                )
                .hasMessage(
                        "JWT expiration must be greater than zero"
                );
    }

    @Test
    void generateToken_WithValidEmail_ShouldCreateSignedToken() {

        String token =
                jwtService.generateToken(EMAIL);

        assertThat(token).isNotBlank();

        assertThat(
                jwtService.extractUsername(token)
        ).isEqualTo(EMAIL);
    }

    @Test
    void generateToken_WithNullEmail_ShouldRejectSubject() {

        assertThatThrownBy(
                () -> jwtService.generateToken(null)
        )
                .isInstanceOf(
                        IllegalArgumentException.class
                )
                .hasMessage(
                        "JWT subject must not be blank"
                );
    }

    @Test
    void generateToken_WithBlankEmail_ShouldRejectSubject() {

        assertThatThrownBy(
                () -> jwtService.generateToken("   ")
        )
                .isInstanceOf(
                        IllegalArgumentException.class
                )
                .hasMessage(
                        "JWT subject must not be blank"
                );
    }

    @Test
    void generatedToken_ShouldContainExpectedClaims() {

        Instant beforeGeneration =
                Instant.now().minusSeconds(1);

        String token =
                jwtService.generateToken(EMAIL);

        Instant afterGeneration =
                Instant.now().plusSeconds(1);

        Claims claims = parseClaims(
                token,
                SECRET
        );

        assertThat(claims.getSubject())
                .isEqualTo(EMAIL);

        assertThat(claims.getIssuedAt())
                .isBetween(
                        Date.from(beforeGeneration),
                        Date.from(afterGeneration)
                );

        assertThat(claims.getExpiration())
                .isAfter(claims.getIssuedAt());

        long actualLifetime =
                claims.getExpiration().getTime()
                        - claims.getIssuedAt().getTime();

        assertThat(actualLifetime)
                .isEqualTo(EXPIRATION_MILLISECONDS);
    }

    @Test
    void isTokenValid_WithValidTokenAndMatchingEmail_ShouldReturnTrue() {

        String token =
                jwtService.generateToken(EMAIL);

        assertThat(
                jwtService.isTokenValid(
                        token,
                        EMAIL
                )
        ).isTrue();
    }

    @Test
    void isTokenValid_WithDifferentEmail_ShouldReturnFalse() {

        String token =
                jwtService.generateToken(EMAIL);

        assertThat(
                jwtService.isTokenValid(
                        token,
                        "different@example.com"
                )
        ).isFalse();
    }

    @Test
    void isTokenValid_WithNullToken_ShouldReturnFalse() {

        assertThat(
                jwtService.isTokenValid(
                        null,
                        EMAIL
                )
        ).isFalse();
    }

    @Test
    void isTokenValid_WithBlankToken_ShouldReturnFalse() {

        assertThat(
                jwtService.isTokenValid(
                        "   ",
                        EMAIL
                )
        ).isFalse();
    }

    @Test
    void isTokenValid_WithNullExpectedEmail_ShouldReturnFalse() {

        String token =
                jwtService.generateToken(EMAIL);

        assertThat(
                jwtService.isTokenValid(
                        token,
                        null
                )
        ).isFalse();
    }

    @Test
    void isTokenValid_WithBlankExpectedEmail_ShouldReturnFalse() {

        String token =
                jwtService.generateToken(EMAIL);

        assertThat(
                jwtService.isTokenValid(
                        token,
                        "   "
                )
        ).isFalse();
    }

    @Test
    void isTokenValid_WithMalformedToken_ShouldReturnFalse() {

        assertThat(
                jwtService.isTokenValid(
                        "not-a-valid-jwt",
                        EMAIL
                )
        ).isFalse();
    }

    @Test
    void isTokenValid_WithTamperedToken_ShouldReturnFalse() {

        String validToken =
                jwtService.generateToken(EMAIL);

        String tamperedToken =
                validToken + "tampered";

        assertThat(
                jwtService.isTokenValid(
                        tamperedToken,
                        EMAIL
                )
        ).isFalse();
    }

    @Test
    void isTokenValid_WithTokenSignedByDifferentKey_ShouldReturnFalse() {

        String token = createToken(
                EMAIL,
                DIFFERENT_SECRET,
                Instant.now().minusSeconds(10),
                Instant.now().plusSeconds(300)
        );

        assertThat(
                jwtService.isTokenValid(
                        token,
                        EMAIL
                )
        ).isFalse();
    }

    @Test
    void isTokenValid_WithExpiredToken_ShouldReturnFalse() {

        Instant now = Instant.now();

        String expiredToken = createToken(
                EMAIL,
                SECRET,
                now.minusSeconds(120),
                now.minusSeconds(60)
        );

        assertThat(
                jwtService.isTokenValid(
                        expiredToken,
                        EMAIL
                )
        ).isFalse();
    }

    @Test
    void extractUsername_WithBlankToken_ShouldRejectToken() {

        assertThatThrownBy(
                () -> jwtService.extractUsername("   ")
        )
                .isInstanceOf(
                        IllegalArgumentException.class
                )
                .hasMessage(
                        "JWT must not be blank"
                );
    }

    @Test
    void extractUsername_WithExpiredToken_ShouldThrowExpiredJwtException() {

        Instant now = Instant.now();

        String expiredToken = createToken(
                EMAIL,
                SECRET,
                now.minusSeconds(120),
                now.minusSeconds(60)
        );

        assertThatThrownBy(
                () -> jwtService.extractUsername(
                        expiredToken
                )
        ).isInstanceOf(
                ExpiredJwtException.class
        );
    }

    @Test
    void extractUsername_WithMalformedToken_ShouldThrowJwtException() {

        assertThatThrownBy(
                () -> jwtService.extractUsername(
                        "malformed-token"
                )
        ).isInstanceOf(
                JwtException.class
        );
    }

    private String createToken(
            String subject,
            String signingSecret,
            Instant issuedAt,
            Instant expiresAt
    ) {
        SecretKey key = Keys.hmacShaKeyFor(
                signingSecret.getBytes(
                        StandardCharsets.UTF_8
                )
        );

        return Jwts.builder()
                .subject(subject)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(
                        key,
                        Jwts.SIG.HS256
                )
                .compact();
    }

    private Claims parseClaims(
            String token,
            String signingSecret
    ) {
        SecretKey key = Keys.hmacShaKeyFor(
                signingSecret.getBytes(
                        StandardCharsets.UTF_8
                )
        );

        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}