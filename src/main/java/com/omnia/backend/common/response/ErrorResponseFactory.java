package com.omnia.backend.common.response;

import com.omnia.backend.common.filter.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class ErrorResponseFactory {

    public static ErrorResponse create(
            HttpStatus status,
            String message,
            HttpServletRequest request
    ) {
        return create(
                status,
                message,
                request,
                null
        );
    }

    public static ErrorResponse create(
            HttpStatus status,
            String message,
            HttpServletRequest request,
            Map<String, List<String>> fieldErrors
    ) {
        return ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .path(request.getRequestURI())
                .correlationId(
                        resolveCorrelationId(request)
                )
                .fieldErrors(fieldErrors)
                .build();
    }

    private static String resolveCorrelationId(
            HttpServletRequest request
    ) {
        Object requestAttribute =
                request.getAttribute(
                        CorrelationIdFilter
                                .REQUEST_ATTRIBUTE
                );

        if (requestAttribute instanceof String value
                && !value.isBlank()) {
            return value;
        }

        String mdcValue =
                MDC.get(
                        CorrelationIdFilter.MDC_KEY
                );

        if (mdcValue == null
                || mdcValue.isBlank()) {
            return null;
        }

        return mdcValue;
    }

    private ErrorResponseFactory() {
    }
}