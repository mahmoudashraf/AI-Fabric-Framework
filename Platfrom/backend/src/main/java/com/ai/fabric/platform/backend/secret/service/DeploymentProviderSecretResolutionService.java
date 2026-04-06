package com.ai.fabric.platform.backend.secret.service;

import com.ai.fabric.platform.backend.secret.entity.DeploymentProviderSecretBindingEntity;
import com.ai.fabric.platform.backend.secret.entity.DeploymentProviderSecretBindingMode;
import com.ai.fabric.platform.backend.secret.entity.PlatformSecretCleanupPolicy;
import com.ai.fabric.platform.backend.secret.entity.PlatformSecretEntity;
import com.ai.fabric.platform.backend.secret.entity.PlatformSecretOwnerType;
import com.ai.fabric.platform.backend.secret.entity.PlatformSecretScopeType;
import com.ai.fabric.platform.backend.secret.model.DeploymentSecretResolutionSummary;
import com.ai.fabric.platform.backend.secret.repository.DeploymentProviderSecretBindingRepository;
import com.ai.fabric.platform.backend.secret.repository.PlatformSecretRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DeploymentProviderSecretResolutionService {

    private final PlatformSecretService platformSecretService;
    private final PlatformSecretRepository platformSecretRepository;
    private final DeploymentProviderSecretBindingRepository bindingRepository;
    private final DeploymentSecretPurposeCatalog purposeCatalog;

    @Autowired
    public DeploymentProviderSecretResolutionService(PlatformSecretService platformSecretService,
                                                    PlatformSecretRepository platformSecretRepository,
                                                    DeploymentProviderSecretBindingRepository bindingRepository,
                                                    DeploymentSecretPurposeCatalog purposeCatalog) {
        this.platformSecretService = platformSecretService;
        this.platformSecretRepository = platformSecretRepository;
        this.bindingRepository = bindingRepository;
        this.purposeCatalog = purposeCatalog;
    }

    public DeploymentProviderSecretResolutionService(PlatformSecretService platformSecretService) {
        this(platformSecretService, null, null, new DeploymentSecretPurposeCatalog());
    }

    public DeploymentSecretResolutionSummary summarize(String deploymentId,
                                                       String secretPurpose) {
        return resolve(deploymentId, secretPurpose, null, null).summary();
    }

    public DeploymentSecretResolutionSummary summarize(String deploymentId,
                                                       String secretPurpose,
                                                       String managedSecretName) {
        return resolve(deploymentId, secretPurpose, managedSecretName, null).summary();
    }

    public DeploymentSecretResolutionSummary summarize(String deploymentId,
                                                       String secretPurpose,
                                                       String managedSecretName,
                                                       String secondaryManagedSecretName) {
        return resolve(deploymentId, secretPurpose, managedSecretName, secondaryManagedSecretName).summary();
    }

    public ResolvedSecretValue resolve(String deploymentId,
                                       String secretPurpose,
                                       String managedSecretName) {
        return resolve(deploymentId, secretPurpose, managedSecretName, null);
    }

    public ResolvedSecretValue resolve(String deploymentId,
                                       String secretPurpose,
                                       String managedSecretName,
                                       String secondaryManagedSecretName) {
        DeploymentSecretPurposeCatalog.SecretPurposeDefinition definition = purposeCatalog.requireDefinition(secretPurpose);
        ManagedSecretNames managedSecretNames = managedSecretNames(
            deploymentId,
            definition,
            managedSecretName,
            secondaryManagedSecretName
        );
        DeploymentProviderSecretBindingEntity binding = binding(deploymentId, definition.purpose());
        DeploymentProviderSecretBindingMode bindingMode = binding == null
            ? DeploymentProviderSecretBindingMode.ALLOW_STANDARD_PRECEDENCE
            : binding.getBindingMode();

        if (binding != null) {
            ResolvedSecretValue override = resolveCandidate(
                deploymentId,
                definition,
                binding.getSecretName(),
                binding.getSecondarySecretName(),
                bindingMode.name(),
                false,
                "DEPLOYMENT_OVERRIDE_PRESENT",
                "Deployment override is active for " + definition.displayName() + "."
            );
            if (override.resolved()) {
                return override;
            }
            if (bindingMode == DeploymentProviderSecretBindingMode.REQUIRE_OVERRIDE) {
                return unresolved(
                    deploymentId,
                    definition,
                    bindingMode.name(),
                    false,
                    "DEPLOYMENT_OVERRIDE_REQUIRED_MISSING",
                    "Deployment override is required for " + definition.displayName() + " but the bound secret is missing."
                );
            }
        }

        ResolvedSecretValue managed = resolveCandidate(
            deploymentId,
            definition,
            managedSecretNames.primarySecretName(),
            managedSecretNames.secondarySecretName(),
            bindingMode.name(),
            binding != null,
            "DEPLOYMENT_MANAGED_PRESENT",
            "Platform-managed deployment secret is active for " + definition.displayName() + "."
        );
        if (managed.resolved()) {
            return managed;
        }

        ResolvedSecretValue global = resolveCandidate(
            deploymentId,
            definition,
            definition.primaryGlobalSecretName(),
            definition.secondaryGlobalSecretName(),
            bindingMode.name(),
            binding != null,
            "GLOBAL_DEFAULT_PRESENT",
            "Global platform secret is active for " + definition.displayName() + "."
        );
        if (global.resolved()) {
            return global;
        }

        return unresolved(
            deploymentId,
            definition,
            bindingMode.name(),
            binding != null,
            binding != null ? "DEPLOYMENT_OVERRIDE_MISSING" : "NO_EFFECTIVE_SECRET",
            binding != null
                ? "Deployment override for " + definition.displayName() + " is missing and no managed or global fallback is available."
                : "No effective secret is available for " + definition.displayName() + "."
        );
    }

    private ManagedSecretNames managedSecretNames(String deploymentId,
                                                  DeploymentSecretPurposeCatalog.SecretPurposeDefinition definition,
                                                  String primarySecretName,
                                                  String secondarySecretName) {
        String normalizedPrimary = trimToNull(primarySecretName);
        String normalizedSecondary = trimToNull(secondarySecretName);
        String deploymentToken = normalizedDeploymentToken(deploymentId);
        if (!StringUtils.hasText(deploymentToken)) {
            return new ManagedSecretNames(normalizedPrimary, normalizedSecondary);
        }
        return switch (definition.purpose()) {
            case "PINECONE_API_KEY" -> new ManagedSecretNames(
                StringUtils.hasText(normalizedPrimary)
                    ? normalizedPrimary
                    : "MANAGED_PINECONE_API_KEY_DEP_" + deploymentToken,
                normalizedSecondary
            );
            case "QDRANT_API_KEY" -> new ManagedSecretNames(
                StringUtils.hasText(normalizedPrimary)
                    ? normalizedPrimary
                    : "MANAGED_QDRANT_DB_API_KEY_DEP_" + deploymentToken,
                normalizedSecondary
            );
            case "MILVUS_RUNTIME_CREDENTIALS" -> new ManagedSecretNames(
                StringUtils.hasText(normalizedPrimary)
                    ? normalizedPrimary
                    : "MANAGED_MILVUS_USERNAME_DEP_" + deploymentToken,
                StringUtils.hasText(normalizedSecondary)
                    ? normalizedSecondary
                    : "MANAGED_MILVUS_PASSWORD_DEP_" + deploymentToken
            );
            default -> new ManagedSecretNames(normalizedPrimary, normalizedSecondary);
        };
    }

    private ResolvedSecretValue resolveCandidate(String deploymentId,
                                                 DeploymentSecretPurposeCatalog.SecretPurposeDefinition definition,
                                                 String primarySecretName,
                                                 String secondarySecretName,
                                                 String bindingMode,
                                                 boolean fallbackUsed,
                                                 String reasonCode,
                                                 String message) {
        String normalizedPrimary = trimToNull(primarySecretName);
        if (!StringUtils.hasText(normalizedPrimary)) {
            return unresolved(deploymentId, definition, bindingMode, fallbackUsed, reasonCode, message);
        }
        String primaryValue = trimToNull(platformSecretService.resolveSecret(normalizedPrimary));
        boolean primaryPresent = platformSecretService.isSecretPresent(normalizedPrimary) || StringUtils.hasText(primaryValue);
        if (definition.paired()) {
            String normalizedSecondary = trimToNull(secondarySecretName);
            if (!StringUtils.hasText(normalizedSecondary)) {
                return unresolved(deploymentId, definition, bindingMode, fallbackUsed, reasonCode, message);
            }
            String secondaryValue = trimToNull(platformSecretService.resolveSecret(normalizedSecondary));
            boolean secondaryPresent = platformSecretService.isSecretPresent(normalizedSecondary) || StringUtils.hasText(secondaryValue);
            if (!primaryPresent || !secondaryPresent) {
                return unresolved(deploymentId, definition, bindingMode, fallbackUsed, reasonCode, message);
            }
            SecretMetadata metadata = metadataForSecret(normalizedPrimary);
            return new ResolvedSecretValue(
                summary(
                    deploymentId,
                    definition,
                    true,
                    bindingMode,
                    metadata.scopeType().name(),
                    metadata.ownerType().name(),
                    normalizedPrimary,
                    normalizedSecondary,
                    fallbackUsed,
                    reasonCode,
                    message
                ),
                primaryValue,
                secondaryValue
            );
        }
        if (!primaryPresent) {
            return unresolved(deploymentId, definition, bindingMode, fallbackUsed, reasonCode, message);
        }
        SecretMetadata metadata = metadataForSecret(normalizedPrimary);
        String resolvedReasonCode = reasonCode;
        if (metadata.scopeType() == PlatformSecretScopeType.GLOBAL_PLATFORM
            && metadata.fromEnvironmentFallback()) {
            resolvedReasonCode = "ENVIRONMENT_FALLBACK_USED";
        }
        return new ResolvedSecretValue(
            summary(
                deploymentId,
                definition,
                true,
                bindingMode,
                metadata.scopeType().name(),
                metadata.ownerType().name(),
                normalizedPrimary,
                definition.paired() ? trimToNull(secondarySecretName) : null,
                fallbackUsed,
                resolvedReasonCode,
                message
            ),
            primaryValue,
            null
        );
    }

    private ResolvedSecretValue unresolved(String deploymentId,
                                           DeploymentSecretPurposeCatalog.SecretPurposeDefinition definition,
                                           String bindingMode,
                                           boolean fallbackUsed,
                                           String reasonCode,
                                           String message) {
        return new ResolvedSecretValue(
            summary(
                deploymentId,
                definition,
                false,
                bindingMode,
                null,
                null,
                null,
                null,
                fallbackUsed,
                reasonCode,
                message
            ),
            null,
            null
        );
    }

    private DeploymentSecretResolutionSummary summary(String deploymentId,
                                                      DeploymentSecretPurposeCatalog.SecretPurposeDefinition definition,
                                                      boolean resolved,
                                                      String bindingMode,
                                                      String scopeType,
                                                      String ownerType,
                                                      String secretName,
                                                      String secondarySecretName,
                                                      boolean fallbackUsed,
                                                      String reasonCode,
                                                      String message) {
        return new DeploymentSecretResolutionSummary(
            deploymentId,
            definition.purpose(),
            definition.displayName(),
            definition.paired(),
            resolved,
            bindingMode,
            scopeType,
            ownerType,
            secretName,
            secondarySecretName,
            fallbackUsed,
            reasonCode,
            message
        );
    }

    private DeploymentProviderSecretBindingEntity binding(String deploymentId, String secretPurpose) {
        if (bindingRepository == null || !StringUtils.hasText(deploymentId)) {
            return null;
        }
        return bindingRepository.findByDeploymentIdAndSecretPurpose(deploymentId, secretPurpose).orElse(null);
    }

    private SecretMetadata metadataForSecret(String secretName) {
        PlatformSecretEntity entity = platformSecretRepository == null ? null : platformSecretRepository.findById(secretName).orElse(null);
        if (entity != null) {
            PlatformSecretScopeType scopeType = entity.getScopeType();
            PlatformSecretOwnerType ownerType = entity.getOwnerType();
            if (scopeType != null && ownerType != null) {
                return new SecretMetadata(scopeType, ownerType, false);
            }
        }
        if ((platformSecretService != null && platformSecretService.isManagedSecretName(secretName))
            || (secretName != null && secretName.startsWith(PlatformSecretService.MANAGED_SECRET_PREFIX))) {
            return new SecretMetadata(PlatformSecretScopeType.DEPLOYMENT_MANAGED, PlatformSecretOwnerType.PLATFORM_MANAGED, false);
        }
        if ((platformSecretService != null && platformSecretService.isSupportedSecretName(secretName))
            || purposeCatalog.definition(secretName).isPresent()
            || "MILVUS_USERNAME".equals(secretName)
            || "MILVUS_PASSWORD".equals(secretName)) {
            boolean envFallback = platformSecretRepository == null
                || platformSecretRepository.findById(secretName).isEmpty();
            return new SecretMetadata(PlatformSecretScopeType.GLOBAL_PLATFORM, PlatformSecretOwnerType.PLATFORM, envFallback);
        }
        return new SecretMetadata(PlatformSecretScopeType.DEPLOYMENT_OVERRIDE, PlatformSecretOwnerType.DEPLOYMENT, false);
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String normalizedDeploymentToken(String deploymentId) {
        String normalized = trimToNull(deploymentId);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        return normalized.replaceAll("[^A-Za-z0-9]", "_").toUpperCase();
    }

    private record SecretMetadata(
        PlatformSecretScopeType scopeType,
        PlatformSecretOwnerType ownerType,
        boolean fromEnvironmentFallback
    ) {
    }

    private record ManagedSecretNames(
        String primarySecretName,
        String secondarySecretName
    ) {
    }

    public record ResolvedSecretValue(
        DeploymentSecretResolutionSummary summary,
        String value,
        String secondaryValue
    ) {
        public boolean resolved() {
            return summary != null && summary.resolved();
        }

        public String primarySecretName() {
            return summary == null ? null : summary.secretName();
        }

        public String secondarySecretName() {
            return summary == null ? null : summary.secondarySecretName();
        }
    }
}
