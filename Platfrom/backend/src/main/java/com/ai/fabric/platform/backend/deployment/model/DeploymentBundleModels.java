package com.ai.fabric.platform.backend.deployment.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;

public final class DeploymentBundleModels {

    private DeploymentBundleModels() {
    }

    public static final String SCHEMA_VERSION = "loomai.deployment-export.v1";

    public enum ExportMode {
        CONFIG_ONLY,
        SEALED_BACKUP
    }

    public enum ImportMode {
        CONFIG_ONLY_CLONE,
        SEALED_CLONE,
        RESTORE_IN_PLACE
    }

    public enum RecipientType {
        OPERATOR_PUBLIC_KEY
    }

    public enum SecretClassification {
        SEALED_EXPORTABLE,
        REGENERATE_RECOMMENDED,
        FORBIDDEN,
        ENVIRONMENT_BOUND,
        MISSING_REFERENCE
    }

    public record DeploymentExportPreviewRequest(
        ExportMode exportMode,
        Boolean includeReleaseEvidence
    ) {
        public ExportMode normalizedExportMode() {
            return exportMode == null ? ExportMode.CONFIG_ONLY : exportMode;
        }
    }

    public record DeploymentExportRequest(
        ExportMode exportMode,
        String reason,
        ExportRecipient recipient,
        Boolean includeReleaseEvidence,
        Boolean includeProviderMappings
    ) {
        public ExportMode normalizedExportMode() {
            return exportMode == null ? ExportMode.CONFIG_ONLY : exportMode;
        }
    }

    public record ExportRecipient(
        RecipientType type,
        String publicKeyPem
    ) {
    }

    public record DeploymentImportPreviewRequest(
        JsonNode bundle,
        ImportMode importMode,
        String targetDeploymentId,
        String newDeploymentName,
        String targetEnvironment,
        String targetCustomerId,
        String targetTenantId,
        String privateKeyPem,
        String reason
    ) {
        public ImportMode normalizedImportMode() {
            return importMode == null ? ImportMode.CONFIG_ONLY_CLONE : importMode;
        }
    }

    public record DeploymentImportRequest(
        JsonNode bundle,
        ImportMode importMode,
        String targetDeploymentId,
        String newDeploymentName,
        String targetEnvironment,
        String targetCustomerId,
        String targetTenantId,
        String privateKeyPem,
        String reason
    ) {
        public ImportMode normalizedImportMode() {
            return importMode == null ? ImportMode.CONFIG_ONLY_CLONE : importMode;
        }
    }

    public record DeploymentBundleSecretInventoryItem(
        String secretName,
        SecretClassification classification,
        boolean valuePresent,
        boolean valueIncluded,
        String restorePolicy,
        List<String> sources
    ) {
    }

    public record DeploymentBundleSecretSummary(
        int includedValues,
        int sealedEligible,
        int regenerateRecommended,
        int forbidden,
        int environmentBound,
        int missingReference,
        List<DeploymentBundleSecretInventoryItem> items
    ) {
    }

    public record DeploymentBundleExternalIntegrationImpact(
        boolean requiresCustomerEnvChange,
        List<String> changedValues,
        String reason
    ) {
    }

    public record DeploymentBundleExportPreviewSummary(
        String deploymentId,
        ExportMode exportMode,
        List<String> includedSections,
        DeploymentBundleSecretSummary secretSummary,
        DeploymentBundleExternalIntegrationImpact externalIntegrationImpact,
        List<String> warnings
    ) {
    }

    public record DeploymentBundleExportSummary(
        String exportId,
        String bundleId,
        ExportMode exportMode,
        String status,
        String bundleHash,
        String manifestHash,
        String secretEnvelopeHash,
        DeploymentBundleSecretSummary secretSummary,
        JsonNode bundle,
        Instant createdAt
    ) {
    }

    public record DeploymentBundleImportPreviewSummary(
        boolean schemaValid,
        boolean integrityValid,
        boolean secretsReadable,
        ImportMode importMode,
        String sourceDeploymentId,
        String targetDeploymentId,
        String newDeploymentName,
        DeploymentBundleExternalIntegrationImpact externalIntegrationImpact,
        List<String> blockingIssues,
        List<String> warnings,
        List<String> requiredSecretActions
    ) {
    }

    public record DeploymentBundleImportExecutionSummary(
        String importId,
        String status,
        ImportMode importMode,
        String deploymentId,
        String draftId,
        DeploymentBundleExternalIntegrationImpact externalIntegrationImpact,
        List<String> requiredSecretActions,
        List<String> nextSteps,
        Instant createdAt
    ) {
    }
}
