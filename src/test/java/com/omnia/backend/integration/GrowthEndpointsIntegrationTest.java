package com.omnia.backend.integration;

import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithMockUser;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GrowthEndpointsIntegrationTest extends AbstractIntegrationTest {

    @Test
    void seoFacetsArePublic() throws Exception {
        mockMvc.perform(get("/api/products/seo-facets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void campaignsRequireAPlatformAdmin() throws Exception {
        mockMvc.perform(get("/api/admin/push-campaigns")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "buyer-growth", roles = "USER")
    void buyersCannotReachCampaigns() throws Exception {
        mockMvc.perform(post("/api/admin/push-campaigns/audience").contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void guestsCanCountAViewWithoutRecordingACategory() throws Exception {
        // One endpoint for both: the seller's view statistics (guests too) and, when signed in, the category view.
        mockMvc.perform(post("/api/products/1/view")).andExpect(status().isNoContent());
    }
}
