package com.ai.fabric.platform.backend.marketplace.service;

import com.ai.fabric.platform.backend.deployment.behavior.DeploymentBehaviorCatalogService;
import com.ai.fabric.platform.backend.deployment.model.UpdateDeploymentDraftRequest;
import com.ai.fabric.platform.backend.deployment.specialist.SpecialistAssistanceAuthorityService;
import com.ai.fabric.platform.backend.deployment.specialist.SpecialistAssistanceCompiler;
import com.ai.fabric.platform.backend.desk.DeskActivationService;
import com.ai.fabric.platform.backend.marketplace.entity.DeploymentMarketplacePluginInstallEntity;
import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginEntity;
import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginVersionEntity;
import com.ai.fabric.platform.backend.marketplace.model.*;
import com.ai.fabric.platform.backend.marketplace.repository.DeploymentMarketplacePluginInstallRepository;
import com.ai.fabric.platform.backend.marketplace.repository.MarketplacePluginVersionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SupportWorkspaceProductTemplateTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final Path root = templateRoot();
    private final MarketplaceManifestService manifests = new MarketplaceManifestService(mapper);

    @Test
    void approvedIdentityProfilesCoverExactCompiledScopesWithoutInternalLoomaiAuthority() throws Exception {
        JsonNode generic = new SpecialistAssistanceCompiler(mapper).compileBehavior(baseBehavior(),
            new SpecialistAssistanceCompiler.Authority(java.util.Map.of(), Set.of("document")))
            .at("/specialistAssistance/compiled");
        JsonNode acme = compileVariant("acme-cloud", "manifests/acme-cloud-support-reads.manifest.json", "fixtures/requests/acme-reads-install.json");
        for (String variant : List.of("generic", "acme")) {
            JsonNode compiled = "generic".equals(variant) ? generic : acme;
            JsonNode security = read("fixtures/requests/" + variant + "-identity-profile.json").path("securityConfig");
            Set<String> granted = new java.util.LinkedHashSet<>(List.of(security.path("externalIdentityGrantedScopes").asText().split(",")));
            Set<String> required = new java.util.LinkedHashSet<>(List.of("chat:query", "chat:suggestions", "chat:conversations",
                "chat:messages", "chat:handover:request", "chat:handover:control", "chat:handover:read"));
            compiled.path("invocations").forEach(invocation -> invocation.path("requiredScopes").forEach(scope -> required.add(scope.asText())));
            assertThat(granted).containsExactlyInAnyOrderElementsOf(required);
            assertThat(granted).noneMatch(scope -> scope.startsWith("support:") || scope.startsWith("runtime:") || scope.contains("*"));
            assertThat(security.path("externalIdentityRequireVerifiedEmail").asBoolean()).isTrue();
            var issues = new java.util.ArrayList<com.ai.fabric.platform.backend.deployment.model.DraftValidationIssue>();
            org.springframework.test.util.ReflectionTestUtils.invokeMethod(
                new com.ai.fabric.platform.backend.deployment.service.DeploymentDraftValidationService(mapper),
                "validateSecurityScopeCsv", security.path("externalIdentityGrantedScopes").asText(), "$.externalIdentityGrantedScopes", "INVALID", issues);
            assertThat(issues).isEmpty();
            var mergedSecurity = security.deepCopy();
            ((ObjectNode) mergedSecurity).put("adminApiKeyEnabled", true).put("connectorApiKeyEnabled", true);
            org.springframework.test.util.ReflectionTestUtils.invokeMethod(
                new com.ai.fabric.platform.backend.deployment.service.DeploymentDraftValidationService(mapper),
                "validateSecurity", mergedSecurity, issues);
            assertThat(issues).isEmpty();
            assertThat(security.has("anonymousGrantedScopes")).isFalse();
        }
        JsonNode engage = read("fixtures/requests/engage-supabase-profile.json");
        var request = mapper.treeToValue(engage,
            com.ai.fabric.platform.backend.aiworkspace.model.CreateAIWorkspaceInstallationRequest.class);
        var profiles = new com.ai.fabric.platform.backend.aiworkspace.service.AIWorkspaceAuthenticationProfileRegistry(mapper);
        var profile = profiles.resolve(request.authProfileCode(), request.authProfileVersion(),
            com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceInteractionPolicy.parse(request.interactionPolicy()),
            request.authConfiguration());
        assertThat(profile.code()).isEqualTo("supabase");
        assertThat(profile.publicConfiguration().has("grantedScopes")).isFalse();
    }

    @Test
    void scopeSyntaxAcceptsVersionedRefsButStillRejectsWhitespaceAndKeepsIssuerSyntaxNarrow() {
        var validator = new com.ai.fabric.platform.backend.deployment.service.DeploymentDraftValidationService(mapper);
        var issues = new java.util.ArrayList<com.ai.fabric.platform.backend.deployment.model.DraftValidationIssue>();
        org.springframework.test.util.ReflectionTestUtils.invokeMethod(validator, "validateSecurityScopeCsv",
            "specialist:assist-area-setup@1", "$.scopes", "INVALID", issues);
        assertThat(issues).isEmpty();
        org.springframework.test.util.ReflectionTestUtils.invokeMethod(validator, "validateSecurityScopeCsv",
            "specialist:assist-area-setup@1\nchat:write", "$.scopes", "INVALID", issues);
        assertThat(issues).hasSize(1);
        issues.clear();
        org.springframework.test.util.ReflectionTestUtils.invokeMethod(validator, "validateSecurityTokenIdentifierCsv",
            "issuer@1", "$.issuer", "INVALID", issues);
        assertThat(issues).hasSize(1);
    }

    @Test
    void orchestrationFixtureUsesExistingBoundedProviderContractAndNativeActionIsNotAConnectorContribution() throws Exception {
        var provider = mapper.createObjectNode();
        provider.set("orchestrationModeOverrides", read("fixtures/orchestration-settings.json"));
        var issues = new java.util.ArrayList<com.ai.fabric.platform.backend.deployment.model.DraftValidationIssue>();
        org.springframework.test.util.ReflectionTestUtils.invokeMethod(
            new com.ai.fabric.platform.backend.deployment.service.DeploymentDraftValidationService(mapper),
            "validateOrchestrationModeOverrides", provider, issues);
        assertThat(issues).isEmpty();
        assertThat(provider.at("/orchestrationModeOverrides/executor/readActionResolution/maxIterations").asInt()).isEqualTo(2);
        assertThat(read("manifests/ai-support-workspace.manifest.json").at("/contributions/template/shell/defaultConversationMode").asText())
            .isEqualTo("executor");
        for (String manifest : List.of("loomai-support-diagnostics", "acme-cloud-support-reads")) {
            for (JsonNode action : read("manifests/" + manifest + ".manifest.json").at("/contributions/actions")) {
                assertThat(action.path("actionId").asText()).isNotEqualTo("consult_specialist");
            }
        }
    }

    @Test
    void everyCatalogManifestPassesActualMarketplaceValidationAndSubmissionShape() throws Exception {
        Set<String> fields = Set.of("schemaVersion", "pluginId", "version", "pluginType", "displayName", "description",
            "compatibility", "pricing", "permissions", "installForm", "contributions");
        for (JsonNode entry : read("catalog.json").path("entries")) {
            JsonNode manifest = read(entry.path("manifest").asText());
            manifest.fieldNames().forEachRemaining(field -> assertThat(fields).contains(field));
            var version = version(manifest);
            var parsed = manifests.parseAndValidate(plugin(manifest), version);
            assertThat(parsed.pluginType()).isEqualTo(manifest.path("pluginType").asText());
            ObjectNode envelope = mapper.createObjectNode().put("pluginSlug", entry.path("pluginSlug").asText())
                .put("releaseChannel", "EARLY_ACCESS");
            envelope.set("manifest", manifest);
            assertThat(mapper.treeToValue(envelope, CreateMarketplacePublisherSubmissionRequest.class).manifest()).isEqualTo(manifest);
            assertThat(manifest.toString()).doesNotContain("api.loomai.pro", "/api/internal/", "OPENAI_API_KEY", "service_role");
        }
    }

    @Test
    void baseIsConversationFirstWithEditableReadSlotsAndNoCaseOrDeskInstanceRequirement() throws Exception {
        JsonNode manifest = read("manifests/ai-support-workspace.manifest.json");
        var parsed = manifests.parseAndValidate(plugin(manifest), version(manifest));
        assertThat(parsed.contributions().templateDeploymentBehaviorType()).isEqualTo("CONVERSATIONAL");
        assertThat(parsed.contributions().templateRequiredPluginRefs()).containsExactly("mkp-data-ai-support-documents@1.0.0");
        JsonNode template = manifest.at("/contributions/template");
        assertThat(template.path("recommendedPluginIds")).extracting(JsonNode::asText).contains("mkp-integration-loomai-desk");
        for (JsonNode area : template.at("/deploymentBehavior/specialistAssistance/areas")) assertThat(area.path("readActions")).isEmpty();
        ObjectNode behavior = baseBehavior();
        var compiled = new SpecialistAssistanceCompiler(mapper).compileBehavior(behavior,
            new SpecialistAssistanceCompiler.Authority(java.util.Map.of(), Set.of("document")));
        assertThat(compiled.at("/specialistAssistance/compiled/invocations")).hasSize(3);
        assertThat(compiled.toString()).doesNotContain("caseProvider", "support_get_", "databaseUrl");
    }

    @Test
    void bothVariantsCompileTheirOwnInstalledReadToolsAndNeverInheritEachOthersAuthority() throws Exception {
        var loomai = compileVariant("loomai", "manifests/loomai-support-diagnostics.manifest.json", "fixtures/requests/loomai-diagnostics-install.json");
        var acme = compileVariant("acme-cloud", "manifests/acme-cloud-support-reads.manifest.json", "fixtures/requests/acme-reads-install.json");
        assertThat(loomai.toString()).contains("support_get_engage_readiness", "support:diagnostics:read");
        assertThat(acme.toString()).contains("acme_get_workspace_status", "acme:support:read")
            .doesNotContain("support_get_", "support:diagnostics:read", "LOOMAI_SUPPORT_DIAGNOSTIC_SERVICE_KEY");
        for (JsonNode compiled : List.of(loomai, acme)) {
            assertThat(compiled.path("invocations")).hasSize(3);
            assertThat(compiled.path("resources")).anySatisfy(resource -> {
                assertThat(resource.path("kind").asText()).isEqualTo("SpecialistChain");
                assertThat(resource.at("/spec/conversationPolicy").asText()).isEqualTo("DISABLED");
                assertThat(resource.at("/spec/targets")).hasSize(2);
                assertThat(resource.at("/spec/targets/0/transitions/parallelEligible").asBoolean()).isTrue();
            });
        }
        var noAcmeAuthority = new SpecialistAssistanceCompiler.Authority(java.util.Map.of(), Set.of("document"));
        var foreign = baseBehavior();
        foreign.set("specialistAssistance", read("overlays/acme-cloud/specialist-assistance.json"));
        assertThatThrownBy(() -> new SpecialistAssistanceCompiler(mapper).compileBehavior(foreign, noAcmeAuthority))
            .hasMessageContaining("not declared READ");
    }

    @Test
    void dataPluginUsesOwnedReadOnlyStorageAndExplicitApprovalNotInlinePrivateDocuments() throws Exception {
        JsonNode manifest = read("manifests/support-documents.manifest.json");
        var parsed = manifests.parseAndValidate(plugin(manifest), version(manifest));
        assertThat(parsed.datasets()).hasSize(1);
        JsonNode dataset = manifest.at("/contributions/datasets/0");
        assertThat(dataset.path("storageScope").asText()).isEqualTo("CUSTOMER_MANAGED");
        assertThat(dataset.path("sharingScope").asText()).isEqualTo("DEPLOYMENT_ONLY");
        assertThat(dataset.at("/sourceConnector/deleteSourceOnRemoval").asBoolean()).isFalse();
        assertThat(dataset.at("/documentPolicy/initialIndexRequiresConfirmation").asBoolean()).isTrue();
        assertThat(dataset.at("/documentPolicy/trustedAutoIndexingAllowed").asBoolean()).isFalse();
        for (JsonNode set : read("fixtures/knowledge-sets.json")) {
            for (JsonNode document : set.path("documents")) {
                Path path = root.resolve(document.path("path").asText()).normalize();
                assertThat(path.startsWith(root)).isTrue();
                String content = Files.readString(path);
                assertThat(content.length()).isGreaterThan(1000);
                assertThat(document.at("/metadata/publicationStatus").asText()).isEqualTo("PUBLIC_APPROVED");
                assertThat(content).doesNotContain("PRIVATE.md", "BEGIN PRIVATE KEY", "service_role");
            }
        }
    }

    @Test
    void requestFixturesDeserializeIntoExistingApiRecordsAndActionsDoNotAskForResourceAuthority() throws Exception {
        mapper.treeToValue(read("fixtures/requests/bootstrap.json"), CreateMarketplaceTemplateBootstrapRequest.class);
        mapper.treeToValue(read("fixtures/requests/document-install-update.json"), UpdateDeploymentMarketplaceInstallRequest.class);
        mapper.treeToValue(read("fixtures/requests/desk-enable.json"), DeskActivationService.EnableRequest.class);
        for (String file : List.of("loomai-diagnostics-install", "acme-reads-install")) {
            mapper.treeToValue(read("fixtures/requests/" + file + ".json"), CreateDeploymentMarketplaceInstallRequest.class);
        }
        for (String file : List.of("loomai-support-diagnostics", "acme-cloud-support-reads")) {
            for (JsonNode action : read("manifests/" + file + ".manifest.json").at("/contributions/actions")) {
                assertThat(action.path("readOnly").asBoolean()).isTrue();
                assertThat(action.path("anonymousAllowed").asBoolean()).isFalse();
                assertThat(action.path("params")).isEmpty();
                assertThat(action.path("groundingVectorSpaces").isMissingNode()).isTrue();
                assertThat(action.at("/route/managedHeaderSecretRefFields").isObject()).isTrue();
            }
        }
        for (String variant : List.of("loomai", "acme-cloud")) {
            var request = mapper.createObjectNode();
            var behavior = baseBehavior();
            behavior.set("specialistAssistance", read("overlays/" + variant + "/specialist-assistance.json"));
            request.set("behaviorConfig", behavior);
            request.set("shellConfig", read("overlays/" + variant + "/shell-config.json"));
            assertThat(mapper.treeToValue(request, UpdateDeploymentDraftRequest.class).securityConfig()).isNull();
        }
    }

    private JsonNode compileVariant(String variant, String actionFile, String requestFile) throws Exception {
        var actionManifest = read(actionFile);
        var dataManifest = read("manifests/support-documents.manifest.json");
        var actionVersion = version(actionManifest);
        var dataVersion = version(dataManifest);
        var actionInstall = install(actionManifest, variant);
        var dataInstall = install(dataManifest, variant);
        JsonNode request = read(requestFile);
        actionInstall.setConfigJson(request.path("config").toString());
        actionInstall.setSecretRefsJson(request.path("secretRefs").toString());
        var draftCompiler = new DeploymentMarketplaceDraftCompilerService(null, null, null, null, null, null,
            manifests, null, null, null, null, mapper, null);
        var actions = mapper.createObjectNode();
        ArrayNode catalog = actions.putArray("actions");
        for (JsonNode action : actionManifest.at("/contributions/actions")) {
            catalog.add(draftCompiler.compileActionContribution(action, actionInstall, plugin(actionManifest), actionVersion));
        }
        var knowledge = mapper.createObjectNode();
        knowledge.putArray("sources").addObject().put("id", "approved-support-documents")
            .put("entityType", "document").put("enabled", true).put("marketplaceManaged", true)
            .put("marketplaceInstallId", dataInstall.getId()).put("marketplacePluginId", dataInstall.getPluginId())
            .put("marketplacePluginVersion", dataVersion.getVersion());
        var installs = mock(DeploymentMarketplacePluginInstallRepository.class);
        var versions = mock(MarketplacePluginVersionRepository.class);
        when(installs.findByDeploymentIdOrderByUpdatedAtDesc(variant)).thenReturn(List.of(dataInstall, actionInstall));
        when(versions.findById(dataVersion.getId())).thenReturn(Optional.of(dataVersion));
        when(versions.findById(actionVersion.getId())).thenReturn(Optional.of(actionVersion));
        var authority = new SpecialistAssistanceAuthorityService(installs, versions, mapper).resolve(variant, actions, knowledge);
        ObjectNode behavior = baseBehavior();
        behavior.set("specialistAssistance", read("overlays/" + variant + "/specialist-assistance.json"));
        return new SpecialistAssistanceCompiler(mapper).compileBehavior(behavior, authority).at("/specialistAssistance/compiled");
    }

    private ObjectNode baseBehavior() throws Exception {
        ObjectNode behavior = (ObjectNode) new DeploymentBehaviorCatalogService(mapper).defaultConfig("CONVERSATIONAL");
        behavior.withArray("executionExtensions").add("SPECIALIST_ASSISTANCE");
        behavior.set("specialistAssistance", read("manifests/ai-support-workspace.manifest.json")
            .at("/contributions/template/deploymentBehavior/specialistAssistance"));
        return behavior;
    }

    private DeploymentMarketplacePluginInstallEntity install(JsonNode manifest, String deployment) {
        var install = new DeploymentMarketplacePluginInstallEntity();
        install.setId(deployment + "-" + manifest.path("pluginId").asText()); install.setDeploymentId(deployment);
        install.setPluginId(manifest.path("pluginId").asText()); install.setPluginVersionId(manifest.path("pluginId").asText() + "-v1");
        install.setStatus("ENABLED"); return install;
    }
    private MarketplacePluginEntity plugin(JsonNode manifest) {
        var plugin = new MarketplacePluginEntity(); plugin.setId(manifest.path("pluginId").asText());
        plugin.setPluginType(manifest.path("pluginType").asText()); return plugin;
    }
    private MarketplacePluginVersionEntity version(JsonNode manifest) {
        var version = new MarketplacePluginVersionEntity(); version.setId(manifest.path("pluginId").asText() + "-v1");
        version.setPluginId(manifest.path("pluginId").asText()); version.setVersion(manifest.path("version").asText());
        version.setManifestJson(manifest.toString()); return version;
    }
    private JsonNode read(String path) throws Exception { return mapper.readTree(Files.readString(root.resolve(path))); }
    private static Path templateRoot() {
        String configured = System.getProperty("support.template.root");
        if (configured != null) return Path.of(configured).toAbsolutePath().normalize();
        for (Path base = Path.of(System.getProperty("user.dir")).toAbsolutePath(); base != null; base = base.getParent()) {
            Path candidate = base.resolve("product-templates/ai-support-workspace");
            if (Files.isDirectory(candidate)) return candidate;
        }
        throw new IllegalStateException("Cannot find product-templates/ai-support-workspace.");
    }
}
