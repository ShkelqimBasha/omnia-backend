package com.omnia.backend.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CorsPropertiesTest {

    @Test
    void constructor_WithValidOrigins_ShouldNormalizeOrigins() {

        CorsProperties properties =
                new CorsProperties(
                        List.of(
                                " http://localhost:3000 ",
                                "http://localhost:5173",
                                "http://localhost:3000"
                        )
                );

        assertThat(properties.allowedOrigins())
                .containsExactly(
                        "http://localhost:3000",
                        "http://localhost:5173"
                );
    }

    @Test
    void constructor_WithNullOrigins_ShouldThrowException() {

        assertThatThrownBy(() ->
                new CorsProperties(null)
        )
                .isInstanceOf(
                        IllegalArgumentException.class
                )
                .hasMessage(
                        "At least one CORS allowed origin "
                                + "is required"
                );
    }

    @Test
    void constructor_WithEmptyOrigins_ShouldThrowException() {

        assertThatThrownBy(() ->
                new CorsProperties(List.of())
        )
                .isInstanceOf(
                        IllegalArgumentException.class
                )
                .hasMessage(
                        "At least one CORS allowed origin "
                                + "is required"
                );
    }

    @Test
    void constructor_WithBlankOrigins_ShouldThrowException() {

        assertThatThrownBy(() ->
                new CorsProperties(
                        List.of(" ", "\t")
                )
        )
                .isInstanceOf(
                        IllegalArgumentException.class
                )
                .hasMessage(
                        "At least one CORS allowed origin "
                                + "is required"
                );
    }

    @Test
    void constructor_WithWildcardOrigin_ShouldThrowException() {

        assertThatThrownBy(() ->
                new CorsProperties(
                        List.of(
                                "http://localhost:3000",
                                "*"
                        )
                )
        )
                .isInstanceOf(
                        IllegalArgumentException.class
                )
                .hasMessage(
                        "Wildcard CORS origins are not allowed"
                );
    }
}