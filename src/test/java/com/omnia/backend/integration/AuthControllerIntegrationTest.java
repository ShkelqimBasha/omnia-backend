package com.omnia.backend.integration;

import com.omnia.backend.dto.request.LoginRequest;
import com.omnia.backend.dto.request.RegisterRequest;
import com.omnia.backend.entity.Role;
import com.omnia.backend.entity.User;
import com.omnia.backend.repository.RoleRepository;
import com.omnia.backend.repository.UserRepository;
import com.omnia.backend.service.interfaces.EmailVerificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /*
     * E zëvendësojmë shërbimin real të email-it gjatë testeve,
     * që testi të mos tentojë të lidhet me Gmail ose SMTP.
     */
    @MockitoBean
    private EmailVerificationService emailVerificationService;

    @Test
    void register_WithValidRequest_ShouldCreateUser() throws Exception {

        String uniqueValue = UUID.randomUUID().toString()
                .replace("-", "");

        String username = "user_" + uniqueValue;
        String email = "user_" + uniqueValue + "@example.com";

        RegisterRequest request = new RegisterRequest();
        request.setFirstName("Test");
        request.setLastName("User");
        request.setUsername(username);
        request.setEmail(email);
        request.setPassword("Password123!");
        request.setPhone("+355691234567");

        mockMvc.perform(
                        post("/api/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.accessToken").isEmpty())
                .andExpect(jsonPath("$.refreshToken").isEmpty())
                .andExpect(jsonPath("$.message").value(
                        "Registration successful. " +
                                "Please verify your email before logging in."
                ));

        User savedUser = userRepository.findByEmail(email)
                .orElseThrow();

        assertThat(savedUser.getFirstName()).isEqualTo("Test");
        assertThat(savedUser.getLastName()).isEqualTo("User");
        assertThat(savedUser.getUsername()).isEqualTo(username);
        assertThat(savedUser.getEmail()).isEqualTo(email);
        assertThat(savedUser.getPhone()).isEqualTo("+355691234567");
        assertThat(savedUser.getEmailVerified()).isFalse();
        assertThat(savedUser.getRole().getName()).isEqualTo("USER");

        assertThat(
                passwordEncoder.matches(
                        "Password123!",
                        savedUser.getPasswordHash()
                )
        ).isTrue();

        verify(emailVerificationService)
                .createVerificationToken(any(User.class));
    }

    @Test
    void register_WithExistingEmail_ShouldReturnError() throws Exception {

        String uniqueValue = UUID.randomUUID().toString()
                .replace("-", "");

        String existingEmail =
                "existing_" + uniqueValue + "@example.com";

        Role userRole = roleRepository.findByName("USER")
                .orElseThrow();

        User existingUser = User.builder()
                .firstName("Existing")
                .lastName("User")
                .username("existing_" + uniqueValue)
                .email(existingEmail)
                .passwordHash(
                        passwordEncoder.encode("Password123!")
                )
                .role(userRole)
                .emailVerified(false)
                .build();

        userRepository.save(existingUser);

        RegisterRequest request = new RegisterRequest();
        request.setFirstName("Another");
        request.setLastName("User");
        request.setUsername("another_" + uniqueValue);
        request.setEmail(existingEmail);
        request.setPassword("Password123!");

        mockMvc.perform(
                        post("/api/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isConflict());
    }

    @Test
    void login_WithVerifiedUser_ShouldReturnTokens() throws Exception {

        String uniqueValue = UUID.randomUUID().toString()
                .replace("-", "");

        String username = "verified_" + uniqueValue;
        String email =
                "verified_" + uniqueValue + "@example.com";

        String rawPassword = "Password123!";

        Role userRole = roleRepository.findByName("USER")
                .orElseThrow();

        User verifiedUser = User.builder()
                .firstName("Verified")
                .lastName("User")
                .username(username)
                .email(email)
                .passwordHash(
                        passwordEncoder.encode(rawPassword)
                )
                .role(userRole)
                .emailVerified(true)
                .build();

        userRepository.save(verifiedUser);

        LoginRequest request = new LoginRequest();
        request.setUsernameOrEmail(email);
        request.setPassword(rawPassword);

        mockMvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isString())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.message").value(
                        "Login successful"
                ));
    }

    @Test
    void login_WithUnverifiedUser_ShouldReturnError() throws Exception {

        String uniqueValue = UUID.randomUUID().toString()
                .replace("-", "");

        String username = "unverified_" + uniqueValue;
        String email =
                "unverified_" + uniqueValue + "@example.com";

        String rawPassword = "Password123!";

        Role userRole = roleRepository.findByName("USER")
                .orElseThrow();

        User unverifiedUser = User.builder()
                .firstName("Unverified")
                .lastName("User")
                .username(username)
                .email(email)
                .passwordHash(
                        passwordEncoder.encode(rawPassword)
                )
                .role(userRole)
                .emailVerified(false)
                .build();

        userRepository.save(unverifiedUser);

        LoginRequest request = new LoginRequest();
        request.setUsernameOrEmail(email);
        request.setPassword(rawPassword);

        mockMvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isForbidden());
    }
}