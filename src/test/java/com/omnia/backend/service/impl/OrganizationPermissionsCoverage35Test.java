package com.omnia.backend.service.impl;

import com.omnia.backend.common.exception.*;
import com.omnia.backend.dto.request.*;
import com.omnia.backend.entity.*;
import com.omnia.backend.enums.*;
import com.omnia.backend.mapper.OrganizationCategoryPermissionMapper;
import com.omnia.backend.repository.*;
import com.omnia.backend.security.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.security.access.AccessDeniedException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrganizationPermissionsCoverage35Test {
    OrganizationRepository organizations;CategoryRepository categories;OrganizationCategoryPermissionRepository permissions;
    CurrentUserService current;OrganizationAccessService access;OrganizationCategoryPermissionServiceImpl service;
    Organization org;Category category;User admin;OrganizationCategoryPermission permission;
    @BeforeEach void setup() {
        organizations=mock(OrganizationRepository.class);categories=mock(CategoryRepository.class);permissions=mock(OrganizationCategoryPermissionRepository.class);
        current=mock(CurrentUserService.class);access=mock(OrganizationAccessService.class);
        service=new OrganizationCategoryPermissionServiceImpl(organizations,categories,permissions,new OrganizationCategoryPermissionMapper(),current,access);
        org=Organization.builder().id(10L).name("Store").build();category=Category.builder().id(5L).name("Clothes").status(CategoryStatus.ACTIVE).build();
        admin=AdminUserCoverage35Test.user(1,"SUPER_ADMIN");when(current.requireCurrentUser()).thenReturn(admin);when(current.hasPlatformAdminAccess(admin)).thenReturn(true);
        when(organizations.findById(10L)).thenReturn(Optional.of(org));when(categories.findById(5L)).thenReturn(Optional.of(category));
        permission=OrganizationCategoryPermission.builder().id(7L).organization(org).category(category).canCreate(true).canUpdate(true).canDelete(false)
                .status(OrganizationPermissionStatus.ACTIVE).grantedBy(admin).build();
        when(permissions.findById(7L)).thenReturn(Optional.of(permission));
        when(permissions.saveAndFlush(any())).thenAnswer(i->i.getArgument(0));
    }
    OrganizationCategoryPermissionRequest request() {
        return OrganizationCategoryPermissionRequest.builder().categoryId(5L).canCreate(true).canUpdate(false).canDelete(false).build();
    }
    OrganizationCategoryPermissionUpdateRequest update() {
        return OrganizationCategoryPermissionUpdateRequest.builder().canCreate(false).canUpdate(true).canDelete(true).status(OrganizationPermissionStatus.ACTIVE).build();
    }
    @Test void assignmentPersistsExactPermissionsAndGrantingAdministrator() {
        var r=service.assignCategory(10L,request());assertEquals(10L,r.getOrganizationId());assertEquals(5L,r.getCategoryId());
        assertTrue(r.getCanCreate());assertFalse(r.getCanUpdate());assertFalse(r.getCanDelete());assertEquals(1L,r.getGrantedByUserId());assertEquals("ACTIVE",r.getStatus());
    }
    @Test void duplicateAssignmentIsRejectedWithoutWrite() {
        when(permissions.existsByOrganizationIdAndCategoryId(10L,5L)).thenReturn(true);
        assertThrows(ResourceAlreadyExistsException.class,()->service.assignCategory(10L,request()));verify(permissions,never()).saveAndFlush(any());
    }
    @Test void inactiveCategoryCannotBeAssigned() {
        category.setStatus(null);assertThrows(IllegalArgumentException.class,()->service.assignCategory(10L,request()));verify(permissions,never()).saveAndFlush(any());
    }
    @Test void missingCategoryAndOrganizationAreRejected() {
        when(categories.findById(5L)).thenReturn(Optional.empty());assertThrows(ResourceNotFoundException.class,()->service.assignCategory(10L,request()));
        assertThrows(ResourceNotFoundException.class,()->service.assignCategory(99L,request()));
    }
    @Test void assignmentsAndUpdatesRequirePlatformAdministrator() {
        when(current.hasPlatformAdminAccess(admin)).thenReturn(false);
        assertThrows(AccessDeniedException.class,()->service.assignCategory(10L,request()));
        assertThrows(AccessDeniedException.class,()->service.updatePermission(10L,7L,update()));verify(permissions,never()).saveAndFlush(any());
    }
    @Test void listChecksOrganizationAccessAndMapsEachCategory() {
        when(permissions.findAllByOrganizationIdOrderByCategoryNameAsc(10L)).thenReturn(List.of(permission));
        var list=service.getOrganizationCategories(10L);assertEquals("Clothes",list.getFirst().getCategoryName());
        verify(access).requireCanAccessOrganization(10L);
    }
    @Test void deniedListNeverQueriesPermissions() {
        doThrow(new AccessDeniedException("denied")).when(access).requireCanAccessOrganization(10L);
        assertThrows(AccessDeniedException.class,()->service.getOrganizationCategories(10L));
        verify(permissions,never()).findAllByOrganizationIdOrderByCategoryNameAsc(anyLong());
    }
    @Test void updatesPersistAllSwitches() {
        var r=service.updatePermission(10L,7L,update());assertFalse(r.getCanCreate());assertTrue(r.getCanUpdate());assertTrue(r.getCanDelete());
        verify(permissions).saveAndFlush(permission);
    }
    @Test void permissionOfAnotherCompanyCannotBeModified() {
        permission.setOrganization(Organization.builder().id(20L).build());
        assertThrows(ResourceNotFoundException.class,()->service.updatePermission(10L,7L,update()));verify(permissions,never()).saveAndFlush(any());
    }
    @Test void unknownPermissionCannotBeModified() { assertThrows(ResourceNotFoundException.class,()->service.updatePermission(10L,99L,update())); }
    @ParameterizedTest @NullSource @ValueSource(longs={0,-1})
    void invalidIdsAreRejected(Long id) {
        assertThrows(IllegalArgumentException.class,()->service.assignCategory(id,request()));
        assertThrows(IllegalArgumentException.class,()->service.getOrganizationCategories(id));
        assertThrows(IllegalArgumentException.class,()->service.updatePermission(10L,id,update()));
        assertThrows(IllegalArgumentException.class,()->service.updatePermission(id,7L,update()));
    }
}
