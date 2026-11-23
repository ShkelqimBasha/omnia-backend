package com.omnia.backend.service.impl;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Counts what buyers do with a listing (views, add to cart, favourites,
 * inquiries) per product and day.
 *
 * Counts are kept in memory and written to product_daily_stats in one
 * small batch every few seconds, so the product page never waits for the
 * database. A crash loses at most the last batch, which is fine for
 * statistics.
 */
@Component
public class ProductActivityCounter {

    public enum Metric { VIEW, CART_ADD, FAVORITE, INQUIRY }

    /* Upper bound of distinct (product, day) keys kept between flushes. */
    static final int MAX_PENDING_KEYS = 20_000;

    private static final Logger log = LoggerFactory.getLogger(ProductActivityCounter.class);

    private static final ZoneId TIRANA = ZoneId.of("Europe/Tirane");

    private record Key(long productId, LocalDate day) { }

    private static final class Counts {
        final AtomicInteger views = new AtomicInteger();
        final AtomicInteger cartAdds = new AtomicInteger();
        final AtomicInteger favorites = new AtomicInteger();
        final AtomicInteger inquiries = new AtomicInteger();

        AtomicInteger of(Metric metric) {
            return switch (metric) {
                case VIEW -> views;
                case CART_ADD -> cartAdds;
                case FAVORITE -> favorites;
                case INQUIRY -> inquiries;
            };
        }
    }

    private final Map<Key, Counts> pending = new ConcurrentHashMap<>();

    private final JdbcTemplate db;

    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public ProductActivityCounter(JdbcTemplate db) {
        this(db, Clock.system(TIRANA));
    }

    ProductActivityCounter(JdbcTemplate db, Clock clock) {
        this.db = db;
        this.clock = clock;
    }

    public static LocalDate today(Clock clock) {
        return LocalDate.now(clock.withZone(TIRANA));
    }

    /** Counts one event; inside a transaction it is counted only if the transaction commits. */
    public void record(Long productId, Metric metric) {
        if (productId == null || productId <= 0 || metric == null) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    add(productId, metric);
                }
            });
        } else {
            add(productId, metric);
        }
    }

    private void add(long productId, Metric metric) {
        Key key = new Key(productId, today(clock));
        Counts counts = pending.get(key);
        if (counts == null) {
            if (pending.size() >= MAX_PENDING_KEYS) {
                return; // flooded with unknown ids: drop rather than grow without limit
            }
            counts = pending.computeIfAbsent(key, k -> new Counts());
        }
        counts.of(metric).incrementAndGet();
    }

    int pendingKeys() {
        return pending.size();
    }

    /**
     * Writes the buffered counts. Rows for products that no longer exist are
     * skipped by the INSERT ... SELECT.
     */
    @Scheduled(fixedDelayString = "${omnia.stats.flush-ms:15000}", initialDelay = 15000)
    public void flush() {
        for (Key key : pending.keySet()) {
            Counts counts = pending.remove(key);
            if (counts == null) {
                continue;
            }
            int views = counts.views.get();
            int cartAdds = counts.cartAdds.get();
            int favorites = counts.favorites.get();
            int inquiries = counts.inquiries.get();
            if (views + cartAdds + favorites + inquiries == 0) {
                continue;
            }
            try {
                db.update("""
                        INSERT INTO product_daily_stats
                          (product_id, stat_date, views, cart_adds, favorites, inquiries)
                        SELECT * FROM (
                          SELECT p.id, ? AS d, ? AS v, ? AS c, ? AS f, ? AS i
                          FROM products p WHERE p.id = ?
                        ) AS n
                        ON DUPLICATE KEY UPDATE
                          views = product_daily_stats.views + n.v,
                          cart_adds = product_daily_stats.cart_adds + n.c,
                          favorites = product_daily_stats.favorites + n.f,
                          inquiries = product_daily_stats.inquiries + n.i
                        """, java.sql.Date.valueOf(key.day()), views, cartAdds, favorites, inquiries,
                        key.productId());
            } catch (RuntimeException e) {
                log.warn("Could not store product statistics for product {}", key.productId(), e);
            }
        }
    }

    @PreDestroy
    void flushOnShutdown() {
        flush();
    }
}
