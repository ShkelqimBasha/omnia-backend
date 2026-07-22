package com.omnia.backend.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import org.springframework.security.test.context.support.WithMockUser;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ErrorResponseIntegrationTest
        extends AbstractIntegrationTest {

    private static final String CORRELATION_ID =
            "error-response-test-123";

    @Test
    void protectedEndpoint_WithoutToken_ShouldReturnStandard401()
            throws Exception {

        mockMvc.perform(
                        get("/api/cart")
                                .header(
                                        "X-Correlation-ID",
                                        CORRELATION_ID
                                )
                )
                .andExpect(status().isUnauthorized())
                .andExpect(
                        content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_JSON
                        )
                )
                .andExpect(
                        header().string(
                                "X-Correlation-ID",
                                CORRELATION_ID
                        )
                )
                .andExpect(
                        jsonPath("$.timestamp")
                                .isNotEmpty()
                )
                .andExpect(
                        jsonPath("$.status")
                                .value(401)
                )
                .andExpect(
                        jsonPath("$.error")
                                .value("Unauthorized")
                )
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "Authentication is required"
                                )
                )
                .andExpect(
                        jsonPath("$.path")
                                .value("/api/cart")
                )
                .andExpect(
                        jsonPath("$.correlationId")
                                .value(CORRELATION_ID)
                )
                .andExpect(
                        jsonPath("$.fieldErrors")
                                .doesNotExist()
                );
    }

    @Test
    void protectedEndpoint_WithInvalidToken_ShouldReturnStandard401()
            throws Exception {

        mockMvc.perform(
                        get("/api/cart")
                                .header(
                                        HttpHeaders.AUTHORIZATION,
                                        "Bearer invalid.jwt.token"
                                )
                                .header(
                                        "X-Correlation-ID",
                                        CORRELATION_ID
                                )
                )
                .andExpect(status().isUnauthorized())
                .andExpect(
                        content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_JSON
                        )
                )
                .andExpect(
                        jsonPath("$.status")
                                .value(401)
                )
                .andExpect(
                        jsonPath("$.error")
                                .value("Unauthorized")
                )
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "Authentication is required"
                                )
                )
                .andExpect(
                        jsonPath("$.path")
                                .value("/api/cart")
                )
                .andExpect(
                        jsonPath("$.correlationId")
                                .value(CORRELATION_ID)
                );
    }


    @Test
    void invalidRequest_ShouldReturnStandardValidationResponse()
            throws Exception {

        mockMvc.perform(
                        post("/api/auth/register")
                                .header(
                                        "X-Correlation-ID",
                                        CORRELATION_ID
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("{}")
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_JSON
                        )
                )
                .andExpect(
                        header().string(
                                "X-Correlation-ID",
                                CORRELATION_ID
                        )
                )
                .andExpect(
                        jsonPath("$.timestamp")
                                .isNotEmpty()
                )
                .andExpect(
                        jsonPath("$.status")
                                .value(400)
                )
                .andExpect(
                        jsonPath("$.error")
                                .value("Bad Request")
                )
                .andExpect(
                        jsonPath("$.message")
                                .value("Validation failed")
                )
                .andExpect(
                        jsonPath("$.path")
                                .value("/api/auth/register")
                )
                .andExpect(
                        jsonPath("$.correlationId")
                                .value(CORRELATION_ID)
                )
                .andExpect(
                        jsonPath("$.fieldErrors")
                                .isMap()
                )
                .andExpect(
                        jsonPath("$.fieldErrors")
                                .isNotEmpty()
                );
    }
    @Test
    @WithMockUser(
            username = "regular-user",
            roles = "USER"
    )
    void adminEndpoint_AsRegularUser_ShouldReturnStandard403()
            throws Exception {

        mockMvc.perform(
                        get("/api/roles")
                                .header(
                                        "X-Correlation-ID",
                                        CORRELATION_ID
                                )
                )
                .andExpect(status().isForbidden())
                .andExpect(
                        content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_JSON
                        )
                )
                .andExpect(
                        header().string(
                                "X-Correlation-ID",
                                CORRELATION_ID
                        )
                )
                .andExpect(
                        jsonPath("$.timestamp")
                                .isNotEmpty()
                )
                .andExpect(
                        jsonPath("$.status")
                                .value(403)
                )
                .andExpect(
                        jsonPath("$.error")
                                .value("Forbidden")
                )
                .andExpect(
                        jsonPath("$.message")
                                .value("Access denied")
                )
                .andExpect(
                        jsonPath("$.path")
                                .value("/api/roles")
                )
                .andExpect(
                        jsonPath("$.correlationId")
                                .value(CORRELATION_ID)
                )
                .andExpect(
                        jsonPath("$.fieldErrors")
                                .doesNotExist()
                );
    }

    @Test
    void malformedJson_ShouldReturnStandard400()
            throws Exception {

        mockMvc.perform(
                        post("/api/auth/register")
                                .header(
                                        "X-Correlation-ID",
                                        CORRELATION_ID
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("{invalid-json")
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_JSON
                        )
                )
                .andExpect(
                        jsonPath("$.status")
                                .value(400)
                )
                .andExpect(
                        jsonPath("$.error")
                                .value("Bad Request")
                )
                .andExpect(
                        jsonPath("$.message")
                                .value("Malformed JSON request")
                )
                .andExpect(
                        jsonPath("$.path")
                                .value("/api/auth/register")
                )
                .andExpect(
                        jsonPath("$.correlationId")
                                .value(CORRELATION_ID)
                );
    }

    @Test
    void missingRequestParameter_ShouldReturnStandard400()
            throws Exception {

        mockMvc.perform(
                        get("/api/auth/verify-email")
                                .header(
                                        "X-Correlation-ID",
                                        CORRELATION_ID
                                )
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_JSON
                        )
                )
                .andExpect(
                        jsonPath("$.status")
                                .value(400)
                )
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "Required request parameter "
                                                + "is missing"
                                )
                )
                .andExpect(
                        jsonPath("$.path")
                                .value(
                                        "/api/auth/verify-email"
                                )
                )
                .andExpect(
                        jsonPath("$.correlationId")
                                .value(CORRELATION_ID)
                );
    }

    @Test
    void unsupportedHttpMethod_ShouldReturnStandard405()
            throws Exception {

        mockMvc.perform(
                        put("/api/auth/login")
                                .header(
                                        "X-Correlation-ID",
                                        CORRELATION_ID
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("{}")
                )
                .andExpect(status().isMethodNotAllowed())
                .andExpect(
                        content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_JSON
                        )
                )
                .andExpect(
                        jsonPath("$.status")
                                .value(405)
                )
                .andExpect(
                        jsonPath("$.error")
                                .value("Method Not Allowed")
                )
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "HTTP method is not supported"
                                )
                )
                .andExpect(
                        jsonPath("$.path")
                                .value("/api/auth/login")
                )
                .andExpect(
                        jsonPath("$.correlationId")
                                .value(CORRELATION_ID)
                );
    }

    @Test
    void unsupportedMediaType_ShouldReturnStandard415()
            throws Exception {

        mockMvc.perform(
                        post("/api/auth/register")
                                .header(
                                        "X-Correlation-ID",
                                        CORRELATION_ID
                                )
                                .contentType(
                                        MediaType.TEXT_PLAIN
                                )
                                .content("invalid-content")
                )
                .andExpect(
                        status().isUnsupportedMediaType()
                )
                .andExpect(
                        content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_JSON
                        )
                )
                .andExpect(
                        jsonPath("$.status")
                                .value(415)
                )
                .andExpect(
                        jsonPath("$.error")
                                .value("Unsupported Media Type")
                )
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "Media type is not supported"
                                )
                )
                .andExpect(
                        jsonPath("$.path")
                                .value("/api/auth/register")
                )
                .andExpect(
                        jsonPath("$.correlationId")
                                .value(CORRELATION_ID)
                );
    }
}