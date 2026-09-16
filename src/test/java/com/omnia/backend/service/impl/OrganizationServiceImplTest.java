package com.omnia.backend.service.impl;

import com.omnia.backend.dto.response
        .OrganizationCatalogResponse;
import com.omnia.backend.entity.Organization;
import com.omnia.backend.enums.OrganizationStatus;
import com.omnia.backend.mapper.OrganizationMapper;
import com.omnia.backend.repository
        .OrganizationMemberRepository;
import com.omnia.backend.repository
        .OrganizationRepository;
import com.omnia.backend.security.service
        .CurrentUserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrganizationServiceImplTest {

    @Mock
    private OrganizationRepository
            organizationRepository;

    @Mock
    private OrganizationMemberRepository
            memberRepository;

    @Mock
    private OrganizationMapper organizationMapper;

    @Mock
    private CurrentUserService currentUserService;

    @InjectMocks
    private OrganizationServiceImpl organizationService;

    @Test
    void getCatalogOrganizations_ShouldReturnOnlyActiveOrganizations() {

        Organization organization =
                Organization.builder()
                        .id(10L)
                        .name("Omnia Store")
                        .slug("omnia-store")
                        .description("Dyqani Omnia")
                        .status(
                                OrganizationStatus.ACTIVE
                        )
                        .build();

        OrganizationCatalogResponse response =
                OrganizationCatalogResponse.builder()
                        .id(10L)
                        .name("Omnia Store")
                        .slug("omnia-store")
                        .description("Dyqani Omnia")
                        .build();

        when(
                organizationRepository
                        .findAllByStatusOrderByNameAsc(
                                OrganizationStatus.ACTIVE
                        )
        ).thenReturn(List.of(organization));

        when(
                organizationMapper
                        .toCatalogResponse(
                                organization
                        )
        ).thenReturn(response);

        List<OrganizationCatalogResponse> result =
                organizationService
                        .getCatalogOrganizations();

        assertEquals(1, result.size());
        assertSame(response, result.getFirst());

        verify(organizationRepository)
                .findAllByStatusOrderByNameAsc(
                        OrganizationStatus.ACTIVE
                );

        verify(organizationMapper)
                .toCatalogResponse(
                        organization
                );

        verifyNoInteractions(
                memberRepository,
                currentUserService
        );

        verifyNoMoreInteractions(
                organizationRepository,
                organizationMapper
        );
    }
}