package com.omnia.backend.service.impl;

import com.omnia.backend.common.exception.ConflictException;
import com.omnia.backend.common.exception.ResourceNotFoundException;
import com.omnia.backend.common.response.PagedResponse;
import com.omnia.backend.dto.request.SettleAdjustmentRequest;
import com.omnia.backend.dto.response.PayoutAdjustmentResponse;
import com.omnia.backend.entity.User;
import com.omnia.backend.security.service.CurrentUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Refunds after a payout (V54). When a paid-out order is refunded, the seller owes Omnia the refunded part
 * minus the commission of its payout: one adjustment per refund step and order. Later payouts deduct open
 * adjustments (oldest first, possibly in part); the admin can also record one as recovered or waived.
 * Nothing is edited or deleted: every settlement is its own row.
 *
 * <p>Locking: refund paths hold the checkout's order rows and then insert here; payouts lock their order
 * rows and then the seller's open adjustments. Both go orders → adjustments.
 */
@Service
public class SellerPayoutAdjustments {

    private static final Logger LOG = LoggerFactory.getLogger(SellerPayoutAdjustments.class);

    public static final String SOURCE_REFUND = "REFUND";
    public static final String SOURCE_PARTIAL_REFUND = "PARTIAL_REFUND";
    public static final String SOURCE_REVERSAL = "REVERSAL";

    static final int SELLER_LIMIT = 50;

    /** An open adjustment of a seller, locked by {@link #lockOpen}. */
    public record Open(long id, BigDecimal remaining) {
    }

    private final JdbcTemplate jdbc;
    private final CurrentUserService currentUserService;
    private final NotificationWriter notificationWriter;

    public SellerPayoutAdjustments(JdbcTemplate jdbc, CurrentUserService currentUserService,
                                   NotificationWriter notificationWriter) {
        this.jdbc = jdbc;
        this.currentUserService = currentUserService;
        this.notificationWriter = notificationWriter;
    }

    // ---------------------------------------------------------------- refunds (PayPal paths)

    /**
     * Records what the sellers of a checkout owe back after {@code refundedTotal} of it was refunded or
     * reversed. Called in the refund path's transaction, with the group's orders locked. Only orders that
     * already have a payout get an adjustment (unpaid ones are simply no longer owed). Cumulative and
     * idempotent: an order gets only the difference between its share of {@code refundedTotal} and what its
     * adjustments already cover, so a repeated event creates nothing.
     *
     * @return the number of adjustments created
     */
    public int recordRefund(long checkoutGroupId, BigDecimal refundedTotal, String source) {
        if (refundedTotal == null || refundedTotal.signum() <= 0) {
            return 0;
        }
        List<Map<String, Object>> orders = jdbc.queryForList(
                "SELECT o.id, o.organization_id, o.total_amount, o.payout_id, sp.commission_percent, g.name"
                        + " FROM orders o LEFT JOIN seller_payouts sp ON sp.id = o.payout_id"
                        + " LEFT JOIN organizations g ON g.id = o.organization_id"
                        + " WHERE o.checkout_group_id = ? ORDER BY o.id", checkoutGroupId);
        if (orders.stream().noneMatch(o -> o.get("payout_id") != null)) {
            return 0;
        }
        List<BigDecimal> totals = orders.stream().map(o -> (BigDecimal) o.get("total_amount")).toList();
        BigDecimal groupTotal = totals.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        List<BigDecimal> shares = PayoutRules.refundShares(refundedTotal, totals);
        int created = 0;
        for (int i = 0; i < orders.size(); i++) {
            Map<String, Object> order = orders.get(i);
            if (order.get("payout_id") == null || order.get("organization_id") == null || shares.get(i).signum() <= 0) {
                continue;
            }
            long orderId = ((Number) order.get("id")).longValue();
            BigDecimal share = shares.get(i);
            BigDecimal covered = jdbc.queryForObject(
                    "SELECT COALESCE(SUM(refunded_amount), 0) FROM seller_payout_adjustments WHERE order_id = ?",
                    BigDecimal.class, orderId);
            BigDecimal delta = share.subtract(covered == null ? BigDecimal.ZERO : covered);
            if (delta.signum() <= 0) {
                continue;
            }
            BigDecimal percent = (BigDecimal) order.get("commission_percent");
            long organizationId = ((Number) order.get("organization_id")).longValue();
            long payoutId = ((Number) order.get("payout_id")).longValue();
            String organizationName = (String) order.get("name");
            String reason = reason(source, orderId, delta, (BigDecimal) order.get("total_amount"),
                    refundedTotal, groupTotal, orders.size());
            Long id = insert(organizationId, orderId, payoutId, "REFUND:" + share.toPlainString(), source, delta,
                    percent, PayoutRules.adjustmentAmount(delta, percent), reason);
            if (id == null) {
                continue;
            }
            created++;
            BigDecimal amount = PayoutRules.adjustmentAmount(delta, percent);
            systemAudit(id, "Shitësi: " + organizationName + " · Porosia #" + orderId + " · Pagesa #" + payoutId
                    + " · Rimbursuar: " + delta.toPlainString() + " · Për t'u kthyer: " + amount.toPlainString());
            notificationWriter.payoutAdjustment(id, organizationId, organizationName, amount);
            LOG.info("Payout adjustment {} for order {} (payout {}): refunded {} → owed back {}",
                    id, orderId, payoutId, delta, amount);
        }
        return created;
    }

    private Long insert(long organizationId, long orderId, long payoutId, String refundKey, String source,
                        BigDecimal refunded, BigDecimal percent, BigDecimal amount, String reason) {
        LocalDateTime now = now();
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        try {
            jdbc.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO seller_payout_adjustments (organization_id, order_id, payout_id, refund_key, source,"
                                + " refunded_amount, commission_percent, amount, reason, created_by, created_at,"
                                + " settled_amount, settled_at) VALUES (?,?,?,?,?,?,?,?,?,NULL,?,?,?)",
                        Statement.RETURN_GENERATED_KEYS);
                ps.setLong(1, organizationId);
                ps.setLong(2, orderId);
                ps.setLong(3, payoutId);
                ps.setString(4, refundKey);
                ps.setString(5, source);
                ps.setBigDecimal(6, refunded);
                ps.setBigDecimal(7, percent == null ? BigDecimal.ZERO : percent);
                ps.setBigDecimal(8, amount);
                ps.setString(9, reason);
                ps.setTimestamp(10, Timestamp.valueOf(now));
                ps.setBigDecimal(11, BigDecimal.ZERO);
                // A 0.00 adjustment (only possible at 100% commission) is born settled.
                ps.setTimestamp(12, amount.signum() == 0 ? Timestamp.valueOf(now) : null);
                return ps;
            }, keys);
        } catch (DuplicateKeyException repeated) {
            // The same refund step was recorded by a concurrent delivery of the same event.
            return null;
        }
        return keys.getKey().longValue();
    }

    private static String reason(String source, long orderId, BigDecimal refunded, BigDecimal orderTotal,
                                 BigDecimal refundedTotal, BigDecimal groupTotal, int orderCount) {
        String what = switch (source) {
            case SOURCE_REVERSAL -> "PayPal e ktheu pagesën (reversal)";
            case SOURCE_PARTIAL_REFUND -> "Rimbursim i pjesshëm";
            default -> "Rimbursim";
        };
        StringBuilder text = new StringBuilder(what).append(" pas pagesës te shitësi: porosia #").append(orderId)
                .append(", ").append(refunded.toPlainString()).append(" € nga ")
                .append(orderTotal.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()).append(" €.");
        if (orderCount > 1 && refundedTotal.compareTo(groupTotal) < 0) {
            text.append(" Blerja kishte ").append(orderCount).append(" porosi; rimbursimi prej ")
                    .append(refundedTotal.toPlainString())
                    .append(" € u nda sipas vlerës së porosive. Kontrollojeni dhe hiqni dorë nëse nuk i përket këtij shitësi.");
        }
        return text.length() > 500 ? text.substring(0, 500) : text.toString();
    }

    /** admin_action_log row for a change no admin made (a PayPal event): actor "Sistemi". */
    private void systemAudit(long adjustmentId, String detail) {
        jdbc.update("INSERT INTO admin_action_log (actor_id, actor_label, http_method, route, target_type, target_id,"
                        + " detail, response_status, created_at) VALUES (NULL, 'Sistemi · PayPal', 'SYSTEM', ?, 'PAYMENT', ?, ?, 201, ?)",
                AdminActionRules.SYSTEM_PAYOUT_ADJUSTMENT_ROUTE, String.valueOf(adjustmentId),
                AdminActionRules.sanitize(detail), Timestamp.valueOf(now()));
    }

    // ---------------------------------------------------------------- payouts

    /** The seller's open adjustments, oldest first, locked until the payout commits. */
    public List<Open> lockOpen(long organizationId) {
        return jdbc.query("SELECT id, amount - settled_amount AS remaining FROM seller_payout_adjustments"
                        + " WHERE organization_id = ? AND settled_at IS NULL ORDER BY id FOR UPDATE",
                (r, i) -> new Open(r.getLong("id"), r.getBigDecimal("remaining")), organizationId);
    }

    /** Settles {@code taken[i]} of {@code open[i]} in the payout (rows locked by {@link #lockOpen}). */
    public void settleInPayout(long payoutId, List<Open> open, List<BigDecimal> taken, long adminId) {
        LocalDateTime now = now();
        for (int i = 0; i < open.size(); i++) {
            BigDecimal amount = taken.get(i);
            if (amount.signum() <= 0) {
                continue;
            }
            Open adjustment = open.get(i);
            boolean done = amount.compareTo(adjustment.remaining()) >= 0;
            jdbc.update("INSERT INTO seller_payout_adjustment_settlements (adjustment_id, kind, payout_id, amount, note,"
                            + " created_by, created_at) VALUES (?, 'PAYOUT', ?, ?, NULL, ?, ?)",
                    adjustment.id(), payoutId, amount, adminId, Timestamp.valueOf(now));
            jdbc.update("UPDATE seller_payout_adjustments SET settled_amount = settled_amount + ?,"
                            + " settled_at = CASE WHEN ? THEN ? ELSE NULL END,"
                            + " settled_in_payout_id = CASE WHEN ? THEN ? ELSE NULL END WHERE id = ?",
                    amount, done, Timestamp.valueOf(now), done, payoutId, adjustment.id());
        }
    }

    /** Open count and remaining sum per seller. */
    public Map<Long, Object[]> openTotals() {
        Map<Long, Object[]> totals = new LinkedHashMap<>();
        jdbc.query("SELECT organization_id, COUNT(*) AS n, SUM(amount - settled_amount) AS remaining"
                        + " FROM seller_payout_adjustments WHERE settled_at IS NULL GROUP BY organization_id",
                (ResultSet r) -> {
                    totals.put(r.getLong("organization_id"), new Object[]{r.getInt("n"), r.getBigDecimal("remaining")});
                });
        return totals;
    }

    // ---------------------------------------------------------------- admin

    @Transactional
    public PayoutAdjustmentResponse settle(Long adjustmentId, SettleAdjustmentRequest request) {
        if (adjustmentId == null || adjustmentId <= 0) {
            throw new IllegalArgumentException("Rregullimi nuk është i vlefshëm.");
        }
        String kind = request == null || request.kind() == null || request.kind().isBlank()
                ? "RECOVERED" : request.kind().trim().toUpperCase(Locale.ROOT);
        if (!kind.equals("RECOVERED") && !kind.equals("WAIVED")) {
            throw new IllegalArgumentException("Zgjidhni: rikuperuar ose hequr dorë.");
        }
        String note = request == null || request.note() == null ? "" : request.note().trim();
        if (note.length() < 3) {
            throw new IllegalArgumentException("Shkruani një shënim: si u rikuperua shuma ose pse hiqet dorë.");
        }
        if (note.length() > 500) {
            throw new IllegalArgumentException("Shënimi mund të ketë deri në 500 karaktere.");
        }
        User admin = currentUserService.requireCurrentUser();
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT a.organization_id, a.order_id, a.amount - a.settled_amount AS remaining, a.settled_at, g.name"
                        + " FROM seller_payout_adjustments a JOIN organizations g ON g.id = a.organization_id"
                        + " WHERE a.id = ? FOR UPDATE OF a", adjustmentId);
        if (rows.isEmpty()) {
            throw new ResourceNotFoundException("Rregullimi nuk u gjet.");
        }
        Map<String, Object> row = rows.get(0);
        BigDecimal remaining = (BigDecimal) row.get("remaining");
        if (row.get("settled_at") != null || remaining.signum() <= 0) {
            throw new ConflictException("Ky rregullim është mbyllur tashmë.");
        }
        LocalDateTime now = now();
        jdbc.update("INSERT INTO seller_payout_adjustment_settlements (adjustment_id, kind, payout_id, amount, note,"
                        + " created_by, created_at) VALUES (?, ?, NULL, ?, ?, ?, ?)",
                adjustmentId, kind, remaining, note, admin.getId(), Timestamp.valueOf(now));
        jdbc.update("UPDATE seller_payout_adjustments SET settled_amount = amount, settled_at = ? WHERE id = ?",
                Timestamp.valueOf(now), adjustmentId);
        AdminAudit.note("Shitësi: " + row.get("name") + " · Porosia #" + row.get("order_id") + " · "
                + (kind.equals("WAIVED") ? "Hoqi dorë" : "Rikuperuar") + ": " + remaining.toPlainString());
        return list("WHERE a.id = ?", List.of(adjustmentId), 1, 0).get(0);
    }

    @Transactional(readOnly = true)
    public PagedResponse<PayoutAdjustmentResponse> history(Long organizationId, String status, int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("Faqja nuk mund të jetë negative.");
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("Madhësia e faqes duhet të jetë nga 1 deri në 100.");
        }
        List<String> where = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (organizationId != null) {
            where.add("a.organization_id = ?");
            args.add(organizationId);
        }
        if (status != null && !status.isBlank()) {
            switch (status.trim().toUpperCase(Locale.ROOT)) {
                case "OPEN" -> where.add("a.settled_at IS NULL");
                case "SETTLED" -> where.add("a.settled_at IS NOT NULL");
                default -> throw new IllegalArgumentException("Statusi duhet të jetë OPEN ose SETTLED.");
            }
        }
        String clause = where.isEmpty() ? "" : "WHERE " + String.join(" AND ", where);
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM seller_payout_adjustments a " + clause,
                Long.class, args.toArray());
        List<PayoutAdjustmentResponse> content = list(clause, args, size, (long) page * size);
        int totalPages = (int) ((total + size - 1) / size);
        return new PagedResponse<>(content, page, size, total, totalPages, page == 0,
                page >= totalPages - 1, page < totalPages - 1, page > 0);
    }

    /** The seller's latest adjustments (newest first). */
    public List<PayoutAdjustmentResponse> forSeller(long organizationId) {
        return list("WHERE a.organization_id = ?", List.of(organizationId), SELLER_LIMIT, 0);
    }

    private List<PayoutAdjustmentResponse> list(String where, List<Object> args, int limit, long offset) {
        List<Object> all = new ArrayList<>(args);
        all.add(limit);
        all.add(offset);
        List<PayoutAdjustmentResponse> rows = jdbc.query(
                "SELECT a.*, g.name AS organization_name, u.first_name, u.last_name, u.username"
                        + " FROM seller_payout_adjustments a JOIN organizations g ON g.id = a.organization_id"
                        + " LEFT JOIN users u ON u.id = a.created_by " + where
                        + " ORDER BY a.created_at DESC, a.id DESC LIMIT ? OFFSET ?",
                (r, i) -> {
                    BigDecimal amount = r.getBigDecimal("amount");
                    BigDecimal settled = r.getBigDecimal("settled_amount");
                    Long createdBy = r.getObject("created_by", Long.class);
                    return new PayoutAdjustmentResponse(r.getLong("id"), r.getLong("organization_id"),
                            r.getString("organization_name"), r.getLong("order_id"), r.getLong("payout_id"),
                            r.getString("source"), r.getBigDecimal("refunded_amount"),
                            r.getBigDecimal("commission_percent"), amount, settled, amount.subtract(settled),
                            r.getTimestamp("settled_at") == null ? "OPEN" : "SETTLED", r.getString("reason"),
                            createdBy, createdBy == null ? "Sistemi" : name(r), time(r, "created_at"),
                            time(r, "settled_at"), r.getObject("settled_in_payout_id", Long.class), List.of());
                }, all.toArray());
        if (rows.isEmpty()) {
            return rows;
        }
        List<Long> ids = rows.stream().map(PayoutAdjustmentResponse::id).toList();
        Map<Long, List<PayoutAdjustmentResponse.Settlement>> settlements = new LinkedHashMap<>();
        jdbc.query("SELECT s.*, u.first_name, u.last_name, u.username FROM seller_payout_adjustment_settlements s"
                        + " LEFT JOIN users u ON u.id = s.created_by WHERE s.adjustment_id IN ("
                        + String.join(",", Collections.nCopies(ids.size(), "?")) + ") ORDER BY s.id",
                (ResultSet r) -> {
                    settlements.computeIfAbsent(r.getLong("adjustment_id"), k -> new ArrayList<>())
                            .add(new PayoutAdjustmentResponse.Settlement(r.getLong("id"), r.getString("kind"),
                                    r.getObject("payout_id", Long.class), r.getBigDecimal("amount"),
                                    r.getString("note"), r.getObject("created_by", Long.class) == null ? null : name(r),
                                    time(r, "created_at")));
                }, ids.toArray());
        return rows.stream().map(a -> new PayoutAdjustmentResponse(a.id(), a.organizationId(), a.organizationName(),
                a.orderId(), a.payoutId(), a.source(), a.refundedAmount(), a.commissionPercent(), a.amount(),
                a.settledAmount(), a.remainingAmount(), a.status(), a.reason(), a.createdById(), a.createdByName(),
                a.createdAt(), a.settledAt(), a.settledInPayoutId(),
                List.copyOf(settlements.getOrDefault(a.id(), List.of())))).toList();
    }

    private static String name(ResultSet r) throws SQLException {
        String first = r.getString("first_name");
        String last = r.getString("last_name");
        String full = ((first == null ? "" : first) + " " + (last == null ? "" : last)).trim();
        return full.isEmpty() ? r.getString("username") : full;
    }

    private static LocalDateTime time(ResultSet r, String column) throws SQLException {
        Timestamp value = r.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }
}
