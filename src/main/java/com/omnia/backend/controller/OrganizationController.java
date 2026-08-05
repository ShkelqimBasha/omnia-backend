package com.omnia.backend.controller;

import com.omnia.backend.dto.request.OrganizationRequest;
import com.omnia.backend.dto.response.OrganizationResponse;
import com.omnia.backend.enums.OrganizationStatus;
import com.omnia.backend.service.interfaces.OrganizationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/organizations")
@Validated
public class OrganizationController {

    private final OrganizationService organizationService;

    public OrganizationController(
            OrganizationService organizationService
    ) {
        this.organizationService = organizationService;
    }

    @PostMapping
    @PreAuthorize(
            "hasAnyRole('SUPER_ADMIN', 'ADMIN')"
    )
    public ResponseEntity<OrganizationResponse>
    createOrganization(
            @Valid
            @RequestBody
            OrganizationRequest request
    ) {
        OrganizationResponse response =
                organizationService
                        .createOrganization(request);

        URI location = URI.create(
                "/api/organizations/"
                        + response.getId()
        );

        return ResponseEntity
                .created(location)
                .body(response);
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<OrganizationResponse>>
    getAccessibleOrganizations() {

        return ResponseEntity.ok(
                organizationService
                        .getAccessibleOrganizations()
        );
    }

    @GetMapping("/{organizationId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<OrganizationResponse>
    getOrganizationById(
            @PathVariable
            @Positive
            Long organizationId
    ) {
        return ResponseEntity.ok(
                organizationService
                        .getOrganizationById(
                                organizationId
                        )
        );
    }

    @PutMapping("/{organizationId}")
    @PreAuthorize(
            "hasAnyRole('SUPER_ADMIN', 'ADMIN')"
    )
    public ResponseEntity<OrganizationResponse>
    updateOrganization(
            @PathVariable
            @Positive
            Long organizationId,

            @Valid
            @RequestBody
            OrganizationRequest request
    ) {
        return ResponseEntity.ok(
                organizationService
                        .updateOrganization(
                                organizationId,
                                request
                        )
        );
    }

    @PatchMapping("/{organizationId}/status")
    @PreAuthorize(
            "hasAnyRole('SUPER_ADMIN', 'ADMIN')"
    )
    public ResponseEntity<OrganizationResponse>
    updateOrganizationStatus(
            @PathVariable
            @Positive
            Long organizationId,

            @RequestParam
            OrganizationStatus status
    ) {
        return ResponseEntity.ok(
                organizationService
                        .updateOrganizationStatus(
                                organizationId,
                                status
                        )
        );
    }
}