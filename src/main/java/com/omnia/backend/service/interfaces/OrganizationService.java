package com.omnia.backend.service.interfaces;

import com.omnia.backend.dto.request.OrganizationRequest;
import com.omnia.backend.dto.response.OrganizationResponse;
import com.omnia.backend.enums.OrganizationStatus;

import java.util.List;

public interface OrganizationService {

    OrganizationResponse createOrganization(
            OrganizationRequest request
    );

    List<OrganizationResponse>
    getAccessibleOrganizations();

    OrganizationResponse getOrganizationById(
            Long organizationId
    );

    OrganizationResponse updateOrganization(
            Long organizationId,
            OrganizationRequest request
    );

    OrganizationResponse updateOrganizationStatus(
            Long organizationId,
            OrganizationStatus status
    );
}