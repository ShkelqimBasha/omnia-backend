package com.omnia.backend.repository;

import com.omnia.backend.entity.OrganizationCategoryPermission;
import com.omnia.backend.enums.OrganizationPermissionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrganizationCategoryPermissionRepository
        extends JpaRepository<
        OrganizationCategoryPermission,
        Long
        > {

    Optional<OrganizationCategoryPermission>
    findByOrganizationIdAndCategoryId(
            Long organizationId,
            Long categoryId
    );

    Optional<OrganizationCategoryPermission>
    findByOrganizationIdAndCategoryIdAndStatus(
            Long organizationId,
            Long categoryId,
            OrganizationPermissionStatus status
    );

    List<OrganizationCategoryPermission>
    findAllByOrganizationIdOrderByCategoryNameAsc(
            Long organizationId
    );

    List<OrganizationCategoryPermission>
    findAllByOrganizationIdAndStatusOrderByCategoryNameAsc(
            Long organizationId,
            OrganizationPermissionStatus status
    );

    boolean existsByOrganizationIdAndCategoryId(
            Long organizationId,
            Long categoryId
    );
}