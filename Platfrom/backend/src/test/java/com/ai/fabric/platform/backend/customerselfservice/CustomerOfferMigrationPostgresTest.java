package com.ai.fabric.platform.backend.customerselfservice;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.DriverManager;

@Testcontainers(disabledWithoutDocker = true)
class CustomerOfferMigrationPostgresTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test void platformOwnedMigrationEnforcesTheSameConstraintsOnPostgres() throws Exception {
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            CustomerOfferMigrationTest.verifyMigration(connection, "db/migration/V160__customer_self_service_offer_authorization.sql");
        }
    }
}
