package com.omnia.backend.mapper;

import com.omnia.backend.dto.response.OrganizationCatalogResponse;
import com.omnia.backend.dto.response.OrganizationResponse;
import com.omnia.backend.entity.Organization;
import org.springframework.stereotype.Component;

@Component
public class OrganizationMapper {

    public OrganizationResponse toResponse(
            Organization organization
    ) {
        if (organization == null) {
            throw new IllegalArgumentException(
                    "Organization must not be null"
            );
        }

        return OrganizationResponse.builder()
                .id(organization.getId())
                .logoUrl(organization.getLogoFile() == null ? null
                        : "/api/files/" + organization.getLogoFile().getId())
                .name(organization.getName())
                .slug(organization.getSlug())
                .description(
                        organization.getDescription()
                )
                .status(
                        organization.getStatus() == null
                                ? null
                                : organization.getStatus().name()
                )
                .createdByUserId(
                        organization.getCreatedBy() == null
                                ? null
                                : organization.getCreatedBy()
                                .getId()
                )
                .verified(organization.isVerified())
                .sellerType(sellerType(organization))
                .createdAt(organization.getCreatedAt())
                .updatedAt(organization.getUpdatedAt())
                .build();
    }

    public OrganizationCatalogResponse toCatalogResponse(
            Organization organization
    ) {
        if (organization == null) {
            throw new IllegalArgumentException(
                    "Organization must not be null"
            );
        }

        return OrganizationCatalogResponse.builder()
                .id(organization.getId())
                .logoUrl(organization.getLogoFile() == null ? null
                        : "/api/files/" + organization.getLogoFile().getId())
                .name(organization.getName())
                .slug(organization.getSlug())
                .description(
                        organization.getDescription()
                )
                .verified(organization.isVerified())
                .sellerType(sellerType(organization))
                .build();
    }

    static String sellerType(Organization organization) {
        return organization.getSellerType() == null
                ? com.omnia.backend.enums.SellerType.BUSINESS.name()
                : organization.getSellerType().name();
    }
}
