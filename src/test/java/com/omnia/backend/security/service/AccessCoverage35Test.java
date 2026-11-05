package com.omnia.backend.security.service;

import com.omnia.backend.common.exception.ResourceNotFoundException;
import com.omnia.backend.entity.*;
import com.omnia.backend.enums.*;
import com.omnia.backend.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccessCoverage35Test {
    CurrentUserService current;OrganizationMemberRepository members;OrganizationCategoryPermissionRepository permissions;OrganizationAccessService access;
    User user;Organization org;OrganizationMember member;OrganizationCategoryPermission permission;
    @BeforeEach void setup(){
        current=mock(CurrentUserService.class);members=mock(OrganizationMemberRepository.class);permissions=mock(OrganizationCategoryPermissionRepository.class);
        access=new OrganizationAccessService(current,members,permissions);user=User.builder().id(2L).build();
        org=Organization.builder().id(10L).status(OrganizationStatus.ACTIVE).build();
        member=OrganizationMember.builder().organization(org).user(user).membershipRole(OrganizationMemberRole.OWNER).status(OrganizationMemberStatus.ACTIVE).build();
        permission=OrganizationCategoryPermission.builder().status(OrganizationPermissionStatus.ACTIVE).canCreate(true).canUpdate(true).canDelete(true).build();
        when(current.requireCurrentUser()).thenReturn(user);
        when(members.findByOrganizationIdAndUserIdAndStatus(10L,2L,OrganizationMemberStatus.ACTIVE)).thenReturn(Optional.of(member));
        when(permissions.findByOrganizationIdAndCategoryIdAndStatus(10L,5L,OrganizationPermissionStatus.ACTIVE)).thenReturn(Optional.of(permission));
    }
    @AfterEach void cleanup(){SecurityContextHolder.clearContext();}
    void product(String action,Long orgId,Long category){switch(action){case "create"->access.requireCanCreateProduct(orgId,category);case "update"->access.requireCanUpdateProduct(orgId,category);default->access.requireCanDeleteProduct(orgId,category);}}
    void management(String action){switch(action){case "orders"->access.requireCanManageOrders(10L);case "coupons"->access.requireCanManageCoupons(10L);default->access.requireCanManageMembers(10L);}}
    @ParameterizedTest @ValueSource(strings={"create","update","delete"})
    void productPermissionsAreIndependentAndRequireActiveCompanyAndMembership(String action){
        assertDoesNotThrow(()->product(action,10L,5L));
        permission.setCanCreate(false);permission.setCanUpdate(false);permission.setCanDelete(false);assertThrows(AccessDeniedException.class,()->product(action,10L,5L));
        member.setMembershipRole(OrganizationMemberRole.STAFF);assertThrows(AccessDeniedException.class,()->product(action,10L,5L));
        org.setStatus(OrganizationStatus.INACTIVE);assertThrows(AccessDeniedException.class,()->product(action,10L,5L));
        when(current.hasPlatformAdminAccess(user)).thenReturn(true);assertDoesNotThrow(()->product(action,10L,5L));
    }
    @ParameterizedTest @ValueSource(strings={"orders","coupons","members"})
    void managementRequiresAppropriateRoleAndPlatformAdministratorCanOverride(String action){
        assertDoesNotThrow(()->management(action));member.setMembershipRole(OrganizationMemberRole.STAFF);assertThrows(AccessDeniedException.class,()->management(action));
        org.setStatus(OrganizationStatus.INACTIVE);if(!action.equals("members"))assertThrows(AccessDeniedException.class,()->management(action));
        when(current.hasPlatformAdminAccess(user)).thenReturn(true);assertDoesNotThrow(()->management(action));
    }
    @Test void missingCategoryPermissionOrMembershipCannotManageProducts(){
        when(permissions.findByOrganizationIdAndCategoryIdAndStatus(10L,5L,OrganizationPermissionStatus.ACTIVE)).thenReturn(Optional.empty());
        assertThrows(AccessDeniedException.class,()->product("create",10L,5L));
        when(members.findByOrganizationIdAndUserIdAndStatus(10L,2L,OrganizationMemberStatus.ACTIVE)).thenReturn(Optional.empty());
        assertThrows(AccessDeniedException.class,()->product("create",10L,5L));assertThrows(AccessDeniedException.class,()->access.requireCanAccessOrganization(10L));
    }
    @ParameterizedTest @NullSource @ValueSource(longs={0,-1})
    void invalidOrganizationAndCategoryAreRejected(Long id){
        assertThrows(IllegalArgumentException.class,()->access.requireCanAccessOrganization(id));
        assertThrows(IllegalArgumentException.class,()->product("create",10L,id));
        assertThrows(IllegalArgumentException.class,()->product("create",id,5L));
    }
    @Test void organizationAdminFlagNeedsSavedIdentityAndManagementMembership(){
        assertFalse(access.hasOrganizationAdminAccess(null));assertFalse(access.hasOrganizationAdminAccess(new User()));
        when(members.findAllByUserIdAndStatusOrderByOrganizationNameAsc(2L,OrganizationMemberStatus.ACTIVE)).thenReturn(List.of(member));
        assertTrue(access.hasOrganizationAdminAccess(user));member.setMembershipRole(OrganizationMemberRole.STAFF);assertFalse(access.hasOrganizationAdminAccess(user));
        when(current.hasPlatformAdminAccess(user)).thenReturn(true);assertTrue(access.hasOrganizationAdminAccess(user));assertDoesNotThrow(()->access.requireCanAccessOrganization(10L));
    }
    @Test void inactiveOrNullableCategoryPermissionsAlwaysDeny(){
        for(var status:OrganizationPermissionStatus.values())for(Boolean flag:new Boolean[]{null,false,true}){
            var p=OrganizationCategoryPermission.builder().status(status).canCreate(flag).canUpdate(flag).canDelete(flag).build();
            boolean expected=status==OrganizationPermissionStatus.ACTIVE&&Boolean.TRUE.equals(flag);
            assertEquals(expected,p.allowsCreate());assertEquals(expected,p.allowsUpdate());assertEquals(expected,p.allowsDelete());
        }
        permission.setStatus(null);assertFalse(permission.allowsCreate());assertFalse(permission.allowsUpdate());assertFalse(permission.allowsDelete());
    }
    @Test void currentUserRequiresAuthenticationAndFallsBackFromEmailToUsername(){
        var users=mock(UserRepository.class);var service=new CurrentUserService(users);
        assertTrue(service.findCurrentUser().isEmpty());assertThrows(AccessDeniedException.class,service::requireCurrentUser);assertFalse(service.hasCurrentPlatformAdminAuthority());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("anonymousUser","",List.of()));assertTrue(service.findCurrentUser().isEmpty());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("user",""));assertThrows(AccessDeniedException.class,service::requireCurrentUser);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(" Buyer ","",List.of()));
        assertThrows(ResourceNotFoundException.class,service::requireCurrentUser);when(users.findByUsername("Buyer")).thenReturn(Optional.of(user));assertSame(user,service.requireCurrentUser());
        when(users.findByEmail("buyer")).thenReturn(Optional.of(user));assertSame(user,service.requireCurrentUser());
    }
    @ParameterizedTest @NullSource @ValueSource(strings={"USER"," admin ","super_admin","unknown"})
    void platformAdministrationIsExplicitAndCaseInsensitive(String role){
        var users=mock(UserRepository.class);var service=new CurrentUserService(users);assertFalse(service.hasPlatformAdminAccess(null));assertFalse(service.hasPlatformAdminAccess(new User()));
        user.setRole(Role.builder().name(role).build());boolean expected=role!=null&&Set.of("ADMIN","SUPER_ADMIN").contains(role.trim().toUpperCase(Locale.ROOT));
        assertEquals(expected,service.hasPlatformAdminAccess(user));
    }
    @Test void adminAuthorityAndStoredAdminRoleAreSupportedWithoutGrantingRegularUsers(){
        var users=mock(UserRepository.class);var service=new CurrentUserService(users);
        assertThrows(AccessDeniedException.class,service::requirePlatformAdmin);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("buyer","",List.of(new SimpleGrantedAuthority("role_admin"))));
        assertTrue(service.hasCurrentPlatformAdminAuthority());assertDoesNotThrow(service::requirePlatformAdmin);verifyNoInteractions(users);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("buyer","",List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        assertThrows(AccessDeniedException.class,service::requirePlatformAdmin);user.setRole(Role.builder().name("ADMIN").build());when(users.findByEmail("buyer")).thenReturn(Optional.of(user));assertDoesNotThrow(service::requirePlatformAdmin);
    }
}
