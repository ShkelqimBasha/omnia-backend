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

    /**
     * The checkout's orders, row-locked by primary key in ID order.
     *
     * <p>A locking read through the non-unique {@code idx_orders_checkout_group_id} takes next-key
     * locks, including the gap above the newest group, so a concurrent checkout inserting its orders
     * there waits on us while holding the product locks we need next (a deadlock reproduced in
     * {@code PayPalLockOrderConcurrencyTest}). The IDs are therefore read without locks (orders never
     * change group after checkout) and locked by primary key, which takes record locks only. The
     * locked rows are re-checked for group membership and ownership before anything is written.
     */
    public List<GroupOrder> lockGroup(String checkoutId, long group) {
        em.flush();
        List<Long> owners = jdbc.queryForList("SELECT user_id FROM paypal_checkouts WHERE id=? AND checkout_group_id=?",
                Long.class, checkoutId, group);
        if (owners.size() != 1) throw new ConflictException("Checkout group mismatch; manual review required");
        List<Long> ids = jdbc.queryForList("SELECT id FROM orders WHERE checkout_group_id=? ORDER BY id", Long.class, group);
        if (ids.isEmpty()) throw new ConflictException("Checkout orders missing; manual review required");
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id,status,total_amount,checkout_group_id,user_id FROM orders WHERE id IN (" + marks(ids.size()) + ") ORDER BY id FOR UPDATE",
                ids.toArray());
        List<GroupOrder> orders = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Object rowGroup = row.get("checkout_group_id");
            Object rowOwner = row.get("user_id");
            if (rowGroup == null || ((Number) rowGroup).longValue() != group
                    || rowOwner == null || ((Number) rowOwner).longValue() != owners.get(0))
                throw new ConflictException("Checkout orders changed; manual review required");
            orders.add(new GroupOrder(((Number) row.get("id")).longValue(), (String) row.get("status"),
                    (java.math.BigDecimal) row.get("total_amount")));
        }
        if (orders.size() != ids.size()) throw new ConflictException("Checkout orders changed; manual review required");
        return orders;
    }

    /**
     * Shared lock on the checkout owner's users row. PayPal checkout creation and cancellation by
     * request key take this row FOR UPDATE before products and before the checkout row, so anything
     * that later references the owner through a foreign key must take it first, before the checkout
     * row, to keep one global order: users(owner) → paypal_checkouts → orders → users → products.
     */
    public void lockCheckoutOwner(String checkoutId) {
        List<Long> owners = jdbc.queryForList("SELECT user_id FROM paypal_checkouts WHERE id=?", Long.class, checkoutId);
        if (!owners.isEmpty()) lockUsers(owners);
    }

    /** Shared locks on users rows, in ID order. Unknown IDs are ignored. */
    public void lockUsers(Collection<Long> userIds) {
        List<Long> ids = new TreeSet<>(userIds.stream().filter(Objects::nonNull).toList()).stream().toList();
        if (ids.isEmpty()) return;
        jdbc.queryForList("SELECT id FROM users WHERE id IN (" + marks(ids.size()) + ") ORDER BY id FOR SHARE",
                Long.class, ids.toArray());
    }

    private static String marks(int count) {
        return String.join(",", Collections.nCopies(count, "?"));
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
        List<GroupOrder> orders = lockGroup(checkoutId, group);
        if (anyCancelled(orders) || !totalsMatch(orders, amount))
            throw new ConflictException("Checkout changed; manual review required");
        if (evidence == null || evidence.captureId() == null || !"PAID".equals(evidence.outcome()))
            throw new ConflictException("Verified capture evidence is required");
        // PayPalEvidence only reports PAID with a parseable capture create_time.
        java.sql.Timestamp paidAt = java.sql.Timestamp.from(java.time.Instant.parse(evidence.captureTime()));
        List<Long> pending = expectedPayPalPayments(orders, evidence.captureId());
        if (!pending.isEmpty()) {
            String marks = marks(pending.size());
            List<Object> args = new ArrayList<>(List.of(evidence.captureId(), paidAt));
            args.addAll(pending);
            int updated = jdbc.update("UPDATE payments SET status='SUCCESS',transaction_id=?,paid_at=COALESCE(paid_at,?) WHERE id IN (" + marks + ") AND status='PENDING' AND method='PAYPAL'",
                    args.toArray());
            // Defence in depth: the rows are locked, so a mismatch means the data changed underneath us.
            if (updated != pending.size())
                throw new ConflictException("PayPal payment records changed; manual review required");
        }
        jdbc.update("UPDATE paypal_checkouts SET state='PAID' WHERE id=?", checkoutId);
        return ledger.event(checkoutId, eventType, actor, fromState, "PAID", reason, evidence,
                "orders=" + ids(orders));
    }

    /**
     * Locks and validates the group's payment rows before anything is written. Every order must have
     * exactly one PAYPAL payment (payments.order_id is unique) that is either PENDING or already
     * SUCCESS with this same capture. Missing, extra, non-PayPal, FAILED, REFUNDED or differently
     * captured rows refuse the settlement; the caller's transaction then rolls back as a whole.
     * Returns the IDs of the PENDING rows to settle.
     */
    private List<Long> expectedPayPalPayments(List<GroupOrder> orders, String captureId) {
        String marks = marks(orders.size());
        // Unique-index lookups by order_id, in order-ID order: record locks only, after the order rows.
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id,order_id,method,status,transaction_id FROM payments WHERE order_id IN (" + marks + ") ORDER BY order_id FOR UPDATE",
                orders.stream().map(GroupOrder::id).toArray());
        Map<Long, Map<String, Object>> byOrder = new HashMap<>();
        for (Map<String, Object> row : rows) byOrder.put(((Number) row.get("order_id")).longValue(), row);
        List<Long> pending = new ArrayList<>();
        for (GroupOrder order : orders) {
            Map<String, Object> payment = byOrder.get(order.id());
            if (payment == null)
                throw new ConflictException("Order " + order.id() + " has no payment record; manual review required");
            if (!"PAYPAL".equals(payment.get("method")))
                throw new ConflictException("Order " + order.id() + " is not a PayPal payment; manual review required");
            String status = String.valueOf(payment.get("status"));
            if ("PENDING".equals(status)) {
                pending.add(((Number) payment.get("id")).longValue());
            } else if (!"SUCCESS".equals(status) || !captureId.equals(payment.get("transaction_id"))) {
                throw new ConflictException("Order " + order.id() + " payment is " + status
                        + " and does not match the verified capture; manual review required");
            }
        }
        return pending;
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
        List<GroupOrder> orders = lockGroup(checkoutId, group);
        if (shippedOrDelivered(orders))
            throw new ConflictException("Shipped or delivered orders need manual handling");
        if (anyCancelled(orders))
            throw new ConflictException("Checkout already partly cancelled; manual review required");

        List<Long> orderIds = orders.stream().map(GroupOrder::id).toList();
        // Every users row the inserts below reference by foreign key (status history and audit actor,
        // notification recipients) gets a shared lock now, before any product lock. Otherwise the
        // implicit FK lock is requested while holding products, and PayPal checkout creation, which
        // holds its buyer's users row and then waits for products, closes a cycle (reproduced in
        // PayPalLockOrderConcurrencyTest).
        lockUsers(referencedUsers(checkoutId, orderIds, actor));

        // Aggregate per product, then lock products in ID order: one stock increment per product.
        List<Map<String, Object>> lines = jdbc.queryForList(
                "SELECT product_id, SUM(quantity) AS quantity FROM order_items WHERE order_id IN (" + marks(orderIds.size()) + ") AND product_id IS NOT NULL GROUP BY product_id ORDER BY product_id",
                orderIds.toArray());
        List<Long> productIds = lines.stream().map(l -> ((Number) l.get("product_id")).longValue()).toList();
        if (!productIds.isEmpty()) {
            String marks = marks(productIds.size());
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

        // Driven by the locked primary keys, so no group-index gap lock is taken here either.
        int cancelled = jdbc.update("UPDATE orders SET status='CANCELLED' WHERE id IN (" + marks(orderIds.size()) + ") AND status NOT IN ('CANCELLED','SHIPPED','DELIVERED')",
                orderIds.toArray());
        if (cancelled != orders.size()) throw new ConflictException("Checkout orders changed; manual review required");
        for (GroupOrder order : orders) {
            jdbc.update("INSERT INTO order_status_history(order_id,from_status,to_status,changed_by_user_id) VALUES(?,?,'CANCELLED',?)",
                    order.id(), order.status(), actor);
        }
        String capture = evidence == null ? null : evidence.captureId();
        List<Object> paymentArgs = new ArrayList<>(Arrays.asList(paymentStatus, "REFUNDED".equals(paymentStatus) ? capture : null));
        paymentArgs.addAll(orderIds);
        jdbc.update("UPDATE payments SET status=?,transaction_id=COALESCE(transaction_id,?) WHERE order_id IN (" + marks(orderIds.size()) + ") AND method='PAYPAL'",
                paymentArgs.toArray());
        jdbc.update("UPDATE paypal_checkouts SET state=? WHERE id=?", toState, checkoutId);
        long event = ledger.event(checkoutId, eventType, actor, fromState, toState, reason, evidence,
                "orders=" + ids(orders) + "; stock=" + restored);
        notifyCancelled(orders);
        return event;
    }

    /** The checkout owner, the actor and every recipient NotificationWriter will write for these orders. */
    private Set<Long> referencedUsers(String checkoutId, List<Long> orderIds, Long actor) {
        Long owner = jdbc.queryForObject("SELECT user_id FROM paypal_checkouts WHERE id=?", Long.class, checkoutId);
        Set<Long> users = new TreeSet<>();
        users.add(owner);
        if (actor != null) users.add(actor);
        List<Long> organizations = jdbc.query("SELECT DISTINCT organization_id FROM orders WHERE id IN (" + marks(orderIds.size()) + ")",
                (row, n) -> row.getObject(1) == null ? null : row.getLong(1), orderIds.toArray());
        for (Long organization : organizations) users.addAll(notifications.orderRecipients(organization, owner));
        return users;
    }

    /** Shipped or delivered orders: keep everything, preserve the evidence and alert SUPER_ADMIN. */
    public long flagManual(String checkoutId, long group, String state, BigDecimal amount,
                           PayPalRecoveryEvidence.Snapshot evidence, Long actor, String reason) {
        List<GroupOrder> orders = lockGroup(checkoutId, group);
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
