package com.ai.fabric.platform.backend.marketplace.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferDeploymentRepository;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferRepository;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerOfferCodec;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentDraftResponse;
import com.ai.fabric.platform.backend.deployment.model.DeploymentSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentTemplateSummary;
import com.ai.fabric.platform.backend.deployment.model.UpdateDeploymentDraftRequest;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.service.DeploymentService;
import com.ai.fabric.platform.backend.externalintegration.ExternalIntegrationCompilerResolver;
import com.ai.fabric.platform.backend.externalintegration.ExternalIntegrationCompositionService;
import com.ai.fabric.platform.backend.externalintegration.ExternalIntegrationContracts.Profile;
import com.ai.fabric.platform.backend.externalintegration.ExternalIntegrationInstallInitializer;
import com.ai.fabric.platform.backend.externalintegration.ExternalIntegrationPolicy;
import com.ai.fabric.platform.backend.marketplace.entity.DeploymentMarketplacePluginInstallEntity;
import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginEntity;
import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginVersionEntity;
import com.ai.fabric.platform.backend.marketplace.model.CreateDeploymentMarketplaceInstallRequest;
import com.ai.fabric.platform.backend.marketplace.model.CreateMarketplaceTemplateBootstrapRequest;
import com.ai.fabric.platform.backend.marketplace.repository.DeploymentMarketplacePluginInstallRepository;
import com.ai.fabric.platform.backend.marketplace.service.DeploymentMarketplaceDraftCompilerService;
import com.ai.fabric.platform.backend.marketplace.service.DeploymentMarketplaceInstallService;
import com.ai.fabric.platform.backend.marketplace.service.MarketplaceCatalogService;
import com.ai.fabric.platform.backend.marketplace.service.MarketplaceManifestService;
import com.ai.fabric.platform.backend.marketplace.service.MarketplaceTemplateBootstrapService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LoomaiSupportExperienceBootstrapTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test void normalConfigurationBootstrapRequestsTheExactReviewedDependenciesWithoutBindingDefaults() throws Exception {
        JsonNode manifest = manifest();
        JsonNode contribution = manifest.at("/contributions/template");
        var plugin = new MarketplacePluginEntity();
        plugin.setId(manifest.path("pluginId").asText()); plugin.setPluginType("TEMPLATE"); plugin.setStatus("ACTIVE");
        var version = new MarketplacePluginVersionEntity();
        version.setId("internal-template-version"); version.setPluginId(plugin.getId()); version.setVersion("1.1.0");
        version.setStatus("PUBLISHED"); version.setManifestJson(manifest.toString());
        var catalog = mock(MarketplaceCatalogService.class);
        when(catalog.requirePluginEntity(plugin.getId())).thenReturn(plugin);
        when(catalog.requirePluginVersionEntity(plugin.getId(), "1.1.0")).thenReturn(version);
        var deployments = mock(DeploymentService.class);
        when(deployments.listTemplates()).thenReturn(List.of(new DeploymentTemplateSummary(
            "custom-start-from-scratch", "Reviewed", "", "openai", "openai", "qdrant", "runtime", "connector",
            true, "REQUIRED", "")));
        var summary = mock(DeploymentSummary.class);
        when(summary.id()).thenReturn("owned-deployment");
        when(deployments.createDeploymentConfiguration(any())).thenReturn(summary);
        var draft = mock(DeploymentDraftResponse.class);
        when(draft.id()).thenReturn("owned-draft");
        when(deployments.getActiveDraftForDeployment(summary.id())).thenReturn(draft);
        var compiler = mock(DeploymentMarketplaceDraftCompilerService.class);
        when(compiler.compileTemplateProviderBaseline(eq(plugin), eq(version), any()))
            .thenReturn((ObjectNode) contribution.path("providerConfig"));
        when(compiler.compileTemplatePromptBaseline(eq(plugin), eq(version), any()))
            .thenReturn((ObjectNode) contribution.path("promptConfig"));
        when(compiler.compileTemplateSecurityBaseline(eq(plugin), eq(version), any()))
            .thenReturn(mapper.createObjectNode());
        var entity = new DeploymentEntity(); entity.setId(summary.id());
        var repository = mock(DeploymentRepository.class);
        when(repository.findById(entity.getId())).thenReturn(Optional.of(entity));
        var installs = mock(DeploymentMarketplaceInstallService.class);
        var installRepository = mock(DeploymentMarketplacePluginInstallRepository.class);
        var service = new MarketplaceTemplateBootstrapService(catalog, new MarketplaceManifestService(mapper), compiler,
            installs, installRepository, repository, deployments, mock(PlatformAuditService.class), mapper);

        assertThat(service.bootstrapConfiguration(plugin.getId(), new CreateMarketplaceTemplateBootstrapRequest(
            "1.1.0", "LoomAI Support", "staging", null, "PLATFORM_MANAGED", "owned-customer", "owned-tenant")))
            .isSameAs(summary);

        var requests = ArgumentCaptor.forClass(CreateDeploymentMarketplaceInstallRequest.class);
        verify(installs, times(5)).createInstallForTrustedTemplateBootstrap(eq(entity), requests.capture());
        assertThat(requests.getAllValues()).extracting(request -> request.pluginId() + "@" + request.pluginVersion())
            .containsExactlyElementsOf(mapper.convertValue(contribution.path("requiredPluginRefs"),
                new com.fasterxml.jackson.core.type.TypeReference<List<String>>() { }));
        assertThat(requests.getAllValues()).allSatisfy(request -> {
            assertThat(request.config()).isEmpty();
            assertThat(request.secretRefs()).isEmpty();
        });
        var update = ArgumentCaptor.forClass(UpdateDeploymentDraftRequest.class);
        verify(deployments).updateDraft(eq("owned-draft"), update.capture());
        assertThat(update.getValue().providerConfig()).isEqualTo(contribution.path("providerConfig"));
        assertThat(update.getValue().promptConfig()).isEqualTo(contribution.path("promptConfig"));
        assertThat(update.getValue().promptConfig().path("answerGenerationPrompt").asText()).isNotBlank();
        assertThat(update.getValue().promptConfig().path("retrievalPrompt").asText()).isNotBlank();
        assertThat(update.getValue().promptConfig().path("assistantUiPrompt").asText()).isNotBlank();
        verify(compiler).syncDeploymentDraftForTrustedTemplateBootstrap(entity.getId());
        verify(deployments).captureTemplateDefaultsForTrustedBootstrap(entity.getId());
        verify(deployments, never()).createDeployment(any());
    }

    @Test void existingExternalInitializerCanBindBothActionsWithoutTransitioningNativeDeskAliases() {
        var ticket = profile("tickets", "mkp-action-support-tickets", "ticket-version",
            Set.of("ticketCommandsUrl"), Set.of("ticketConnectorKey"));
        var diagnostics = profile("diagnostics", "mkp-action-loomai-support-diagnostics", "diagnostic-version",
            Set.of("caseSummaryUrl", "connectionSyncUrl", "deploymentStatusUrl", "engageReadinessUrl"),
            Set.of("diagnosticServiceKey"));
        var profiles = List.of(ticket, diagnostics);
        var repository = mock(DeploymentMarketplacePluginInstallRepository.class);
        var compiler = new ExternalIntegrationCompilerResolver(mapper);
        var composition = spy(new ExternalIntegrationCompositionService(mock(CustomerOfferDeploymentRepository.class),
            mock(CustomerOfferRepository.class), mock(ExternalIntegrationPolicy.class), compiler,
            new CustomerOfferCodec(mapper), mapper, repository));
        var deployment = new DeploymentEntity(); deployment.setId("owned-deployment");
        doReturn(profiles).when(composition).bootstrapProfiles(deployment);
        var current = profiles.stream().map(profile -> {
            var install = new DeploymentMarketplacePluginInstallEntity();
            install.setId(profile.id()); install.setDeploymentId(deployment.getId()); install.setPluginId(profile.pluginId());
            install.setPluginVersionId(profile.pluginVersionId()); install.setStatus("DISABLED");
            install.setConfigJson("{}"); install.setSecretRefsJson("{}");
            return install;
        }).toList();
        when(repository.findByDeploymentIdOrderByUpdatedAtDesc(deployment.getId())).thenReturn(current);
        var installation = mock(DeploymentMarketplaceInstallService.class);

        new ExternalIntegrationInstallInitializer(composition, repository, installation, mapper).initialize(deployment);

        for (var profile : profiles) {
            verify(installation).updateInstallForTrustedCallerWithoutDraftSync(eq(deployment), eq(profile.id()), argThat(request -> {
                assertThat(request.status()).isEqualTo("ENABLED");
                assertThat(request.pluginVersion()).isNull();
                assertThat(request.config().size()).isEqualTo(profile.configFields().size());
                profile.configFields().forEach(field -> assertThat(request.config().path(field).asText())
                    .isEqualTo("${" + ExternalIntegrationCompilerResolver.alias(profile, field) + "}"));
                profile.secretFields().forEach(field -> assertThat(request.secretRefs().path(field).asText())
                    .isEqualTo(ExternalIntegrationCompilerResolver.alias(profile, field)));
                assertThat(request.config().toString()).doesNotContain("MANAGED_DESK_TICKET", "https://");
                return true;
            }));
        }
        verifyNoMoreInteractions(installation);
        assertThat(current).allSatisfy(install -> {
            assertThat(install.getStatus()).isEqualTo("DISABLED");
            assertThat(install.getConfigJson()).isEqualTo("{}");
            assertThat(install.getSecretRefsJson()).isEqualTo("{}");
        });
    }

    private Profile profile(String slot, String plugin, String version, Set<String> config, Set<String> secrets) {
        return new Profile("loomai-external-integration-profile/v1", slot, slot, plugin, version, "a".repeat(64),
            Set.of("owned-target"), config, secrets, config, Set.of(), Set.of("console.example.test"));
    }

    private JsonNode manifest() throws Exception {
        String configured = System.getProperty("support.template.root");
        for (Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath(); dir != null; dir = dir.getParent()) {
            Path root = configured == null ? dir.resolve("product-templates/ai-support-workspace") : Path.of(configured);
            Path file = root.resolve("manifests/loomai-support-workspace.1.1.0.manifest.json");
            if (Files.isRegularFile(file)) return mapper.readTree(Files.readString(file));
        }
        throw new IllegalStateException("Support instance template not found");
    }
}
