package com.omnia.backend.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter
        extends OncePerRequestFilter {

    public static final String HEADER_NAME =
            "X-Correlation-ID";

    public static final String MDC_KEY =
            "correlationId";

    public static final String REQUEST_ATTRIBUTE =
            CorrelationIdFilter.class.getName()
                    + ".correlationId";

    private static final int MAX_LENGTH = 128;

    private static final Pattern VALID_CORRELATION_ID =
            Pattern.compile(
                    "^[A-Za-z0-9]"
                            + "[A-Za-z0-9._:-]{0,"
                            + (MAX_LENGTH - 1)
                            + "}$"
            );

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String correlationId = resolveCorrelationId(
                request.getHeader(HEADER_NAME)
        );

        request.setAttribute(
                REQUEST_ATTRIBUTE,
                correlationId
        );

        response.setHeader(
                HEADER_NAME,
                correlationId
        );

        MDC.put(MDC_KEY, correlationId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private String resolveCorrelationId(
            String requestedCorrelationId
    ) {
        if (requestedCorrelationId == null) {
            return generateCorrelationId();
        }

        String normalizedCorrelationId =
                requestedCorrelationId.trim();

        if (normalizedCorrelationId.isEmpty()
                || normalizedCorrelationId.length()
                > MAX_LENGTH
                || !VALID_CORRELATION_ID
                .matcher(normalizedCorrelationId)
                .matches()) {
            return generateCorrelationId();
        }

        return normalizedCorrelationId;
    }

    private String generateCorrelationId() {
        return UUID.randomUUID().toString();
    }
}