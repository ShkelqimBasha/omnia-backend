package com.omnia.backend.service.impl;

import com.omnia.backend.common.exception.ResourceNotFoundException;
import com.omnia.backend.dto.response.UserResponse;
import com.omnia.backend.entity.UploadedFile;
import com.omnia.backend.entity.User;
import com.omnia.backend.mapper.UserMapper;
import com.omnia.backend.repository.ProductImageRepository;
import com.omnia.backend.repository.UploadedFileRepository;
import com.omnia.backend.repository.UserRepository;
import com.omnia.backend.service.interfaces.UserProfileService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
public class UserProfileServiceImpl
        implements UserProfileService {

    private static final Set<String>
            ALLOWED_AVATAR_CONTENT_TYPES =
            Set.of(
                    "image/jpeg",
                    "image/png",
                    "image/webp"
            );

    private final UserRepository userRepository;
    private final UploadedFileRepository uploadedFileRepository;
    private final ProductImageRepository productImageRepository;

    public UserProfileServiceImpl(
            UserRepository userRepository,
            UploadedFileRepository uploadedFileRepository,
            ProductImageRepository productImageRepository
    ) {
        this.userRepository = userRepository;
        this.uploadedFileRepository =
                uploadedFileRepository;
        this.productImageRepository =
                productImageRepository;
    }

    @Override
    @Transactional
    public UserResponse updateAvatar(
            Long uploadedFileId
    ) {
        validateUploadedFileId(uploadedFileId);

        User currentUser = getCurrentUser();

        UploadedFile uploadedFile =
                uploadedFileRepository
                        .findById(uploadedFileId)
                        .orElseThrow(() ->
                                new ResourceNotFoundException(
                                        "Uploaded file not found"
                                )
                        );

        assertFileOwnership(
                uploadedFile,
                currentUser
        );

        assertValidImage(uploadedFile);

        if (productImageRepository
                .existsByUploadedFileId(
                        uploadedFileId
                )) {
            throw new IllegalArgumentException(
                    "Uploaded file is already attached "
                            + "to a product image"
            );
        }

        if (userRepository
                .existsByAvatarFileIdAndUserIdNot(
                        uploadedFileId,
                        currentUser.getId()
                )) {
            throw new IllegalArgumentException(
                    "Uploaded file is already used "
                            + "as another user's avatar"
            );
        }

        currentUser.setAvatarFile(uploadedFile);
        currentUser.setLegacyProfileImage(null);

        User savedUser =
                userRepository.saveAndFlush(
                        currentUser
                );

        return UserMapper.toResponse(savedUser);
    }

    @Override
    @Transactional
    public UserResponse removeAvatar() {

        User currentUser = getCurrentUser();

        /*
         * Remove only the avatar relationship.
         *
         * The uploaded file is retained so that filesystem
         * changes cannot become inconsistent with a rolled-back
         * database transaction. The owner can explicitly delete
         * the detached file through /api/files/{fileId}.
         */
        currentUser.setAvatarFile(null);
        currentUser.setLegacyProfileImage(null);

        User savedUser =
                userRepository.saveAndFlush(
                        currentUser
                );

        return UserMapper.toResponse(savedUser);
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
            throw new AccessDeniedException(
                    "Authenticated user is required"
            );
        }

        String usernameOrEmail =
                authentication.getName();

        return userRepository
                .findByEmail(usernameOrEmail)
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

    private void assertFileOwnership(
            UploadedFile uploadedFile,
            User currentUser
    ) {
        User owner = uploadedFile.getUploadedBy();

        if (owner == null
                || owner.getId() == null
                || !owner.getId().equals(
                currentUser.getId()
        )) {
            throw new AccessDeniedException(
                    "You can only use your own "
                            + "uploaded file as avatar"
            );
        }
    }

    private void assertValidImage(
            UploadedFile uploadedFile
    ) {
        if (uploadedFile.getContentType() == null
                || !ALLOWED_AVATAR_CONTENT_TYPES
                .contains(
                        uploadedFile.getContentType()
                )) {
            throw new IllegalArgumentException(
                    "Avatar must be a JPEG, PNG "
                            + "or WEBP image"
            );
        }
    }

    private void validateUploadedFileId(
            Long uploadedFileId
    ) {
        if (uploadedFileId == null
                || uploadedFileId <= 0) {
            throw new IllegalArgumentException(
                    "Avatar file id must be positive"
            );
        }
    }
}