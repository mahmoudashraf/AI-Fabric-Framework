package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentProviderResourceHandleEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentTargetProfileEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import com.ai.fabric.platform.backend.deployment.model.ExistingVectorBindingContracts.AuthenticationType;
import com.ai.fabric.platform.backend.deployment.model.ExistingVectorBindingContracts.Request;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentProviderResourceHandleRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementGateService;
import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginEntity;
import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginVersionEntity;
import com.ai.fabric.platform.backend.marketplace.service.MarketplaceManifestService;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.BeanUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

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

        String immutableTokenTemplate = text("select row_to_json(v)::text from platform_marketplace_plugin_versions v where id='mkv-template-simulated-dealership-experience-v1-8'");
        migrate("194");
        assertThat(text("select row_to_json(v)::text from platform_marketplace_plugin_versions v where id='mkv-template-simulated-dealership-experience-v1-8'"))
            .isEqualTo(immutableTokenTemplate);
        var corrected = mapper.readTree(text("select manifest_json from platform_marketplace_plugin_versions where id='mkv-template-simulated-dealership-experience-v1-9'"));
        ObjectNode expectedContract = tokenCandidate.deepCopy(); expectedContract.put("version", "1.9.0");
        var declaration = (ObjectNode) expectedContract.path("contributions").path("template").path("deploymentBehavior");
        declaration.putArray("allowedExecutionExtensions").add("CONVERSATION_HANDOVER");
        declaration.putArray("defaultExecutionExtensions").add("CONVERSATION_HANDOVER");
        assertThat(corrected).isEqualTo(expectedContract);
        assertThat(text("select json_agg(row_to_json(p) order by id)::text from deployment_target_profiles p")).isEqualTo(targets);
        version.setId("mkv-template-simulated-dealership-experience-v1-9"); version.setVersion("1.9.0");
        version.setManifestJson(corrected.toString());
        assertThat(new MarketplaceManifestService(mapper).parseAndValidate(plugin, version).manifest()).isEqualTo(corrected);

        // Consume the real runtime declaration, not one synthesized from the requirements under test.
        Path runtimeManifest = Path.of(System.getProperty("basedir", "."))
            .resolve("../../ai-infrastructure-module/ai-fabric-runtime/src/main/resources/META-INF/loomai/runtime-capabilities.json");
        var actual = mapper.readTree(Files.readString(runtimeManifest));
        assertThat(actual.path("aiFabricVersion").asText()).isEqualTo("0.8.40");
        var behaviors = new com.ai.fabric.platform.backend.deployment.behavior.DeploymentBehaviorCatalogService(mapper);
        var manifests = new DeploymentSourceCapabilityManifestService(mapper);
        ObjectNode behavior = (ObjectNode) behaviors.defaultConfig("CONVERSATIONAL");
        behavior.set("channelBindings", declaration.path("allowedChannelBindings"));
        behavior.set("executionExtensions", declaration.path("defaultExecutionExtensions"));
        manifests.requireSupports(actual, behaviors.releaseRequirements(behavior));
        manifests.requireDocumentKnowledgeSupport(actual);
        manifests.requireExternalHttpIntegrationSupport(actual);
        ObjectNode unsupported = behavior.deepCopy();
        unsupported.set("executionExtensions", tokenCandidate.path("contributions").path("template")
            .path("deploymentBehavior").path("defaultExecutionExtensions"));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> manifests.requireSupports(actual, behaviors.releaseRequirements(unsupported)))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
            .hasMessageContaining("supportedExecutionExtensions: GOVERNED_RESOLVER");
        var engagementRefs = java.util.stream.StreamSupport.stream(corrected.path("contributions").path("template")
            .path("requiredPluginRefs").spliterator(), false).map(value -> value.asText())
            .filter(value -> value.startsWith("mkp-action-dealership-engagement-v1@")).toList();
        assertThat(engagementRefs).hasSize(1);
        String[] engagementRef = engagementRefs.getFirst().split("@", 2);
        com.fasterxml.jackson.databind.JsonNode engagement;
        try (Connection connection = POSTGRES.createConnection("");
             var query = connection.prepareStatement("select manifest_json from platform_marketplace_plugin_versions where plugin_id=? and version=?")) {
            query.setString(1, engagementRef[0]); query.setString(2, engagementRef[1]);
            try (var rows = query.executeQuery()) {
                assertThat(rows.next()).isTrue(); engagement = mapper.readTree(rows.getString(1));
                assertThat(rows.next()).isFalse();
            }
        }
        var actions = java.util.stream.StreamSupport.stream(engagement.path("contributions").path("actions").spliterator(), false).toList();
        assertThat(actions).filteredOn(action -> "dealership_request_test_drive".equals(action.path("actionId").asText()))
            .singleElement().satisfies(action -> {
                assertThat(action.path("requiresConfirmation").booleanValue()).isTrue();
                assertThat(action.path("anonymousAllowed").isBoolean()).isTrue();
                assertThat(action.path("anonymousAllowed").booleanValue()).isFalse();
                assertThat(action.path("readOnly").isBoolean()).isTrue();
                assertThat(action.path("readOnly").booleanValue()).isFalse();
            });
        assertThat(actions).filteredOn(action -> "dealership_request_callback".equals(action.path("actionId").asText()))
            .singleElement().satisfies(action -> {
                assertThat(action.path("requiresConfirmation").booleanValue()).isTrue();
                assertThat(action.path("anonymousAllowed").booleanValue()).isTrue();
                assertThat(action.path("readOnly").isBoolean()).isTrue();
                assertThat(action.path("readOnly").booleanValue()).isFalse();
            });
        assertThat(text("select count(*)::text from provider_usage_allocations")).isEqualTo("0");
        String correctedHistory = text("select row_to_json(v)::text from platform_marketplace_plugin_versions v where id='mkv-template-simulated-dealership-experience-v1-9'");
        migrate("195");
        assertThat(text("select row_to_json(v)::text from platform_marketplace_plugin_versions v where id='mkv-template-simulated-dealership-experience-v1-9'"))
            .isEqualTo(correctedHistory);
        assertThat(text("select json_agg(row_to_json(p) order by id)::text from deployment_target_profiles p")).isEqualTo(targets);
        assertThat(text("select count(*)::text from deployment_admission_unused_cancellations")).isEqualTo("0");
        assertThat(text("select count(*)::text from deployment_capacity_reservations")).isEqualTo("0");
        assertThat(text("select count(*)::text from provider_usage_allocations")).isEqualTo("0");
        assertThat(text("select version from flyway_schema_history where success order by installed_rank desc limit 1")).isEqualTo("195");
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-10-18T21:03:25.799299238Z", "2026-10-18T21:03:25.999999999Z"})
    void bindingSummaryRemainsExactlyEqualAfterPostgresTimestampRoundTrip(String timestamp) throws Exception {
        var deployment = new DeploymentEntity();
        deployment.setId("dep-timestamp-test"); deployment.setCustomerId("customer-test"); deployment.setTenantId("tenant-test");
        var version = new DeploymentVersionEntity();
        version.setId("ver-timestamp-test"); version.setDeploymentId(deployment.getId());
        version.setStatus("PUBLISHED"); version.setConfigHash("timestamp-config-hash");
        version.setEntityConfigJson("{\"ai-entities\":{\"vehicle\":{}}}");
        version.setProviderConfigJson("""
            {"vectorStrategy":"milvus","vectorProvisioningMode":"EXTERNAL_EXISTING","vectorStoragePosture":"SHARED",
             "milvusExternalBindingRequired":true,"milvusAuthenticationType":"TOKEN"}
            """);
        var target = new DeploymentTargetProfileEntity();
        target.setId("target-timestamp-test"); target.setActive(true); target.setProviderType(DeploymentProviderType.COOLIFY);
        var deployments = mock(DeploymentRepository.class);
        var versions = mock(DeploymentVersionRepository.class);
        var targets = mock(DeploymentTargetProfileRepository.class);
        var handles = mock(DeploymentProviderResourceHandleRepository.class);
        var secrets = mock(PlatformSecretService.class);
        var zilliz = mock(ZillizCloudControlPlaneClient.class);
        var saved = new AtomicReference<DeploymentProviderResourceHandleEntity>();
        var vault = new HashMap<String, String>();
        vault.put("ZILLIZ_CLOUD_API_KEY", "synthetic-control-plane-credential");
        when(deployments.findByIdForUpdate(deployment.getId())).thenReturn(Optional.of(deployment));
        when(versions.findById(version.getId())).thenReturn(Optional.of(version));
        when(targets.findById(target.getId())).thenReturn(Optional.of(target));
        when(handles.findFirstByDeploymentIdAndTargetProfileIdAndResourceKindOrderByUpdatedAtDesc(
            eq(deployment.getId()), eq(target.getId()), anyString())).thenAnswer(call -> Optional.ofNullable(saved.get()));
        when(handles.saveAndFlush(any())).thenAnswer(call -> { saved.set(call.getArgument(0)); return saved.get(); });
        when(secrets.resolveSecret(anyString())).thenAnswer(call -> vault.get(call.getArgument(0)));
        when(secrets.upsertManagedSecret(anyString(), anyString(), any())).thenAnswer(call -> {
            vault.put(call.getArgument(0), call.getArgument(1)); return null;
        });
        String endpoint = "https://synthetic-timestamps.zilliz.com";
        when(zilliz.getCluster("synthetic-cluster", "synthetic-control-plane-credential")).thenReturn(
            new ZillizCloudControlPlaneClient.ZillizClusterSummary("synthetic-cluster", "Free", "project-test", "region-test",
                "Serverless", "", 0, "RUNNING", endpoint, "Free"));
        when(zilliz.listServingCollections(endpoint, "synthetic-serving-token")).thenReturn(java.util.List.of());
        var service = new DeploymentExistingVectorBindingService(deployments, versions, targets, handles, secrets, zilliz,
            new TenantScopedVectorHandleResolver(), mock(TargetRetirementGateService.class), mock(PlatformAuditService.class), mapper);
        var request = new Request(version.getId(), "synthetic-cluster", "project-test", "region-test", "Free", "default",
            null, null, "synthetic-approval", AuthenticationType.TOKEN, "synthetic-serving-token");
        Instant observedAt = Instant.parse(timestamp);
        try (var time = mockStatic(Instant.class, CALLS_REAL_METHODS); Connection connection = POSTGRES.createConnection("")) {
            time.when(Instant::now).thenReturn(observedAt);
            var first = service.bind(deployment.getId(), target.getId(), request);
            try (var statement = connection.createStatement()) {
                statement.execute("create temporary table binding_timestamps (last_observed_at timestamp with time zone, "
                    + "created_at timestamp with time zone not null, updated_at timestamp with time zone not null)");
            }
            try (var insert = connection.prepareStatement("insert into binding_timestamps values (?, ?, ?)")) {
                insert.setObject(1, saved.get().getLastObservedAt().atOffset(ZoneOffset.UTC));
                insert.setObject(2, saved.get().getCreatedAt().atOffset(ZoneOffset.UTC));
                insert.setObject(3, saved.get().getUpdatedAt().atOffset(ZoneOffset.UTC));
                assertThat(insert.executeUpdate()).isEqualTo(1);
            }
            var reloaded = new DeploymentProviderResourceHandleEntity();
            BeanUtils.copyProperties(saved.get(), reloaded);
            try (var statement = connection.createStatement(); var result = statement.executeQuery("select * from binding_timestamps")) {
                assertThat(result.next()).isTrue();
                reloaded.setLastObservedAt(result.getObject("last_observed_at", OffsetDateTime.class).toInstant());
                reloaded.setCreatedAt(result.getObject("created_at", OffsetDateTime.class).toInstant());
                reloaded.setUpdatedAt(result.getObject("updated_at", OffsetDateTime.class).toInstant());
                assertThat(result.next()).isFalse();
            }
            saved.set(reloaded);
            Instant expected = observedAt.truncatedTo(ChronoUnit.MICROS);
            assertThat(first.checkedAt()).isEqualTo(expected);
            assertThat(reloaded.getLastObservedAt()).isEqualTo(expected);
            assertThat(reloaded.getCreatedAt()).isEqualTo(expected);
            assertThat(reloaded.getUpdatedAt()).isEqualTo(expected);
            assertThat(service.get(deployment.getId(), target.getId())).isEqualTo(first);
            assertThat(service.bind(deployment.getId(), target.getId(), request)).isEqualTo(first);
            verify(handles, times(1)).saveAndFlush(any());
            verify(secrets, times(2)).upsertManagedSecret(anyString(), anyString(), any());
        }
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
