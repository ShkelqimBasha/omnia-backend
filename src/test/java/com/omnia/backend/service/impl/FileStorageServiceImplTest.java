package com.omnia.backend.service.impl;

import com.omnia.backend.common.exception.FileStorageException;
import com.omnia.backend.common.exception.InvalidFileException;
import com.omnia.backend.common.exception.ResourceNotFoundException;
import com.omnia.backend.config.FileStorageProperties;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.unit.DataSize;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileStorageServiceImplTest {

    private static final Instant FIXED_INSTANT =
            Instant.parse("2026-07-22T10:00:00Z");

    private static final Clock FIXED_CLOCK =
            Clock.fixed(
                    FIXED_INSTANT,
                    ZoneOffset.UTC
            );

    private static final LocalDateTime FIXED_DATE_TIME =
            LocalDateTime.ofInstant(
                    FIXED_INSTANT,
                    ZoneOffset.UTC
            );

    private static final byte[] JPEG_BYTES = {
            (byte) 0xFF,
            (byte) 0xD8,
            (byte) 0xFF,
            (byte) 0xE0,
            0x00,
            0x10,
            0x4A,
            0x46,
            0x49,
            0x46,
            0x00,
            0x01,
            0x01,
            0x00,
            0x00,
            0x01,
            0x00,
            0x01,
            0x00,
            0x00,
            (byte) 0xFF,
            (byte) 0xD9
    };

    private static final byte[] PNG_BYTES = {
            (byte) 0x89,
            0x50,
            0x4E,
            0x47,
            0x0D,
            0x0A,
            0x1A,
            0x0A,
            0x00,
            0x00,
            0x00,
            0x0D,
            0x49,
            0x48,
            0x44,
            0x52
    };

    private static final byte[] WEBP_BYTES = {
            0x52,
            0x49,
            0x46,
            0x46,
            0x0C,
            0x00,
            0x00,
            0x00,
            0x57,
            0x45,
            0x42,
            0x50,
            0x56,
            0x50,
            0x38,
            0x20,
            0x00,
            0x00,
            0x00,
            0x00
    };

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

        FileStorageProperties properties =
                new FileStorageProperties(
                        tempDirectory.toString(),
                        DataSize.ofMegabytes(5)
                );

        fileStorageService =
                new FileStorageServiceImpl(
                        uploadedFileRepository,
                        userRepository,
                        properties,
                        FIXED_CLOCK
                );

        user = User.builder()
                .id(1L)
                .username("shkelqim")
                .email("shkelqim@example.com")
                .build();

        authenticate(
                "shkelqim@example.com",
                "ROLE_USER"
        );

        lenient()
                .when(userRepository.findByEmail(
                        "shkelqim@example.com"
                ))
                .thenReturn(Optional.of(user));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void storeFile_WithValidJpeg_ShouldStoreFileAndMetadata()
            throws Exception {

        MockMultipartFile file = createFile(
                "photo.jpg",
                "image/jpeg",
                JPEG_BYTES
        );

        mockSuccessfulSave(10L);

        UploadedFile result =
                fileStorageService.storeFile(file);

        assertNotNull(result);
        assertEquals(10L, result.getId());
        assertEquals(
                "photo.jpg",
                result.getOriginalName()
        );
        assertEquals(
                "image/jpeg",
                result.getContentType()
        );
        assertEquals(
                (long) JPEG_BYTES.length,
                result.getSize()
        );
        assertEquals(user, result.getUploadedBy());
        assertEquals(
                FIXED_DATE_TIME,
                result.getUploadedAt()
        );

        assertNotNull(result.getStoredName());
        assertTrue(
                result.getStoredName().endsWith(".jpg")
        );

        assertNotNull(result.getChecksumSha256());
        assertEquals(
                64,
                result.getChecksumSha256().length()
        );
        assertTrue(
                result.getChecksumSha256()
                        .matches("[0-9a-f]{64}")
        );

        Path storedFile =
                tempDirectory.resolve(
                        result.getStoredName()
                );

        assertTrue(Files.exists(storedFile));
        assertArrayEquals(
                JPEG_BYTES,
                Files.readAllBytes(storedFile)
        );
    }

    @Test
    void storeFile_WithValidPng_ShouldStorePng()
            throws Exception {

        MockMultipartFile file = createFile(
                "product.PNG",
                "image/png",
                PNG_BYTES
        );

        mockSuccessfulSave(null);

        UploadedFile result =
                fileStorageService.storeFile(file);

        assertEquals(
                "image/png",
                result.getContentType()
        );
        assertTrue(
                result.getStoredName().endsWith(".png")
        );

        Path storedFile =
                tempDirectory.resolve(
                        result.getStoredName()
                );

        assertTrue(Files.exists(storedFile));
        assertArrayEquals(
                PNG_BYTES,
                Files.readAllBytes(storedFile)
        );
    }

    @Test
    void storeFile_WithValidWebp_ShouldStoreWebp()
            throws Exception {

        MockMultipartFile file = createFile(
                "product.WEBP",
                "image/webp",
                WEBP_BYTES
        );

        mockSuccessfulSave(null);

        UploadedFile result =
                fileStorageService.storeFile(file);

        assertEquals(
                "image/webp",
                result.getContentType()
        );
        assertTrue(
                result.getStoredName().endsWith(".webp")
        );

        Path storedFile =
                tempDirectory.resolve(
                        result.getStoredName()
                );

        assertTrue(Files.exists(storedFile));
        assertArrayEquals(
                WEBP_BYTES,
                Files.readAllBytes(storedFile)
        );
    }

    @Test
    void storeFile_WithJpegExtension_ShouldUseCanonicalJpgExtension() {

        MockMultipartFile file = createFile(
                "photo.JPEG",
                "image/jpeg",
                JPEG_BYTES
        );

        mockSuccessfulSave(null);

        UploadedFile result =
                fileStorageService.storeFile(file);

        assertTrue(
                result.getStoredName().endsWith(".jpg")
        );
        assertEquals(
                "image/jpeg",
                result.getContentType()
        );
    }

    @Test
    void storeFile_ShouldSaveCorrectMetadata() {

        MockMultipartFile file = createFile(
                "product.png",
                "image/png",
                PNG_BYTES
        );

        mockSuccessfulSave(null);

        fileStorageService.storeFile(file);

        ArgumentCaptor<UploadedFile> captor =
                ArgumentCaptor.forClass(
                        UploadedFile.class
                );

        verify(uploadedFileRepository)
                .save(captor.capture());

        UploadedFile metadata = captor.getValue();

        assertEquals(
                "product.png",
                metadata.getOriginalName()
        );
        assertEquals(
                "image/png",
                metadata.getContentType()
        );
        assertEquals(
                (long) PNG_BYTES.length,
                metadata.getSize()
        );
        assertEquals(
                user,
                metadata.getUploadedBy()
        );
        assertEquals(
                FIXED_DATE_TIME,
                metadata.getUploadedAt()
        );

        assertNotNull(metadata.getStoredName());
        assertFalse(
                metadata.getStoredName()
                        .contains("product")
        );

        assertNotNull(
                metadata.getChecksumSha256()
        );
        assertEquals(
                64,
                metadata.getChecksumSha256().length()
        );

        assertTrue(
                Files.exists(
                        tempDirectory.resolve(
                                metadata.getStoredName()
                        )
                )
        );
    }

    @Test
    void storeFile_WithPathComponents_ShouldKeepSafeFilename() {

        MockMultipartFile file = createFile(
                "../../avatar.png",
                "image/png",
                PNG_BYTES
        );

        mockSuccessfulSave(null);

        UploadedFile result =
                fileStorageService.storeFile(file);

        assertEquals(
                "avatar.png",
                result.getOriginalName()
        );
        assertFalse(
                result.getOriginalName().contains("..")
        );
        assertFalse(
                result.getOriginalName().contains("/")
        );
        assertFalse(
                result.getOriginalName().contains("\\")
        );
    }

    @Test
    void storeFile_WithNullFile_ShouldThrowException() {

        assertThrows(
                InvalidFileException.class,
                () -> fileStorageService.storeFile(null)
        );

        verify(uploadedFileRepository, never())
                .save(any(UploadedFile.class));
    }

    @Test
    void storeFile_WithEmptyFile_ShouldThrowException() {

        MockMultipartFile file = createFile(
                "photo.jpg",
                "image/jpeg",
                new byte[0]
        );

        assertThrows(
                InvalidFileException.class,
                () -> fileStorageService.storeFile(file)
        );

        verify(uploadedFileRepository, never())
                .save(any(UploadedFile.class));
    }

    @Test
    void storeFile_WithNullFilename_ShouldThrowException() {

        MockMultipartFile file = createFile(
                null,
                "image/jpeg",
                JPEG_BYTES
        );

        assertThrows(
                InvalidFileException.class,
                () -> fileStorageService.storeFile(file)
        );

        verify(uploadedFileRepository, never())
                .save(any(UploadedFile.class));
    }

    @Test
    void storeFile_WithBlankFilename_ShouldThrowException() {

        MockMultipartFile file = createFile(
                "   ",
                "image/jpeg",
                JPEG_BYTES
        );

        assertThrows(
                InvalidFileException.class,
                () -> fileStorageService.storeFile(file)
        );

        verify(uploadedFileRepository, never())
                .save(any(UploadedFile.class));
    }

    @Test
    void storeFile_WithoutExtension_ShouldThrowException() {

        MockMultipartFile file = createFile(
                "photo",
                "image/jpeg",
                JPEG_BYTES
        );

        assertThrows(
                InvalidFileException.class,
                () -> fileStorageService.storeFile(file)
        );

        verify(uploadedFileRepository, never())
                .save(any(UploadedFile.class));
    }

    @Test
    void storeFile_WithUnsupportedExtension_ShouldThrowException() {

        MockMultipartFile file = createFile(
                "photo.gif",
                "image/jpeg",
                JPEG_BYTES
        );

        assertThrows(
                InvalidFileException.class,
                () -> fileStorageService.storeFile(file)
        );

        verify(uploadedFileRepository, never())
                .save(any(UploadedFile.class));
    }

    @Test
    void storeFile_WithUnsupportedContentType_ShouldThrowException() {

        MockMultipartFile file = createFile(
                "document.png",
                "application/pdf",
                PNG_BYTES
        );

        assertThrows(
                InvalidFileException.class,
                () -> fileStorageService.storeFile(file)
        );

        verify(uploadedFileRepository, never())
                .save(any(UploadedFile.class));
    }

    @Test
    void storeFile_WithNullContentType_ShouldThrowException() {

        MockMultipartFile file = createFile(
                "photo.jpg",
                null,
                JPEG_BYTES
        );

        assertThrows(
                InvalidFileException.class,
                () -> fileStorageService.storeFile(file)
        );

        verify(uploadedFileRepository, never())
                .save(any(UploadedFile.class));
    }

    @Test
    void storeFile_WithFakeJpegContent_ShouldThrowException() {

        MockMultipartFile file = createFile(
                "photo.jpg",
                "image/jpeg",
                "not-a-jpeg".getBytes()
        );

        assertThrows(
                InvalidFileException.class,
                () -> fileStorageService.storeFile(file)
        );

        verify(uploadedFileRepository, never())
                .save(any(UploadedFile.class));
    }

    @Test
    void storeFile_WithFakePngContent_ShouldThrowException() {

        MockMultipartFile file = createFile(
                "photo.png",
                "image/png",
                "not-a-png".getBytes()
        );

        assertThrows(
                InvalidFileException.class,
                () -> fileStorageService.storeFile(file)
        );

        verify(uploadedFileRepository, never())
                .save(any(UploadedFile.class));
    }

    @Test
    void storeFile_WithFakeWebpContent_ShouldThrowException() {

        MockMultipartFile file = createFile(
                "photo.webp",
                "image/webp",
                "not-a-webp".getBytes()
        );

        assertThrows(
                InvalidFileException.class,
                () -> fileStorageService.storeFile(file)
        );

        verify(uploadedFileRepository, never())
                .save(any(UploadedFile.class));
    }

    @Test
    void storeFile_WithMismatchedMimeType_ShouldThrowException() {

        MockMultipartFile file = createFile(
                "photo.jpg",
                "image/jpeg",
                PNG_BYTES
        );

        assertThrows(
                InvalidFileException.class,
                () -> fileStorageService.storeFile(file)
        );

        verify(uploadedFileRepository, never())
                .save(any(UploadedFile.class));
    }

    @Test
    void storeFile_WithExtensionAndContentMismatch_ShouldThrowException() {

        MockMultipartFile file = createFile(
                "photo.png",
                "image/png",
                JPEG_BYTES
        );

        assertThrows(
                InvalidFileException.class,
                () -> fileStorageService.storeFile(file)
        );

        verify(uploadedFileRepository, never())
                .save(any(UploadedFile.class));
    }

    @Test
    void storeFile_ExceedingMaximumSize_ShouldThrowException() {

        FileStorageProperties properties =
                new FileStorageProperties(
                        tempDirectory.toString(),
                        DataSize.ofBytes(8)
                );

        FileStorageServiceImpl limitedService =
                new FileStorageServiceImpl(
                        uploadedFileRepository,
                        userRepository,
                        properties,
                        FIXED_CLOCK
                );

        MockMultipartFile file = createFile(
                "photo.jpg",
                "image/jpeg",
                JPEG_BYTES
        );

        assertThrows(
                InvalidFileException.class,
                () -> limitedService.storeFile(file)
        );

        verify(uploadedFileRepository, never())
                .save(any(UploadedFile.class));
    }

    @Test
    void storeFile_WithUsernameAuthentication_ShouldResolveByUsername() {

        authenticate(
                "shkelqim",
                "ROLE_USER"
        );

        MockMultipartFile file = createFile(
                "photo.jpg",
                "image/jpeg",
                JPEG_BYTES
        );

        when(userRepository.findByEmail("shkelqim"))
                .thenReturn(Optional.empty());

        when(userRepository.findByUsername("shkelqim"))
                .thenReturn(Optional.of(user));

        mockSuccessfulSave(null);

        UploadedFile result =
                fileStorageService.storeFile(file);

        assertEquals(
                user,
                result.getUploadedBy()
        );

        verify(userRepository)
                .findByEmail("shkelqim");

        verify(userRepository)
                .findByUsername("shkelqim");
    }

    @Test
    void storeFile_WithoutAuthentication_ShouldThrowAccessDenied() {

        SecurityContextHolder.clearContext();

        MockMultipartFile file = createFile(
                "photo.jpg",
                "image/jpeg",
                JPEG_BYTES
        );

        assertThrows(
                AccessDeniedException.class,
                () -> fileStorageService.storeFile(file)
        );

        verifyNoInteractions(userRepository);

        verify(uploadedFileRepository, never())
                .save(any(UploadedFile.class));

        assertDirectoryIsEmpty();
    }

    @Test
    void storeFile_WhenAuthenticatedUserDoesNotExist_ShouldThrowException() {

        when(userRepository.findByEmail(
                "shkelqim@example.com"
        )).thenReturn(Optional.empty());

        when(userRepository.findByUsername(
                "shkelqim@example.com"
        )).thenReturn(Optional.empty());

        MockMultipartFile file = createFile(
                "photo.jpg",
                "image/jpeg",
                JPEG_BYTES
        );

        assertThrows(
                ResourceNotFoundException.class,
                () -> fileStorageService.storeFile(file)
        );

        verify(uploadedFileRepository, never())
                .save(any(UploadedFile.class));

        assertDirectoryIsEmpty();
    }

    @Test
    void storeFile_WhenDatabaseSaveFails_ShouldDeletePhysicalFile() {

        MockMultipartFile file = createFile(
                "photo.jpg",
                "image/jpeg",
                JPEG_BYTES
        );

        when(uploadedFileRepository.save(
                any(UploadedFile.class)
        )).thenThrow(
                new RuntimeException("Database failure")
        );

        assertThrows(
                RuntimeException.class,
                () -> fileStorageService.storeFile(file)
        );

        assertDirectoryIsEmpty();
    }

    @Test
    void getFileMetadata_WithExistingId_ShouldReturnMetadata() {

        UploadedFile uploadedFile =
                createUploadedFile(
                        10L,
                        "stored-photo.jpg",
                        user
                );

        when(uploadedFileRepository.findById(10L))
                .thenReturn(Optional.of(uploadedFile));

        UploadedFile result =
                fileStorageService.getFileMetadata(10L);

        assertSame(uploadedFile, result);

        verify(uploadedFileRepository)
                .findById(10L);
    }

    @Test
    void getFileMetadata_WithMissingId_ShouldThrowException() {

        when(uploadedFileRepository.findById(99L))
                .thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> fileStorageService
                        .getFileMetadata(99L)
        );
    }

    @Test
    void getFileMetadata_WithZeroId_ShouldThrowException() {

        assertThrows(
                IllegalArgumentException.class,
                () -> fileStorageService
                        .getFileMetadata(0L)
        );

        verifyNoInteractions(uploadedFileRepository);
    }

    @Test
    void getFileMetadata_WithNegativeId_ShouldThrowException() {

        assertThrows(
                IllegalArgumentException.class,
                () -> fileStorageService
                        .getFileMetadata(-1L)
        );

        verifyNoInteractions(uploadedFileRepository);
    }

    @Test
    void loadFileAsResource_WithExistingFile_ShouldReturnResource()
            throws Exception {

        String storedName = "stored-photo.jpg";

        Path physicalFile =
                tempDirectory.resolve(storedName);

        Files.write(
                physicalFile,
                JPEG_BYTES
        );

        UploadedFile uploadedFile =
                createUploadedFile(
                        10L,
                        storedName,
                        user
                );

        when(uploadedFileRepository.findById(10L))
                .thenReturn(Optional.of(uploadedFile));

        Resource resource =
                fileStorageService.loadFileAsResource(10L);

        assertNotNull(resource);
        assertTrue(resource.exists());
        assertTrue(resource.isReadable());
        assertArrayEquals(
                JPEG_BYTES,
                resource.getContentAsByteArray()
        );
    }

    @Test
    void loadFileAsResource_WhenPhysicalFileIsMissing_ShouldThrowException() {

        UploadedFile uploadedFile =
                createUploadedFile(
                        10L,
                        "missing-photo.jpg",
                        user
                );

        when(uploadedFileRepository.findById(10L))
                .thenReturn(Optional.of(uploadedFile));

        assertThrows(
                ResourceNotFoundException.class,
                () -> fileStorageService
                        .loadFileAsResource(10L)
        );
    }

    @Test
    void loadFileAsResource_WithTraversalName_ShouldThrowException() {

        UploadedFile uploadedFile =
                createUploadedFile(
                        10L,
                        "../outside.jpg",
                        user
                );

        when(uploadedFileRepository.findById(10L))
                .thenReturn(Optional.of(uploadedFile));

        assertThrows(
                FileStorageException.class,
                () -> fileStorageService
                        .loadFileAsResource(10L)
        );
    }

    @Test
    void loadFileAsResource_WithAbsoluteName_ShouldThrowException() {

        String unsafeName =
                tempDirectory.resolve("outside.jpg")
                        .toAbsolutePath()
                        .toString();

        UploadedFile uploadedFile =
                createUploadedFile(
                        10L,
                        unsafeName,
                        user
                );

        when(uploadedFileRepository.findById(10L))
                .thenReturn(Optional.of(uploadedFile));

        assertThrows(
                FileStorageException.class,
                () -> fileStorageService
                        .loadFileAsResource(10L)
        );
    }

    @Test
    void deleteFile_AsOwner_ShouldDeleteMetadataAndPhysicalFile()
            throws Exception {

        String storedName = "delete-photo.jpg";

        Path physicalFile =
                tempDirectory.resolve(storedName);

        Files.write(
                physicalFile,
                JPEG_BYTES
        );

        UploadedFile uploadedFile =
                createUploadedFile(
                        10L,
                        storedName,
                        user
                );

        when(uploadedFileRepository.findById(10L))
                .thenReturn(Optional.of(uploadedFile));

        fileStorageService.deleteFile(10L);

        assertFalse(Files.exists(physicalFile));

        verify(uploadedFileRepository)
                .delete(uploadedFile);

        verify(uploadedFileRepository)
                .flush();
    }

    @Test
    void deleteFile_WhenPhysicalFileIsMissing_ShouldDeleteMetadata() {

        UploadedFile uploadedFile =
                createUploadedFile(
                        10L,
                        "missing-photo.jpg",
                        user
                );

        when(uploadedFileRepository.findById(10L))
                .thenReturn(Optional.of(uploadedFile));

        fileStorageService.deleteFile(10L);

        verify(uploadedFileRepository)
                .delete(uploadedFile);

        verify(uploadedFileRepository)
                .flush();
    }

    @Test
    void deleteFile_AsAdmin_ShouldDeleteAnotherUsersFile()
            throws Exception {

        User owner = User.builder()
                .id(2L)
                .username("owner")
                .email("owner@example.com")
                .build();

        User admin = User.builder()
                .id(99L)
                .username("admin")
                .email("admin@example.com")
                .build();

        String storedName = "admin-delete.jpg";

        Path physicalFile =
                tempDirectory.resolve(storedName);

        Files.write(
                physicalFile,
                JPEG_BYTES
        );

        UploadedFile uploadedFile =
                createUploadedFile(
                        10L,
                        storedName,
                        owner
                );

        when(uploadedFileRepository.findById(10L))
                .thenReturn(Optional.of(uploadedFile));

        authenticate(
                "admin@example.com",
                "ROLE_ADMIN"
        );

        when(userRepository.findByEmail(
                "admin@example.com"
        )).thenReturn(Optional.of(admin));

        fileStorageService.deleteFile(10L);

        assertFalse(Files.exists(physicalFile));

        verify(uploadedFileRepository)
                .delete(uploadedFile);

        verify(uploadedFileRepository)
                .flush();
    }

    @Test
    void deleteFile_AsDifferentUser_ShouldThrowAccessDenied() {

        User owner = User.builder()
                .id(2L)
                .username("owner")
                .email("owner@example.com")
                .build();

        UploadedFile uploadedFile =
                createUploadedFile(
                        10L,
                        "owners-photo.jpg",
                        owner
                );

        when(uploadedFileRepository.findById(10L))
                .thenReturn(Optional.of(uploadedFile));

        assertThrows(
                AccessDeniedException.class,
                () -> fileStorageService.deleteFile(10L)
        );

        verify(uploadedFileRepository, never())
                .delete(any(UploadedFile.class));

        verify(uploadedFileRepository, never())
                .flush();
    }

    @Test
    void deleteFile_WithMissingMetadata_ShouldThrowException() {

        when(uploadedFileRepository.findById(99L))
                .thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> fileStorageService.deleteFile(99L)
        );

        verify(uploadedFileRepository, never())
                .delete(any(UploadedFile.class));

        verify(uploadedFileRepository, never())
                .flush();
    }

    @Test
    void deleteFile_WithTraversalName_ShouldThrowException() {

        UploadedFile uploadedFile =
                createUploadedFile(
                        10L,
                        "../outside.jpg",
                        user
                );

        when(uploadedFileRepository.findById(10L))
                .thenReturn(Optional.of(uploadedFile));

        assertThrows(
                FileStorageException.class,
                () -> fileStorageService.deleteFile(10L)
        );

        verify(uploadedFileRepository, never())
                .delete(any(UploadedFile.class));

        verify(uploadedFileRepository, never())
                .flush();
    }

    private MockMultipartFile createFile(
            String filename,
            String contentType,
            byte[] content
    ) {
        return new MockMultipartFile(
                "file",
                filename,
                contentType,
                content
        );
    }

    private void authenticate(
            String principal,
            String authority
    ) {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        AuthorityUtils.createAuthorityList(
                                authority
                        )
                );

        SecurityContextHolder.getContext()
                .setAuthentication(authentication);
    }

    private void mockSuccessfulSave(Long generatedId) {

        when(uploadedFileRepository.save(
                any(UploadedFile.class)
        )).thenAnswer(invocation -> {

            UploadedFile uploadedFile =
                    invocation.getArgument(0);

            if (generatedId != null) {
                uploadedFile.setId(generatedId);
            }

            return uploadedFile;
        });
    }

    private UploadedFile createUploadedFile(
            Long id,
            String storedName,
            User owner
    ) {
        return UploadedFile.builder()
                .id(id)
                .originalName("photo.jpg")
                .storedName(storedName)
                .contentType("image/jpeg")
                .size((long) JPEG_BYTES.length)
                .checksumSha256("0".repeat(64))
                .uploadedBy(owner)
                .uploadedAt(FIXED_DATE_TIME)
                .build();
    }

    private void assertDirectoryIsEmpty() {

        assertDoesNotThrow(() -> {
            try (var files = Files.list(tempDirectory)) {
                assertEquals(0, files.count());
            }
        });
    }
}