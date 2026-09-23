package com.omnia.backend.security.service;

import com.omnia.backend.entity.Organization;
import com.omnia.backend.entity.OrganizationMember;
import com.omnia.backend.entity.User;
import com.omnia.backend.enums.OrganizationMemberRole;
import com.omnia.backend.enums.OrganizationMemberStatus;
import com.omnia.backend.enums.OrganizationStatus;
import com.omnia.backend.repository.OrganizationCategoryPermissionRepository;
import com.omnia.backend.repository.OrganizationMemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrganizationCouponAccessTest {

    private static final long USER_ID = 5L;
    private static final long ORGANIZATION_ID = 10L;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private OrganizationMemberRepository memberRepository;

    @Mock
    private OrganizationCategoryPermissionRepository
            permissionRepository;

    private OrganizationAccessService accessService;
    private User currentUser;
    private Organization organization;

    @BeforeEach
    void setUp() {
        accessService = new OrganizationAccessService(
                currentUserService,
                memberRepository,
                permissionRepository
        );

        currentUser = User.builder()
                .id(USER_ID)
                .username("shkelqim")
                .email("shkelqim@example.com")
                .build();

        organization = Organization.builder()
                .id(ORGANIZATION_ID)
                .name("Omnia Store")
                .slug("omnia-store")
                .status(OrganizationStatus.ACTIVE)
                .build();
    }

    @Test
    void ownerAndAdmin_ShouldManageCoupons() {
        OrganizationMember owner =
                createMember(
                        OrganizationMemberRole.OWNER
                );

        OrganizationMember admin =
                createMember(
                        OrganizationMemberRole.ADMIN
                );

        OrganizationMember staff =
                createMember(
                        OrganizationMemberRole.STAFF
                );

        assertTrue(owner.canManageCoupons());
        assertTrue(admin.canManageCoupons());
        assertFalse(staff.canManageCoupons());
    }

    @Test
    void organizationAdmin_ShouldManageCoupons() {
        mockRegularUser();

        OrganizationMember admin =
                createMember(
                        OrganizationMemberRole.ADMIN
                );

        when(memberRepository
                .findByOrganizationIdAndUserIdAndStatus(
                        ORGANIZATION_ID,
                        USER_ID,
                        OrganizationMemberStatus.ACTIVE
                ))
                .thenReturn(Optional.of(admin));

        assertDoesNotThrow(
                () -> accessService
                        .requireCanManageCoupons(
                                ORGANIZATION_ID
                        )
        );
    }

    @Test
    void staff_ShouldNotManageCoupons() {
        mockRegularUser();

        OrganizationMember staff =
                createMember(
                        OrganizationMemberRole.STAFF
                );

        when(memberRepository
                .findByOrganizationIdAndUserIdAndStatus(
                        ORGANIZATION_ID,
                        USER_ID,
                        OrganizationMemberStatus.ACTIVE
                ))
                .thenReturn(Optional.of(staff));

        assertThrows(
                AccessDeniedException.class,
                () -> accessService
                        .requireCanManageCoupons(
                                ORGANIZATION_ID
                        )
        );
    }

    @Test
    void platformAdmin_ShouldManageWithoutMembership() {
        when(currentUserService
                .requireCurrentUser())
                .thenReturn(currentUser);

        when(currentUserService
                .hasPlatformAdminAccess(
                        currentUser
                ))
                .thenReturn(true);

        assertDoesNotThrow(
                () -> accessService
                        .requireCanManageCoupons(
                                ORGANIZATION_ID
                        )
        );

        verifyNoInteractions(memberRepository);
    }

    private void mockRegularUser() {
        when(currentUserService
                .requireCurrentUser())
                .thenReturn(currentUser);

        when(currentUserService
                .hasPlatformAdminAccess(
                        currentUser
                ))
                .thenReturn(false);
    }

    private OrganizationMember createMember(
            OrganizationMemberRole role
    ) {
        return OrganizationMember.builder()
                .organization(organization)
                .user(currentUser)
                .membershipRole(role)
                .status(
                        OrganizationMemberStatus.ACTIVE
                )
                .build();
    }
}