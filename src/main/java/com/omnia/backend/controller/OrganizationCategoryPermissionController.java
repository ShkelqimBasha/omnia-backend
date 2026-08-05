package com.omnia.backend.controller;

import com.omnia.backend.dto.request.OrganizationCategoryPermissionRequest;
import com.omnia.backend.dto.request.OrganizationCategoryPermissionUpdateRequest;
import com.omnia.backend.dto.response.OrganizationCategoryPermissionResponse;
import com.omnia.backend.service.interfaces.OrganizationCategoryPermissionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(
        "/api/organizations/{organizationId}/categories"
)
@Validated
@PreAuthorize("isAuthenticated()")
public class OrganizationCategoryPermissionController {

    private final OrganizationCategoryPermissionService
            permissionService;

    public OrganizationCategoryPermissionController(
            OrganizationCategoryPermissionService
                    permissionService
    ) {
        this.permissionService = permissionService;
    }

    @PostMapping
    public ResponseEntity<
            OrganizationCategoryPermissionResponse
            >
    assignCategory(
            @PathVariable
            @Positive
            Long organizationId,

            @Valid
            @RequestBody
            OrganizationCategoryPermissionRequest request
    ) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(
                        permissionService.assignCategory(
                                organizationId,
                                request
                        )
                );
    }

    @GetMapping
    public ResponseEntity<
            List<OrganizationCategoryPermissionResponse>
            >
    getOrganizationCategories(
            @PathVariable
            @Positive
            Long organizationId
    ) {
        return ResponseEntity.ok(
                permissionService
                        .getOrganizationCategories(
                                organizationId
                        )
        );
    }

    @PutMapping("/{permissionId}")
    public ResponseEntity<
            OrganizationCategoryPermissionResponse
            >
    updatePermission(
            @PathVariable
            @Positive
            Long organizationId,

            @PathVariable
            @Positive
            Long permissionId,

            @Valid
            @RequestBody
            OrganizationCategoryPermissionUpdateRequest
                    request
    ) {
        return ResponseEntity.ok(
                permissionService.updatePermission(
                        organizationId,
                        permissionId,
                        request
                )
        );
    }
}