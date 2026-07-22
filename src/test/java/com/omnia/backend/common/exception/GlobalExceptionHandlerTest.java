package com.omnia.backend.common.exception;

import com.omnia.backend.common.response.ErrorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        exceptionHandler =
                new GlobalExceptionHandler();

        request = new MockHttpServletRequest();
        request.setRequestURI("/v3/api-docs");
    }

    @Test
    void handleNoResourceFound_ShouldReturnNotFoundResponse() {

        NoResourceFoundException exception =
                new NoResourceFoundException(
                        HttpMethod.GET,
                        "/v3/api-docs"
                );

        ResponseEntity<ErrorResponse> response =
                exceptionHandler.handleNoResourceFound(
                        exception,
                        request
                );

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        assertThat(response.getBody())
                .isNotNull();

        assertThat(response.getBody().getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND.value());

        assertThat(response.getBody().getError())
                .isEqualTo(
                        HttpStatus.NOT_FOUND
                                .getReasonPhrase()
                );

        assertThat(response.getBody().getMessage())
                .isEqualTo("Resource not found");

        assertThat(response.getBody().getPath())
                .isEqualTo("/v3/api-docs");

        assertThat(response.getBody().getTimestamp())
                .isNotNull();
    }
}