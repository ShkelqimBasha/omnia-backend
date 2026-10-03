package com.omnia.backend.controller;
import com.omnia.backend.dto.request.OrganizationLogoRequest;
import com.omnia.backend.dto.response.OrganizationResponse;
import com.omnia.backend.service.impl.OrganizationLogoService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/organizations")
@Validated
public class OrganizationLogoController {
    private final OrganizationLogoService service;
    public OrganizationLogoController(OrganizationLogoService service) { this.service = service; }
    @PutMapping("/{organizationId}/logo")
    @PreAuthorize("isAuthenticated()")
    public OrganizationResponse setLogo(@PathVariable @Positive Long organizationId,
            @Valid @RequestBody OrganizationLogoRequest request) {
        return service.setLogo(organizationId, request.uploadedFileId());
    }
}
