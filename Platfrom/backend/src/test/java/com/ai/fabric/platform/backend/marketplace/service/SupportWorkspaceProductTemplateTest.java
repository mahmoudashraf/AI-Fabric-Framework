package com.ai.fabric.platform.backend.marketplace.service;

import com.ai.fabric.platform.backend.deployment.behavior.DeploymentBehaviorCatalogService;
import com.ai.fabric.platform.backend.deployment.model.UpdateDeploymentDraftRequest;
import com.ai.fabric.platform.backend.deployment.specialist.SpecialistAssistanceAuthorityService;
import com.ai.fabric.platform.backend.deployment.specialist.SpecialistAssistanceCompiler;
import com.ai.fabric.platform.backend.desk.DeskActivationService;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerOfferCodec;
import com.ai.fabric.platform.backend.externalintegration.ExternalIntegrationContracts;
import com.ai.fabric.platform.backend.externalintegration.ExternalIntegrationEndpointPolicy;
import com.ai.fabric.platform.backend.externalintegration.ExternalIntegrationPolicy;
import com.ai.fabric.platform.backend.marketplace.entity.DeploymentMarketplacePluginInstallEntity;
import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginEntity;
import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginVersionEntity;
import com.ai.fabric.platform.backend.marketplace.model.*;
import com.ai.fabric.platform.backend.marketplace.repository.DeploymentMarketplacePluginInstallRepository;
import com.ai.fabric.platform.backend.marketplace.repository.MarketplacePluginVersionRepository;
import com.ai.fabric.platform.backend.marketplace.repository.MarketplacePluginRepository;
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
    void reusableStarterDefersAssistanceWithoutDiscardingConfigurationUntilStorageIsConnected() throws Exception {
        JsonNode previous = read("manifests/ai-support-workspace.manifest.json");
        JsonNode current = read("manifests/ai-support-workspace.1.0.1.manifest.json");
        assertThat(previous.path("version").asText()).isEqualTo("1.0.0");
        assertThat(current.path("version").asText()).isEqualTo("1.0.1");
        JsonNode oldTemplate = previous.at("/contributions/template");
        JsonNode template = current.at("/contributions/template");
        assertThat(template.path("requiredPluginRefs")).isEqualTo(oldTemplate.path("requiredPluginRefs"));
        assertThat(template.at("/deploymentBehavior/defaultExecutionExtensions")).isEmpty();
        assertThat(template.at("/deploymentBehavior/specialistAssistance/enabled").asBoolean()).isFalse();
        assertThat(template.at("/deploymentBehavior/specialistAssistance/areas"))
            .isEqualTo(oldTemplate.at("/deploymentBehavior/specialistAssistance/areas"));
        ObjectNode behavior = ((ObjectNode) template.path("deploymentBehavior")).deepCopy();
        behavior.set("executionExtensions", mapper.createArrayNode());
        JsonNode compiled = new SpecialistAssistanceCompiler(mapper).compileBehavior(behavior,
            new SpecialistAssistanceCompiler.Authority(java.util.Map.of(), Set.of()));
        assertThat(compiled.at("/specialistAssistance/compiled").isMissingNode()).isTrue();
        behavior.with("specialistAssistance").put("enabled", true);
        behavior.withArray("executionExtensions").add("SPECIALIST_ASSISTANCE");
        assertThatThrownBy(() -> new SpecialistAssistanceCompiler(mapper).compileBehavior(behavior,
            new SpecialistAssistanceCompiler.Authority(java.util.Map.of(), Set.of())))
            .hasMessageContaining("source not declared");
    }

    @Test
    void approvedIdentityProfilesCoverExactCompiledScopesWithoutInternalLoomaiAuthority() throws Exception {
        JsonNode generic = new SpecialistAssistanceCompiler(mapper).compileBehavior(baseBehavior(),
            new SpecialistAssistanceCompiler.Authority(java.util.Map.of(), Set.of("document")))
            .at("/specialistAssistance/compiled");
        JsonNode acme = compileVariant("acme-cloud", "manifests/acme-cloud-support-reads.1.0.1.manifest.json", "fixtures/requests/acme-reads-install.json");
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
            for (JsonNode action : read("manifests/" + manifest + ".1.0.1.manifest.json").at("/contributions/actions")) {
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
                .put("releaseChannel", entry.path("releaseChannel").asText("EARLY_ACCESS"));
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
        for (JsonNode resource : compiled.at("/specialistAssistance/compiled/resources")) {
            if ("Specialist".equals(resource.path("kind").asText())) {
                assertThat(resource.at("/spec/mode").asText()).isEqualTo(
                    resource.at("/metadata/name").asText().startsWith("assist-manager-") ? "resolver" : "navigator_deep");
            }
        }
    }

    @Test
    void bothVariantsCompileTheirOwnInstalledReadToolsAndNeverInheritEachOthersAuthority() throws Exception {
        var loomai = compileVariant("loomai", "manifests/loomai-support-diagnostics.1.0.1.manifest.json", "fixtures/requests/loomai-diagnostics-install.json");
        var acme = compileVariant("acme-cloud", "manifests/acme-cloud-support-reads.1.0.1.manifest.json", "fixtures/requests/acme-reads-install.json");
        assertThat(loomai.toString()).contains("support_get_engage_readiness", "support:diagnostics:read");
        assertThat(acme.toString()).contains("acme_get_workspace_status", "acme:support:read")
            .doesNotContain("support_get_", "support:diagnostics:read", "LOOMAI_SUPPORT_DIAGNOSTIC_SERVICE_KEY");
        for (var variant : java.util.Map.of("loomai", loomai, "acme-cloud", acme).entrySet()) {
            JsonNode compiled = variant.getValue();
            var readsByWorker = new java.util.HashMap<String, JsonNode>();
            for (JsonNode area : read("overlays/" + variant.getKey() + "/specialist-assistance.json").path("areas")) {
                readsByWorker.put("assist-area-" + area.path("id").asText(), area.path("readActions"));
            }
            assertThat(compiled.path("invocations")).hasSize(3);
            int workers = 0;
            for (JsonNode resource : compiled.path("resources")) {
                if ("Specialist".equals(resource.path("kind").asText())) {
                    boolean manager = resource.at("/metadata/name").asText().startsWith("assist-manager-");
                    assertThat(resource.at("/spec/mode").asText()).isEqualTo(manager ? "resolver" : "executor");
                    assertThat(resource.at("/spec/execution/writePolicy").asText()).isEqualTo("DISABLED");
                    assertThat(resource.at("/spec/capabilities/actions/proposableWrites")).isEmpty();
                    assertThat(resource.at("/spec/capabilities/actions/visible"))
                        .isEqualTo(resource.at("/spec/capabilities/actions/requestableReads"));
                    if (!manager) {
                        workers++;
                        assertThat(resource.at("/spec/capabilities/actions/requestableReads"))
                            .isEqualTo(readsByWorker.get(resource.at("/metadata/name").asText()));
                    }
                }
            }
            assertThat(workers).isEqualTo(2);
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
        var bootstrap = mapper.treeToValue(read("fixtures/requests/bootstrap.json"), CreateMarketplaceTemplateBootstrapRequest.class);
        assertThat(com.ai.fabric.platform.backend.deployment.service.ManagedDeploymentProfileCatalog.supportsVectorProvisioningMode(
            "lucene", bootstrap.vectorProvisioningMode())).isTrue();
        mapper.treeToValue(read("fixtures/requests/document-install-update.json"), UpdateDeploymentMarketplaceInstallRequest.class);
        mapper.treeToValue(read("fixtures/requests/desk-enable.json"), DeskActivationService.EnableRequest.class);
        for (String file : List.of("loomai-diagnostics-install", "acme-reads-install")) {
            var install = mapper.treeToValue(read("fixtures/requests/" + file + ".json"), CreateDeploymentMarketplaceInstallRequest.class);
            assertThat(install.pluginVersion()).isEqualTo("1.0.1");
        }
        for (String file : List.of("loomai-support-diagnostics", "acme-cloud-support-reads")) {
            for (JsonNode action : read("manifests/" + file + ".1.0.1.manifest.json").at("/contributions/actions")) {
                assertThat(action.path("adapterType").asText()).isEqualTo("connector-http");
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

    @Test
    void correctionsPreservePublishedActionSnapshotsAndReusableStorageTemplateBytes() throws Exception {
        var expected = java.util.Map.of(
            "loomai-support-diagnostics", "ce5e2992b539377d6222b9099ef79c1fc56c28c75cba780a8d7bbaac66230c0b",
            "acme-cloud-support-reads", "5a4bf545bce8ca6f5d8f6cebfa19485f85e3be2cb74a4d2cf36d9c6e237ccf53",
            "ai-support-workspace", "9dda2702d07b1a186e91bc9449959aa9eb51055b346666728cab487b4417403f",
            "support-documents", "9f7c3bd38afe3b3e70376c1df794c2d4e5069e3b39507174d6e1ec14c6f95cd2");
        for (var entry : expected.entrySet()) {
            byte[] bytes = Files.readAllBytes(root.resolve("manifests/" + entry.getKey() + ".manifest.json"));
            assertThat(java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)))
                .isEqualTo(entry.getValue());
        }
        for (String file : List.of("loomai-support-diagnostics", "acme-cloud-support-reads")) {
            ObjectNode corrected = (ObjectNode) read("manifests/" + file + ".1.0.1.manifest.json");
            assertThat(corrected.path("version").asText()).isEqualTo("1.0.1");
            corrected.put("version", "1.0.0");
            for (JsonNode action : corrected.at("/contributions/actions")) ((ObjectNode) action).remove("adapterType");
            assertThat(corrected).isEqualTo(read("manifests/" + file + ".manifest.json"));
        }
        var publicationHistory = java.util.Map.of(
            "manifests/ai-support-workspace.manifest.json", "PUBLISHED_BOOTSTRAP_PREREQUISITE_DEFECT_RECORDED",
            "manifests/loomai-support-diagnostics.manifest.json", "NOT_FOUND_STAGING_404_CONFIRMED_BY_MAIN",
            "manifests/acme-cloud-support-reads.manifest.json", "PUBLISHED_REPORTED_BY_COORDINATOR");
        assertThat(read("catalog.json").path("immutableHistory")).hasSize(publicationHistory.size());
        for (JsonNode history : read("catalog.json").path("immutableHistory")) {
            assertThat(history.path("stagingPublication").asText()).isEqualTo(
                publicationHistory.get(history.path("manifest").asText()));
            assertThat(history.path("republish").asBoolean()).isFalse();
        }
    }

    @Test
    void mountedTemplatesBootstrapOnlyTheirOwnCorrectedActionAndDeferAssistanceUntilSourcesAreConfigured() throws Exception {
        for (String variant : List.of("loomai", "acme")) {
            JsonNode manifest = read("manifests/" + variant + "-support-mounted-mechanics.manifest.json");
            var parsed = manifests.parseAndValidate(plugin(manifest), version(manifest));
            String action = "loomai".equals(variant) ? "mkp-action-loomai-support-diagnostics" : "mkp-action-acme-cloud-support-reads";
            assertThat(parsed.contributions().templateRequiredPluginRefs()).containsExactly(
                "mkp-data-document-knowledge-mounted-demo@1.0.0", action + "@1.0.1");
            JsonNode template = manifest.at("/contributions/template");
            assertThat(template.path("requiredPluginInstallDefaults").size()).isEqualTo(1);
            assertThat(template.path("requiredPluginInstallDefaults").path(action + "@1.0.1"))
                .isEqualTo(mapper.readTree("{\"config\":{},\"secretRefs\":{}}"));
            assertThat(template.at("/deploymentBehavior/defaultExecutionExtensions")).isEmpty();
            assertThat(parsed.contributions().templateDeploymentBehaviorType()).isEqualTo("CONVERSATIONAL");
            assertThat(manifest.path("description").asText()).contains("staging mechanics only", "qualification");
            assertThat(manifest.toString()).doesNotContain("mkp-data-ai-support-documents", "MANAGED_", "databaseUrl");
        }
    }

    @Test
    void correctedActionsPassActualExternalProfilePolicyWhilePublishedLegacySnapshotsDoNot() throws Exception {
        for (String file : List.of("loomai-support-diagnostics", "acme-cloud-support-reads")) {
            var plugins = mock(MarketplacePluginRepository.class);
            var versions = mock(MarketplacePluginVersionRepository.class);
            var codec = new CustomerOfferCodec(mapper);
            var policy = new ExternalIntegrationPolicy(plugins, versions, manifests, codec, new ExternalIntegrationEndpointPolicy());
            for (String suffix : List.of("", ".1.0.1")) {
                JsonNode manifest = read("manifests/" + file + suffix + ".manifest.json");
                var plugin = plugin(manifest); plugin.setStatus("ACTIVE");
                var version = version(manifest); version.setStatus("PUBLISHED");
                when(plugins.findById(plugin.getId())).thenReturn(Optional.of(plugin));
                when(versions.findById(version.getId())).thenReturn(Optional.of(version));
                Set<String> endpoints = new java.util.TreeSet<>(), secrets = new java.util.TreeSet<>();
                for (JsonNode field : manifest.path("installForm")) {
                    ("url".equals(field.path("type").asText()) ? endpoints : secrets).add(field.path("id").asText());
                }
                var profile = new ExternalIntegrationContracts.Profile(ExternalIntegrationContracts.PROFILE_SCHEMA,
                    "mechanics-profile", "support-reads", plugin.getId(), version.getId(), codec.hash(manifest),
                    Set.of("target-stage"), endpoints, secrets, endpoints, Set.of(), Set.of("fixture.example.com"));
                if (suffix.isEmpty()) assertThatThrownBy(() -> policy.validateProfile(profile)).hasMessageContaining("UNREVIEWED_EXECUTION_ENDPOINT");
                else policy.validateProfile(profile);
            }
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
        install.setPluginId(manifest.path("pluginId").asText()); install.setPluginVersionId(version(manifest).getId());
        install.setStatus("ENABLED"); return install;
    }
    private MarketplacePluginEntity plugin(JsonNode manifest) {
        var plugin = new MarketplacePluginEntity(); plugin.setId(manifest.path("pluginId").asText());
        plugin.setPluginType(manifest.path("pluginType").asText()); return plugin;
    }
    private MarketplacePluginVersionEntity version(JsonNode manifest) {
        var version = new MarketplacePluginVersionEntity(); version.setId(manifest.path("pluginId").asText() + "-" + manifest.path("version").asText());
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
