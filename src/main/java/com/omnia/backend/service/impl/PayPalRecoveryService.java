package com.omnia.backend.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnia.backend.common.exception.ConflictException;
import com.omnia.backend.common.exception.ResourceNotFoundException;
import com.omnia.backend.entity.User;
import com.omnia.backend.enums.UserStatus;
import com.omnia.backend.security.service.CurrentUserService;
import com.omnia.backend.service.impl.PayPalRecoveryEvidence.Action;
import com.omnia.backend.service.impl.PayPalRecoveryEvidence.Snapshot;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

/**
 * SUPER_ADMIN resolution of PayPal checkouts in RECOVERY_REQUIRED. The server, not the admin,
 * decides what the evidence permits; the admin picks one of those actions, quoting the state and
 * evidence fingerprint they reviewed so a stale view is refused.
 */
@Service
public class PayPalRecoveryService {
    static final String RESOLVE = "RESOLVE";
    private final JdbcTemplate jdbc;
    private final CurrentUserService current;
    private final PayPalGateway gateway;
    private final PayPalRecoveryLedger ledger;
    private final PayPalRecoveryTransitions transitions;
    private final ObjectMapper json;
    private final TransactionTemplate tx;

    public PayPalRecoveryService(JdbcTemplate jdbc, CurrentUserService current, PayPalGateway gateway,
                                 PayPalRecoveryLedger ledger, PayPalRecoveryTransitions transitions, ObjectMapper json,
                                 PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        this.current = current;
        this.gateway = gateway;
        this.ledger = ledger;
        this.transitions = transitions;
        this.json = json;
        this.tx = new TransactionTemplate(manager);
    }

    public record OrderLine(long id, String status, BigDecimal total) {}
    public record Summary(String checkoutId, String state, BigDecimal amount, Instant createdAt, List<Long> orderIds,
                          long openCases) {}
    public record Detail(String checkoutId, String state, String currency, BigDecimal amount, String providerOrderId,
                         Instant createdAt, List<OrderLine> orders, Snapshot evidence, String evidenceFingerprint,
                         List<String> allowedActions, List<Map<String, Object>> history,
                         List<Map<String, Object>> reviewCases) {}
    public record ResolveRequest(String action, String expectedState, String evidenceFingerprint, String reason) {}
    public record ResolveResult(String checkoutId, String action, String fromState, String state,
                                String evidenceFingerprint, List<Long> orderIds, long auditEventId,
                                List<Long> closedCaseIds) {}

    /**
     * Documented outcome when a SUPER_ADMIN closes a review case that was handled outside this API.
     * <ul>
     *   <li>REFUNDED_EXTERNALLY: the money was refunded in the PayPal dashboard.</li>
     *   <li>ACCEPTED_AS_PAID: the capture is kept and the goods were or will be fulfilled.</li>
     *   <li>CONFIRMED_NO_PAYMENT: PayPal confirmed no money moved. Refused for cases that record a
     *       capture, because a verified capture is never overruled by an assertion.</li>
     *   <li>NO_ACTION_REQUIRED: reviewed; nothing to change (for example a duplicate report).</li>
     * </ul>
     */
    public enum CaseOutcome { REFUNDED_EXTERNALLY, ACCEPTED_AS_PAID, CONFIRMED_NO_PAYMENT, NO_ACTION_REQUIRED }
    public record CloseRequest(String outcome, String reason) {}
    public record CloseResult(long caseId, String checkoutId, String kind, String status, String outcome,
                              long auditEventId) {}

    /** Resolution consumes the evidence these cases recorded, so a successful resolution closes them. */
    static final Set<String> CLOSED_BY_RESOLUTION = Set.of("RECOVERY_EVIDENCE");

    /** {@code createAttempted}: a PayPal create request may have been sent (marker committed before each request). */
    private record Row(String id, long group, BigDecimal amount, String provider, String state, Instant created,
                       boolean createAttempted, String environment) {}

    public List<Summary> list() {
        requireSuperAdmin();
        return jdbc.query("SELECT p.id,p.state,p.amount,p.created_at,p.checkout_group_id,(SELECT COUNT(*) FROM paypal_review_cases c WHERE c.checkout_id=p.id AND c.status='OPEN') AS open_cases FROM paypal_checkouts p WHERE p.state='RECOVERY_REQUIRED' OR EXISTS (SELECT 1 FROM paypal_review_cases c WHERE c.checkout_id=p.id AND c.status='OPEN') ORDER BY p.created_at",
                (r, n) -> new Summary(r.getString("id"), r.getString("state"), r.getBigDecimal("amount"),
                        r.getTimestamp("created_at").toInstant(), orderIds(r.getLong("checkout_group_id")),
                        r.getLong("open_cases")));
    }

    public Detail detailForOrder(long orderId) {
        requireSuperAdmin();
        List<String> ids = jdbc.queryForList("SELECT p.id FROM paypal_checkouts p JOIN orders o ON o.checkout_group_id=p.checkout_group_id WHERE o.id=?",
                String.class, orderId);
        if (ids.isEmpty()) throw new ResourceNotFoundException("No PayPal checkout for this order");
        return detail(ids.get(0));
    }

    /** Reads PayPal outside any database lock; resolve() fetches again under the lock. */
    public Detail detail(String checkoutId) {
        requireSuperAdmin();
        uuid(checkoutId);
        Row row = find(checkoutId, false);
        Snapshot evidence = PayPalRecoveryEvidence.fetch(gateway, row.environment(), row.provider(), row.createAttempted(), row.id(), row.amount());
        List<OrderLine> orders = jdbc.query("SELECT id,status,total_amount FROM orders WHERE checkout_group_id=? ORDER BY id",
                (r, n) -> new OrderLine(r.getLong(1), r.getString(2), r.getBigDecimal(3)), row.group());
        List<PayPalRecoveryTransitions.GroupOrder> group = orders.stream()
                .map(o -> new PayPalRecoveryTransitions.GroupOrder(o.id(), o.status(), o.total())).toList();
        List<String> allowed = "RECOVERY_REQUIRED".equals(row.state())
                ? permitted(evidence, group, row.amount()).stream().map(Enum::name).sorted().toList()
                : List.of();
        return tx.execute(s -> new Detail(row.id(), row.state(), "EUR", row.amount(), row.provider(), row.created(),
                orders, evidence, evidence.fingerprint(), allowed, ledger.events(row.id()), ledger.cases(row.id())));
    }

    public ResolveResult resolve(String checkoutId, String idempotencyKey, ResolveRequest request) {
        User actor = requireSuperAdmin();
        uuid(checkoutId);
        String key = idempotencyKey(idempotencyKey);
        Action action = parseAction(request == null ? null : request.action());
        String reason = reason(request.reason());
        if (request.expectedState() == null || request.evidenceFingerprint() == null
                || !request.evidenceFingerprint().matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("Expected state and evidence fingerprint are required");
        String hash = sha256(action.name() + "\n" + request.expectedState() + "\n" + request.evidenceFingerprint() + "\n" + reason);

        return tx.execute(status -> {
            // Global lock order: users(owner) → paypal_checkouts → orders (by primary key) → users → products.
            // The buyer's checkout creation and cancel-by-key hold their users row first; taking it here,
            // before the checkout row, keeps a resolution from closing a cycle with them.
            transitions.lockCheckoutOwner(checkoutId);
            Row row = find(checkoutId, true);
            // Keys are scoped to (checkout, operation). A replay returns the original answer.
            Optional<ResolveResult> replay = replay(row.id(), RESOLVE, key, hash, actor, ResolveResult.class);
            if (replay.isPresent()) return replay.get();
            if (!row.state().equals(request.expectedState()))
                throw new ConflictException("Checkout state changed to " + row.state() + "; review it again");
            if (!"RECOVERY_REQUIRED".equals(row.state()))
                throw new ConflictException("Only RECOVERY_REQUIRED checkouts can be resolved here");

            // Fresh provider evidence under the row lock: webhooks for this checkout wait for us.
            Snapshot evidence = PayPalRecoveryEvidence.fetch(gateway, row.environment(), row.provider(), row.createAttempted(), row.id(), row.amount());
            if (!evidence.fingerprint().equals(request.evidenceFingerprint()))
                throw new ConflictException("PayPal evidence changed; review it again");
            List<PayPalRecoveryTransitions.GroupOrder> orders = transitions.lockGroup(row.id(), row.group());
            if (!permitted(evidence, orders, row.amount()).contains(action))
                throw new ConflictException("The current evidence does not permit " + action);

            long event;
            String next;
            switch (action) {
                case CONFIRM_PAID -> {
                    event = transitions.settlePaid(row.id(), row.group(), row.amount(), row.state(), evidence,
                            actor.getId(), reason, "RESOLVED_PAID");
                    next = "PAID";
                }
                case CONFIRM_REFUNDED -> {
                    event = transitions.cancelGroup(row.id(), row.group(), row.state(), "REFUNDED", "REFUNDED",
                            evidence, actor.getId(), reason, "RESOLVED_REFUNDED");
                    next = "REFUNDED";
                }
                case ABANDON -> {
                    event = transitions.cancelGroup(row.id(), row.group(), row.state(), "ABANDONED", "FAILED",
                            evidence, actor.getId(), reason, "RESOLVED_ABANDONED");
                    next = "ABANDONED";
                }
                default -> {
                    event = transitions.flagManual(row.id(), row.group(), row.state(), row.amount(), evidence,
                            actor.getId(), reason);
                    next = row.state();
                }
            }
            List<Long> closed = List.of();
            if (action != Action.FLAG_MANUAL) {
                String outcome = "RESOLVED_" + action.name();
                closed = ledger.closeOpenCases(row.id(), CLOSED_BY_RESOLUTION, actor.getId(), outcome, reason);
                if (!closed.isEmpty())
                    ledger.event(row.id(), "CASES_CLOSED", actor.getId(), next, next, reason, null,
                            "cases=" + closed + "; outcome=" + outcome);
            }
            ResolveResult result = new ResolveResult(row.id(), action.name(), row.state(), next,
                    evidence.fingerprint(), orderIds(row.group()), event, closed);
            ledger.storeRequest(row.id(), RESOLVE, key, hash, actor.getId(), result);
            return result;
        });
    }

    /**
     * Close a review case handled outside this API. The checkout, orders, payments and the case's
     * recorded evidence are unchanged; the closure and its documented outcome are audited.
     */
    public CloseResult closeCase(long caseId, String idempotencyKey, CloseRequest request) {
        User actor = requireSuperAdmin();
        String key = idempotencyKey(idempotencyKey);
        CaseOutcome outcome = parseOutcome(request == null ? null : request.outcome());
        String reason = reason(request.reason());
        String hash = sha256(caseId + "\n" + outcome.name() + "\n" + reason);
        String operation = "CLOSE_CASE:" + caseId;
        return tx.execute(status -> {
            String checkoutId = ledger.caseCheckout(caseId)
                    .orElseThrow(() -> new ResourceNotFoundException("Review case not found"));
            Row row = find(checkoutId, true);                 // checkout row, then the case row
            PayPalRecoveryLedger.ReviewCase reviewCase = ledger.lockCase(caseId)
                    .orElseThrow(() -> new ResourceNotFoundException("Review case not found"));
            Optional<CloseResult> replay = replay(row.id(), operation, key, hash, actor, CloseResult.class);
            if (replay.isPresent()) return replay.get();
            if (!"OPEN".equals(reviewCase.status()))
                throw new ConflictException("Review case is already closed");
            if (outcome == CaseOutcome.CONFIRMED_NO_PAYMENT && reviewCase.showsMoney())
                throw new ConflictException("This case records a verified capture; it cannot be closed as no payment");
            ledger.closeCase(caseId, actor.getId(), outcome.name(), reason);
            long event = ledger.event(row.id(), "CASE_CLOSED", actor.getId(), row.state(), row.state(), reason, null,
                    "case=" + caseId + "; kind=" + reviewCase.kind() + "; outcome=" + outcome.name());
            CloseResult result = new CloseResult(caseId, row.id(), reviewCase.kind(), "CLOSED", outcome.name(), event);
            ledger.storeRequest(row.id(), operation, key, hash, actor.getId(), result);
            return result;
        });
    }

    /** Keys are scoped to (checkout, operation); the same key may only repeat the same request. */
    private <T> Optional<T> replay(String checkoutId, String operation, String key, String hash, User actor, Class<T> type) {
        Optional<PayPalRecoveryLedger.Stored> stored = ledger.request(checkoutId, operation, key);
        if (stored.isEmpty()) return Optional.empty();
        if (!stored.get().requestHash().equals(hash) || stored.get().actorUserId() != actor.getId())
            throw new ConflictException("Idempotency key already used for a different request");
        return Optional.of(read(stored.get().response(), type));
    }

    private static String idempotencyKey(String value) {
        String key = value == null ? null : value.trim().toLowerCase(Locale.ROOT);
        uuid(key);
        return key;
    }

    private static String reason(String value) {
        String reason = value == null ? "" : value.trim();
        if (reason.length() < 10 || reason.length() > 1000)
            throw new IllegalArgumentException("A reason of 10 to 1000 characters is required");
        return reason;
    }

    private static CaseOutcome parseOutcome(String value) {
        try {
            return CaseOutcome.valueOf(value);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Unknown case outcome");
        }
    }

    static Set<Action> permitted(Snapshot evidence, List<PayPalRecoveryTransitions.GroupOrder> orders, BigDecimal amount) {
        return PayPalRecoveryEvidence.allowed(evidence, PayPalRecoveryTransitions.shippedOrDelivered(orders),
                PayPalRecoveryTransitions.anyCancelled(orders), PayPalRecoveryTransitions.totalsMatch(orders, amount));
    }

    /** Role and status come from the database, not only from the token's authorities. */
    private User requireSuperAdmin() {
        User user = current.requireCurrentUser();
        if (user.getStatus() != UserStatus.ACTIVE || user.getRole() == null
                || !"SUPER_ADMIN".equals(user.getRole().getName()))
            throw new AccessDeniedException("SUPER_ADMIN required");
        return user;
    }

    private Row find(String id, boolean lock) {
        List<Row> rows = jdbc.query("SELECT id,checkout_group_id,amount,paypal_order_id,state,created_at,paypal_create_attempted_at,environment FROM paypal_checkouts WHERE id=?" + (lock ? " FOR UPDATE" : ""),
                (r, n) -> new Row(r.getString(1), r.getLong(2), r.getBigDecimal(3), r.getString(4), r.getString(5),
                        r.getTimestamp(6).toInstant(), r.getTimestamp(7) != null, r.getString(8)), id);
        if (rows.isEmpty()) throw new ResourceNotFoundException("PayPal checkout not found");
        return rows.get(0);
    }

    private List<Long> orderIds(long group) {
        return jdbc.queryForList("SELECT id FROM orders WHERE checkout_group_id=? ORDER BY id", Long.class, group);
    }

    private static Action parseAction(String value) {
        try {
            return Action.valueOf(value);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Unknown recovery action");
        }
    }

    private <T> T read(String response, Class<T> type) {
        try {
            return json.readValue(response, type);
        } catch (Exception ex) {
            throw new IllegalStateException("Stored recovery response unreadable", ex);
        }
    }

    private static void uuid(String id) {
        if (id == null || !id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
            throw new IllegalArgumentException("Invalid identifier");
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
