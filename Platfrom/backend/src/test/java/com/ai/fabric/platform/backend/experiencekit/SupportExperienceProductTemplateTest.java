package com.ai.fabric.platform.backend.experiencekit;

import com.ai.fabric.platform.backend.aiworkspace.service.AIWorkspaceConfigurationValidator;
import com.ai.fabric.platform.backend.config.PlatformAIWorkspaceProperties;
import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginEntity;
import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginVersionEntity;
import com.ai.fabric.platform.backend.marketplace.service.MarketplaceManifestService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class SupportExperienceProductTemplateTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final Path root = root();

    @Test void newContributionsUseActualTemplateActionAndIntegrationPrimitives() throws Exception {
        for (String file : List.of("ai-support-workspace.1.1.0", "loomai-support-workspace.1.1.0", "support-tickets.1.0.0", "loomai-desk-support-channel.1.0.1")) {
            var manifest = read("manifests/" + file + ".manifest.json");
            var plugin = new MarketplacePluginEntity();
            plugin.setId(manifest.path("pluginId").asText()); plugin.setPluginType(manifest.path("pluginType").asText());
            var version = new MarketplacePluginVersionEntity();
            version.setId(file); version.setPluginId(plugin.getId()); version.setVersion(manifest.path("version").asText());
            version.setManifestJson(manifest.toString());
            var parsed = new MarketplaceManifestService(mapper).parseAndValidate(plugin, version);
            assertThat(parsed.pluginType()).isEqualTo(plugin.getPluginType());
            if ("TEMPLATE".equals(parsed.pluginType()))
                assertThat(parsed.contributions().templateRequiredRuntimeCapabilityIds())
                    .contains("standalone-conversation-control");
        }
    }

    @Test void templatePreservesDocumentDependencyOptionalSpecialistsAndEditableProviderChoices() throws Exception {
        var old = read("manifests/ai-support-workspace.1.0.1.manifest.json").at("/contributions/template");
        var template = read("manifests/ai-support-workspace.1.1.0.manifest.json").at("/contributions/template");
        assertThat(template.path("requiredPluginRefs")).containsAll(() -> old.path("requiredPluginRefs").elements());
        assertThat(template.path("requiredPluginRefs").toString()).contains("mkp-action-support-tickets@1.0.0");
        assertThat(template.at("/deploymentBehavior/specialistAssistance")).isEqualTo(old.at("/deploymentBehavior/specialistAssistance"));
        assertThat(template.at("/deploymentBehavior/defaultExecutionExtensions")).isEmpty();
        assertThat(template.at("/providerConfig/canonicalConversationsEnabled").asBoolean()).isTrue();
        assertThat(template.at("/providerConfig/openaiModel").asText()).isEqualTo("gpt-5.4-mini");
        assertThat(template.at("/providerConfig/orchestrationModeOverrides/executor/readActionResolution/maxIterations").asInt()).isEqualTo(2);
        assertThat(template.at("/promptConfig/ragMaxDocumentsUsedForContext").asInt()).isEqualTo(8);
        assertThat(template.at("/promptConfig/ragMaxContextChars").asInt()).isEqualTo(8000);
        assertThat(template.at("/requiredPluginInstallDefaults/mkp-action-support-tickets@1.0.0/config/ticketCommandsUrl").asText())
            .isEqualTo("${MANAGED_DESK_TICKET_COMMANDS_URL}");
        assertThat(template.at("/requiredPluginInstallDefaults/mkp-action-support-tickets@1.0.0/secretRefs/ticketConnectorKey").asText())
            .isEqualTo("MANAGED_DESK_TICKET_CONNECTOR_KEY");
        assertThat(template.toString()).doesNotContain("loomai-support-diagnostics", "s3://", "service_role", "openaiApiKey");
    }

    @Test void canonicalConversationTemplatePreferenceMustBeAnExplicitBoolean() throws Exception {
        var manifest = read("manifests/ai-support-workspace.1.1.0.manifest.json");
        ((com.fasterxml.jackson.databind.node.ObjectNode) manifest.at("/contributions/template/providerConfig"))
            .put("canonicalConversationsEnabled", "true");
        var plugin = new MarketplacePluginEntity();
        plugin.setId(manifest.path("pluginId").asText()); plugin.setPluginType("TEMPLATE");
        var version = new MarketplacePluginVersionEntity();
        version.setPluginId(plugin.getId()); version.setVersion("1.1.0"); version.setManifestJson(manifest.toString());
        assertThatThrownBy(() -> new MarketplaceManifestService(mapper).parseAndValidate(plugin, version))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
            .hasMessageContaining("canonicalConversationsEnabled must be a boolean");
    }

    @Test void supportExperienceCandidatesCannotReplaceAcceptedCatalogueDefaults() throws Exception {
        var catalogue = read("catalog.json");
        assertThat(catalogue.path("entries").get(1).path("manifest").asText())
            .isEqualTo("manifests/ai-support-workspace.1.0.1.manifest.json");
        assertThat(catalogue.path("supportExperienceCandidates")).hasSize(4);
        for (JsonNode candidate : catalogue.path("supportExperienceCandidates")) {
            assertThat(candidate.path("publicationScope").asText()).isEqualTo("SOURCE_ONLY");
            assertThat(candidate.path("productionDefaultAccepted").asBoolean()).isFalse();
        }
    }

    @Test void ticketActionRequiresConfirmationAndNeverAcceptsModelProvidedAuthority() throws Exception {
        var actions = read("manifests/support-tickets.1.0.0.manifest.json").at("/contributions/actions");
        assertThat(actions).hasSize(3);
        assertThat(actions.get(0).path("confirmationRequired").asBoolean()).isTrue();
        assertThat(actions.get(0).path("readOnly").asBoolean()).isFalse();
        for (JsonNode action : actions) {
            assertThat(action.path("anonymousAllowed").asBoolean()).isFalse();
            for (JsonNode param : action.path("params")) assertThat(param.path("name").asText())
                .isIn("subject", "details", "topic", "requestId");
            assertThat(action.at("/route/managedUrlField").asText()).isEqualTo("ticketCommandsUrl");
            assertThat(action.at("/route/request/body").has("trace")).isFalse();
            assertThat(action.at("/route/request/body").has("requesterId")).isFalse();
            assertThat(action.at("/route/request/body").has("conversationId")).isFalse();
            assertThat(action.at("/route/url").isMissingNode()).isTrue();
        }
        assertThat(actions.get(0).at("/route/idempotencyHeader").asText()).isEqualTo("Idempotency-Key");
        assertThat(actions.get(1).path("readOnly").asBoolean()).isTrue();
        assertThat(actions.get(2).path("readOnly").asBoolean()).isTrue();
    }

    @Test void neutralPackFixturePassesTheRealValidatorAndHasNoBindingsOrInternalDiagnostics() throws Exception {
        var config = read("fixtures/support-experience-config.json");
        new AIWorkspaceConfigurationValidator(new PlatformAIWorkspaceProperties(Path.of("."), false, 120,
            Duration.ofSeconds(60), null, null, "apps.example.test", "https://workspace.example.test"))
            .validateSupportConfiguration(config);
        assertThat(config.at("/capabilities/tickets").asBoolean()).isFalse();
        assertThat(config.at("/capabilities/handover").asBoolean()).isFalse();
        assertThat(config.path("support").has("spaceId")).isFalse();
        assertThat(config.toString()).doesNotContain("loomai-support-diagnostics", "secretRef", "requesterId", "s3://");
    }

    @Test void sourceOfferHelperMatchesRealWorkspaceProfileAndHashContracts() throws Exception {
        String module = mapper.writeValueAsString(root.resolve("scripts/build-support-offer.mjs").toUri().toString());
        String script = "import {buildSupportWorkspaceProfile,supportOfferHash} from " + module + ";"
            + "const profile=buildSupportWorkspaceProfile({authentication:{projectUrl:'https://auth.example.test',"
            + "publishableKey:'sb_publishable_fixture_key',methods:['magic-link']}});"
            + "console.log(JSON.stringify({workspaceProfiles:[profile],settings:"
            + "[['INTERACTION_PROFILE_ID','workspace',profile.interaction],['THEME_PROFILE_ID','theme',profile.theme],"
            + "['AUTH_PROFILE_ID','auth',profile.authentication]].map(([setting,prefix,value])=>"
            + "({setting,type:'CHOICE',maxLength:100,allowedValues:[prefix+':'+supportOfferHash(value)]}))}));";
        var process = new ProcessBuilder("node", "--input-type=module", "-e", script).redirectErrorStream(true).start();
        assertThat(process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(process.exitValue()).as(output).isZero();
        var definition = mapper.readValue(output, com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Definition.class);
        var properties = new PlatformAIWorkspaceProperties(Path.of("."), false, 120, Duration.ofSeconds(60), null, null,
            "apps.example.test", "https://workspace.example.test");
        var configuration = new AIWorkspaceConfigurationValidator(properties);
        var packs = new com.ai.fabric.platform.backend.aiworkspace.service.AIWorkspaceExperiencePackRegistry(
            org.mockito.Mockito.mock(com.ai.fabric.platform.backend.aiworkspace.service.AIWorkspaceAssetCatalogService.class), configuration);
        new com.ai.fabric.platform.backend.customerselfservice.service.CustomerWorkspaceProfileValidator(
            new com.ai.fabric.platform.backend.customerselfservice.service.CustomerOfferCodec(mapper), configuration, packs,
            new com.ai.fabric.platform.backend.aiworkspace.service.AIWorkspaceConnectionProfileRegistry(properties),
            new com.ai.fabric.platform.backend.aiworkspace.service.AIWorkspaceAuthenticationProfileRegistry(mapper)).validate(definition);
        assertThat(definition.workspaceProfiles().getFirst().interaction().path("packCode").asText()).isEqualTo("support");
    }

    private JsonNode read(String file) throws Exception { return mapper.readTree(Files.readString(root.resolve(file))); }
    private static Path root() {
        String configured = System.getProperty("support.template.root");
        if (configured != null) return Path.of(configured);
        for (Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath(); dir != null; dir = dir.getParent()) {
            Path candidate = dir.resolve("product-templates/ai-support-workspace");
            if (Files.isDirectory(candidate)) return candidate;
        }
        throw new IllegalStateException("Support template package not found.");
    }
}
