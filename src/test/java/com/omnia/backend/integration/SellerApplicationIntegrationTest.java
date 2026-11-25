package com.omnia.backend.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.omnia.backend.entity.Category;
import com.omnia.backend.entity.User;
import com.omnia.backend.enums.CategoryKind;
import com.omnia.backend.enums.CategoryStatus;
import com.omnia.backend.event.SellerApplicationDecidedEvent;
import com.omnia.backend.repository.CategoryRepository;
import com.omnia.backend.repository.RoleRepository;
import com.omnia.backend.repository.UserRepository;
import com.omnia.backend.service.interfaces.EmailService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * "Bëhu shitës" end to end on real MySQL: apply, admin review, the store it creates,
 * and the private-seller limit. Not @Transactional: every request commits as in production.
 */
class SellerApplicationIntegrationTest extends AbstractIntegrationTest {

    @MockitoBean private EmailService emailService;

    @Autowired private CategoryRepository categoryRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private JdbcTemplate jdbc;

    private String admin;
    private String applicant;
    private String other;
    private Category fashion;
    private Category electronics;
    private Category subcategory;
    private Category inactive;
    private final List<Long> categories = new ArrayList<>();

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        admin = createUser("sa_admin_" + suffix, "ADMIN");
        applicant = createUser("sa_user_" + suffix, "USER");
        other = createUser("sa_other_" + suffix, "USER");
        fashion = category("Veshje " + suffix, null, CategoryStatus.ACTIVE);
        electronics = category("Elektronikë " + suffix, null, CategoryStatus.ACTIVE);
        subcategory = category("Këpucë " + suffix, fashion, CategoryStatus.ACTIVE);
        inactive = category("Vjetër " + suffix, null, CategoryStatus.INACTIVE);
    }

    @AfterEach
    void tearDown() {
        for (String email : List.of(applicant, other, admin)) {
            Long id = userRepository.findByEmail(email).orElseThrow().getId();
            List<Long> orgs = jdbc.queryForList(
                    "SELECT organization_id FROM seller_applications WHERE user_id = ? AND organization_id IS NOT NULL",
                    Long.class, id);
            jdbc.update("DELETE FROM seller_applications WHERE user_id = ?", id);
            for (Long org : orgs) {
                jdbc.update("DELETE FROM products WHERE organization_id = ?", org);
                jdbc.update("DELETE FROM organization_category_permissions WHERE organization_id = ?", org);
                jdbc.update("DELETE FROM organization_members WHERE organization_id = ?", org);
                jdbc.update("DELETE FROM organizations WHERE id = ?", org);
            }
            jdbc.update("DELETE FROM push_outbox WHERE notification_id IN (SELECT id FROM notification_inbox WHERE recipient_id = ?)", id);
            jdbc.update("DELETE FROM notification_inbox WHERE recipient_id = ?", id);
        }
        for (Long category : categories.reversed()) {
            jdbc.update("DELETE FROM categories WHERE id = ?", category);
        }
    }

    private String createUser(String username, String role) {
        String email = username + "@example.com";
        userRepository.saveAndFlush(User.builder().firstName("Ana").lastName("Shitëse")
                .username(username).email(email).passwordHash("hash")
                .role(roleRepository.findByName(role).orElseThrow()).emailVerified(true).build());
        return email;
    }

    private Category category(String name, Category parent, CategoryStatus status) {
        Category saved = categoryRepository.save(Category.builder().name(name).parent(parent)
                .status(status).kind(CategoryKind.GENERAL).build());
        categories.add(saved.getId());
        return saved;
    }

    private ResultActions as(String email, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.with(user(email).roles(email.equals(admin) ? "ADMIN" : "USER")));
    }

    private ResultActions json(String email, MockHttpServletRequestBuilder request, String body) throws Exception {
        return as(email, request.contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private JsonNode body(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private String application(String type, String storeName, String nipt, List<Long> categoryIds) throws Exception {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("sellerType", type);
        body.put("storeName", storeName);
        body.put("phone", "+355 69 123 4567");
        body.put("city", "Tiranë");
        body.put("nipt", nipt);
        body.put("categoryIds", categoryIds);
        body.put("description", "Rroba dhe aksesorë");
        return objectMapper.writeValueAsString(body);
    }

    private String productJson(long categoryId, long organizationId, String name) {
        return "{\"name\":\"" + name + "\",\"price\":20.00,\"stock\":1,\"categoryId\":" + categoryId
                + ",\"organizationId\":" + organizationId + "}";
    }

    private long userId(String email) {
        return userRepository.findByEmail(email).orElseThrow().getId();
    }

    @Test
    void businessApplicationIsApprovedIntoAStoreTheUserOwns() throws Exception {
        String store = "Butiku " + UUID.randomUUID().toString().substring(0, 6);
        List<Long> ids = List.of(fashion.getId(), electronics.getId());

        json(applicant, post("/api/seller-applications"), application("BUSINESS", store, "12345", ids))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("NIPT duhet të ketë formatin K12345678A."));
        json(applicant, post("/api/seller-applications"), application("BUSINESS", store, null, ids))
                .andExpect(status().isBadRequest());
        json(applicant, post("/api/seller-applications"),
                application("BUSINESS", store, "K12345678A", List.of(subcategory.getId())))
                .andExpect(status().isBadRequest());
        json(applicant, post("/api/seller-applications"),
                application("BUSINESS", store, "K12345678A", List.of(inactive.getId())))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/seller-applications").contentType(MediaType.APPLICATION_JSON)
                        .content(application("BUSINESS", store, "K12345678A", ids)))
                .andExpect(status().isUnauthorized());

        long id = body(json(applicant, post("/api/seller-applications"),
                application("BUSINESS", store, " k12345678a ", ids))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sellerType").value("BUSINESS"))
                .andExpect(jsonPath("$.nipt").value("K12345678A"))
                .andExpect(jsonPath("$.phone").value("+355691234567"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.categoryIds.length()").value(2))
                .andExpect(jsonPath("$.categoryNames[0]").value(fashion.getName()))
                .andExpect(jsonPath("$.applicant.email").value(applicant))
                .andExpect(jsonPath("$.applicant.name").value("Ana Shitëse"))
                .andExpect(jsonPath("$.organizationId").doesNotExist()))
                .get("id").asLong();

        json(applicant, post("/api/seller-applications"), application("BUSINESS", store, "K12345678A", ids))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Ke tashmë një aplikim në shqyrtim."));
        as(applicant, get("/api/seller-applications/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.status").value("PENDING"));
        as(other, get("/api/seller-applications/me")).andExpect(status().isNoContent());

        // Only platform admins review.
        as(applicant, get("/api/admin/seller-applications")).andExpect(status().isForbidden());
        as(applicant, post("/api/admin/seller-applications/{id}/approve", id)).andExpect(status().isForbidden());
        json(applicant, post("/api/admin/seller-applications/{id}/reject", id), "{\"reason\":\"Jo\"}")
                .andExpect(status().isForbidden());
        as(admin, get("/api/admin/seller-applications").param("status", "PENDING").param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", hasItem((int) id)))
                .andExpect(jsonPath("$.totalElements").isNumber());

        long organizationId = body(as(admin, post("/api/admin/seller-applications/{id}/approve", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.reviewedAt").isNotEmpty()))
                .get("organizationId").asLong();
        as(admin, post("/api/admin/seller-applications/{id}/approve", id)).andExpect(status().isConflict());

        Map<String, Object> org = jdbc.queryForMap(
                "SELECT name, status, seller_type, phone, primary_administrator_id, editor_name_key FROM organizations WHERE id = ?",
                organizationId);
        assertEquals(store, org.get("name"));
        assertEquals("ACTIVE", org.get("status"));
        assertEquals("BUSINESS", org.get("seller_type"));
        assertEquals("+355691234567", org.get("phone"));
        assertEquals(userId(applicant), ((Number) org.get("primary_administrator_id")).longValue());
        assertEquals(store.toLowerCase(java.util.Locale.ROOT), org.get("editor_name_key"));
        assertEquals("OWNER", jdbc.queryForObject(
                "SELECT membership_role FROM organization_members WHERE organization_id = ? AND user_id = ? AND status = 'ACTIVE'",
                String.class, organizationId, userId(applicant)));
        assertEquals(2, jdbc.queryForObject(
                "SELECT COUNT(*) FROM organization_category_permissions WHERE organization_id = ? AND status = 'ACTIVE' "
                        + "AND can_create AND can_update AND can_delete", Integer.class, organizationId));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM notification_inbox WHERE recipient_id = ? AND kind = 'SELLER_APPLICATION_APPROVED' "
                        + "AND label = 'Aplikimi u pranua — dyqani yt është gati.'", Integer.class, userId(applicant)));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM admin_action_log WHERE route = '/api/admin/seller-applications/{id}/approve' "
                        + "AND target_type = 'SELLER_APPLICATION' AND target_id = ?", Integer.class, String.valueOf(id)));
        ArgumentCaptor<SellerApplicationDecidedEvent> email = ArgumentCaptor.forClass(SellerApplicationDecidedEvent.class);
        verify(emailService, atLeastOnce()).sendSellerApplicationDecisionEmail(email.capture());
        assertTrue(email.getAllValues().stream().anyMatch(e -> e.approved() && applicant.equals(e.recipientEmail())));

        // The new owner sees the notice in the inbox and can list in an assigned category.
        as(applicant, get("/api/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].kind").value("SELLER_APPLICATION_APPROVED"))
                .andExpect(jsonPath("$.items[0].targetType").value("APPLICATION"));
        json(applicant, post("/api/products"), productJson(fashion.getId(), organizationId, "Xhaketë"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.organizationSellerType").value("BUSINESS"));
        json(applicant, post("/api/products"), productJson(subcategory.getId(), organizationId, "Atlete"))
                .andExpect(status().isForbidden());

        // A store owner cannot apply again; the store is public with its seller type.
        json(applicant, post("/api/seller-applications"), application("PRIVATE", store, null, ids))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Ke tashmë një dyqan në Omnia."));
        mockMvc.perform(get("/api/organizations/catalog"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + organizationId + ")].sellerType").value("BUSINESS"));
        as(applicant, get("/api/organizations/{id}", organizationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sellerType").value("BUSINESS"));
    }

    @Test
    void privateSellerGetsAPrivateStoreWithThirtyActiveListings() throws Exception {
        String store = "Gardëroba " + UUID.randomUUID().toString().substring(0, 6);
        long id = body(json(applicant, post("/api/seller-applications"),
                application("PRIVATE", store, "K12345678A", List.of(fashion.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sellerType").value("PRIVATE"))
                .andExpect(jsonPath("$.nipt").doesNotExist()))
                .get("id").asLong();
        long organizationId = body(as(admin, post("/api/admin/seller-applications/{id}/approve", id))
                .andExpect(status().isOk()))
                .get("organizationId").asLong();
        assertEquals("PRIVATE", jdbc.queryForObject(
                "SELECT seller_type FROM organizations WHERE id = ?", String.class, organizationId));

        for (int i = 1; i <= 30; i++) {
            ResultActions created = json(applicant, post("/api/products"),
                    productJson(fashion.getId(), organizationId, "Fustan " + i))
                    .andExpect(status().isCreated());
            if (i == 1) {
                created.andExpect(jsonPath("$.organizationSellerType").value("PRIVATE"));
            }
        }
        json(applicant, post("/api/products"), productJson(fashion.getId(), organizationId, "Fustan 31"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Shitësit privatë mund të kenë deri në 30 njoftime aktive."));
        assertEquals(30, jdbc.queryForObject(
                "SELECT COUNT(*) FROM products WHERE organization_id = ? AND status = 'ACTIVE'",
                Integer.class, organizationId));
        // Platform admins are not limited.
        json(admin, post("/api/products"), productJson(fashion.getId(), organizationId, "Fustan admin"))
                .andExpect(status().isCreated());
        // Deactivating one frees a place.
        long first = jdbc.queryForObject(
                "SELECT MIN(id) FROM products WHERE organization_id = ?", Long.class, organizationId);
        as(applicant, org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/products/{id}", first))
                .andExpect(status().is2xxSuccessful());
        jdbc.update("UPDATE products SET status = 'INACTIVE' WHERE organization_id = ? AND name = 'Fustan admin'",
                organizationId);
        json(applicant, post("/api/products"), productJson(fashion.getId(), organizationId, "Fustan 31"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/organizations/catalog"))
                .andExpect(jsonPath("$[?(@.id == " + organizationId + ")].sellerType").value("PRIVATE"));
    }

    @Test
    void rejectedApplicationHasAReasonAndTheUserMayApplyAgain() throws Exception {
        String store = "Dyqani " + UUID.randomUUID().toString().substring(0, 6);
        long id = body(json(applicant, post("/api/seller-applications"),
                application("BUSINESS", store, "L98765432B", List.of(electronics.getId())))
                .andExpect(status().isCreated()))
                .get("id").asLong();

        json(admin, post("/api/admin/seller-applications/{id}/reject", id), "{\"reason\":\"x\"}")
                .andExpect(status().isBadRequest());
        json(admin, post("/api/admin/seller-applications/{id}/reject", id), "{}")
                .andExpect(status().isBadRequest());
        json(admin, post("/api/admin/seller-applications/{id}/reject", id),
                "{\"reason\":\"NIPT nuk përputhet me emrin\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectReason").value("NIPT nuk përputhet me emrin"))
                .andExpect(jsonPath("$.organizationId").doesNotExist());
        as(admin, post("/api/admin/seller-applications/{id}/approve", id)).andExpect(status().isConflict());
        as(admin, post("/api/admin/seller-applications/{id}/approve", 999_999_999L)).andExpect(status().isNotFound());
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM notification_inbox WHERE recipient_id = ? AND kind = 'SELLER_APPLICATION_REJECTED' "
                        + "AND label = 'Aplikimi u refuzua: NIPT nuk përputhet me emrin'", Integer.class, userId(applicant)));
        assertFalse(jdbc.queryForObject(
                "SELECT COUNT(*) FROM organizations WHERE name = ?", Integer.class, store) > 0);
        as(applicant, get("/api/seller-applications/me"))
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectReason").value("NIPT nuk përputhet me emrin"));
        as(admin, get("/api/admin/seller-applications").param("status", "REJECTED").param("size", "50"))
                .andExpect(jsonPath("$.content[*].id", hasItem((int) id)));

        long again = body(json(applicant, post("/api/seller-applications"),
                application("BUSINESS", store, "K12345678A", List.of(electronics.getId())))
                .andExpect(status().isCreated()))
                .get("id").asLong();
        as(applicant, get("/api/seller-applications/me")).andExpect(jsonPath("$.id").value(again));
    }

    @Test
    void approvingATakenStoreNameNumbersIt() throws Exception {
        String store = "Emri " + UUID.randomUUID().toString().substring(0, 6);
        long first = body(json(applicant, post("/api/seller-applications"),
                application("PRIVATE", store, null, List.of(fashion.getId())))
                .andExpect(status().isCreated())).get("id").asLong();
        long second = body(json(other, post("/api/seller-applications"),
                application("PRIVATE", store, null, List.of(fashion.getId())))
                .andExpect(status().isCreated())).get("id").asLong();
        long a = body(as(admin, post("/api/admin/seller-applications/{id}/approve", first))).get("organizationId").asLong();
        long b = body(as(admin, post("/api/admin/seller-applications/{id}/approve", second))).get("organizationId").asLong();
        assertEquals(store, jdbc.queryForObject("SELECT name FROM organizations WHERE id = ?", String.class, a));
        assertEquals(store + " (2)", jdbc.queryForObject("SELECT name FROM organizations WHERE id = ?", String.class, b));
        String slugA = jdbc.queryForObject("SELECT slug FROM organizations WHERE id = ?", String.class, a);
        String slugB = jdbc.queryForObject("SELECT slug FROM organizations WHERE id = ?", String.class, b);
        assertFalse(slugA.equals(slugB));
    }
}
