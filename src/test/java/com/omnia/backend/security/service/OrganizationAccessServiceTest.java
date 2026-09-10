package com.omnia.backend.security.service;

import com.omnia.backend.entity.Organization;
import com.omnia.backend.entity.OrganizationCategoryPermission;
import com.omnia.backend.entity.OrganizationMember;
import com.omnia.backend.entity.User;
import com.omnia.backend.enums.OrganizationMemberStatus;
import com.omnia.backend.enums.OrganizationPermissionStatus;
import com.omnia.backend.repository.OrganizationCategoryPermissionRepository;
import com.omnia.backend.repository.OrganizationMemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrganizationAccessServiceTest {

    private static final long USER_ID = 5L;
    private static final long ORGANIZATION_ID = 10L;
    private static final long CATEGORY_ID = 20L;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private OrganizationMemberRepository memberRepository;

    @Mock
    private OrganizationCategoryPermissionRepository
            permissionRepository;

    @Mock
    private User currentUser;

    @Mock
    private OrganizationMember member;

    @Mock
    private Organization organization;

    @Mock
    private OrganizationCategoryPermission permission;

    @InjectMocks
    private OrganizationAccessService accessService;

    @Test
    void platformAdminCanCreateProductWithoutMembership() {
        when(currentUserService.requireCurrentUser())
                .thenReturn(currentUser);

        when(currentUserService.hasPlatformAdminAccess(currentUser))
                .thenReturn(true);

        assertDoesNotThrow(() ->
                accessService.requireCanCreateProduct(
                        ORGANIZATION_ID,
                        CATEGORY_ID
                )
        );

        verifyNoInteractions(
                memberRepository,
                permissionRepository
        );
    }

    @Test
    void productManagerCanCreateProductInAllowedCategory() {
        mockRegularUser();
        mockActiveProductManager();
        mockActiveCategoryPermission();

        when(permission.allowsCreate())
                .thenReturn(true);

        assertDoesNotThrow(() ->
                accessService.requireCanCreateProduct(
                        ORGANIZATION_ID,
                        CATEGORY_ID
                )
        );
    }

    @Test
    void userOutsideOrganizationCannotCreateProduct() {
        mockRegularUser();

        when(memberRepository
                .findByOrganizationIdAndUserIdAndStatus(
                        ORGANIZATION_ID,
                        USER_ID,
                        OrganizationMemberStatus.ACTIVE
                ))
                .thenReturn(Optional.empty());

        assertThrows(
                AccessDeniedException.class,
                () -> accessService.requireCanCreateProduct(
                        ORGANIZATION_ID,
                        CATEGORY_ID
                )
        );
    }

    @Test
    void productManagerCannotCreateInUnassignedCategory() {
        mockRegularUser();
        mockActiveProductManager();

        when(permissionRepository
                .findByOrganizationIdAndCategoryIdAndStatus(
                        ORGANIZATION_ID,
                        CATEGORY_ID,
                        OrganizationPermissionStatus.ACTIVE
                ))
                .thenReturn(Optional.empty());

        assertThrows(
                AccessDeniedException.class,
                () -> accessService.requireCanCreateProduct(
                        ORGANIZATION_ID,
                        CATEGORY_ID
                )
        );
    }

    @Test
    void productManagerCannotUpdateWhenPermissionIsDisabled() {
        mockRegularUser();
        mockActiveProductManager();
        mockActiveCategoryPermission();

        when(permission.allowsUpdate())
                .thenReturn(false);

        assertThrows(
                AccessDeniedException.class,
                () -> accessService.requireCanUpdateProduct(
                        ORGANIZATION_ID,
                        CATEGORY_ID
                )
        );
    }

    @Test
    void productManagerCanDeleteWhenPermissionIsEnabled() {
        mockRegularUser();
        mockActiveProductManager();
        mockActiveCategoryPermission();

        when(permission.allowsDelete())
                .thenReturn(true);

        assertDoesNotThrow(() ->
                accessService.requireCanDeleteProduct(
                        ORGANIZATION_ID,
                        CATEGORY_ID
                )
        );
    }

    @Test
    void inactiveOrganizationCannotManageProducts() {
        mockRegularUser();

        when(memberRepository
                .findByOrganizationIdAndUserIdAndStatus(
                        ORGANIZATION_ID,
                        USER_ID,
                        OrganizationMemberStatus.ACTIVE
                ))
                .thenReturn(Optional.of(member));

        when(member.getOrganization())
                .thenReturn(organization);

        when(organization.isActive())
                .thenReturn(false);

        assertThrows(
                AccessDeniedException.class,
                () -> accessService.requireCanCreateProduct(
                        ORGANIZATION_ID,
                        CATEGORY_ID
                )
        );
    }

    @Test
    void organizationOwnerCanManageMembers() {
        mockRegularUser();
        mockActiveMember();

        when(member.canManageMembers())
                .thenReturn(true);

        assertDoesNotThrow(() ->
                accessService.requireCanManageMembers(
                        ORGANIZATION_ID
                )
        );
    }

    @Test
    void nonOwnerCannotManageMembers() {
        mockRegularUser();
        mockActiveMember();

        when(member.canManageMembers())
                .thenReturn(false);

        assertThrows(
                AccessDeniedException.class,
                () -> accessService.requireCanManageMembers(
                        ORGANIZATION_ID
                )
        );
    }

    @Test
    void userOutsideOrganizationCannotAccessIt() {
        mockRegularUser();

        when(memberRepository
                .findByOrganizationIdAndUserIdAndStatus(
                        ORGANIZATION_ID,
                        USER_ID,
                        OrganizationMemberStatus.ACTIVE
                ))
                .thenReturn(Optional.empty());

        assertThrows(
                AccessDeniedException.class,
                () -> accessService.requireCanAccessOrganization(
                        ORGANIZATION_ID
                )
        );
    }

    private void mockRegularUser() {
        when(currentUserService.requireCurrentUser())
                .thenReturn(currentUser);

        when(currentUserService.hasPlatformAdminAccess(currentUser))
                .thenReturn(false);

        when(currentUser.getId())
                .thenReturn(USER_ID);
    }

    private void mockActiveMember() {
        when(memberRepository
                .findByOrganizationIdAndUserIdAndStatus(
                        ORGANIZATION_ID,
                        USER_ID,
                        OrganizationMemberStatus.ACTIVE
                ))
                .thenReturn(Optional.of(member));
    }

    private void mockActiveProductManager() {
        mockActiveMember();

        when(member.getOrganization())
                .thenReturn(organization);

        when(organization.isActive())
                .thenReturn(true);

        when(member.canManageProducts())
                .thenReturn(true);
    }

    private void mockActiveCategoryPermission() {
        when(permissionRepository
                .findByOrganizationIdAndCategoryIdAndStatus(
                        ORGANIZATION_ID,
                        CATEGORY_ID,
                        OrganizationPermissionStatus.ACTIVE
                ))
                .thenReturn(Optional.of(permission));
    }
}