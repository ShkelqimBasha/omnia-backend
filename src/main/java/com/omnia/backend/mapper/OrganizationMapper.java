package com.omnia.backend.mapper;

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
                .createdAt(organization.getCreatedAt())
                .updatedAt(organization.getUpdatedAt())
                .build();
    }
}