package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginEntity;
import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginVersionEntity;
import com.ai.fabric.platform.backend.marketplace.service.MarketplaceManifestService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class ExistingVectorBindingMigrationPostgresTest {
    @Container private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    private final ObjectMapper mapper = new ObjectMapper();

    @Test void publishesOnlyExplicitExternalBindingCandidateAndPreservesExistingHistoryAndProfiles() throws Exception {
        migrate("191");
        String before = text("select row_to_json(v)::text from platform_marketplace_plugin_versions v where id='mkv-template-simulated-dealership-experience-v1-6'");
        String targets = text("select json_agg(row_to_json(p) order by id)::text from deployment_target_profiles p");
        var old = mapper.readTree(text("select manifest_json from platform_marketplace_plugin_versions where id='mkv-template-simulated-dealership-experience-v1-6'"));
        migrate("192");
        assertThat(text("select row_to_json(v)::text from platform_marketplace_plugin_versions v where id='mkv-template-simulated-dealership-experience-v1-6'")).isEqualTo(before);
        assertThat(text("select json_agg(row_to_json(p) order by id)::text from deployment_target_profiles p")).isEqualTo(targets);
        var candidate = mapper.readTree(text("select manifest_json from platform_marketplace_plugin_versions where id='mkv-template-simulated-dealership-experience-v1-7'"));
        ObjectNode expected = old.deepCopy(); expected.put("version", "1.7.0");
        var config = (ObjectNode) expected.path("contributions").path("template").path("providerConfig");
        config.remove(java.util.List.of("milvusHost", "milvusDatabaseName", "zillizCloudProjectId", "zillizCloudRegionId", "zillizCloudClusterPlan", "zillizCloudCuType", "zillizCloudCuSize"));
        config.put("vectorProvisioningMode", "EXTERNAL_EXISTING").put("vectorStoragePosture", "SHARED").put("milvusExternalBindingRequired", true);
        assertThat(candidate).isEqualTo(expected);
        assertThat(ManagedDeploymentProfileCatalog.managedVectorProvisioningRequested(config)).isFalse();
        assertThat(DeploymentExistingVectorBindingService.required(config)).isTrue();
        assertThat(config.path("openaiMaxTokens").asInt()).isEqualTo(2048);
        var plugin = new MarketplacePluginEntity(); plugin.setId("mkp-template-simulated-dealership-experience-v1"); plugin.setPluginType("TEMPLATE");
        var version = new MarketplacePluginVersionEntity(); version.setId("mkv-template-simulated-dealership-experience-v1-7");
        version.setPluginId(plugin.getId()); version.setVersion("1.7.0"); version.setStatus("PUBLISHED"); version.setManifestJson(candidate.toString());
        assertThat(new MarketplaceManifestService(mapper).parseAndValidate(plugin, version).manifest()).isEqualTo(candidate);
        assertThat(text("select indexdef from pg_indexes where indexname='uq_active_external_vector_binding_cluster'"))
            .contains("UNIQUE INDEX", "provider_resource_uuid", "EXTERNAL_VECTOR_STORAGE_BINDING", "ACTIVE");
        assertThat(text("select count(*)::text from deployment_provider_resource_handles where resource_kind='EXTERNAL_VECTOR_STORAGE_BINDING'")).isEqualTo("0");
        assertThat(text("select count(*)::text from provider_usage_allocations")).isEqualTo("0");

        String immutableUsernamePasswordTemplate = text("select row_to_json(v)::text from platform_marketplace_plugin_versions v where id='mkv-template-simulated-dealership-experience-v1-7'");
        migrate("193");
        assertThat(text("select row_to_json(v)::text from platform_marketplace_plugin_versions v where id='mkv-template-simulated-dealership-experience-v1-7'"))
            .isEqualTo(immutableUsernamePasswordTemplate);
        assertThat(text("select json_agg(row_to_json(p) order by id)::text from deployment_target_profiles p")).isEqualTo(targets);
        var tokenCandidate = mapper.readTree(text("select manifest_json from platform_marketplace_plugin_versions where id='mkv-template-simulated-dealership-experience-v1-8'"));
        ObjectNode expectedToken = candidate.deepCopy(); expectedToken.put("version", "1.8.0");
        ((ObjectNode) expectedToken.path("contributions").path("template").path("providerConfig"))
            .put("milvusAuthenticationType", "TOKEN");
        assertThat(tokenCandidate).isEqualTo(expectedToken);
        version.setId("mkv-template-simulated-dealership-experience-v1-8"); version.setVersion("1.8.0");
        version.setManifestJson(tokenCandidate.toString());
        assertThat(new MarketplaceManifestService(mapper).parseAndValidate(plugin, version).manifest()).isEqualTo(tokenCandidate);
        assertThat(text("select count(*)::text from deployment_provider_resource_handles where resource_kind='EXTERNAL_VECTOR_STORAGE_BINDING'")).isEqualTo("0");
        assertThat(text("select count(*)::text from provider_usage_allocations")).isEqualTo("0");
    }

    private void migrate(String version) {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .locations("classpath:db/migration").target(MigrationVersion.fromVersion(version)).load().migrate();
    }
    private String text(String sql) throws Exception {
        try (Connection connection = POSTGRES.createConnection(""); var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue(); return result.getString(1);
        }
    }
}
