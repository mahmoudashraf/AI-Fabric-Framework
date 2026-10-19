package com.ai.fabric.platform.backend.deployment;

import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Definition;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerOfferCodec;
import com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencyArtifactPolicy;
import com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencySpec;
import com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencySpecValidator;
import com.ai.fabric.platform.backend.deployment.service.DeploymentSourceCapabilityManifestService;
import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginEntity;
import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginVersionEntity;
import com.ai.fabric.platform.backend.marketplace.service.MarketplaceManifestService;
import com.ai.fabric.platform.backend.marketplace.service.DeploymentMarketplaceInstallService;
import com.ai.fabric.platform.backend.marketplace.repository.MarketplacePluginRepository;
import com.ai.fabric.platform.backend.marketplace.repository.MarketplacePluginVersionRepository;
import com.ai.fabric.platform.backend.externalintegration.ExternalIntegrationCompilerResolver;
import com.ai.fabric.platform.backend.externalintegration.ExternalIntegrationEndpointPolicy;
import com.ai.fabric.platform.backend.externalintegration.ExternalIntegrationPolicy;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// Helper contract test against the real migration chain and production manifest parser.
@Testcontainers
class DealershipExternalCatalogPostgresTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    static final ObjectMapper MAPPER = new ObjectMapper();
    static final String EXTERNAL = "mkv-template-dealership-external-systems-v1-0";
    static final String INBOX = "mkv-template-dealership-external-systems-managed-inbox-v1-0";
    static final Map<String, String> BEFORE = new LinkedHashMap<>();
    static String history;
    static final List<String> PRESERVED_QUERIES = List.of(
        "select json_agg(row_to_json(p) order by id)::text from deployment_target_profiles p",
        "select json_agg(row_to_json(o) order by id)::text from platform_customer_offers o",
        "select json_agg(row_to_json(e) order by id)::text from platform_customer_offer_entitlements e",
        "select count(*)::text from deployment_managed_dependency_specs",
        "select count(*)::text from deployment_provider_resource_handles",
        "select count(*)::text from deployment_capacity_reservations",
        "select count(*)::text from provider_usage_allocations"
    );

    @BeforeAll static void migrate() throws Exception {
        migrateTo("195");
        history = text("select json_agg(row_to_json(v) order by id)::text from platform_marketplace_plugin_versions v");
        for (String query : PRESERVED_QUERIES) BEFORE.put(query, text(query));
        migrateTo("196");
    }

    @Test void newCatalogParsesAndPreservesEveryHistoricalRowAndOperationalLedger() throws Exception {
        String ids = "'mkv-data-dealership-external-stock-v1-0','mkv-action-dealership-external-discovery-v1-0',"
            + "'mkv-action-dealership-external-engagement-v1-0','" + EXTERNAL + "','" + INBOX + "'";
        assertThat(text("select json_agg(row_to_json(v) order by id)::text from platform_marketplace_plugin_versions v where id not in (" + ids + ")"))
            .isEqualTo(history);
        for (var entry : BEFORE.entrySet()) assertThat(text(entry.getKey())).as(entry.getKey()).isEqualTo(entry.getValue());
        try (var connection = POSTGRES.createConnection(""); var statement = connection.createStatement();
             var rows = statement.executeQuery("select p.id,p.plugin_type,v.id,v.version,v.status,v.manifest_json from platform_marketplace_plugins p join platform_marketplace_plugin_versions v on v.plugin_id=p.id where v.id in (" + ids + ")")) {
            int count = 0;
            while (rows.next()) {
                var plugin = new MarketplacePluginEntity(); plugin.setId(rows.getString(1)); plugin.setPluginType(rows.getString(2));
                var version = new MarketplacePluginVersionEntity(); version.setId(rows.getString(3)); version.setPluginId(plugin.getId());
                version.setVersion(rows.getString(4)); version.setStatus(rows.getString(5)); version.setManifestJson(rows.getString(6));
                assertThat(new MarketplaceManifestService(MAPPER).parseAndValidate(plugin, version).manifest()).isEqualTo(MAPPER.readTree(version.getManifestJson()));
                count++;
            }
            assertThat(count).isEqualTo(5);
        }
        assertThat(text("select version from flyway_schema_history where success order by installed_rank desc limit 1")).isEqualTo("196");
    }

    @Test void bothTemplatesAreUnboundExternalIntegrationsAndOnlyExplicitInboxHasManagedDefaults() throws Exception {
        var old = manifest("mkv-template-simulated-dealership-experience-v1-9").path("contributions").path("template");
        for (String id : List.of(EXTERNAL, INBOX)) {
            var template = manifest(id).path("contributions").path("template");
            assertThat(template.path("providerConfig")).isEqualTo(old.path("providerConfig"));
            assertThat(template.path("deploymentBehavior")).isEqualTo(old.path("deploymentBehavior"));
            assertThat(template.path("requiredPluginRefs")).hasSize(6);
            assertThat(template.toString()).doesNotContain("MANAGED_SIMULATOR", "MANAGED_RECEIVER", "demo-backend");
            var defaults = template.path("requiredPluginInstallDefaults");
            assertThat(defaults.path("mkp-data-dealership-knowledge-v1@1.0.0"))
                .isEqualTo(MAPPER.readTree("{\"config\":{\"publicContentApprovalConfirmed\":false}}"));
            if (EXTERNAL.equals(id)) assertThat(defaults).hasSize(1);
            else {
                assertThat(defaults).hasSize(2);
                assertThat(defaults.path("mkp-integration-basic-loomai-inbox@1.3.0"))
                    .isEqualTo(old.path("requiredPluginInstallDefaults").path("mkp-integration-basic-loomai-inbox@1.3.0"));
            }
            for (JsonNode ref : template.path("requiredPluginRefs")) {
                String[] parts = ref.asText().split("@", 2);
                assertThat(text("select count(*)::text from platform_marketplace_plugin_versions where plugin_id='" + parts[0] + "' and version='" + parts[1] + "' and status='PUBLISHED'"))
                    .isEqualTo("1");
            }
        }
    }

    @Test void explicitFalseKnowledgeDefaultIsValidButMissingStorageAndSourceConsentRemainSeparate() throws Exception {
        var knowledge = manifest("mkv-data-dealership-knowledge-v1-0");
        var plugin = new MarketplacePluginEntity(); plugin.setId("mkp-data-dealership-knowledge-v1"); plugin.setPluginType("DATA");
        var version = new MarketplacePluginVersionEntity(); version.setId("mkv-data-dealership-knowledge-v1-0");
        version.setPluginId(plugin.getId()); version.setVersion("1.0.0"); version.setManifestJson(knowledge.toString());
        var parsed = new MarketplaceManifestService(MAPPER).parseAndValidate(plugin, version);
        var evaluator = mock(DeploymentMarketplaceInstallService.class);
        for (String templateId : List.of(EXTERNAL, INBOX)) {
            var defaults = manifest(templateId).at("/contributions/template/requiredPluginInstallDefaults/mkp-data-dealership-knowledge-v1@1.0.0");
            var config = (com.fasterxml.jackson.databind.node.ObjectNode) defaults.path("config").deepCopy();
            assertThat(config.path("publicContentApprovalConfirmed").isBoolean()).isTrue();
            assertThat(config.path("publicContentApprovalConfirmed").booleanValue()).isFalse();
            assertThat(config.has("documentStorageBindingRef")).isFalse();
            assertThat(defaults.has("secretRefs")).isFalse();
            List<String> missingStorage = ReflectionTestUtils.invokeMethod(evaluator, "evaluateInstallForm", parsed, config, MAPPER.createObjectNode());
            assertThat(missingStorage).containsExactly("Missing required config field 'documentStorageBindingRef'.");
            config.put("documentStorageBindingRef", "slot:document-storage");
            List<String> boundForm = ReflectionTestUtils.invokeMethod(evaluator, "evaluateInstallForm", parsed, config, MAPPER.createObjectNode());
            assertThat(boundForm).isEmpty();
            assertThat(config.path("publicContentApprovalConfirmed").booleanValue()).isFalse();
            config.remove("publicContentApprovalConfirmed");
            List<String> missingApproval = ReflectionTestUtils.invokeMethod(evaluator, "evaluateInstallForm", parsed, config, MAPPER.createObjectNode());
            assertThat(missingApproval).containsExactly("Missing required config field 'publicContentApprovalConfirmed'.");
            for (JsonNode invalid : MAPPER.readTree("[\"false\",0,{},[]]")) {
                config.set("publicContentApprovalConfirmed", invalid);
                List<String> wrongType = ReflectionTestUtils.invokeMethod(evaluator, "evaluateInstallForm", parsed, config, MAPPER.createObjectNode());
                assertThat(wrongType).containsExactly("Config field 'publicContentApprovalConfirmed' must be a boolean.");
            }
        }
        var policy = knowledge.at("/contributions/datasets/0/documentPolicy");
        assertThat(policy.path("initialIndexRequiresConfirmation").booleanValue()).isTrue();
        assertThat(policy.path("trustedAutoIndexingAllowed").booleanValue()).isFalse();
        assertThat(knowledge.at("/contributions/knowledgeSources/0/filters/publicationStatus").textValue()).isEqualTo("PUBLIC_APPROVED");
    }

    @Test void canonicalSourceAndEveryReadAndWriteActionRemainWithTheirSecuritySemantics() throws Exception {
        var data = manifest("mkv-data-dealership-external-stock-v1-0");
        var source = data.path("contributions").path("datasets").get(0).path("syncConnector");
        assertThat(source.path("httpSource").path("sourceId").asText()).isEqualTo("dealership-external-stock-source");
        assertThat(source.path("connectionProfile").path("managedBaseUrlField").asText()).isEqualTo("providerBaseUrl");
        assertThat(source.path("httpSource").path("tombstonePolicy")).isEqualTo(manifest("mkv-data-simulated-dealership-stock-v1-1")
            .path("contributions").path("datasets").get(0).path("syncConnector").path("httpSource").path("tombstonePolicy"));
        for (JsonNode field : data.path("installForm")) {
            assertThat(field.path("required").asBoolean()).isTrue();
            assertThat(field.has("defaultValue")).isFalse();
        }
        var reads = manifest("mkv-action-dealership-external-discovery-v1-0").path("contributions").path("actions");
        assertThat(reads).hasSize(3);
        assertThat(reads.findValuesAsText("actionId")).containsExactly("dealership_search_inventory", "dealership_get_vehicle", "dealership_compare_vehicles");
        for (JsonNode action : reads) {
            assertThat(action.path("readOnly").asBoolean()).isTrue();
            assertThat(action.at("/route/sourceProjection/sourceRef").asText()).isEqualTo("dealership-external-stock-source");
            assertThat(action.at("/route/sourceProjection/requireSuccessfulSync").asBoolean()).isTrue();
            assertThat(action.at("/route/sourceProjection/maxStalenessSeconds").asInt()).isEqualTo(1800);
        }
        var writes = manifest("mkv-action-dealership-external-engagement-v1-0").path("contributions").path("actions");
        assertThat(writes).isEqualTo(manifest("mkv-action-dealership-engagement-v1-2").path("contributions").path("actions"));
        assertThat(writes).hasSize(2);
        for (JsonNode action : writes) {
            assertThat(action.path("requiresConfirmation").asBoolean()).isTrue();
            assertThat(action.at("/route/managedUrlField").asText()).isEqualTo("receiverActionUrl");
            if ("dealership_request_test_drive".equals(action.path("actionId").asText())) {
                assertThat(action.path("anonymousAllowed").asBoolean()).isFalse();
            }
        }
    }

    @Test void optionalInboxFixturePassesRealSpecValidatorAndDedicatedArtifactNormalization() throws Exception {
        Path root = Path.of(System.getProperty("repoRoot"));
        var spec = MAPPER.readValue(Files.readString(root.resolve(
            "product-services/basic-loomai-inbox-service/deployment/managed-dependency-spec.template.json")), ManagedDependencySpec.class);
        new ManagedDependencySpecValidator(MAPPER).validate(spec);
        assertThat(spec.services()).hasSize(1);
        assertThat(spec.services().getFirst().database()).isNull();
        var declaration = MAPPER.readTree("""
            {"schemaVersion":"loomai-managed-dependency-artifact/v1","managedDependencyContract":{
              "schemaVersion":"loomai-managed-dependency-contract/v1","purpose":"AI_OPERATIONAL_SERVICE",
              "lifecycleOwnership":"DEPLOYMENT_OWNED","persistence":"AI_OPERATIONAL_STATE",
              "initializationContract":"inbox-callback-canary/v1","serviceContract":"BASIC_INBOX_KEYS_V1"}}
            """);
        var normalized = new DeploymentSourceCapabilityManifestService(MAPPER).normalize(declaration);
        ManagedDependencyArtifactPolicy.requireCustomerManagedContract(normalized.manifest().path("managedDependencyContract"));
        assertThat(normalized.manifest()).isEqualTo(declaration);
        assertThat(normalized.hash()).isEqualTo("534d1b349fea12deec5374ce8114f3b5a2c47b1ba1791fe581db16d1bf0be403");
    }

    @Test void javascriptProfilesFromActualV196ManifestsPassTypedPolicyAndExactCompilerEvidence() throws Exception {
        var codec = new CustomerOfferCodec(MAPPER);
        var plugins = mock(MarketplacePluginRepository.class);
        var versions = mock(MarketplacePluginVersionRepository.class);
        var input = MAPPER.createObjectNode();
        var versionInputs = input.putArray("pluginVersions");
        var definitionTree = MAPPER.createObjectNode();
        var pins = definitionTree.putArray("plugins");
        for (String id : List.of("mkv-data-dealership-external-stock-v1-0", "mkv-action-dealership-external-engagement-v1-0")) {
            JsonNode manifest = manifest(id);
            var plugin = new MarketplacePluginEntity(); plugin.setId(manifest.path("pluginId").asText());
            plugin.setPluginType(manifest.path("pluginType").asText()); plugin.setStatus("ACTIVE");
            var version = new MarketplacePluginVersionEntity(); version.setId(id); version.setPluginId(plugin.getId());
            version.setVersion("1.0.0"); version.setStatus("PUBLISHED"); version.setManifestJson(manifest.toString());
            when(plugins.findById(plugin.getId())).thenReturn(Optional.of(plugin));
            when(versions.findById(id)).thenReturn(Optional.of(version));
            var value = versionInputs.addObject(); value.put("id", id).put("pluginId", plugin.getId()).put("version", "1.0.0");
            value.set("manifest", manifest);
            pins.addObject().put("pluginId", plugin.getId()).put("versionId", id).put("manifestSha256", codec.hash(manifest));
        }
        input.putArray("targetProfileIds").add("target-staging").add("target-production");
        var hosts = input.putObject("allowedHosts");
        hosts.putArray("vehicle-stock").add("stock.customer.example");
        hosts.putArray("business-actions").add("actions.customer.example");
        String script = """
            import {readFileSync} from 'node:fs';
            import {pathToFileURL} from 'node:url';
            const {buildExternalIntegrationBindings} = await import(pathToFileURL(process.argv[1]).href);
            process.stdout.write(JSON.stringify(buildExternalIntegrationBindings(JSON.parse(readFileSync(0, 'utf8')))));
            """;
        Path helper = Path.of(System.getProperty("repoRoot"), "verification-support/productization-01036/simulated-dealership-offer.mjs");
        var process = new ProcessBuilder("node", "--input-type=module", "--eval", script, helper.toString()).redirectErrorStream(true).start();
        JsonNode generated;
        try {
            try (var stdin = process.getOutputStream()) { stdin.write(input.toString().getBytes(StandardCharsets.UTF_8)); }
            assertThat(process.waitFor(20, TimeUnit.SECONDS)).as("bounded local JS helper").isTrue();
            String output = new String(process.getInputStream().readNBytes(32768), StandardCharsets.UTF_8);
            assertThat(process.exitValue()).as(output).isZero();
            generated = MAPPER.readTree(output);
        } finally { process.destroyForcibly(); }
        definitionTree.set("externalIntegrationProfiles", generated.path("profiles"));
        definitionTree.set("bindingSlots", generated.path("slots"));
        var targets = definitionTree.putArray("targets");
        targets.addObject().put("targetProfileId", "target-staging").put("intent", "CUSTOMER_STAGING").put("profileFingerprint", "synthetic-stage");
        targets.addObject().put("targetProfileId", "target-production").put("intent", "CUSTOMER_PRODUCTION").put("profileFingerprint", "synthetic-production");
        var definition = MAPPER.treeToValue(definitionTree, Definition.class);
        var policy = new ExternalIntegrationPolicy(plugins, versions, new MarketplaceManifestService(MAPPER), codec,
            new ExternalIntegrationEndpointPolicy());
        policy.validateDefinition(definition);
        assertThat(definition.externalIntegrationProfiles()).hasSize(2);
        assertThat(definition.externalIntegrationProfiles().getFirst().accountScopeFields()).containsExactly("advertiserId");
        assertThat(definition.bindingSlots()).allMatch(slot -> slot.installConfigField() == null);
        var provenance = MAPPER.createObjectNode();
        var evidence = provenance.putArray("externalIntegrationProfiles");
        var compiler = new ExternalIntegrationCompilerResolver(MAPPER);
        definition.externalIntegrationProfiles().forEach(profile -> evidence.add(compiler.compilerEvidence(profile)));
        policy.requireCompilerEvidence(definition, provenance);
        evidence.remove(0);
        assertThatThrownBy(() -> policy.requireCompilerEvidence(definition, provenance)).hasMessageContaining("COMPILER_EVIDENCE_REQUIRED");
    }

    static JsonNode manifest(String id) throws Exception {
        return MAPPER.readTree(text("select manifest_json from platform_marketplace_plugin_versions where id='" + id + "'"));
    }
    static void migrateTo(String version) {
        Flyway.configure().dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
            .locations("classpath:db/migration").target(MigrationVersion.fromVersion(version)).load().migrate();
    }
    static String text(String query) throws Exception {
        try (var connection = POSTGRES.createConnection(""); var statement = connection.createStatement(); var rows = statement.executeQuery(query)) {
            assertThat(rows.next()).isTrue(); return rows.getString(1);
        }
    }
}
