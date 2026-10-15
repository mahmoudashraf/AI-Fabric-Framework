package com.ai.fabric.platform.backend.deployment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.PreparedStatement;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class CustomerRuntimeDatabaseCapacityMigrationPostgresTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void customerBehaviorTargetsCarryReviewedDatabaseCapacity() throws Exception {
        Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .locations("classpath:db/migration")
            .target(MigrationVersion.fromVersion("177"))
            .load()
            .migrate();

        ObjectMapper mapper = new ObjectMapper();
        try (Connection connection = POSTGRES.createConnection("");
             PreparedStatement statement = connection.prepareStatement("""
                 select resource_defaults_json
                   from deployment_target_profiles
                  where id in ('dtp-coolify-staging-behavior', 'dtp-coolify-production-behavior')
                  order by id
                 """)) {
            var rows = statement.executeQuery();
            int count = 0;
            while (rows.next()) {
                var defaults = mapper.readTree(rows.getString(1));
                assertThat(defaults.path(CoolifyDatabaseCapacity.CPU_FIELD).asInt()).isEqualTo(250);
                assertThat(defaults.path(CoolifyDatabaseCapacity.MEMORY_FIELD).asInt()).isEqualTo(512);
                count++;
            }
            assertThat(count).isEqualTo(2);
        }
    }
}
