package com.omnia.backend.service.impl;

import com.omnia.backend.common.exception.ResourceNotFoundException;
import com.omnia.backend.dto.response.OrganizationResponse;
import com.omnia.backend.entity.Organization;
import com.omnia.backend.entity.UploadedFile;
import com.omnia.backend.entity.User;
import com.omnia.backend.mapper.OrganizationMapper;
import com.omnia.backend.repository.OrganizationRepository;
import com.omnia.backend.repository.UploadedFileRepository;
import com.omnia.backend.security.service.CurrentUserService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationLogoService {
    private final OrganizationRepository organizations;
    private final UploadedFileRepository files;
    private final CurrentUserService users;
    private final OrganizationMapper mapper;
    public OrganizationLogoService(OrganizationRepository organizations, UploadedFileRepository files,
            CurrentUserService users, OrganizationMapper mapper) {
        this.organizations = organizations;
        this.files = files;
        this.users = users;
        this.mapper = mapper;
    }
    @Transactional
    public OrganizationResponse setLogo(Long organizationId, Long fileId) {
        User current = users.requireCurrentUser();
        if (!users.hasPlatformAdminAccess(current))
            throw new AccessDeniedException("Platform administrator access is required");
        if (organizationId == null || organizationId <= 0 || fileId == null || fileId <= 0)
            throw new IllegalArgumentException("Organization and uploaded file IDs must be positive");
        Organization organization = organizations.findById(organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found"));
        UploadedFile file = files.findById(fileId)
                .orElseThrow(() -> new ResourceNotFoundException("Uploaded file not found"));
        if (file.getContentType() == null || !java.util.Set.of("image/jpeg", "image/png", "image/webp").contains(file.getContentType()))
            throw new IllegalArgumentException("Company logo must be an image");
        organization.setLogoFile(file);
        return mapper.toResponse(organizations.saveAndFlush(organization));
    }
}
