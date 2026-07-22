package com.omnia.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Objects;

@ConfigurationProperties(
        prefix = "app.security.refresh-token"
)
public record RefreshTokenProperties(
        Duration lifetime
) {

    private static final Duration MINIMUM_LIFETIME =
            Duration.ofMinutes(5);

    private static final Duration MAXIMUM_LIFETIME =
            Duration.ofDays(90);

    public RefreshTokenProperties {
        Objects.requireNonNull(
                lifetime,
                "Refresh token lifetime must not be null"
        );

        if (lifetime.compareTo(MINIMUM_LIFETIME) < 0) {
            throw new IllegalArgumentException(
                    "Refresh token lifetime must be at least 5 minutes"
            );
        }

        if (lifetime.compareTo(MAXIMUM_LIFETIME) > 0) {
            throw new IllegalArgumentException(
                    "Refresh token lifetime must not exceed 90 days"
            );
        }
    }
}