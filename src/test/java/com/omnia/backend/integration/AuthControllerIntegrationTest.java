package com.omnia.backend.integration;

import com.omnia.backend.dto.request.LoginRequest;
import com.omnia.backend.dto.request.RefreshTokenRequest;
import com.omnia.backend.dto.request.RegisterRequest;
import com.omnia.backend.dto.request.ResendVerificationRequest;
import com.omnia.backend.entity.EmailVerificationToken;
import com.omnia.backend.entity.RefreshToken;
import com.omnia.backend.entity.Role;
import com.omnia.backend.entity.User;
import com.omnia.backend.repository.EmailVerificationTokenRepository;
import com.omnia.backend.repository.RefreshTokenRepository;
import com.omnia.backend.repository.RoleRepository;
import com.omnia.backend.repository.UserRepository;
import com.omnia.backend.service.interfaces.EmailService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthControllerIntegrationTest
        extends AbstractIntegrationTest {

    private static final String TEST_PASSWORD =
            "Password123!";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private EmailVerificationTokenRepository
            emailVerificationTokenRepository;

    /*
     * Mock-ojmë vetëm dërgimin real të email-it.
     * EmailVerificationService mbetet real dhe testohet
     * bashkë me controller, repository dhe databazën.
     */
    @MockitoBean
    private EmailService emailService;

    @Test
    void register_WithValidRequest_ShouldCreateUser()
            throws Exception {

        String uniqueValue = uniqueValue();

        String username =
                "user_" + uniqueValue;

        String email =
                "user_" + uniqueValue
                        + "@example.com";

        RegisterRequest request =
                new RegisterRequest();

        request.setFirstName("Test");
        request.setLastName("User");
        request.setUsername(username);
        request.setEmail(email);
        request.setPassword(TEST_PASSWORD);
        request.setPhone("+355691234567");

        mockMvc.perform(
                        post("/api/auth/register")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        objectMapper.writeValueAsString(
                                                request
                                        )
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_JSON
                        )
                )
                .andExpect(
                        jsonPath("$.accessToken").isEmpty()
                )
                .andExpect(
                        jsonPath("$.refreshToken").isEmpty()
                )
                .andExpect(
                        jsonPath("$.message").value(
                                "Registration successful. "
                                        + "Please verify your email "
                                        + "before logging in."
                        )
                );

        User savedUser =
                userRepository.findByEmail(email)
                        .orElseThrow();

        assertThat(savedUser.getFirstName())
                .isEqualTo("Test");

        assertThat(savedUser.getLastName())
                .isEqualTo("User");

        assertThat(savedUser.getUsername())
                .isEqualTo(username);

        assertThat(savedUser.getEmail())
                .isEqualTo(email);

        assertThat(savedUser.getPhone())
                .isEqualTo("+355691234567");

        assertThat(savedUser.getEmailVerified())
                .isFalse();

        assertThat(savedUser.getRole().getName())
                .isEqualTo("USER");

        assertThat(
                passwordEncoder.matches(
                        TEST_PASSWORD,
                        savedUser.getPasswordHash()
                )
        ).isTrue();

        verify(emailService)
                .sendEmailVerification(
                        eq(email),
                        eq("Test User"),
                        any(String.class)
                );

        EmailVerificationToken verificationToken =
                emailVerificationTokenRepository
                        .findByUserId(savedUser.getId())
                        .orElseThrow();

        assertThat(verificationToken.getTokenHash())
                .hasSize(64);

        assertThat(verificationToken.getUsed())
                .isFalse();
    }

    @Test
    void register_WithExistingEmail_ShouldReturnError()
            throws Exception {

        String uniqueValue = uniqueValue();

        String existingEmail =
                "existing_" + uniqueValue
                        + "@example.com";

        Role userRole = getUserRole();

        User existingUser =
                User.builder()
                        .firstName("Existing")
                        .lastName("User")
                        .username(
                                "existing_" + uniqueValue
                        )
                        .email(existingEmail)
                        .passwordHash(
                                passwordEncoder.encode(
                                        TEST_PASSWORD
                                )
                        )
                        .role(userRole)
                        .emailVerified(false)
                        .build();

        userRepository.save(existingUser);

        RegisterRequest request =
                new RegisterRequest();

        request.setFirstName("Another");
        request.setLastName("User");
        request.setUsername(
                "another_" + uniqueValue
        );
        request.setEmail(existingEmail);
        request.setPassword(TEST_PASSWORD);

        mockMvc.perform(
                        post("/api/auth/register")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        objectMapper.writeValueAsString(
                                                request
                                        )
                                )
                )
                .andExpect(status().isConflict());
    }

    @Test
    void login_WithVerifiedUser_ShouldReturnTokens()
            throws Exception {

        User verifiedUser =
                createVerifiedUser();

        LoginRequest request =
                createLoginRequest(verifiedUser);

        mockMvc.perform(
                        post("/api/auth/login")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        objectMapper.writeValueAsString(
                                                request
                                        )
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_JSON
                        )
                )
                .andExpect(
                        jsonPath("$.accessToken").isString()
                )
                .andExpect(
                        jsonPath("$.accessToken").isNotEmpty()
                )
                .andExpect(
                        jsonPath("$.refreshToken").isString()
                )
                .andExpect(
                        jsonPath("$.refreshToken").isNotEmpty()
                )
                .andExpect(
                        jsonPath("$.message").value(
                                "Login successful"
                        )
                );
    }

    @Test
    void login_WithUnverifiedUser_ShouldReturnError()
            throws Exception {

        User unverifiedUser =
                createUnverifiedUser();

        LoginRequest request =
                createLoginRequest(unverifiedUser);

        mockMvc.perform(
                        post("/api/auth/login")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        objectMapper.writeValueAsString(
                                                request
                                        )
                                )
                )
                .andExpect(status().isForbidden());
    }

    @Test
    void refreshToken_WithValidToken_ShouldRotateTokens()
            throws Exception {

        User user = createVerifiedUser();

        String currentRawToken =
                loginAndGetRefreshToken(user);

        RefreshTokenRequest request =
                createRefreshTokenRequest(
                        currentRawToken
                );

        MvcResult result =
                mockMvc.perform(
                                post("/api/auth/refresh")
                                        .contentType(
                                                MediaType.APPLICATION_JSON
                                        )
                                        .content(
                                                objectMapper
                                                        .writeValueAsString(
                                                                request
                                                        )
                                        )
                        )
                        .andExpect(status().isOk())
                        .andExpect(
                                content()
                                        .contentTypeCompatibleWith(
                                                MediaType.APPLICATION_JSON
                                        )
                        )
                        .andExpect(
                                jsonPath("$.accessToken")
                                        .isNotEmpty()
                        )
                        .andExpect(
                                jsonPath("$.refreshToken")
                                        .isNotEmpty()
                        )
                        .andExpect(
                                jsonPath("$.message").value(
                                        "Token refreshed successfully"
                                )
                        )
                        .andReturn();

        String newRawToken =
                objectMapper.readTree(
                                result.getResponse()
                                        .getContentAsString()
                        )
                        .get("refreshToken")
                        .asText();

        assertThat(newRawToken)
                .isNotEqualTo(currentRawToken);

        RefreshToken oldStoredToken =
                refreshTokenRepository
                        .findByTokenHash(
                                hashToken(currentRawToken)
                        )
                        .orElseThrow();

        RefreshToken newStoredToken =
                refreshTokenRepository
                        .findByTokenHash(
                                hashToken(newRawToken)
                        )
                        .orElseThrow();

        assertThat(oldStoredToken.getRevoked())
                .isTrue();

        assertThat(newStoredToken.getRevoked())
                .isFalse();

        assertThat(oldStoredToken.getTokenHash())
                .isNotEqualTo(currentRawToken);

        assertThat(newStoredToken.getTokenHash())
                .isNotEqualTo(newRawToken);
    }

    @Test
    void refreshToken_WithInvalidToken_ShouldReturnUnauthorized()
            throws Exception {

        RefreshTokenRequest request =
                createRefreshTokenRequest(
                        "invalid-refresh-token"
                );

        mockMvc.perform(
                        post("/api/auth/refresh")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        objectMapper.writeValueAsString(
                                                request
                                        )
                                )
                )
                .andExpect(status().isUnauthorized())
                .andExpect(
                        jsonPath("$.status").value(401)
                )
                .andExpect(
                        jsonPath("$.error").value(
                                "Unauthorized"
                        )
                )
                .andExpect(
                        jsonPath("$.message").value(
                                "Invalid refresh token"
                        )
                )
                .andExpect(
                        jsonPath("$.path").value(
                                "/api/auth/refresh"
                        )
                );
    }

    @Test
    void refreshToken_WithExpiredToken_ShouldReturnUnauthorized()
            throws Exception {

        User user = createVerifiedUser();

        String rawToken =
                loginAndGetRefreshToken(user);

        RefreshToken storedToken =
                refreshTokenRepository
                        .findByTokenHash(
                                hashToken(rawToken)
                        )
                        .orElseThrow();

        storedToken.setExpiresAt(
                LocalDateTime.now(ZoneOffset.UTC)
                        .minusSeconds(1)
        );

        refreshTokenRepository
                .saveAndFlush(storedToken);

        RefreshTokenRequest request =
                createRefreshTokenRequest(rawToken);

        mockMvc.perform(
                        post("/api/auth/refresh")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        objectMapper.writeValueAsString(
                                                request
                                        )
                                )
                )
                .andExpect(status().isUnauthorized())
                .andExpect(
                        jsonPath("$.status").value(401)
                )
                .andExpect(
                        jsonPath("$.error").value(
                                "Unauthorized"
                        )
                )
                .andExpect(
                        jsonPath("$.message").value(
                                "Refresh token has expired"
                        )
                )
                .andExpect(
                        jsonPath("$.path").value(
                                "/api/auth/refresh"
                        )
                );
    }

    @Test
    void refreshToken_WithRevokedToken_ShouldReturnUnauthorized()
            throws Exception {

        User user = createVerifiedUser();

        String rawToken =
                loginAndGetRefreshToken(user);

        RefreshToken storedToken =
                refreshTokenRepository
                        .findByTokenHash(
                                hashToken(rawToken)
                        )
                        .orElseThrow();

        storedToken.setRevoked(true);

        refreshTokenRepository
                .saveAndFlush(storedToken);

        RefreshTokenRequest request =
                createRefreshTokenRequest(rawToken);

        mockMvc.perform(
                        post("/api/auth/refresh")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        objectMapper.writeValueAsString(
                                                request
                                        )
                                )
                )
                .andExpect(status().isUnauthorized())
                .andExpect(
                        jsonPath("$.status").value(401)
                )
                .andExpect(
                        jsonPath("$.error").value(
                                "Unauthorized"
                        )
                )
                .andExpect(
                        jsonPath("$.message").value(
                                "Refresh token has been revoked"
                        )
                )
                .andExpect(
                        jsonPath("$.path").value(
                                "/api/auth/refresh"
                        )
                );
    }

    @Test
    void logout_WithValidToken_ShouldRevokeTokenAndPreventRefresh()
            throws Exception {

        User user = createVerifiedUser();

        String rawToken =
                loginAndGetRefreshToken(user);

        RefreshTokenRequest request =
                createRefreshTokenRequest(rawToken);

        mockMvc.perform(
                        post("/api/auth/logout")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        objectMapper.writeValueAsString(
                                                request
                                        )
                                )
                )
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        RefreshToken storedToken =
                refreshTokenRepository
                        .findByTokenHash(
                                hashToken(rawToken)
                        )
                        .orElseThrow();

        assertThat(storedToken.getRevoked())
                .isTrue();

        mockMvc.perform(
                        post("/api/auth/refresh")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        objectMapper.writeValueAsString(
                                                request
                                        )
                                )
                )
                .andExpect(status().isUnauthorized())
                .andExpect(
                        jsonPath("$.message").value(
                                "Refresh token has been revoked"
                        )
                );
    }

    @Test
    void logout_WithAlreadyRevokedToken_ShouldBeIdempotent()
            throws Exception {

        User user = createVerifiedUser();

        String rawToken =
                loginAndGetRefreshToken(user);

        RefreshTokenRequest request =
                createRefreshTokenRequest(rawToken);

        mockMvc.perform(
                        post("/api/auth/logout")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        objectMapper.writeValueAsString(
                                                request
                                        )
                                )
                )
                .andExpect(status().isNoContent());

        mockMvc.perform(
                        post("/api/auth/logout")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        objectMapper.writeValueAsString(
                                                request
                                        )
                                )
                )
                .andExpect(status().isNoContent());
    }

    @Test
    void logout_WithInvalidToken_ShouldReturnUnauthorized()
            throws Exception {

        RefreshTokenRequest request =
                createRefreshTokenRequest(
                        "invalid-refresh-token"
                );

        mockMvc.perform(
                        post("/api/auth/logout")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        objectMapper.writeValueAsString(
                                                request
                                        )
                                )
                )
                .andExpect(status().isUnauthorized())
                .andExpect(
                        jsonPath("$.status").value(401)
                )
                .andExpect(
                        jsonPath("$.error").value(
                                "Unauthorized"
                        )
                )
                .andExpect(
                        jsonPath("$.message").value(
                                "Invalid refresh token"
                        )
                )
                .andExpect(
                        jsonPath("$.path").value(
                                "/api/auth/logout"
                        )
                );
    }

    @Test
    void verifyEmail_WithValidToken_ShouldVerifyUserAndUseToken()
            throws Exception {

        User unverifiedUser =
                createUnverifiedUser();

        String rawToken =
                requestAndCaptureVerificationToken(
                        unverifiedUser
                );

        mockMvc.perform(
                        get("/api/auth/verify-email")
                                .param("token", rawToken)
                )
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        User updatedUser =
                userRepository
                        .findById(
                                unverifiedUser.getId()
                        )
                        .orElseThrow();

        EmailVerificationToken storedToken =
                emailVerificationTokenRepository
                        .findByTokenHash(
                                hashToken(rawToken)
                        )
                        .orElseThrow();

        assertThat(updatedUser.getEmailVerified())
                .isTrue();

        assertThat(storedToken.getUsed())
                .isTrue();

        assertThat(storedToken.getTokenHash())
                .isNotEqualTo(rawToken);
    }

    @Test
    void verifyEmail_WithExpiredToken_ShouldReturnGone()
            throws Exception {

        User unverifiedUser =
                createUnverifiedUser();

        String rawToken =
                requestAndCaptureVerificationToken(
                        unverifiedUser
                );

        EmailVerificationToken storedToken =
                emailVerificationTokenRepository
                        .findByTokenHash(
                                hashToken(rawToken)
                        )
                        .orElseThrow();

        storedToken.setExpiresAt(
                LocalDateTime.now(ZoneOffset.UTC)
                        .minusSeconds(1)
        );

        emailVerificationTokenRepository
                .saveAndFlush(storedToken);

        mockMvc.perform(
                        get("/api/auth/verify-email")
                                .param("token", rawToken)
                )
                .andExpect(status().isGone())
                .andExpect(
                        jsonPath("$.status").value(410)
                )
                .andExpect(
                        jsonPath("$.message").value(
                                "Email verification token has expired"
                        )
                )
                .andExpect(
                        jsonPath("$.path").value(
                                "/api/auth/verify-email"
                        )
                );

        User unchangedUser =
                userRepository
                        .findById(
                                unverifiedUser.getId()
                        )
                        .orElseThrow();

        assertThat(unchangedUser.getEmailVerified())
                .isFalse();
    }

    @Test
    void verifyEmail_WithUsedToken_ShouldReturnBadRequest()
            throws Exception {

        User unverifiedUser =
                createUnverifiedUser();

        String rawToken =
                requestAndCaptureVerificationToken(
                        unverifiedUser
                );

        EmailVerificationToken storedToken =
                emailVerificationTokenRepository
                        .findByTokenHash(
                                hashToken(rawToken)
                        )
                        .orElseThrow();

        storedToken.setUsed(true);

        emailVerificationTokenRepository
                .saveAndFlush(storedToken);

        mockMvc.perform(
                        get("/api/auth/verify-email")
                                .param("token", rawToken)
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.status").value(400)
                )
                .andExpect(
                        jsonPath("$.message").value(
                                "Email verification token "
                                        + "has already been used"
                        )
                )
                .andExpect(
                        jsonPath("$.path").value(
                                "/api/auth/verify-email"
                        )
                );
    }

    @Test
    void verifyEmail_WithInvalidToken_ShouldReturnBadRequest()
            throws Exception {

        mockMvc.perform(
                        get("/api/auth/verify-email")
                                .param(
                                        "token",
                                        "invalid-verification-token"
                                )
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.status").value(400)
                )
                .andExpect(
                        jsonPath("$.message").value(
                                "Invalid email verification token"
                        )
                )
                .andExpect(
                        jsonPath("$.path").value(
                                "/api/auth/verify-email"
                        )
                );
    }

    @Test
    void resendVerification_WithUnknownEmail_ShouldNotRevealAccount()
            throws Exception {

        ResendVerificationRequest request =
                ResendVerificationRequest.builder()
                        .email(
                                "missing_"
                                        + uniqueValue()
                                        + "@example.com"
                        )
                        .build();

        mockMvc.perform(
                        post(
                                "/api/auth/resend-verification"
                        )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        objectMapper.writeValueAsString(
                                                request
                                        )
                                )
                )
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verifyNoInteractions(emailService);
    }

    @Test
    void resendVerification_WithVerifiedUser_ShouldNotRevealStatus()
            throws Exception {

        User verifiedUser =
                createVerifiedUser();

        ResendVerificationRequest request =
                ResendVerificationRequest.builder()
                        .email(verifiedUser.getEmail())
                        .build();

        mockMvc.perform(
                        post(
                                "/api/auth/resend-verification"
                        )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        objectMapper.writeValueAsString(
                                                request
                                        )
                                )
                )
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verifyNoInteractions(emailService);
    }

    private User createVerifiedUser() {
        return createUser(true, "refresh");
    }

    private User createUnverifiedUser() {
        return createUser(
                false,
                "verification"
        );
    }

    private User createUser(
            boolean emailVerified,
            String prefix
    ) {
        String uniqueValue = uniqueValue();

        Role userRole = getUserRole();

        User user =
                User.builder()
                        .firstName(
                                emailVerified
                                        ? "Refresh"
                                        : "Verification"
                        )
                        .lastName("User")
                        .username(
                                prefix + "_" + uniqueValue
                        )
                        .email(
                                prefix
                                        + "_"
                                        + uniqueValue
                                        + "@example.com"
                        )
                        .passwordHash(
                                passwordEncoder.encode(
                                        TEST_PASSWORD
                                )
                        )
                        .role(userRole)
                        .emailVerified(emailVerified)
                        .build();

        return userRepository.save(user);
    }

    private Role getUserRole() {
        return roleRepository
                .findByName("USER")
                .orElseThrow();
    }

    private LoginRequest createLoginRequest(
            User user
    ) {
        LoginRequest request =
                new LoginRequest();

        request.setUsernameOrEmail(
                user.getEmail()
        );

        request.setPassword(TEST_PASSWORD);

        return request;
    }

    private RefreshTokenRequest createRefreshTokenRequest(
            String rawToken
    ) {
        return RefreshTokenRequest.builder()
                .refreshToken(rawToken)
                .build();
    }

    private String loginAndGetRefreshToken(
            User user
    ) throws Exception {

        LoginRequest request =
                createLoginRequest(user);

        MvcResult result =
                mockMvc.perform(
                                post("/api/auth/login")
                                        .contentType(
                                                MediaType.APPLICATION_JSON
                                        )
                                        .content(
                                                objectMapper
                                                        .writeValueAsString(
                                                                request
                                                        )
                                        )
                        )
                        .andExpect(status().isOk())
                        .andReturn();

        return objectMapper
                .readTree(
                        result.getResponse()
                                .getContentAsString()
                )
                .get("refreshToken")
                .asText();
    }

    private String requestAndCaptureVerificationToken(
            User user
    ) throws Exception {

        ResendVerificationRequest request =
                ResendVerificationRequest.builder()
                        .email(user.getEmail())
                        .build();

        mockMvc.perform(
                        post(
                                "/api/auth/resend-verification"
                        )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        objectMapper.writeValueAsString(
                                                request
                                        )
                                )
                )
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        ArgumentCaptor<String> tokenCaptor =
                ArgumentCaptor.forClass(
                        String.class
                );

        verify(emailService)
                .sendEmailVerification(
                        eq(user.getEmail()),
                        eq("Verification User"),
                        tokenCaptor.capture()
                );

        String rawToken =
                tokenCaptor.getValue();

        assertThat(rawToken)
                .isNotBlank();

        assertThat(rawToken)
                .hasSize(43);

        EmailVerificationToken storedToken =
                emailVerificationTokenRepository
                        .findByTokenHash(
                                hashToken(rawToken)
                        )
                        .orElseThrow();

        assertThat(storedToken.getTokenHash())
                .isNotEqualTo(rawToken);

        return rawToken;
    }

    private String hashToken(
            String rawToken
    ) {
        try {
            MessageDigest digest =
                    MessageDigest.getInstance(
                            "SHA-256"
                    );

            byte[] hash =
                    digest.digest(
                            rawToken.getBytes(
                                    StandardCharsets.UTF_8
                            )
                    );

            return HexFormat.of()
                    .formatHex(hash);

        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }

    private String uniqueValue() {
        return UUID.randomUUID()
                .toString()
                .replace("-", "");
    }
}