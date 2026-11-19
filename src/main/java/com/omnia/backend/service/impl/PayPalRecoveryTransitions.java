package com.omnia.backend.service.impl;

import com.omnia.backend.common.exception.ConflictException;
import com.omnia.backend.entity.Order;
import com.omnia.backend.event.OrderStatusChangedEvent;
import jakarta.persistence.EntityManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Checkout-group transitions used by recovery and webhooks. Callers must already hold the
 * paypal_checkouts row lock. Lock order everywhere is checkout row, then the group's orders by
 * ID, then products by ID, matching PayPalCheckoutService and PayPalOrderGuard.
 *
 * <p>The existing per-order cancellation (OrderServiceImpl.cancelMyOrder) is deliberately not
 * reused: it requires the buyer as current user, locks products in item order per order (so two
 * orders of one group can deadlock with another writer), restores stock order by order, leaves
 * the PayPal payment rows untouched and writes no PayPal audit. This class cancels the whole
 * group in one statement set and restores each product once from the aggregated quantities.
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class PayPalRecoveryTransitions {
    private final JdbcTemplate jdbc;
    private final EntityManager em;
    private final NotificationWriter notifications;
    private final ApplicationEventPublisher events;
    private final PayPalRecoveryLedger ledger;

    public PayPalRecoveryTransitions(JdbcTemplate jdbc, EntityManager em, NotificationWriter notifications,
                                     ApplicationEventPublisher events, PayPalRecoveryLedger ledger) {
        this.jdbc = jdbc;
        this.em = em;
        this.notifications = notifications;
        this.events = events;
        this.ledger = ledger;
    }

    public record GroupOrder(long id, String status, BigDecimal total) {}

    /** The group's orders, row-locked in ID order. */
    public List<GroupOrder> lockGroup(long group) {
        em.flush();
        List<GroupOrder> orders = jdbc.query("SELECT id,status,total_amount FROM orders WHERE checkout_group_id=? ORDER BY id FOR UPDATE",
                (row, n) -> new GroupOrder(row.getLong(1), row.getString(2), row.getBigDecimal(3)), group);
        if (orders.isEmpty()) throw new ConflictException("Checkout orders missing; manual review required");
        return orders;
    }

    public static boolean shippedOrDelivered(List<GroupOrder> orders) {
        return orders.stream().anyMatch(o -> "SHIPPED".equals(o.status()) || "DELIVERED".equals(o.status()));
    }

    public static boolean anyCancelled(List<GroupOrder> orders) {
        return orders.stream().anyMatch(o -> "CANCELLED".equals(o.status()));
    }

    public static boolean totalsMatch(List<GroupOrder> orders, BigDecimal amount) {
        BigDecimal sum = orders.stream().map(GroupOrder::total).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        return amount != null && sum.compareTo(amount) == 0;
    }

    /** Record verified settlement: every PayPal payment of the group becomes SUCCESS with the capture. */
    public long settlePaid(String checkoutId, long group, BigDecimal amount, String fromState,
                           PayPalRecoveryEvidence.Snapshot evidence, Long actor, String reason, String eventType) {
        List<GroupOrder> orders = lockGroup(group);
        if (anyCancelled(orders) || !totalsMatch(orders, amount))
            throw new ConflictException("Checkout changed; manual review required");
        if (evidence == null || evidence.captureId() == null || !"PAID".equals(evidence.outcome()))
            throw new ConflictException("Verified capture evidence is required");
        // PayPalEvidence only reports PAID with a parseable capture create_time.
        java.sql.Timestamp paidAt = java.sql.Timestamp.from(java.time.Instant.parse(evidence.captureTime()));
        jdbc.update("UPDATE payments p JOIN orders o ON o.id=p.order_id SET p.status='SUCCESS',p.transaction_id=?,p.paid_at=COALESCE(p.paid_at,?) WHERE o.checkout_group_id=? AND p.method='PAYPAL' AND p.status='PENDING'",
                evidence.captureId(), paidAt, group);
        jdbc.update("UPDATE paypal_checkouts SET state='PAID' WHERE id=?", checkoutId);
        return ledger.event(checkoutId, eventType, actor, fromState, "PAID", reason, evidence,
                "orders=" + ids(orders));
    }

    /**
     * Cancel every order in the group, restore stock exactly once, update the PayPal payments and
     * the checkout state, and audit it, all in the caller's transaction. Shipped or delivered
     * orders refuse the whole transition; nothing is changed.
     */
    public long cancelGroup(String checkoutId, long group, String fromState, String toState, String paymentStatus,
                            PayPalRecoveryEvidence.Snapshot evidence, Long actor, String reason, String eventType) {
        if (!Set.of("ABANDONED", "REFUNDED").contains(toState) || !Set.of("FAILED", "REFUNDED").contains(paymentStatus))
            throw new IllegalArgumentException("Unsupported cancellation target");
        List<GroupOrder> orders = lockGroup(group);
        if (shippedOrDelivered(orders))
            throw new ConflictException("Shipped or delivered orders need manual handling");
        if (anyCancelled(orders))
            throw new ConflictException("Checkout already partly cancelled; manual review required");

        // Aggregate per product, then lock products in ID order: one stock increment per product.
        List<Map<String, Object>> lines = jdbc.queryForList(
                "SELECT oi.product_id AS product_id, SUM(oi.quantity) AS quantity FROM order_items oi JOIN orders o ON o.id=oi.order_id WHERE o.checkout_group_id=? AND oi.product_id IS NOT NULL GROUP BY oi.product_id ORDER BY oi.product_id",
                group);
        List<Long> productIds = lines.stream().map(l -> ((Number) l.get("product_id")).longValue()).toList();
        if (!productIds.isEmpty()) {
            String marks = productIds.stream().map(id -> "?").collect(Collectors.joining(","));
            jdbc.queryForList("SELECT id FROM products WHERE id IN (" + marks + ") ORDER BY id FOR UPDATE",
                    Long.class, productIds.toArray());
        }
        List<String> restored = new ArrayList<>();
        for (Map<String, Object> line : lines) {
            long product = ((Number) line.get("product_id")).longValue();
            int quantity = ((Number) line.get("quantity")).intValue();
            // order_items.product_id references products, so the row exists and is locked above.
            jdbc.update("UPDATE products SET stock=COALESCE(stock,0)+? WHERE id=?", quantity, product);
            restored.add(product + "+" + quantity);
        }

        int cancelled = jdbc.update("UPDATE orders SET status='CANCELLED' WHERE checkout_group_id=? AND status NOT IN ('CANCELLED','SHIPPED','DELIVERED')", group);
        if (cancelled != orders.size()) throw new ConflictException("Checkout orders changed; manual review required");
        for (GroupOrder order : orders) {
            jdbc.update("INSERT INTO order_status_history(order_id,from_status,to_status,changed_by_user_id) VALUES(?,?,'CANCELLED',?)",
                    order.id(), order.status(), actor);
        }
        String capture = evidence == null ? null : evidence.captureId();
        jdbc.update("UPDATE payments p JOIN orders o ON o.id=p.order_id SET p.status=?,p.transaction_id=COALESCE(p.transaction_id,?) WHERE o.checkout_group_id=? AND p.method='PAYPAL'",
                paymentStatus, "REFUNDED".equals(paymentStatus) ? capture : null, group);
        jdbc.update("UPDATE paypal_checkouts SET state=? WHERE id=?", toState, checkoutId);
        long event = ledger.event(checkoutId, eventType, actor, fromState, toState, reason, evidence,
                "orders=" + ids(orders) + "; stock=" + restored);
        notifyCancelled(orders);
        return event;
    }

    /** Shipped or delivered orders: keep everything, preserve the evidence and alert SUPER_ADMIN. */
    public long flagManual(String checkoutId, long group, String state, BigDecimal amount,
                           PayPalRecoveryEvidence.Snapshot evidence, Long actor, String reason) {
        List<GroupOrder> orders = lockGroup(group);
        String captureKey = evidence == null || evidence.captureId() == null ? "" : evidence.captureId();
        if (ledger.openCase(checkoutId, "SHIPPED_ORDERS", captureKey, evidence, "orders=" + statuses(orders)))
            ledger.alertSuperAdmins(checkoutId, "SHIPPED_ORDERS", captureKey, amount);
        return ledger.event(checkoutId, "FLAGGED_MANUAL", actor, state, state, reason, evidence, "orders=" + statuses(orders));
    }

    /** Inbox and push rows join this transaction (outbox); e-mails are AFTER_COMMIT listeners. */
    private void notifyCancelled(List<GroupOrder> orders) {
        for (GroupOrder row : orders) {
            Order order = em.find(Order.class, row.id());   // locked above, so it exists
            em.refresh(order);
            notifications.order(order);
            String email = order.getShippingEmail();
            if ((email == null || email.isBlank()) && order.getUser() != null) email = order.getUser().getEmail();
            if (email == null || email.isBlank()) continue;
            String name = order.getShippingName();
            if ((name == null || name.isBlank()) && order.getUser() != null) name = order.getUser().getUsername();
            events.publishEvent(new OrderStatusChangedEvent(email.trim(), name, order.getId(), order.getStatus(), order.getTotalAmount()));
        }
    }

    private static String ids(List<GroupOrder> orders) {
        return orders.stream().map(o -> String.valueOf(o.id())).collect(Collectors.joining(","));
    }

    private static String statuses(List<GroupOrder> orders) {
        return orders.stream().map(o -> o.id() + ":" + o.status()).collect(Collectors.joining(","));
    }
}
