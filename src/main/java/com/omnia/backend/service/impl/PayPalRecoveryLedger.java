package com.omnia.backend.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.*;

/**
 * Append-only audit, review cases, scoped idempotency records and webhook de-duplication for
 * PayPal recovery. Every write joins the caller's transaction, so audit rows commit or roll back
 * together with the state change they describe.
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
public class PayPalRecoveryLedger {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public PayPalRecoveryLedger(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public record Stored(String requestHash, long actorUserId, String response) {}

    public long event(String checkoutId, String type, Long actor, String from, String to, String reason,
                      PayPalRecoveryEvidence.Snapshot evidence, String details) {
        String evidenceJson = evidence == null ? null : write(evidence);
        String fingerprint = evidence == null ? null : evidence.fingerprint();
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO paypal_recovery_events(checkout_id,event_type,actor_user_id,from_state,to_state,reason,evidence,evidence_fingerprint,details) VALUES(?,?,?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, checkoutId);
            statement.setString(2, type);
            if (actor == null) statement.setNull(3, java.sql.Types.BIGINT); else statement.setLong(3, actor);
            statement.setString(4, from);
            statement.setString(5, to);
            statement.setString(6, clip(reason, 1000));
            statement.setString(7, evidenceJson);
            statement.setString(8, fingerprint);
            statement.setString(9, clip(details, 2000));
            return statement;
        }, keys);
        return keys.getKey() == null ? 0 : keys.getKey().longValue();
    }

    /**
     * Opens a review case once per checkout, kind and capture. Returns false for a repeated report.
     * Callers hold the paypal_checkouts row lock, so check-then-insert cannot race; the unique key
     * still backs it. (Connector/J reports found rows, so ON DUPLICATE KEY cannot signal a repeat.)
     */
    public boolean openCase(String checkoutId, String kind, String captureKey, PayPalRecoveryEvidence.Snapshot evidence,
                            String details) {
        String capture = captureKey == null ? "" : captureKey;
        Integer existing = jdbc.queryForObject("SELECT COUNT(*) FROM paypal_review_cases WHERE checkout_id=? AND kind=? AND capture_key=?",
                Integer.class, checkoutId, kind, capture);
        if (existing != null && existing > 0) return false;
        jdbc.update("INSERT INTO paypal_review_cases(checkout_id,kind,capture_key,evidence,details) VALUES(?,?,?,?,?)",
                checkoutId, kind, capture, evidence == null ? null : write(evidence), clip(details, 2000));
        return true;
    }

    /** A review case as stored. {@code showsMoney} is true when the case records a capture. */
    public record ReviewCase(long id, String checkoutId, String kind, String captureKey, String status) {
        public boolean showsMoney() {
            if ("RECOVERY_EVIDENCE".equals(kind)) return !captureKey.endsWith(":");
            return !captureKey.isEmpty();
        }
    }

    public Optional<String> caseCheckout(long caseId) {
        return jdbc.queryForList("SELECT checkout_id FROM paypal_review_cases WHERE id=?", String.class, caseId)
                .stream().findFirst();
    }

    /** Call with the checkout row already locked: checkout row, then case row. */
    public Optional<ReviewCase> lockCase(long caseId) {
        return jdbc.query("SELECT id,checkout_id,kind,capture_key,status FROM paypal_review_cases WHERE id=? FOR UPDATE",
                (row, n) -> new ReviewCase(row.getLong(1), row.getString(2), row.getString(3), row.getString(4), row.getString(5)),
                caseId).stream().findFirst();
    }

    /** Close one open case. Evidence is left exactly as recorded. */
    public void closeCase(long caseId, long actor, String outcome, String reason) {
        int closed = jdbc.update("UPDATE paypal_review_cases SET status='CLOSED',closed_by_user_id=?,closed_at=UTC_TIMESTAMP(6),closure_outcome=?,closure_reason=? WHERE id=? AND status='OPEN'",
                actor, outcome, clip(reason, 1000), caseId);
        if (closed != 1) throw new IllegalStateException("Review case was not open");
    }

    /** Close every open case of the given kinds for a checkout; returns their IDs in order. */
    public List<Long> closeOpenCases(String checkoutId, Set<String> kinds, long actor, String outcome, String reason) {
        List<Long> ids = new ArrayList<>();
        for (String kind : new TreeSet<>(kinds)) {
            ids.addAll(jdbc.queryForList("SELECT id FROM paypal_review_cases WHERE checkout_id=? AND kind=? AND status='OPEN' ORDER BY id FOR UPDATE",
                    Long.class, checkoutId, kind));
        }
        Collections.sort(ids);
        for (long id : ids) closeCase(id, actor, outcome, reason);
        return ids;
    }

    public Optional<Stored> request(String checkoutId, String operation, String key) {
        return jdbc.query(
                "SELECT request_hash,actor_user_id,CAST(response AS CHAR) AS response FROM paypal_recovery_requests WHERE checkout_id=? AND operation=? AND idempotency_key=?",
                (row, n) -> new Stored(row.getString(1), row.getLong(2), row.getString(3)),
                checkoutId, operation, key).stream().findFirst();
    }

    public void storeRequest(String checkoutId, String operation, String key, String hash, long actor, Object response) {
        jdbc.update("INSERT INTO paypal_recovery_requests(checkout_id,operation,idempotency_key,request_hash,actor_user_id,response) VALUES(?,?,?,?,?,?)",
                checkoutId, operation, key, hash, actor, write(response));
    }

    public boolean webhookSeen(String eventId, String checkoutId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM paypal_webhook_events WHERE event_id=? AND checkout_id=?",
                Integer.class, eventId, checkoutId);
        return count != null && count > 0;
    }

    public void webhookRecord(String eventId, String checkoutId, String type, String outcome) {
        jdbc.update("INSERT INTO paypal_webhook_events(event_id,checkout_id,event_type,outcome) VALUES(?,?,?,?)",
                eventId, checkoutId, clip(type, 64), clip(outcome, 32));
    }

    public List<Map<String, Object>> events(String checkoutId) {
        return jdbc.queryForList("SELECT id,event_type,actor_user_id,from_state,to_state,reason,CAST(evidence AS CHAR) AS evidence,evidence_fingerprint,details,created_at FROM paypal_recovery_events WHERE checkout_id=? ORDER BY id",
                checkoutId);
    }

    public List<Map<String, Object>> cases(String checkoutId) {
        return jdbc.queryForList("SELECT id,kind,capture_key,status,CAST(evidence AS CHAR) AS evidence,details,created_at,closed_by_user_id,closed_at,closure_outcome,closure_reason FROM paypal_review_cases WHERE checkout_id=? ORDER BY id",
                checkoutId);
    }

    /**
     * Inbox alert (and push via the outbox) for every active SUPER_ADMIN. The outbox is only
     * read after this transaction commits, so nothing is delivered for a rolled-back change.
     */
    public void alertSuperAdmins(String checkoutId, String kind, String captureKey, BigDecimal amount) {
        Long order = jdbc.queryForObject("SELECT MIN(o.id) FROM orders o JOIN paypal_checkouts p ON p.checkout_group_id=o.checkout_group_id WHERE p.id=?",
                Long.class, checkoutId);
        if (order == null) return;
        String key = clip("paypal-review:" + checkoutId + ":" + kind + ":" + (captureKey == null ? "" : captureKey), 180);
        List<Long> admins = jdbc.queryForList("SELECT u.id FROM users u JOIN roles r ON r.id=u.role_id WHERE r.name='SUPER_ADMIN' AND u.status='ACTIVE' AND u.deleted_at IS NULL",
                Long.class);
        for (long admin : admins) {
            jdbc.update("INSERT INTO notification_inbox (recipient_id,event_key,kind,category,target_type,target_id,organization_id,label,status,amount,quantity,created_at) VALUES (?,?,'PAYPAL_REVIEW','SYSTEM','ORDER',?,NULL,?,?,?,NULL,UTC_TIMESTAMP(6)) ON DUPLICATE KEY UPDATE id=id",
                    admin, key, order, clip("PayPal review: " + kind, 200), clip(kind, 30), amount);
            jdbc.update("INSERT INTO push_outbox(notification_id,available_at) SELECT id,UTC_TIMESTAMP(6) FROM notification_inbox WHERE recipient_id=? AND event_key=? ON DUPLICATE KEY UPDATE notification_id=notification_id",
                    admin, key);
        }
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not record PayPal recovery data", ex);
        }
    }

    private static String clip(String value, int max) {
        return value == null ? null : value.length() <= max ? value : value.substring(0, max);
    }
}
