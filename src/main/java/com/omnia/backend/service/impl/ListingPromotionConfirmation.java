package com.omnia.backend.service.impl;

import com.omnia.backend.security.service.CurrentUserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@RestController
@RequestMapping("/api/listing-promotions")
public class ListingPromotionConfirmation {
    private final CurrentUserService users;
    private final PayPalPromotionGateway paypal;
    private final JdbcTemplate db;
    private final boolean sandboxEnabled;

    public ListingPromotionConfirmation(CurrentUserService users, PayPalPromotionGateway paypal,
            JdbcTemplate db, @Value("${omnia.promotions.paypal.enabled:false}") boolean enabled,
            @Value("${omnia.promotions.paypal.live:false}") boolean live) {
        this.users = users;
        this.paypal = paypal;
        this.db = db;
        this.sandboxEnabled = enabled && !live;
    }

    @PostMapping("/{productId}/checkout/{reference}/confirm")
    public Confirmation confirm(@PathVariable("productId") long productId,
            @PathVariable("reference") UUID reference) {
        if (!sandboxEnabled) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
        long buyer = users.requireCurrentUser().getId();
        String key = reference.toString();
        var attempt = load(key, productId, buyer);
        if (attempt.until() != null) return new Confirmation("PAID", attempt.until());
        if (attempt.orderId() == null) throw new ResponseStatusException(HttpStatus.CONFLICT);

        var evidence = paypal.capture(attempt.orderId(), reference, attempt.merchant(), attempt.amount());
        if (!"PAID".equals(evidence.state())) return new Confirmation(evidence.state(), null);

        Instant until = evidence.paidAt().plus(attempt.days(), ChronoUnit.DAYS);
        db.update("""
                UPDATE listing_promotion_payments
                SET capture_id = ?, paid_at = ?, promoted_until = ?
                WHERE reference_id = ? AND product_id = ? AND buyer_id = ? AND capture_id IS NULL
                """, evidence.captureId(), Timestamp.from(evidence.paidAt()), Timestamp.from(until),
                key, productId, buyer);
        return new Confirmation("PAID", load(key, productId, buyer).until());
    }

    private Attempt load(String key, long productId, long buyer) {
        return db.query("""
                SELECT provider_order_id, merchant_id, amount, duration_days, promoted_until
                FROM listing_promotion_payments
                WHERE reference_id = ? AND product_id = ? AND buyer_id = ?
                """, (rs, row) -> {
                    var until = rs.getTimestamp(5);
                    return new Attempt(rs.getString(1), rs.getString(2), rs.getBigDecimal(3),
                            rs.getInt(4), until == null ? null : until.toInstant());
                }, key, productId, buyer).stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private record Attempt(String orderId, String merchant, BigDecimal amount, int days, Instant until) {}
    public record Confirmation(String state, Instant promotedUntil) {}
}
