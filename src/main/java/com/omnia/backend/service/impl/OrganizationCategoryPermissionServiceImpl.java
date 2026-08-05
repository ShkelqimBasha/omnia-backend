package com.omnia.backend.service.impl;

import com.omnia.backend.common.exception.ResourceAlreadyExistsException;
import com.omnia.backend.common.exception.ResourceNotFoundException;
import com.omnia.backend.dto.request.OrganizationCategoryPermissionRequest;
import com.omnia.backend.dto.request.OrganizationCategoryPermissionUpdateRequest;
import com.omnia.backend.dto.response.OrganizationCategoryPermissionResponse;
import com.omnia.backend.entity.Category;
import com.omnia.backend.entity.Organization;
import com.omnia.backend.entity.OrganizationCategoryPermission;
import com.omnia.backend.entity.User;
import com.omnia.backend.enums.CategoryStatus;
import com.omnia.backend.enums.OrganizationPermissionStatus;
import com.omnia.backend.mapper.OrganizationCategoryPermissionMapper;
import com.omnia.backend.repository.CategoryRepository;
import com.omnia.backend.repository.OrganizationCategoryPermissionRepository;
import com.omnia.backend.repository.OrganizationRepository;
import com.omnia.backend.security.service.CurrentUserService;
import com.omnia.backend.security.service.OrganizationAccessService;
import com.omnia.backend.service.interfaces.OrganizationCategoryPermissionService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
public class OrganizationCategoryPermissionServiceImpl
        implements OrganizationCategoryPermissionService {

    private final OrganizationRepository
            organizationRepository;

    private final CategoryRepository categoryRepository;

    private final OrganizationCategoryPermissionRepository
            permissionRepository;

    private final OrganizationCategoryPermissionMapper
            permissionMapper;

    private final CurrentUserService currentUserService;

    private final OrganizationAccessService accessService;

    public OrganizationCategoryPermissionServiceImpl(
            OrganizationRepository organizationRepository,
            CategoryRepository categoryRepository,
            OrganizationCategoryPermissionRepository
                    permissionRepository,
            OrganizationCategoryPermissionMapper
                    permissionMapper,
            CurrentUserService currentUserService,
            OrganizationAccessService accessService
    ) {
        this.organizationRepository =
                organizationRepository;
        this.categoryRepository = categoryRepository;
        this.permissionRepository =
                permissionRepository;
        this.permissionMapper = permissionMapper;
        this.currentUserService = currentUserService;
        this.accessService = accessService;
    }

    @Override
    @Transactional
    public OrganizationCategoryPermissionResponse
    assignCategory(
            Long organizationId,
            OrganizationCategoryPermissionRequest request
    ) {
        validatePositiveId(
                organizationId,
                "Organization id"
        );

        User currentUser =
                requirePlatformAdministrator();

        Organization organization =
                findOrganization(organizationId);

        Category category =
                categoryRepository
                        .findById(request.getCategoryId())
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Category not found"
                                )
                        );

        if (!CategoryStatus.ACTIVE.equals(
                category.getStatus()
        )) {
            throw new IllegalArgumentException(
                    "Only an active category can be "
                            + "assigned to an organization"
            );
        }

        if (permissionRepository
                .existsByOrganizationIdAndCategoryId(
                        organizationId,
                        category.getId()
                )) {
            throw new ResourceAlreadyExistsException(
                    "Category is already assigned "
                            + "to this organization"
            );
        }

        OrganizationCategoryPermission permission =
                OrganizationCategoryPermission.builder()
                        .organization(organization)
                        .category(category)
                        .canCreate(request.getCanCreate())
                        .canUpdate(request.getCanUpdate())
                        .canDelete(request.getCanDelete())
                        .status(
                                OrganizationPermissionStatus.ACTIVE
                        )
                        .grantedBy(currentUser)
                        .build();

        OrganizationCategoryPermission savedPermission =
                permissionRepository.saveAndFlush(
                        permission
                );

        return permissionMapper.toResponse(
                savedPermission
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrganizationCategoryPermissionResponse>
    getOrganizationCategories(
            Long organizationId
    ) {
        validatePositiveId(
                organizationId,
                "Organization id"
        );

        accessService.requireCanAccessOrganization(
                organizationId
        );

        findOrganization(organizationId);

        return permissionRepository
                .findAllByOrganizationIdOrderByCategoryNameAsc(
                        organizationId
                )
                .stream()
                .map(permissionMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public OrganizationCategoryPermissionResponse
    updatePermission(
            Long organizationId,
            Long permissionId,
            OrganizationCategoryPermissionUpdateRequest request
    ) {
        validatePositiveId(
                organizationId,
                "Organization id"
        );

        validatePositiveId(
                permissionId,
                "Permission id"
        );

        requirePlatformAdministrator();
        findOrganization(organizationId);

        OrganizationCategoryPermission permission =
                permissionRepository
                        .findById(permissionId)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Organization category "
                                                + "permission not found"
                                )
                        );

        if (!Objects.equals(
                permission.getOrganization().getId(),
                organizationId
        )) {
            throw new ResourceNotFoundException(
                    "Organization category permission "
                            + "not found"
            );
        }

        permission.setCanCreate(
                request.getCanCreate()
        );

        permission.setCanUpdate(
                request.getCanUpdate()
        );

        permission.setCanDelete(
                request.getCanDelete()
        );

        permission.setStatus(request.getStatus());

        OrganizationCategoryPermission savedPermission =
                permissionRepository.saveAndFlush(
                        permission
                );

        return permissionMapper.toResponse(
                savedPermission
        );
    }

    private User requirePlatformAdministrator() {

        User currentUser =
                currentUserService.requireCurrentUser();

        if (!currentUserService.hasPlatformAdminAccess(
                currentUser
        )) {
            throw new AccessDeniedException(
                    "Platform administrator access is required"
            );
        }

        return currentUser;
    }

    private Organization findOrganization(
            Long organizationId
    ) {
        return organizationRepository
                .findById(organizationId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Organization not found"
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