package com.omnia.backend.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class V44BuyerToolsMigrationTest extends AbstractIntegrationTest {

    /** V44 keeps every existing review and marks only those backed by a delivered order. */
    @Test
    void v44KeepsExistingReviewsAndBackfillsTheBadge() {
        String schema = "omnia_v44_" + UUID.randomUUID().toString().substring(0, 8);
        String url = MYSQL_CONTAINER.getJdbcUrl().replace("/omnia_test", "/" + schema)
                + (MYSQL_CONTAINER.getJdbcUrl().contains("?") ? "&" : "?") + "createDatabaseIfNotExist=true";
        DriverManagerDataSource ds = new DriverManagerDataSource(url, "root", MYSQL_CONTAINER.getPassword());
        JdbcTemplate db = new JdbcTemplate(ds);
        try {
            Flyway.configure().dataSource(ds).locations("classpath:db/migration").target("42").load().migrate();
            long role = db.queryForObject("SELECT id FROM roles WHERE name='USER'", Long.class);
            db.update("INSERT INTO users (role_id, first_name, email, password_hash) VALUES (?, 'A', 'a@x.test', 'h'), (?, 'B', 'b@x.test', 'h')", role, role);
            long a = db.queryForObject("SELECT id FROM users WHERE email='a@x.test'", Long.class);
            long b = db.queryForObject("SELECT id FROM users WHERE email='b@x.test'", Long.class);
            long category = db.queryForObject("SELECT MIN(id) FROM categories", Long.class);
            db.update("INSERT INTO products (category_id, name, price, stock, status) VALUES (?, 'Bluzë', 10, 3, 'ACTIVE')", category);
            long product = db.queryForObject("SELECT id FROM products WHERE name='Bluzë'", Long.class);
            db.update("INSERT INTO orders (user_id, total_amount, status) VALUES (?, 10, 'DELIVERED'), (?, 10, 'CANCELLED')", a, b);
            for (long order : db.queryForList("SELECT id FROM orders", Long.class)) {
                db.update("INSERT INTO order_items (order_id, product_id, product_name, unit_price, quantity, subtotal) VALUES (?, ?, 'Bluzë', 10, 1, 10)", order, product);
            }
            db.update("INSERT INTO reviews (user_id, product_id, rating, comment) VALUES (?, ?, 5, 'E mirë'), (?, ?, 2, 'Jo')", a, product, b, product);

            Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();

            List<Map<String, Object>> rows = db.queryForList("SELECT user_id, verified_purchase, hidden FROM reviews ORDER BY user_id");
            assertEquals(2, rows.size(), "no review is deleted");
            assertEquals(Boolean.TRUE, rows.get(0).get("verified_purchase"));
            assertEquals(Boolean.FALSE, rows.get(1).get("verified_purchase"));
            assertEquals(Boolean.FALSE, rows.get(1).get("hidden"), "unverified old reviews stay visible");
        } finally {
            db.execute("DROP DATABASE IF EXISTS " + schema);
        }
    }
}
