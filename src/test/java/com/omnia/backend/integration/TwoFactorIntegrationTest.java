package com.omnia.backend.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.omnia.backend.entity.User;
import com.omnia.backend.repository.RoleRepository;
import com.omnia.backend.repository.UserRepository;
import com.omnia.backend.security.twofactor.Totp;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TwoFactorIntegrationTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "Password123!";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void setupEnableThenSignInNeedsTheSecondStep() throws Exception {
        User user = createUser();

        JsonNode first = login(user.getEmail());
        assertThat(first.path("twoFactorRequired").asBoolean(false)).isFalse();
        String token = first.path("accessToken").asText();

        mockMvc.perform(get("/api/auth/2fa/status").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));

        JsonNode setup = json(mockMvc.perform(post("/api/auth/2fa/setup")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        String secret = setup.path("secret").asText();
        assertThat(setup.path("otpauthUri").asText()).startsWith("otpauth://totp/Omnia");

        // A wrong code does not turn it on.
        mockMvc.perform(post("/api/auth/2fa/enable")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", "000000x"))))
                .andExpect(status().isBadRequest());

        String code = Totp.code(secret, Totp.stepAt(System.currentTimeMillis() / 1000));
        JsonNode enabled = json(mockMvc.perform(post("/api/auth/2fa/enable")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", code))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertThat(enabled.path("recoveryCodes")).hasSize(10);
        String recovery = enabled.path("recoveryCodes").get(0).asText();

        // Password alone no longer gives tokens.
        JsonNode challenge = login(user.getEmail());
        assertThat(challenge.path("twoFactorRequired").asBoolean()).isTrue();
        assertThat(challenge.path("accessToken").isMissingNode() || challenge.path("accessToken").isNull()).isTrue();
        String challengeToken = challenge.path("challengeToken").asText();

        mockMvc.perform(post("/api/auth/2fa/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("challengeToken", challengeToken, "code", "123456"))))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/2fa/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("challengeToken", challengeToken, "code", recovery.toUpperCase()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty());

        // The challenge and the recovery code each work once.
        mockMvc.perform(post("/api/auth/2fa/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("challengeToken", challengeToken, "code", recovery))))
                .andExpect(status().isUnauthorized());
        String second = login(user.getEmail()).path("challengeToken").asText();
        mockMvc.perform(post("/api/auth/2fa/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("challengeToken", second, "code", recovery))))
                .andExpect(status().isUnauthorized());

        // Turning it off needs a code; afterwards the password alone signs in again.
        mockMvc.perform(post("/api/auth/2fa/disable")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("code", enabled.path("recoveryCodes").get(1).asText()))))
                .andExpect(status().isNoContent());
        assertThat(login(user.getEmail()).path("accessToken").asText()).isNotEmpty();
    }

    @Test
    void statusNeedsASignedInUser() throws Exception {
        mockMvc.perform(get("/api/auth/2fa/status")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/2fa/setup")).andExpect(status().isUnauthorized());
    }

    private JsonNode login(String email) throws Exception {
        return json(mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("usernameOrEmail", email, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private JsonNode json(String body) throws Exception {
        return objectMapper.readTree(body);
    }

    private User createUser() {
        String unique = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return userRepository.save(User.builder()
                .firstName("Two")
                .lastName("Factor")
                .username("tf_" + unique)
                .email("tf_" + unique + "@example.com")
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .role(roleRepository.findByName("USER").orElseThrow())
                .emailVerified(true)
                .build());
    }
}
