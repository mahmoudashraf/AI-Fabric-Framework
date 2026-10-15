package com.ai.fabric.platform.backend.marketplace.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentDraftResponse;
import com.ai.fabric.platform.backend.deployment.model.DeploymentSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentTemplateSummary;
import com.ai.fabric.platform.backend.deployment.model.UpdateDeploymentDraftRequest;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.service.DeploymentService;
import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginEntity;
import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginVersionEntity;
import com.ai.fabric.platform.backend.marketplace.model.CreateDeploymentMarketplaceInstallRequest;
import com.ai.fabric.platform.backend.marketplace.model.CreateMarketplaceTemplateBootstrapRequest;
import com.ai.fabric.platform.backend.marketplace.repository.DeploymentMarketplacePluginInstallRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class MarketplaceTemplateBootstrapServiceTest {
    private final MarketplaceCatalogService catalog = mock(MarketplaceCatalogService.class);
    private final MarketplaceManifestService manifests = mock(MarketplaceManifestService.class);
    private final DeploymentMarketplaceDraftCompilerService compiler = mock(DeploymentMarketplaceDraftCompilerService.class);
    private final DeploymentMarketplaceInstallService installs = mock(DeploymentMarketplaceInstallService.class);
    private final DeploymentMarketplacePluginInstallRepository installRepository = mock(DeploymentMarketplacePluginInstallRepository.class);
    private final DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
    private final DeploymentService deployments = mock(DeploymentService.class);
    private final PlatformAuditService audit = mock(PlatformAuditService.class);
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void reviewedTemplateInstallDefaultsReachTheExactPinnedDependency() throws Exception {
        MarketplacePluginEntity plugin = new MarketplacePluginEntity();
        plugin.setId("template-plugin");
        plugin.setPluginType("TEMPLATE");
        MarketplacePluginVersionEntity version = new MarketplacePluginVersionEntity();
        version.setId("template-version");
        version.setPluginId(plugin.getId());
        version.setVersion("1.0.0");
        var manifest = mapper.readTree("""
            {
              "contributions": {"template": {
                "templateId": "reviewed-base",
                "requiredPluginRefs": ["data-plugin@1.2.3"],
                "requiredPluginInstallDefaults": {
                  "data-plugin@1.2.3": {
                    "config": {"scopeId": "${MANAGED_SCOPE_ID}"},
                    "secretRefs": {"providerKey": "MANAGED_PROVIDER_KEY"}
                  }
                },
                "providerConfig": {
                  "vectorStrategy": "milvus",
                  "vectorProvisioningMode": "PLATFORM_MANAGED"
                }
              }}
            }
            """);
        var parsed = mock(MarketplaceManifestService.ParsedMarketplaceManifest.class, RETURNS_DEEP_STUBS);
        when(parsed.pluginType()).thenReturn("TEMPLATE");
        when(parsed.manifest()).thenReturn(manifest);
        when(parsed.contributions().templateRequiredPluginRefs()).thenReturn(List.of("data-plugin@1.2.3"));
        when(parsed.contributions().templateDeploymentBehaviorType()).thenReturn("CONVERSATIONAL");
        when(parsed.compatibility().supportedDeploymentTargets()).thenReturn(List.of());
        when(parsed.compatibility().supportedProviderModes()).thenReturn(List.of());
        when(catalog.requirePluginEntity(plugin.getId())).thenReturn(plugin);
        when(catalog.requirePluginVersionEntity(plugin.getId(), version.getVersion())).thenReturn(version);
        when(manifests.parseAndValidate(plugin, version)).thenReturn(parsed);
        when(deployments.listTemplates()).thenReturn(List.of(new DeploymentTemplateSummary(
            "reviewed-base", "Reviewed", "", "openai", "openai", "qdrant", "runtime", "connector",
            true, "REQUIRED", ""
        )));
        DeploymentSummary deployment = mock(DeploymentSummary.class);
        when(deployment.id()).thenReturn("deployment");
        when(deployments.createDeploymentConfiguration(any())).thenReturn(deployment);
        DeploymentDraftResponse draft = mock(DeploymentDraftResponse.class);
        when(draft.id()).thenReturn("draft");
        when(draft.providerConfig()).thenReturn(mapper.createObjectNode());
        when(draft.securityConfig()).thenReturn(mapper.createObjectNode());
        when(deployments.getActiveDraftForDeployment("deployment")).thenReturn(draft);
        var providerBaseline = mapper.createObjectNode()
            .put("vectorStrategy", "milvus")
            .put("vectorProvisioningMode", "PLATFORM_MANAGED");
        when(compiler.compileTemplateProviderBaseline(eq(plugin), eq(version), any()))
            .thenReturn(providerBaseline);
        when(compiler.compileTemplateSecurityBaseline(eq(plugin), eq(version), any()))
            .thenReturn(mapper.createObjectNode());
        DeploymentEntity entity = new DeploymentEntity();
        entity.setId("deployment");
        when(deploymentRepository.findById("deployment")).thenReturn(Optional.of(entity));
        when(installRepository.findByDeploymentIdAndPluginId("deployment", plugin.getId()))
            .thenReturn(Optional.empty());

        var service = new MarketplaceTemplateBootstrapService(catalog, manifests, compiler, installs,
            installRepository, deploymentRepository, deployments, audit, mapper);
        service.bootstrapConfiguration(plugin.getId(), new CreateMarketplaceTemplateBootstrapRequest(
            "1.0.0", "Deployment", "staging", null, "REQUIRED", "customer", "tenant"
        ));

        var request = ArgumentCaptor.forClass(CreateDeploymentMarketplaceInstallRequest.class);
        verify(installs).createInstallForTrustedTemplateBootstrap(eq(entity), request.capture());
        assertThat(request.getValue().pluginId()).isEqualTo("data-plugin");
        assertThat(request.getValue().pluginVersion()).isEqualTo("1.2.3");
        assertThat(request.getValue().config().path("scopeId").asText()).isEqualTo("${MANAGED_SCOPE_ID}");
        assertThat(request.getValue().secretRefs().path("providerKey").asText()).isEqualTo("MANAGED_PROVIDER_KEY");
        var draftUpdate = ArgumentCaptor.forClass(UpdateDeploymentDraftRequest.class);
        verify(deployments).updateDraft(eq("draft"), draftUpdate.capture());
        assertThat(draftUpdate.getValue().providerConfig()).isEqualTo(providerBaseline);
        verify(compiler).syncDeploymentDraftForTrustedTemplateBootstrap("deployment");
    }
}
