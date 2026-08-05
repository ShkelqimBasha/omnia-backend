package com.omnia.backend.mapper;

import com.omnia.backend.dto.response.OrganizationCategoryPermissionResponse;
import com.omnia.backend.entity.OrganizationCategoryPermission;
import org.springframework.stereotype.Component;

@Component
public class OrganizationCategoryPermissionMapper {

    public OrganizationCategoryPermissionResponse toResponse(
            OrganizationCategoryPermission permission
    ) {
        if (permission == null) {
            throw new IllegalArgumentException(
                    "Organization category permission "
                            + "must not be null"
            );
        }

        return OrganizationCategoryPermissionResponse
                .builder()
                .id(permission.getId())
                .organizationId(
                        permission.getOrganization().getId()
                )
                .organizationName(
                        permission.getOrganization().getName()
                )
                .categoryId(
                        permission.getCategory().getId()
                )
                .categoryName(
                        permission.getCategory().getName()
                )
                .canCreate(permission.getCanCreate())
                .canUpdate(permission.getCanUpdate())
                .canDelete(permission.getCanDelete())
                .status(
                        permission.getStatus() == null
                                ? null
                                : permission.getStatus().name()
                )
                .grantedByUserId(
                        permission.getGrantedBy() == null
                                ? null
                                : permission.getGrantedBy()
                                .getId()
                )
                .createdAt(permission.getCreatedAt())
                .updatedAt(permission.getUpdatedAt())
                .build();
    }
}