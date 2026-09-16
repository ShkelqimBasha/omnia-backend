package com.omnia.backend.controller;

import com.omnia.backend.dto.response
        .OrganizationCatalogResponse;
import com.omnia.backend.service.interfaces
        .OrganizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup
        .MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request
        .MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result
        .MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result
        .MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result
        .MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OrganizationControllerTest {

    @Mock
    private OrganizationService organizationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {

        OrganizationController controller =
                new OrganizationController(
                        organizationService
                );

        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .build();
    }

    @Test
    void getCatalogOrganizations_ShouldReturnCatalog()
            throws Exception {

        OrganizationCatalogResponse organization =
                OrganizationCatalogResponse.builder()
                        .id(10L)
                        .name("Omnia Store")
                        .slug("omnia-store")
                        .description("Dyqani Omnia")
                        .build();

        when(
                organizationService
                        .getCatalogOrganizations()
        ).thenReturn(List.of(organization));

        mockMvc.perform(
                        get(
                                "/api/organizations/catalog"
                        )
                )
                .andExpect(status().isOk())
                .andExpect(
                        content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_JSON
                        )
                )
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(
                        jsonPath("$[0].id")
                                .value(10L)
                )
                .andExpect(
                        jsonPath("$[0].name")
                                .value("Omnia Store")
                )
                .andExpect(
                        jsonPath("$[0].slug")
                                .value("omnia-store")
                )
                .andExpect(
                        jsonPath("$[0].description")
                                .value("Dyqani Omnia")
                )
                .andExpect(
                        jsonPath("$[0].status")
                                .doesNotExist()
                )
                .andExpect(
                        jsonPath("$[0].createdByUserId")
                                .doesNotExist()
                )
                .andExpect(
                        jsonPath("$[0].createdAt")
                                .doesNotExist()
                )
                .andExpect(
                        jsonPath("$[0].updatedAt")
                                .doesNotExist()
                );

        verify(organizationService)
                .getCatalogOrganizations();

        verifyNoMoreInteractions(
                organizationService
        );
    }
}