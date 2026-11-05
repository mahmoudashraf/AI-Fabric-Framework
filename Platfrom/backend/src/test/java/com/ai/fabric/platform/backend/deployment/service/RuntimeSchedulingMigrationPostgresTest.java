package com.ai.fabric.platform.backend.deployment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.util.HashMap;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class RuntimeSchedulingMigrationPostgresTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    private final ObjectMapper mapper = new ObjectMapper();

    @Test void changesOnlySeededSchedulingAndInitialHeapsWhileKeepingExplicitOverrides() throws Exception {
        migrate("233");
        try (var connection = POSTGRES.createConnection(""); var statement = connection.createStatement()) {
            statement.executeUpdate("""
                UPDATE deployment_target_profiles SET resource_defaults_json =
                (resource_defaults_json::jsonb || '{"connectorJavaOpts":"-Xms96m -Xmx512m","connectorLimitsCpuSet":"0-1"}'::jsonb)::text
                WHERE id='dtp-coolify-production-behavior'
                """);
        }
        var before = profiles(); migrate("234"); var after = profiles();
        for (var entry : before.entrySet()) {
            var previous = entry.getValue(); var current = after.get(entry.getKey());
            if (!entry.getKey().endsWith("-behavior")) { assertThat(current).isEqualTo(previous); continue; }
            for (String field : new String[]{"runtimeLimitsMemory", "runtimeLimitsMemorySwap", "runtimeLimitsMemoryReservation",
                "runtimeLimitsCpus", "connectorLimitsMemory", "connectorLimitsMemorySwap", "connectorLimitsMemoryReservation",
                "connectorLimitsCpus", "runtimeDatabaseMemoryMiB", "runtimeDatabaseCpuMillicores", "vectorizationRunnerJavaOpts"}) {
                assertThat(current.path(field)).as(field).isEqualTo(previous.path(field));
            }
            assertThat(current.path("runtimeJavaOpts").asText()).isEqualTo("-XX:ActiveProcessorCount=1 -Xms64m -Xmx768m");
            assertThat(current.path("runtimeLimitsCpuSet").asText()).isEmpty();
        }
        var staging = after.get("dtp-coolify-staging-behavior");
        assertThat(staging.path("connectorJavaOpts").asText()).isEqualTo("-XX:ActiveProcessorCount=1 -Xms32m -Xmx512m");
        assertThat(staging.path("connectorLimitsCpuSet").asText()).isEmpty();
        var production = after.get("dtp-coolify-production-behavior");
        assertThat(production.path("connectorJavaOpts").asText()).isEqualTo("-Xms96m -Xmx512m");
        assertThat(production.path("connectorLimitsCpuSet").asText()).isEqualTo("0-1");
    }

    private void migrate(String target) {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .locations("classpath:db/migration").target(MigrationVersion.fromVersion(target)).load().migrate();
    }
    private Map<String, JsonNode> profiles() throws Exception {
        var values = new HashMap<String, JsonNode>();
        try (var connection = POSTGRES.createConnection(""); var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT id,resource_defaults_json FROM deployment_target_profiles")) {
            while (rows.next()) values.put(rows.getString(1), mapper.readTree(rows.getString(2)));
        }
        return values;
    }
}
