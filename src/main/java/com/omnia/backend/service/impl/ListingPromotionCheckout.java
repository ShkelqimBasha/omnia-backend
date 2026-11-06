package com.omnia.backend.service.impl;

import com.omnia.backend.controller.ListingPromotionOfferController;
import com.omnia.backend.security.service.CurrentUserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/api/listing-promotions")
public class ListingPromotionCheckout {
    private final ListingPromotionOfferController offers;
    private final CurrentUserService users;
    private final PayPalPromotionGateway paypal;
    private final JdbcTemplate db;
    private final boolean sandboxEnabled;

    public ListingPromotionCheckout(ListingPromotionOfferController offers, CurrentUserService users,
            PayPalPromotionGateway paypal, JdbcTemplate db,
            @Value("${omnia.promotions.paypal.enabled:false}") boolean enabled,
            @Value("${omnia.promotions.paypal.live:false}") boolean live) {
        this.offers = offers;
        this.users = users;
        this.paypal = paypal;
        this.db = db;
        this.sandboxEnabled = enabled && !live;
    }

    @PostMapping("/{productId}/checkout/{reference}")
    public Checkout create(@PathVariable("productId") Long productId,
            @PathVariable("reference") UUID reference) {
        if (!sandboxEnabled) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
        var offer = offers.offer(productId);
        long buyer = users.requireCurrentUser().getId();
        String key = reference.toString();
        db.update("""
                INSERT INTO listing_promotion_payments
                  (reference_id, product_id, buyer_id, amount, currency, duration_days)
                VALUES (?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE reference_id = reference_id
                """, key, productId, buyer, offer.amount(), offer.currency(), offer.durationDays());
        var attempt = load(key);
        if (attempt.buyer() != buyer || attempt.product() != productId) {
            throw new AccessDeniedException("Invalid promotion reference");
        }
        if (attempt.url() == null) {
            // An unresolved attempt must not be retried beyond PayPal's idempotency window.
            var fresh = db.queryForObject("""
                    SELECT created_at >= CURRENT_TIMESTAMP - INTERVAL 1 HOUR
                    FROM listing_promotion_payments WHERE reference_id = ?
                    """, Boolean.class, key);
            if (!Boolean.TRUE.equals(fresh)) throw new ResponseStatusException(HttpStatus.CONFLICT);
            var approval = paypal.create(reference, attempt.amount());
            db.update("""
                    UPDATE listing_promotion_payments
                    SET provider_order_id = ?, merchant_id = ?, approval_url = ?
                    WHERE reference_id = ? AND provider_order_id IS NULL
                    """, approval.providerOrderId(), approval.merchantId(), approval.url().toString(), key);
            attempt = load(key);
        }
        return new Checkout(reference, attempt.url());
    }

    private Attempt load(String key) {
        return db.queryForObject("""
                SELECT product_id, buyer_id, amount, approval_url
                FROM listing_promotion_payments WHERE reference_id = ?
                """, (rs, row) -> new Attempt(rs.getLong(1), rs.getLong(2),
                        rs.getBigDecimal(3), rs.getString(4)), key);
    }

    private record Attempt(long product, long buyer, BigDecimal amount, String url) {}
    public record Checkout(UUID reference, String approvalUrl) {}
}
