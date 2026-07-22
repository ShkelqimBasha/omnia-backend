package com.omnia.backend.integration;

import com.omnia.backend.dto.request.ForgotPasswordRequest;
import com.omnia.backend.dto.request.LoginRequest;
import com.omnia.backend.dto.request.ResetPasswordRequest;
import com.omnia.backend.entity.PasswordResetToken;
import com.omnia.backend.entity.RefreshToken;
import com.omnia.backend.entity.Role;
import com.omnia.backend.entity.User;
import com.omnia.backend.repository.PasswordResetTokenRepository;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PasswordResetControllerIntegrationTest
        extends AbstractIntegrationTest {

    private static final String CURRENT_PASSWORD =
            "Password123!";

    private static final String NEW_PASSWORD =
            "NewPassword123!";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PasswordResetTokenRepository
            passwordResetTokenRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    /*
     * Mock-ojmë vetëm dërgimin SMTP.
     * Controller, service, repository, Flyway dhe MySQL
     * përdoren realisht në integration tests.
     */
    @MockitoBean
    private EmailService emailService;

    @Test
    void forgotPassword_WithKnownEmail_ShouldStoreHashAndSendRawToken()
            throws Exception {

        User user = createVerifiedUser();

        String rawToken =
                requestAndCaptureResetToken(user);

        PasswordResetToken storedToken =
                passwordResetTokenRepository
                        .findByTokenHash(
                                hashToken(rawToken)
                        )
                        .orElseThrow();

        assertThat(rawToken)
                .isNotBlank()
                .hasSize(43);

        assertThat(storedToken.getTokenHash())
                .hasSize(64)
                .isEqualTo(hashToken(rawToken))
                .isNotEqualTo(rawToken);

        assertThat(storedToken.getUser().getId())
                .isEqualTo(user.getId());

        assertThat(storedToken.getUsed())
                .isFalse();

        assertThat(storedToken.getExpiresAt())
                .isAfter(storedToken.getCreatedAt());
    }

    @Test
    void forgotPassword_WithUnknownEmail_ShouldNotRevealAccount()
            throws Exception {

        ForgotPasswordRequest request =
                ForgotPasswordRequest.builder()
                        .email(
                                "missing_"
                                        + uniqueValue()
                                        + "@example.com"
                        )
                        .build();

        mockMvc.perform(
                        post("/api/auth/forgot-password")
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
    void resetPassword_WithValidToken_ShouldChangePasswordAndRevokeSessions()
            throws Exception {

        User user = createVerifiedUser();

        String refreshRawToken =
                loginAndGetRefreshToken(user);

        String resetRawToken =
                requestAndCaptureResetToken(user);

        ResetPasswordRequest request =
                createResetRequest(
                        resetRawToken,
                        NEW_PASSWORD,
                        NEW_PASSWORD
                );

        mockMvc.perform(
                        post("/api/auth/reset-password")
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

        User updatedUser =
                userRepository.findById(user.getId())
                        .orElseThrow();

        PasswordResetToken usedToken =
                passwordResetTokenRepository
                        .findByTokenHash(
                                hashToken(resetRawToken)
                        )
                        .orElseThrow();

        RefreshToken revokedRefreshToken =
                refreshTokenRepository
                        .findByTokenHash(
                                hashToken(refreshRawToken)
                        )
                        .orElseThrow();

        assertThat(
                passwordEncoder.matches(
                        NEW_PASSWORD,
                        updatedUser.getPasswordHash()
                )
        ).isTrue();

        assertThat(
                passwordEncoder.matches(
                        CURRENT_PASSWORD,
                        updatedUser.getPasswordHash()
                )
        ).isFalse();

        assertThat(usedToken.getUsed())
                .isTrue();

        assertThat(revokedRefreshToken.getRevoked())
                .isTrue();
    }

    @Test
    void resetPassword_WithExpiredToken_ShouldReturnGone()
            throws Exception {

        User user = createVerifiedUser();

        String rawToken =
                requestAndCaptureResetToken(user);

        PasswordResetToken storedToken =
                passwordResetTokenRepository
                        .findByTokenHash(
                                hashToken(rawToken)
                        )
                        .orElseThrow();

        storedToken.setExpiresAt(
                LocalDateTime.now(ZoneOffset.UTC)
                        .minusSeconds(1)
        );

        passwordResetTokenRepository
                .saveAndFlush(storedToken);

        ResetPasswordRequest request =
                createResetRequest(
                        rawToken,
                        NEW_PASSWORD,
                        NEW_PASSWORD
                );

        mockMvc.perform(
                        post("/api/auth/reset-password")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        objectMapper.writeValueAsString(
                                                request
                                        )
                                )
                )
                .andExpect(status().isGone())
                .andExpect(
                        jsonPath("$.status").value(410)
                )
                .andExpect(
                        jsonPath("$.message").value(
                                "Password reset token has expired"
                        )
                )
                .andExpect(
                        jsonPath("$.path").value(
                                "/api/auth/reset-password"
                        )
                );

        User unchangedUser =
                userRepository.findById(user.getId())
                        .orElseThrow();

        assertThat(
                passwordEncoder.matches(
                        CURRENT_PASSWORD,
                        unchangedUser.getPasswordHash()
                )
        ).isTrue();
    }

    @Test
    void resetPassword_WithUsedToken_ShouldReturnBadRequest()
            throws Exception {

        User user = createVerifiedUser();

        String rawToken =
                requestAndCaptureResetToken(user);

        PasswordResetToken storedToken =
                passwordResetTokenRepository
                        .findByTokenHash(
                                hashToken(rawToken)
                        )
                        .orElseThrow();

        storedToken.setUsed(true);

        passwordResetTokenRepository
                .saveAndFlush(storedToken);

        ResetPasswordRequest request =
                createResetRequest(
                        rawToken,
                        NEW_PASSWORD,
                        NEW_PASSWORD
                );

        mockMvc.perform(
                        post("/api/auth/reset-password")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        objectMapper.writeValueAsString(
                                                request
                                        )
                                )
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.status").value(400)
                )
                .andExpect(
                        jsonPath("$.message").value(
                                "Password reset token has already been used"
                        )
                );
    }

    @Test
    void resetPassword_WithInvalidToken_ShouldReturnBadRequest()
            throws Exception {

        ResetPasswordRequest request =
                createResetRequest(
                        "invalid-reset-token",
                        NEW_PASSWORD,
                        NEW_PASSWORD
                );

        mockMvc.perform(
                        post("/api/auth/reset-password")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        objectMapper.writeValueAsString(
                                                request
                                        )
                                )
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.status").value(400)
                )
                .andExpect(
                        jsonPath("$.message").value(
                                "Invalid password reset token"
                        )
                )
                .andExpect(
                        jsonPath("$.path").value(
                                "/api/auth/reset-password"
                        )
                );
    }

    @Test
    void resetPassword_WithWeakPassword_ShouldReturnBadRequest()
            throws Exception {

        User user = createVerifiedUser();

        String rawToken =
                requestAndCaptureResetToken(user);

        ResetPasswordRequest request =
                createResetRequest(
                        rawToken,
                        "weakpass",
                        "weakpass"
                );

        mockMvc.perform(
                        post("/api/auth/reset-password")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        objectMapper.writeValueAsString(
                                                request
                                        )
                                )
                )
                .andExpect(status().isBadRequest());

        PasswordResetToken unchangedToken =
                passwordResetTokenRepository
                        .findByTokenHash(
                                hashToken(rawToken)
                        )
                        .orElseThrow();

        assertThat(unchangedToken.getUsed())
                .isFalse();
    }

    @Test
    void resetPassword_WithDifferentConfirmation_ShouldReturnBadRequest()
            throws Exception {

        User user = createVerifiedUser();

        String rawToken =
                requestAndCaptureResetToken(user);

        ResetPasswordRequest request =
                createResetRequest(
                        rawToken,
                        NEW_PASSWORD,
                        "DifferentPassword123!"
                );

        mockMvc.perform(
                        post("/api/auth/reset-password")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        objectMapper.writeValueAsString(
                                                request
                                        )
                                )
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.message").value(
                                "Passwords do not match"
                        )
                );
    }

    @Test
    void resetPassword_WithCurrentPassword_ShouldReturnBadRequest()
            throws Exception {

        User user = createVerifiedUser();

        String rawToken =
                requestAndCaptureResetToken(user);

        ResetPasswordRequest request =
                createResetRequest(
                        rawToken,
                        CURRENT_PASSWORD,
                        CURRENT_PASSWORD
                );

        mockMvc.perform(
                        post("/api/auth/reset-password")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        objectMapper.writeValueAsString(
                                                request
                                        )
                                )
                )
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.message").value(
                                "New password must be different "
                                        + "from the current password"
                        )
                );

        PasswordResetToken unchangedToken =
                passwordResetTokenRepository
                        .findByTokenHash(
                                hashToken(rawToken)
                        )
                        .orElseThrow();

        assertThat(unchangedToken.getUsed())
                .isFalse();
    }

    private User createVerifiedUser() {
        String uniqueValue = uniqueValue();

        Role userRole =
                roleRepository.findByName("USER")
                        .orElseThrow();

        User user =
                User.builder()
                        .firstName("Password")
                        .lastName("Reset")
                        .username(
                                "password_reset_"
                                        + uniqueValue
                        )
                        .email(
                                "password_reset_"
                                        + uniqueValue
                                        + "@example.com"
                        )
                        .passwordHash(
                                passwordEncoder.encode(
                                        CURRENT_PASSWORD
                                )
                        )
                        .role(userRole)
                        .emailVerified(true)
                        .build();

        return userRepository.save(user);
    }

    private String requestAndCaptureResetToken(
            User user
    ) throws Exception {

        ForgotPasswordRequest request =
                ForgotPasswordRequest.builder()
                        .email(user.getEmail())
                        .build();

        mockMvc.perform(
                        post("/api/auth/forgot-password")
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
                ArgumentCaptor.forClass(String.class);

        verify(emailService)
                .sendPasswordResetEmail(
                        eq(user.getEmail()),
                        eq("Password Reset"),
                        tokenCaptor.capture()
                );

        String rawToken =
                tokenCaptor.getValue();

        assertThat(rawToken)
                .isNotBlank()
                .hasSize(43);

        PasswordResetToken storedToken =
                passwordResetTokenRepository
                        .findByTokenHash(
                                hashToken(rawToken)
                        )
                        .orElseThrow();

        assertThat(storedToken.getTokenHash())
                .isNotEqualTo(rawToken);

        return rawToken;
    }

    private String loginAndGetRefreshToken(
            User user
    ) throws Exception {

        LoginRequest request =
                new LoginRequest();

        request.setUsernameOrEmail(
                user.getEmail()
        );

        request.setPassword(
                CURRENT_PASSWORD
        );

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

    private ResetPasswordRequest createResetRequest(
            String token,
            String newPassword,
            String confirmPassword
    ) {
        return ResetPasswordRequest.builder()
                .token(token)
                .newPassword(newPassword)
                .confirmPassword(confirmPassword)
                .build();
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