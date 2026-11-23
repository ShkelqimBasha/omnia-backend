package com.omnia.backend.service.impl;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

/**
 * V37 and V38 against a database that already holds PayPal checkouts: migrate to V36, add a legacy
 * RECOVERY_REQUIRED checkout (state set by hand, no audit), an ordinary one and one whose PayPal
 * order ID was never stored, then apply V37 and V38 in turn.
 * Runs on its own MySQL container so the shared test database is not touched.
 */
class PayPalRecoveryMigrationTest {
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("omnia_migration").withUsername("omnia_migration").withPassword("omnia_migration_password");
    private static JdbcTemplate jdbc;

    @BeforeAll static void start() {
        MYSQL.start();
        jdbc = new JdbcTemplate(new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()));
    }

    @AfterAll static void stop() {
        MYSQL.stop();
    }

    private static Flyway flyway(String target) {
        var config = Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration");
        if (target != null) config.target(MigrationVersion.fromVersion(target));
        return config.load();
    }

    @Test void v37BackfillsLegacyRecoveryCheckoutsOnlyAndV38MarksEveryExistingCheckoutAsAttempted() {
        flyway("36").migrate();
        assertEquals("36", flyway("36").info().current().getVersion().getVersion());

        long role = jdbc.queryForObject("SELECT id FROM roles WHERE name='USER'", Long.class);
        jdbc.update("INSERT INTO users(role_id,username,first_name,email,password_hash) VALUES(?,?,?,?,?)",
                role, "legacy", "Legacy", "legacy@example.test", "unused");
        long user = jdbc.queryForObject("SELECT id FROM users WHERE username='legacy'", Long.class);
        String legacy = "11111111-1111-4111-8111-111111111111", ordinary = "22222222-2222-4222-8222-222222222222",
                uncertain = "33333333-3333-4333-8333-333333333333";
        for (String id : List.of(legacy, ordinary, uncertain)) {
            jdbc.update("INSERT INTO checkout_groups(checkout_reference,user_id) VALUES(?,?)", id, user);
            long group = jdbc.queryForObject("SELECT id FROM checkout_groups WHERE checkout_reference=?", Long.class, id);
            // The uncertain checkout's create request timed out before its PayPal order ID was stored.
            jdbc.update("INSERT INTO paypal_checkouts(id,user_id,request_key,request_hash,checkout_group_id,amount,paypal_order_id,state) VALUES(?,?,?,?,?,?,?,?)",
                    id, user, "r" + id.substring(1), "0".repeat(64), group, "31.00",
                    id.equals(uncertain) ? null : "LEGACY" + id.charAt(0) + "ORDER",
                    id.equals(legacy) ? "RECOVERY_REQUIRED" : id.equals(ordinary) ? "APPROVAL" : "CREATING");
        }
        jdbc.update("UPDATE paypal_checkouts SET created_at='2026-01-02 03:04:05', updated_at='2026-01-03 04:05:06'");

        flyway("37").migrate();
        assertEquals("37", flyway("37").info().current().getVersion().getVersion());
        flyway("37").validate();

        List<Map<String, Object>> events = jdbc.queryForList(
                "SELECT checkout_id,event_type,actor_user_id,from_state,to_state FROM paypal_recovery_events ORDER BY id");
        assertEquals(1, events.size(), events.toString());
        assertEquals(legacy, events.get(0).get("checkout_id"));
        assertEquals("LEGACY_MANUAL", events.get(0).get("event_type"));
        assertNull(events.get(0).get("actor_user_id"), "nobody is credited with the manual change");
        assertNull(events.get(0).get("from_state"));
        assertEquals("RECOVERY_REQUIRED", events.get(0).get("to_state"));
        // The migration records history only; the checkouts themselves are unchanged.
        assertEquals("RECOVERY_REQUIRED", jdbc.queryForObject("SELECT state FROM paypal_checkouts WHERE id=?", String.class, legacy));
        assertEquals("APPROVAL", jdbc.queryForObject("SELECT state FROM paypal_checkouts WHERE id=?", String.class, ordinary));
        for (String table : List.of("paypal_recovery_requests", "paypal_review_cases", "paypal_webhook_events"))
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class), table);

        // Closure columns obey the lifecycle constraint.
        jdbc.update("INSERT INTO paypal_review_cases(checkout_id,kind) VALUES(?, 'RECOVERY_EVIDENCE')", legacy);
        assertThrows(Exception.class, () -> jdbc.update("UPDATE paypal_review_cases SET status='CLOSED'"),
                "a closed case needs who, when, outcome and reason");
        assertThrows(Exception.class, () -> jdbc.update("UPDATE paypal_review_cases SET status='DONE'"));
        jdbc.update("DELETE FROM paypal_review_cases");

        List<Map<String, Object>> before = jdbc.queryForList(
                "SELECT id,user_id,request_key,request_hash,checkout_group_id,amount,paypal_order_id,approval_url,state,created_at,updated_at FROM paypal_checkouts ORDER BY id");
        flyway("38").migrate();
        assertEquals("38", flyway("38").info().current().getVersion().getVersion());
        flyway("38").validate();
        // Nothing proves an existing checkout never sent a create request, so every row is marked.
        assertEquals(3, jdbc.queryForObject(
                "SELECT COUNT(*) FROM paypal_checkouts WHERE paypal_create_attempted_at = created_at", Integer.class));
        // Request keys, provider IDs, states and timestamps are preserved exactly; history is unchanged.
        assertEquals(before, jdbc.queryForList(
                "SELECT id,user_id,request_key,request_hash,checkout_group_id,amount,paypal_order_id,approval_url,state,created_at,updated_at FROM paypal_checkouts ORDER BY id"));
        assertNull(jdbc.queryForObject("SELECT paypal_order_id FROM paypal_checkouts WHERE id=?", String.class, uncertain));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM paypal_recovery_events", Integer.class));

        // V39: every existing checkout was created by the Sandbox-only backend and is marked Sandbox.
        String columns = "id,user_id,request_key,request_hash,checkout_group_id,amount,paypal_order_id,approval_url,state,created_at,updated_at,paypal_create_attempted_at";
        List<Map<String, Object>> beforeV39 = jdbc.queryForList("SELECT " + columns + " FROM paypal_checkouts ORDER BY id");
        flyway(null).migrate();
        // V40 (main categories), V41 (accent colour), V42 (product sizes) and V49 (payment methods per company) do not touch PayPal tables.
        assertEquals("49", flyway(null).info().current().getVersion().getVersion());
        flyway(null).validate();
        assertEquals(List.of("sandbox", "sandbox", "sandbox"),
                jdbc.queryForList("SELECT environment FROM paypal_checkouts ORDER BY id", String.class));
        // Including the legacy RECOVERY_REQUIRED checkout (like order #31): state, provider ID, marker
        // and timestamps are exactly as before.
        assertEquals(beforeV39, jdbc.queryForList("SELECT " + columns + " FROM paypal_checkouts ORDER BY id"));
        assertEquals("RECOVERY_REQUIRED", jdbc.queryForObject("SELECT state FROM paypal_checkouts WHERE id=?", String.class, legacy));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM paypal_recovery_events", Integer.class));
        // Only the two known environments can be stored.
        assertThrows(Exception.class, () -> jdbc.update("UPDATE paypal_checkouts SET environment='production' WHERE id=?", legacy));
        assertThrows(Exception.class, () -> jdbc.update("UPDATE paypal_checkouts SET environment=NULL WHERE id=?", legacy));
        jdbc.update("UPDATE paypal_checkouts SET environment='live' WHERE id=?", ordinary);
        assertEquals("live", jdbc.queryForObject("SELECT environment FROM paypal_checkouts WHERE id=?", String.class, ordinary));
    }

    @Test void migrationVersionsAreUniqueAndV42IsTheLatest() throws Exception {
        Pattern name = Pattern.compile("V(\\d+)__.+\\.sql");
        Map<Integer, List<String>> versions = new TreeMap<>();
        for (Resource resource : new PathMatchingResourcePatternResolver().getResources("classpath*:db/migration/*.sql")) {
            Matcher m = name.matcher(Objects.requireNonNull(resource.getFilename()));
            if (m.matches()) versions.computeIfAbsent(Integer.parseInt(m.group(1)), v -> new ArrayList<>()).add(resource.getFilename());
        }
        versions.forEach((version, files) -> assertEquals(1, files.size(), "duplicate version V" + version + ": " + files));
        assertEquals(List.of("V37__add_paypal_recovery.sql"), versions.get(37));
        assertEquals(List.of("V38__record_paypal_create_attempts.sql"), versions.get(38));
        assertEquals(List.of("V39__record_paypal_checkout_environment.sql"), versions.get(39));
        assertEquals(List.of("V40__add_main_categories.sql"), versions.get(40));
        assertEquals(List.of("V41__add_user_settings_accent_color.sql"), versions.get(41));
        assertEquals(List.of("V42__add_product_sizes.sql"), versions.get(42));
        assertEquals(List.of("V49__add_organization_payment_settings.sql"), versions.get(49));
        // V43–V48 are reserved for parallel branches.
        assertEquals(49, ((TreeMap<Integer, List<String>>) versions).lastKey());
        for (int v = 1; v <= 42; v++) assertTrue(versions.containsKey(v), "missing V" + v);
    }
}
