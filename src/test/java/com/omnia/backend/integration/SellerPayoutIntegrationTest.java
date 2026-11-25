package com.omnia.backend.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.omnia.backend.entity.User;
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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Seller payouts end to end on real MySQL: which orders Omnia owes, recording a payout, the seller's
 * own view and access rules. Not @Transactional: every request commits as in production.
 */
class SellerPayoutIntegrationTest extends AbstractIntegrationTest {

    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private JdbcTemplate jdbc;

    private String admin;
    private String owner;
    private String otherOwner;
    private String buyer;
    private long storeA;
    private long storeB;
    private final List<Long> orders = new ArrayList<>();
    private final List<Long> groups = new ArrayList<>();

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        admin = createUser("po_admin_" + suffix, "ADMIN");
        owner = createUser("po_owner_" + suffix, "USER");
        otherOwner = createUser("po_other_" + suffix, "USER");
        buyer = createUser("po_buyer_" + suffix, "USER");
        storeA = organization("Dyqani A " + suffix, "po-a-" + suffix);
        storeB = organization("Dyqani B " + suffix, "po-b-" + suffix);
        member(storeA, owner, "OWNER");
        member(storeB, otherOwner, "OWNER");
    }

    @AfterEach
    void tearDown() {
        for (Long order : orders) {
            jdbc.update("DELETE FROM order_status_history WHERE order_id = ?", order);
            jdbc.update("DELETE FROM payments WHERE order_id = ?", order);
            jdbc.update("DELETE FROM orders WHERE id = ?", order);
        }
        for (Long group : groups) {
            jdbc.update("DELETE FROM checkout_groups WHERE id = ?", group);
        }
        for (long org : List.of(storeA, storeB)) {
            jdbc.update("UPDATE orders SET payout_id = NULL WHERE organization_id = ?", org);
            jdbc.update("DELETE FROM seller_payouts WHERE organization_id = ?", org);
            jdbc.update("DELETE FROM organization_members WHERE organization_id = ?", org);
        }
        for (String email : List.of(admin, owner, otherOwner, buyer)) {
            long id = userId(email);
            jdbc.update("DELETE FROM push_outbox WHERE notification_id IN (SELECT id FROM notification_inbox WHERE recipient_id = ?)", id);
            jdbc.update("DELETE FROM notification_inbox WHERE recipient_id = ?", id);
        }
        for (long org : List.of(storeA, storeB)) {
            jdbc.update("DELETE FROM organizations WHERE id = ?", org);
        }
    }

    // ------------------------------------------------------------ fixtures

    private String createUser(String username, String role) {
        String email = username + "@example.com";
        userRepository.saveAndFlush(User.builder().firstName("Ana").lastName("Shitëse")
                .username(username).email(email).passwordHash("hash")
                .role(roleRepository.findByName(role).orElseThrow()).emailVerified(true).build());
        return email;
    }

    private long userId(String email) {
        return userRepository.findByEmail(email).orElseThrow().getId();
    }

    private long organization(String name, String slug) {
        jdbc.update("INSERT INTO organizations(name, slug, status) VALUES(?, ?, 'ACTIVE')", name, slug);
        return jdbc.queryForObject("SELECT id FROM organizations WHERE slug = ?", Long.class, slug);
    }

    private void member(long org, String email, String role) {
        jdbc.update("INSERT INTO organization_members(organization_id, user_id, membership_role, status) VALUES(?, ?, ?, 'ACTIVE')",
                org, userId(email), role);
    }

    private long order(long org, String status, String method, String paymentStatus, String amount) {
        jdbc.update("INSERT INTO checkout_groups(checkout_reference, user_id, order_count, total_amount) VALUES(UUID(), ?, 1, ?)",
                userId(buyer), new BigDecimal(amount));
        long group = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        groups.add(group);
        jdbc.update("INSERT INTO orders(user_id, checkout_group_id, organization_id, organization_name, total_amount, status)"
                        + " VALUES(?, ?, ?, 'Dyqani', ?, ?)", userId(buyer), group, org, new BigDecimal(amount), status);
        long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        orders.add(id);
        jdbc.update("INSERT INTO payments(order_id, method, status, paid_at) VALUES(?, ?, ?, ?)", id, method, paymentStatus,
                "SUCCESS".equals(paymentStatus) ? new java.sql.Timestamp(System.currentTimeMillis()) : null);
        if ("DELIVERED".equals(status)) {
            jdbc.update("INSERT INTO order_status_history(order_id, from_status, to_status) VALUES(?, 'SHIPPED', 'DELIVERED')", id);
        }
        return id;
    }

    private ResultActions as(String email, MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.with(user(email).roles(email.equals(admin) ? "ADMIN" : "USER")));
    }

    private ResultActions create(String email, String body) throws Exception {
        return as(email, post("/api/admin/payouts").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private JsonNode body(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private JsonNode balanceRow(long org) throws Exception {
        JsonNode rows = body(as(admin, get("/api/admin/payouts/balances")).andExpect(status().isOk()));
        for (JsonNode row : rows) {
            if (row.get("organizationId").asLong() == org) return row;
        }
        return null;
    }

    /** Money is compared by value; the raw JSON is checked separately for two decimals. */
    private static void assertMoney(String expected, JsonNode node) {
        assertEquals(0, new BigDecimal(expected).compareTo(node.decimalValue()), expected + " != " + node);
    }

    private Long payoutId(long order) {
        return jdbc.queryForObject("SELECT payout_id FROM orders WHERE id = ?", Long.class, order);
    }

    // ------------------------------------------------------------ tests

    @Test
    void onlyDeliveredPayPalOrdersThatWereNotRefundedAreOwed() throws Exception {
        long paid = order(storeA, "DELIVERED", "PAYPAL", "SUCCESS", "40.00");
        long paid2 = order(storeA, "DELIVERED", "PAYPAL", "SUCCESS", "10.50");
        order(storeA, "DELIVERED", "CASH_ON_DELIVERY", "SUCCESS", "99.00");
        order(storeA, "SHIPPED", "PAYPAL", "SUCCESS", "77.00");
        order(storeA, "DELIVERED", "PAYPAL", "REFUNDED", "66.00");
        order(storeA, "DELIVERED", "PAYPAL", "PENDING", "55.00");     // partial refund under review
        order(storeA, "CANCELLED", "PAYPAL", "REFUNDED", "44.00");

        ResultActions eligibleResult = as(admin, get("/api/admin/payouts/organizations/" + storeA + "/eligible"))
                .andExpect(status().isOk());
        assertTrue(eligibleResult.andReturn().getResponse().getContentAsString().contains("\"totalAmount\":40.00"));
        JsonNode eligible = body(eligibleResult);
        assertEquals(2, eligible.size());
        assertEquals(paid, eligible.get(0).get("orderId").asLong());
        assertEquals(paid2, eligible.get(1).get("orderId").asLong());
        assertEquals("PAYPAL", eligible.get(0).get("paymentMethod").asText());
        assertMoney("40.00", eligible.get(0).get("totalAmount"));
        assertFalse(eligible.get(0).get("deliveredAt").isNull());
        assertFalse(eligible.get(0).get("checkoutReference").isNull());

        JsonNode row = balanceRow(storeA);
        assertEquals(2, row.get("orderCount").asInt());
        assertMoney("50.50", row.get("grossAmount"));
        assertMoney("0.00", row.get("commissionAmount"));
        assertMoney("50.50", row.get("netAmount"));
        assertEquals("BUSINESS", row.get("sellerType").asText());
        assertFalse(row.get("oldestOrderAt").isNull());
        assertNull(balanceRow(storeB));
    }

    @Test
    void adminRecordsAPayoutAndTheSameOrderCannotBePaidTwice() throws Exception {
        long first = order(storeA, "DELIVERED", "PAYPAL", "SUCCESS", "40.00");
        long second = order(storeA, "DELIVERED", "PAYPAL", "SUCCESS", "10.50");
        long cod = order(storeA, "DELIVERED", "CASH_ON_DELIVERY", "SUCCESS", "99.00");
        long other = order(storeB, "DELIVERED", "PAYPAL", "SUCCESS", "5.00");

        // Not eligible / another store's / already paid: nothing is recorded.
        create(admin, "{\"organizationId\":" + storeA + ",\"orderIds\":[" + first + "," + cod + "]}")
                .andExpect(status().isBadRequest());
        create(admin, "{\"organizationId\":" + storeA + ",\"orderIds\":[" + other + "]}")
                .andExpect(status().isBadRequest());
        assertNull(payoutId(first));

        JsonNode payout = body(create(admin, "{\"organizationId\":" + storeA + ",\"orderIds\":[" + first
                + "],\"reference\":\"BKT-2026-001\",\"note\":\"Transfertë bankare\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderCount").value(1))
                .andExpect(jsonPath("$.reference").value("BKT-2026-001")));
        long payout1 = payout.get("id").asLong();
        assertMoney("40.00", payout.get("netAmount"));
        assertEquals(first, payout.get("orderIds").get(0).asLong());
        assertEquals(payout1, payoutId(first));
        assertNull(payoutId(second));

        create(admin, "{\"organizationId\":" + storeA + ",\"orderIds\":[" + first + "]}")
                .andExpect(status().isConflict());

        // Default: every remaining eligible order.
        JsonNode rest = body(create(admin, "{\"organizationId\":" + storeA + "}").andExpect(status().isCreated()));
        assertEquals(1, rest.get("orderCount").asInt());
        assertMoney("10.50", rest.get("grossAmount"));
        assertEquals(rest.get("id").asLong(), payoutId(second));
        assertNull(payoutId(cod));

        assertNull(balanceRow(storeA));
        create(admin, "{\"organizationId\":" + storeA + "}").andExpect(status().isConflict());

        JsonNode history = body(as(admin, get("/api/admin/payouts?organizationId=" + storeA + "&page=0&size=10"))
                .andExpect(status().isOk()));
        assertEquals(2, history.get("totalElements").asInt());
        assertEquals(rest.get("id").asLong(), history.get("content").get(0).get("id").asLong());

        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM admin_action_log WHERE route = '/api/admin/payouts'"
                + " AND actor_id = ? AND response_status = 201", Long.class, userId(admin)) >= 2);
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM notification_inbox WHERE recipient_id = ?"
                + " AND kind = 'PAYOUT_SENT' AND organization_id = ?", Long.class, userId(owner), storeA));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM notification_inbox WHERE recipient_id = ?"
                + " AND kind = 'PAYOUT_SENT'", Long.class, userId(otherOwner)));
    }

    @Test
    void sellerSeesOwnBalanceAndHistoryButNotAnotherStore() throws Exception {
        long first = order(storeA, "DELIVERED", "PAYPAL", "SUCCESS", "40.00");
        order(storeA, "DELIVERED", "PAYPAL", "SUCCESS", "10.50");
        create(admin, "{\"organizationId\":" + storeA + ",\"orderIds\":[" + first + "]}")
                .andExpect(status().isCreated());

        as(owner, get("/api/organizations/" + storeA + "/payouts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance.orderCount").value(1))
                .andExpect(jsonPath("$.balance.netAmount").value(10.5))
                .andExpect(jsonPath("$.balance.grossAmount").value(10.5))
                .andExpect(jsonPath("$.commissionPercent").value(0))
                .andExpect(jsonPath("$.payouts.length()").value(1))
                .andExpect(jsonPath("$.payouts[0].netAmount").value(40.0));

        as(owner, get("/api/organizations/" + storeB + "/payouts")).andExpect(status().isForbidden());
        as(buyer, get("/api/organizations/" + storeA + "/payouts")).andExpect(status().isForbidden());
        as(admin, get("/api/organizations/" + storeA + "/payouts")).andExpect(status().isOk());
    }

    @Test
    void nonAdminsCannotUseAdminEndpoints() throws Exception {
        long first = order(storeA, "DELIVERED", "PAYPAL", "SUCCESS", "40.00");
        as(owner, get("/api/admin/payouts/balances")).andExpect(status().isForbidden());
        as(owner, get("/api/admin/payouts/organizations/" + storeA + "/eligible")).andExpect(status().isForbidden());
        as(owner, get("/api/admin/payouts")).andExpect(status().isForbidden());
        create(owner, "{\"organizationId\":" + storeA + "}").andExpect(status().isForbidden());
        assertNull(payoutId(first));
        mockMvc.perform(get("/api/admin/payouts/balances")).andExpect(status().isUnauthorized());
    }
}
