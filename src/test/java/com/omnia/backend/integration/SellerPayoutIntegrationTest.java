package com.omnia.backend.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.omnia.backend.entity.User;
import com.omnia.backend.repository.RoleRepository;
import com.omnia.backend.repository.UserRepository;
import com.omnia.backend.service.impl.SellerPayoutAdjustments;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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
    @Autowired private SellerPayoutAdjustments adjustments;
    @Autowired private PlatformTransactionManager transactionManager;

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
        for (long org : List.of(storeA, storeB)) {
            jdbc.update("DELETE s FROM seller_payout_adjustment_settlements s JOIN seller_payout_adjustments a"
                    + " ON a.id = s.adjustment_id WHERE a.organization_id = ?", org);
            jdbc.update("DELETE FROM seller_payout_adjustments WHERE organization_id = ?", org);
        }
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

    // ------------------------------------------------------------ refunds after a payout (V54)

    /** A PayPal refund path reporting {@code amount} refunded for the order's checkout (in its own transaction). */
    private int refund(long order, String amount) {
        long group = jdbc.queryForObject("SELECT checkout_group_id FROM orders WHERE id = ?", Long.class, order);
        return new TransactionTemplate(transactionManager).execute(s ->
                adjustments.recordRefund(group, new BigDecimal(amount), SellerPayoutAdjustments.SOURCE_REFUND));
    }

    private JsonNode paidOut(long order) throws Exception {
        return body(create(admin, "{\"organizationId\":" + storeA + ",\"orderIds\":[" + order + "]}")
                .andExpect(status().isCreated()));
    }

    private JsonNode adjustmentsOf(long org) throws Exception {
        return body(as(admin, get("/api/admin/payouts/adjustments?organizationId=" + org)).andExpect(status().isOk()))
                .get("content");
    }

    private ResultActions settle(String email, long adjustment, String body) throws Exception {
        return as(email, post("/api/admin/payouts/adjustments/" + adjustment + "/settle")
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void refundAfterPayoutIsRecordedOnceAndTheBalanceGoesNegative() throws Exception {
        long sold = order(storeA, "DELIVERED", "PAYPAL", "SUCCESS", "40.00");
        long payout = paidOut(sold).get("id").asLong();

        assertEquals(1, refund(sold, "40.00"));
        assertEquals(0, refund(sold, "40.00"));            // the same event again

        JsonNode list = adjustmentsOf(storeA);
        assertEquals(1, list.size());
        JsonNode adjustment = list.get(0);
        assertEquals(sold, adjustment.get("orderId").asLong());
        assertEquals(payout, adjustment.get("payoutId").asLong());
        assertEquals("OPEN", adjustment.get("status").asText());
        assertEquals("Sistemi", adjustment.get("createdByName").asText());
        assertMoney("40.00", adjustment.get("amount"));
        assertMoney("40.00", adjustment.get("remainingAmount"));

        // Nothing owed, 40.00 to recover: the seller owes Omnia.
        JsonNode row = balanceRow(storeA);
        assertEquals(0, row.get("orderCount").asInt());
        assertEquals(1, row.get("adjustmentCount").asInt());
        assertMoney("40.00", row.get("adjustmentAmount"));
        assertMoney("-40.00", row.get("payableAmount"));
        assertTrue(row.get("organizationName").asText().startsWith("Dyqani A"));

        as(owner, get("/api/organizations/" + storeA + "/payouts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance.payableAmount").value(-40.0))
                .andExpect(jsonPath("$.balance.adjustmentAmount").value(40.0))
                .andExpect(jsonPath("$.adjustments.length()").value(1))
                .andExpect(jsonPath("$.adjustments[0].status").value("OPEN"));
        as(otherOwner, get("/api/organizations/" + storeB + "/payouts"))
                .andExpect(jsonPath("$.adjustments.length()").value(0));

        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM notification_inbox WHERE recipient_id = ?"
                + " AND kind = 'PAYOUT_ADJUSTMENT' AND organization_id = ?", Long.class, userId(owner), storeA));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM notification_inbox WHERE recipient_id = ?"
                + " AND kind = 'PAYOUT_ADJUSTMENT'", Long.class, userId(otherOwner)));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM admin_action_log WHERE http_method = 'SYSTEM'"
                + " AND target_id = ?", Long.class, adjustment.get("id").asText()));

        // An order that was never paid out is simply no longer owed: no adjustment.
        long unpaid = order(storeA, "DELIVERED", "PAYPAL", "SUCCESS", "9.00");
        assertEquals(0, refund(unpaid, "9.00"));
    }

    @Test
    void nextPayoutDeductsOpenAdjustmentsAndSettlesThem() throws Exception {
        long sold = order(storeA, "DELIVERED", "PAYPAL", "SUCCESS", "40.00");
        paidOut(sold);
        refund(sold, "10.00");                               // partial
        refund(sold, "40.00");                               // then the rest: only 30.00 more
        assertEquals(2, adjustmentsOf(storeA).size());
        long next = order(storeA, "DELIVERED", "PAYPAL", "SUCCESS", "100.00");
        assertMoney("60.00", balanceRow(storeA).get("payableAmount"));

        JsonNode payout = body(create(admin, "{\"organizationId\":" + storeA + "}").andExpect(status().isCreated()));
        assertMoney("100.00", payout.get("netAmount"));
        assertMoney("40.00", payout.get("deductionAmount"));
        assertMoney("60.00", payout.get("transferAmount"));
        assertEquals(next, payout.get("orderIds").get(0).asLong());

        for (JsonNode adjustment : adjustmentsOf(storeA)) {
            assertEquals("SETTLED", adjustment.get("status").asText());
            assertEquals(payout.get("id").asLong(), adjustment.get("settledInPayoutId").asLong());
            assertEquals("PAYOUT", adjustment.get("settlements").get(0).get("kind").asText());
            assertMoney("0.00", adjustment.get("remainingAmount"));
        }
        assertNull(balanceRow(storeA));
        assertEquals(0, new BigDecimal("60.00").compareTo(jdbc.queryForObject("SELECT amount FROM notification_inbox"
                + " WHERE recipient_id = ? AND event_key = ?", BigDecimal.class, userId(owner),
                "payout:" + payout.get("id").asLong())));
        as(admin, get("/api/admin/payouts/adjustments?organizationId=" + storeA + "&status=OPEN"))
                .andExpect(jsonPath("$.totalElements").value(0));
        as(admin, get("/api/admin/payouts/adjustments?organizationId=" + storeA + "&status=SETTLED"))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void payoutSmallerThanTheDebtTransfersZeroAndTheRestIsRecoveredManually() throws Exception {
        long sold = order(storeA, "DELIVERED", "PAYPAL", "SUCCESS", "40.00");
        paidOut(sold);
        refund(sold, "40.00");
        order(storeA, "DELIVERED", "PAYPAL", "SUCCESS", "15.00");
        assertMoney("-25.00", balanceRow(storeA).get("payableAmount"));

        JsonNode payout = body(create(admin, "{\"organizationId\":" + storeA + "}").andExpect(status().isCreated()));
        assertMoney("15.00", payout.get("deductionAmount"));
        assertMoney("0.00", payout.get("transferAmount"));

        JsonNode adjustment = adjustmentsOf(storeA).get(0);
        assertEquals("OPEN", adjustment.get("status").asText());
        assertMoney("15.00", adjustment.get("settledAmount"));
        assertMoney("25.00", adjustment.get("remainingAmount"));
        JsonNode row = balanceRow(storeA);
        assertEquals(0, row.get("orderCount").asInt());
        assertMoney("-25.00", row.get("payableAmount"));

        long id = adjustment.get("id").asLong();
        settle(owner, id, "{\"note\":\"U pagua me bankë\"}").andExpect(status().isForbidden());
        settle(admin, id, "{\"note\":\" \"}").andExpect(status().isBadRequest());
        settle(admin, id, "{\"kind\":\"FORGOTTEN\",\"note\":\"U pagua me bankë\"}").andExpect(status().isBadRequest());
        settle(admin, id, "{\"note\":\"Shitësi e ktheu me bankë, ref. R-77\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SETTLED"))
                .andExpect(jsonPath("$.remainingAmount").value(0.0))
                .andExpect(jsonPath("$.settlements.length()").value(2))
                .andExpect(jsonPath("$.settlements[1].kind").value("RECOVERED"))
                .andExpect(jsonPath("$.settlements[1].amount").value(25.0))
                .andExpect(jsonPath("$.settlements[1].note").value("Shitësi e ktheu me bankë, ref. R-77"));
        settle(admin, id, "{\"note\":\"përsëri\"}").andExpect(status().isConflict());
        settle(admin, 999_999_999L, "{\"note\":\"nuk ekziston\"}").andExpect(status().isNotFound());
        assertNull(balanceRow(storeA));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM admin_action_log WHERE route ="
                + " '/api/admin/payouts/adjustments/{adjustmentId}/settle' AND target_id = ?", Long.class, String.valueOf(id)));
        // Full history is kept.
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM seller_payout_adjustment_settlements"
                + " WHERE adjustment_id = ?", Long.class, id));
    }

    @Test
    void adminCanWaiveAnAdjustment() throws Exception {
        long sold = order(storeA, "DELIVERED", "PAYPAL", "SUCCESS", "20.00");
        paidOut(sold);
        refund(sold, "20.00");
        long id = adjustmentsOf(storeA).get(0).get("id").asLong();
        settle(admin, id, "{\"kind\":\"waived\",\"note\":\"Rimbursimi i përkiste shitësit tjetër\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settlements[0].kind").value("WAIVED"));
        assertNull(balanceRow(storeA));
    }

    @Test
    void theSameTransferReferenceCannotBeRecordedTwiceForASeller() throws Exception {
        long first = order(storeA, "DELIVERED", "PAYPAL", "SUCCESS", "40.00");
        long second = order(storeA, "DELIVERED", "PAYPAL", "SUCCESS", "10.00");
        long other = order(storeB, "DELIVERED", "PAYPAL", "SUCCESS", "5.00");
        create(admin, "{\"organizationId\":" + storeA + ",\"orderIds\":[" + first + "],\"reference\":\"BKT-2026-001\"}")
                .andExpect(status().isCreated());
        create(admin, "{\"organizationId\":" + storeA + ",\"orderIds\":[" + second + "],\"reference\":\"  bkt-2026-001 \"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("referencë")));
        assertNull(payoutId(second));
        // Another seller may have the same bank reference; an empty reference is never a duplicate.
        create(admin, "{\"organizationId\":" + storeB + ",\"orderIds\":[" + other + "],\"reference\":\"BKT-2026-001\"}")
                .andExpect(status().isCreated());
        create(admin, "{\"organizationId\":" + storeA + ",\"orderIds\":[" + second + "],\"reference\":\"  \"}")
                .andExpect(status().isCreated());
    }

    @Test
    void onlyAdminsSeeAndSettleAdjustments() throws Exception {
        long sold = order(storeA, "DELIVERED", "PAYPAL", "SUCCESS", "20.00");
        paidOut(sold);
        refund(sold, "20.00");
        long id = adjustmentsOf(storeA).get(0).get("id").asLong();
        as(owner, get("/api/admin/payouts/adjustments")).andExpect(status().isForbidden());
        settle(owner, id, "{\"note\":\"e paguar\"}").andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/payouts/adjustments")).andExpect(status().isUnauthorized());
        as(admin, get("/api/admin/payouts/adjustments?status=BAD")).andExpect(status().isBadRequest());
        assertEquals("OPEN", adjustmentsOf(storeA).get(0).get("status").asText());
    }
}
