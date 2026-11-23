package com.omnia.backend.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Chat endpoints never answer anonymous callers, even under the public /api/products/** paths. */
class ChatControllerIntegrationTest extends AbstractIntegrationTest {

    @Test
    void anonymousCallersAreRejected() throws Exception {
        mockMvc.perform(get("/api/conversations/mine")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/conversations/1/messages")).andExpect(status().isUnauthorized());
        // /api/products/** is public at the filter level; method security still refuses (403).
        mockMvc.perform(get("/api/products/1/conversation")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/products/1/conversation").contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"hi\"}")).andExpect(status().is4xxClientError());
    }
}
