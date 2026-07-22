package com.omnia.backend.integration;

import com.omnia.backend.common.filter.CorrelationIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

class CorrelationIdIntegrationTest
        extends AbstractIntegrationTest {

    @Test
    void request_WithValidCorrelationId_ShouldReturnSameId()
            throws Exception {

        String correlationId =
                "integration-request-123";

        mockMvc.perform(
                        get("/api/products")
                                .header(
                                        CorrelationIdFilter.HEADER_NAME,
                                        correlationId
                                )
                )
                .andExpect(
                        header().string(
                                CorrelationIdFilter.HEADER_NAME,
                                correlationId
                        )
                );
    }

    @Test
    void request_WithoutCorrelationId_ShouldGenerateUuid()
            throws Exception {

        MvcResult result = mockMvc.perform(
                        get("/api/products")
                )
                .andExpect(
                        header().exists(
                                CorrelationIdFilter.HEADER_NAME
                        )
                )
                .andReturn();

        String generatedCorrelationId =
                result.getResponse().getHeader(
                        CorrelationIdFilter.HEADER_NAME
                );

        assertThat(generatedCorrelationId)
                .isNotBlank();

        assertThat(
                UUID.fromString(generatedCorrelationId)
        ).isNotNull();
    }

    @Test
    void request_WithInvalidCorrelationId_ShouldReplaceIt()
            throws Exception {

        String invalidCorrelationId =
                "invalid correlation id";

        MvcResult result = mockMvc.perform(
                        get("/api/products")
                                .header(
                                        CorrelationIdFilter.HEADER_NAME,
                                        invalidCorrelationId
                                )
                )
                .andExpect(
                        header().exists(
                                CorrelationIdFilter.HEADER_NAME
                        )
                )
                .andReturn();

        String generatedCorrelationId =
                result.getResponse().getHeader(
                        CorrelationIdFilter.HEADER_NAME
                );

        assertThat(generatedCorrelationId)
                .isNotEqualTo(invalidCorrelationId);

        assertThat(
                UUID.fromString(generatedCorrelationId)
        ).isNotNull();
    }
}