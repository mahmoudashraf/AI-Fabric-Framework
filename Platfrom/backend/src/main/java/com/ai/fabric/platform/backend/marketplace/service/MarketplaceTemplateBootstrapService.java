package com.ai.fabric.platform.backend.marketplace.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.model.CreateDeploymentRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentDraftResponse;
import com.ai.fabric.platform.backend.deployment.model.DeploymentSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentTemplateSummary;
import com.ai.fabric.platform.backend.deployment.model.UpdateDeploymentDraftRequest;
import com.ai.fabric.platform.backend.deployment.service.DeploymentService;
import com.ai.fabric.platform.backend.marketplace.entity.DeploymentMarketplacePluginInstallEntity;
import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginEntity;
import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginVersionEntity;
import com.ai.fabric.platform.backend.marketplace.model.CreateMarketplaceTemplateBootstrapRequest;
import com.ai.fabric.platform.backend.marketplace.repository.DeploymentMarketplacePluginInstallRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.springframework.http.HttpStatus.CONFLICT;

@Service
public class MarketplaceTemplateBootstrapService {

    private static final String BOOTSTRAPPED_STATUS = "BOOTSTRAPPED";
    private static final String DEFAULT_TEMPLATE_ID = "custom-start-from-scratch";

    private final MarketplaceCatalogService marketplaceCatalogService;
    private final MarketplaceManifestService marketplaceManifestService;
    private final DeploymentMarketplaceDraftCompilerService deploymentMarketplaceDraftCompilerService;
    private final DeploymentMarketplacePluginInstallRepository installRepository;
    private final DeploymentService deploymentService;
    private final PlatformAuditService platformAuditService;
    private final ObjectMapper objectMapper;

    public MarketplaceTemplateBootstrapService(MarketplaceCatalogService marketplaceCatalogService,
                                               MarketplaceManifestService marketplaceManifestService,
                                               DeploymentMarketplaceDraftCompilerService deploymentMarketplaceDraftCompilerService,
                                               DeploymentMarketplacePluginInstallRepository installRepository,
                                               DeploymentService deploymentService,
                                               PlatformAuditService platformAuditService,
                                               ObjectMapper objectMapper) {
        this.marketplaceCatalogService = marketplaceCatalogService;
        this.marketplaceManifestService = marketplaceManifestService;
        this.deploymentMarketplaceDraftCompilerService = deploymentMarketplaceDraftCompilerService;
        this.installRepository = installRepository;
        this.deploymentService = deploymentService;
        this.platformAuditService = platformAuditService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public DeploymentSummary bootstrap(String pluginIdOrSlug, CreateMarketplaceTemplateBootstrapRequest request) {
        MarketplacePluginEntity plugin = marketplaceCatalogService.requirePluginEntity(pluginIdOrSlug);
        MarketplacePluginVersionEntity version = resolveVersion(plugin, request.pluginVersion());
        MarketplaceManifestService.ParsedMarketplaceManifest parsed =
            marketplaceManifestService.parseAndValidate(plugin, version);
        if (!"TEMPLATE".equals(parsed.pluginType())) {
            throw new ResponseStatusException(CONFLICT, "Marketplace plugin is not a template plugin: " + plugin.getId());
        }
        JsonNode templateContribution = parsed.manifest().path("contributions").path("template");
        if (!templateContribution.isObject()) {
            throw new ResponseStatusException(CONFLICT, "Template plugin is missing contributions.template: " + plugin.getId());
        }

        String deploymentTemplateId = StringUtils.hasText(request.templateId())
            ? request.templateId().trim()
            : StringUtils.hasText(templateContribution.path("templateId").asText(""))
                ? templateContribution.path("templateId").asText("").trim()
                : DEFAULT_TEMPLATE_ID;
        DeploymentTemplateSummary baseTemplate = deploymentService.listTemplates().stream()
            .filter(template -> template.id().equals(deploymentTemplateId))
            .findFirst()
            .orElseThrow(() -> new ResponseStatusException(CONFLICT, "Unknown templateId for marketplace bootstrap: " + deploymentTemplateId));
        validateTemplateCompatibility(plugin, version, parsed, baseTemplate);
        String curatedModuleId = StringUtils.hasText(templateContribution.path("curatedModuleId").asText(""))
            ? templateContribution.path("curatedModuleId").asText("").trim()
            : null;

        DeploymentSummary deployment = deploymentService.createDeployment(
            new CreateDeploymentRequest(
                request.name(),
                request.environment(),
                deploymentTemplateId,
                curatedModuleId,
                request.vectorProvisioningMode(),
                request.customerId(),
                request.tenantId()
            )
        );

        DeploymentDraftResponse draft = deploymentService.getActiveDraftForDeployment(deployment.id());
        JsonNode shellConfig = deploymentMarketplaceDraftCompilerService.compileTemplateShellBaseline(
            plugin,
            version,
            draft.shellConfig()
        );
        JsonNode securityConfig = deploymentMarketplaceDraftCompilerService.compileTemplateSecurityBaseline(
            plugin,
            version,
            draft.securityConfig()
        );
        deploymentService.updateDraft(
            draft.id(),
            new UpdateDeploymentDraftRequest(
                null,
                null,
                null,
                null,
                securityConfig,
                null,
                null,
                shellConfig
            )
        );

        createBootstrapInstallRecord(deployment.id(), plugin, version);
        platformAuditService.record(
            "MARKETPLACE_TEMPLATE_BOOTSTRAPPED",
            "DEPLOYMENT",
            deployment.id(),
            Map.of(
                "pluginId", plugin.getId(),
                "pluginVersion", version.getVersion(),
                "templateId", deploymentTemplateId,
                "curatedModuleId", curatedModuleId == null ? "" : curatedModuleId
            )
        );
        return deployment;
    }

    private void createBootstrapInstallRecord(String deploymentId,
                                              MarketplacePluginEntity plugin,
                                              MarketplacePluginVersionEntity version) {
        installRepository.findByDeploymentIdAndPluginId(deploymentId, plugin.getId()).ifPresent(existing -> {
            throw new ResponseStatusException(
                CONFLICT,
                "Marketplace template plugin is already recorded for deployment " + deploymentId + ": " + plugin.getId()
            );
        });

        Instant now = Instant.now();
        DeploymentMarketplacePluginInstallEntity install = new DeploymentMarketplacePluginInstallEntity();
        install.setId("mpi-" + UUID.randomUUID().toString().substring(0, 8));
        install.setDeploymentId(deploymentId);
        install.setPluginId(plugin.getId());
        install.setPluginVersionId(version.getId());
        install.setStatus(BOOTSTRAPPED_STATUS);
        install.setConfigJson(writeJson(objectMapper.createObjectNode()));
        install.setSecretRefsJson(writeJson(objectMapper.createObjectNode()));
        install.setCreatedAt(now);
        install.setUpdatedAt(now);
        installRepository.save(install);
    }

    private MarketplacePluginVersionEntity resolveVersion(MarketplacePluginEntity plugin, String requestedVersion) {
        return StringUtils.hasText(requestedVersion)
            ? marketplaceCatalogService.requirePluginVersionEntity(plugin.getId(), requestedVersion.trim())
            : marketplaceCatalogService.requireLatestPublishedVersionEntity(plugin.getId());
    }

    private void validateTemplateCompatibility(MarketplacePluginEntity plugin,
                                               MarketplacePluginVersionEntity version,
                                               MarketplaceManifestService.ParsedMarketplaceManifest parsed,
                                               DeploymentTemplateSummary baseTemplate) {
        var compatibility = parsed.compatibility();
        if (!compatibility.supportedDeploymentTargets().isEmpty()
            && !compatibility.supportedDeploymentTargets().contains(baseTemplate.id())) {
            throw new ResponseStatusException(
                CONFLICT,
                "Marketplace template plugin " + plugin.getId()
                    + " does not support base template " + baseTemplate.id()
            );
        }
        if (!compatibility.supportedProviderModes().isEmpty()) {
            var actualModes = java.util.Map.of(
                "llm", baseTemplate.llmProvider(),
                "embedding", baseTemplate.embeddingProvider(),
                "vector", baseTemplate.vectorStrategy(),
                "runtime", baseTemplate.runtimeProfile(),
                "connector", baseTemplate.connectorProfile()
            );
            var grouped = compatibility.supportedProviderModes().stream()
                .collect(java.util.stream.Collectors.groupingBy(
                    mode -> mode.substring(0, mode.indexOf(':')),
                    java.util.LinkedHashMap::new,
                    java.util.stream.Collectors.mapping(
                        mode -> mode.substring(mode.indexOf(':') + 1),
                        java.util.stream.Collectors.toList()
                    )
                ));
            for (var entry : grouped.entrySet()) {
                String actual = actualModes.getOrDefault(entry.getKey(), "");
                if (!entry.getValue().contains(actual)) {
                    throw new ResponseStatusException(
                        CONFLICT,
                        "Marketplace template plugin " + plugin.getId()
                            + " is incompatible with base template " + baseTemplate.id()
                            + " for provider mode " + entry.getKey()
                    );
                }
            }
        }
    }

    private String writeJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to write marketplace template bootstrap install JSON.", ex);
        }
    }
}
