package com.omnia.backend.service.impl;

import com.omnia.backend.common.exception.EmailNotVerifiedException;
import com.omnia.backend.common.exception.InvalidCredentialsException;
import com.omnia.backend.common.exception.ResourceAlreadyExistsException;
import com.omnia.backend.dto.request.LoginRequest;
import com.omnia.backend.dto.request.RefreshTokenRequest;
import com.omnia.backend.dto.request.RegisterRequest;
import com.omnia.backend.dto.response.AuthResponse;
import com.omnia.backend.dto.response.UserResponse;
import com.omnia.backend.entity.RefreshToken;
import com.omnia.backend.entity.Role;
import com.omnia.backend.entity.User;
import com.omnia.backend.repository.RoleRepository;
import com.omnia.backend.repository.UserRepository;
import com.omnia.backend.security.jwt.JwtService;
import com.omnia.backend.service.interfaces.EmailVerificationService;
import com.omnia.backend.service.interfaces.RefreshTokenService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private EmailVerificationService emailVerificationService;

    @Mock
    private RegisterRequest registerRequest;

    @Mock
    private LoginRequest loginRequest;

    @Mock
    private RefreshTokenRequest refreshTokenRequest;

    @InjectMocks
    private AuthServiceImpl authService;

    private Role userRole;
    private User verifiedUser;
    private RefreshToken refreshToken;

    @BeforeEach
    void setUp() {

        userRole = Role.builder()
                .id(1L)
                .name("USER")
                .build();

        verifiedUser = User.builder()
                .id(1L)
                .firstName("Shkelqim")
                .lastName("Basha")
                .username("shkelqim123")
                .email("shkelqim@example.com")
                .passwordHash("encoded-password")
                .phone("0691234567")
                .role(userRole)
                .emailVerified(true)
                .build();

        refreshToken = mock(RefreshToken.class);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void register_shouldRegisterUserAndSendVerificationEmail() {

        when(registerRequest.getFirstName())
                .thenReturn(" Shkelqim ");

        when(registerRequest.getLastName())
                .thenReturn(" Basha ");

        when(registerRequest.getUsername())
                .thenReturn("shkelqim123");

        when(registerRequest.getEmail())
                .thenReturn(" SHKELQIM@EXAMPLE.COM ");

        when(registerRequest.getPassword())
                .thenReturn("Password@123");

        when(registerRequest.getPhone())
                .thenReturn(" 0691234567 ");

        when(userRepository.existsByEmail("shkelqim@example.com"))
                .thenReturn(false);

        when(userRepository.existsByUsername("shkelqim123"))
                .thenReturn(false);

        when(roleRepository.findByName("USER"))
                .thenReturn(Optional.of(userRole));

        when(passwordEncoder.encode("Password@123"))
                .thenReturn("encoded-password");

        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> {
                    User user = invocation.getArgument(0);
                    user.setId(1L);
                    return user;
                });

        AuthResponse result =
                authService.register(registerRequest);

        assertNotNull(result);
        assertNull(result.getAccessToken());
        assertNull(result.getRefreshToken());
        assertEquals(
                "Registration successful. " +
                        "Please verify your email before logging in.",
                result.getMessage()
        );

        ArgumentCaptor<User> userCaptor =
                ArgumentCaptor.forClass(User.class);

        verify(userRepository).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();

        assertEquals("Shkelqim", savedUser.getFirstName());
        assertEquals("Basha", savedUser.getLastName());
        assertEquals("shkelqim123", savedUser.getUsername());
        assertEquals(
                "shkelqim@example.com",
                savedUser.getEmail()
        );
        assertEquals(
                "encoded-password",
                savedUser.getPasswordHash()
        );
        assertEquals("0691234567", savedUser.getPhone());
        assertEquals(userRole, savedUser.getRole());
        assertFalse(savedUser.getEmailVerified());

        verify(emailVerificationService)
                .createVerificationToken(savedUser);

        verifyNoInteractions(
                jwtService,
                refreshTokenService
        );
    }

    @Test
    void register_shouldThrowWhenEmailAlreadyExists() {

        when(registerRequest.getEmail())
                .thenReturn("shkelqim@example.com");

        /*
         * AuthService normalizon username-in përpara
         * kontrollit të email-it, ndaj duhet ta japim.
         */
        when(registerRequest.getUsername())
                .thenReturn("shkelqim123");

        when(userRepository.existsByEmail(
                "shkelqim@example.com"
        )).thenReturn(true);

        ResourceAlreadyExistsException exception =
                assertThrows(
                        ResourceAlreadyExistsException.class,
                        () -> authService.register(registerRequest)
                );

        assertEquals(
                "Email is already in use",
                exception.getMessage()
        );

        verify(userRepository)
                .existsByEmail("shkelqim@example.com");

        verify(userRepository, never())
                .save(any(User.class));

        verifyNoInteractions(
                roleRepository,
                passwordEncoder,
                jwtService,
                refreshTokenService,
                emailVerificationService
        );
    }

    @Test
    void register_shouldThrowWhenUsernameAlreadyExists() {

        when(registerRequest.getEmail())
                .thenReturn("shkelqim@example.com");

        when(registerRequest.getUsername())
                .thenReturn("shkelqim123");

        when(userRepository.existsByEmail(
                "shkelqim@example.com"
        )).thenReturn(false);

        when(userRepository.existsByUsername(
                "shkelqim123"
        )).thenReturn(true);

        ResourceAlreadyExistsException exception =
                assertThrows(
                        ResourceAlreadyExistsException.class,
                        () -> authService.register(registerRequest)
                );

        assertEquals(
                "Username is already in use",
                exception.getMessage()
        );

        verify(userRepository)
                .existsByUsername("shkelqim123");

        verify(userRepository, never())
                .save(any(User.class));

        verifyNoInteractions(
                roleRepository,
                passwordEncoder,
                jwtService,
                refreshTokenService,
                emailVerificationService
        );
    }

    @Test
    void login_shouldLoginSuccessfullyWithEmail() {

        when(loginRequest.getUsernameOrEmail())
                .thenReturn(" SHKELQIM@EXAMPLE.COM ");

        when(loginRequest.getPassword())
                .thenReturn("Password@123");

        when(userRepository.findByEmail(
                "shkelqim@example.com"
        )).thenReturn(Optional.of(verifiedUser));

        when(passwordEncoder.matches(
                "Password@123",
                "encoded-password"
        )).thenReturn(true);

        when(jwtService.generateToken(
                "shkelqim@example.com"
        )).thenReturn("access-token");

        when(refreshTokenService.createRefreshToken(
                verifiedUser
        )).thenReturn("refresh-token");

        AuthResponse result =
                authService.login(loginRequest);

        assertNotNull(result);
        assertEquals(
                "access-token",
                result.getAccessToken()
        );
        assertEquals(
                "refresh-token",
                result.getRefreshToken()
        );
        assertEquals(
                "Login successful",
                result.getMessage()
        );

        verify(passwordEncoder).matches(
                "Password@123",
                "encoded-password"
        );

        verify(jwtService)
                .generateToken("shkelqim@example.com");

        verify(refreshTokenService)
                .createRefreshToken(verifiedUser);
    }

    @Test
    void login_shouldThrowWhenPasswordIsInvalid() {

        when(loginRequest.getUsernameOrEmail())
                .thenReturn("shkelqim@example.com");

        when(loginRequest.getPassword())
                .thenReturn("WrongPassword");

        when(userRepository.findByEmail(
                "shkelqim@example.com"
        )).thenReturn(Optional.of(verifiedUser));

        when(passwordEncoder.matches(
                "WrongPassword",
                "encoded-password"
        )).thenReturn(false);

        InvalidCredentialsException exception =
                assertThrows(
                        InvalidCredentialsException.class,
                        () -> authService.login(loginRequest)
                );

        assertEquals(
                "Invalid credentials",
                exception.getMessage()
        );

        verifyNoInteractions(
                jwtService,
                refreshTokenService
        );
    }

    @Test
    void login_shouldThrowWhenEmailIsNotVerified() {

        verifiedUser.setEmailVerified(false);

        when(loginRequest.getUsernameOrEmail())
                .thenReturn("shkelqim@example.com");

        when(loginRequest.getPassword())
                .thenReturn("Password@123");

        when(userRepository.findByEmail(
                "shkelqim@example.com"
        )).thenReturn(Optional.of(verifiedUser));

        when(passwordEncoder.matches(
                "Password@123",
                "encoded-password"
        )).thenReturn(true);

        EmailNotVerifiedException exception =
                assertThrows(
                        EmailNotVerifiedException.class,
                        () -> authService.login(loginRequest)
                );

        assertEquals(
                "Please verify your email before logging in",
                exception.getMessage()
        );

        verifyNoInteractions(
                jwtService,
                refreshTokenService
        );
    }

    @Test
    void getCurrentUser_shouldReturnAuthenticatedUser() {

        /*
         * Konstruktori me authorities krijon një Authentication
         * me isAuthenticated() = true.
         */
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        "shkelqim@example.com",
                        null,
                        AuthorityUtils.NO_AUTHORITIES
                );

        SecurityContextHolder
                .getContext()
                .setAuthentication(authentication);

        when(userRepository.findByEmail(
                "shkelqim@example.com"
        )).thenReturn(Optional.of(verifiedUser));

        UserResponse result =
                authService.getCurrentUser();

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals(
                "Shkelqim",
                result.getFirstName()
        );
        assertEquals(
                "Basha",
                result.getLastName()
        );
        assertEquals(
                "shkelqim123",
                result.getUsername()
        );
        assertEquals(
                "shkelqim@example.com",
                result.getEmail()
        );
        assertEquals(
                "USER",
                result.getRole()
        );
    }

    @Test
    void getCurrentUser_shouldThrowWhenNotAuthenticated() {

        SecurityContextHolder.clearContext();

        InvalidCredentialsException exception =
                assertThrows(
                        InvalidCredentialsException.class,
                        () -> authService.getCurrentUser()
                );

        assertEquals(
                "Unauthorized",
                exception.getMessage()
        );

        verifyNoInteractions(userRepository);
    }

    @Test
    void refreshToken_shouldRotateTokensSuccessfully() {

        when(refreshTokenRequest.getRefreshToken())
                .thenReturn("old-refresh-token");

        when(refreshTokenService.verifyRefreshToken(
                "old-refresh-token"
        )).thenReturn(refreshToken);

        when(refreshToken.getUser())
                .thenReturn(verifiedUser);

        RefreshToken newRefreshToken =
                mock(RefreshToken.class);

        when(refreshTokenService.createRefreshToken(
                verifiedUser
        )).thenReturn("new-refresh-token");

        when(jwtService.generateToken(
                "shkelqim@example.com"
        )).thenReturn("new-access-token");

        AuthResponse result =
                authService.refreshToken(refreshTokenRequest);

        assertNotNull(result);
        assertEquals(
                "new-access-token",
                result.getAccessToken()
        );
        assertEquals(
                "new-refresh-token",
                result.getRefreshToken()
        );
        assertEquals(
                "Token refreshed successfully",
                result.getMessage()
        );

        verify(refreshTokenService)
                .verifyRefreshToken("old-refresh-token");

        verify(refreshTokenService)
                .createRefreshToken(verifiedUser);

        verify(jwtService)
                .generateToken("shkelqim@example.com");
    }

    @Test
    void refreshToken_shouldRevokeTokensWhenEmailIsNotVerified() {

        verifiedUser.setEmailVerified(false);

        when(refreshTokenRequest.getRefreshToken())
                .thenReturn("old-refresh-token");

        when(refreshTokenService.verifyRefreshToken(
                "old-refresh-token"
        )).thenReturn(refreshToken);

        when(refreshToken.getUser())
                .thenReturn(verifiedUser);

        EmailNotVerifiedException exception =
                assertThrows(
                        EmailNotVerifiedException.class,
                        () -> authService.refreshToken(
                                refreshTokenRequest
                        )
                );

        assertEquals(
                "Please verify your email before refreshing authentication",
                exception.getMessage()
        );

        verify(refreshTokenService)
                .revokeAllUserTokens(1L);

        verify(refreshTokenService, never())
                .createRefreshToken(any(User.class));

        verifyNoInteractions(jwtService);
    }

    @Test
    void logout_shouldRevokeRefreshToken() {

        when(refreshTokenRequest.getRefreshToken())
                .thenReturn("refresh-token");

        authService.logout(refreshTokenRequest);

        verify(refreshTokenService)
                .revokeToken("refresh-token");
    }
}