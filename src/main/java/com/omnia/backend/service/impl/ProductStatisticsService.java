package com.omnia.backend.service.impl;

import com.omnia.backend.common.exception.ResourceNotFoundException;
import com.omnia.backend.security.service.OrganizationAccessService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Per-listing statistics for the seller: views, inquiries, add to cart, favourites. */
@Service
@Transactional(readOnly = true)
public class ProductStatisticsService {

    static final int MAX_PRODUCTS = 200;

    private final JdbcTemplate db;

    private final OrganizationAccessService accessService;

    private final Clock clock;

    public ProductStatisticsService(JdbcTemplate db, OrganizationAccessService accessService) {
        this.db = db;
        this.accessService = accessService;
        this.clock = Clock.system(ZoneId.of("Europe/Tirane"));
    }

    public record Totals(long views, long inquiries, long cartAdds, long favorites) { }

    public record ProductRow(long productId, String name, String status, Totals totals,
                             long favoritesNow) { }

    public record DayRow(LocalDate date, long views, long inquiries, long cartAdds, long favorites) { }

    public record ProductDetail(long productId, String name, int days, LocalDate from, LocalDate to,
                                Totals totals, long favoritesNow, List<DayRow> daily) { }

    public record OrganizationProducts(int days, LocalDate from, LocalDate to, Totals totals,
                                       List<ProductRow> products) { }

    static int requireDays(int days) {
        if (days != 7 && days != 30) {
            throw new IllegalArgumentException("Periudha duhet të jetë 7 ose 30 ditë");
        }
        return days;
    }

    public OrganizationProducts forOrganization(Long organizationId, int days) {
        requireDays(days);
        accessService.requireCanAccessOrganization(organizationId);
        LocalDate to = ProductActivityCounter.today(clock);
        LocalDate from = to.minusDays(days - 1L);

        List<ProductRow> rows = db.query("""
                SELECT p.id, p.name, p.status,
                       COALESCE(SUM(s.views), 0), COALESCE(SUM(s.inquiries), 0),
                       COALESCE(SUM(s.cart_adds), 0), COALESCE(SUM(s.favorites), 0),
                       (SELECT COUNT(*) FROM favorites f WHERE f.product_id = p.id)
                FROM products p
                LEFT JOIN product_daily_stats s
                  ON s.product_id = p.id AND s.stat_date BETWEEN ? AND ?
                WHERE p.organization_id = ?
                GROUP BY p.id, p.name, p.status
                ORDER BY COALESCE(SUM(s.views), 0) DESC, p.id DESC
                LIMIT ?
                """, (rs, i) -> new ProductRow(rs.getLong(1), rs.getString(2), rs.getString(3),
                        new Totals(rs.getLong(4), rs.getLong(5), rs.getLong(6), rs.getLong(7)),
                        rs.getLong(8)),
                java.sql.Date.valueOf(from), java.sql.Date.valueOf(to), organizationId, MAX_PRODUCTS);

        long views = 0, inquiries = 0, cartAdds = 0, favorites = 0;
        for (ProductRow row : rows) {
            views += row.totals().views();
            inquiries += row.totals().inquiries();
            cartAdds += row.totals().cartAdds();
            favorites += row.totals().favorites();
        }
        return new OrganizationProducts(days, from, to,
                new Totals(views, inquiries, cartAdds, favorites), rows);
    }

    public ProductDetail forProduct(Long organizationId, Long productId, int days) {
        requireDays(days);
        accessService.requireCanAccessOrganization(organizationId);
        List<String> names = db.queryForList(
                "SELECT name FROM products WHERE id = ? AND organization_id = ?",
                String.class, productId, organizationId);
        if (names.isEmpty()) {
            throw new ResourceNotFoundException("Listimi nuk u gjet");
        }
        LocalDate to = ProductActivityCounter.today(clock);
        LocalDate from = to.minusDays(days - 1L);

        Map<LocalDate, DayRow> stored = new HashMap<>();
        db.query("""
                SELECT stat_date, views, inquiries, cart_adds, favorites
                FROM product_daily_stats
                WHERE product_id = ? AND stat_date BETWEEN ? AND ?
                """, rs -> {
                    LocalDate day = rs.getDate(1).toLocalDate();
                    stored.put(day, new DayRow(day, rs.getLong(2), rs.getLong(3),
                            rs.getLong(4), rs.getLong(5)));
                }, productId, java.sql.Date.valueOf(from), java.sql.Date.valueOf(to));

        List<DayRow> daily = fillDays(from, to, stored);
        long favoritesNow = db.queryForObject(
                "SELECT COUNT(*) FROM favorites WHERE product_id = ?", Long.class, productId);
        return new ProductDetail(productId, names.get(0), days, from, to, sum(daily), favoritesNow, daily);
    }

    /** One row per day from..to (inclusive); days without activity are zero. */
    static List<DayRow> fillDays(LocalDate from, LocalDate to, Map<LocalDate, DayRow> stored) {
        List<DayRow> daily = new ArrayList<>();
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            daily.add(stored.getOrDefault(day, new DayRow(day, 0, 0, 0, 0)));
        }
        return daily;
    }

    static Totals sum(List<DayRow> daily) {
        long views = 0, inquiries = 0, cartAdds = 0, favorites = 0;
        for (DayRow row : daily) {
            views += row.views();
            inquiries += row.inquiries();
            cartAdds += row.cartAdds();
            favorites += row.favorites();
        }
        return new Totals(views, inquiries, cartAdds, favorites);
    }
}
