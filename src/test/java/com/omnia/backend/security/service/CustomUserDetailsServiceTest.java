package com.omnia.backend.security.service;

import com.omnia.backend.entity.Role;
import com.omnia.backend.entity.User;
import com.omnia.backend.enums.UserStatus;
import com.omnia.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    private static final String EMAIL =
            "user@example.com";

    private static final String USERNAME =
            "TestUser";

    private static final String PASSWORD_HASH =
            "encoded-password";

    @Mock
    private UserRepository userRepository;

    private CustomUserDetailsService userDetailsService;

    @BeforeEach
    void setUp() {
        userDetailsService =
                new CustomUserDetailsService(
                        userRepository
                );
    }

    @Test
    void loadUserByUsername_WithEmail_ShouldReturnUserDetails() {

        User user = createUser(
                UserStatus.ACTIVE,
                true,
                "USER"
        );

        when(
                userRepository.findByEmail(EMAIL)
        ).thenReturn(Optional.of(user));

        UserDetails result =
                userDetailsService.loadUserByUsername(
                        EMAIL
                );

        assertThat(result.getUsername())
                .isEqualTo(EMAIL);

        assertThat(result.getPassword())
                .isEqualTo(PASSWORD_HASH);

        assertThat(result.isEnabled())
                .isTrue();

        assertThat(result.isAccountNonLocked())
                .isTrue();

        assertThat(result.isAccountNonExpired())
                .isTrue();

        assertThat(result.isCredentialsNonExpired())
                .isTrue();

        assertThat(result.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_USER");

        verify(userRepository)
                .findByEmail(EMAIL);

        verify(
                userRepository,
                never()
        ).findByUsername(USERNAME);
    }

    @Test
    void loadUserByUsername_WithEmail_ShouldNormalizeWhitespaceAndCase() {

        User user = createUser(
                UserStatus.ACTIVE,
                true,
                "USER"
        );

        when(
                userRepository.findByEmail(EMAIL)
        ).thenReturn(Optional.of(user));

        UserDetails result =
                userDetailsService.loadUserByUsername(
                        "  USER@EXAMPLE.COM  "
                );

        assertThat(result.getUsername())
                .isEqualTo(EMAIL);

        verify(userRepository)
                .findByEmail(EMAIL);
    }

    @Test
    void loadUserByUsername_WhenEmailNotFound_ShouldTryUsername() {

        User user = createUser(
                UserStatus.ACTIVE,
                true,
                "USER"
        );

        when(
                userRepository.findByEmail(
                        "testuser"
                )
        ).thenReturn(Optional.empty());

        when(
                userRepository.findByUsername(
                        USERNAME
                )
        ).thenReturn(Optional.of(user));

        UserDetails result =
                userDetailsService.loadUserByUsername(
                        "  " + USERNAME + "  "
                );

        assertThat(result.getUsername())
                .isEqualTo(EMAIL);

        verify(userRepository)
                .findByEmail("testuser");

        verify(userRepository)
                .findByUsername(USERNAME);
    }

    @Test
    void loadUserByUsername_WhenUserDoesNotExist_ShouldThrow() {

        when(
                userRepository.findByEmail(
                        "missing@example.com"
                )
        ).thenReturn(Optional.empty());

        when(
                userRepository.findByUsername(
                        "missing@example.com"
                )
        ).thenReturn(Optional.empty());

        assertThatThrownBy(
                () -> userDetailsService
                        .loadUserByUsername(
                                "missing@example.com"
                        )
        )
                .isInstanceOf(
                        UsernameNotFoundException.class
                )
                .hasMessage("User not found");
    }

    @Test
    void loadUserByUsername_WithNullIdentifier_ShouldThrow() {

        assertThatThrownBy(
                () -> userDetailsService
                        .loadUserByUsername(null)
        )
                .isInstanceOf(
                        UsernameNotFoundException.class
                )
                .hasMessage("User not found");

        verifyNoInteractions(userRepository);
    }

    @Test
    void loadUserByUsername_WithBlankIdentifier_ShouldThrow() {

        assertThatThrownBy(
                () -> userDetailsService
                        .loadUserByUsername("   ")
        )
                .isInstanceOf(
                        UsernameNotFoundException.class
                )
                .hasMessage("User not found");

        verifyNoInteractions(userRepository);
    }

    @Test
    void loadUserByUsername_WithUnverifiedEmail_ShouldDisableUser() {

        User user = createUser(
                UserStatus.ACTIVE,
                false,
                "USER"
        );

        when(
                userRepository.findByEmail(EMAIL)
        ).thenReturn(Optional.of(user));

        UserDetails result =
                userDetailsService.loadUserByUsername(
                        EMAIL
                );

        assertThat(result.isEnabled())
                .isFalse();

        assertThat(result.isAccountNonLocked())
                .isTrue();
    }

    @Test
    void loadUserByUsername_WithInactiveUser_ShouldDisableUser() {

        User user = createUser(
                UserStatus.INACTIVE,
                true,
                "USER"
        );

        when(
                userRepository.findByEmail(EMAIL)
        ).thenReturn(Optional.of(user));

        UserDetails result =
                userDetailsService.loadUserByUsername(
                        EMAIL
                );

        assertThat(result.isEnabled())
                .isFalse();

        assertThat(result.isAccountNonLocked())
                .isTrue();
    }

    @Test
    void loadUserByUsername_WithBannedUser_ShouldDisableAndLockUser() {

        User user = createUser(
                UserStatus.BANNED,
                true,
                "USER"
        );

        when(
                userRepository.findByEmail(EMAIL)
        ).thenReturn(Optional.of(user));

        UserDetails result =
                userDetailsService.loadUserByUsername(
                        EMAIL
                );

        assertThat(result.isEnabled())
                .isFalse();

        assertThat(result.isAccountNonLocked())
                .isFalse();
    }

    @Test
    void loadUserByUsername_WithLowercaseRole_ShouldNormalizeAuthority() {

        User user = createUser(
                UserStatus.ACTIVE,
                true,
                " admin "
        );

        when(
                userRepository.findByEmail(EMAIL)
        ).thenReturn(Optional.of(user));

        UserDetails result =
                userDetailsService.loadUserByUsername(
                        EMAIL
                );

        assertThat(result.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_ADMIN");
    }

    private User createUser(
            UserStatus status,
            boolean emailVerified,
            String roleName
    ) {
        Role role = Role.builder()
                .id(1L)
                .name(roleName)
                .description("Test role")
                .build();

        return User.builder()
                .id(10L)
                .firstName("Test")
                .lastName("User")
                .username(USERNAME)
                .email(EMAIL)
                .passwordHash(PASSWORD_HASH)
                .role(role)
                .status(status)
                .emailVerified(emailVerified)
                .build();
    }
}