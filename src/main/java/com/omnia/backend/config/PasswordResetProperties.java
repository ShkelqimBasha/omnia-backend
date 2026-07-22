package com.omnia.backend.config;

import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.time.DurationMax;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(
        prefix = "app.security.password-reset"
)
public record PasswordResetProperties(

        @NotNull(
                message = "Password reset lifetime must be configured"
        )
        @DurationMin(
                minutes = 5,
                message = "Password reset lifetime must be at least 5 minutes"
        )
        @DurationMax(
                hours = 24,
                message = "Password reset lifetime must not exceed 24 hours"
        )
        Duration lifetime
) {
}