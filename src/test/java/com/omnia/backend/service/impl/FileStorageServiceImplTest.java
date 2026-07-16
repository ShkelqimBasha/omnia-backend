package com.omnia.backend.service.impl;

import com.omnia.backend.common.exception.InvalidFileException;
import com.omnia.backend.common.exception.ResourceNotFoundException;
import com.omnia.backend.entity.UploadedFile;
import com.omnia.backend.entity.User;
import com.omnia.backend.repository.UploadedFileRepository;
import com.omnia.backend.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileStorageServiceImplTest {

    @Mock
    private UploadedFileRepository uploadedFileRepository;

    @Mock
    private UserRepository userRepository;

    @TempDir
    Path tempDirectory;

    private FileStorageServiceImpl fileStorageService;
    private User user;

    @BeforeEach
    void setUp() {

        fileStorageService = new FileStorageServiceImpl(
                uploadedFileRepository,
                userRepository,
                tempDirectory.toString()
        );

        user = User.builder()
                .id(1L)
                .username("shkelqim")
                .email("shkelqim@example.com")
                .build();

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        "shkelqim@example.com",
                        null,
                        AuthorityUtils.NO_AUTHORITIES
                );

        SecurityContextHolder
                .getContext()
                .setAuthentication(authentication);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void storeFile_shouldStoreJpegSuccessfully() throws Exception {

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "photo.jpg",
                "image/jpeg",
                "jpeg-content".getBytes()
        );

        when(userRepository.findByEmail(
                "shkelqim@example.com"
        )).thenReturn(Optional.of(user));

        when(uploadedFileRepository.save(
                any(UploadedFile.class)
        )).thenAnswer(invocation -> {
            UploadedFile uploadedFile =
                    invocation.getArgument(0);
            uploadedFile.setId(10L);
            return uploadedFile;
        });

        UploadedFile result =
                fileStorageService.storeFile(file);

        assertNotNull(result);
        assertEquals(10L, result.getId());
        assertEquals("photo.jpg", result.getOriginalName());
        assertEquals("image/jpeg", result.getContentType());
        assertEquals(file.getSize(), result.getSize());
        assertEquals(user, result.getUploadedBy());
        assertNotNull(result.getUploadedAt());
        assertTrue(result.getStoredName().endsWith(".jpg"));

        Path storedPath = Path.of(result.getPath());

        assertTrue(Files.exists(storedPath));
        assertArrayEquals(
                "jpeg-content".getBytes(),
                Files.readAllBytes(storedPath)
        );
    }

    @Test
    void storeFile_shouldStorePngSuccessfully() {

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "photo.png",
                "image/png",
                "png-content".getBytes()
        );

        when(userRepository.findByEmail(
                "shkelqim@example.com"
        )).thenReturn(Optional.of(user));

        when(uploadedFileRepository.save(
                any(UploadedFile.class)
        )).thenAnswer(invocation ->
                invocation.getArgument(0)
        );

        UploadedFile result =
                fileStorageService.storeFile(file);

        assertTrue(result.getStoredName().endsWith(".png"));
        assertEquals("image/png", result.getContentType());
    }

    @Test
    void storeFile_shouldStoreWebpSuccessfully() {

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "photo.webp",
                "image/webp",
                "webp-content".getBytes()
        );

        when(userRepository.findByEmail(
                "shkelqim@example.com"
        )).thenReturn(Optional.of(user));

        when(uploadedFileRepository.save(
                any(UploadedFile.class)
        )).thenAnswer(invocation ->
                invocation.getArgument(0)
        );

        UploadedFile result =
                fileStorageService.storeFile(file);

        assertTrue(result.getStoredName().endsWith(".webp"));
        assertEquals("image/webp", result.getContentType());
    }

    @Test
    void storeFile_shouldNormalizeUppercaseExtension() {

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "PHOTO.JPEG",
                "image/jpeg",
                "content".getBytes()
        );

        when(userRepository.findByEmail(
                "shkelqim@example.com"
        )).thenReturn(Optional.of(user));

        when(uploadedFileRepository.save(
                any(UploadedFile.class)
        )).thenAnswer(invocation ->
                invocation.getArgument(0)
        );

        UploadedFile result =
                fileStorageService.storeFile(file);

        assertTrue(result.getStoredName().endsWith(".jpeg"));
    }

    @Test
    void storeFile_shouldSaveCorrectMetadata() {

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "product.png",
                "image/png",
                "product-image".getBytes()
        );

        when(userRepository.findByEmail(
                "shkelqim@example.com"
        )).thenReturn(Optional.of(user));

        when(uploadedFileRepository.save(
                any(UploadedFile.class)
        )).thenAnswer(invocation ->
                invocation.getArgument(0)
        );

        fileStorageService.storeFile(file);

        ArgumentCaptor<UploadedFile> fileCaptor =
                ArgumentCaptor.forClass(UploadedFile.class);

        verify(uploadedFileRepository)
                .save(fileCaptor.capture());

        UploadedFile metadata = fileCaptor.getValue();

        assertEquals("product.png", metadata.getOriginalName());
        assertEquals("image/png", metadata.getContentType());
        assertEquals(file.getSize(), metadata.getSize());
        assertEquals(user, metadata.getUploadedBy());
        assertNotNull(metadata.getStoredName());
        assertNotNull(metadata.getPath());
        assertNotNull(metadata.getUploadedAt());
    }

    @Test
    void storeFile_shouldThrowWhenFileIsNull() {

        InvalidFileException exception =
                assertThrows(
                        InvalidFileException.class,
                        () -> fileStorageService.storeFile(null)
                );

        assertEquals(
                "File must not be empty",
                exception.getMessage()
        );

        verifyNoInteractions(
                uploadedFileRepository,
                userRepository
        );
    }

    @Test
    void storeFile_shouldThrowWhenFileIsEmpty() {

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "photo.jpg",
                "image/jpeg",
                new byte[0]
        );

        InvalidFileException exception =
                assertThrows(
                        InvalidFileException.class,
                        () -> fileStorageService.storeFile(file)
                );

        assertEquals(
                "File must not be empty",
                exception.getMessage()
        );
    }

    @Test
    void storeFile_shouldThrowWhenContentTypeIsInvalid() {

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "document.pdf",
                "application/pdf",
                "pdf".getBytes()
        );

        InvalidFileException exception =
                assertThrows(
                        InvalidFileException.class,
                        () -> fileStorageService.storeFile(file)
                );

        assertEquals(
                "Only JPEG, PNG and WEBP images are allowed",
                exception.getMessage()
        );
    }

    @Test
    void storeFile_shouldThrowWhenContentTypeIsNull() {

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "photo.jpg",
                null,
                "content".getBytes()
        );

        InvalidFileException exception =
                assertThrows(
                        InvalidFileException.class,
                        () -> fileStorageService.storeFile(file)
                );

        assertEquals(
                "Only JPEG, PNG and WEBP images are allowed",
                exception.getMessage()
        );
    }

    @Test
    void storeFile_shouldThrowWhenOriginalFilenameIsBlank() {

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "   ",
                "image/jpeg",
                "content".getBytes()
        );

        InvalidFileException exception =
                assertThrows(
                        InvalidFileException.class,
                        () -> fileStorageService.storeFile(file)
                );

        assertEquals(
                "Original filename is missing",
                exception.getMessage()
        );
    }

    @Test
    void storeFile_shouldThrowWhenExtensionIsMissing() {

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "photo",
                "image/jpeg",
                "content".getBytes()
        );

        InvalidFileException exception =
                assertThrows(
                        InvalidFileException.class,
                        () -> fileStorageService.storeFile(file)
                );

        assertEquals(
                "File extension is missing",
                exception.getMessage()
        );
    }

    @Test
    void storeFile_shouldThrowWhenExtensionIsUnsupported() {

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "photo.gif",
                "image/jpeg",
                "content".getBytes()
        );

        InvalidFileException exception =
                assertThrows(
                        InvalidFileException.class,
                        () -> fileStorageService.storeFile(file)
                );

        assertEquals(
                "Unsupported file extension",
                exception.getMessage()
        );
    }

    @Test
    void storeFile_shouldFallbackToUsername() {

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        "shkelqim",
                        null,
                        AuthorityUtils.NO_AUTHORITIES
                );

        SecurityContextHolder
                .getContext()
                .setAuthentication(authentication);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "photo.jpg",
                "image/jpeg",
                "content".getBytes()
        );

        when(userRepository.findByEmail("shkelqim"))
                .thenReturn(Optional.empty());

        when(userRepository.findByUsername("shkelqim"))
                .thenReturn(Optional.of(user));

        when(uploadedFileRepository.save(
                any(UploadedFile.class)
        )).thenAnswer(invocation ->
                invocation.getArgument(0)
        );

        UploadedFile result =
                fileStorageService.storeFile(file);

        assertEquals(user, result.getUploadedBy());

        verify(userRepository)
                .findByUsername("shkelqim");
    }

    @Test
    void storeFile_shouldThrowWhenUserIsNotAuthenticated() {

        SecurityContextHolder.clearContext();

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "photo.jpg",
                "image/jpeg",
                "content".getBytes()
        );

        InvalidFileException exception =
                assertThrows(
                        InvalidFileException.class,
                        () -> fileStorageService.storeFile(file)
                );

        assertEquals(
                "Authenticated user is required",
                exception.getMessage()
        );

        verifyNoInteractions(userRepository);
    }

    @Test
    void storeFile_shouldThrowWhenAuthenticatedUserIsMissing() {

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "photo.jpg",
                "image/jpeg",
                "content".getBytes()
        );

        when(userRepository.findByEmail(
                "shkelqim@example.com"
        )).thenReturn(Optional.empty());

        when(userRepository.findByUsername(
                "shkelqim@example.com"
        )).thenReturn(Optional.empty());

        ResourceNotFoundException exception =
                assertThrows(
                        ResourceNotFoundException.class,
                        () -> fileStorageService.storeFile(file)
                );

        assertEquals(
                "Authenticated user not found",
                exception.getMessage()
        );

        verify(uploadedFileRepository, never())
                .save(any(UploadedFile.class));
    }

    @Test
    void getFileMetadata_shouldReturnFile() {

        UploadedFile uploadedFile = createUploadedFile(
                tempDirectory.resolve("photo.jpg")
        );

        when(uploadedFileRepository.findById(10L))
                .thenReturn(Optional.of(uploadedFile));

        UploadedFile result =
                fileStorageService.getFileMetadata(10L);

        assertSame(uploadedFile, result);
    }

    @Test
    void getFileMetadata_shouldThrowWhenFileDoesNotExist() {

        when(uploadedFileRepository.findById(99L))
                .thenReturn(Optional.empty());

        ResourceNotFoundException exception =
                assertThrows(
                        ResourceNotFoundException.class,
                        () -> fileStorageService.getFileMetadata(99L)
                );

        assertEquals(
                "Uploaded file not found",
                exception.getMessage()
        );
    }

    @Test
    void loadFileAsResource_shouldLoadExistingFile() throws Exception {

        Path physicalFile =
                tempDirectory.resolve("stored-photo.jpg");

        Files.write(
                physicalFile,
                "stored-content".getBytes()
        );

        UploadedFile uploadedFile =
                createUploadedFile(physicalFile);

        when(uploadedFileRepository.findById(10L))
                .thenReturn(Optional.of(uploadedFile));

        Resource resource =
                fileStorageService.loadFileAsResource(10L);

        assertNotNull(resource);
        assertTrue(resource.exists());
        assertTrue(resource.isReadable());
        assertEquals(
                "stored-content",
                resource.getContentAsString(
                        java.nio.charset.StandardCharsets.UTF_8
                )
        );
    }

    @Test
    void loadFileAsResource_shouldThrowWhenPhysicalFileIsMissing() {

        Path missingFile =
                tempDirectory.resolve("missing.jpg");

        UploadedFile uploadedFile =
                createUploadedFile(missingFile);

        when(uploadedFileRepository.findById(10L))
                .thenReturn(Optional.of(uploadedFile));

        ResourceNotFoundException exception =
                assertThrows(
                        ResourceNotFoundException.class,
                        () -> fileStorageService
                                .loadFileAsResource(10L)
                );

        assertEquals(
                "Stored file could not be found",
                exception.getMessage()
        );
    }

    @Test
    void deleteFile_shouldDeletePhysicalFileAndMetadata()
            throws Exception {

        Path physicalFile =
                tempDirectory.resolve("delete-photo.jpg");

        Files.write(
                physicalFile,
                "delete-content".getBytes()
        );

        UploadedFile uploadedFile =
                createUploadedFile(physicalFile);

        when(uploadedFileRepository.findById(10L))
                .thenReturn(Optional.of(uploadedFile));

        fileStorageService.deleteFile(10L);

        assertFalse(Files.exists(physicalFile));

        verify(uploadedFileRepository)
                .delete(uploadedFile);
    }

    @Test
    void deleteFile_shouldStillDeleteMetadataWhenPhysicalFileMissing() {

        Path missingFile =
                tempDirectory.resolve("already-missing.jpg");

        UploadedFile uploadedFile =
                createUploadedFile(missingFile);

        when(uploadedFileRepository.findById(10L))
                .thenReturn(Optional.of(uploadedFile));

        fileStorageService.deleteFile(10L);

        verify(uploadedFileRepository)
                .delete(uploadedFile);
    }

    @Test
    void deleteFile_shouldThrowWhenMetadataDoesNotExist() {

        when(uploadedFileRepository.findById(99L))
                .thenReturn(Optional.empty());

        ResourceNotFoundException exception =
                assertThrows(
                        ResourceNotFoundException.class,
                        () -> fileStorageService.deleteFile(99L)
                );

        assertEquals(
                "Uploaded file not found",
                exception.getMessage()
        );

        verify(uploadedFileRepository, never())
                .delete(any(UploadedFile.class));
    }

    @Test
    void storeFile_shouldDeletePhysicalFileWhenDatabaseSaveFails() {

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "photo.jpg",
                "image/jpeg",
                "content".getBytes()
        );

        when(userRepository.findByEmail(
                "shkelqim@example.com"
        )).thenReturn(Optional.of(user));

        when(uploadedFileRepository.save(
                any(UploadedFile.class)
        )).thenThrow(new RuntimeException("Database failure"));

        assertThrows(
                RuntimeException.class,
                () -> fileStorageService.storeFile(file)
        );

        assertDoesNotThrow(() -> {
            try (var files = Files.list(tempDirectory)) {
                assertEquals(0, files.count());
            }
        });
    }

    private UploadedFile createUploadedFile(Path path) {

        return UploadedFile.builder()
                .id(10L)
                .originalName("photo.jpg")
                .storedName("stored-photo.jpg")
                .contentType("image/jpeg")
                .size(100L)
                .path(path.toString())
                .uploadedBy(user)
                .uploadedAt(java.time.LocalDateTime.now())
                .build();
    }
}