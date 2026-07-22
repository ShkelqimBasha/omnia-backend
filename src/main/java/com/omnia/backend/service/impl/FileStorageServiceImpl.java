package com.omnia.backend.service.impl;

import com.omnia.backend.common.exception.FileStorageException;
import com.omnia.backend.common.exception.InvalidFileException;
import com.omnia.backend.common.exception.ResourceNotFoundException;
import com.omnia.backend.config.FileStorageProperties;
import com.omnia.backend.entity.UploadedFile;
import com.omnia.backend.entity.User;
import com.omnia.backend.repository.UploadedFileRepository;
import com.omnia.backend.repository.UserRepository;
import com.omnia.backend.service.interfaces.FileStorageService;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

@Service
public class FileStorageServiceImpl
        implements FileStorageService {

    private final UploadedFileRepository uploadedFileRepository;
    private final UserRepository userRepository;
    private final FileStorageProperties properties;
    private final Clock clock;
    private final Path storageLocation;

    public FileStorageServiceImpl(
            UploadedFileRepository uploadedFileRepository,
            UserRepository userRepository,
            FileStorageProperties properties,
            Clock clock
    ) {
        this.uploadedFileRepository =
                uploadedFileRepository;

        this.userRepository = userRepository;
        this.properties = properties;
        this.clock = clock;

        try {
            this.storageLocation =
                    Path.of(properties.uploadDir())
                            .toAbsolutePath()
                            .normalize();

            Files.createDirectories(
                    this.storageLocation
            );

            if (!Files.isDirectory(
                    this.storageLocation,
                    LinkOption.NOFOLLOW_LINKS
            )) {
                throw new FileStorageException(
                        "File upload location is not a directory"
                );
            }

            if (!Files.isWritable(
                    this.storageLocation
            )) {
                throw new FileStorageException(
                        "File upload directory is not writable"
                );
            }

        } catch (IOException exception) {
            throw new FileStorageException(
                    "Could not initialize file upload directory",
                    exception
            );
        }
    }

    @Override
    @Transactional
    public UploadedFile storeFile(
            MultipartFile file
    ) {
        validateBasicFileProperties(file);

        User currentUser = getCurrentUser();

        String originalName =
                sanitizeOriginalFilename(
                        file.getOriginalFilename()
                );

        String requestedExtension =
                extractExtension(originalName);

        byte[] fileBytes =
                readFileBytes(file);

        DetectedImageType detectedType =
                detectImageType(fileBytes);

        validateDeclaredContentType(
                file.getContentType(),
                detectedType
        );

        validateExtension(
                requestedExtension,
                detectedType
        );

        String storedName =
                UUID.randomUUID()
                        + detectedType.canonicalExtension();

        Path destination =
                resolveStoredFilePath(storedName);

        String checksum =
                calculateSha256(fileBytes);

        try {
            Files.write(
                    destination,
                    fileBytes,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE
            );

        } catch (IOException exception) {
            throw new FileStorageException(
                    "Could not store file",
                    exception
            );
        }

        UploadedFile uploadedFile =
                UploadedFile.builder()
                        .originalName(originalName)
                        .storedName(storedName)
                        .contentType(
                                detectedType.contentType()
                        )
                        .size((long) fileBytes.length)
                        .checksumSha256(checksum)
                        .uploadedBy(currentUser)
                        .uploadedAt(currentDateTime())
                        .build();

        try {
            return uploadedFileRepository
                    .save(uploadedFile);

        } catch (RuntimeException exception) {
            deletePhysicalFileQuietly(
                    destination
            );

            throw exception;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Resource loadFileAsResource(
            Long fileId
    ) {
        UploadedFile uploadedFile =
                getFileMetadata(fileId);

        Path filePath =
                resolveStoredFilePath(
                        uploadedFile.getStoredName()
                );

        if (Files.isSymbolicLink(filePath)
                || !Files.isRegularFile(
                filePath,
                LinkOption.NOFOLLOW_LINKS
        )
                || !Files.isReadable(filePath)) {
            throw new ResourceNotFoundException(
                    "Stored file could not be found"
            );
        }

        try {
            return new UrlResource(
                    filePath.toUri()
            );

        } catch (MalformedURLException exception) {
            throw new FileStorageException(
                    "Invalid stored file location",
                    exception
            );
        }
    }

    @Override
    @Transactional(readOnly = true)
    public UploadedFile getFileMetadata(
            Long fileId
    ) {
        validateFileId(fileId);

        return uploadedFileRepository
                .findById(fileId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Uploaded file not found"
                        )
                );
    }

    @Override
    @Transactional
    public void deleteFile(Long fileId) {
        UploadedFile uploadedFile =
                getFileMetadata(fileId);

        Authentication authentication =
                getRequiredAuthentication();

        User currentUser =
                findAuthenticatedUser(
                        authentication
                );

        verifyDeletePermission(
                uploadedFile,
                currentUser,
                authentication
        );

        Path filePath =
                resolveStoredFilePath(
                        uploadedFile.getStoredName()
                );

        /*
         * Ekzekutojmë DELETE në databazë dhe flush
         * përpara fshirjes fizike. Nëse SQL dështon,
         * skedari fizik mbetet i paprekur.
         */
        uploadedFileRepository.delete(
                uploadedFile
        );

        uploadedFileRepository.flush();

        try {
            Files.deleteIfExists(filePath);

        } catch (IOException exception) {
            throw new FileStorageException(
                    "Could not delete stored file",
                    exception
            );
        }
    }

    private void validateBasicFileProperties(
            MultipartFile file
    ) {
        if (file == null || file.isEmpty()) {
            throw new InvalidFileException(
                    "File must not be empty"
            );
        }

        if (file.getSize() <= 0) {
            throw new InvalidFileException(
                    "File must not be empty"
            );
        }

        if (file.getSize()
                > properties.maxSize().toBytes()) {
            throw new InvalidFileException(
                    "File exceeds the maximum allowed size of "
                            + properties.maxSize()
            );
        }

        if (file.getOriginalFilename() == null
                || file.getOriginalFilename()
                .isBlank()) {
            throw new InvalidFileException(
                    "Original filename is missing"
            );
        }
    }

    private byte[] readFileBytes(
            MultipartFile file
    ) {
        try {
            byte[] bytes = file.getBytes();

            if (bytes.length == 0) {
                throw new InvalidFileException(
                        "File must not be empty"
                );
            }

            if (bytes.length
                    > properties.maxSize().toBytes()) {
                throw new InvalidFileException(
                        "File exceeds the maximum allowed size of "
                                + properties.maxSize()
                );
            }

            return bytes;

        } catch (IOException exception) {
            throw new FileStorageException(
                    "Could not read uploaded file",
                    exception
            );
        }
    }

    private String sanitizeOriginalFilename(
            String originalFilename
    ) {
        String normalized =
                originalFilename
                        .replace('\\', '/');

        int lastSlash =
                normalized.lastIndexOf('/');

        String filename =
                lastSlash >= 0
                        ? normalized.substring(
                        lastSlash + 1
                )
                        : normalized;

        filename = filename
                .replaceAll(
                        "[\\p{Cntrl}]",
                        ""
                )
                .trim();

        if (filename.isBlank()
                || ".".equals(filename)
                || "..".equals(filename)) {
            throw new InvalidFileException(
                    "Original filename is invalid"
            );
        }

        if (filename.length() > 255) {
            throw new InvalidFileException(
                    "Original filename must not exceed 255 characters"
            );
        }

        return filename;
    }

    private String extractExtension(
            String filename
    ) {
        int dotIndex =
                filename.lastIndexOf('.');

        if (dotIndex <= 0
                || dotIndex
                == filename.length() - 1) {
            throw new InvalidFileException(
                    "File extension is missing"
            );
        }

        return filename
                .substring(dotIndex)
                .toLowerCase(Locale.ROOT);
    }

    private void validateDeclaredContentType(
            String declaredContentType,
            DetectedImageType detectedType
    ) {
        if (declaredContentType == null
                || declaredContentType.isBlank()) {
            throw new InvalidFileException(
                    "File content type is missing"
            );
        }

        String normalizedContentType =
                declaredContentType
                        .trim()
                        .toLowerCase(Locale.ROOT);

        if (!detectedType.contentType()
                .equals(normalizedContentType)) {
            throw new InvalidFileException(
                    "Declared content type does not match file content"
            );
        }
    }

    private void validateExtension(
            String extension,
            DetectedImageType detectedType
    ) {
        boolean validExtension =
                switch (detectedType) {
                    case JPEG ->
                            ".jpg".equals(extension)
                                    || ".jpeg".equals(extension);

                    case PNG ->
                            ".png".equals(extension);

                    case WEBP ->
                            ".webp".equals(extension);
                };

        if (!validExtension) {
            throw new InvalidFileException(
                    "File extension does not match file content"
            );
        }
    }

    private DetectedImageType detectImageType(
            byte[] bytes
    ) {
        if (isJpeg(bytes)) {
            return DetectedImageType.JPEG;
        }

        if (isPng(bytes)) {
            return DetectedImageType.PNG;
        }

        if (isWebp(bytes)) {
            return DetectedImageType.WEBP;
        }

        throw new InvalidFileException(
                "Only valid JPEG, PNG and WEBP images are allowed"
        );
    }

    private boolean isJpeg(byte[] bytes) {
        return bytes.length >= 3
                && (bytes[0] & 0xFF) == 0xFF
                && (bytes[1] & 0xFF) == 0xD8
                && (bytes[2] & 0xFF) == 0xFF;
    }

    private boolean isPng(byte[] bytes) {
        int[] signature = {
                0x89, 0x50, 0x4E, 0x47,
                0x0D, 0x0A, 0x1A, 0x0A
        };

        if (bytes.length < signature.length) {
            return false;
        }

        for (int index = 0;
             index < signature.length;
             index++) {

            if ((bytes[index] & 0xFF)
                    != signature[index]) {
                return false;
            }
        }

        return true;
    }

    private boolean isWebp(byte[] bytes) {
        if (bytes.length < 12) {
            return false;
        }

        String riff =
                new String(
                        bytes,
                        0,
                        4,
                        StandardCharsets.US_ASCII
                );

        String webp =
                new String(
                        bytes,
                        8,
                        4,
                        StandardCharsets.US_ASCII
                );

        return "RIFF".equals(riff)
                && "WEBP".equals(webp);
    }

    private String calculateSha256(
            byte[] bytes
    ) {
        try {
            MessageDigest digest =
                    MessageDigest.getInstance(
                            "SHA-256"
                    );

            return HexFormat.of()
                    .formatHex(
                            digest.digest(bytes)
                    );

        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 algorithm is not available",
                    exception
            );
        }
    }

    private Path resolveStoredFilePath(
            String storedName
    ) {
        if (storedName == null
                || storedName.isBlank()
                || storedName.contains("/")
                || storedName.contains("\\")
                || ".".equals(storedName)
                || "..".equals(storedName)) {
            throw new FileStorageException(
                    "Invalid stored file name"
            );
        }

        Path destination =
                storageLocation
                        .resolve(storedName)
                        .normalize();

        if (!destination.startsWith(
                storageLocation
        )) {
            throw new FileStorageException(
                    "Stored file resolves outside upload directory"
            );
        }

        return destination;
    }

    private void validateFileId(Long fileId) {
        if (fileId == null || fileId <= 0) {
            throw new IllegalArgumentException(
                    "File ID must be a positive number"
            );
        }
    }

    private User getCurrentUser() {
        Authentication authentication =
                getRequiredAuthentication();

        return findAuthenticatedUser(
                authentication
        );
    }

    private Authentication getRequiredAuthentication() {
        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || "anonymousUser".equals(
                authentication.getPrincipal()
        )) {
            throw new AccessDeniedException(
                    "Authenticated user is required"
            );
        }

        return authentication;
    }

    private User findAuthenticatedUser(
            Authentication authentication
    ) {
        String usernameOrEmail =
                authentication.getName();

        return userRepository
                .findByEmail(usernameOrEmail)
                .or(() ->
                        userRepository
                                .findByUsername(
                                        usernameOrEmail
                                )
                )
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Authenticated user not found"
                        )
                );
    }

    private void verifyDeletePermission(
            UploadedFile uploadedFile,
            User currentUser,
            Authentication authentication
    ) {
        boolean owner =
                uploadedFile.getUploadedBy() != null
                        && uploadedFile
                        .getUploadedBy()
                        .getId()
                        .equals(currentUser.getId());

        boolean admin =
                authentication
                        .getAuthorities()
                        .stream()
                        .map(
                                GrantedAuthority::getAuthority
                        )
                        .anyMatch(
                                "ROLE_ADMIN"::equals
                        );

        if (!owner && !admin) {
            throw new AccessDeniedException(
                    "You are not allowed to delete this file"
            );
        }
    }

    private LocalDateTime currentDateTime() {
        return LocalDateTime.ofInstant(
                clock.instant(),
                clock.getZone()
        );
    }

    private void deletePhysicalFileQuietly(
            Path filePath
    ) {
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException ignored) {
            // Best-effort rollback i skedarit fizik.
        }
    }

    private enum DetectedImageType {

        JPEG("image/jpeg", ".jpg"),
        PNG("image/png", ".png"),
        WEBP("image/webp", ".webp");

        private final String contentType;
        private final String canonicalExtension;

        DetectedImageType(
                String contentType,
                String canonicalExtension
        ) {
            this.contentType = contentType;
            this.canonicalExtension =
                    canonicalExtension;
        }

        public String contentType() {
            return contentType;
        }

        public String canonicalExtension() {
            return canonicalExtension;
        }
    }
}