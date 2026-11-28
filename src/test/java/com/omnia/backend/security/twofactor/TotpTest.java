package com.omnia.backend.security.twofactor;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class TotpTest {

    /** RFC 6238 appendix B, SHA-1 secret "12345678901234567890" (last 6 of the 8 digits). */
    private static final String RFC_SECRET =
            Totp.base32("12345678901234567890".getBytes(StandardCharsets.US_ASCII));

    @Test
    void matchesTheRfcTestVectors() {
        assertThat(RFC_SECRET).isEqualTo("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");
        assertThat(Totp.code(RFC_SECRET, Totp.stepAt(59))).isEqualTo("287082");
        assertThat(Totp.code(RFC_SECRET, Totp.stepAt(1111111109))).isEqualTo("081804");
        assertThat(Totp.code(RFC_SECRET, Totp.stepAt(1234567890))).isEqualTo("005924");
        assertThat(Totp.code(RFC_SECRET, Totp.stepAt(2000000000))).isEqualTo("279037");
    }

    @Test
    void base32RoundTrips() {
        String secret = Totp.newSecret();
        assertThat(secret).hasSize(32).matches("[A-Z2-7]+");
        assertThat(Totp.base32(Totp.base32Decode(secret))).isEqualTo(secret);
    }

    @Test
    void acceptsOneStepOfDriftAndEachCodeOnlyOnce() {
        String secret = Totp.newSecret();
        long now = 1_000_000;
        assertThat(Totp.matchingStep(secret, Totp.code(secret, now), now, 0)).isEqualTo(now);
        assertThat(Totp.matchingStep(secret, Totp.code(secret, now - 1), now, 0)).isEqualTo(now - 1);
        assertThat(Totp.matchingStep(secret, Totp.code(secret, now + 1), now, 0)).isEqualTo(now + 1);
        assertThat(Totp.matchingStep(secret, Totp.code(secret, now - 2), now, 0)).isEqualTo(-1);
        // Already used: the same step does not work again.
        assertThat(Totp.matchingStep(secret, Totp.code(secret, now), now, now)).isEqualTo(-1);
        assertThat(Totp.matchingStep(secret, "12345", now, 0)).isEqualTo(-1);
        assertThat(Totp.matchingStep(secret, "abcdef", now, 0)).isEqualTo(-1);
        assertThat(Totp.matchingStep(secret, null, now, 0)).isEqualTo(-1);
    }

    @Test
    void otpauthUriNamesOmniaAndTheAccount() {
        assertThat(TwoFactorService.otpauthUri("arta@example.com", "ABC"))
                .isEqualTo("otpauth://totp/Omnia%3Aarta%40example.com?secret=ABC&issuer=Omnia"
                        + "&algorithm=SHA1&digits=6&period=30");
        assertThat(TwoFactorService.normalizeRecovery(" AB2c-DE3f ")).isEqualTo("ab2cde3f");
    }
}
