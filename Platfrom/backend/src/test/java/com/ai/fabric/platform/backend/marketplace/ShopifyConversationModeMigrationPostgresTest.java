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

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class ShopifyConversationModeMigrationPostgresTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:16-alpine");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void latestMigrationUsesTheCurrentShopifyShellConversationMode() throws Exception {
        Flyway.configure()
            .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .locations("classpath:db/migration")
            .target(MigrationVersion.fromVersion("146"))
            .load()
            .migrate();

        try (Connection connection = POSTGRES.createConnection("");
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("""
                 select plugin_id, manifest_json
                 from platform_marketplace_plugin_versions
                 where plugin_id in (
                     'mkp-template-shopify-companion',
                     'mkp-template-shopify-companion-staging',
                     'mkp-template-shopify-companion-production'
                 )
                 order by plugin_id
                 """)) {
            int templateCount = 0;
            while (result.next()) {
                templateCount++;
                JsonNode manifest = objectMapper.readTree(result.getString("manifest_json"));
                assertThat(manifest.at("/contributions/template/shell/defaultConversationMode").asText())
                    .isEqualTo("thinker_deep");
            }
            assertThat(templateCount).isEqualTo(3);
        }
    }
}
