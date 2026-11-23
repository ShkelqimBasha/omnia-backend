package com.omnia.backend.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.omnia.backend.entity.Category;
import com.omnia.backend.entity.Organization;
import com.omnia.backend.entity.OrganizationMember;
import com.omnia.backend.entity.User;
import com.omnia.backend.enums.CategoryKind;
import com.omnia.backend.enums.CategoryStatus;
import com.omnia.backend.enums.OrganizationMemberRole;
import com.omnia.backend.enums.OrganizationMemberStatus;
import com.omnia.backend.enums.OrganizationStatus;
import com.omnia.backend.repository.CategoryRepository;
import com.omnia.backend.repository.OrganizationMemberRepository;
import com.omnia.backend.repository.OrganizationRepository;
import com.omnia.backend.repository.RoleRepository;
import com.omnia.backend.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Besueshmëria end to end on real MySQL: seller verification with a private document,
 * product reports and moderation, and the admin action log. Not @Transactional: every
 * request commits as in production (the log is written after the response).
 */
class TrustAndSafetyIntegrationTest extends AbstractIntegrationTest {

    private static final String SUPER = "trust-super@example.com";
    private static final String ADMIN = "trust-admin@example.com";
    private static final String SELLER = "trust-seller@example.com";
    private static final String BUYER = "trust-buyer@example.com";
    private static final String OUTSIDER = "trust-outsider@example.com";

    private static final byte[] PDF = "%PDF-1.4\n1 0 obj<<>>endobj\ntrailer<<>>\n%%EOF".getBytes(StandardCharsets.US_ASCII);

    @Autowired private CategoryRepository categoryRepository;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private OrganizationMemberRepository memberRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private JdbcTemplate jdbc;

    private Organization company;
    private Category category;
    private final List<Long> products = new ArrayList<>();
    private final List<Long> categories = new ArrayList<>();

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User superAdmin = ensureUser(SUPER, "trust_super", "SUPER_ADMIN");
        ensureUser(ADMIN, "trust_admin", "ADMIN");
        User seller = ensureUser(SELLER, "trust_seller", "USER");
        ensureUser(BUYER, "trust_buyer", "USER");
        ensureUser(OUTSIDER, "trust_outsider", "USER");
        jdbc.update("DELETE FROM product_reports WHERE reporter_id IN (SELECT id FROM users WHERE email LIKE 'trust-%')");
        jdbc.update("DELETE FROM admin_action_log WHERE actor_id = ?", superAdmin.getId());
        company = organizationRepository.save(Organization.builder().name("Dyqani " + suffix)
                .slug("dyqani-" + suffix).status(OrganizationStatus.ACTIVE).build());
        memberRepository.save(OrganizationMember.builder().organization(company).user(seller)
                .membershipRole(OrganizationMemberRole.OWNER).status(OrganizationMemberStatus.ACTIVE).build());
        category = categoryRepository.save(Category.builder().name("Elektronikë " + suffix)
                .status(CategoryStatus.ACTIVE).kind(CategoryKind.GENERAL).build());
        categories.add(category.getId());
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM notification_inbox WHERE organization_id = ?", company.getId());
        jdbc.update("DELETE FROM product_reports WHERE organization_id = ?", company.getId());
        List<Long> files = jdbc.queryForList(
                "SELECT document_file_id FROM organization_verifications WHERE organization_id = ? "
                        + "AND document_file_id IS NOT NULL", Long.class, company.getId());
        jdbc.update("DELETE FROM organization_verifications WHERE organization_id = ?", company.getId());
        for (Long file : files) {
            jdbc.update("DELETE FROM uploaded_files WHERE id = ?", file);
        }
        for (Long product : products) {
            jdbc.update("DELETE FROM products WHERE id = ?", product);
        }
        jdbc.update("DELETE FROM organization_members WHERE organization_id = ?", company.getId());
        organizationRepository.deleteById(company.getId());
        for (Long id : categories) {
            jdbc.update("DELETE FROM categories WHERE id = ?", id);
        }
    }

    private User ensureUser(String email, String username, String role) {
        return userRepository.findByEmail(email).orElseGet(() -> userRepository.saveAndFlush(User.builder()
                .firstName("Test").lastName(username).username(username).email(email).passwordHash("hash")
                .role(roleRepository.findByName(role).orElseThrow()).emailVerified(true).build()));
    }

    private ResultActions as(String email, MockHttpServletRequestBuilder request) throws Exception {
        String role = SUPER.equals(email) ? "SUPER_ADMIN" : ADMIN.equals(email) ? "ADMIN" : "USER";
        return mockMvc.perform(request.with(user(email).roles(role)));
    }

    private ResultActions json(String email, MockHttpServletRequestBuilder request, String body) throws Exception {
        return as(email, request.contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private JsonNode body(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private ResultActions submit(String email, String nipt, MockMultipartFile document) throws Exception {
        return as(email, multipart("/api/organizations/{id}/verification", company.getId())
                .file(document).param("nipt", nipt));
    }

    private static MockMultipartFile pdf() {
        return new MockMultipartFile("document", "regjistrimi.pdf", "application/pdf", PDF);
    }

    private long createProduct() throws Exception {
        JsonNode created = body(json(SUPER, post("/api/products"),
                "{\"name\":\"Telefon\",\"price\":120.00,\"stock\":3,\"categoryId\":" + category.getId()
                        + ",\"organizationId\":" + company.getId() + "}").andExpect(status().isCreated()));
        long id = created.get("id").asLong();
        products.add(id);
        return id;
    }

    private long userId(String email) {
        return userRepository.findByEmail(email).orElseThrow().getId();
    }

    @Test
    void sellerAsksForVerificationAdminRefusesThenApprovesAndTheBadgeShows() throws Exception {
        long product = createProduct();

        // Only the company's owner/admin can ask; the NIPT must look like K12345678A.
        submit(OUTSIDER, "K12345678A", pdf()).andExpect(status().isForbidden());
        submit(SELLER, "12345", pdf()).andExpect(status().isBadRequest());
        submit(SELLER, "K12345678A",
                new MockMultipartFile("document", "skript.html", "text/html", "<script>".getBytes()))
                .andExpect(status().isBadRequest());

        JsonNode first = body(submit(SELLER, "k1234 5678a", pdf()).andExpect(status().isCreated()));
        long firstId = first.get("id").asLong();
        assertEquals("K12345678A", first.get("nipt").asText());
        assertEquals("PENDING", first.get("status").asText());
        assertTrue(first.get("documentUrl").isNull(), "the company never gets a download link");
        submit(SELLER, "K12345678A", pdf()).andExpect(status().isConflict());

        // The document is private: not through /api/files for anyone but platform admins.
        long fileId = jdbc.queryForObject(
                "SELECT document_file_id FROM organization_verifications WHERE id = ?", Long.class, firstId);
        as(SELLER, get("/api/files/{id}", fileId)).andExpect(status().isNotFound());
        as(BUYER, get("/api/files/{id}/metadata", fileId)).andExpect(status().isNotFound());
        as(SELLER, delete("/api/files/{id}", fileId)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/files/{id}", fileId)).andExpect(status().isUnauthorized());
        as(SELLER, get("/api/admin/verifications/{id}/document", firstId)).andExpect(status().isForbidden());
        as(SUPER, get("/api/admin/verifications/{id}/document", firstId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));

        // Admin queue, then a refusal with a reason the company can read.
        as(SUPER, get("/api/admin/verifications").param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == " + firstId + ")].documentUrl")
                        .value("/api/admin/verifications/" + firstId + "/document"));
        json(SUPER, post("/api/admin/verifications/{id}/reject", firstId), "{\"reason\":\"\"}")
                .andExpect(status().isBadRequest());
        json(SUPER, post("/api/admin/verifications/{id}/reject", firstId),
                "{\"reason\":\"Dokumenti nuk lexohet\"}").andExpect(status().isOk());
        as(SELLER, get("/api/organizations/{id}/verification", company.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(false))
                .andExpect(jsonPath("$.canSubmit").value(true))
                .andExpect(jsonPath("$.latest.status").value("REJECTED"))
                .andExpect(jsonPath("$.latest.rejectionReason").value("Dokumenti nuk lexohet"));
        json(SUPER, post("/api/admin/verifications/{id}/approve", firstId), "{}")
                .andExpect(status().isConflict());

        // Second try is approved: the badge shows on the company and on its products.
        long secondId = body(submit(SELLER, "K12345678A",
                new MockMultipartFile("document", "certifikata.png", "image/png",
                        new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0}))
                .andExpect(status().isCreated())).get("id").asLong();
        as(ADMIN, post("/api/admin/verifications/{id}/approve", secondId)).andExpect(status().isOk());
        mockMvc.perform(get("/api/products/{id}", product))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizationVerified").value(true));
        mockMvc.perform(get("/api/organizations/catalog"))
                .andExpect(jsonPath("$[?(@.id == " + company.getId() + ")].verified").value(true));
        submit(SELLER, "K12345678A", pdf()).andExpect(status().isConflict());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM notification_inbox n JOIN users u "
                + "ON u.id = n.recipient_id WHERE u.email = ? AND n.kind = 'VERIFICATION_APPROVED'",
                Integer.class, SELLER));

        // Revoking removes the badge.
        json(SUPER, post("/api/admin/organizations/{id}/verification/revoke", company.getId()),
                "{\"reason\":\"Kompania u mbyll\"}").andExpect(status().isOk());
        mockMvc.perform(get("/api/products/{id}", product))
                .andExpect(jsonPath("$.organizationVerified").value(false));

        // The admin log has every decision; the seller's own request is not an admin action.
        JsonNode log = body(as(SUPER, get("/api/admin/action-log").param("targetType", "VERIFICATION"))
                .andExpect(status().isOk()));
        List<String> actions = new ArrayList<>();
        log.get("content").forEach(entry -> actions.add(entry.get("action").asText()));
        assertTrue(actions.contains("Refuzoi verifikimin e shitësit"), actions.toString());
        assertTrue(actions.contains("Hoqi verifikimin e shitësit"), actions.toString());
        assertTrue(log.toString().contains("Dokumenti nuk lexohet"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM admin_action_log WHERE actor_id = ?",
                Integer.class, userId(SELLER)));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM admin_action_log WHERE actor_id = ? "
                        + "AND route = '/api/admin/verifications/{id}/approve' AND target_id = ?",
                Integer.class, userId(ADMIN), String.valueOf(secondId)));
        jdbc.update("DELETE FROM admin_action_log WHERE actor_id = ?", userId(ADMIN));
    }

    @Test
    void buyersReportAListingAndTheAdminHidesWarnsOrDismisses() throws Exception {
        long phone = createProduct();
        long case2 = createProduct();

        mockMvc.perform(get("/api/products/report-reasons"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].value").value("FRAUD"))
                .andExpect(jsonPath("$[2].label").value("Çmim i rremë"));
        mockMvc.perform(post("/api/products/{id}/reports", phone).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"FRAUD\"}")).andExpect(status().isUnauthorized());
        json(BUYER, post("/api/products/{id}/reports", phone), "{\"reason\":\"OTHER\"}")
                .andExpect(status().isBadRequest());
        json(BUYER, post("/api/products/{id}/reports", phone), "{\"reason\":\"NOPE\"}")
                .andExpect(status().isBadRequest());

        json(BUYER, post("/api/products/{id}/reports", phone),
                "{\"reason\":\"FRAUD\",\"details\":\"Kërkon pagesë jashtë platformës\"}")
                .andExpect(status().isCreated());
        json(BUYER, post("/api/products/{id}/reports", phone), "{\"reason\":\"FAKE_PRICE\"}")
                .andExpect(status().isConflict());
        json(OUTSIDER, post("/api/products/{id}/reports", phone), "{\"reason\":\"FAKE_PRICE\"}")
                .andExpect(status().isCreated());

        // Queue: both reports, each knowing the product has 2 open reports.
        JsonNode queue = body(as(SUPER, get("/api/admin/product-reports").param("status", "OPEN"))
                .andExpect(status().isOk()));
        List<JsonNode> mine = new ArrayList<>();
        queue.get("content").forEach(r -> { if (r.get("productId").asLong() == phone) mine.add(r); });
        assertEquals(2, mine.size());
        assertEquals(2, mine.get(0).get("openReportsOnProduct").asInt());
        as(SELLER, get("/api/admin/product-reports")).andExpect(status().isForbidden());

        // Warn: the company gets a notice, the listing stays, both reports are closed.
        long reportId = mine.get(0).get("id").asLong();
        json(SUPER, post("/api/admin/product-reports/{id}/warn", reportId),
                "{\"note\":\"Hiqni kërkesën për pagesë jashtë\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WARNED"));
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM product_reports WHERE product_id = ? AND status = 'OPEN'", Integer.class, phone));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM notification_inbox WHERE recipient_id = ? "
                + "AND kind = 'PRODUCT_WARNING' AND target_id = ?", Integer.class, userId(SELLER), phone));
        json(SUPER, post("/api/admin/product-reports/{id}/dismiss", reportId), "{}")
                .andExpect(status().isConflict());

        // Hide: the listing leaves the catalog; restore brings it back.
        long hideId = body(json(BUYER, post("/api/products/{id}/reports", case2),
                "{\"reason\":\"PROHIBITED\"}").andExpect(status().isCreated())).get("id").asLong();
        as(SUPER, post("/api/admin/product-reports/{id}/hide", hideId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("HIDDEN"))
                .andExpect(jsonPath("$.productStatus").value("INACTIVE"));
        json(OUTSIDER, post("/api/products/{id}/reports", case2), "{\"reason\":\"FRAUD\"}")
                .andExpect(status().isNotFound());
        as(SUPER, post("/api/admin/product-reports/{id}/restore", hideId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productStatus").value("ACTIVE"));

        // Dismiss closes without touching the listing.
        long dismissId = body(json(OUTSIDER, post("/api/products/{id}/reports", case2),
                "{\"reason\":\"OTHER\",\"details\":\"S'më pëlqen\"}").andExpect(status().isCreated()))
                .get("id").asLong();
        as(SUPER, post("/api/admin/product-reports/{id}/dismiss", dismissId))
                .andExpect(jsonPath("$.status").value("DISMISSED"))
                .andExpect(jsonPath("$.productStatus").value("ACTIVE"));

        JsonNode log = body(as(SUPER, get("/api/admin/action-log").param("targetType", "MODERATION")));
        assertTrue(log.toString().contains("Paralajmëroi kompaninë"));
        assertTrue(log.toString().contains("Fshehu produktin e raportuar"));
        assertTrue(log.toString().contains("Hiqni kërkesën për pagesë jashtë"));
    }

    @Test
    void reportsAreLimitedPerUserPerDay() throws Exception {
        long product = createProduct();
        long buyer = userId(BUYER);
        for (int i = 0; i < 10; i++) {
            jdbc.update("INSERT INTO product_reports (product_id, organization_id, reporter_id, reason, status, "
                    + "created_at) VALUES (?, ?, ?, 'FRAUD', 'DISMISSED', UTC_TIMESTAMP(6))",
                    product, company.getId(), buyer);
        }
        json(BUYER, post("/api/products/{id}/reports", product), "{\"reason\":\"FRAUD\"}")
                .andExpect(status().isTooManyRequests());
        jdbc.update("UPDATE product_reports SET created_at = UTC_TIMESTAMP(6) - INTERVAL 2 DAY WHERE reporter_id = ?",
                buyer);
        json(BUYER, post("/api/products/{id}/reports", product), "{\"reason\":\"FRAUD\"}")
                .andExpect(status().isCreated());
    }

    @Test
    void actionLogKeepsWhoChangedWhatButNeverTheBody() throws Exception {
        JsonNode created = body(json(SUPER, post("/api/categories"),
                "{\"name\":\"Kategori Log " + UUID.randomUUID().toString().substring(0, 6)
                        + "\",\"description\":\"fjalekalimi sekret-123-xyz\"}")
                .andExpect(status().isCreated()));
        categories.add(created.get("id").asLong());
        as(SUPER, org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .patch("/api/organizations/{id}/status", company.getId()).param("status", "INACTIVE"))
                .andExpect(status().isOk());

        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM admin_action_log WHERE detail LIKE '%sekret-123-xyz%'", Integer.class));
        JsonNode log = body(as(SUPER, get("/api/admin/action-log")
                .param("actorId", String.valueOf(userId(SUPER)))).andExpect(status().isOk()));
        String text = log.toString();
        assertTrue(text.contains("Krijoi kategorinë"), text);
        assertTrue(text.contains("Ndryshoi statusin e kompanisë"), text);
        assertTrue(text.contains("status=INACTIVE"), text);
        assertFalse(text.contains("sekret-123-xyz"));

        as(SUPER, get("/api/admin/action-log").param("targetType", "COMPANY")
                .param("q", String.valueOf(company.getId())))
                .andExpect(jsonPath("$.content[0].targetId").value(String.valueOf(company.getId())))
                .andExpect(jsonPath("$.content[0].targetTypeLabel").value("kompani"));
        as(SUPER, get("/api/admin/action-log").param("from", "2999-01-01"))
                .andExpect(jsonPath("$.content.length()").value(0));
        as(SUPER, get("/api/admin/action-log/filters"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetTypes[0].value").value("COMPANY"));
        as(ADMIN, get("/api/admin/action-log")).andExpect(status().isForbidden());
        as(BUYER, get("/api/admin/action-log")).andExpect(status().isForbidden());
    }

    @Test
    void generalUploadsStillOnlyAcceptImages() throws Exception {
        as(SELLER, multipart("/api/files/upload")
                .file(new MockMultipartFile("file", "dok.pdf", "application/pdf", PDF)))
                .andExpect(status().isBadRequest());
    }
}
