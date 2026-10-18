package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.config.PlatformDeliveryProperties;
import com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencyGraphEntity;
import com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencyGraphStore;
import com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencySpec;
import com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencySpecRepository;
import com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencySpecValidator;
import com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencyState;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentDraftEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentSecretLiteralRiskSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentSecretUsageItemSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentSecretUsageSummary;
import com.ai.fabric.platform.backend.secret.model.DeploymentSecretResolutionSummary;
import com.ai.fabric.platform.backend.secret.model.PlatformSecretSummary;
import com.ai.fabric.platform.backend.secret.service.DeploymentProviderSecretResolutionService;
import com.ai.fabric.platform.backend.secret.service.DeploymentSecretPurposeCatalog;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DeploymentSecretUsageService {

    private static final Pattern SECRET_REFERENCE_PATTERN = Pattern.compile("^\\$\\{(?:(?:secret:)?)([A-Z0-9_]+)}$");

    private final PlatformSecretService platformSecretService;
    private final DeploymentProviderSecretResolutionService deploymentProviderSecretResolutionService;
    private final DeploymentSecretPurposeCatalog deploymentSecretPurposeCatalog;
    private final PlatformDeliveryProperties deliveryProperties;
    private final ObjectMapper objectMapper;
    private final DeploymentExistingVectorBindingService existingVectorBindings;
    private final ManagedDependencyGraphStore dependencyGraphs;
    private final ManagedDependencySpecValidator dependencySpecs;
    private final ManagedDependencySpecRepository reviewedDependencySpecs;

    public DeploymentSecretUsageService(PlatformSecretService platformSecretService,
                                        DeploymentProviderSecretResolutionService deploymentProviderSecretResolutionService,
                                        DeploymentSecretPurposeCatalog deploymentSecretPurposeCatalog,
                                        PlatformDeliveryProperties deliveryProperties,
                                        ObjectMapper objectMapper,
                                        DeploymentExistingVectorBindingService existingVectorBindings,
                                        ManagedDependencyGraphStore dependencyGraphs,
                                        ManagedDependencySpecValidator dependencySpecs,
                                        ManagedDependencySpecRepository reviewedDependencySpecs) {
        this.platformSecretService = platformSecretService;
        this.deploymentProviderSecretResolutionService = deploymentProviderSecretResolutionService;
        this.deploymentSecretPurposeCatalog = deploymentSecretPurposeCatalog;
        this.deliveryProperties = deliveryProperties;
        this.objectMapper = objectMapper;
        this.existingVectorBindings = existingVectorBindings;
        this.dependencyGraphs = dependencyGraphs;
        this.dependencySpecs = dependencySpecs;
        this.reviewedDependencySpecs = reviewedDependencySpecs;
    }

    public DeploymentSecretUsageSummary build(String deploymentId, DeploymentDraftEntity draft) {
        return build(deploymentId, draft, null);
    }

    public DeploymentSecretUsageSummary build(DeploymentEntity deployment, DeploymentDraftEntity draft,
                                              DeploymentVersionEntity version, String targetProfileId) {
        Objects.requireNonNull(deployment, "Deployment context is required.");
        return build(deployment.getId(), draft, new BindingContext(deployment, version, targetProfileId));
    }

    private DeploymentSecretUsageSummary build(String deploymentId, DeploymentDraftEntity draft, BindingContext bindingContext) {
        JsonNode routingConfig = readJson(draft.getRoutingConfigJson());
        JsonNode providerConfig = readJson(draft.getProviderConfigJson());
        JsonNode securityConfig = readJson(draft.getSecurityConfigJson());
        JsonNode marketplaceDatasetConfig = readJson(draft.getMarketplaceDatasetConfigJson());
        ManagedDeploymentProfileCatalog.validateMilvusAuthentication(providerConfig, false);

        Map<String, PlatformSecretSummary> secretCatalog = platformSecretService.listSecrets().stream()
            .collect(LinkedHashMap::new, (map, item) -> map.put(item.name(), item), Map::putAll);
        Map<String, UsageAccumulator> usages = new LinkedHashMap<>();
        List<DeploymentSecretLiteralRiskSummary> literalRisks = new ArrayList<>();

        for (Map.Entry<String, String> entry : ManagedDeploymentProfileCatalog.providerSecretNamesByLlmSelection(providerConfig).entrySet()) {
            registerUsage(
                usages,
                entry.getValue(),
                true,
                "Provider stack",
                "$.providerConfig." + entry.getKey() + ".credential"
            );
        }
        String embeddingSecretName = ManagedDeploymentProfileCatalog.secretNameForEmbeddingProvider(
            ManagedDeploymentProfileCatalog.resolveEmbeddingProvider(providerConfig)
        );
        if (embeddingSecretName != null && !embeddingSecretName.isBlank()) {
            registerUsage(usages, embeddingSecretName, true, "Provider stack", "$.providerConfig.embeddingProvider");
        }
        if (ManagedDeploymentProfileCatalog.usesMilvus(providerConfig)
            && DeploymentExistingVectorBindingService.required(providerConfig)) {
            boolean tokenAuth = ManagedDeploymentProfileCatalog.milvusTokenAuthentication(providerConfig);
            String purpose = tokenAuth ? "MILVUS_TOKEN" : "MILVUS_RUNTIME_CREDENTIALS";
            registerUsage(usages, purpose, true, "Vector database", "$.providerConfig.milvusAuthenticationType");
            UsageAccumulator usage = usages.get(purpose);
            usage.bindingOnly = true;
            usage.bindingPresent = externalVectorBindingReady(deploymentId, draft, bindingContext);
            if (bindingContext != null && identifier(bindingContext.targetProfileId()) && bindingContext.version() != null
                && identifier(bindingContext.version().getId())) {
                usage.bindingScope = "target " + bindingContext.targetProfileId() + " and published version " + bindingContext.version().getId();
            }
        } else {
            String requiredVectorSecretName = ManagedDeploymentProfileCatalog.requiredVectorSecretName(providerConfig);
            if (requiredVectorSecretName != null && !requiredVectorSecretName.isBlank()) {
                registerUsage(usages, requiredVectorSecretName, true, "Vector database", "$.providerConfig.vectorStrategy");
            }
            for (String optionalVectorSecretName : ManagedDeploymentProfileCatalog.optionalVectorSecretNames(providerConfig)) {
                registerUsage(usages, optionalVectorSecretName, false, "Vector database", "$.providerConfig.vectorStrategy");
            }
        }

        boolean connectorApiKeyEnabled = ManagedDeploymentProfileCatalog.connectorApiKeyEnabled(securityConfig);
        boolean connectorRuntimeProxyEnabled = ManagedDeploymentProfileCatalog.connectorRuntimeProxyEnabled(providerConfig);

        if (ManagedDeploymentProfileCatalog.adminApiKeyEnabled(securityConfig)) {
            registerUsage(
                usages,
                "AI_FABRIC_RUNTIME_TRUSTED_BACKEND_API_KEY",
                true,
                "Runtime service",
                "$.securityConfig.adminApiKeyEnabled"
            );
            registerUsage(
                usages,
                "AI_FABRIC_RUNTIME_PRIVATE_ASSERTION_SIGNING_KEY",
                true,
                "Runtime service",
                "$.securityConfig.adminApiKeyEnabled"
            );
            if (connectorRuntimeProxyEnabled) {
                registerUsage(
                    usages,
                    "AI_FABRIC_RUNTIME_TRUSTED_BACKEND_API_KEY",
                    true,
                    "REST connector",
                    "runtime proxy machine auth"
                );
            }
        }
        registerUsage(
            usages,
            "AI_FABRIC_RUNTIME_TRUSTED_BACKEND_API_KEY",
            false,
            "Runtime service",
            "private runtime trusted-backend machine auth"
        );
        registerUsage(
            usages,
            "AI_FABRIC_RUNTIME_PRIVATE_ASSERTION_SIGNING_KEY",
            false,
            "Runtime service",
            "private runtime signed assertion validation"
        );
        registerUsage(
            usages,
            "AI_FABRIC_RUNTIME_PUBLIC_TOKEN_SIGNING_KEY",
            false,
            "Runtime service",
            "public runtime signed bearer-token validation"
        );

        if (deliveryProperties.signedArtifactsEnabled()) {
            registerUsage(usages, "PLATFORM_ARTIFACT_SIGNING_KEY", true, "Platform delivery", "signed artifacts");
        }

        JsonNode inboundAuthApiKey = routingConfig.path("connector").path("inbound-auth").path("api-key");
        String inboundAuthValue = inboundAuthApiKey.path("value").asText("${CONNECTOR_API_KEY}");
        if (connectorApiKeyEnabled && !isPlatformManagedConnectorCredential(inboundAuthValue)) {
            registerFromDraftValue(
                usages,
                literalRisks,
                secretCatalog,
                inboundAuthValue,
                "REST connector",
                "$.connector.inbound-auth.api-key.value",
                "Connector inbound auth should reference a managed secret placeholder instead of a literal credential.",
                null
            );
        }

        registerFromDraftValue(
            usages,
            literalRisks,
            secretCatalog,
            routingConfig.path("connector").path("upstream").path("auth").path("value").asText(""),
            "Store and upstream integration",
            "$.connector.upstream.auth.value",
            "Upstream auth should use a placeholder or external secret reference instead of a literal credential.",
            null
        );

        registerHttpIntegrationSecrets(usages, literalRisks, secretCatalog, marketplaceDatasetConfig);
        projectLocalDependencySecrets(deploymentId, draft, bindingContext, usages);

        List<DeploymentSecretUsageItemSummary> secrets = usages.entrySet().stream()
            .map(entry -> toItemSummary(
                deploymentId,
                entry.getValue().dependencyEvidence != null && entry.getValue().draftReferenceOnly
                    ? null : resolveSecretSummary(secretCatalog, entry.getKey()),
                entry.getKey(),
                entry.getValue()
            ))
            .toList();

        int missingRequiredCount = (int) secrets.stream()
            .filter(item -> item.required() && !item.present())
            .count();
        int literalRiskCount = literalRisks.size();
        String summaryMessage;
        if (literalRiskCount > 0) {
            summaryMessage = literalRiskCount + " literal credential risk(s) were detected in the deployment draft.";
        } else if (missingRequiredCount > 0) {
            summaryMessage = missingRequiredCount + " required deployment secret(s) are still missing.";
        } else {
            summaryMessage = "Deployment secret usage is mapped and no literal credential risks were detected.";
        }

        return new DeploymentSecretUsageSummary(
            deploymentId,
            secrets,
            List.copyOf(literalRisks),
            missingRequiredCount,
            literalRiskCount,
            summaryMessage
        );
    }

    private boolean externalVectorBindingReady(String deploymentId, DeploymentDraftEntity draft, BindingContext context) {
        if (!matchesPublishedScope(deploymentId, draft, context)) {
            return false;
        }
        var deployment = context.deployment();
        var version = context.version();
        try {
            JsonNode publishedConfig = readJson(version.getProviderConfigJson());
            JsonNode effectiveConfig = existingVectorBindings.resolveLocal(deployment, version, publishedConfig, context.targetProfileId());
            if (!ManagedDeploymentProfileCatalog.usesMilvus(effectiveConfig)
                || !DeploymentExistingVectorBindingService.required(effectiveConfig)
                || !ManagedDeploymentProfileCatalog.resolveMilvusAuthenticationType(publishedConfig)
                    .equals(ManagedDeploymentProfileCatalog.resolveMilvusAuthenticationType(effectiveConfig))) {
                return false;
            }
            ManagedDeploymentProfileCatalog.validateMilvusAuthentication(effectiveConfig, true);
            return true;
        } catch (ResponseStatusException ex) {
            if (ex.getStatusCode().value() == 412 || ex.getStatusCode().value() == 409 || ex.getStatusCode().value() == 404) {
                return false;
            }
            throw ex;
        }
    }

    private boolean matchesPublishedScope(String deploymentId, DeploymentDraftEntity draft, BindingContext context) {
        return context != null && identifier(deploymentId) && identifier(context.targetProfileId())
            && context.version() != null && identifier(context.version().getId())
            && deploymentId.equals(draft.getDeploymentId()) && deploymentId.equals(context.version().getDeploymentId())
            && context.deployment().getArchivedAt() == null && !"ARCHIVED".equals(context.deployment().getStatus())
            && "PUBLISHED".equals(context.version().getStatus()) && draftMatchesVersion(draft, context.version());
    }

    private void projectLocalDependencySecrets(String deploymentId, DeploymentDraftEntity draft, BindingContext context,
                                                Map<String, UsageAccumulator> usages) {
        if (context == null || !identifier(deploymentId) || !identifier(context.targetProfileId())
            || usages.values().stream().allMatch(usage -> usage.draftReferences.isEmpty())) {
            return;
        }
        var graph = dependencyGraphs.find(deploymentId, context.targetProfileId()).orElse(null);
        // Release-time projection also checks providers; readiness consumes only persisted local evidence.
        var spec = publishedDependencySpec(context.version());
        if (spec == null && graph != null) {
            spec = dependencyGraphs.spec(graph);
        }
        if (graph == null && spec == null) {
            return;
        }
        dependencySpecs.validate(spec);
        boolean scoped = matchesPublishedScope(deploymentId, draft, context)
            && identifier(context.deployment().getCustomerId()) && identifier(context.deployment().getTenantId())
            && (graph == null || deploymentId.equals(graph.getDeploymentId())
                && context.targetProfileId().equals(graph.getTargetProfileId())
                && context.version().getId().equals(graph.getVersionId())
                && context.deployment().getCustomerId().equals(graph.getCustomerId())
                && context.deployment().getTenantId().equals(graph.getTenantId())
                && dependencySpecs.fingerprint(spec).equals(graph.getSpecFingerprint()));
        boolean provisioned = graph != null && scoped && locallyProvisioned(graph);
        var state = provisioned ? dependencyGraphs.state(graph) : null;
        String scope = "target " + context.targetProfileId() + " and published version "
            + (context.version() == null ? "(not supplied)" : context.version().getId());
        for (var binding : spec.runtimeEnvironment()) {
            var usage = usages.get(binding.name());
            if (binding.target() != ManagedDependencySpec.RuntimeTarget.CONNECTOR || usage == null
                || !usage.draftReferences.contains(binding.name())) {
                continue;
            }
            boolean present = provisioned && dependencySecretPresent(deploymentId, binding.value(), state);
            String message = present
                ? "Local managed-dependency credentials are ready for " + scope + ". Provider health and runtime initialization have not been checked."
                : !scoped
                    ? "Local managed-dependency credentials are missing for " + scope + ". Persisted evidence does not match this draft and scope."
                    : !provisioned
                        ? "Local managed-dependency credentials are missing for " + scope + ". Dependency provisioning is pending or not ready."
                        : "Local managed-dependency credentials are missing for " + scope + ". Exact provisioned dependency and secret-presence evidence is required.";
            usage.dependencyEvidence = new DependencySecretEvidence(present, message);
        }
    }

    private ManagedDependencySpec publishedDependencySpec(DeploymentVersionEntity version) {
        if (version == null) {
            return null;
        }
        JsonNode installs = readJson(version.getCompositionProvenanceJson()).path("marketplaceInstalls");
        Set<String> templateVersions = new LinkedHashSet<>();
        if (installs.isArray()) {
            for (JsonNode install : installs) {
                if ("TEMPLATE".equals(install.path("pluginType").asText())) {
                    String versionId = install.path("pluginVersionId").asText();
                    if (!identifier(versionId)) {
                        throw new IllegalStateException("Persisted template version identifier is invalid.");
                    }
                    templateVersions.add(versionId);
                }
            }
        }
        if (templateVersions.isEmpty()) {
            return null;
        }
        if (templateVersions.size() != 1) {
            throw new IllegalStateException("Persisted template version is ambiguous.");
        }
        String templateVersion = templateVersions.iterator().next();
        var matches = reviewedDependencySpecs.findByTemplateVersionIdAndActiveTrue(templateVersion);
        if (matches.isEmpty()) {
            return null;
        }
        if (matches.size() != 1) {
            throw new IllegalStateException("Reviewed dependency specification is ambiguous.");
        }
        var publication = matches.getFirst();
        var spec = dependencyGraphs.parse(publication.getSpecJson(), ManagedDependencySpec.class);
        dependencySpecs.validate(spec);
        if (!publication.isActive() || !templateVersion.equals(publication.getTemplateVersionId())
            || publication.getReviewedAt() == null || publication.getReviewedBy() == null || publication.getReviewedBy().isBlank()
            || !dependencySpecs.fingerprint(spec).equals(publication.getFingerprint())) {
            throw new IllegalStateException("Reviewed dependency specification does not match its publication.");
        }
        return spec;
    }

    private boolean locallyProvisioned(ManagedDependencyGraphEntity graph) {
        return ("PROVISIONED".equals(graph.getStatus()) || "READY".equals(graph.getStatus())) && graph.getWorkerToken() == null
            && graph.getReservationRef() != null && !graph.getReservationRef().isBlank()
            && graph.getBindingFingerprint() != null && graph.getBindingFingerprint().matches("[a-f0-9]{64}");
    }

    private boolean dependencySecretPresent(String deploymentId, ManagedDependencySpec.Value value, ManagedDependencyState state) {
        if (state.configurationRevision < 0) {
            return false;
        }
        String reference;
        if (value.kind() == ManagedDependencySpec.ValueKind.SERVICE_SECRET) {
            var nodes = state.nodes.stream().filter(node -> value.serviceSlot().equals(node.slot)).toList();
            if (nodes.size() != 1) {
                return false;
            }
            var node = nodes.getFirst();
            if (node.disabled || node.applicationUuid == null || node.applicationUuid.isBlank()
                || node.baseUrl == null || node.baseUrl.isBlank()
                || !("PROVISIONED".equals(node.status) || "READY".equals(node.status))
                || node.provisionedConfigurationRevision != state.configurationRevision) {
                return false;
            }
            reference = node.secrets.get(value.reference());
        } else if (value.kind() == ManagedDependencySpec.ValueKind.TARGET_SECRET) {
            reference = state.targetSecretReferences.get(value.reference());
        } else {
            return false;
        }
        if (reference == null || reference.isBlank() || !platformSecretService.isManagedSecretName(reference)) {
            return false;
        }
        var metadata = platformSecretService.describeSecret(reference);
        return metadata != null && reference.equals(metadata.name()) && metadata.present() && metadata.managedByPlatform()
            && deploymentId.equals(metadata.deploymentId()) && "DEPLOYMENT_MANAGED".equals(metadata.scopeType());
    }

    private boolean identifier(String value) {
        return value != null && value.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");
    }

    private boolean draftMatchesVersion(DeploymentDraftEntity draft, DeploymentVersionEntity version) {
        return Objects.equals(draft.getActionsConfigJson(), version.getActionsConfigJson())
            && Objects.equals(draft.getEntityConfigJson(), version.getEntityConfigJson())
            && Objects.equals(draft.getEntityConfigContractVersion(), version.getEntityConfigContractVersion())
            && Objects.equals(draft.getRoutingConfigJson(), version.getRoutingConfigJson())
            && Objects.equals(draft.getProviderConfigJson(), version.getProviderConfigJson())
            && Objects.equals(draft.getSecurityConfigJson(), version.getSecurityConfigJson())
            && Objects.equals(draft.getPromptConfigJson(), version.getPromptConfigJson())
            && Objects.equals(draft.getKnowledgeSourceConfigJson(), version.getKnowledgeSourceConfigJson())
            && Objects.equals(draft.getShellConfigJson(), version.getShellConfigJson())
            && Objects.equals(draft.getMarketplaceDatasetConfigJson(), version.getMarketplaceDatasetConfigJson())
            && Objects.equals(draft.getBehaviorConfigJson(), version.getBehaviorConfigJson());
    }

    private void registerHttpIntegrationSecrets(
        Map<String, UsageAccumulator> usages,
        List<DeploymentSecretLiteralRiskSummary> literalRisks,
        Map<String, PlatformSecretSummary> secretCatalog,
        JsonNode marketplaceDatasetConfig
    ) {
        JsonNode datasets = marketplaceDatasetConfig.path("datasets");
        if (!datasets.isArray()) {
            return;
        }
        for (int index = 0; index < datasets.size(); index++) {
            JsonNode dataset = datasets.get(index);
            if (!"EXTERNAL_SYNC_HTTP".equalsIgnoreCase(dataset.path("ingestionMode").asText(""))) {
                continue;
            }
            String base = "$.marketplaceDatasetConfig.datasets[" + index + "].syncConnector";
            JsonNode connector = dataset.path("syncConnector");
            JsonNode auth = connector.path("connectionProfile").path("auth");
            registerFromDraftValue(
                usages, literalRisks, secretCatalog,
                auth.path("apiKeyValue").asText(""),
                "REST connector", base + ".connectionProfile.auth.apiKeyValue",
                "External-provider API-key auth must reference a deployment secret.", null
            );
            JsonNode credentialFields = auth.path("credentialFields");
            if (credentialFields.isObject()) {
                credentialFields.fields().forEachRemaining(entry -> registerFromDraftValue(
                    usages, literalRisks, secretCatalog,
                    entry.getValue().asText(""),
                    "REST connector", base + ".connectionProfile.auth.credentialFields." + entry.getKey(),
                    "External-provider token credentials must reference deployment secrets.", null
                ));
            }
            registerFromDraftValue(
                usages, literalRisks, secretCatalog,
                connector.path("webhook").path("verification").path("secret").asText(""),
                "REST connector", base + ".webhook.verification.secret",
                "External-provider webhook verification must reference a deployment secret.", null
            );
        }
    }

    private void registerFromDraftValue(Map<String, UsageAccumulator> usages,
                                        List<DeploymentSecretLiteralRiskSummary> literalRisks,
                                        Map<String, PlatformSecretSummary> secretCatalog,
                                        String value,
                                        String service,
                                        String path,
                                        String literalRiskMessage,
                                        String defaultSecretName) {
        if (value == null || value.isBlank()) {
            return;
        }
        String secretName = referencedSecretName(value);
        if (secretName != null) {
            boolean managed = secretCatalog.containsKey(secretName)
                || platformSecretService.isManagedSecretName(secretName);
            registerUsage(usages, secretName, managed, service, path, true);
            return;
        }
        if (defaultSecretName != null) {
            registerUsage(usages, defaultSecretName, true, service, path);
        }
        literalRisks.add(new DeploymentSecretLiteralRiskSummary(service, path, literalRiskMessage));
    }

    private void registerUsage(Map<String, UsageAccumulator> usages,
                               String secretName,
                               boolean required,
                               String service,
                               String path) {
        registerUsage(usages, secretName, required, service, path, false);
    }

    private void registerUsage(Map<String, UsageAccumulator> usages,
                               String secretName,
                               boolean required,
                               String service,
                               String path,
                               boolean draftReference) {
        String usageKey = usageKey(secretName);
        UsageAccumulator usage = usages.computeIfAbsent(usageKey, ignored -> new UsageAccumulator());
        usage.secretPurpose = secretPurposeFor(secretName);
        usage.required = usage.required || required;
        if (draftReference) {
            usage.draftReferences.add(secretName);
        }
        usage.draftReferenceOnly &= draftReference;
        usage.services.add(service);
        usage.paths.add(path);
    }

    private DeploymentSecretUsageItemSummary toItemSummary(String deploymentId,
                                                           PlatformSecretSummary summary,
                                                           String secretName,
                                                           UsageAccumulator usage) {
        if (usage.dependencyEvidence != null && usage.draftReferenceOnly) {
            return new DeploymentSecretUsageItemSummary(secretName, secretName, true, usage.dependencyEvidence.present(),
                "DEPLOYMENT_MANAGED", usage.dependencyEvidence.present() ? "READY" : "MISSING",
                List.copyOf(usage.services), List.copyOf(usage.paths), usage.dependencyEvidence.message(), null, null);
        }
        DeploymentSecretResolutionSummary effectiveResolution = usage.bindingOnly
            ? null : effectiveResolution(deploymentId, usage.secretPurpose);
        boolean present = usage.bindingOnly
            ? usage.bindingPresent
            : effectiveResolution != null ? effectiveResolution.resolved() : summary != null && summary.present();
        if (usage.dependencyEvidence != null) {
            present &= usage.dependencyEvidence.present();
        }
        String source = usage.bindingOnly ? "DEPLOYMENT_MANAGED" : effectiveResolution != null && effectiveResolution.scopeType() != null
            ? effectiveResolution.scopeType()
            : summary != null ? summary.source() : "UNMANAGED";
        boolean required = usage.required || usage.dependencyEvidence != null || (summary != null && summary.required());
        String status;
        String message;
        if (required && !present) {
            status = "MISSING";
            message = effectiveResolution != null && effectiveResolution.diagnosticMessage() != null
                ? effectiveResolution.diagnosticMessage()
                : secretName + " is referenced by this deployment but is not present in the platform secret store.";
        } else if (present) {
            status = "READY";
            message = effectiveResolution != null && effectiveResolution.diagnosticMessage() != null
                ? effectiveResolution.diagnosticMessage()
                : secretName + " is present and referenced by the deployment.";
        } else {
            status = "WARNING";
            message = effectiveResolution != null && effectiveResolution.diagnosticMessage() != null
                ? effectiveResolution.diagnosticMessage()
                : secretName + " is referenced, but it is outside the current managed platform secret catalog.";
        }
        if (usage.bindingOnly) {
            message = usage.bindingScope == null
                ? "An explicit published version and target are required to verify local external-vector credentials."
                : present
                    ? "Local external-vector credentials are ready for " + usage.bindingScope + ". Provider health has not been checked."
                    : "Local external-vector credentials are not ready for " + usage.bindingScope + ". Binding evidence is missing or does not match this draft.";
        }
        if (usage.dependencyEvidence != null) {
            message += " " + usage.dependencyEvidence.message();
        }
        return new DeploymentSecretUsageItemSummary(
            secretName,
            effectiveResolution != null ? effectiveResolution.displayName() : summary != null ? summary.displayName() : secretName,
            required,
            present,
            source,
            status,
            List.copyOf(usage.services),
            List.copyOf(usage.paths),
            message,
            usage.secretPurpose,
            effectiveResolution
        );
    }

    private PlatformSecretSummary resolveSecretSummary(Map<String, PlatformSecretSummary> secretCatalog,
                                                       String secretName) {
        PlatformSecretSummary summary = secretCatalog.get(secretName);
        if (summary != null || !platformSecretService.isManagedSecretName(secretName)) {
            return summary;
        }
        return platformSecretService.describeSecret(secretName);
    }

    private DeploymentSecretResolutionSummary effectiveResolution(String deploymentId, String secretPurpose) {
        if (secretPurpose == null) {
            return null;
        }
        return deploymentProviderSecretResolutionService.summarize(deploymentId, secretPurpose);
    }

    private String secretPurposeFor(String secretName) {
        String normalizedUsageKey = usageKey(secretName);
        return deploymentSecretPurposeCatalog.isSupportedOverridePurpose(normalizedUsageKey)
            ? normalizedUsageKey
            : null;
    }

    private String usageKey(String secretName) {
        if ("MILVUS_USERNAME".equals(secretName) || "MILVUS_PASSWORD".equals(secretName)) {
            return "MILVUS_RUNTIME_CREDENTIALS";
        }
        return secretName;
    }

    private String referencedSecretName(String value) {
        Matcher matcher = SECRET_REFERENCE_PATTERN.matcher(value.trim());
        if (!matcher.matches()) {
            return null;
        }
        return matcher.group(1);
    }

    private boolean isPlatformManagedConnectorCredential(String value) {
        return "${CONNECTOR_API_KEY}".equals(value) || "${secret:CONNECTOR_API_KEY}".equals(value);
    }

    private JsonNode readJson(String value) {
        try {
            return value == null || value.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse deployment draft config JSON.", ex);
        }
    }

    private static final class UsageAccumulator {
        private String secretPurpose;
        private boolean required;
        private boolean draftReferenceOnly = true;
        private DependencySecretEvidence dependencyEvidence;
        private boolean bindingOnly;
        private boolean bindingPresent;
        private String bindingScope;
        private final Set<String> services = new LinkedHashSet<>();
        private final Set<String> paths = new LinkedHashSet<>();
        private final Set<String> draftReferences = new LinkedHashSet<>();
    }

    private record DependencySecretEvidence(boolean present, String message) { }

    private record BindingContext(DeploymentEntity deployment, DeploymentVersionEntity version, String targetProfileId) { }
}
