package com.omnia.backend.service.impl;

import com.omnia.backend.common.exception.ResourceNotFoundException;
import com.omnia.backend.dto.request.OrganizationRequest;
import com.omnia.backend.entity.*;
import com.omnia.backend.enums.*;
import com.omnia.backend.mapper.OrganizationMapper;
import com.omnia.backend.repository.*;
import com.omnia.backend.security.service.CurrentUserService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrganizationCoverage35Test {
    OrganizationRepository organizations;OrganizationMemberRepository members;CurrentUserService current;
    OrganizationServiceImpl service;User admin;Organization org;
    @BeforeEach void setup() {
        organizations=mock(OrganizationRepository.class);members=mock(OrganizationMemberRepository.class);current=mock(CurrentUserService.class);
        service=new OrganizationServiceImpl(organizations,members,new OrganizationMapper(),current);
        admin=AdminUserCoverage35Test.user(1,"SUPER_ADMIN");when(current.requireCurrentUser()).thenReturn(admin);
        when(current.hasPlatformAdminAccess(admin)).thenReturn(true);
        org=Organization.builder().id(10L).name("Store").slug("original").status(OrganizationStatus.ACTIVE).build();
        when(organizations.findById(10L)).thenReturn(Optional.of(org));
        when(organizations.saveAndFlush(any())).thenAnswer(i->{Organization o=i.getArgument(0);if(o.getId()==null)o.setId(20L);return o;});
    }
    @Test void creationNormalizesNameAndDescriptionAndResolvesSlugCollisions() {
        when(organizations.existsBySlugIgnoreCase("shkelqim-store")).thenReturn(true);
        when(organizations.existsBySlugIgnoreCase("shkelqim-store-2")).thenReturn(true);
        var r=service.createOrganization(OrganizationRequest.builder().name("  Shkëlqim   Store ").description(" About ").build());
        assertEquals("Shkëlqim Store",r.getName());assertEquals("shkelqim-store-3",r.getSlug());assertEquals("About",r.getDescription());
        assertEquals("ACTIVE",r.getStatus());assertEquals(1L,r.getCreatedByUserId());
    }
    @Test void nameWithoutLatinSlugUsesFallback() {
        var r=service.createOrganization(OrganizationRequest.builder().name("店").build());assertEquals("organization",r.getSlug());
    }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings={"   "})
    void blankNameCannotCreateOrRenameCompany(String name) {
        var r=OrganizationRequest.builder().name(name).build();
        assertThrows(IllegalArgumentException.class,()->service.createOrganization(r));
        assertThrows(IllegalArgumentException.class,()->service.updateOrganization(10L,r));verify(organizations,never()).saveAndFlush(any());
    }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings={"   "})
    void blankDescriptionIsStoredAsNullAndRenameKeepsExistingLinks(String value) {
        var r=service.updateOrganization(10L,OrganizationRequest.builder().name(" New  Store ").description(value).build());
        assertEquals("New Store",r.getName());assertNull(r.getDescription());assertEquals("original",r.getSlug());
        verify(organizations,never()).existsBySlugIgnoreCase(anyString());
    }
    @Test void nonBlankDescriptionCanBeUpdated() {
        var r=service.updateOrganization(10L,OrganizationRequest.builder().name("Store").description(" New details ").build());
        assertEquals("New details",r.getDescription());
    }
    @Test void administratorSeesAllCompaniesInNameOrder() {
        when(organizations.findAll(any(Sort.class))).thenReturn(List.of(org));
        var result=service.getAccessibleOrganizations();assertEquals(10L,result.getFirst().getId());
        verify(organizations).findAll(Sort.by(Sort.Direction.ASC,"name"));verifyNoInteractions(members);
    }
    @Test void ordinaryUserSeesOnlyActiveMembershipCompanies() {
        when(current.hasPlatformAdminAccess(admin)).thenReturn(false);
        when(members.findAllByUserIdAndStatusOrderByOrganizationNameAsc(1L,OrganizationMemberStatus.ACTIVE))
                .thenReturn(List.of(OrganizationMember.builder().organization(org).build()));
        assertEquals(10L,service.getAccessibleOrganizations().getFirst().getId());
        verify(organizations,never()).findAll(any(Sort.class));
    }
    @Test void lookupNeedsMembershipForNonAdministrator() {
        assertEquals(10L,service.getOrganizationById(10L).getId());
        when(current.hasPlatformAdminAccess(admin)).thenReturn(false);
        assertThrows(AccessDeniedException.class,()->service.getOrganizationById(10L));
        when(members.findByOrganizationIdAndUserIdAndStatus(10L,1L,OrganizationMemberStatus.ACTIVE))
                .thenReturn(Optional.of(OrganizationMember.builder().organization(org).build()));
        assertEquals(10L,service.getOrganizationById(10L).getId());
    }
    @Test void ordinaryUserCannotCreateRenameOrDisableCompanies() {
        when(current.hasPlatformAdminAccess(admin)).thenReturn(false);
        var r=OrganizationRequest.builder().name("Store").build();
        assertThrows(AccessDeniedException.class,()->service.createOrganization(r));
        assertThrows(AccessDeniedException.class,()->service.updateOrganization(10L,r));
        assertThrows(AccessDeniedException.class,()->service.updateOrganizationStatus(10L,OrganizationStatus.ACTIVE));
        verify(organizations,never()).saveAndFlush(any());
    }
    @ParameterizedTest @EnumSource(OrganizationStatus.class)
    void statusTransitionsPersistRequestedState(OrganizationStatus status) {
        assertEquals(status.name(),service.updateOrganizationStatus(10L,status).getStatus());assertEquals(status,org.getStatus());
    }
    @Test void statusIsRequired() {
        assertThrows(IllegalArgumentException.class,()->service.updateOrganizationStatus(10L,null));verify(organizations,never()).saveAndFlush(any());
    }
    @ParameterizedTest @NullSource @ValueSource(longs={0,-1})
    void invalidIdsAreRejectedBeforeAccessOrQuery(Long id) {
        assertThrows(IllegalArgumentException.class,()->service.getOrganizationById(id));
        assertThrows(IllegalArgumentException.class,()->service.updateOrganization(id,new OrganizationRequest()));
        assertThrows(IllegalArgumentException.class,()->service.updateOrganizationStatus(id,OrganizationStatus.ACTIVE));
        verifyNoInteractions(current);
    }
    @Test void missingCompanyIsNotFound() { assertThrows(ResourceNotFoundException.class,()->service.getOrganizationById(99L)); }
}
