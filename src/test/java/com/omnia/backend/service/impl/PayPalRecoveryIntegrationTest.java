package com.omnia.backend.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.omnia.backend.entity.*;
import com.omnia.backend.enums.*;
import com.omnia.backend.integration.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Recovery against real MySQL. Not @Transactional: every request commits, so row locks,
 * concurrent resolutions and webhooks behave as in production.
 */
class PayPalRecoveryIntegrationTest extends AbstractIntegrationTest {
    private static final String BASE = "/api/admin/paypal/recovery";
    @MockitoBean PayPalGateway gateway;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @Autowired PlatformTransactionManager manager;
    @Autowired PayPalCheckoutService checkoutService;
    @Autowired PayPalRecoveryTransitions transitions;
    private final ObjectMapper json = new ObjectMapper();

    private String superAdmin, secondSuperAdmin, admin, buyer, checkoutId, provider;
    private long buyerId, productA, productB, firstOrder, secondOrder, groupId;
    private static final BigDecimal TOTAL = new BigDecimal("35.50");

    @BeforeEach void setup() {
        when(gateway.environment()).thenReturn("sandbox");
        when(gateway.merchant()).thenReturn("MERCHANT1");
        when(gateway.verifyWebhook(anyMap(), any())).thenReturn(true);
        new TransactionTemplate(manager).executeWithoutResult(s -> {
            superAdmin = newUser("SUPER_ADMIN");
            secondSuperAdmin = newUser("SUPER_ADMIN");
            admin = newUser("ADMIN");
            buyer = newUser("USER");
            buyerId = jdbc.queryForObject("SELECT id FROM users WHERE username=?", Long.class, buyer);
            String tag = UUID.randomUUID().toString().substring(0, 8);
            jdbc.update("INSERT INTO categories(name) VALUES(?)", "rec-" + tag);
            long category = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
            long orgA = organization("seller-a-" + tag), orgB = organization("seller-b-" + tag);
            productA = product(category, orgA, "A-" + tag, 5);
            productB = product(category, orgB, "B-" + tag, 7);

            User owner = em.find(User.class, buyerId);
            CheckoutGroup group = CheckoutGroup.builder().user(owner).totalAmount(TOTAL).orderCount(2).build();
            em.persist(group);
            Order first = Order.builder().user(owner).checkoutGroup(group).status(OrderStatus.PENDING).totalAmount(new BigDecimal("20.00")).build();
            Order second = Order.builder().user(owner).checkoutGroup(group).status(OrderStatus.PENDING).totalAmount(new BigDecimal("15.50")).build();
            em.persist(first);
            em.persist(second);
            em.persist(Payment.builder().order(first).method(PaymentMethod.PAYPAL).status(PaymentStatus.PENDING).build());
            em.persist(Payment.builder().order(second).method(PaymentMethod.PAYPAL).status(PaymentStatus.PENDING).build());
            em.flush();
            groupId = group.getId();
            firstOrder = first.getId();
            secondOrder = second.getId();
            jdbc.update("UPDATE orders SET organization_id=? WHERE id=?", orgA, firstOrder);
            jdbc.update("UPDATE orders SET organization_id=? WHERE id=?", orgB, secondOrder);
            item(firstOrder, productA, 2, "10.00");
            item(secondOrder, productB, 1, "10.50");
            item(secondOrder, productA, 1, "5.00");
            checkoutId = UUID.randomUUID().toString();
            provider = "PROV" + tag.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "0");
            jdbc.update("INSERT INTO paypal_checkouts(id,user_id,request_key,request_hash,checkout_group_id,amount,paypal_order_id,approval_url,state) VALUES(?,?,?,?,?,?,?,?,'RECOVERY_REQUIRED')",
                    checkoutId, buyerId, UUID.randomUUID().toString(), "0".repeat(64), groupId, TOTAL, provider,
                    "https://www.sandbox.paypal.com/checkoutnow?token=" + provider);
        });
        evidence("APPROVED", null, "CAPTURE1");
    }

    private String newUser(String role) {
        String name = "rec" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        Role r = em.createQuery("select r from Role r where r.name=:n", Role.class).setParameter("n", role).getSingleResult();
        em.persist(User.builder().username(name).email(name + "@example.test").passwordHash("unused").firstName("T")
                .role(r).status(UserStatus.ACTIVE).build());
        em.flush();
        return name;
    }

    private long organization(String slug) {
        jdbc.update("INSERT INTO organizations(name,slug,status) VALUES(?,?,'ACTIVE')", slug, slug);
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private long product(long category, long org, String name, int stock) {
        jdbc.update("INSERT INTO products(category_id,organization_id,name,price,stock,status) VALUES(?,?,?,10.00,?,'ACTIVE')",
                category, org, name, stock);
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private void item(long order, long product, int quantity, String price) {
        BigDecimal unit = new BigDecimal(price);
        jdbc.update("INSERT INTO order_items(order_id,product_id,product_name,unit_price,quantity,subtotal) VALUES(?,?,?,?,?,?)",
                order, product, "p" + product, unit, quantity, unit.multiply(BigDecimal.valueOf(quantity)));
    }

    private JsonNode order(String status, String captureStatus, String captureId) {
        ObjectNode n = json.createObjectNode().put("id", provider).put("intent", "CAPTURE").put("status", status);
        n.putObject("payer").put("email_address", "buyer@private.example");
        ObjectNode unit = n.putArray("purchase_units").addObject().put("reference_id", checkoutId);
        unit.putObject("payee").put("merchant_id", "MERCHANT1");
        unit.putObject("amount").put("currency_code", "EUR").put("value", TOTAL.toPlainString());
        if (captureStatus != null) {
            ObjectNode c = unit.putObject("payments").putArray("captures").addObject().put("id", captureId)
                    .put("status", captureStatus).put("final_capture", true).put("create_time", "2026-11-02T10:00:00Z");
            c.putObject("amount").put("currency_code", "EUR").put("value", TOTAL.toPlainString());
        }
        return n;
    }

    private void evidence(String status, String captureStatus, String captureId) {
        JsonNode node = order(status, captureStatus, captureId);
        doReturn(node).when(gateway).get(provider);
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, String username, String role) {
        return request.with(user(username).roles(role));
    }

    private JsonNode detail() throws Exception {
        MvcResult result = mockMvc.perform(as(get(BASE + "/checkouts/" + checkoutId), superAdmin, "SUPER_ADMIN")).andReturn();
        assertEquals(200, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        return json.readTree(result.getResponse().getContentAsString());
    }

    private MvcResult resolve(String username, String key, String action, String expectedState, String fingerprint, String reason) throws Exception {
        String body = json.createObjectNode().put("action", action).put("expectedState", expectedState)
                .put("evidenceFingerprint", fingerprint).put("reason", reason).toString();
        return mockMvc.perform(as(post(BASE + "/checkouts/" + checkoutId + "/resolve"), username, "SUPER_ADMIN")
                .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
    }

    private int stock(long product) {
        return jdbc.queryForObject("SELECT stock FROM products WHERE id=?", Integer.class, product);
    }

    private String state() {
        return jdbc.queryForObject("SELECT state FROM paypal_checkouts WHERE id=?", String.class, checkoutId);
    }

    private List<String> orderStatuses() {
        return jdbc.queryForList("SELECT status FROM orders WHERE checkout_group_id=? ORDER BY id", String.class, groupId);
    }

    private long count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    private void webhook(String eventId, String type) {
        ObjectNode event = json.createObjectNode().put("id", eventId).put("event_type", type);
        if (type.startsWith("CHECKOUT.ORDER.")) event.putObject("resource").put("id", provider);
        else event.putObject("resource").putObject("supplementary_data").putObject("related_ids").put("order_id", provider);
        checkoutService.webhook(Map.of(), event);
    }

    @Test void onlyDatabaseSuperAdminsReachRecovery() throws Exception {
        assertEquals(403, mockMvc.perform(as(get(BASE), buyer, "USER")).andReturn().getResponse().getStatus());
        assertEquals(403, mockMvc.perform(as(get(BASE), admin, "ADMIN")).andReturn().getResponse().getStatus());
        int anonymous = mockMvc.perform(get(BASE)).andReturn().getResponse().getStatus();
        assertTrue(anonymous == 401 || anonymous == 403, "anonymous status " + anonymous);
        // A token claiming SUPER_ADMIN for a buyer account is refused by the database role check.
        assertEquals(403, mockMvc.perform(as(get(BASE), buyer, "SUPER_ADMIN")).andReturn().getResponse().getStatus());
        assertEquals(200, mockMvc.perform(as(get(BASE), superAdmin, "SUPER_ADMIN")).andReturn().getResponse().getStatus());
        // The order lookup resolves to the checkout the order belongs to.
        MvcResult byOrder = mockMvc.perform(as(get(BASE + "/orders/" + secondOrder), superAdmin, "SUPER_ADMIN")).andReturn();
        assertEquals(checkoutId, json.readTree(byOrder.getResponse().getContentAsString()).path("checkoutId").asText());
    }

    @Test void notFoundAccessDeniedOrUnavailableEvidenceChangesNothing() throws Exception {
        for (RuntimeException failure : List.of(new PayPalGateway.Unavailable("NOT_FOUND", 404, "dbg"),
                new PayPalGateway.Unavailable("ACCESS_DENIED", 401, null),
                new IllegalStateException("PayPal unavailable; check payment status"))) {
            doThrow(failure).when(gateway).get(provider);
            JsonNode detail = detail();
            assertTrue(detail.path("allowedActions").isEmpty(), detail.toString());
            assertNotEquals("AVAILABLE", detail.path("evidence").path("availability").asText());
            MvcResult refused = resolve(superAdmin, UUID.randomUUID().toString(), "ABANDON", "RECOVERY_REQUIRED",
                    detail.path("evidenceFingerprint").asText(), "Order 31 style: PayPal returned no evidence");
            assertEquals(409, refused.getResponse().getStatus());
        }
        assertEquals("RECOVERY_REQUIRED", state());
        assertEquals(List.of("PENDING", "PENDING"), orderStatuses());
        assertEquals(5, stock(productA));
        assertEquals(7, stock(productB));
        assertEquals(0, count("SELECT COUNT(*) FROM paypal_recovery_requests WHERE checkout_id=?", checkoutId));
        verify(gateway, never()).capture(anyString(), anyString());
    }

    @Test void abandonCancelsTheWholeMultiSellerGroupAtomicallyAndIdempotently() throws Exception {
        JsonNode detail = detail();
        assertEquals("[\"ABANDON\"]", detail.path("allowedActions").toString());
        assertFalse(detail.toString().contains("buyer@private.example"), "admin response leaked payer data");
        String key = UUID.randomUUID().toString();
        String fingerprint = detail.path("evidenceFingerprint").asText();
        MvcResult done = resolve(superAdmin, key, "ABANDON", "RECOVERY_REQUIRED", fingerprint, "Buyer approved but never captured");
        assertEquals(200, done.getResponse().getStatus(), done.getResponse().getContentAsString());

        assertEquals("ABANDONED", state());
        assertEquals(List.of("CANCELLED", "CANCELLED"), orderStatuses());
        assertEquals(5 + 3, stock(productA), "product A restored once across both sellers' orders");
        assertEquals(7 + 1, stock(productB));
        assertEquals(2, count("SELECT COUNT(*) FROM payments p JOIN orders o ON o.id=p.order_id WHERE o.checkout_group_id=? AND p.status='FAILED'", groupId));
        long superAdminId = jdbc.queryForObject("SELECT id FROM users WHERE username=?", Long.class, superAdmin);
        assertEquals(2, count("SELECT COUNT(*) FROM order_status_history WHERE order_id IN (?,?) AND to_status='CANCELLED' AND changed_by_user_id=?", firstOrder, secondOrder, superAdminId));
        Map<String, Object> audit = jdbc.queryForMap("SELECT event_type,actor_user_id,from_state,to_state,CAST(evidence AS CHAR) AS evidence FROM paypal_recovery_events WHERE checkout_id=? AND event_type='RESOLVED_ABANDONED'", checkoutId);
        assertEquals(superAdminId, ((Number) audit.get("actor_user_id")).longValue());
        assertFalse(String.valueOf(audit.get("evidence")).contains("buyer@private.example"));
        assertTrue(count("SELECT COUNT(*) FROM notification_inbox WHERE event_key=? AND kind='ORDER_STATUS'", "order:" + firstOrder + ":CANCELLED") > 0, "cancellation notice queued in the outbox");

        // Same key, same request: the stored answer, no second stock restoration.
        MvcResult replay = resolve(superAdmin, key, "ABANDON", "RECOVERY_REQUIRED", fingerprint, "Buyer approved but never captured");
        assertEquals(200, replay.getResponse().getStatus());
        assertEquals(json.readTree(done.getResponse().getContentAsString()), json.readTree(replay.getResponse().getContentAsString()));
        assertEquals(8, stock(productA));
        // Same key with a different request, or by another admin: conflict.
        assertEquals(409, resolve(superAdmin, key, "ABANDON", "RECOVERY_REQUIRED", fingerprint, "A different reason entirely").getResponse().getStatus());
        assertEquals(409, resolve(secondSuperAdmin, key, "ABANDON", "RECOVERY_REQUIRED", fingerprint, "Buyer approved but never captured").getResponse().getStatus());
        // A new key against the old view is stale.
        assertEquals(409, resolve(superAdmin, UUID.randomUUID().toString(), "ABANDON", "RECOVERY_REQUIRED", fingerprint, "Buyer approved but never captured").getResponse().getStatus());
        assertEquals(8, stock(productA));
        assertEquals(7 + 1, stock(productB));
    }

    @Test void abandonedCheckoutBlocksEveryCapturePath() throws Exception {
        JsonNode detail = detail();
        assertEquals(200, resolve(superAdmin, UUID.randomUUID().toString(), "ABANDON", "RECOVERY_REQUIRED",
                detail.path("evidenceFingerprint").asText(), "Abandon before testing capture paths").getResponse().getStatus());
        // Buyer check / capture endpoints and an approval webhook never ask PayPal to capture.
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(buyer, "n/a", List.of()));
        try {
            assertEquals("ABANDONED", checkoutService.sync(checkoutId, true).state());
            assertEquals("ABANDONED", checkoutService.sync(checkoutId, false).state());
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
        webhook("WH-APPROVED-" + checkoutId, "CHECKOUT.ORDER.APPROVED");
        verify(gateway, never()).capture(anyString(), anyString());
        assertEquals("ABANDONED", state());
    }

    @Test void lateCaptureOpensOneAuditedCaseAndAlertsSuperAdmins() throws Exception {
        JsonNode detail = detail();
        resolve(superAdmin, UUID.randomUUID().toString(), "ABANDON", "RECOVERY_REQUIRED",
                detail.path("evidenceFingerprint").asText(), "Abandon, then PayPal reports a capture");
        evidence("COMPLETED", "COMPLETED", "LATECAPTURE1");
        webhook("WH-LATE-1", "PAYMENT.CAPTURE.COMPLETED");
        webhook("WH-LATE-1", "PAYMENT.CAPTURE.COMPLETED");      // same delivery again
        webhook("WH-LATE-2", "PAYMENT.CAPTURE.COMPLETED");      // new delivery, same capture
        assertEquals(1, count("SELECT COUNT(*) FROM paypal_review_cases WHERE checkout_id=? AND kind='LATE_CAPTURE' AND capture_key='LATECAPTURE1'", checkoutId));
        String preserved = jdbc.queryForObject("SELECT CAST(evidence AS CHAR) FROM paypal_review_cases WHERE checkout_id=? AND kind='LATE_CAPTURE'", String.class, checkoutId);
        assertTrue(preserved.contains("LATECAPTURE1") && preserved.contains("35.50"), preserved);
        assertFalse(preserved.contains("buyer@private.example"));
        assertEquals(1, count("SELECT COUNT(*) FROM paypal_recovery_events WHERE checkout_id=? AND event_type='LATE_CAPTURE'", checkoutId));
        long superAdminId = jdbc.queryForObject("SELECT id FROM users WHERE username=?", Long.class, superAdmin);
        assertEquals(1, count("SELECT COUNT(*) FROM notification_inbox WHERE recipient_id=? AND kind='PAYPAL_REVIEW' AND event_key LIKE ?", superAdminId, "paypal-review:" + checkoutId + ":LATE_CAPTURE%"));
        long buyerAlerts = count("SELECT COUNT(*) FROM notification_inbox WHERE recipient_id=? AND kind='PAYPAL_REVIEW'", buyerId);
        assertEquals(0, buyerAlerts, "only SUPER_ADMIN is alerted");
        assertEquals("ABANDONED", state());
        assertEquals(8, stock(productA), "late capture does not touch stock again");
        verify(gateway, never()).capture(anyString(), anyString());
    }

    @Test void repeatedRefundIsNoOpButAConflictingCaptureIsReviewed() throws Exception {
        evidence("COMPLETED", "REFUNDED", "CAPTURE1");
        JsonNode detail = detail();
        assertEquals("[\"CONFIRM_REFUNDED\"]", detail.path("allowedActions").toString());
        assertEquals(200, resolve(superAdmin, UUID.randomUUID().toString(), "CONFIRM_REFUNDED", "RECOVERY_REQUIRED",
                detail.path("evidenceFingerprint").asText(), "Refunded in the PayPal dashboard").getResponse().getStatus());
        assertEquals("REFUNDED", state());
        assertEquals(List.of("CANCELLED", "CANCELLED"), orderStatuses());
        assertEquals(2, count("SELECT COUNT(*) FROM payments p JOIN orders o ON o.id=p.order_id WHERE o.checkout_group_id=? AND p.status='REFUNDED' AND p.transaction_id='CAPTURE1'", groupId));
        long events = count("SELECT COUNT(*) FROM paypal_recovery_events WHERE checkout_id=?", checkoutId);

        webhook("WH-REFUND-1", "PAYMENT.CAPTURE.REFUNDED");
        webhook("WH-REFUND-2", "PAYMENT.CAPTURE.REFUNDED");
        assertEquals(events, count("SELECT COUNT(*) FROM paypal_recovery_events WHERE checkout_id=?", checkoutId));
        assertEquals(0, count("SELECT COUNT(*) FROM paypal_review_cases WHERE checkout_id=?", checkoutId));
        assertEquals("DUPLICATE_REFUND", jdbc.queryForObject("SELECT outcome FROM paypal_webhook_events WHERE event_id='WH-REFUND-2' AND checkout_id=?", String.class, checkoutId));

        evidence("COMPLETED", "COMPLETED", "OTHERCAPTURE9");
        webhook("WH-CONFLICT-1", "PAYMENT.CAPTURE.COMPLETED");
        assertEquals(1, count("SELECT COUNT(*) FROM paypal_review_cases WHERE checkout_id=? AND kind='CAPTURE_CONFLICT' AND capture_key='OTHERCAPTURE9'", checkoutId));
        assertEquals(5 + 3, stock(productA));
    }

    @Test void staleEvidenceIsRefused() throws Exception {
        JsonNode approved = detail();
        evidence("COMPLETED", "COMPLETED", "CAPTURE1");      // PayPal changed after the admin looked
        MvcResult stale = resolve(superAdmin, UUID.randomUUID().toString(), "ABANDON", "RECOVERY_REQUIRED",
                approved.path("evidenceFingerprint").asText(), "Acting on an outdated view");
        assertEquals(409, stale.getResponse().getStatus());
        assertEquals("RECOVERY_REQUIRED", state());
        assertEquals(5, stock(productA));
        JsonNode paid = detail();
        assertEquals("[\"CONFIRM_PAID\"]", paid.path("allowedActions").toString());
        assertEquals(409, resolve(superAdmin, UUID.randomUUID().toString(), "ABANDON", "RECOVERY_REQUIRED",
                paid.path("evidenceFingerprint").asText(), "Abandon is not what the evidence allows").getResponse().getStatus());
        assertEquals(200, resolve(superAdmin, UUID.randomUUID().toString(), "CONFIRM_PAID", "RECOVERY_REQUIRED",
                paid.path("evidenceFingerprint").asText(), "Capture verified in PayPal").getResponse().getStatus());
        assertEquals("PAID", state());
        assertEquals(List.of("PENDING", "PENDING"), orderStatuses());
        assertEquals(2, count("SELECT COUNT(*) FROM payments p JOIN orders o ON o.id=p.order_id WHERE o.checkout_group_id=? AND p.status='SUCCESS' AND p.transaction_id='CAPTURE1'", groupId));
        assertEquals(5, stock(productA));
    }

    @Test void shippedOrdersAreOnlyFlaggedWithEvidencePreserved() throws Exception {
        evidence("COMPLETED", "COMPLETED", "CAPTURE1");
        jdbc.update("UPDATE orders SET status='SHIPPED' WHERE id=?", secondOrder);
        JsonNode detail = detail();
        assertEquals("[\"FLAG_MANUAL\"]", detail.path("allowedActions").toString());
        String fingerprint = detail.path("evidenceFingerprint").asText();
        assertEquals(409, resolve(superAdmin, UUID.randomUUID().toString(), "ABANDON", "RECOVERY_REQUIRED", fingerprint, "Must not cancel shipped goods").getResponse().getStatus());
        assertEquals(200, resolve(superAdmin, UUID.randomUUID().toString(), "FLAG_MANUAL", "RECOVERY_REQUIRED", fingerprint, "Shipped already; handle manually").getResponse().getStatus());
        assertEquals("RECOVERY_REQUIRED", state());
        assertEquals(List.of("PENDING", "SHIPPED"), orderStatuses());
        assertEquals(5, stock(productA));
        String preserved = jdbc.queryForObject("SELECT CAST(evidence AS CHAR) FROM paypal_review_cases WHERE checkout_id=? AND kind='SHIPPED_ORDERS'", String.class, checkoutId);
        assertTrue(preserved.contains("CAPTURE1"), preserved);
        assertEquals(1, count("SELECT COUNT(*) FROM paypal_recovery_events WHERE checkout_id=? AND event_type='FLAGGED_MANUAL'", checkoutId));
    }

    @Test void concurrentResolutionsRestoreStockExactlyOnce() throws Exception {
        String fingerprint = detail().path("evidenceFingerprint").asText();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (String who : List.of(superAdmin, secondSuperAdmin)) {
            results.add(pool.submit(() -> {
                start.await();
                return resolve(who, UUID.randomUUID().toString(), "ABANDON", "RECOVERY_REQUIRED", fingerprint,
                        "Racing resolution by " + who).getResponse().getStatus();
            }));
        }
        start.countDown();
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> f : results) statuses.add(f.get(60, TimeUnit.SECONDS));
        pool.shutdown();
        Collections.sort(statuses);
        assertEquals(List.of(200, 409), statuses);
        assertEquals(8, stock(productA));
        assertEquals(8, stock(productB));
        assertEquals(1, count("SELECT COUNT(*) FROM paypal_recovery_events WHERE checkout_id=? AND event_type='RESOLVED_ABANDONED'", checkoutId));
    }

    @Test void webhookWaitingOnAResolutionSeesTheClosedCheckout() throws Exception {
        String fingerprint = detail().path("evidenceFingerprint").asText();
        CountDownLatch resolverInsideLock = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        JsonNode approved = order("APPROVED", null, null), captured = order("COMPLETED", "COMPLETED", "RACECAPTURE1");
        doAnswer(inv -> {
            if (calls.getAndIncrement() == 0) {      // the resolver, holding the checkout row lock
                resolverInsideLock.countDown();
                assertTrue(release.await(30, TimeUnit.SECONDS));
                return approved;
            }
            return captured;                          // PayPal captured remotely meanwhile
        }).when(gateway).get(provider);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Future<Integer> resolution = pool.submit(() -> resolve(superAdmin, UUID.randomUUID().toString(), "ABANDON",
                "RECOVERY_REQUIRED", fingerprint, "Resolution racing a webhook").getResponse().getStatus());
        assertTrue(resolverInsideLock.await(30, TimeUnit.SECONDS));
        Future<?> hook = pool.submit(() -> { webhook("WH-RACE-1", "PAYMENT.CAPTURE.COMPLETED"); return null; });
        Thread.sleep(500);                            // the webhook is now blocked on the row lock
        assertFalse(hook.isDone());
        release.countDown();
        assertEquals(200, resolution.get(60, TimeUnit.SECONDS));
        hook.get(60, TimeUnit.SECONDS);
        pool.shutdown();
        assertEquals("ABANDONED", state());
        assertEquals(1, count("SELECT COUNT(*) FROM paypal_review_cases WHERE checkout_id=? AND kind='LATE_CAPTURE' AND capture_key='RACECAPTURE1'", checkoutId));
        assertEquals(8, stock(productA));
        verify(gateway, never()).capture(anyString(), anyString());
    }

    @Test void malformedOrUnknownRequestsAreRejectedWithoutChanges() throws Exception {
        String fingerprint = detail().path("evidenceFingerprint").asText();
        String key = UUID.randomUUID().toString();
        assertEquals(400, resolve(superAdmin, key, "CAPTURE", "RECOVERY_REQUIRED", fingerprint, "Admins can never capture").getResponse().getStatus());
        assertEquals(400, resolve(superAdmin, key, "ABANDON", "RECOVERY_REQUIRED", fingerprint, "short").getResponse().getStatus());
        assertEquals(400, resolve(superAdmin, key, "ABANDON", "RECOVERY_REQUIRED", "not-a-fingerprint", "A long enough reason").getResponse().getStatus());
        assertEquals(400, resolve(superAdmin, "not-a-uuid", "ABANDON", "RECOVERY_REQUIRED", fingerprint, "A long enough reason").getResponse().getStatus());
        String body = json.createObjectNode().put("action", "ABANDON").put("expectedState", "RECOVERY_REQUIRED")
                .put("evidenceFingerprint", fingerprint).put("reason", "A long enough reason").toString();
        assertEquals(400, mockMvc.perform(as(post(BASE + "/checkouts/" + checkoutId + "/resolve"), superAdmin, "SUPER_ADMIN")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse().getStatus(), "missing Idempotency-Key");
        assertEquals(404, mockMvc.perform(as(get(BASE + "/checkouts/" + UUID.randomUUID()), superAdmin, "SUPER_ADMIN")).andReturn().getResponse().getStatus());
        assertEquals(404, mockMvc.perform(as(get(BASE + "/orders/999999999"), superAdmin, "SUPER_ADMIN")).andReturn().getResponse().getStatus());
        assertEquals(409, resolve(superAdmin, key, "ABANDON", "APPROVAL", fingerprint, "Wrong expected state").getResponse().getStatus());
        jdbc.update("UPDATE paypal_checkouts SET state='APPROVAL' WHERE id=?", checkoutId);
        assertTrue(detail().path("allowedActions").isEmpty(), "only RECOVERY_REQUIRED checkouts offer actions");
        assertEquals(409, resolve(superAdmin, key, "ABANDON", "APPROVAL", fingerprint, "Not under recovery").getResponse().getStatus());
        assertEquals(5, stock(productA));
        assertEquals(List.of("PENDING", "PENDING"), orderStatuses());
    }

    @Test void partlyCancelledOrChangedGroupsCannotBeResolved() throws Exception {
        jdbc.update("UPDATE orders SET status='CANCELLED' WHERE id=?", firstOrder);
        JsonNode detail = detail();
        assertTrue(detail.path("allowedActions").isEmpty(), detail.toString());
        assertEquals(409, resolve(superAdmin, UUID.randomUUID().toString(), "ABANDON", "RECOVERY_REQUIRED",
                detail.path("evidenceFingerprint").asText(), "Group already changed by hand").getResponse().getStatus());
        jdbc.update("UPDATE orders SET status='PENDING', total_amount=1.00 WHERE id=?", firstOrder);
        assertTrue(detail().path("allowedActions").isEmpty(), "totals no longer match the PayPal amount");
        assertEquals(5, stock(productA));
    }

    // ---- Review-case lifecycle ----

    private MvcResult close(String username, String role, String key, long caseId, String outcome, String reason) throws Exception {
        String body = json.createObjectNode().put("outcome", outcome).put("reason", reason).toString();
        return mockMvc.perform(as(post(BASE + "/cases/" + caseId + "/close"), username, role)
                .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
    }

    private long caseId(String kind) {
        return jdbc.queryForObject("SELECT id FROM paypal_review_cases WHERE checkout_id=? AND kind=?", Long.class, checkoutId, kind);
    }

    private Map<String, Object> reviewCase(long id) {
        return jdbc.queryForMap("SELECT status,closed_by_user_id,closed_at,closure_outcome,closure_reason,CAST(evidence AS CHAR) AS evidence FROM paypal_review_cases WHERE id=?", id);
    }

    private long userId(String username) {
        return jdbc.queryForObject("SELECT id FROM users WHERE username=?", Long.class, username);
    }

    @Test void resolutionClosesTheRecoveryEvidenceCasesItActedOnAtomically() throws Exception {
        evidence("COMPLETED", "COMPLETED", "CAPTURE1");
        webhook("WH-EVIDENCE-1", "PAYMENT.CAPTURE.COMPLETED");
        long evidenceCase = caseId("RECOVERY_EVIDENCE");
        String recorded = (String) reviewCase(evidenceCase).get("evidence");
        JsonNode detail = detail();
        assertEquals("[\"CONFIRM_PAID\"]", detail.path("allowedActions").toString());
        MvcResult done = resolve(superAdmin, UUID.randomUUID().toString().toUpperCase(Locale.ROOT), "CONFIRM_PAID",
                "RECOVERY_REQUIRED", detail.path("evidenceFingerprint").asText(), "Capture verified, fulfil the orders");
        assertEquals(200, done.getResponse().getStatus(), done.getResponse().getContentAsString());
        assertEquals("[" + evidenceCase + "]", json.readTree(done.getResponse().getContentAsString()).path("closedCaseIds").toString());

        Map<String, Object> closed = reviewCase(evidenceCase);
        assertEquals("CLOSED", closed.get("status"));
        assertEquals(userId(superAdmin), ((Number) closed.get("closed_by_user_id")).longValue());
        assertNotNull(closed.get("closed_at"));
        assertEquals("RESOLVED_CONFIRM_PAID", closed.get("closure_outcome"));
        assertEquals("Capture verified, fulfil the orders", closed.get("closure_reason"));
        assertEquals(recorded, closed.get("evidence"), "evidence is preserved exactly");
        assertEquals(1, count("SELECT COUNT(*) FROM paypal_recovery_events WHERE checkout_id=? AND event_type='CASES_CLOSED'", checkoutId));
        MvcResult list = mockMvc.perform(as(get(BASE), superAdmin, "SUPER_ADMIN")).andReturn();
        assertFalse(list.getResponse().getContentAsString().contains(checkoutId), "resolved, no open cases: off the list");
    }

    @Test void failedResolutionLeavesCasesOpen() throws Exception {
        evidence("COMPLETED", "COMPLETED", "CAPTURE1");
        webhook("WH-EVIDENCE-2", "PAYMENT.CAPTURE.COMPLETED");
        long evidenceCase = caseId("RECOVERY_EVIDENCE");
        String fingerprint = detail().path("evidenceFingerprint").asText();
        jdbc.update("UPDATE orders SET total_amount=total_amount+1 WHERE id=?", firstOrder);  // group changed meanwhile
        assertEquals(409, resolve(superAdmin, UUID.randomUUID().toString(), "CONFIRM_PAID", "RECOVERY_REQUIRED",
                fingerprint, "Group changed under the admin").getResponse().getStatus());
        assertEquals("OPEN", reviewCase(evidenceCase).get("status"));
        assertEquals("RECOVERY_REQUIRED", state());
    }

    @Test void manuallyHandledLateCaptureIsClosedWithADocumentedOutcome() throws Exception {
        resolve(superAdmin, UUID.randomUUID().toString(), "ABANDON", "RECOVERY_REQUIRED",
                detail().path("evidenceFingerprint").asText(), "Abandon, then PayPal reports a capture");
        evidence("COMPLETED", "COMPLETED", "LATE9");
        webhook("WH-LATE-9", "PAYMENT.CAPTURE.COMPLETED");
        long lateCase = caseId("LATE_CAPTURE");
        String recorded = (String) reviewCase(lateCase).get("evidence");
        long events = count("SELECT COUNT(*) FROM paypal_recovery_events WHERE checkout_id=?", checkoutId);
        String key = UUID.randomUUID().toString();

        assertEquals(403, close(buyer, "USER", key, lateCase, "REFUNDED_EXTERNALLY", "Buyers cannot close cases").getResponse().getStatus());
        assertEquals(403, close(admin, "ADMIN", key, lateCase, "REFUNDED_EXTERNALLY", "Admins cannot close cases").getResponse().getStatus());
        assertEquals(400, close(superAdmin, "SUPER_ADMIN", key, lateCase, "MAYBE", "Unknown outcome value").getResponse().getStatus());
        assertEquals(400, close(superAdmin, "SUPER_ADMIN", key, lateCase, "REFUNDED_EXTERNALLY", "short").getResponse().getStatus());
        assertEquals(404, close(superAdmin, "SUPER_ADMIN", key, 999999999L, "REFUNDED_EXTERNALLY", "No such case exists").getResponse().getStatus());
        assertEquals(409, close(superAdmin, "SUPER_ADMIN", key, lateCase, "CONFIRMED_NO_PAYMENT", "PayPal shows a capture though").getResponse().getStatus(),
                "a verified capture is never closed as no payment");

        MvcResult closed = close(superAdmin, "SUPER_ADMIN", key, lateCase, "REFUNDED_EXTERNALLY", "Refunded LATE9 in the PayPal dashboard");
        assertEquals(200, closed.getResponse().getStatus(), closed.getResponse().getContentAsString());
        Map<String, Object> row = reviewCase(lateCase);
        assertEquals("CLOSED", row.get("status"));
        assertEquals("REFUNDED_EXTERNALLY", row.get("closure_outcome"));
        assertEquals(userId(superAdmin), ((Number) row.get("closed_by_user_id")).longValue());
        assertEquals(recorded, row.get("evidence"));
        assertEquals(events + 1, count("SELECT COUNT(*) FROM paypal_recovery_events WHERE checkout_id=?", checkoutId));
        assertEquals(1, count("SELECT COUNT(*) FROM paypal_recovery_events WHERE checkout_id=? AND event_type='CASE_CLOSED' AND actor_user_id=?", checkoutId, userId(superAdmin)));
        assertEquals("ABANDONED", state());
        assertEquals(8, stock(productA), "closing a case never touches stock");

        // Replays, conflicts and a repeated webhook after closing.
        MvcResult replay = close(superAdmin, "SUPER_ADMIN", key, lateCase, "REFUNDED_EXTERNALLY", "Refunded LATE9 in the PayPal dashboard");
        assertEquals(json.readTree(closed.getResponse().getContentAsString()), json.readTree(replay.getResponse().getContentAsString()));
        assertEquals(409, close(superAdmin, "SUPER_ADMIN", key, lateCase, "ACCEPTED_AS_PAID", "Different outcome, same key").getResponse().getStatus());
        assertEquals(409, close(secondSuperAdmin, "SUPER_ADMIN", key, lateCase, "REFUNDED_EXTERNALLY", "Refunded LATE9 in the PayPal dashboard").getResponse().getStatus());
        assertEquals(409, close(superAdmin, "SUPER_ADMIN", UUID.randomUUID().toString(), lateCase, "NO_ACTION_REQUIRED", "Already closed earlier").getResponse().getStatus());
        webhook("WH-LATE-10", "PAYMENT.CAPTURE.COMPLETED");
        assertEquals(1, count("SELECT COUNT(*) FROM paypal_review_cases WHERE checkout_id=?", checkoutId));
        assertEquals("CLOSED", reviewCase(lateCase).get("status"), "a repeated report does not reopen a closed case");
    }

    @Test void shippedOrdersCanBeFlaggedEvenWhilePayPalAnswers404WithChangingDebugIds() throws Exception {
        AtomicInteger debug = new AtomicInteger();
        doAnswer(inv -> { throw new PayPalGateway.Unavailable("NOT_FOUND", 404, "dbg" + debug.incrementAndGet()); })
                .when(gateway).get(provider);
        jdbc.update("UPDATE orders SET status='DELIVERED' WHERE id=?", firstOrder);
        JsonNode detail = detail();
        assertEquals("[\"FLAG_MANUAL\"]", detail.path("allowedActions").toString());
        assertEquals("NOT_FOUND", detail.path("evidence").path("availability").asText());
        MvcResult flagged = resolve(superAdmin, UUID.randomUUID().toString(), "FLAG_MANUAL", "RECOVERY_REQUIRED",
                detail.path("evidenceFingerprint").asText(), "Delivered; PayPal has no record with our keys");
        assertEquals(200, flagged.getResponse().getStatus(), flagged.getResponse().getContentAsString());
        assertEquals("[]", json.readTree(flagged.getResponse().getContentAsString()).path("closedCaseIds").toString());
        long shipped = caseId("SHIPPED_ORDERS");
        assertEquals(200, close(superAdmin, "SUPER_ADMIN", UUID.randomUUID().toString(), shipped, "ACCEPTED_AS_PAID",
                "Confirmed with the buyer; delivery stands").getResponse().getStatus());
        assertEquals("RECOVERY_REQUIRED", state(), "closing the case does not resolve the checkout");
    }

    // ---- Buyer cancellation now uses the same atomic group cancellation ----

    @Test void buyerAbandonCancelsAMultiSellerGroupOnceWithSortedLocks() {
        jdbc.update("UPDATE paypal_checkouts SET state='APPROVAL' WHERE id=?", checkoutId);
        jdbc.update("UPDATE orders SET shipping_email='buyer-copy@example.test', shipping_name='Buyer' WHERE id=?", secondOrder);
        evidence("CREATED", null, null);
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(buyer, "n/a", List.of()));
        try {
            assertEquals("ABANDONED", checkoutService.abandon(checkoutId).state());
            assertEquals("ABANDONED", checkoutService.abandon(checkoutId).state(), "repeating is harmless");
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
        assertEquals(List.of("CANCELLED", "CANCELLED"), orderStatuses());
        assertEquals(8, stock(productA));
        assertEquals(8, stock(productB));
        assertEquals(2, count("SELECT COUNT(*) FROM payments p JOIN orders o ON o.id=p.order_id WHERE o.checkout_group_id=? AND p.status='FAILED'", groupId));
        assertEquals(1, count("SELECT COUNT(*) FROM paypal_recovery_events WHERE checkout_id=? AND event_type='BUYER_ABANDONED' AND actor_user_id=?", checkoutId, buyerId));
        assertEquals(2, count("SELECT COUNT(*) FROM order_status_history WHERE order_id IN (?,?) AND to_status='CANCELLED' AND changed_by_user_id=?", firstOrder, secondOrder, buyerId));
    }

    // ---- Reversals and evidence anomalies from webhooks ----

    private void markPaid() throws Exception {
        evidence("COMPLETED", "COMPLETED", "CAPTURE1");
        JsonNode detail = detail();
        assertEquals(200, resolve(superAdmin, UUID.randomUUID().toString(), "CONFIRM_PAID", "RECOVERY_REQUIRED",
                detail.path("evidenceFingerprint").asText(), "Paid; testing reversals").getResponse().getStatus());
    }

    private void reversal(String eventId, String capture, String value) {
        ObjectNode event = json.createObjectNode().put("id", eventId).put("event_type", "PAYMENT.CAPTURE.REVERSED");
        ObjectNode resource = event.putObject("resource").put("id", capture);
        resource.putObject("amount").put("currency_code", "EUR").put("value", value);
        resource.putObject("supplementary_data").putObject("related_ids").put("order_id", provider);
        checkoutService.webhook(Map.of(), event);
    }

    @Test void verifiedReversalMovesToReviewOnceAndConflictsAreCases() throws Exception {
        markPaid();
        reversal("WH-REV-1", "CAPTURE1", "35.50");
        assertEquals("REVIEW", state());
        assertEquals(2, count("SELECT COUNT(*) FROM payments p JOIN orders o ON o.id=p.order_id WHERE o.checkout_group_id=? AND p.status='FAILED'", groupId));
        assertEquals(1, count("SELECT COUNT(*) FROM paypal_review_cases WHERE checkout_id=? AND kind='REVERSAL'", checkoutId));
        reversal("WH-REV-2", "CAPTURE1", "35.50");
        assertEquals("DUPLICATE_REVERSAL", jdbc.queryForObject("SELECT outcome FROM paypal_webhook_events WHERE event_id='WH-REV-2' AND checkout_id=?", String.class, checkoutId));
        reversal("WH-REV-3", "OTHERCAPTURE", "35.50");
        reversal("WH-REV-4", "CAPTURE1", "not-a-number");
        assertEquals(1, count("SELECT COUNT(*) FROM paypal_review_cases WHERE checkout_id=? AND kind='REVERSAL_CONFLICT' AND capture_key='OTHERCAPTURE'", checkoutId));
        assertEquals(1, count("SELECT COUNT(*) FROM paypal_review_cases WHERE checkout_id=? AND kind='REVERSAL_CONFLICT' AND capture_key='CAPTURE1'", checkoutId));
        assertEquals(1, count("SELECT COUNT(*) FROM paypal_recovery_events WHERE checkout_id=? AND event_type='REVERSAL'", checkoutId));
    }

    @Test void mismatchedOrUnchangedEvidenceUnderRecoveryIsHandledWithoutStateChanges() throws Exception {
        webhook("WH-UNCHANGED-1", "CHECKOUT.ORDER.APPROVED");
        assertEquals("RECOVERY_UNCHANGED", jdbc.queryForObject("SELECT outcome FROM paypal_webhook_events WHERE event_id='WH-UNCHANGED-1'", String.class));
        JsonNode other = order("COMPLETED", "COMPLETED", "CAPTURE1");
        ((ObjectNode) other.path("purchase_units").path(0).path("payee")).put("merchant_id", "SOMEONEELSE");
        doReturn(other).when(gateway).get(provider);
        webhook("WH-MISMATCH-1", "PAYMENT.CAPTURE.COMPLETED");
        webhook("WH-MISMATCH-2", "PAYMENT.CAPTURE.COMPLETED");
        assertEquals(1, count("SELECT COUNT(*) FROM paypal_review_cases WHERE checkout_id=? AND kind='EVIDENCE_MISMATCH'", checkoutId));
        assertEquals("DUPLICATE_EVIDENCE_MISMATCH", jdbc.queryForObject("SELECT outcome FROM paypal_webhook_events WHERE event_id='WH-MISMATCH-2'", String.class));
        assertTrue(detail().path("allowedActions").isEmpty(), "mismatched evidence permits nothing");
        assertEquals("RECOVERY_REQUIRED", state());
    }

    @Test void closedCheckoutWithMismatchOrFailedCaptureEvidence() throws Exception {
        resolve(superAdmin, UUID.randomUUID().toString(), "ABANDON", "RECOVERY_REQUIRED",
                detail().path("evidenceFingerprint").asText(), "Abandon before odd webhooks arrive");
        evidence("COMPLETED", "DECLINED", "DECLINED1");
        webhook("WH-DECLINED-1", "PAYMENT.CAPTURE.DENIED");
        assertEquals("NO_CAPTURE", jdbc.queryForObject("SELECT outcome FROM paypal_webhook_events WHERE event_id='WH-DECLINED-1'", String.class));
        JsonNode other = order("COMPLETED", "COMPLETED", "CAPTURE1");
        ((ObjectNode) other.path("purchase_units").path(0)).put("reference_id", "somebody-else");
        doReturn(other).when(gateway).get(provider);
        webhook("WH-MISMATCH-3", "PAYMENT.CAPTURE.COMPLETED");
        assertEquals(1, count("SELECT COUNT(*) FROM paypal_review_cases WHERE checkout_id=? AND kind='EVIDENCE_MISMATCH'", checkoutId));
        assertEquals(0, count("SELECT COUNT(*) FROM paypal_review_cases WHERE checkout_id=? AND kind='LATE_CAPTURE'", checkoutId));
    }

    // ---- Guards inside the group transitions (callers normally prevent these) ----

    @Test void groupTransitionsRefuseInconsistentGroups() {
        TransactionTemplate tx = new TransactionTemplate(manager);
        PayPalRecoveryEvidence.Snapshot approved = PayPalRecoveryEvidence.notCreated();
        assertThrows(IllegalArgumentException.class, () -> tx.executeWithoutResult(s -> transitions.cancelGroup(
                checkoutId, groupId, "RECOVERY_REQUIRED", "PAID", "FAILED", approved, null, "x", "X")));
        assertThrows(IllegalArgumentException.class, () -> tx.executeWithoutResult(s -> transitions.cancelGroup(
                checkoutId, groupId, "RECOVERY_REQUIRED", "ABANDONED", "SUCCESS", approved, null, "x", "X")));
        assertThrows(com.omnia.backend.common.exception.ConflictException.class, () -> tx.executeWithoutResult(s ->
                transitions.settlePaid(checkoutId, groupId, TOTAL, "RECOVERY_REQUIRED", approved, null, "x", "X")),
                "PAID needs verified capture evidence");
        assertThrows(com.omnia.backend.common.exception.ConflictException.class, () -> tx.executeWithoutResult(s ->
                transitions.lockGroup(checkoutId, -1L)));
        jdbc.update("UPDATE orders SET status='SHIPPED' WHERE id=?", firstOrder);
        assertThrows(com.omnia.backend.common.exception.ConflictException.class, () -> tx.executeWithoutResult(s -> transitions.cancelGroup(
                checkoutId, groupId, "RECOVERY_REQUIRED", "ABANDONED", "FAILED", approved, null, "x", "X")));
        jdbc.update("UPDATE orders SET status='CANCELLED' WHERE id=?", firstOrder);
        assertThrows(com.omnia.backend.common.exception.ConflictException.class, () -> tx.executeWithoutResult(s -> transitions.cancelGroup(
                checkoutId, groupId, "RECOVERY_REQUIRED", "ABANDONED", "FAILED", approved, null, "x", "X")));
        assertThrows(com.omnia.backend.common.exception.ConflictException.class, () -> tx.executeWithoutResult(s ->
                transitions.settlePaid(checkoutId, groupId, TOTAL, "RECOVERY_REQUIRED", approved, null, "x", "X")));
        assertEquals(5, stock(productA));
        assertEquals("RECOVERY_REQUIRED", state());
    }

    @Test void webhookWithoutEvidenceUnderRecoveryIsRetriedNotRecorded() {
        doThrow(new PayPalGateway.Unavailable("NOT_FOUND", 404, null)).when(gateway).get(provider);
        assertThrows(IllegalStateException.class, () -> webhook("WH-404-1", "PAYMENT.CAPTURE.COMPLETED"));
        assertEquals(0, count("SELECT COUNT(*) FROM paypal_webhook_events WHERE event_id='WH-404-1'"));
        assertEquals("RECOVERY_REQUIRED", state());
        evidence("COMPLETED", "COMPLETED", "CAPTURE1");
        webhook("WH-OK-1", "PAYMENT.CAPTURE.COMPLETED");
        assertEquals(1, count("SELECT COUNT(*) FROM paypal_review_cases WHERE checkout_id=? AND kind='RECOVERY_EVIDENCE'", checkoutId));
        assertEquals("RECOVERY_REQUIRED", state(), "webhooks never resolve a checkout under recovery");
    }

    // ---- Lock order against checkout creation and cancel-by-key (InnoDB deadlocks A and B) ----
    //
    // A raw JDBC session plays the competing transaction, statement by statement, in the order the
    // production code takes its locks. The recovery or abandon runs through the real code on a worker
    // thread. Each step waits until performance_schema shows the worker blocked on a given table, so
    // the interleaving is exact rather than timing based. Before the fix these interleavings ended in
    // ERROR 1213 (deadlock): either the raw session or the worker was rolled back.

    @Autowired javax.sql.DataSource dataSource;
    @Autowired org.springframework.core.env.Environment environment;

    /** Root can read performance_schema lock tables; Testcontainers gives root the same password. */
    private java.sql.Connection rootConnection() throws java.sql.SQLException {
        return java.sql.DriverManager.getConnection(environment.getProperty("spring.datasource.url"), "root",
                environment.getProperty("spring.datasource.password"));
    }

    /** Blocks until some transaction waits for a lock on {@code table}; fails after 20 seconds. */
    private void awaitLockWaitOn(String table) throws Exception {
        try (java.sql.Connection root = rootConnection();
             java.sql.PreparedStatement query = root.prepareStatement(
                     "SELECT COUNT(*) FROM performance_schema.data_lock_waits w JOIN performance_schema.data_locks l ON l.engine_lock_id=w.requesting_engine_lock_id WHERE l.object_schema=? AND l.object_name=?")) {
            query.setString(1, root.getCatalog());
            query.setString(2, table);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
            while (System.nanoTime() < deadline) {
                try (java.sql.ResultSet rows = query.executeQuery()) {
                    rows.next();
                    if (rows.getLong(1) > 0) return;
                }
                Thread.sleep(25);
            }
        }
        fail("No transaction ever waited for a lock on " + table);
    }

    private void sql(java.sql.Connection c, String statement, Object... args) throws java.sql.SQLException {
        try (java.sql.PreparedStatement s = c.prepareStatement(statement)) {
            for (int i = 0; i < args.length; i++) s.setObject(i + 1, args[i]);
            s.execute();
        }
    }

    /** The competing transaction: autocommit off, short lock wait so a regression fails fast. */
    private java.sql.Connection competingTransaction() throws java.sql.SQLException {
        java.sql.Connection c = dataSource.getConnection();
        c.setAutoCommit(false);
        sql(c, "SET SESSION innodb_lock_wait_timeout=20");
        return c;
    }

    private Future<Integer> resolveAbandonInBackground(ExecutorService pool) throws Exception {
        String fingerprint = detail().path("evidenceFingerprint").asText();
        return pool.submit(() -> resolve(superAdmin, UUID.randomUUID().toString(), "ABANDON", "RECOVERY_REQUIRED",
                fingerprint, "Lock order regression check").getResponse().getStatus());
    }

    private Future<String> buyerAbandonInBackground(ExecutorService pool) {
        jdbc.update("UPDATE paypal_checkouts SET state='APPROVAL' WHERE id=?", checkoutId);
        evidence("CREATED", null, null);
        return pool.submit(() -> asBuyer(() -> checkoutService.abandon(checkoutId).state()));
    }

    private void assertCancelledExactlyOnce() {
        assertEquals(List.of("CANCELLED", "CANCELLED"), orderStatuses());
        assertEquals(8, stock(productA), "5 + 2 + 1 restored once");
        assertEquals(8, stock(productB), "7 + 1 restored once");
        assertEquals(2, paymentsWithStatus("FAILED"));
        assertEquals("ABANDONED", state());
    }

    /** Deadlock A: the checkout inserts its order into the gap above our group while holding a product we need. */
    private void newestGroupCancellationDoesNotBlockACheckoutInsert(Future<?> worker) throws Exception {
        assertEquals(groupId, count("SELECT MAX(checkout_group_id) FROM orders"), "the recovered group is the newest");
        try (java.sql.Connection checkout = competingTransaction()) {
            sql(checkout, "SELECT id FROM products WHERE id=? FOR UPDATE", productA);    // CheckoutServiceImpl: products first
            awaitLockWaitOn("products");                                                   // the cancellation now waits for productA
            sql(checkout, "INSERT INTO checkout_groups(checkout_reference,user_id,order_count,total_amount) VALUES(UUID(),?,1,10.00)", buyerId);
            sql(checkout, "INSERT INTO orders(user_id,checkout_group_id,total_amount,status) VALUES(?,LAST_INSERT_ID(),10.00,'PENDING')", buyerId);
            checkout.rollback();
        }
    }

    @Test void recoveryOfTheNewestGroupDoesNotDeadlockWithAConcurrentCheckout() throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<Integer> resolved = resolveAbandonInBackground(pool);
            newestGroupCancellationDoesNotBlockACheckoutInsert(resolved);
            assertEquals(200, resolved.get(60, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
        assertCancelledExactlyOnce();
        assertEquals(1, count("SELECT COUNT(*) FROM paypal_recovery_events WHERE checkout_id=? AND event_type='RESOLVED_ABANDONED'", checkoutId));
    }

    @Test void buyerAbandonOfTheNewestGroupDoesNotDeadlockWithAConcurrentCheckout() throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<String> abandoned = buyerAbandonInBackground(pool);
            newestGroupCancellationDoesNotBlockACheckoutInsert(abandoned);
            assertEquals("ABANDONED", abandoned.get(60, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
        assertCancelledExactlyOnce();
        assertEquals(1, count("SELECT COUNT(*) FROM paypal_recovery_events WHERE checkout_id=? AND event_type='BUYER_ABANDONED'", checkoutId));
    }

    /** Deadlock B: the buyer's own PayPal checkout holds their users row, then needs a product. */
    @Test void recoveryDoesNotDeadlockWithTheSameBuyerCreatingAPayPalCheckout() throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try (java.sql.Connection create = competingTransaction()) {
            sql(create, "SELECT id FROM users WHERE id=? FOR UPDATE", buyerId);          // PayPalCheckoutService.create
            Future<Integer> resolved = resolveAbandonInBackground(pool);
            awaitLockWaitOn("users");                                                     // the resolution waits for the buyer row
            sql(create, "SELECT id FROM products WHERE id=? FOR UPDATE", productA);       // CheckoutServiceImpl: products next
            create.rollback();
            assertEquals(200, resolved.get(60, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
        assertCancelledExactlyOnce();
    }

    /** Deadlock B, cancel-by-key variant: the buyer's users row, then this checkout row. */
    @Test void recoveryDoesNotDeadlockWithTheSameBuyerCancellingByRequestKey() throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try (java.sql.Connection cancel = competingTransaction()) {
            sql(cancel, "SELECT id FROM users WHERE id=? FOR UPDATE", buyerId);          // PayPalCheckoutService.cancelRequest
            Future<Integer> resolved = resolveAbandonInBackground(pool);
            awaitLockWaitOn("users");
            sql(cancel, "SELECT id FROM paypal_checkouts WHERE id=? FOR UPDATE", checkoutId);  // abandon(): locked(id)
            cancel.rollback();
            assertEquals(200, resolved.get(60, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
        assertCancelledExactlyOnce();
    }

    @Test void racingConfirmPaidSettlesExactlyOnce() throws Exception {
        evidence("COMPLETED", "COMPLETED", "CAPTURE1");
        String fingerprint = detail().path("evidenceFingerprint").asText();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<Integer>> results = new ArrayList<>();
        try (java.sql.Connection gate = competingTransaction()) {
            // Hold the checkout row so both resolutions queue on it, then release them together.
            sql(gate, "SELECT id FROM paypal_checkouts WHERE id=? FOR UPDATE", checkoutId);
            for (String who : List.of(superAdmin, secondSuperAdmin))
                results.add(pool.submit(() -> resolve(who, UUID.randomUUID().toString(), "CONFIRM_PAID", "RECOVERY_REQUIRED",
                        fingerprint, "Racing settlement by " + who).getResponse().getStatus()));
            try (java.sql.Connection root = rootConnection();
                 java.sql.PreparedStatement waiting = root.prepareStatement(
                         // One row per (waiter, blocker) pair; the second waiter also waits behind the first.
                         "SELECT COUNT(DISTINCT w.requesting_engine_transaction_id) FROM performance_schema.data_lock_waits w JOIN performance_schema.data_locks l ON l.engine_lock_id=w.requesting_engine_lock_id WHERE l.object_name='paypal_checkouts'")) {
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
                long queued = 0;
                while (queued < 2 && System.nanoTime() < deadline) {
                    try (java.sql.ResultSet rows = waiting.executeQuery()) { rows.next(); queued = rows.getLong(1); }
                    if (queued < 2) Thread.sleep(25);
                }
                assertEquals(2, queued, "both resolutions queue on the checkout row");
            }
            gate.rollback();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> f : results) statuses.add(f.get(60, TimeUnit.SECONDS));
            Collections.sort(statuses);
            assertEquals(List.of(200, 409), statuses);
        } finally {
            pool.shutdownNow();
        }
        assertEquals("PAID", state());
        for (Map<String, Object> payment : groupPayments()) {
            assertEquals("SUCCESS", payment.get("status"));
            assertEquals("CAPTURE1", payment.get("transaction_id"));
        }
        assertEquals(1, count("SELECT COUNT(*) FROM paypal_recovery_events WHERE checkout_id=? AND event_type='RESOLVED_PAID'", checkoutId));
        assertEquals(5, stock(productA));
        assertEquals(7, stock(productB));
    }

    // ---- CONFIRM_PAID settles only the expected PayPal payment rows, or nothing at all ----

    private long paymentId(long order) {
        return jdbc.queryForObject("SELECT id FROM payments WHERE order_id=?", Long.class, order);
    }

    private List<Map<String, Object>> groupPayments() {
        return jdbc.queryForList("SELECT p.order_id,p.method,p.status,p.transaction_id,p.paid_at FROM payments p JOIN orders o ON o.id=p.order_id WHERE o.checkout_group_id=? ORDER BY p.order_id", groupId);
    }

    private MvcResult confirmPaid() throws Exception {
        JsonNode detail = detail();
        assertEquals("[\"CONFIRM_PAID\"]", detail.path("allowedActions").toString(), detail.toString());
        return resolve(superAdmin, UUID.randomUUID().toString(), "CONFIRM_PAID", "RECOVERY_REQUIRED",
                detail.path("evidenceFingerprint").asText(), "Capture verified, fulfil the orders");
    }

    @Test void confirmPaidRefusesMissingOrInconsistentPaymentRowsAndChangesNothing() throws Exception {
        evidence("COMPLETED", "COMPLETED", "CAPTURE1");
        long secondPayment = paymentId(secondOrder);
        Map<String, Runnable> breakages = new LinkedHashMap<>();
        breakages.put("missing", () -> jdbc.update("DELETE FROM payments WHERE id=?", secondPayment));
        breakages.put("failed", () -> jdbc.update("UPDATE payments SET status='FAILED' WHERE id=?", secondPayment));
        breakages.put("refunded", () -> jdbc.update("UPDATE payments SET status='REFUNDED',transaction_id='CAPTURE1' WHERE id=?", secondPayment));
        breakages.put("other capture", () -> jdbc.update("UPDATE payments SET status='SUCCESS',transaction_id='OTHERCAPTURE9' WHERE id=?", secondPayment));
        breakages.put("not PayPal", () -> jdbc.update("UPDATE payments SET method='CASH_ON_DELIVERY' WHERE id=?", secondPayment));
        breakages.put("no status", () -> jdbc.update("UPDATE payments SET status=NULL WHERE id=?", secondPayment));

        for (Map.Entry<String, Runnable> breakage : breakages.entrySet()) {
            breakage.getValue().run();
            List<Map<String, Object>> before = groupPayments();

            MvcResult refused = confirmPaid();

            assertEquals(409, refused.getResponse().getStatus(), breakage.getKey() + ": " + refused.getResponse().getContentAsString());
            // Atomic: the valid row was not settled either, and nothing else moved.
            assertEquals(before, groupPayments(), breakage.getKey());
            assertEquals("PENDING", jdbc.queryForObject("SELECT status FROM payments WHERE order_id=?", String.class, firstOrder), breakage.getKey());
            assertEquals("RECOVERY_REQUIRED", state(), breakage.getKey());
            assertEquals(List.of("PENDING", "PENDING"), orderStatuses(), breakage.getKey());
            assertEquals(0, count("SELECT COUNT(*) FROM paypal_recovery_events WHERE checkout_id=? AND event_type LIKE 'RESOLVED%'", checkoutId), breakage.getKey());
            assertEquals(0, count("SELECT COUNT(*) FROM paypal_recovery_requests WHERE checkout_id=?", checkoutId), breakage.getKey());

            // Restore the second payment exactly as the fixture created it.
            jdbc.update("DELETE FROM payments WHERE order_id=?", secondOrder);
            jdbc.update("INSERT INTO payments(id,order_id,method,status) VALUES(?,?,'PAYPAL','PENDING')", secondPayment, secondOrder);
        }
        assertEquals(5, stock(productA));
        assertEquals(7, stock(productB));
    }

    @Test void confirmPaidAcceptsAPaymentAlreadySettledWithTheSameCapture() throws Exception {
        evidence("COMPLETED", "COMPLETED", "CAPTURE1");
        jdbc.update("UPDATE payments SET status='SUCCESS',transaction_id='CAPTURE1',paid_at='2026-11-02 10:00:00' WHERE order_id=?", secondOrder);

        MvcResult done = confirmPaid();

        assertEquals(200, done.getResponse().getStatus(), done.getResponse().getContentAsString());
        assertEquals("PAID", state());
        for (Map<String, Object> payment : groupPayments()) {
            assertEquals("SUCCESS", payment.get("status"));
            assertEquals("CAPTURE1", payment.get("transaction_id"));
            assertNotNull(payment.get("paid_at"));
        }
    }

    // ---- A missing local PayPal order ID is not proof that PayPal never created one ----

    private <T> T asBuyer(java.util.function.Supplier<T> action) {
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(buyer, "n/a", List.of()));
        try {
            return action.get();
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }
    }

    private String requestKey() {
        return jdbc.queryForObject("SELECT request_key FROM paypal_checkouts WHERE id=?", String.class, checkoutId);
    }

    private long paymentsWithStatus(String status) {
        return count("SELECT COUNT(*) FROM payments p JOIN orders o ON o.id=p.order_id WHERE o.checkout_group_id=? AND p.status=?", groupId, status);
    }

    @Test void createThatSucceededRemotelyWithoutALocalIdCannotBeAbandonedByAnAdmin() throws Exception {
        String key = requestKey();
        jdbc.update("UPDATE paypal_checkouts SET state='CREATING',paypal_order_id=NULL,approval_url=NULL WHERE id=?", checkoutId);
        // PayPal creates the order for this request ID; the response is lost, so the local transaction rolls back.
        AtomicInteger remoteCreates = new AtomicInteger();
        doAnswer(inv -> {
            remoteCreates.incrementAndGet();
            throw new IllegalStateException("PayPal create timed out");
        }).when(gateway).create(eq(checkoutId), any());

        assertThrows(IllegalStateException.class, () -> asBuyer(() -> checkoutService.sync(checkoutId, false)));
        assertEquals(1, remoteCreates.get());
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT state,paypal_order_id,paypal_create_attempted_at FROM paypal_checkouts WHERE id=?", checkoutId);
        assertEquals("CREATING", row.get("state"));
        assertNull(row.get("paypal_order_id"), "the PayPal order ID never reached the database");
        assertNotNull(row.get("paypal_create_attempted_at"), "the attempt marker was committed before PayPal was called");

        // PayPal's idempotency window passes: the request cannot be repeated safely, so the checkout goes to recovery.
        jdbc.update("UPDATE paypal_checkouts SET created_at=DATE_SUB(created_at,INTERVAL 6 HOUR),updated_at=updated_at WHERE id=?", checkoutId);
        assertEquals("RECOVERY_REQUIRED", asBuyer(() -> checkoutService.sync(checkoutId, false)).state());
        assertEquals(1, remoteCreates.get(), "an uncertain create is never sent again after the window");
        assertEquals(1, count("SELECT COUNT(*) FROM paypal_recovery_events WHERE checkout_id=? AND event_type='CREATION_UNCERTAIN' AND from_state='CREATING' AND to_state='RECOVERY_REQUIRED'", checkoutId));

        JsonNode detail = detail();
        assertEquals("CREATION_UNCERTAIN", detail.path("evidence").path("availability").asText());
        assertTrue(detail.path("allowedActions").isEmpty(), detail.toString());
        MvcResult refused = resolve(superAdmin, UUID.randomUUID().toString(), "ABANDON", "RECOVERY_REQUIRED",
                detail.path("evidenceFingerprint").asText(), "Missing PayPal order ID, assume never created");
        assertEquals(409, refused.getResponse().getStatus(), refused.getResponse().getContentAsString());

        // Nothing was cancelled or restocked on the strength of the missing ID, and the recovery data is intact.
        assertEquals(List.of("PENDING", "PENDING"), orderStatuses());
        assertEquals(5, stock(productA));
        assertEquals(7, stock(productB));
        assertEquals(2, paymentsWithStatus("PENDING"));
        assertEquals("RECOVERY_REQUIRED", state());
        assertEquals(key, requestKey());
        assertNull(jdbc.queryForObject("SELECT paypal_order_id FROM paypal_checkouts WHERE id=?", String.class, checkoutId));
        assertEquals(0, count("SELECT COUNT(*) FROM paypal_recovery_events WHERE checkout_id=? AND event_type LIKE 'RESOLVED%'", checkoutId));
        assertThrows(IllegalArgumentException.class, () -> asBuyer(() -> checkoutService.abandon(checkoutId)),
                "the buyer cannot cancel it either");
        assertEquals(List.of("PENDING", "PENDING"), orderStatuses());
    }

    @Test void checkoutThatNeverSentACreateRequestMayStillBeAbandonedByAnAdmin() throws Exception {
        String key = requestKey();
        // No PayPal order ID and no attempt marker: no create request ever left this backend.
        jdbc.update("UPDATE paypal_checkouts SET paypal_order_id=NULL,approval_url=NULL,paypal_create_attempted_at=NULL WHERE id=?", checkoutId);

        JsonNode detail = detail();
        assertEquals("NOT_CREATED", detail.path("evidence").path("availability").asText());
        assertEquals(List.of("ABANDON"), json.convertValue(detail.path("allowedActions"), List.class));
        MvcResult done = resolve(superAdmin, UUID.randomUUID().toString(), "ABANDON", "RECOVERY_REQUIRED",
                detail.path("evidenceFingerprint").asText(), "Never reached PayPal; release the stock");
        assertEquals(200, done.getResponse().getStatus(), done.getResponse().getContentAsString());

        assertEquals("ABANDONED", state());
        assertEquals(List.of("CANCELLED", "CANCELLED"), orderStatuses());
        assertEquals(8, stock(productA));
        assertEquals(8, stock(productB));
        assertEquals(key, requestKey());
        verify(gateway, never()).create(anyString(), any());
        verify(gateway, never()).get(anyString());
    }

    /**
     * Order #31's situation after the switch to Live: a Sandbox checkout in RECOVERY_REQUIRED with a
     * stored Sandbox order ID. The Live server never asks PayPal Live about it, permits no action on
     * the strength of that, ignores webhooks for its order ID and leaves it exactly as it was.
     */
    @Test void sandboxCheckoutUnderRecoveryIsNeverSentToLive() throws Exception {
        when(gateway.environment()).thenReturn("live");
        String before = state();
        String key = requestKey();

        JsonNode detail = detail();
        assertEquals("OTHER_ENVIRONMENT", detail.path("evidence").path("availability").asText());
        assertTrue(detail.path("evidence").path("detail").asText().contains("PayPal sandbox"));
        assertEquals(List.of(), json.convertValue(detail.path("allowedActions"), List.class));
        for (String action : List.of("ABANDON", "CONFIRM_PAID", "CONFIRM_REFUNDED")) {
            MvcResult refused = resolve(superAdmin, UUID.randomUUID().toString(), action, "RECOVERY_REQUIRED",
                    detail.path("evidenceFingerprint").asText(), "No Live evidence for a Sandbox checkout");
            assertEquals(409, refused.getResponse().getStatus(), action);
        }

        // A Live webhook carrying the same order ID matches no Live checkout: nothing is read or recorded.
        webhook("WH-LIVE-1", "PAYMENT.CAPTURE.COMPLETED");
        webhook("WH-LIVE-2", "CHECKOUT.ORDER.APPROVED");
        assertEquals(0L, count("SELECT COUNT(*) FROM paypal_webhook_events WHERE checkout_id=?", checkoutId));
        assertEquals(0L, count("SELECT COUNT(*) FROM paypal_review_cases WHERE checkout_id=?", checkoutId));

        assertEquals(before, state());
        assertEquals(List.of("PENDING", "PENDING"), orderStatuses());
        assertEquals(key, requestKey());
        verify(gateway, never()).get(anyString());
        verify(gateway, never()).capture(anyString(), anyString());
        verify(gateway, never()).create(anyString(), any());
        verify(gateway, never()).merchant();
    }

    private JsonNode config() throws Exception {
        MvcResult result = mockMvc.perform(as(get("/api/paypal/config"), buyer, "USER")).andReturn();
        assertEquals(200, result.getResponse().getStatus());
        return json.readTree(result.getResponse().getContentAsString());
    }

    /** The contract the Android app (e47ca41) checks before it uses PayPal. */
    @Test void configReportsLiveOnlyWhenLiveIsConfiguredAndReady() throws Exception {
        JsonNode sandbox = config();
        assertTrue(sandbox.path("enabled").asBoolean());
        assertTrue(sandbox.path("sandbox").asBoolean());
        assertEquals("sandbox", sandbox.path("environment").asText());
        assertEquals("EUR", sandbox.path("currency").asText());

        when(gateway.environment()).thenReturn("live");
        JsonNode live = config();
        assertTrue(live.path("enabled").asBoolean());
        assertFalse(live.path("sandbox").asBoolean());
        assertEquals("live", live.path("environment").asText());

        // Misconfigured (unknown setting, mixed credentials) or disabled: never reported as Live.
        when(gateway.environment()).thenReturn(null);
        JsonNode off = config();
        assertFalse(off.path("enabled").asBoolean());
        assertTrue(off.path("sandbox").asBoolean());
        assertEquals("sandbox", off.path("environment").asText());
    }

    @Test void checkoutAnswersCarryTheCheckoutsOwnEnvironment() throws Exception {
        MvcResult result = mockMvc.perform(as(get("/api/paypal/orders/" + firstOrder), buyer, "USER")).andReturn();
        assertEquals(200, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        JsonNode view = json.readTree(result.getResponse().getContentAsString());
        assertTrue(view.path("sandbox").asBoolean());
        assertEquals("sandbox", view.path("environment").asText());
        // Still described as Sandbox while the server runs Live: the app's Live build refuses it.
        when(gateway.environment()).thenReturn("live");
        view = json.readTree(mockMvc.perform(as(get("/api/paypal/orders/" + firstOrder), buyer, "USER")).andReturn().getResponse().getContentAsString());
        assertEquals("sandbox", view.path("environment").asText());
        assertTrue(view.path("sandbox").asBoolean());
    }

    @Test void returnPageNamesTheServersEnvironment() throws Exception {
        String sandbox = mockMvc.perform(get("/api/paypal/return").param("checkout", checkoutId)).andReturn().getResponse().getContentAsString();
        assertTrue(sandbox.contains("PayPal Sandbox"));
        assertTrue(sandbox.contains("omnia://paypal-return?checkout=" + checkoutId));
        when(gateway.environment()).thenReturn("live");
        String live = mockMvc.perform(get("/api/paypal/return").param("checkout", checkoutId)).andReturn().getResponse().getContentAsString();
        assertFalse(live.contains("Sandbox"), live);
        assertTrue(live.contains("PayPal</h2>"), live);
    }

    @Test void existingCheckoutsAreSandboxAndTheColumnRefusesUnknownEnvironments() {
        assertEquals("sandbox", jdbc.queryForObject("SELECT environment FROM paypal_checkouts WHERE id=?", String.class, checkoutId));
        assertThrows(org.springframework.dao.DataAccessException.class,
                () -> jdbc.update("UPDATE paypal_checkouts SET environment='production' WHERE id=?", checkoutId));
        assertEquals("sandbox", jdbc.queryForObject("SELECT environment FROM paypal_checkouts WHERE id=?", String.class, checkoutId));
    }

}
