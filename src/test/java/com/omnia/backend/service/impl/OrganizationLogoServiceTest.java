package com.omnia.backend.service.impl;

import com.omnia.backend.common.exception.ResourceNotFoundException;
import com.omnia.backend.entity.*;
import com.omnia.backend.enums.OrganizationStatus;
import com.omnia.backend.mapper.OrganizationMapper;
import com.omnia.backend.repository.OrganizationRepository;
import com.omnia.backend.repository.UploadedFileRepository;
import com.omnia.backend.security.service.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrganizationLogoServiceTest {
    OrganizationRepository organizations;
    UploadedFileRepository files;
    CurrentUserService users;
    OrganizationLogoService service;
    User user;
    Organization organization;
    UploadedFile file;
    @BeforeEach void setup() {
        organizations = mock(OrganizationRepository.class);
        files = mock(UploadedFileRepository.class);
        users = mock(CurrentUserService.class);
        user = User.builder().id(7L).build();
        organization = Organization.builder().id(2L).name("Omnia").status(OrganizationStatus.ACTIVE).build();
        file = UploadedFile.builder().id(9L).contentType("image/png").uploadedBy(user).build();
        service = new OrganizationLogoService(organizations, files, users, new OrganizationMapper());
        when(users.requireCurrentUser()).thenReturn(user);
        when(users.hasPlatformAdminAccess(user)).thenReturn(true);
        when(organizations.findById(2L)).thenReturn(Optional.of(organization));
        when(files.findById(9L)).thenReturn(Optional.of(file));
        when(organizations.saveAndFlush(organization)).thenReturn(organization);
    }
    @Test void savesUploadedLogoAndReturnsDownloadUrl() {
        assertEquals("/api/files/9", service.setLogo(2L,9L).getLogoUrl());
        assertSame(file, organization.getLogoFile());
        verify(organizations).saveAndFlush(organization);
    }
    @Test void replacesLogoWithoutChangingCompanyIdentity() {
        organization.setLogoFile(UploadedFile.builder().id(1L).build());
        service.setLogo(2L,9L);
        assertEquals("Omnia", organization.getName());
        assertEquals(2L, organization.getId());
        assertSame(file, organization.getLogoFile());
    }
    @Test void deniesNonPlatformAdministratorBeforeLoadingFiles() {
        when(users.hasPlatformAdminAccess(user)).thenReturn(false);
        assertThrows(AccessDeniedException.class, () -> service.setLogo(2L,9L));
        verifyNoInteractions(files, organizations);
    }
    @Test void rejectsNonRasterImageAndDoesNotSave() {
        file.setContentType("image/svg+xml");
        assertThrows(IllegalArgumentException.class, () -> service.setLogo(2L,9L));
        verify(organizations,never()).saveAndFlush(any());
    }
    @Test void missingUploadedFileDoesNotModifyLogo() {
        when(files.findById(9L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.setLogo(2L,9L));
        assertNull(organization.getLogoFile());
    }
    @Test void invalidIdentifiersAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.setLogo(0L,9L));
        assertThrows(IllegalArgumentException.class, () -> service.setLogo(2L,0L));
        verifyNoInteractions(files, organizations);
    }
}
