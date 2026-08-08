package com.omnia.backend.service.impl;

import com.omnia.backend.entity.Role;
import com.omnia.backend.entity.User;
import com.omnia.backend.enums.UserStatus;
import com.omnia.backend.mapper.AdminUserMapper;
import com.omnia.backend.repository.OrganizationMemberRepository;
import com.omnia.backend.repository.RoleRepository;
import com.omnia.backend.repository.UserRepository;
import com.omnia.backend.security.service.CurrentUserService;
import com.omnia.backend.service.interfaces.EmailVerificationService;
import com.omnia.backend.service.interfaces.RefreshTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private OrganizationMemberRepository memberRepository;

    @Mock
    private AdminUserMapper userMapper;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EmailVerificationService emailVerificationService;

    private AdminUserServiceImpl adminUserService;
    private User currentAdmin;

    @BeforeEach
    void setUp() {
        adminUserService = new AdminUserServiceImpl(
                userRepository,
                roleRepository,
                memberRepository,
                userMapper,
                currentUserService,
                refreshTokenService,
                passwordEncoder,
                emailVerificationService
        );

        currentAdmin = createUser(
                1L,
                "current-admin",
                "SUPER_ADMIN"
        );

        when(currentUserService.requireCurrentUser())
                .thenReturn(currentAdmin);
        when(currentUserService.hasPlatformAdminAccess(currentAdmin))
                .thenReturn(true);
    }

    @Test
    void deleteUser_WithNormalUser_ShouldSoftDeleteAndRevokeSessions() {
        User targetUser = createUser(2L, "target-user", "USER");
        when(userRepository.findById(2L))
                .thenReturn(Optional.of(targetUser));

        adminUserService.deleteUser(2L);

        assertEquals(UserStatus.INACTIVE, targetUser.getStatus());
        assertNotNull(targetUser.getDeletedAt());
        verify(userRepository).saveAndFlush(targetUser);
        verify(refreshTokenService).revokeAllUserTokens(2L);
    }

    @Test
    void deleteUser_WithOwnAccount_ShouldBeRejected() {
        when(userRepository.findById(1L))
                .thenReturn(Optional.of(currentAdmin));

        assertThrows(
                AccessDeniedException.class,
                () -> adminUserService.deleteUser(1L)
        );

        verify(userRepository, never()).saveAndFlush(currentAdmin);
        verify(refreshTokenService, never()).revokeAllUserTokens(1L);
    }

    @Test
    void deleteUser_WithLastActivePlatformAdmin_ShouldBeRejected() {
        User targetAdmin = createUser(2L, "target-admin", "ADMIN");
        when(userRepository.findById(2L))
                .thenReturn(Optional.of(targetAdmin));
        when(userRepository.countByStatusAndRoleNameIn(
                UserStatus.ACTIVE,
                java.util.Set.of("ADMIN", "SUPER_ADMIN")
        )).thenReturn(1L);

        assertThrows(
                IllegalArgumentException.class,
                () -> adminUserService.deleteUser(2L)
        );

        verify(userRepository, never()).saveAndFlush(targetAdmin);
        verify(refreshTokenService, never()).revokeAllUserTokens(2L);
    }

    @Test
    void deleteUser_WithMoreThanOneActiveAdmin_ShouldDeleteAdmin() {
        User targetAdmin = createUser(2L, "target-admin", "ADMIN");
        when(userRepository.findById(2L))
                .thenReturn(Optional.of(targetAdmin));
        when(userRepository.countByStatusAndRoleNameIn(
                UserStatus.ACTIVE,
                java.util.Set.of("ADMIN", "SUPER_ADMIN")
        )).thenReturn(2L);

        adminUserService.deleteUser(2L);

        assertEquals(UserStatus.INACTIVE, targetAdmin.getStatus());
        assertNotNull(targetAdmin.getDeletedAt());
        verify(userRepository).saveAndFlush(targetAdmin);
        verify(refreshTokenService).revokeAllUserTokens(2L);
    }

    private User createUser(
            Long id,
            String username,
            String roleName
    ) {
        Role role = Role.builder()
                .id(id)
                .name(roleName)
                .build();

        return User.builder()
                .id(id)
                .firstName(username)
                .username(username)
                .email(username + "@example.com")
                .passwordHash("password-hash")
                .role(role)
                .status(UserStatus.ACTIVE)
                .emailVerified(true)
                .build();
    }
}