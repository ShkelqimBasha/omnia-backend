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
 * V37 against a database that already holds PayPal checkouts: migrate to V36, add a legacy
 * RECOVERY_REQUIRED checkout (state set by hand, no audit) and an ordinary one, then apply V37.
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

    @Test void v37BackfillsLegacyRecoveryCheckoutsOnly() {
        flyway("36").migrate();
        assertEquals("36", flyway("36").info().current().getVersion().getVersion());

        long role = jdbc.queryForObject("SELECT id FROM roles WHERE name='USER'", Long.class);
        jdbc.update("INSERT INTO users(role_id,username,first_name,email,password_hash) VALUES(?,?,?,?,?)",
                role, "legacy", "Legacy", "legacy@example.test", "unused");
        long user = jdbc.queryForObject("SELECT id FROM users WHERE username='legacy'", Long.class);
        String legacy = "11111111-1111-4111-8111-111111111111", ordinary = "22222222-2222-4222-8222-222222222222";
        for (String id : List.of(legacy, ordinary)) {
            jdbc.update("INSERT INTO checkout_groups(checkout_reference,user_id) VALUES(?,?)", id, user);
            long group = jdbc.queryForObject("SELECT id FROM checkout_groups WHERE checkout_reference=?", Long.class, id);
            jdbc.update("INSERT INTO paypal_checkouts(id,user_id,request_key,request_hash,checkout_group_id,amount,paypal_order_id,state) VALUES(?,?,?,?,?,?,?,?)",
                    id, user, id, "0".repeat(64), group, "31.00", "LEGACY" + id.charAt(0) + "ORDER",
                    id.equals(legacy) ? "RECOVERY_REQUIRED" : "APPROVAL");
        }

        flyway(null).migrate();
        assertEquals("37", flyway(null).info().current().getVersion().getVersion());
        flyway(null).validate();

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
    }

    @Test void migrationVersionsAreUniqueAndV37IsTheLatest() throws Exception {
        Pattern name = Pattern.compile("V(\\d+)__.+\\.sql");
        Map<Integer, List<String>> versions = new TreeMap<>();
        for (Resource resource : new PathMatchingResourcePatternResolver().getResources("classpath*:db/migration/*.sql")) {
            Matcher m = name.matcher(Objects.requireNonNull(resource.getFilename()));
            if (m.matches()) versions.computeIfAbsent(Integer.parseInt(m.group(1)), v -> new ArrayList<>()).add(resource.getFilename());
        }
        versions.forEach((version, files) -> assertEquals(1, files.size(), "duplicate version V" + version + ": " + files));
        assertEquals(List.of("V37__add_paypal_recovery.sql"), versions.get(37));
        assertEquals(37, ((TreeMap<Integer, List<String>>) versions).lastKey());
        for (int v = 1; v <= 37; v++) assertTrue(versions.containsKey(v), "missing V" + v);
    }
}
