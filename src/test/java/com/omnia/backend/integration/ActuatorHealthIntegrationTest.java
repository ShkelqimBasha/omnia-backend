package com.omnia.backend.integration;

import com.omnia.backend.common.filter.CorrelationIdFilter;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ActuatorHealthIntegrationTest
        extends AbstractIntegrationTest {

    @Test
    void health_ShouldBePublicAndUp()
            throws Exception {

        mockMvc.perform(
                        get("/actuator/health")
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.status")
                                .value("UP")
                )
                .andExpect(
                        header().exists(
                                CorrelationIdFilter.HEADER_NAME
                        )
                );
    }

    @Test
    void liveness_ShouldBePublicAndUp()
            throws Exception {

        mockMvc.perform(
                        get("/actuator/health/liveness")
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.status")
                                .value("UP")
                );
    }

    @Test
    void readiness_ShouldBePublicAndUp()
            throws Exception {

        mockMvc.perform(
                        get("/actuator/health/readiness")
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.status")
                                .value("UP")
                );
    }

    @Test
    void additionalLivenessPath_ShouldBeAvailable()
            throws Exception {

        mockMvc.perform(
                        get("/livez")
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.status")
                                .value("UP")
                );
    }

    @Test
    void additionalReadinessPath_ShouldBeAvailable()
            throws Exception {

        mockMvc.perform(
                        get("/readyz")
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.status")
                                .value("UP")
                );
    }

    @Test
    void info_WhenUnauthenticated_ShouldBeForbidden()
            throws Exception {

        mockMvc.perform(
                        get("/actuator/info")
                )
                .andExpect(status().isForbidden());
    }

    @Test
    void unexposedActuatorEndpoint_ShouldNotBePublic()
            throws Exception {

        mockMvc.perform(
                        get("/actuator/env")
                )
                .andExpect(status().isForbidden());
    }
}