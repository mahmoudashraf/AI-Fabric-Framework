package com.ai.fabric.platform.backend.externalintegration;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Runs all 24 real application/JPA worker authority cases on the production PostgreSQL schema. */
@Testcontainers
class ExternalIntegrationWorkerAuthorityPostgresTest extends ExternalIntegrationWorkerAuthorityJpaTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource static void postgres(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
        properties.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        properties.add("spring.flyway.locations", () -> "classpath:db/migration");
        properties.add("spring.flyway.target", () -> "206");
    }
}
