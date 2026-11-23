package com.omnia.backend.service.impl;

import com.omnia.backend.common.exception.ConflictException;
import com.omnia.backend.dto.request.DeleteAccountRequest;
import com.omnia.backend.entity.OrganizationMember;
import com.omnia.backend.entity.User;
import com.omnia.backend.enums.OrganizationMemberRole;
import com.omnia.backend.enums.OrganizationMemberStatus;
import com.omnia.backend.enums.UserStatus;
import com.omnia.backend.repository.EmailVerificationTokenRepository;
import com.omnia.backend.repository.OrganizationMemberRepository;
import com.omnia.backend.repository.PasswordResetTokenRepository;
import com.omnia.backend.repository.UserRepository;
import com.omnia.backend.repository.UserSettingsRepository;
import com.omnia.backend.security.service.CurrentUserService;
import com.omnia.backend.service.interfaces.RefreshTokenService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Self-service account deletion ({@code DELETE /api/users/me}).
 *
 * Same soft delete as the admin delete (status INACTIVE + deleted_at, refresh
 * tokens revoked), plus the personal data on the user row is anonymised so the
 * e-mail and Google identity are released. Orders keep their own shipping
 * snapshot for accounting.
 */
@Service
public class AccountDeletionService {

    static final String DELETED_EMAIL_DOMAIN = "deleted.omnia.invalid";

    private final CurrentUserService currentUserService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final OrganizationMemberRepository organizationMemberRepository;

    public AccountDeletionService(
            CurrentUserService currentUserService,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            RefreshTokenService refreshTokenService,
            PasswordResetTokenRepository passwordResetTokenRepository,
            EmailVerificationTokenRepository emailVerificationTokenRepository,
            UserSettingsRepository userSettingsRepository,
            OrganizationMemberRepository organizationMemberRepository
    ) {
        this.currentUserService = currentUserService;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.emailVerificationTokenRepository = emailVerificationTokenRepository;
        this.userSettingsRepository = userSettingsRepository;
        this.organizationMemberRepository = organizationMemberRepository;
    }

    @Transactional
    public void deleteCurrentAccount(DeleteAccountRequest request) {

        User user = currentUserService.requireCurrentUser();
        DeleteAccountRequest body =
                request == null ? new DeleteAccountRequest() : request;

        verifyConfirmation(user, body);

        if (currentUserService.hasPlatformAdminAccess(user)) {
            throw new ConflictException(
                    "Platform administrators cannot delete their own account"
            );
        }

        List<OrganizationMember> memberships =
                organizationMemberRepository
                        .findAllByUserIdAndStatusOrderByOrganizationNameAsc(
                                user.getId(),
                                OrganizationMemberStatus.ACTIVE
                        );

        rejectIfSoleOwner(memberships);

        for (OrganizationMember membership : memberships) {
            membership.setStatus(OrganizationMemberStatus.INACTIVE);
        }
        organizationMemberRepository.saveAll(memberships);

        anonymise(user);
        userRepository.saveAndFlush(user);

        refreshTokenService.revokeAllUserTokens(user.getId());
        passwordResetTokenRepository.deleteByUserId(user.getId());
        emailVerificationTokenRepository.deleteByUserId(user.getId());
        userSettingsRepository.findById(user.getId())
                .ifPresent(userSettingsRepository::delete);
    }

    private void verifyConfirmation(User user, DeleteAccountRequest body) {

        String password = body.getCurrentPassword();

        if (password != null && !password.isEmpty()) {
            if (!passwordEncoder.matches(password, user.getPasswordHash())) {
                throw new IllegalArgumentException("Current password is incorrect");
            }
            return;
        }

        // Google sign-in accounts have a random password they never saw.
        boolean googleAccount =
                user.getGoogleUid34() != null
                        && !user.getGoogleUid34().isBlank();

        if (googleAccount && Boolean.TRUE.equals(body.getConfirmGoogle())) {
            return;
        }

        throw new IllegalArgumentException(
                googleAccount
                        ? "Confirm the deletion of your Google account"
                        : "Current password is required"
        );
    }

    private void rejectIfSoleOwner(List<OrganizationMember> memberships) {

        for (OrganizationMember membership : memberships) {
            if (membership.getMembershipRole() != OrganizationMemberRole.OWNER
                    || membership.getOrganization() == null) {
                continue;
            }

            long owners =
                    organizationMemberRepository
                            .countByOrganizationIdAndMembershipRoleAndStatus(
                                    membership.getOrganization().getId(),
                                    OrganizationMemberRole.OWNER,
                                    OrganizationMemberStatus.ACTIVE
                            );

            if (owners <= 1) {
                throw new ConflictException(
                        "Transfer ownership of your organization before deleting your account"
                );
            }
        }
    }

    private void anonymise(User user) {

        Long id = user.getId();

        user.setStatus(UserStatus.INACTIVE);
        user.setDeletedAt(LocalDateTime.now());
        user.setEmail("deleted-" + id + "@" + DELETED_EMAIL_DOMAIN);
        user.setUsername("deleted_" + id);
        user.setFirstName("Përdorues");
        user.setLastName("i fshirë");
        user.setPhone(null);
        user.setLegacyProfileImage(null);
        user.setAvatarFile(null);
        user.setGoogleUid34(null);
        user.setEmailVerified(false);
        user.setPasswordHash(
                passwordEncoder.encode(
                        // 64 chars: BCrypt rejects passwords over 72 bytes.
                        UUID.randomUUID().toString().replace("-", "")
                                + UUID.randomUUID().toString().replace("-", "")
                )
        );
    }
}
