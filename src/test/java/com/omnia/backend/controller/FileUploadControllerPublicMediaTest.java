package com.omnia.backend.controller;

import com.omnia.backend.entity.UploadedFile;
import com.omnia.backend.service.interfaces.FileStorageService;
import com.omnia.backend.service.interfaces.PublicCatalogMediaService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class FileUploadControllerPublicMediaTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G'};

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private PublicCatalogMediaService publicCatalogMediaService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(
                        new FileUploadController(
                                fileStorageService,
                                publicCatalogMediaService
                        )
                )
                .build();
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void guest_CanDownloadActiveCatalogPhoto() throws Exception {
        signInAsGuest();
        when(publicCatalogMediaService.isPublicCatalogFile(5L)).thenReturn(true);
        stubFile(5L);

        mockMvc.perform(get("/api/files/5"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(PNG));
    }

    @Test
    void guest_CannotDownloadNonCatalogFile() throws Exception {
        signInAsGuest();
        when(publicCatalogMediaService.isPublicCatalogFile(7L)).thenReturn(false);

        mockMvc.perform(get("/api/files/7"))
                .andExpect(status().isUnauthorized());

        verify(fileStorageService, never()).loadFileAsResource(anyLong());
    }

    @Test
    void noAuthentication_IsTreatedAsGuest() throws Exception {
        SecurityContextHolder.clearContext();
        when(publicCatalogMediaService.isPublicCatalogFile(7L)).thenReturn(false);

        mockMvc.perform(get("/api/files/7"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void signedInUser_KeepsExistingAccessWithoutCatalogCheck() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "buyer@example.com",
                        null,
                        AuthorityUtils.createAuthorityList("ROLE_USER")
                )
        );
        stubFile(9L);

        mockMvc.perform(get("/api/files/9"))
                .andExpect(status().isOk());

        verify(publicCatalogMediaService, never()).isPublicCatalogFile(anyLong());
    }

    private static void signInAsGuest() {
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken(
                        "guest",
                        "anonymousUser",
                        AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")
                )
        );
    }

    private void stubFile(Long id) {
        UploadedFile file = new UploadedFile();
        file.setId(id);
        file.setOriginalName("photo.png");
        file.setContentType("image/png");
        file.setSize((long) PNG.length);
        when(fileStorageService.getFileMetadata(id)).thenReturn(file);
        when(fileStorageService.loadFileAsResource(id)).thenReturn(new ByteArrayResource(PNG));
    }
}
