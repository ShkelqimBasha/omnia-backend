package com.omnia.backend.security.service;

import com.omnia.backend.entity.OrganizationCategoryPermission;
import com.omnia.backend.entity.OrganizationMember;
import com.omnia.backend.entity.User;
import com.omnia.backend.enums.OrganizationMemberStatus;
import com.omnia.backend.enums.OrganizationPermissionStatus;
import com.omnia.backend.repository.OrganizationCategoryPermissionRepository;
import com.omnia.backend.repository.OrganizationMemberRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationAccessService {

    private final CurrentUserService currentUserService;

    private final OrganizationMemberRepository
            memberRepository;

    private final OrganizationCategoryPermissionRepository
            permissionRepository;

    public OrganizationAccessService(
            CurrentUserService currentUserService,
            OrganizationMemberRepository memberRepository,
            OrganizationCategoryPermissionRepository
                    permissionRepository
    ) {
        this.currentUserService = currentUserService;
        this.memberRepository = memberRepository;
        this.permissionRepository = permissionRepository;
    }

    @Transactional(readOnly = true)
    public void requireCanCreateProduct(
            Long organizationId,
            Long categoryId
    ) {
        User currentUser =
                currentUserService.requireCurrentUser();

        if (currentUserService.hasPlatformAdminAccess(
                currentUser
        )) {
            return;
        }

        requireProductManager(
                currentUser,
                organizationId
        );

        OrganizationCategoryPermission permission =
                requireCategoryPermission(
                        organizationId,
                        categoryId
                );

        if (!permission.allowsCreate()) {
            throw new AccessDeniedException(
                    "The organization cannot create "
                            + "products in this category"
            );
        }
    }

    @Transactional(readOnly = true)
    public void requireCanUpdateProduct(
            Long organizationId,
            Long categoryId
    ) {
        User currentUser =
                currentUserService.requireCurrentUser();

        if (currentUserService.hasPlatformAdminAccess(
                currentUser
        )) {
            return;
        }

        requireProductManager(
                currentUser,
                organizationId
        );

        OrganizationCategoryPermission permission =
                requireCategoryPermission(
                        organizationId,
                        categoryId
                );

        if (!permission.allowsUpdate()) {
            throw new AccessDeniedException(
                    "The organization cannot update "
                            + "products in this category"
            );
        }
    }

    @Transactional(readOnly = true)
    public void requireCanDeleteProduct(
            Long organizationId,
            Long categoryId
    ) {
        User currentUser =
                currentUserService.requireCurrentUser();

        if (currentUserService.hasPlatformAdminAccess(
                currentUser
        )) {
            return;
        }

        requireProductManager(
                currentUser,
                organizationId
        );

        OrganizationCategoryPermission permission =
                requireCategoryPermission(
                        organizationId,
                        categoryId
                );

        if (!permission.allowsDelete()) {
            throw new AccessDeniedException(
                    "The organization cannot delete "
                            + "products in this category"
            );
        }
    }

    @Transactional(readOnly = true)
    public void requireCanManageMembers(
            Long organizationId
    ) {
        User currentUser =
                currentUserService.requireCurrentUser();

        if (currentUserService.hasPlatformAdminAccess(
                currentUser
        )) {
            return;
        }

        OrganizationMember member =
                requireActiveMember(
                        currentUser,
                        organizationId
                );

        if (!member.canManageMembers()) {
            throw new AccessDeniedException(
                    "Only the organization owner can "
                            + "manage members"
            );
        }
    }
    @Transactional(readOnly = true)
    public void requireCanAccessOrganization(
            Long organizationId
    ) {
        validatePositiveId(
                organizationId,
                "Organization id"
        );

        User currentUser =
                currentUserService.requireCurrentUser();

        if (currentUserService.hasPlatformAdminAccess(
                currentUser
        )) {
            return;
        }

        requireActiveMember(
                currentUser,
                organizationId
        );
    }

    @Transactional(readOnly = true)
    public boolean hasOrganizationAdminAccess(
            User user
    ) {
        if (user == null || user.getId() == null) {
            return false;
        }

        if (currentUserService.hasPlatformAdminAccess(user)) {
            return true;
        }

        return memberRepository
                .findAllByUserIdAndStatusOrderByOrganizationNameAsc(
                        user.getId(),
                        OrganizationMemberStatus.ACTIVE
                )
                .stream()
                .anyMatch(OrganizationMember::canManageProducts);
    }

    private void requireProductManager(
            User currentUser,
            Long organizationId
    ) {
        OrganizationMember member =
                requireActiveMember(
                        currentUser,
                        organizationId
                );

        if (!member.getOrganization().isActive()) {
            throw new AccessDeniedException(
                    "Organization is not active"
            );
        }

        if (!member.canManageProducts()) {
            throw new AccessDeniedException(
                    "Product management permission is required"
            );
        }
    }

    private OrganizationMember requireActiveMember(
            User currentUser,
            Long organizationId
    ) {
        validatePositiveId(
                organizationId,
                "Organization id"
        );

        return memberRepository
                .findByOrganizationIdAndUserIdAndStatus(
                        organizationId,
                        currentUser.getId(),
                        OrganizationMemberStatus.ACTIVE
                )
                .orElseThrow(() ->
                        new AccessDeniedException(
                                "You are not an active member "
                                        + "of this organization"
                        )
                );
    }

    private OrganizationCategoryPermission
    requireCategoryPermission(
            Long organizationId,
            Long categoryId
    ) {
        validatePositiveId(
                categoryId,
                "Category id"
        );

        return permissionRepository
                .findByOrganizationIdAndCategoryIdAndStatus(
                        organizationId,
                        categoryId,
                        OrganizationPermissionStatus.ACTIVE
                )
                .orElseThrow(() ->
                        new AccessDeniedException(
                                "The category is not assigned "
                                        + "to this organization"
                        )
                );
    }

    private void validatePositiveId(
            Long id,
            String fieldName
    ) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException(
                    fieldName + " must be positive"
            );
        }
    }
}