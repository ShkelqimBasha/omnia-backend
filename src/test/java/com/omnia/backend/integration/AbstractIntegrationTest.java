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

    private static final MySQLContainer<?> MYSQL_CONTAINER =
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
}