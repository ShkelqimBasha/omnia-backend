package com.omnia.backend.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.omnia.backend.entity.Category;
import com.omnia.backend.entity.Organization;
import com.omnia.backend.entity.User;
import com.omnia.backend.enums.CategoryKind;
import com.omnia.backend.enums.CategoryStatus;
import com.omnia.backend.enums.OrganizationStatus;
import com.omnia.backend.repository.CategoryRepository;
import com.omnia.backend.repository.OrganizationRepository;
import com.omnia.backend.repository.RoleRepository;
import com.omnia.backend.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Payment methods per company on real MySQL: the company panel sets them, the cart learns
 * which methods it may use, and cash-on-delivery checkout follows them. Not @Transactional.
 */
class PaymentSettingsIntegrationTest extends AbstractIntegrationTest {

    private static final String ADMIN = "paysettings-admin@example.com";
    private static final String OWNER = "paysettings-owner@example.com";
    private static final String STAFF = "paysettings-staff@example.com";
    private static final String BUYER = "paysettings-buyer@example.com";

    @Autowired private CategoryRepository categoryRepository;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private JdbcTemplate jdbc;

    private Category general;
    private Organization shop;
    private Organization bakery;
    private final List<Long> products = new ArrayList<>();

    private User account(String email, String role) {
        return userRepository.findByEmail(email).orElseGet(() -> userRepository.saveAndFlush(User.builder()
                .firstName("Test").lastName("Pagesa").username(email.substring(0, email.indexOf('@')).replace('-', '_'))
                .email(email).passwordHash("hash").role(roleRepository.findByName(role).orElseThrow())
                .emailVerified(true).build()));
    }

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        account(ADMIN, "ADMIN");
        User owner = account(OWNER, "USER");
        User staff = account(STAFF, "USER");
        account(BUYER, "USER");
        shop = organizationRepository.save(Organization.builder().name("Dyqani " + suffix)
                .slug("dyqani-" + suffix).status(OrganizationStatus.ACTIVE).build());
        bakery = organizationRepository.save(Organization.builder().name("Furra " + suffix)
                .slug("furra-" + suffix).status(OrganizationStatus.ACTIVE).build());
        general = categoryRepository.save(Category.builder().name("Shtëpi " + suffix)
                .status(CategoryStatus.ACTIVE).kind(CategoryKind.GENERAL).build());
        jdbc.update("INSERT INTO organization_members (organization_id, user_id, membership_role, status) "
                + "VALUES (?, ?, 'OWNER', 'ACTIVE')", shop.getId(), owner.getId());
        jdbc.update("INSERT INTO organization_members (organization_id, user_id, membership_role, status) "
                + "VALUES (?, ?, 'STAFF', 'ACTIVE')", shop.getId(), staff.getId());
    }

    @AfterEach
    void tearDown() {
        Long buyerId = userRepository.findByEmail(BUYER).orElseThrow().getId();
        jdbc.update("DELETE FROM order_status_history WHERE order_id IN (SELECT id FROM orders WHERE user_id = ?)", buyerId);
        jdbc.update("DELETE FROM orders WHERE user_id = ?", buyerId);
        jdbc.update("DELETE FROM checkout_groups WHERE user_id = ?", buyerId);
        for (Long product : products) jdbc.update("DELETE FROM products WHERE id = ?", product);
        jdbc.update("DELETE FROM organization_members WHERE organization_id IN (?, ?)", shop.getId(), bakery.getId());
        categoryRepository.delete(general);
        jdbc.update("DELETE FROM organizations WHERE id IN (?, ?)", shop.getId(), bakery.getId());
    }

    private ResultActions json(String email, MockHttpServletRequestBuilder request, String body) throws Exception {
        return mockMvc.perform(request.with(user(email).roles(ADMIN.equals(email) ? "ADMIN" : "USER"))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions as(String email, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.with(user(email).roles(ADMIN.equals(email) ? "ADMIN" : "USER")));
    }

    private long product(Organization organization, String price) throws Exception {
        String body = "{\"name\":\"Artikull\",\"price\":" + price + ",\"stock\":20,\"categoryId\":" + general.getId()
                + ",\"organizationId\":" + organization.getId() + "}";
        JsonNode created = objectMapper.readTree(json(ADMIN, post("/api/products"), body)
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        long id = created.get("id").asLong();
        products.add(id);
        return id;
    }

    private static String settings(boolean cod, boolean paypal, String min, String max) {
        return "{\"acceptsCashOnDelivery\":" + cod + ",\"acceptsPaypal\":" + paypal
                + ",\"codMinOrderAmount\":" + min + ",\"codMaxOrderAmount\":" + max + "}";
    }

    private static String items(long... productIdAndQuantity) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < productIdAndQuantity.length; i += 2) {
            if (out.length() > 0) out.append(',');
            out.append("{\"productId\":").append(productIdAndQuantity[i])
                    .append(",\"quantity\":").append(productIdAndQuantity[i + 1]).append('}');
        }
        return "[" + out + "]";
    }

    private static String checkout(String items) {
        return "{\"shippingName\":\"Ana\",\"shippingEmail\":\"ana@example.com\",\"shippingPhone\":\"+355690000000\","
                + "\"shippingAddress\":\"Rruga e Kavajës, Tiranë\",\"items\":" + items + "}";
    }

    @Test
    void defaultsKeepBothMethodsAndOnlyOwnersOrAdminsChangeThem() throws Exception {
        as(OWNER, get("/api/organizations/{id}/payment-settings", shop.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acceptsCashOnDelivery").value(true))
                .andExpect(jsonPath("$.acceptsPaypal").value(true))
                .andExpect(jsonPath("$.codMinOrderAmount").isEmpty())
                .andExpect(jsonPath("$.codMaxOrderAmount").isEmpty());
        as(STAFF, get("/api/organizations/{id}/payment-settings", shop.getId())).andExpect(status().isOk());
        as(BUYER, get("/api/organizations/{id}/payment-settings", shop.getId())).andExpect(status().isForbidden());
        as(OWNER, get("/api/organizations/{id}/payment-settings", bakery.getId())).andExpect(status().isForbidden());

        json(STAFF, put("/api/organizations/{id}/payment-settings", shop.getId()), settings(false, true, "null", "null"))
                .andExpect(status().isForbidden());
        json(OWNER, put("/api/organizations/{id}/payment-settings", bakery.getId()), settings(false, true, "null", "null"))
                .andExpect(status().isForbidden());

        json(OWNER, put("/api/organizations/{id}/payment-settings", shop.getId()), settings(false, false, "null", "null"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Zgjidh të paktën një mënyrë pagese."));
        json(OWNER, put("/api/organizations/{id}/payment-settings", shop.getId()), settings(true, true, "50", "10"))
                .andExpect(status().isBadRequest());
        json(OWNER, put("/api/organizations/{id}/payment-settings", shop.getId()), settings(true, true, "-1", "null"))
                .andExpect(status().isBadRequest());
        json(OWNER, put("/api/organizations/{id}/payment-settings", shop.getId()), settings(true, true, "null", "0"))
                .andExpect(status().isBadRequest());

        json(OWNER, put("/api/organizations/{id}/payment-settings", shop.getId()), settings(true, false, "5", "200.5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acceptsPaypal").value(false))
                .andExpect(jsonPath("$.codMinOrderAmount").value(5.00))
                .andExpect(jsonPath("$.codMaxOrderAmount").value(200.50));
        json(ADMIN, put("/api/organizations/{id}/payment-settings", bakery.getId()), settings(false, true, "null", "null"))
                .andExpect(status().isOk());

        // The database refuses a company without any method, whatever writes it.
        assertThrows(org.springframework.dao.DataAccessException.class, () -> jdbc.update(
                "UPDATE organizations SET accepts_cash_on_delivery = FALSE, accepts_paypal = FALSE WHERE id = ?",
                shop.getId()));
        assertEquals(Boolean.TRUE, jdbc.queryForObject(
                "SELECT accepts_cash_on_delivery FROM organizations WHERE id = ?", Boolean.class, shop.getId()));
    }

    @Test
    void cartOnlyOffersMethodsEveryCompanyAcceptsAndCheckoutFollows() throws Exception {
        long mug = product(shop, "15.00");
        long bread = product(bakery, "2.00");

        as(BUYER, post("/api/orders/checkout/payment-options").contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":" + items(mug, 1, bread, 2) + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cashOnDelivery.available").value(true))
                .andExpect(jsonPath("$.paypal.available").value(true))
                .andExpect(jsonPath("$.mixedRules").value(false))
                .andExpect(jsonPath("$.message").isEmpty())
                .andExpect(jsonPath("$.companies.length()").value(2));

        json(ADMIN, put("/api/organizations/{id}/payment-settings", bakery.getId()), settings(false, true, "null", "null"))
                .andExpect(status().isOk());
        json(ADMIN, put("/api/organizations/{id}/payment-settings", shop.getId()), settings(true, true, "20", "null"))
                .andExpect(status().isOk());

        json(BUYER, post("/api/orders/checkout/payment-options"), "{\"items\":" + items(mug, 1, bread, 2) + "}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cashOnDelivery.available").value(false))
                .andExpect(jsonPath("$.cashOnDelivery.reason").value(
                        "Për pagesë në dorëzim, produktet nga «" + shop.getName() + "» duhet të jenë të paktën 20,00 €."))
                .andExpect(jsonPath("$.paypal.available").value(true))
                .andExpect(jsonPath("$.mixedRules").value(true))
                .andExpect(jsonPath("$.message").value(
                        "Shporta ka produkte nga disa biznese me rregulla të ndryshme pagese: "
                                + "ofrohen vetëm mënyrat që i pranojnë të gjithë."))
                .andExpect(jsonPath("$.companies[?(@.organizationId == " + bakery.getId()
                        + ")].cashOnDeliveryAvailable").value(false))
                .andExpect(jsonPath("$.companies[?(@.organizationId == " + shop.getId()
                        + ")].subtotal").value(15.00));

        json(BUYER, post("/api/orders/checkout"), checkout(items(mug, 1, bread, 2)))
                .andExpect(status().isBadRequest());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM orders o JOIN users u ON u.id = o.user_id "
                + "WHERE u.email = ?", Integer.class, BUYER), "a refused checkout creates nothing");

        // The shop alone, at its minimum, takes cash on delivery.
        json(BUYER, post("/api/orders/checkout/payment-options"), "{\"items\":" + items(mug, 2) + "}")
                .andExpect(jsonPath("$.cashOnDelivery.available").value(true));
        json(BUYER, post("/api/orders/checkout"), checkout(items(mug, 2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orders[0].paymentMethod").value("CASH_ON_DELIVERY"));

        json(BUYER, post("/api/orders/checkout/payment-options"), "{\"items\":[]}")
                .andExpect(status().isBadRequest());
    }
}
