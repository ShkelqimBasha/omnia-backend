package com.omnia.backend.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CorsIntegrationTest
        extends AbstractIntegrationTest {

    private static final String ALLOWED_ORIGIN =
            "http://localhost:3000";

    private static final String DISALLOWED_ORIGIN =
            "https://untrusted.example";

    @Test
    void preflightRequest_FromAllowedOrigin_ShouldSucceed()
            throws Exception {

        mockMvc.perform(
                        options("/api/auth/login")
                                .header(
                                        HttpHeaders.ORIGIN,
                                        ALLOWED_ORIGIN
                                )
                                .header(
                                        HttpHeaders
                                                .ACCESS_CONTROL_REQUEST_METHOD,
                                        "POST"
                                )
                                .header(
                                        HttpHeaders
                                                .ACCESS_CONTROL_REQUEST_HEADERS,
                                        "Authorization, "
                                                + "Content-Type, "
                                                + "X-Correlation-ID"
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        header().string(
                                HttpHeaders
                                        .ACCESS_CONTROL_ALLOW_ORIGIN,
                                ALLOWED_ORIGIN
                        )
                )
                .andExpect(
                        header().string(
                                HttpHeaders
                                        .ACCESS_CONTROL_ALLOW_METHODS,
                                containsString("POST")
                        )
                )
                .andExpect(
                        header().string(
                                HttpHeaders
                                        .ACCESS_CONTROL_ALLOW_HEADERS,
                                containsString("Authorization")
                        )
                )
                .andExpect(
                        header().string(
                                HttpHeaders
                                        .ACCESS_CONTROL_MAX_AGE,
                                "3600"
                        )
                );
    }

    @Test
    void preflightRequest_FromDisallowedOrigin_ShouldBeRejected()
            throws Exception {

        mockMvc.perform(
                        options("/api/auth/login")
                                .header(
                                        HttpHeaders.ORIGIN,
                                        DISALLOWED_ORIGIN
                                )
                                .header(
                                        HttpHeaders
                                                .ACCESS_CONTROL_REQUEST_METHOD,
                                        "POST"
                                )
                )
                .andExpect(status().isForbidden())
                .andExpect(
                        header().doesNotExist(
                                HttpHeaders
                                        .ACCESS_CONTROL_ALLOW_ORIGIN
                        )
                );
    }
}