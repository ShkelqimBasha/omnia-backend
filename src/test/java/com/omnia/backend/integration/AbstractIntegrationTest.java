package com.omnia.backend.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omnia.backend.OmniaBackendApplication;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(classes = OmniaBackendApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    protected static final MySQLContainer<?> MYSQL_CONTAINER =
            new MySQLContainer<>(
                    DockerImageName.parse("mysql:8.4")
            )
                    .withDatabaseName("omnia_test")
                    .withUsername("omnia_test")
                    .withPassword("omnia_test_password");

    static {
        MYSQL_CONTAINER.start();
    }

    @DynamicPropertySource
    static void configureDatabaseProperties(
            DynamicPropertyRegistry registry
    ) {
        registry.add(
                "spring.datasource.url",
                MYSQL_CONTAINER::getJdbcUrl
        );
        registry.add(
                "spring.datasource.username",
                MYSQL_CONTAINER::getUsername
        );
        registry.add(
                "spring.datasource.password",
                MYSQL_CONTAINER::getPassword
        );
        registry.add(
                "spring.datasource.driver-class-name",
                MYSQL_CONTAINER::getDriverClassName
        );
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    private javax.sql.DataSource dataSource;

    /**
     * Removes order, checkout and payment rows left by other test classes. They reference products
     * and users, so a test that deletes those would otherwise depend on the order the test classes run
     * in (which differs between Windows and Linux CI).
     */
    protected void deleteOrderData() {
        try (java.sql.Connection connection = dataSource.getConnection();
             java.sql.Statement statement = connection.createStatement()) {
            java.util.List<String> tables = new java.util.ArrayList<>();
            try (java.sql.ResultSet rows = statement.executeQuery(
                    "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE() "
                            + "AND table_type = 'BASE TABLE' AND (table_name LIKE 'order%' "
                            + "OR table_name LIKE 'checkout%' OR table_name LIKE 'payment%' "
                            + "OR table_name LIKE 'paypal%')")) {
                while (rows.next()) tables.add(rows.getString(1));
            }
            statement.execute("SET FOREIGN_KEY_CHECKS = 0");
            try {
                for (String table : tables) statement.executeUpdate("DELETE FROM `" + table + "`");
            } finally {
                statement.execute("SET FOREIGN_KEY_CHECKS = 1");
            }
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException("Could not clear order data", e);
        }
    }
}