package com.omnia.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(
        List<String> allowedOrigins
) {

    public CorsProperties {

        if (allowedOrigins == null
                || allowedOrigins.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one CORS allowed origin is required"
            );
        }

        allowedOrigins = allowedOrigins.stream()
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .distinct()
                .toList();

        if (allowedOrigins.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one CORS allowed origin is required"
            );
        }

        if (allowedOrigins.contains("*")) {
            throw new IllegalArgumentException(
                    "Wildcard CORS origins are not allowed"
            );
        }
    }
}