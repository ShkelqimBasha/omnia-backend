package com.omnia.backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ListingPromotionStatusController {
    private final JdbcTemplate db;

    public ListingPromotionStatusController(JdbcTemplate db) {
        this.db = db;
    }

    @GetMapping("/promotion-status")
    public List<Promotion> active(@RequestParam("ids") List<Long> ids) {
        if (ids.isEmpty() || ids.size() > 100 || ids.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        return db.query("""
                SELECT p.id, MAX(pay.promoted_until) AS promoted_until
                FROM listing_promotion_payments pay
                JOIN products p ON p.id = pay.product_id
                JOIN organizations o ON o.id = p.organization_id
                WHERE p.status = 'ACTIVE' AND o.status = 'ACTIVE'
                  AND pay.capture_id IS NOT NULL
                  AND pay.paid_at IS NOT NULL
                  AND pay.promoted_until > CURRENT_TIMESTAMP
                  AND p.id IN (%s)
                GROUP BY p.id
                ORDER BY p.id
                """.formatted(placeholders),
                (rs, row) -> new Promotion(rs.getLong(1), rs.getTimestamp(2).toInstant()), ids.toArray());
    }

    public record Promotion(long productId, Instant promotedUntil) {}
}
