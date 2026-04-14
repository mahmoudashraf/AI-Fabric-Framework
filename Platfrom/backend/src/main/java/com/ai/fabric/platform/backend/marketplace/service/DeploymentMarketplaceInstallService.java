package com.ai.fabric.platform.backend.marketplace.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentDraftEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentDraftRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.deployment.service.ManagedDeploymentProfileCatalog;
import com.ai.fabric.platform.backend.deployment.service.DeploymentAccessService;
import com.ai.fabric.platform.backend.marketplace.entity.DeploymentMarketplacePluginInstallEntity;
import com.ai.fabric.platform.backend.marketplace.entity.DeploymentMarketplacePluginEntitlementEntity;
import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginEntity;
import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginVersionEntity;
import com.ai.fabric.platform.backend.marketplace.model.CreateDeploymentMarketplaceInstallRequest;
import com.ai.fabric.platform.backend.marketplace.model.DeploymentMarketplaceEntitlementSummary;
import com.ai.fabric.platform.backend.marketplace.model.DeploymentMarketplaceImpactSummary;
import com.ai.fabric.platform.backend.marketplace.model.DeploymentMarketplaceInstallImpactSummary;
import com.ai.fabric.platform.backend.marketplace.model.DeploymentMarketplaceInstallResolutionSummary;
import com.ai.fabric.platform.backend.marketplace.model.DeploymentMarketplaceInstallSummary;
import com.ai.fabric.platform.backend.marketplace.model.MarketplacePluginContributionSummary;
import com.ai.fabric.platform.backend.marketplace.model.UpdateDeploymentMarketplaceEntitlementRequest;
import com.ai.fabric.platform.backend.marketplace.model.UpdateDeploymentMarketplaceInstallRequest;
import com.ai.fabric.platform.backend.marketplace.repository.DeploymentMarketplacePluginInstallRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class DeploymentMarketplaceInstallService {

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;
    private static final String STATUS_ENABLED = "ENABLED";
    private static final String STATUS_DISABLED = "DISABLED";

    private final DeploymentRepository deploymentRepository;
    private final DeploymentDraftRepository deploymentDraftRepository;
    private final DeploymentVersionRepository deploymentVersionRepository;
    private final DeploymentAccessService deploymentAccessService;
    private final DeploymentMarketplacePluginInstallRepository installRepository;
    private final MarketplaceCatalogService marketplaceCatalogService;
    private final MarketplaceManifestService marketplaceManifestService;
    private final DeploymentMarketplaceDraftCompilerService deploymentMarketplaceDraftCompilerService;
    private final MarketplaceEntitlementService marketplaceEntitlementService;
    private final PlatformAuditService platformAuditService;
    private final ObjectMapper objectMapper;

    public DeploymentMarketplaceInstallService(DeploymentRepository deploymentRepository,
                                               DeploymentDraftRepository deploymentDraftRepository,
                                               DeploymentVersionRepository deploymentVersionRepository,
                                               DeploymentAccessService deploymentAccessService,
                                               DeploymentMarketplacePluginInstallRepository installRepository,
                                               MarketplaceCatalogService marketplaceCatalogService,
                                               MarketplaceManifestService marketplaceManifestService,
                                               DeploymentMarketplaceDraftCompilerService deploymentMarketplaceDraftCompilerService,
                                               MarketplaceEntitlementService marketplaceEntitlementService,
                                               PlatformAuditService platformAuditService,
                                               ObjectMapper objectMapper) {
        this.deploymentRepository = deploymentRepository;
        this.deploymentDraftRepository = deploymentDraftRepository;
        this.deploymentVersionRepository = deploymentVersionRepository;
        this.deploymentAccessService = deploymentAccessService;
        this.installRepository = installRepository;
        this.marketplaceCatalogService = marketplaceCatalogService;
        this.marketplaceManifestService = marketplaceManifestService;
        this.deploymentMarketplaceDraftCompilerService = deploymentMarketplaceDraftCompilerService;
        this.marketplaceEntitlementService = marketplaceEntitlementService;
        this.platformAuditService = platformAuditService;
        this.objectMapper = objectMapper;
    }

    public List<DeploymentMarketplaceInstallSummary> listInstalls(String deploymentId) {
        DeploymentEntity deployment = requireDeploymentViewer(deploymentId);
        DeploymentDraftEntity activeDraft = resolveActiveDraft(deployment);
        DeploymentVersionEntity activeVersion = resolveActiveVersion(deployment);
        return installRepository.findByDeploymentIdOrderByUpdatedAtDesc(deployment.getId()).stream()
            .map(install -> toSummary(deployment, activeDraft, activeVersion, install))
            .toList();
    }

    public DeploymentMarketplaceImpactSummary getImpact(String deploymentId) {
        DeploymentEntity deployment = requireDeploymentViewer(deploymentId);
        DeploymentDraftEntity activeDraft = resolveActiveDraft(deployment);
        return summarizeImpact(
            deployment,
            activeDraft,
            installRepository.findByDeploymentIdOrderByUpdatedAtDesc(deployment.getId())
        );
    }

    @Transactional
    public DeploymentMarketplaceInstallSummary createInstall(String deploymentId,
                                                             CreateDeploymentMarketplaceInstallRequest request) {
        DeploymentEntity deployment = requireDeploymentEditor(deploymentId);
        MarketplacePluginEntity plugin = marketplaceCatalogService.requirePluginEntity(request.pluginId());
        MarketplacePluginVersionEntity version = marketplaceCatalogService.requirePluginVersionEntity(
            plugin.getId(),
            request.pluginVersion()
        );
        validateInstallable(plugin, version, deployment.getId(), null);
        DeploymentDraftEntity activeDraft = resolveActiveDraft(deployment);
        validateInstallInputs(
            deployment,
            activeDraft,
            marketplaceManifestService.parseAndValidate(plugin, version),
            normalizeObject(request.config(), "config"),
            normalizeObject(request.secretRefs(), "secretRefs"),
            STATUS_ENABLED
        );
        installRepository.findByDeploymentIdAndPluginId(deployment.getId(), plugin.getId())
            .ifPresent(existing -> {
                throw new ResponseStatusException(
                    CONFLICT,
                    "Marketplace plugin is already installed for deployment " + deployment.getId() + ": " + plugin.getId()
                );
            });

        Instant now = Instant.now();
        DeploymentMarketplacePluginInstallEntity install = new DeploymentMarketplacePluginInstallEntity();
        install.setId("mpi-" + UUID.randomUUID().toString().substring(0, 8));
        install.setDeploymentId(deployment.getId());
        install.setPluginId(plugin.getId());
        install.setPluginVersionId(version.getId());
        install.setStatus(STATUS_ENABLED);
        install.setConfigJson(writeJson(normalizeObject(request.config(), "config")));
        install.setSecretRefsJson(writeJson(normalizeObject(request.secretRefs(), "secretRefs")));
        install.setCreatedAt(now);
        install.setUpdatedAt(now);
        installRepository.save(install);
        marketplaceEntitlementService.ensureEntitlement(
            install,
            marketplaceManifestService.parseAndValidate(plugin, version)
        );

        platformAuditService.record(
            "MARKETPLACE_INSTALL_CREATED",
            "DEPLOYMENT_MARKETPLACE_INSTALL",
            install.getId(),
            Map.of(
                "deploymentId", deployment.getId(),
                "pluginId", plugin.getId(),
                "pluginVersion", version.getVersion(),
                "status", install.getStatus()
            )
        );
        deploymentMarketplaceDraftCompilerService.syncDeploymentDraft(deployment.getId());

        return toSummary(deployment, activeDraft, resolveActiveVersion(deployment), install);
    }

    @Transactional
    public DeploymentMarketplaceInstallSummary updateInstall(String deploymentId,
                                                             String installId,
                                                             UpdateDeploymentMarketplaceInstallRequest request) {
        DeploymentEntity deployment = requireDeploymentEditor(deploymentId);
        DeploymentMarketplacePluginInstallEntity install = requireInstall(deployment.getId(), installId);
        MarketplacePluginEntity plugin = marketplaceCatalogService.requirePluginEntity(install.getPluginId());
        MarketplacePluginVersionEntity version = resolveUpdatedVersion(plugin, install, request.pluginVersion());
        validateInstallable(plugin, version, deployment.getId(), install.getId());
        DeploymentDraftEntity activeDraft = resolveActiveDraft(deployment);
        MarketplaceManifestService.ParsedMarketplaceManifest parsed =
            marketplaceManifestService.parseAndValidate(plugin, version);

        if (request.status() != null) {
            install.setStatus(normalizeStatus(request.status()));
        }
        if (request.pluginVersion() != null) {
            install.setPluginVersionId(version.getId());
        }
        if (request.config() != null) {
            install.setConfigJson(writeJson(normalizeObject(request.config(), "config")));
        }
        if (request.secretRefs() != null) {
            install.setSecretRefsJson(writeJson(normalizeObject(request.secretRefs(), "secretRefs")));
        }
        validateInstallInputs(
            deployment,
            activeDraft,
            parsed,
            readJson(install.getConfigJson()),
            readJson(install.getSecretRefsJson()),
            install.getStatus()
        );
        install.setUpdatedAt(Instant.now());
        installRepository.save(install);
        marketplaceEntitlementService.ensureEntitlement(install, parsed);

        platformAuditService.record(
            "MARKETPLACE_INSTALL_UPDATED",
            "DEPLOYMENT_MARKETPLACE_INSTALL",
            install.getId(),
            Map.of(
                "deploymentId", deployment.getId(),
                "pluginId", plugin.getId(),
                "pluginVersion", version.getVersion(),
                "status", install.getStatus()
            )
        );
        deploymentMarketplaceDraftCompilerService.syncDeploymentDraft(deployment.getId());

        return toSummary(deployment, activeDraft, resolveActiveVersion(deployment), install);
    }

    @Transactional
    public void deleteInstall(String deploymentId, String installId) {
        DeploymentEntity deployment = requireDeploymentEditor(deploymentId);
        DeploymentMarketplacePluginInstallEntity install = requireInstall(deployment.getId(), installId);
        DeploymentVersionEntity activeVersion = resolveActiveVersion(deployment);
        if (installPresentInVersion(activeVersion, install.getId())) {
            install.setStatus(STATUS_DISABLED);
            install.setUpdatedAt(Instant.now());
            installRepository.save(install);
            platformAuditService.record(
                "MARKETPLACE_INSTALL_DISABLE_FOR_REMOVAL",
                "DEPLOYMENT_MARKETPLACE_INSTALL",
                install.getId(),
                Map.of(
                    "deploymentId", deployment.getId(),
                    "pluginId", install.getPluginId(),
                    "pluginVersionId", install.getPluginVersionId(),
                    "status", install.getStatus()
                )
            );
        } else {
            installRepository.delete(install);
            platformAuditService.record(
                "MARKETPLACE_INSTALL_DELETED",
                "DEPLOYMENT_MARKETPLACE_INSTALL",
                install.getId(),
                Map.of(
                    "deploymentId", deployment.getId(),
                    "pluginId", install.getPluginId(),
                    "pluginVersionId", install.getPluginVersionId()
                )
            );
        }
        deploymentMarketplaceDraftCompilerService.syncDeploymentDraft(deployment.getId());
    }

    @Transactional
    public DeploymentMarketplaceInstallSummary updateEntitlement(String deploymentId,
                                                                 String installId,
                                                                 UpdateDeploymentMarketplaceEntitlementRequest request) {
        DeploymentEntity deployment = requireDeploymentEditor(deploymentId);
        DeploymentMarketplacePluginInstallEntity install = requireInstall(deployment.getId(), installId);
        MarketplacePluginEntity plugin = marketplaceCatalogService.requirePluginEntity(install.getPluginId());
        MarketplacePluginVersionEntity version = requirePluginVersionById(install.getPluginVersionId());
        MarketplaceManifestService.ParsedMarketplaceManifest parsed =
            marketplaceManifestService.parseAndValidate(plugin, version);
        marketplaceEntitlementService.updateEntitlement(install, parsed, request);
        platformAuditService.record(
            "MARKETPLACE_INSTALL_ENTITLEMENT_UPDATED",
            "DEPLOYMENT_MARKETPLACE_INSTALL",
            install.getId(),
            Map.of(
                "deploymentId", deployment.getId(),
                "pluginId", plugin.getId(),
                "pluginVersion", version.getVersion()
            )
        );
        deploymentMarketplaceDraftCompilerService.syncDeploymentDraft(deployment.getId());
        return toSummary(deployment, resolveActiveDraft(deployment), resolveActiveVersion(deployment), install);
    }

    public DeploymentMarketplaceInstallResolutionSummary resolveInstall(String deploymentId, String installId) {
        DeploymentEntity deployment = requireDeploymentViewer(deploymentId);
        DeploymentMarketplacePluginInstallEntity install = requireInstall(deployment.getId(), installId);
        DeploymentDraftEntity activeDraft = resolveActiveDraft(deployment);
        deploymentMarketplaceDraftCompilerService.syncDeploymentDraft(deployment.getId());
        DeploymentMarketplaceInstallSummary summary = toSummary(deployment, activeDraft, resolveActiveVersion(deployment), install);
        return new DeploymentMarketplaceInstallResolutionSummary(
            summary,
            summarizeImpact(deployment, activeDraft, installRepository.findByDeploymentIdOrderByUpdatedAtDesc(deployment.getId()))
        );
    }

    private DeploymentMarketplaceImpactSummary summarizeImpact(DeploymentEntity deployment,
                                                               DeploymentDraftEntity activeDraft,
                                                               List<DeploymentMarketplacePluginInstallEntity> installs) {
        LinkedHashSet<String> pluginIds = new LinkedHashSet<>();
        LinkedHashSet<String> actionIds = new LinkedHashSet<>();
        LinkedHashSet<String> knowledgeSourceIds = new LinkedHashSet<>();
        LinkedHashSet<String> shellModuleIds = new LinkedHashSet<>();
        LinkedHashSet<String> shellCardIds = new LinkedHashSet<>();
        LinkedHashSet<String> recommendedPluginIds = new LinkedHashSet<>();
        List<DeploymentMarketplaceInstallImpactSummary> installImpacts = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        int actionPluginCount = 0;
        int dataPluginCount = 0;
        int templatePluginCount = 0;

        for (DeploymentMarketplacePluginInstallEntity install : installs) {
            MarketplacePluginEntity plugin = marketplaceCatalogService.requirePluginEntity(install.getPluginId());
            MarketplacePluginVersionEntity version = requirePluginVersionById(install.getPluginVersionId());
            MarketplaceManifestService.ParsedMarketplaceManifest parsed = marketplaceManifestService.parseAndValidate(plugin, version);
            MarketplacePluginContributionSummary contribution = parsed.contributions();
            InstallEvaluation evaluation = evaluateInstall(
                deployment,
                activeDraft,
                install,
                parsed,
                readJson(install.getConfigJson()),
                readJson(install.getSecretRefsJson()),
                install.getStatus()
            );
            pluginIds.add(plugin.getId());
            actionIds.addAll(contribution.actionIds());
            knowledgeSourceIds.addAll(contribution.knowledgeSourceIds());
            shellModuleIds.addAll(contribution.shellModuleIds());
            shellCardIds.addAll(contribution.shellCardIds());
            recommendedPluginIds.addAll(parsed.recommendedPluginIds());
            switch (parsed.pluginType()) {
                case "ACTION" -> actionPluginCount++;
                case "DATA" -> dataPluginCount++;
                case "TEMPLATE" -> {
                    templatePluginCount++;
                    if (!"BOOTSTRAPPED".equalsIgnoreCase(install.getStatus())) {
                        warnings.add("Template plugin installs are intent-only and must resolve through bootstrap flow: " + plugin.getId());
                    }
                }
                default -> warnings.add("Unknown plugin type encountered during impact preview: " + parsed.pluginType());
            }
            if (STATUS_DISABLED.equalsIgnoreCase(install.getStatus())) {
                warnings.add("Disabled install remains recorded but should not be treated as active by later compilation: " + plugin.getId());
            }
            warnings.addAll(evaluation.warnings());
            installImpacts.add(new DeploymentMarketplaceInstallImpactSummary(
                install.getId(),
                plugin.getId(),
                plugin.getDisplayName(),
                parsed.pluginType(),
                version.getVersion(),
                contribution.actionIds(),
                contribution.knowledgeSourceIds(),
                contribution.shellModuleIds(),
                contribution.shellCardIds()
            ));
        }

        return new DeploymentMarketplaceImpactSummary(
            deployment.getId(),
            installs.size(),
            actionPluginCount,
            dataPluginCount,
            templatePluginCount,
            List.copyOf(pluginIds),
            List.copyOf(actionIds),
            List.copyOf(knowledgeSourceIds),
            List.copyOf(shellModuleIds),
            List.copyOf(shellCardIds),
            List.copyOf(installImpacts),
            List.copyOf(recommendedPluginIds),
            List.copyOf(warnings)
        );
    }

    private DeploymentMarketplaceInstallSummary toSummary(DeploymentEntity deployment,
                                                          DeploymentDraftEntity activeDraft,
                                                          DeploymentVersionEntity activeVersion,
                                                          DeploymentMarketplacePluginInstallEntity install) {
        MarketplacePluginEntity plugin = marketplaceCatalogService.requirePluginEntity(install.getPluginId());
        MarketplacePluginVersionEntity version = requirePluginVersionById(install.getPluginVersionId());
        MarketplaceManifestService.ParsedMarketplaceManifest parsed = marketplaceManifestService.parseAndValidate(plugin, version);
        InstallEvaluation evaluation = evaluateInstall(
            deployment,
            activeDraft,
            install,
            parsed,
            readJson(install.getConfigJson()),
            readJson(install.getSecretRefsJson()),
            install.getStatus()
        );
        String liveState = deriveLiveState(activeVersion, install);
        return new DeploymentMarketplaceInstallSummary(
            install.getId(),
            install.getDeploymentId(),
            plugin.getId(),
            plugin.getSlug(),
            plugin.getDisplayName(),
            parsed.pluginType(),
            version.getId(),
            version.getVersion(),
            install.getStatus(),
            readJson(install.getConfigJson()),
            readJson(install.getSecretRefsJson()),
            parsed.contributions(),
            evaluation.readinessStatus(),
            evaluation.warnings(),
            evaluation.entitlement(),
            liveState,
            install.getCreatedAt(),
            install.getUpdatedAt()
        );
    }

    private String deriveLiveState(DeploymentVersionEntity activeVersion,
                                   DeploymentMarketplacePluginInstallEntity install) {
        String normalizedStatus = normalizeStatusValue(install.getStatus());
        if ("BOOTSTRAPPED".equals(normalizedStatus)) {
            return "BOOTSTRAPPED";
        }
        if (activeVersion == null) {
            return "NOT_APPLIED";
        }
        boolean presentInLiveVersion = installPresentInVersion(activeVersion, install.getId());
        if (presentInLiveVersion) {
            return "DISABLED".equals(normalizedStatus) ? "LIVE_PENDING_REMOVAL" : "LIVE";
        }
        if ("DISABLED".equals(normalizedStatus)) {
            return "DISABLED";
        }
        return "DRAFT_ONLY";
    }

    private void validateInstallable(MarketplacePluginEntity plugin,
                                     MarketplacePluginVersionEntity version,
                                     String deploymentId,
                                     String installId) {
        MarketplaceManifestService.ParsedMarketplaceManifest parsed = marketplaceManifestService.parseAndValidate(plugin, version);
        if (!"ACTIVE".equalsIgnoreCase(plugin.getStatus())) {
            throw new ResponseStatusException(CONFLICT, "Marketplace plugin is not active: " + plugin.getId());
        }
        if (!"PUBLISHED".equalsIgnoreCase(version.getStatus())) {
            throw new ResponseStatusException(CONFLICT, "Marketplace plugin version is not published: " + plugin.getId() + "@" + version.getVersion());
        }
        if ("TEMPLATE".equals(parsed.pluginType())) {
            throw new ResponseStatusException(
                CONFLICT,
                "Template plugins must be used through marketplace bootstrap flow, not deployment install APIs: " + plugin.getId()
            );
        }
        if (parsed.compatibility().requiredCapabilities().contains("templates")) {
            throw new ResponseStatusException(CONFLICT, "Template-only marketplace capability cannot be installed into an existing deployment: " + plugin.getId());
        }
        if (StringUtils.hasText(installId)) {
            return;
        }
        if (!StringUtils.hasText(deploymentId)) {
            throw new ResponseStatusException(BAD_REQUEST, "deploymentId is required");
        }
    }

    private MarketplacePluginVersionEntity resolveUpdatedVersion(MarketplacePluginEntity plugin,
                                                                 DeploymentMarketplacePluginInstallEntity install,
                                                                 String requestedVersion) {
        if (!StringUtils.hasText(requestedVersion)) {
            return requirePluginVersionById(install.getPluginVersionId());
        }
        return marketplaceCatalogService.requirePluginVersionEntity(plugin.getId(), requestedVersion);
    }

    private DeploymentMarketplacePluginInstallEntity requireInstall(String deploymentId, String installId) {
        DeploymentMarketplacePluginInstallEntity install = installRepository.findById(installId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Marketplace install not found: " + installId));
        if (!deploymentId.equals(install.getDeploymentId())) {
            throw new ResponseStatusException(NOT_FOUND, "Marketplace install not found: " + installId);
        }
        return install;
    }

    private MarketplacePluginVersionEntity requirePluginVersionById(String pluginVersionId) {
        return marketplaceCatalogService.requirePluginVersionEntityById(pluginVersionId);
    }

    private DeploymentEntity requireDeploymentViewer(String deploymentId) {
        DeploymentEntity deployment = deploymentRepository.findById(deploymentId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Deployment not found: " + deploymentId));
        return deploymentAccessService.requireDeploymentAccess(deployment);
    }

    private DeploymentEntity requireDeploymentEditor(String deploymentId) {
        DeploymentEntity deployment = deploymentRepository.findById(deploymentId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Deployment not found: " + deploymentId));
        return deploymentAccessService.requireDeploymentEditorAccess(deployment);
    }

    private JsonNode normalizeObject(JsonNode node, String fieldName) {
        if (node == null || node.isNull()) {
            return JSON.objectNode();
        }
        if (!node.isObject()) {
            throw new ResponseStatusException(BAD_REQUEST, fieldName + " must be a JSON object.");
        }
        return node;
    }

    private String normalizeStatus(String status) {
        String normalized = normalizeStatusValue(status);
        if (!STATUS_ENABLED.equals(normalized) && !STATUS_DISABLED.equals(normalized)) {
            throw new ResponseStatusException(BAD_REQUEST, "install status must be ENABLED or DISABLED.");
        }
        return normalized;
    }

    private String normalizeStatusValue(String status) {
        return status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
    }

    private void validateInstallInputs(DeploymentEntity deployment,
                                       DeploymentDraftEntity activeDraft,
                                       MarketplaceManifestService.ParsedMarketplaceManifest parsed,
                                       JsonNode config,
                                       JsonNode secretRefs,
                                       String status) {
        InstallEvaluation evaluation = evaluateInstall(deployment, activeDraft, null, parsed, config, secretRefs, status);
        if ("ENABLED".equals(normalizeStatusValue(status)) && !"READY".equals(evaluation.readinessStatus())) {
            if ("ENTITLEMENT_REQUIRED".equals(evaluation.readinessStatus())
                || "ENTITLEMENT_GRACE".equals(evaluation.readinessStatus())
                || "ENTITLEMENT_BLOCKED".equals(evaluation.readinessStatus())) {
                return;
            }
            throw new ResponseStatusException(
                "INCOMPATIBLE".equals(evaluation.readinessStatus()) ? CONFLICT : BAD_REQUEST,
                String.join(" ", evaluation.warnings())
            );
        }
    }

    private InstallEvaluation evaluateInstall(DeploymentEntity deployment,
                                              DeploymentDraftEntity activeDraft,
                                              DeploymentMarketplacePluginInstallEntity install,
                                              MarketplaceManifestService.ParsedMarketplaceManifest parsed,
                                              JsonNode config,
                                              JsonNode secretRefs,
                                              String status) {
        if ("BOOTSTRAPPED".equals(normalizeStatusValue(status))) {
            return new InstallEvaluation("BOOTSTRAPPED", List.of(), null);
        }
        List<String> warnings = new ArrayList<>();
        warnings.addAll(evaluateCompatibility(deployment, activeDraft, parsed));
        warnings.addAll(evaluateInstallForm(parsed, config, secretRefs));
        if (!warnings.isEmpty()) {
            String readiness = warnings.stream().anyMatch(message -> message.startsWith("Incompatible"))
                ? "INCOMPATIBLE"
                : "MISSING_INPUTS";
            return new InstallEvaluation(readiness, List.copyOf(warnings), buildEntitlementSummary(parsed));
        }
        if ("DISABLED".equals(normalizeStatusValue(status))) {
            var entitlementEvaluation = evaluateEntitlement(parsed, install == null ? null : marketplaceEntitlementService.findByInstallId(install.getId()));
            return new InstallEvaluation("DISABLED", List.of(), entitlementEvaluation.summary());
        }
        MarketplaceEntitlementService.EntitlementEvaluation entitlementEvaluation =
            evaluateEntitlement(parsed, install == null ? null : marketplaceEntitlementService.findByInstallId(install.getId()));
        warnings.addAll(entitlementEvaluation.warnings());
        if (!entitlementEvaluation.summary().requiresEntitlement()) {
            return new InstallEvaluation("READY", List.copyOf(warnings), entitlementEvaluation.summary());
        }
        if (entitlementEvaluation.entitledForCompilation()) {
            String readiness = "PAST_DUE".equals(entitlementEvaluation.summary().status())
                || "CANCELLED".equals(entitlementEvaluation.summary().status())
                ? "ENTITLEMENT_GRACE"
                : "READY";
            return new InstallEvaluation(readiness, List.copyOf(warnings), entitlementEvaluation.summary());
        }
        String readiness = "PENDING".equals(entitlementEvaluation.summary().status())
            ? "ENTITLEMENT_REQUIRED"
            : "ENTITLEMENT_BLOCKED";
        return new InstallEvaluation(readiness, List.copyOf(warnings), entitlementEvaluation.summary());
    }

    private MarketplaceEntitlementService.EntitlementEvaluation evaluateEntitlement(MarketplaceManifestService.ParsedMarketplaceManifest parsed,
                                                                                     DeploymentMarketplacePluginEntitlementEntity entitlement) {
        return marketplaceEntitlementService.evaluate(parsed, entitlement);
    }

    private DeploymentMarketplaceEntitlementSummary buildEntitlementSummary(MarketplaceManifestService.ParsedMarketplaceManifest parsed) {
        return marketplaceEntitlementService.evaluate(parsed, null).summary();
    }

    private List<String> evaluateCompatibility(DeploymentEntity deployment,
                                               DeploymentDraftEntity activeDraft,
                                               MarketplaceManifestService.ParsedMarketplaceManifest parsed) {
        List<String> warnings = new ArrayList<>();
        var compatibility = parsed.compatibility();
        if (!compatibility.supportedDeploymentTargets().isEmpty()
            && !compatibility.supportedDeploymentTargets().contains(deployment.getTemplateId())) {
            warnings.add(
                "Incompatible deployment target. Supported templates: "
                    + String.join(", ", compatibility.supportedDeploymentTargets()) + "."
            );
        }
        if (!compatibility.supportedAuthModes().isEmpty()) {
            LinkedHashSet<String> deploymentAuthModes = supportedDeploymentAuthModes(activeDraft);
            boolean matches = compatibility.supportedAuthModes().stream().anyMatch(deploymentAuthModes::contains);
            if (!matches) {
                warnings.add(
                    "Incompatible auth posture. Supported auth modes: "
                        + String.join(", ", compatibility.supportedAuthModes()) + "."
                );
            }
        }
        if (!compatibility.supportedProviderModes().isEmpty()) {
            JsonNode providerConfig = readJson(activeDraft.getProviderConfigJson());
            Map<String, String> providerModes = Map.of(
                "llm", ManagedDeploymentProfileCatalog.resolveLlmProvider(providerConfig),
                "embedding", ManagedDeploymentProfileCatalog.resolveEmbeddingProvider(providerConfig),
                "vector", ManagedDeploymentProfileCatalog.resolveVectorStrategy(providerConfig),
                "runtime", ManagedDeploymentProfileCatalog.resolveRuntimeProfile(providerConfig),
                "connector", providerConfig.path("connectorProfile").asText("").trim().isEmpty()
                    ? ManagedDeploymentProfileCatalog.CONNECTOR_PROFILE_HOSTED
                    : providerConfig.path("connectorProfile").asText("").trim().toLowerCase(Locale.ROOT)
            );
            Map<String, List<String>> supportedByKey = compatibility.supportedProviderModes().stream()
                .collect(java.util.stream.Collectors.groupingBy(
                    mode -> mode.substring(0, mode.indexOf(':')),
                    LinkedHashMap::new,
                    java.util.stream.Collectors.mapping(
                        mode -> mode.substring(mode.indexOf(':') + 1),
                        java.util.stream.Collectors.toList()
                    )
                ));
            supportedByKey.forEach((key, supportedValues) -> {
                String actual = providerModes.getOrDefault(key, "");
                if (!supportedValues.contains(actual)) {
                    warnings.add(
                        "Incompatible provider mode for " + key + ". Supported: " + String.join(", ", supportedValues) + "."
                    );
                }
            });
        }
        return warnings;
    }

    private List<String> evaluateInstallForm(MarketplaceManifestService.ParsedMarketplaceManifest parsed,
                                             JsonNode config,
                                             JsonNode secretRefs) {
        List<String> warnings = new ArrayList<>();
        for (var field : parsed.installForm()) {
            JsonNode source = "secretRef".equals(field.type()) ? secretRefs : config;
            JsonNode value = source.path(field.id());
            if ((value.isMissingNode() || value.isNull() || (value.isTextual() && value.asText("").trim().isEmpty()))
                && field.required()) {
                warnings.add("Missing required " + ("secretRef".equals(field.type()) ? "secret ref" : "config") + " field '" + field.id() + "'.");
                continue;
            }
            if (value.isMissingNode() || value.isNull()) {
                continue;
            }
            switch (field.type()) {
                case "text" -> {
                    if (!value.isTextual() || value.asText("").trim().isEmpty()) {
                        warnings.add("Config field '" + field.id() + "' must be a non-empty string.");
                    }
                }
                case "url" -> {
                    String text = value.asText("").trim();
                    if (!value.isTextual() || (!text.startsWith("http://") && !text.startsWith("https://"))) {
                        warnings.add("Config field '" + field.id() + "' must be an absolute http(s) URL.");
                    }
                }
                case "boolean" -> {
                    if (!value.isBoolean()) {
                        warnings.add("Config field '" + field.id() + "' must be a boolean.");
                    }
                }
                case "number" -> {
                    if (!value.isNumber()) {
                        warnings.add("Config field '" + field.id() + "' must be a number.");
                    }
                }
                case "select" -> {
                    String text = value.asText("").trim();
                    if (!value.isTextual() || !field.options().contains(text)) {
                        warnings.add("Config field '" + field.id() + "' must be one of: " + String.join(", ", field.options()) + ".");
                    }
                }
                case "secretRef" -> {
                    if (!value.isTextual() || value.asText("").trim().isEmpty()) {
                        warnings.add("Secret ref field '" + field.id() + "' must be a non-empty string reference.");
                    }
                }
                default -> {
                }
            }
        }
        return warnings;
    }

    private LinkedHashSet<String> supportedDeploymentAuthModes(DeploymentDraftEntity activeDraft) {
        LinkedHashSet<String> authModes = new LinkedHashSet<>();
        authModes.add("PLATFORM_PROXY_SESSION");
        authModes.add("PRIVATE_RUNTIME_BACKEND_MEDIATED");
        JsonNode securityConfig = readJson(activeDraft.getSecurityConfigJson());
        if (ManagedDeploymentProfileCatalog.publicRuntimeRequested(securityConfig)) {
            authModes.add("PUBLIC_RUNTIME_AUTHENTICATED");
        }
        if (ManagedDeploymentProfileCatalog.publicRuntimeBootstrapEnabled(securityConfig)) {
            authModes.add("PUBLIC_RUNTIME_ANONYMOUS");
        }
        return authModes;
    }

    private DeploymentVersionEntity resolveActiveVersion(DeploymentEntity deployment) {
        if (!StringUtils.hasText(deployment.getActiveVersionId())) {
            return null;
        }
        return deploymentVersionRepository.findById(deployment.getActiveVersionId()).orElse(null);
    }

    private DeploymentDraftEntity resolveActiveDraft(DeploymentEntity deployment) {
        if (!StringUtils.hasText(deployment.getActiveDraftId())) {
            throw new ResponseStatusException(CONFLICT, "Deployment has no active draft: " + deployment.getId());
        }
        return deploymentDraftRepository.findById(deployment.getActiveDraftId())
            .orElseThrow(() -> new ResponseStatusException(CONFLICT, "Active draft not found for deployment: " + deployment.getId()));
    }

    private boolean installPresentInVersion(DeploymentVersionEntity version, String installId) {
        if (version == null || !StringUtils.hasText(installId)) {
            return false;
        }
        return containsInstallId(readJson(version.getActionsConfigJson()), installId)
            || containsInstallId(readJson(version.getKnowledgeSourceConfigJson()), installId)
            || containsInstallId(readJson(version.getShellConfigJson()), installId);
    }

    private boolean containsInstallId(JsonNode node, String installId) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return false;
        }
        if (node.isObject()) {
            if (installId.equals(node.path("marketplaceInstallId").asText(""))) {
                return true;
            }
            var fields = node.fields();
            while (fields.hasNext()) {
                if (containsInstallId(fields.next().getValue(), installId)) {
                    return true;
                }
            }
            return false;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                if (containsInstallId(child, installId)) {
                    return true;
                }
            }
        }
        return false;
    }

    private JsonNode readJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to read marketplace install JSON.", ex);
        }
    }

    private String writeJson(JsonNode json) {
        try {
            return objectMapper.writeValueAsString(json == null ? JSON.objectNode() : json);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to write marketplace install JSON.", ex);
        }
    }

    private record InstallEvaluation(
        String readinessStatus,
        List<String> warnings,
        DeploymentMarketplaceEntitlementSummary entitlement
    ) {
    }
}
