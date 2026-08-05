package com.omnia.backend.service.interfaces;

import com.omnia.backend.dto.request.OrganizationCategoryPermissionRequest;
import com.omnia.backend.dto.request.OrganizationCategoryPermissionUpdateRequest;
import com.omnia.backend.dto.response.OrganizationCategoryPermissionResponse;

import java.util.List;

public interface OrganizationCategoryPermissionService {

    OrganizationCategoryPermissionResponse
    assignCategory(
            Long organizationId,
            OrganizationCategoryPermissionRequest request
    );

    List<OrganizationCategoryPermissionResponse>
    getOrganizationCategories(
            Long organizationId
    );

    OrganizationCategoryPermissionResponse
    updatePermission(
            Long organizationId,
            Long permissionId,
            OrganizationCategoryPermissionUpdateRequest request
    );
}