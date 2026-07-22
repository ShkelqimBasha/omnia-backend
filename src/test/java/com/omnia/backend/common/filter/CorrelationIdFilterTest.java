package com.omnia.backend.common.filter;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdFilterTest {

    private CorrelationIdFilter filter;

    @BeforeEach
    void setUp() {
        MDC.clear();
        filter = new CorrelationIdFilter();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void doFilter_WithValidCorrelationId_ShouldPreserveIt()
            throws Exception {

        MockHttpServletRequest request =
                new MockHttpServletRequest();

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        String correlationId = "android-request-123";

        request.addHeader(
                CorrelationIdFilter.HEADER_NAME,
                correlationId
        );

        FilterChain filterChain = (servletRequest, servletResponse) -> {
            assertThat(
                    MDC.get(CorrelationIdFilter.MDC_KEY)
            ).isEqualTo(correlationId);

            assertThat(
                    servletRequest.getAttribute(
                            CorrelationIdFilter.REQUEST_ATTRIBUTE
                    )
            ).isEqualTo(correlationId);
        };

        filter.doFilter(
                request,
                response,
                filterChain
        );

        assertThat(
                response.getHeader(
                        CorrelationIdFilter.HEADER_NAME
                )
        ).isEqualTo(correlationId);

        assertThat(
                MDC.get(CorrelationIdFilter.MDC_KEY)
        ).isNull();
    }

    @Test
    void doFilter_WithSurroundingWhitespace_ShouldNormalizeId()
            throws Exception {

        MockHttpServletRequest request =
                new MockHttpServletRequest();

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        request.addHeader(
                CorrelationIdFilter.HEADER_NAME,
                "  request-456  "
        );

        filter.doFilter(
                request,
                response,
                (servletRequest, servletResponse) -> {
                    assertThat(
                            MDC.get(
                                    CorrelationIdFilter.MDC_KEY
                            )
                    ).isEqualTo("request-456");
                }
        );

        assertThat(
                response.getHeader(
                        CorrelationIdFilter.HEADER_NAME
                )
        ).isEqualTo("request-456");

        assertThat(
                MDC.get(CorrelationIdFilter.MDC_KEY)
        ).isNull();
    }

    @Test
    void doFilter_WithoutCorrelationId_ShouldGenerateUuid()
            throws Exception {

        MockHttpServletRequest request =
                new MockHttpServletRequest();

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        filter.doFilter(
                request,
                response,
                (servletRequest, servletResponse) -> {
                }
        );

        String generatedCorrelationId =
                response.getHeader(
                        CorrelationIdFilter.HEADER_NAME
                );

        assertThat(generatedCorrelationId)
                .isNotBlank();

        assertThat(
                UUID.fromString(generatedCorrelationId)
        ).isNotNull();

        assertThat(
                request.getAttribute(
                        CorrelationIdFilter.REQUEST_ATTRIBUTE
                )
        ).isEqualTo(generatedCorrelationId);

        assertThat(
                MDC.get(CorrelationIdFilter.MDC_KEY)
        ).isNull();
    }

    @Test
    void doFilter_WithInvalidCharacters_ShouldGenerateNewUuid()
            throws Exception {

        MockHttpServletRequest request =
                new MockHttpServletRequest();

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        request.addHeader(
                CorrelationIdFilter.HEADER_NAME,
                "invalid correlation id"
        );

        filter.doFilter(
                request,
                response,
                (servletRequest, servletResponse) -> {
                }
        );

        String generatedCorrelationId =
                response.getHeader(
                        CorrelationIdFilter.HEADER_NAME
                );

        assertThat(generatedCorrelationId)
                .isNotEqualTo("invalid correlation id");

        assertThat(
                UUID.fromString(generatedCorrelationId)
        ).isNotNull();
    }

    @Test
    void doFilter_WithOversizedId_ShouldGenerateNewUuid()
            throws Exception {

        MockHttpServletRequest request =
                new MockHttpServletRequest();

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        String oversizedCorrelationId =
                "a".repeat(129);

        request.addHeader(
                CorrelationIdFilter.HEADER_NAME,
                oversizedCorrelationId
        );

        filter.doFilter(
                request,
                response,
                (servletRequest, servletResponse) -> {
                }
        );

        String generatedCorrelationId =
                response.getHeader(
                        CorrelationIdFilter.HEADER_NAME
                );

        assertThat(generatedCorrelationId)
                .isNotEqualTo(oversizedCorrelationId);

        assertThat(
                UUID.fromString(generatedCorrelationId)
        ).isNotNull();
    }

    @Test
    void doFilter_WhenDownstreamFails_ShouldAlwaysClearMdc()
            throws Exception {

        MockHttpServletRequest request =
                new MockHttpServletRequest();

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        request.addHeader(
                CorrelationIdFilter.HEADER_NAME,
                "failing-request-789"
        );

        try {
            filter.doFilter(
                    request,
                    response,
                    (servletRequest, servletResponse) -> {
                        throw new IllegalStateException(
                                "Simulated downstream failure"
                        );
                    }
            );
        } catch (IllegalStateException ignored) {
        }

        assertThat(
                response.getHeader(
                        CorrelationIdFilter.HEADER_NAME
                )
        ).isEqualTo("failing-request-789");

        assertThat(
                MDC.get(CorrelationIdFilter.MDC_KEY)
        ).isNull();
    }
}