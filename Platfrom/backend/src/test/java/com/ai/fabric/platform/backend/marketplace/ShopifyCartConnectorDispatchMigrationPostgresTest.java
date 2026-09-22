package com.ai.fabric.platform.backend.marketplace;

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
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class ShopifyCartConnectorDispatchMigrationPostgresTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:16-alpine");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void latestMigrationRoutesAdaptedCartWritesThroughTheDeploymentConnector() throws Exception {
        Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .locations("classpath:db/migration")
            .target(MigrationVersion.fromVersion("145"))
            .load()
            .migrate();

        try (Connection connection = POSTGRES.createConnection("");
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("""
                 select version, manifest_json
                 from platform_marketplace_plugin_versions
                 where id = 'mkv-action-shopify-cart-mcp-v1'
                 """)) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString("version")).isEqualTo("2.0.3");

            JsonNode manifest = objectMapper.readTree(result.getString("manifest_json"));
            assertThat(manifest.path("version").asText()).isEqualTo("2.0.3");
            assertThat(dispatchMode(manifest, "shopify_create_cart")).isEqualTo("CONNECTOR");
            assertThat(dispatchMode(manifest, "shopify_update_cart")).isEqualTo("CONNECTOR");
            assertThat(dispatchMode(manifest, "shopify_get_cart")).isEmpty();
            assertThat(result.next()).isFalse();
        }
    }

    private String dispatchMode(JsonNode manifest, String actionId) {
        return StreamSupport.stream(
                manifest.path("contributions").path("actions").spliterator(),
                false
            )
            .filter(action -> actionId.equals(action.path("actionId").asText()))
            .map(action -> action.path("execution").path("mcp").path("dispatchMode").asText())
            .findFirst()
            .orElseThrow();
    }
}
