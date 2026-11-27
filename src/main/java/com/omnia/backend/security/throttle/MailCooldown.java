package com.omnia.backend.security.throttle;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;

/**
 * At most {@link #LIMIT} password-reset or verification e-mails per address per
 * {@link #WINDOW}. Extra requests are dropped silently, so the answer stays the same.
 */
@Component
public class MailCooldown {

    static final int LIMIT = 3;
    static final Duration WINDOW = Duration.ofMinutes(30);

    private final RateLimiter limiter;

    public MailCooldown(RateLimiter limiter) {
        this.limiter = limiter;
    }

    public boolean allow(String kind, String email) {
        if (email == null) return true;
        return limiter.tryAcquire("mail:" + kind + ":" + email.trim().toLowerCase(Locale.ROOT), LIMIT, WINDOW);
    }
}
