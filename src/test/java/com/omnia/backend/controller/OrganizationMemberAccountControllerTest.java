package com.omnia.backend.controller;

import com.omnia.backend.dto.response
        .OrganizationMemberAccountResponse;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request
        .MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result
        .MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result
        .MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OrganizationMemberAccountControllerTest {

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
    void createMemberAccount_WhenExistingUserNeedsConfirmation_ShouldReturnOk()
            throws Exception {

        OrganizationMemberAccountResponse response =
                OrganizationMemberAccountResponse
                        .builder()
                        .confirmationRequired(true)
                        .accountCreated(false)
                        .verificationEmailSent(false)
                        .message(
                                "Konfirmoni lidhjen me kompanine."
                        )
                        .build();

        when(
                memberService.createMemberAccount(
                        eq(ORGANIZATION_ID),
                        any()
                )
        ).thenReturn(response);

        mockMvc.perform(
                        post(
                                "/api/organizations/{organizationId}"
                                        + "/members/accounts",
                                ORGANIZATION_ID
                        )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "firstName": "Test",
                                          "lastName": "User",
                                          "username": "testuser",
                                          "email": "test@example.com",
                                          "password": "Password123!",
                                          "phone": "0690000000",
                                          "membershipRole": "STAFF",
                                          "attachExisting": false
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.confirmationRequired")
                                .value(true)
                )
                .andExpect(
                        jsonPath("$.accountCreated")
                                .value(false)
                );
    }

    @Test
    void createMemberAccount_WhenNewAccountCreated_ShouldReturnCreated()
            throws Exception {

        OrganizationMemberAccountResponse response =
                OrganizationMemberAccountResponse
                        .builder()
                        .confirmationRequired(false)
                        .accountCreated(true)
                        .verificationEmailSent(true)
                        .message(
                                "Llogaria dhe anetaresia u krijuan."
                        )
                        .build();

        when(
                memberService.createMemberAccount(
                        eq(ORGANIZATION_ID),
                        any()
                )
        ).thenReturn(response);

        mockMvc.perform(
                        post(
                                "/api/organizations/{organizationId}"
                                        + "/members/accounts",
                                ORGANIZATION_ID
                        )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "firstName": "New",
                                          "lastName": "Member",
                                          "username": "newmember",
                                          "email": "new@example.com",
                                          "password": "Password123!",
                                          "phone": "0690000001",
                                          "membershipRole": "STAFF",
                                          "attachExisting": false
                                        }
                                        """)
                )
                .andExpect(status().isCreated())
                .andExpect(
                        jsonPath("$.confirmationRequired")
                                .value(false)
                )
                .andExpect(
                        jsonPath("$.accountCreated")
                                .value(true)
                )
                .andExpect(
                        jsonPath("$.verificationEmailSent")
                                .value(true)
                );
    }
}