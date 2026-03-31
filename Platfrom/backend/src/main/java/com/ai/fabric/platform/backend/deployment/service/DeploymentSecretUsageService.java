package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.config.PlatformDeliveryProperties;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentDraftEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentSecretLiteralRiskSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentSecretUsageItemSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentSecretUsageSummary;
import com.ai.fabric.platform.backend.secret.model.PlatformSecretSummary;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DeploymentSecretUsageService {

    private static final Pattern SECRET_REFERENCE_PATTERN = Pattern.compile("^\\$\\{(?:(?:secret:)?)([A-Z0-9_]+)}$");

    private final PlatformSecretService platformSecretService;
    private final PlatformDeliveryProperties deliveryProperties;
    private final ObjectMapper objectMapper;

    public DeploymentSecretUsageService(PlatformSecretService platformSecretService,
                                        PlatformDeliveryProperties deliveryProperties,
                                        ObjectMapper objectMapper) {
        this.platformSecretService = platformSecretService;
        this.deliveryProperties = deliveryProperties;
        this.objectMapper = objectMapper;
    }

    public DeploymentSecretUsageSummary build(String deploymentId, DeploymentDraftEntity draft) {
        JsonNode routingConfig = readJson(draft.getRoutingConfigJson());
        JsonNode providerConfig = readJson(draft.getProviderConfigJson());
        JsonNode securityConfig = readJson(draft.getSecurityConfigJson());

        Map<String, PlatformSecretSummary> secretCatalog = platformSecretService.listSecrets().stream()
            .collect(LinkedHashMap::new, (map, item) -> map.put(item.name(), item), Map::putAll);
        Map<String, UsageAccumulator> usages = new LinkedHashMap<>();
        List<DeploymentSecretLiteralRiskSummary> literalRisks = new ArrayList<>();

        String llmSecretName = ManagedDeploymentProfileCatalog.secretNameForLlmProvider(
            ManagedDeploymentProfileCatalog.resolveLlmProvider(providerConfig)
        );
        if (llmSecretName != null && !llmSecretName.isBlank()) {
            registerUsage(usages, llmSecretName, true, "Provider stack", "$.providerConfig.llmProvider");
        }
        String embeddingSecretName = ManagedDeploymentProfileCatalog.secretNameForEmbeddingProvider(
            ManagedDeploymentProfileCatalog.resolveEmbeddingProvider(providerConfig)
        );
        if (embeddingSecretName != null && !embeddingSecretName.isBlank()) {
            registerUsage(usages, embeddingSecretName, true, "Provider stack", "$.providerConfig.embeddingProvider");
        }
        String requiredVectorSecretName = ManagedDeploymentProfileCatalog.requiredVectorSecretName(
            ManagedDeploymentProfileCatalog.resolveVectorStrategy(providerConfig)
        );
        if (requiredVectorSecretName != null && !requiredVectorSecretName.isBlank()) {
            registerUsage(usages, requiredVectorSecretName, true, "Vector database", "$.providerConfig.vectorStrategy");
        }
        for (String optionalVectorSecretName : ManagedDeploymentProfileCatalog.optionalVectorSecretNames(
            ManagedDeploymentProfileCatalog.resolveVectorStrategy(providerConfig)
        )) {
            registerUsage(usages, optionalVectorSecretName, false, "Vector database", "$.providerConfig.vectorStrategy");
        }

        boolean connectorApiKeyEnabled = ManagedDeploymentProfileCatalog.connectorApiKeyEnabled(securityConfig);
        if (connectorApiKeyEnabled) {
            registerUsage(usages, "ACTIONS_CONNECTOR_API_KEY", true, "Runtime service", "runtime-to-connector");
            registerUsage(usages, "CONNECTOR_API_KEY", true, "REST connector", "$.securityConfig.connectorApiKeyEnabled");
        }

        if (ManagedDeploymentProfileCatalog.adminApiKeyEnabled(securityConfig)) {
            registerUsage(usages, "APP_ADMIN_API_KEY", true, "Runtime service", "$.adminApiKeyEnabled");
            registerUsage(usages, "APP_ADMIN_API_KEY", true, "REST connector", "runtime proxy admin access");
        }

        if (deliveryProperties.signedArtifactsEnabled()) {
            registerUsage(usages, "PLATFORM_ARTIFACT_SIGNING_KEY", true, "Platform delivery", "signed artifacts");
        }

        JsonNode inboundAuthApiKey = routingConfig.path("connector").path("inbound-auth").path("api-key");
        if (connectorApiKeyEnabled) {
            registerFromDraftValue(
                usages,
                literalRisks,
                secretCatalog,
                inboundAuthApiKey.path("value").asText("${CONNECTOR_API_KEY}"),
                "REST connector",
                "$.connector.inbound-auth.api-key.value",
                "Connector inbound auth should reference a managed secret placeholder instead of a literal credential.",
                "CONNECTOR_API_KEY"
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

        List<DeploymentSecretUsageItemSummary> secrets = usages.entrySet().stream()
            .map(entry -> toItemSummary(secretCatalog.get(entry.getKey()), entry.getKey(), entry.getValue()))
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
            registerUsage(usages, secretName, secretCatalog.containsKey(secretName), service, path);
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
        UsageAccumulator usage = usages.computeIfAbsent(secretName, ignored -> new UsageAccumulator());
        usage.required = usage.required || required;
        usage.services.add(service);
        usage.paths.add(path);
    }

    private DeploymentSecretUsageItemSummary toItemSummary(PlatformSecretSummary summary,
                                                           String secretName,
                                                           UsageAccumulator usage) {
        boolean present = summary != null && summary.present();
        String source = summary != null ? summary.source() : "UNMANAGED";
        boolean required = usage.required || (summary != null && summary.required());
        String status;
        String message;
        if (required && !present) {
            status = "MISSING";
            message = secretName + " is referenced by this deployment but is not present in the platform secret store.";
        } else if (present) {
            status = "READY";
            message = secretName + " is present and referenced by the deployment.";
        } else {
            status = "WARNING";
            message = secretName + " is referenced, but it is outside the current managed platform secret catalog.";
        }
        return new DeploymentSecretUsageItemSummary(
            secretName,
            summary != null ? summary.displayName() : secretName,
            required,
            present,
            source,
            status,
            List.copyOf(usage.services),
            List.copyOf(usage.paths),
            message
        );
    }

    private String referencedSecretName(String value) {
        Matcher matcher = SECRET_REFERENCE_PATTERN.matcher(value.trim());
        if (!matcher.matches()) {
            return null;
        }
        return matcher.group(1);
    }

    private JsonNode readJson(String value) {
        try {
            return value == null || value.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse deployment draft config JSON.", ex);
        }
    }

    private static final class UsageAccumulator {
        private boolean required;
        private final Set<String> services = new LinkedHashSet<>();
        private final Set<String> paths = new LinkedHashSet<>();
    }
}
