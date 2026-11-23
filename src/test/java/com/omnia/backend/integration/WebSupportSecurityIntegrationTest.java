package com.omnia.backend.integration;

import com.omnia.backend.entity.Role;
import com.omnia.backend.entity.User;
import com.omnia.backend.repository.RoleRepository;
import com.omnia.backend.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security rules used by the web client: public reviews, admin-only manual
 * payments and self-service account deletion.
 */
class WebSupportSecurityIntegrationTest extends AbstractIntegrationTest {

    private static final String DELETE_EMAIL = "delete.me@example.com";

    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update(
                "delete from users where email = ?",
                DELETE_EMAIL
        );
        jdbcTemplate.update(
                "delete from users where email like 'deleted-%@deleted.omnia.invalid'"
        );
    }

    @Test
    @WithAnonymousUser
    void guestCanReadProductReviews() throws Exception {
        // 404 for an unknown product (not 401) proves the endpoint is public.
        mockMvc.perform(get("/api/reviews/product/{productId}", 999_999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Product not found"));
    }

    @Test
    @WithAnonymousUser
    void guestStillCannotCreateReviews() throws Exception {
        mockMvc.perform(post("/api/reviews/{productId}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":5,\"comment\":\"ok\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "buyer@example.com", roles = "USER")
    void regularUserCannotCreateManualPayment() throws Exception {
        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("orderId", 1, "method", "CARD"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = DELETE_EMAIL, roles = "USER")
    void userCanDeleteOwnAccountWithPassword() throws Exception {
        User user = createUser();

        mockMvc.perform(delete("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"wrong-password\"}"))
                .andExpect(status().isBadRequest());

        assertThat(userRepository.findByEmail(DELETE_EMAIL)).isPresent();

        mockMvc.perform(delete("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Secret123!\"}"))
                .andExpect(status().isNoContent());

        assertThat(userRepository.findByEmail(DELETE_EMAIL)).isEmpty();

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "select email, username, status, deleted_at, phone from users where id = ?",
                user.getId()
        );
        assertThat(row.get("email"))
                .isEqualTo("deleted-" + user.getId() + "@deleted.omnia.invalid");
        assertThat(row.get("username")).isEqualTo("deleted_" + user.getId());
        assertThat(row.get("status")).isEqualTo("INACTIVE");
        assertThat(row.get("deleted_at")).isNotNull();
        assertThat(row.get("phone")).isNull();
    }

    @Test
    @WithAnonymousUser
    void guestCannotDeleteAccount() throws Exception {
        mockMvc.perform(delete("/api/users/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"x\"}"))
                .andExpect(status().isUnauthorized());
    }

    private User createUser() {
        Role role = roleRepository.findByName("USER")
                .orElseThrow(() -> new IllegalStateException("USER role is missing"));

        return userRepository.saveAndFlush(User.builder()
                .firstName("Delete")
                .lastName("Me")
                .username("delete_me")
                .email(DELETE_EMAIL)
                .phone("+38344000000")
                .passwordHash(passwordEncoder.encode("Secret123!"))
                .role(role)
                .emailVerified(true)
                .build());
    }
}
