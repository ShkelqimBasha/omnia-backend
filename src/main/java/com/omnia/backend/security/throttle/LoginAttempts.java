package com.omnia.backend.security.throttle;

import com.omnia.backend.common.exception.TooManyRequestsException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;

/**
 * Per-account lockout: after {@link #MAX_FAILURES} wrong passwords within
 * {@link #WINDOW} the typed username or e-mail is refused until the oldest failure
 * ages out. Unknown accounts are counted the same way, so the answer never tells
 * whether an account exists.
 */
@Component
public class LoginAttempts {

    static final int MAX_FAILURES = 5;
    static final Duration WINDOW = Duration.ofMinutes(15);

    private final RateLimiter limiter;

    public LoginAttempts(RateLimiter limiter) {
        this.limiter = limiter;
    }

    public void requireNotLocked(String identifier) {
        String key = key(identifier);
        if (limiter.count(key, WINDOW) >= MAX_FAILURES) {
            long minutes = Math.max(1, (limiter.millisUntilFree(key, WINDOW) + 59_999) / 60_000);
            throw new TooManyRequestsException(
                    "Too many failed sign-in attempts. Try again in " + minutes + " minutes.");
        }
    }

    public void recordFailure(String identifier) {
        limiter.tryAcquire(key(identifier), Integer.MAX_VALUE, WINDOW);
    }

    public void recordSuccess(String identifier) {
        limiter.reset(key(identifier));
    }

    private static String key(String identifier) {
        return "login-fail:" + (identifier == null ? "" : identifier.trim().toLowerCase(Locale.ROOT));
    }
}
