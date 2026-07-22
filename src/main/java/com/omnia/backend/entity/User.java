package com.omnia.backend.entity;

import com.omnia.backend.enums.UserStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(
            name = "role_id",
            nullable = false
    )
    private Role role;

    @Column(
            name = "first_name",
            nullable = false,
            length = 100
    )
    private String firstName;

    @Column(
            name = "last_name",
            length = 100
    )
    private String lastName;

    @Column(
            nullable = false,
            unique = true,
            length = 100
    )
    private String username;

    @Column(
            nullable = false,
            unique = true,
            length = 150
    )
    private String email;

    @Column(
            name = "password_hash",
            nullable = false
    )
    private String passwordHash;

    @Column(length = 30)
    private String phone;

    /*
     * Kept only for users created before secure file-upload
     * avatar integration.
     */
    @Column(
            name = "profile_image",
            length = 500
    )
    private String legacyProfileImage;

    /*
     * New user avatars reference securely uploaded files.
     *
     * The database unique index ensures that one uploaded
     * file cannot be assigned as the avatar of multiple users.
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "avatar_file_id",
            unique = true
    )
    private UploadedFile avatarFile;

    @Builder.Default
    @Column(
            name = "email_verified",
            nullable = false
    )
    private Boolean emailVerified = false;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(nullable = false)
    private UserStatus status = UserStatus.ACTIVE;

    @Column(
            name = "created_at",
            insertable = false,
            updatable = false
    )
    private LocalDateTime createdAt;

    @Column(
            name = "updated_at",
            insertable = false,
            updatable = false
    )
    private LocalDateTime updatedAt;

    @OneToMany(
            mappedBy = "user",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    @Builder.Default
    private List<RefreshToken> refreshTokens =
            new ArrayList<>();

    @OneToMany(
            mappedBy = "user",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    @Builder.Default
    private List<EmailVerificationToken>
            emailVerificationTokens =
            new ArrayList<>();

    @OneToMany(
            mappedBy = "user",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    @Builder.Default
    private List<PasswordResetToken>
            passwordResetTokens =
            new ArrayList<>();

    public boolean hasUploadedAvatar() {
        return avatarFile != null;
    }

    public boolean hasLegacyAvatar() {
        return legacyProfileImage != null
                && !legacyProfileImage.isBlank();
    }
}