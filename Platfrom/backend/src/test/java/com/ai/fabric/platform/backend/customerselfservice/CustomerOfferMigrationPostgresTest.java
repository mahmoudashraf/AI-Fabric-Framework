package com.ai.fabric.platform.backend.customerselfservice;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.DriverManager;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
class CustomerOfferMigrationPostgresTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test void platformOwnedMigrationEnforcesTheSameConstraintsOnPostgres() throws Exception {
        try (var connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            CustomerOfferMigrationTest.verifyMigration(connection, "db/migration/V160__customer_self_service_offer_authorization.sql");
            ScriptUtils.executeSqlScript(connection,
                new ClassPathResource("db/migration/V181__customer_offer_command_types.sql"));
            for (String command : new String[]{"BOOTSTRAP_CONFIG", "BIND_S3", "REVOKE_S3", "PUBLISH_VERSION"}) {
                execute(connection, "INSERT INTO platform_customer_entitlement_commands VALUES ('command-" + command
                    + "','grant','key-" + command + "','hash','" + command
                    + "','admin','{}',CURRENT_TIMESTAMP)");
            }
            assertThatThrownBy(() -> execute(connection,
                "INSERT INTO platform_customer_entitlement_commands VALUES "
                    + "('command-unknown','grant','key-unknown','hash','UNKNOWN','admin','{}',CURRENT_TIMESTAMP)"))
                .isInstanceOf(SQLException.class);
        }
    }

    private static void execute(java.sql.Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement()) { statement.execute(sql); }
    }
}
