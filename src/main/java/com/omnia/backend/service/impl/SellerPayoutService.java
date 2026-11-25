package com.omnia.backend.service.impl;

import com.omnia.backend.common.exception.ConflictException;
import com.omnia.backend.common.exception.ResourceNotFoundException;
import com.omnia.backend.common.response.PagedResponse;
import com.omnia.backend.dto.request.CreatePayoutRequest;
import com.omnia.backend.dto.response.PayoutBalanceResponse;
import com.omnia.backend.dto.response.PayoutEligibleOrderResponse;
import com.omnia.backend.dto.response.SellerPayoutResponse;
import com.omnia.backend.dto.response.SellerPayoutSummaryResponse;
import com.omnia.backend.entity.User;
import com.omnia.backend.enums.PaymentMethod;
import com.omnia.backend.enums.SellerType;
import com.omnia.backend.security.service.CurrentUserService;
import com.omnia.backend.security.service.OrganizationAccessService;
import org.springframework.beans.factory.annotation.Value;
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
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Seller payouts: what Omnia owes each seller for PayPal orders it collected and the seller delivered,
 * and the record of each transfer. Eligibility is defined in {@link PayoutRules}; the SQL below mirrors it.
 * Written with JDBC on purpose: orders.payout_id is not mapped on the Order entity, so a JPA save of an
 * order can never overwrite it.
 */
@Service
public class SellerPayoutService {

    static final int MAX_ORDERS_PER_PAYOUT = 1000;
    static final int SELLER_HISTORY_LIMIT = 50;

    /** Eligible, not yet paid out orders. Mirrors PayoutRules.eligible. */
    static final String ELIGIBLE = " FROM orders o JOIN payments p ON p.order_id = o.id"
            + " WHERE o.organization_id IS NOT NULL AND o.payout_id IS NULL AND o.status = 'DELIVERED'"
            + " AND p.method = 'PAYPAL' AND p.status = 'SUCCESS'"
            + " AND NOT EXISTS (SELECT 1 FROM paypal_checkouts pc WHERE pc.checkout_group_id = o.checkout_group_id"
            + " AND pc.state IN ('REFUNDED', 'REVIEW'))";

    private final JdbcTemplate jdbc;
    private final CurrentUserService currentUserService;
    private final OrganizationAccessService organizationAccessService;
    private final NotificationWriter notificationWriter;
    private final BigDecimal commissionPercent;
    private final Clock clock;

    public SellerPayoutService(JdbcTemplate jdbc,
                               CurrentUserService currentUserService,
                               OrganizationAccessService organizationAccessService,
                               NotificationWriter notificationWriter,
                               @Value("${app.payouts.commission-percent:0}") BigDecimal commissionPercent) {
        this.jdbc = jdbc;
        this.currentUserService = currentUserService;
        this.organizationAccessService = organizationAccessService;
        this.notificationWriter = notificationWriter;
        this.commissionPercent = PayoutRules.commissionPercent(commissionPercent);
        this.clock = Clock.systemUTC();
    }

    public BigDecimal commissionPercent() {
        return commissionPercent;
    }

    // ---------------------------------------------------------------- admin

    @Transactional(readOnly = true)
    public List<PayoutBalanceResponse> balances() {
        List<PayoutBalanceResponse> rows = jdbc.query(
                "SELECT o.organization_id, g.name, g.seller_type, COUNT(*) AS order_count,"
                        + " SUM(o.total_amount) AS gross, MIN(o.created_at) AS oldest"
                        + ELIGIBLE.replace(" FROM orders o", " FROM orders o JOIN organizations g ON g.id = o.organization_id")
                        + " GROUP BY o.organization_id, g.name, g.seller_type",
                (r, i) -> {
                    PayoutRules.Amounts a = PayoutRules.amounts(r.getBigDecimal("gross"), commissionPercent);
                    return new PayoutBalanceResponse(r.getLong("organization_id"), r.getString("name"),
                            sellerType(r.getString("seller_type")), r.getInt("order_count"),
                            a.gross(), a.commission(), a.net(), time(r, "oldest"));
                });
        List<PayoutBalanceResponse> sorted = new ArrayList<>(rows);
        sorted.sort(Comparator.comparing(PayoutBalanceResponse::netAmount).reversed()
                .thenComparing(PayoutBalanceResponse::organizationId));
        return sorted;
    }

    @Transactional(readOnly = true)
    public List<PayoutEligibleOrderResponse> eligibleOrders(Long organizationId) {
        requireOrganization(organizationId);
        return eligible(organizationId, null, false);
    }

    @Transactional
    public SellerPayoutResponse create(CreatePayoutRequest request) {
        if (request == null || request.organizationId() == null || request.organizationId() <= 0) {
            throw new IllegalArgumentException("Zgjidhni shitësin për pagesën.");
        }
        long organizationId = request.organizationId();
        String reference = trimmed(request.reference(), 100, "Referenca e pagesës mund të ketë deri në 100 karaktere.");
        String note = trimmed(request.note(), 500, "Shënimi mund të ketë deri në 500 karaktere.");
        String organizationName = requireOrganization(organizationId);
        User admin = currentUserService.requireCurrentUser();

        List<PayoutEligibleOrderResponse> orders;
        if (request.orderIds() == null || request.orderIds().isEmpty()) {
            orders = eligible(organizationId, null, true);
            if (orders.isEmpty()) {
                throw new ConflictException("Ky shitës nuk ka porosi të papaguara për t'u paguar.");
            }
            if (orders.size() > MAX_ORDERS_PER_PAYOUT) {
                orders = orders.subList(0, MAX_ORDERS_PER_PAYOUT);
            }
        } else {
            orders = lockRequested(organizationId, request.orderIds());
        }

        BigDecimal gross = orders.stream().map(PayoutEligibleOrderResponse::totalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        PayoutRules.Amounts amounts = PayoutRules.amounts(gross, commissionPercent);
        LocalDateTime now = LocalDateTime.now(clock.withZone(ZoneOffset.UTC));
        int count = orders.size();

        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO seller_payouts (organization_id, gross_amount, commission_percent, commission_amount,"
                            + " net_amount, order_count, reference, note, paid_at, created_by, created_at)"
                            + " VALUES (?,?,?,?,?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, organizationId);
            ps.setBigDecimal(2, amounts.gross());
            ps.setBigDecimal(3, commissionPercent);
            ps.setBigDecimal(4, amounts.commission());
            ps.setBigDecimal(5, amounts.net());
            ps.setInt(6, count);
            ps.setString(7, reference);
            ps.setString(8, note);
            ps.setTimestamp(9, Timestamp.valueOf(now));
            ps.setLong(10, admin.getId());
            ps.setTimestamp(11, Timestamp.valueOf(now));
            return ps;
        }, keys);
        long payoutId = keys.getKey().longValue();

        List<Long> ids = orders.stream().map(PayoutEligibleOrderResponse::orderId).toList();
        List<Object> args = new ArrayList<>();
        args.add(payoutId);
        args.addAll(ids);
        int updated = jdbc.update("UPDATE orders SET payout_id = ? WHERE payout_id IS NULL AND id IN ("
                + marks(ids.size()) + ")", args.toArray());
        if (updated != ids.size()) {
            // Cannot happen under the row locks; roll back rather than record a wrong payout.
            throw new ConflictException("Disa porosi u paguan njëkohësisht. Rifreskoni dhe provoni përsëri.");
        }

        AdminAudit.note("Shitësi: " + organizationName + " · Porosi: " + count
                + " · Neto: " + amounts.net().toPlainString());
        notificationWriter.payout(payoutId, organizationId, organizationName, amounts.net());
        return payouts("WHERE sp.id = ?", List.of(payoutId), 1, 0).get(0);
    }

    @Transactional(readOnly = true)
    public PagedResponse<SellerPayoutResponse> history(Long organizationId, int page, int size) {
        if (page < 0) {
            throw new IllegalArgumentException("Faqja nuk mund të jetë negative.");
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("Madhësia e faqes duhet të jetë nga 1 deri në 100.");
        }
        String where = organizationId == null ? "" : "WHERE sp.organization_id = ?";
        List<Object> args = organizationId == null ? List.of() : List.of(organizationId);
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM seller_payouts sp " + where, Long.class, args.toArray());
        List<SellerPayoutResponse> content = payouts(where, args, size, (long) page * size);
        int totalPages = (int) ((total + size - 1) / size);
        return new PagedResponse<>(content, page, size, total, totalPages, page == 0,
                page >= totalPages - 1, page < totalPages - 1, page > 0);
    }

    // ---------------------------------------------------------------- seller

    @Transactional(readOnly = true)
    public SellerPayoutSummaryResponse sellerSummary(Long organizationId) {
        organizationAccessService.requireCanManageOrders(organizationId);
        requireOrganization(organizationId);
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT COUNT(*) AS order_count, COALESCE(SUM(o.total_amount), 0) AS gross"
                        + ELIGIBLE + " AND o.organization_id = ?", organizationId);
        PayoutRules.Amounts a = PayoutRules.amounts((BigDecimal) row.get("gross"), commissionPercent);
        var balance = new SellerPayoutSummaryResponse.Balance(((Number) row.get("order_count")).intValue(),
                a.gross(), a.commission(), a.net());
        return new SellerPayoutSummaryResponse(balance, commissionPercent,
                payouts("WHERE sp.organization_id = ?", List.of(organizationId), SELLER_HISTORY_LIMIT, 0));
    }

    // ---------------------------------------------------------------- helpers

    private String requireOrganization(Long organizationId) {
        if (organizationId == null || organizationId <= 0) {
            throw new IllegalArgumentException("Shitësi nuk është i vlefshëm.");
        }
        List<String> names = jdbc.queryForList("SELECT name FROM organizations WHERE id = ?", String.class, organizationId);
        if (names.isEmpty()) {
            throw new ResourceNotFoundException("Shitësi nuk u gjet.");
        }
        return names.get(0);
    }

    /** Eligible orders of the seller, oldest first; with lock = true the order and payment rows are locked. */
    private List<PayoutEligibleOrderResponse> eligible(long organizationId, List<Long> onlyIds, boolean lock) {
        StringBuilder sql = new StringBuilder("SELECT o.id, o.created_at, o.total_amount, p.method, p.paid_at,"
                + " (SELECT cg.checkout_reference FROM checkout_groups cg WHERE cg.id = o.checkout_group_id) AS reference,"
                + " (SELECT MAX(h.changed_at) FROM order_status_history h WHERE h.order_id = o.id"
                + " AND h.to_status = 'DELIVERED') AS delivered_at")
                .append(ELIGIBLE).append(" AND o.organization_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(organizationId);
        if (onlyIds != null) {
            sql.append(" AND o.id IN (").append(marks(onlyIds.size())).append(")");
            args.addAll(onlyIds);
        }
        sql.append(" ORDER BY o.id");
        if (lock) {
            sql.append(" FOR UPDATE OF o, p");
        }
        return jdbc.query(sql.toString(), (r, i) -> {
            LocalDateTime created = time(r, "created_at");
            LocalDateTime delivered = time(r, "delivered_at");
            return new PayoutEligibleOrderResponse(r.getLong("id"), r.getString("reference"), created,
                    time(r, "paid_at"), delivered == null ? created : delivered,
                    r.getBigDecimal("total_amount").setScale(2), PaymentMethod.valueOf(r.getString("method")));
        }, args.toArray());
    }

    /**
     * Locks the requested orders (ascending id, like checkout and PayPal settlement) and checks each one:
     * another seller's or unknown → 400, already paid out → 409, not eligible → 400.
     */
    private List<PayoutEligibleOrderResponse> lockRequested(long organizationId, List<Long> requested) {
        Set<Long> unique = new LinkedHashSet<>();
        for (Long id : requested) {
            if (id == null || id <= 0) {
                throw new IllegalArgumentException("Numri i porosisë nuk është i vlefshëm.");
            }
            unique.add(id);
        }
        if (unique.size() > MAX_ORDERS_PER_PAYOUT) {
            throw new IllegalArgumentException("Një pagesë mund të përfshijë deri në " + MAX_ORDERS_PER_PAYOUT + " porosi.");
        }
        List<Long> ids = new ArrayList<>(unique);
        Collections.sort(ids);
        Map<Long, Object[]> found = new LinkedHashMap<>();
        jdbc.query("SELECT id, organization_id, payout_id FROM orders WHERE id IN (" + marks(ids.size())
                        + ") ORDER BY id FOR UPDATE",
                (ResultSet r) -> {
                    found.put(r.getLong("id"), new Object[]{r.getObject("organization_id", Long.class),
                            r.getObject("payout_id", Long.class)});
                }, ids.toArray());
        for (Long id : ids) {
            Object[] row = found.get(id);
            if (row == null || row[0] == null || ((Long) row[0]) != organizationId) {
                throw new IllegalArgumentException("Porosia #" + id + " nuk i përket këtij shitësi.");
            }
            if (row[1] != null) {
                throw new ConflictException("Porosia #" + id + " është paguar tashmë te shitësi.");
            }
        }
        List<PayoutEligibleOrderResponse> eligible = eligible(organizationId, ids, true);
        Set<Long> ok = new java.util.HashSet<>(eligible.stream().map(PayoutEligibleOrderResponse::orderId).toList());
        for (Long id : ids) {
            if (!ok.contains(id)) {
                throw new IllegalArgumentException("Porosia #" + id
                        + " nuk mund të paguhet: duhet të jetë paguar me PayPal, e dorëzuar dhe pa rimbursim.");
            }
        }
        return eligible;
    }

    private List<SellerPayoutResponse> payouts(String where, List<Object> args, int limit, long offset) {
        List<Object> all = new ArrayList<>(args);
        all.add(limit);
        all.add(offset);
        List<SellerPayoutResponse> rows = jdbc.query(
                "SELECT sp.*, g.name AS organization_name, u.first_name, u.last_name, u.username"
                        + " FROM seller_payouts sp JOIN organizations g ON g.id = sp.organization_id"
                        + " LEFT JOIN users u ON u.id = sp.created_by " + where
                        + " ORDER BY sp.paid_at DESC, sp.id DESC LIMIT ? OFFSET ?",
                (r, i) -> new SellerPayoutResponse(r.getLong("id"), r.getLong("organization_id"),
                        r.getString("organization_name"), r.getBigDecimal("gross_amount"),
                        r.getBigDecimal("commission_percent"), r.getBigDecimal("commission_amount"),
                        r.getBigDecimal("net_amount"), r.getInt("order_count"), List.of(),
                        r.getString("reference"), r.getString("note"), time(r, "paid_at"),
                        r.getObject("created_by", Long.class), name(r), time(r, "created_at")),
                all.toArray());
        if (rows.isEmpty()) {
            return rows;
        }
        List<Long> payoutIds = rows.stream().map(SellerPayoutResponse::id).toList();
        Map<Long, List<Long>> orderIds = new LinkedHashMap<>();
        jdbc.query("SELECT payout_id, id FROM orders WHERE payout_id IN (" + marks(payoutIds.size()) + ") ORDER BY id",
                (ResultSet r) -> {
                    orderIds.computeIfAbsent(r.getLong("payout_id"), k -> new ArrayList<>()).add(r.getLong("id"));
                }, payoutIds.toArray());
        return rows.stream().map(p -> new SellerPayoutResponse(p.id(), p.organizationId(), p.organizationName(),
                p.grossAmount(), p.commissionPercent(), p.commissionAmount(), p.netAmount(), p.orderCount(),
                List.copyOf(orderIds.getOrDefault(p.id(), List.of())), p.reference(), p.note(), p.paidAt(),
                p.createdById(), p.createdByName(), p.createdAt())).toList();
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

    private static SellerType sellerType(String value) {
        try {
            return value == null ? SellerType.BUSINESS : SellerType.valueOf(value);
        } catch (IllegalArgumentException unknown) {
            return SellerType.BUSINESS;
        }
    }

    private static String trimmed(String value, int max, String message) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String clean = value.trim();
        if (clean.length() > max) {
            throw new IllegalArgumentException(message);
        }
        return clean;
    }

    private static String marks(int count) {
        return String.join(",", Collections.nCopies(count, "?"));
    }
}
