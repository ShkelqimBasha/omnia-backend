package com.omnia.backend.service.impl;

import com.omnia.backend.dto.response.OrganizationMemberCandidateResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MemberCandidateEmailTest {

    private static OrganizationMemberCandidateResponse candidate() {
        return OrganizationMemberCandidateResponse.builder()
                .id(1L).username("arta").email("arta.k@example.com").build();
    }

    @Test
    void foundByUsernameHidesTheEmail() {
        assertThat(OrganizationMemberServiceImpl.hideUnaskedEmail(candidate(), "ARTA").getEmail())
                .isEqualTo("a***@example.com");
    }

    @Test
    void foundByEmailKeepsTheEmailTheOwnerTyped() {
        assertThat(OrganizationMemberServiceImpl.hideUnaskedEmail(candidate(), "Arta.K@example.com").getEmail())
                .isEqualTo("arta.k@example.com");
    }
}
