package com.omnia.backend.controller;

import com.omnia.backend.dto.response
        .OrganizationMemberCandidateResponse;
import com.omnia.backend.service.interfaces
        .OrganizationMemberService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
class OrganizationMemberControllerTest {

    private static final Long ORGANIZATION_ID = 10L;

    @Mock
    private OrganizationMemberService memberService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {

        OrganizationMemberController controller =
                new OrganizationMemberController(
                        memberService
                );

        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .build();
    }

    @Test
    void searchMemberCandidates_WithValidQuery_ShouldReturnCandidates()
            throws Exception {

        OrganizationMemberCandidateResponse candidate =
                OrganizationMemberCandidateResponse.builder()
                        .id(20L)
                        .firstName("Test")
                        .lastName("User")
                        .username("testuser")
                        .email("test@example.com")
                        .build();

        when(
                memberService.searchMemberCandidates(
                        ORGANIZATION_ID,
                        "test"
                )
        ).thenReturn(List.of(candidate));

        mockMvc.perform(
                        get(
                                "/api/organizations/{organizationId}"
                                        + "/members/candidates",
                                ORGANIZATION_ID
                        )
                                .param("query", "test")
                )
                .andExpect(status().isOk())
                .andExpect(
                        content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_JSON
                        )
                )
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(20L))
                .andExpect(
                        jsonPath("$[0].firstName")
                                .value("Test")
                )
                .andExpect(
                        jsonPath("$[0].lastName")
                                .value("User")
                )
                .andExpect(
                        jsonPath("$[0].username")
                                .value("testuser")
                )
                .andExpect(
                        jsonPath("$[0].email")
                                .value("test@example.com")
                );

        verify(memberService).searchMemberCandidates(
                ORGANIZATION_ID,
                "test"
        );

        verifyNoMoreInteractions(memberService);
    }



    @Test
    void searchMemberCandidates_WithoutQuery_ShouldReturnBadRequest()
            throws Exception {

        mockMvc.perform(
                        get(
                                "/api/organizations/{organizationId}"
                                        + "/members/candidates",
                                ORGANIZATION_ID
                        )
                )
                .andExpect(status().isBadRequest());

        verifyNoInteractions(memberService);
    }
}