package com.ai.fabric.platform.backend.deployment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class CustomerRuntimeWorkerCapacityMigrationPostgresTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:16-alpine");

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void productizedTargetsEnforceTheReviewedWorkerEnvelope() throws Exception {
        Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .locations("classpath:db/migration")
            .target(MigrationVersion.fromVersion("185"))
            .load()
            .migrate();

        try (Connection connection = POSTGRES.createConnection("");
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("""
                 select id, resource_defaults_json
                 from deployment_target_profiles
                 where id in ('dtp-coolify-production-behavior', 'dtp-coolify-staging-behavior')
                 order by id
                 """)) {
            assertProfile(result, "dtp-coolify-production-behavior", "2", "1");
            assertProfile(result, "dtp-coolify-staging-behavior", "6", "5");
            assertThat(result.next()).isFalse();
        }
    }

    private void assertProfile(ResultSet result, String id, String connectorCpuSet,
                               String runnerCpuSet) throws Exception {
        assertThat(result.next()).isTrue();
        assertThat(result.getString("id")).isEqualTo(id);
        JsonNode defaults = mapper.readTree(result.getString("resource_defaults_json"));
        assertThat(defaults.path("connectorLimitsCpus").asText()).isEqualTo("0.5");
        assertThat(defaults.path("connectorLimitsMemory").asText()).isEqualTo("768m");
        assertThat(defaults.path("connectorLimitsCpuSet").asText()).isEqualTo(connectorCpuSet);
        assertThat(defaults.path("connectorJavaOpts").asText()).contains("-Xmx512m");
        assertThat(defaults.path("vectorizationRunnerLimitsCpus").asText()).isEqualTo("0.5");
        assertThat(defaults.path("vectorizationRunnerLimitsMemory").asText()).isEqualTo("768m");
        assertThat(defaults.path("vectorizationRunnerLimitsCpuSet").asText()).isEqualTo(runnerCpuSet);
        assertThat(defaults.path("vectorizationRunnerJavaOpts").asText()).contains("-Xmx512m");
        assertThat(defaults.path("runtimeDatabaseDiskMiB").asInt()).isEqualTo(2048);
    }
}
