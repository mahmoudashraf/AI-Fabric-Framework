package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.config.PlatformDeliveryProperties;
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

    public DeploymentSecretUsageService(PlatformSecretService platformSecretService,
                                        DeploymentProviderSecretResolutionService deploymentProviderSecretResolutionService,
                                        DeploymentSecretPurposeCatalog deploymentSecretPurposeCatalog,
                                        PlatformDeliveryProperties deliveryProperties,
                                        ObjectMapper objectMapper,
                                        DeploymentExistingVectorBindingService existingVectorBindings) {
        this.platformSecretService = platformSecretService;
        this.deploymentProviderSecretResolutionService = deploymentProviderSecretResolutionService;
        this.deploymentSecretPurposeCatalog = deploymentSecretPurposeCatalog;
        this.deliveryProperties = deliveryProperties;
        this.objectMapper = objectMapper;
        this.existingVectorBindings = existingVectorBindings;
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

        List<DeploymentSecretUsageItemSummary> secrets = usages.entrySet().stream()
            .map(entry -> toItemSummary(
                deploymentId,
                resolveSecretSummary(secretCatalog, entry.getKey()),
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
        if (context == null) {
            return false;
        }
        var deployment = context.deployment();
        var version = context.version();
        if (!identifier(deploymentId) || !identifier(context.targetProfileId()) || version == null || !identifier(version.getId())
            || !deploymentId.equals(draft.getDeploymentId()) || !deploymentId.equals(version.getDeploymentId())
            || deployment.getArchivedAt() != null || "ARCHIVED".equals(deployment.getStatus())
            || !"PUBLISHED".equals(version.getStatus()) || !draftMatchesVersion(draft, version)) {
            return false;
        }
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
            registerUsage(usages, secretName, managed, service, path);
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
        String usageKey = usageKey(secretName);
        UsageAccumulator usage = usages.computeIfAbsent(usageKey, ignored -> new UsageAccumulator());
        usage.secretPurpose = secretPurposeFor(secretName);
        usage.required = usage.required || required;
        usage.services.add(service);
        usage.paths.add(path);
    }

    private DeploymentSecretUsageItemSummary toItemSummary(String deploymentId,
                                                           PlatformSecretSummary summary,
                                                           String secretName,
                                                           UsageAccumulator usage) {
        DeploymentSecretResolutionSummary effectiveResolution = usage.bindingOnly
            ? null : effectiveResolution(deploymentId, usage.secretPurpose);
        boolean present = usage.bindingOnly
            ? usage.bindingPresent
            : effectiveResolution != null ? effectiveResolution.resolved() : summary != null && summary.present();
        String source = usage.bindingOnly ? "DEPLOYMENT_MANAGED" : effectiveResolution != null && effectiveResolution.scopeType() != null
            ? effectiveResolution.scopeType()
            : summary != null ? summary.source() : "UNMANAGED";
        boolean required = usage.required || (summary != null && summary.required());
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
        private boolean bindingOnly;
        private boolean bindingPresent;
        private String bindingScope;
        private final Set<String> services = new LinkedHashSet<>();
        private final Set<String> paths = new LinkedHashSet<>();
    }

    private record BindingContext(DeploymentEntity deployment, DeploymentVersionEntity version, String targetProfileId) { }
}
