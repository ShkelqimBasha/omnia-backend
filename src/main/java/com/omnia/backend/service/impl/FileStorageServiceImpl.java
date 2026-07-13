package com.omnia.backend.service.impl;

import com.omnia.backend.common.exception.FileStorageException;
import com.omnia.backend.common.exception.InvalidFileException;
import com.omnia.backend.common.exception.ResourceNotFoundException;
import com.omnia.backend.entity.UploadedFile;
import com.omnia.backend.entity.User;
import com.omnia.backend.repository.UploadedFileRepository;
import com.omnia.backend.repository.UserRepository;
import com.omnia.backend.service.interfaces.FileStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

@Service
public class FileStorageServiceImpl implements FileStorageService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );

    private final UploadedFileRepository uploadedFileRepository;
    private final UserRepository userRepository;
    private final Path storageLocation;

    public FileStorageServiceImpl(
            UploadedFileRepository uploadedFileRepository,
            UserRepository userRepository,
            @Value("${app.file.upload-dir}") String uploadDirectory
    ) {
        this.uploadedFileRepository = uploadedFileRepository;
        this.userRepository = userRepository;

        try {
            this.storageLocation = Paths.get(uploadDirectory)
                    .toAbsolutePath()
                    .normalize();

            Files.createDirectories(this.storageLocation);
        } catch (IOException ex) {
            throw new FileStorageException(
                    "Could not create file upload directory",
                    ex
            );
        }
    }

    @Override
    @Transactional
    public UploadedFile storeFile(MultipartFile file) {

        validateFile(file);

        String extension = getExtension(file.getOriginalFilename());

        String storedName =
                UUID.randomUUID() + extension;

        Path destination = storageLocation
                .resolve(storedName)
                .normalize();

        if (!destination.startsWith(storageLocation)) {
            throw new InvalidFileException(
                    "Invalid file path"
            );
        }

        try {
            Files.copy(
                    file.getInputStream(),
                    destination,
                    StandardCopyOption.REPLACE_EXISTING
            );
        } catch (IOException ex) {
            throw new FileStorageException(
                    "Could not store file",
                    ex
            );
        }

        User currentUser = getCurrentUser();

        UploadedFile uploadedFile = UploadedFile.builder()
                .originalName(file.getOriginalFilename())
                .storedName(storedName)
                .contentType(file.getContentType())
                .size(file.getSize())
                .path(destination.toString())
                .uploadedBy(currentUser)
                .uploadedAt(LocalDateTime.now())
                .build();

        try {
            return uploadedFileRepository.save(uploadedFile);
        } catch (RuntimeException ex) {
            deletePhysicalFileQuietly(destination);
            throw ex;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Resource loadFileAsResource(Long fileId) {

        UploadedFile uploadedFile = getFileMetadata(fileId);

        try {
            Path filePath = Paths.get(uploadedFile.getPath())
                    .toAbsolutePath()
                    .normalize();

            Resource resource = new UrlResource(
                    filePath.toUri()
            );

            if (!resource.exists() || !resource.isReadable()) {
                throw new ResourceNotFoundException(
                        "Stored file could not be found"
                );
            }

            return resource;

        } catch (MalformedURLException ex) {
            throw new FileStorageException(
                    "Invalid stored file path",
                    ex
            );
        }
    }

    @Override
    @Transactional(readOnly = true)
    public UploadedFile getFileMetadata(Long fileId) {

        return uploadedFileRepository.findById(fileId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Uploaded file not found"
                        )
                );
    }

    @Override
    @Transactional
    public void deleteFile(Long fileId) {

        UploadedFile uploadedFile = getFileMetadata(fileId);

        Path filePath = Paths.get(uploadedFile.getPath())
                .toAbsolutePath()
                .normalize();

        try {
            Files.deleteIfExists(filePath);
        } catch (IOException ex) {
            throw new FileStorageException(
                    "Could not delete stored file",
                    ex
            );
        }

        uploadedFileRepository.delete(uploadedFile);
    }

    private void validateFile(MultipartFile file) {

        if (file == null || file.isEmpty()) {
            throw new InvalidFileException(
                    "File must not be empty"
            );
        }

        if (file.getContentType() == null
                || !ALLOWED_CONTENT_TYPES.contains(
                file.getContentType()
        )) {
            throw new InvalidFileException(
                    "Only JPEG, PNG and WEBP images are allowed"
            );
        }

        if (file.getOriginalFilename() == null
                || file.getOriginalFilename().isBlank()) {
            throw new InvalidFileException(
                    "Original filename is missing"
            );
        }
    }

    private String getExtension(String originalFilename) {

        String cleanFilename = Paths.get(originalFilename)
                .getFileName()
                .toString();

        int dotIndex = cleanFilename.lastIndexOf('.');

        if (dotIndex < 0) {
            throw new InvalidFileException(
                    "File extension is missing"
            );
        }

        String extension = cleanFilename
                .substring(dotIndex)
                .toLowerCase();

        if (!Set.of(".jpg", ".jpeg", ".png", ".webp")
                .contains(extension)) {
            throw new InvalidFileException(
                    "Unsupported file extension"
            );
        }

        return extension;
    }

    private User getCurrentUser() {

        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || "anonymousUser".equals(
                authentication.getPrincipal()
        )) {
            throw new InvalidFileException(
                    "Authenticated user is required"
            );
        }

        String usernameOrEmail = authentication.getName();

        return userRepository.findByEmail(usernameOrEmail)
                .or(() ->
                        userRepository.findByUsername(
                                usernameOrEmail
                        )
                )
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Authenticated user not found"
                        )
                );
    }

    private void deletePhysicalFileQuietly(Path filePath) {
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException ignored) {
        }
    }
}