package com.omnia.backend.security.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnia.backend.common.response.ErrorResponse;
import com.omnia.backend.common.response.ErrorResponseFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class SecurityErrorResponseWriter {

    private final ObjectMapper objectMapper;

    public SecurityErrorResponseWriter(
            ObjectMapper objectMapper
    ) {
        this.objectMapper = objectMapper;
    }

    public void write(
            HttpServletRequest request,
            HttpServletResponse response,
            HttpStatus status,
            String message
    ) throws IOException {

        ErrorResponse errorResponse =
                ErrorResponseFactory.create(
                        status,
                        message,
                        request
                );

        response.setStatus(status.value());

        response.setCharacterEncoding(
                StandardCharsets.UTF_8.name()
        );

        response.setContentType(
                MediaType.APPLICATION_JSON_VALUE
        );

        objectMapper.writeValue(
                response.getOutputStream(),
                errorResponse
        );
    }
}