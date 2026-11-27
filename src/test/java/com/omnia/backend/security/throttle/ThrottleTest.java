package com.omnia.backend.security.throttle;

import com.omnia.backend.common.exception.TooManyRequestsException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ThrottleTest {

    private final AtomicLong now = new AtomicLong(1_000_000L);
    private final Clock clock = new Clock() {
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return Instant.ofEpochMilli(now.get()); }
    };
    private final RateLimiter limiter = new RateLimiter(clock);

    @Test
    void limiterAllowsUpToTheLimitThenRefusesUntilTheWindowMoves() {
        for (int i = 0; i < 3; i++) {
            assertThat(limiter.tryAcquire("k", 3, Duration.ofMinutes(1))).isTrue();
        }
        assertThat(limiter.tryAcquire("k", 3, Duration.ofMinutes(1))).isFalse();
        now.addAndGet(Duration.ofMinutes(1).toMillis() + 1);
        assertThat(limiter.tryAcquire("k", 3, Duration.ofMinutes(1))).isTrue();
    }

    @Test
    void fiveWrongPasswordsLockTheAccountForFifteenMinutesAndSuccessClearsIt() {
        LoginAttempts attempts = new LoginAttempts(limiter);
        for (int i = 0; i < 4; i++) attempts.recordFailure("Arta@Example.com");
        attempts.requireNotLocked("arta@example.com");
        attempts.recordFailure("arta@example.com ");
        assertThatThrownBy(() -> attempts.requireNotLocked("ARTA@example.com"))
                .isInstanceOf(TooManyRequestsException.class)
                .hasMessageContaining("15 minutes");
        // Another account is not affected.
        attempts.requireNotLocked("someone-else");
        now.addAndGet(LoginAttempts.WINDOW.toMillis() + 1);
        attempts.requireNotLocked("arta@example.com");

        for (int i = 0; i < 4; i++) attempts.recordFailure("bora");
        attempts.recordSuccess("bora");
        attempts.recordFailure("bora");
        attempts.requireNotLocked("bora");
    }

    @Test
    void mailCooldownAllowsThreeEmailsPerAddressPerHalfHour() {
        MailCooldown cooldown = new MailCooldown(limiter);
        assertThat(cooldown.allow("reset", "a@b.c")).isTrue();
        assertThat(cooldown.allow("reset", "A@B.C")).isTrue();
        assertThat(cooldown.allow("reset", "a@b.c")).isTrue();
        assertThat(cooldown.allow("reset", "a@b.c")).isFalse();
        assertThat(cooldown.allow("verify", "a@b.c")).isTrue();
    }

    @Test
    void onlyAuthAndCouponLookupsAreThrottled() {
        assertThat(AuthThrottleFilter.ruleFor("POST", "/api/auth/login")).isEqualTo(AuthThrottleFilter.LOGIN);
        assertThat(AuthThrottleFilter.ruleFor("POST", "/api/auth/google")).isEqualTo(AuthThrottleFilter.LOGIN);
        assertThat(AuthThrottleFilter.ruleFor("POST", "/api/auth/register")).isEqualTo(AuthThrottleFilter.REGISTER);
        assertThat(AuthThrottleFilter.ruleFor("POST", "/api/auth/forgot-password")).isEqualTo(AuthThrottleFilter.PASSWORD);
        assertThat(AuthThrottleFilter.ruleFor("GET", "/api/coupons/SUMMER10")).isEqualTo(AuthThrottleFilter.COUPON);
        assertThat(AuthThrottleFilter.ruleFor("GET", "/api/coupons/")).isNull();
        assertThat(AuthThrottleFilter.ruleFor("GET", "/api/auth/me")).isNull();
        assertThat(AuthThrottleFilter.ruleFor("GET", "/api/products")).isNull();
    }

    @Test
    void clientIpTrustsForwardedForOnlyFromTheLocalProxy() {
        MockHttpServletRequest viaCaddy = new MockHttpServletRequest();
        viaCaddy.setRemoteAddr("127.0.0.1");
        viaCaddy.addHeader("X-Forwarded-For", "198.51.100.7");
        assertThat(ClientIp.of(viaCaddy)).isEqualTo("198.51.100.7");

        MockHttpServletRequest direct = new MockHttpServletRequest();
        direct.setRemoteAddr("203.0.113.9");
        direct.addHeader("X-Forwarded-For", "1.2.3.4");
        assertThat(ClientIp.of(direct)).isEqualTo("203.0.113.9");
    }
}
