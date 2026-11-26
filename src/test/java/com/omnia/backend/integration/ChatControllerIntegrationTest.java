package com.omnia.backend.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;

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

    @Test
    @WithMockUser(username = "chat-not-admin@example.com", roles = "USER")
    void adminChatEndpointsRejectNonAdmins() throws Exception {
        mockMvc.perform(get("/api/admin/conversations")).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/conversations").contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":1,\"body\":\"hi\"}")).andExpect(status().isForbidden());
    }

    @Test
    void adminChatEndpointsRejectAnonymousCallers() throws Exception {
        mockMvc.perform(get("/api/admin/conversations")).andExpect(status().isUnauthorized());
    }
}
