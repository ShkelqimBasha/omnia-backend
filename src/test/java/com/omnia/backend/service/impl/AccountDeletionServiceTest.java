package com.omnia.backend.service.impl;

import com.omnia.backend.common.exception.ConflictException;
import com.omnia.backend.dto.request.DeleteAccountRequest;
import com.omnia.backend.entity.Organization;
import com.omnia.backend.entity.OrganizationMember;
import com.omnia.backend.entity.User;
import com.omnia.backend.entity.UserSettings;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AccountDeletionServiceTest {

    @Mock CurrentUserService currentUserService;
    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock RefreshTokenService refreshTokenService;
    @Mock PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock EmailVerificationTokenRepository emailVerificationTokenRepository;
    @Mock UserSettingsRepository userSettingsRepository;
    @Mock OrganizationMemberRepository organizationMemberRepository;

    @InjectMocks AccountDeletionService service;

    User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(7L)
                .firstName("Real")
                .lastName("User")
                .username("real_user")
                .email("real@example.com")
                .phone("+38344111222")
                .passwordHash("old-hash")
                .emailVerified(true)
                .status(UserStatus.ACTIVE)
                .build();

        when(currentUserService.requireCurrentUser()).thenReturn(user);
        when(passwordEncoder.matches("Secret123", "old-hash")).thenReturn(true);
        when(passwordEncoder.encode(anyString())).thenReturn("random-hash");
        when(organizationMemberRepository
                .findAllByUserIdAndStatusOrderByOrganizationNameAsc(
                        7L, OrganizationMemberStatus.ACTIVE))
                .thenReturn(List.of());
    }

    @Test
    void passwordAccount_withCorrectPassword_isSoftDeletedAndAnonymised() {
        UserSettings settings = UserSettings.builder().userId(7L).build();
        when(userSettingsRepository.findById(7L)).thenReturn(Optional.of(settings));

        service.deleteCurrentAccount(password("Secret123"));

        assertEquals(UserStatus.INACTIVE, user.getStatus());
        assertNotNull(user.getDeletedAt());
        assertEquals("deleted-7@deleted.omnia.invalid", user.getEmail());
        assertEquals("deleted_7", user.getUsername());
        assertNull(user.getPhone());
        assertNull(user.getGoogleUid34());
        assertFalse(user.getEmailVerified());
        assertEquals("random-hash", user.getPasswordHash());

        verify(userRepository).saveAndFlush(user);
        verify(refreshTokenService).revokeAllUserTokens(7L);
        verify(passwordResetTokenRepository).deleteByUserId(7L);
        verify(emailVerificationTokenRepository).deleteByUserId(7L);
        verify(userSettingsRepository).delete(settings);
    }

    @Test
    void wrongPassword_changesNothing() {
        assertThrows(IllegalArgumentException.class,
                () -> service.deleteCurrentAccount(password("wrong")));

        assertEquals("real@example.com", user.getEmail());
        verify(userRepository, never()).saveAndFlush(any());
        verifyNoInteractions(refreshTokenService);
    }

    @Test
    void passwordAccount_withoutPassword_isRejectedEvenWithGoogleFlag() {
        DeleteAccountRequest request =
                DeleteAccountRequest.builder().confirmGoogle(true).build();

        assertThrows(IllegalArgumentException.class,
                () -> service.deleteCurrentAccount(request));
        assertThrows(IllegalArgumentException.class,
                () -> service.deleteCurrentAccount(null));

        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void googleAccount_confirmsWithFlag() {
        user.setGoogleUid34("google-uid");

        service.deleteCurrentAccount(
                DeleteAccountRequest.builder().confirmGoogle(true).build());

        assertNull(user.getGoogleUid34());
        assertEquals(UserStatus.INACTIVE, user.getStatus());
        verify(refreshTokenService).revokeAllUserTokens(7L);
    }

    @Test
    void googleAccount_withoutFlag_isRejected() {
        user.setGoogleUid34("google-uid");

        assertThrows(IllegalArgumentException.class,
                () -> service.deleteCurrentAccount(new DeleteAccountRequest()));

        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void platformAdmin_cannotDeleteOwnAccount() {
        when(currentUserService.hasPlatformAdminAccess(user)).thenReturn(true);

        assertThrows(ConflictException.class,
                () -> service.deleteCurrentAccount(password("Secret123")));

        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void soleOrganizationOwner_mustTransferOwnershipFirst() {
        OrganizationMember membership = membership(OrganizationMemberRole.OWNER);
        when(organizationMemberRepository
                .findAllByUserIdAndStatusOrderByOrganizationNameAsc(
                        7L, OrganizationMemberStatus.ACTIVE))
                .thenReturn(List.of(membership));
        when(organizationMemberRepository
                .countByOrganizationIdAndMembershipRoleAndStatus(
                        3L, OrganizationMemberRole.OWNER, OrganizationMemberStatus.ACTIVE))
                .thenReturn(1L);

        assertThrows(ConflictException.class,
                () -> service.deleteCurrentAccount(password("Secret123")));

        assertEquals(OrganizationMemberStatus.ACTIVE, membership.getStatus());
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void staffMembershipsAreDeactivated() {
        OrganizationMember membership = membership(OrganizationMemberRole.STAFF);
        when(organizationMemberRepository
                .findAllByUserIdAndStatusOrderByOrganizationNameAsc(
                        7L, OrganizationMemberStatus.ACTIVE))
                .thenReturn(List.of(membership));

        service.deleteCurrentAccount(password("Secret123"));

        assertEquals(OrganizationMemberStatus.INACTIVE, membership.getStatus());
        verify(organizationMemberRepository).saveAll(List.of(membership));
    }

    private static DeleteAccountRequest password(String value) {
        return DeleteAccountRequest.builder().currentPassword(value).build();
    }

    private static OrganizationMember membership(OrganizationMemberRole role) {
        return OrganizationMember.builder()
                .id(11L)
                .organization(Organization.builder().id(3L).build())
                .membershipRole(role)
                .status(OrganizationMemberStatus.ACTIVE)
                .build();
    }
}
