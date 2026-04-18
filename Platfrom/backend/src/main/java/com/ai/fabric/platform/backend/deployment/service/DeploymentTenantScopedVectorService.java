package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentTenantBindingSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentTenantScopedVectorRegistrySummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentTenantScopedVectorSummary;
import com.ai.fabric.platform.backend.tenant.service.PlatformCustomerTenantService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DeploymentTenantScopedVectorService {

    private final PlatformCustomerTenantService platformCustomerTenantService;
    private final TenantScopedVectorHandleResolver tenantScopedVectorHandleResolver;
    private final DeploymentTenantScopedVectorRegistryService deploymentTenantScopedVectorRegistryService;

    public DeploymentTenantScopedVectorService(PlatformCustomerTenantService platformCustomerTenantService,
                                               TenantScopedVectorHandleResolver tenantScopedVectorHandleResolver,
                                               DeploymentTenantScopedVectorRegistryService deploymentTenantScopedVectorRegistryService) {
        this.platformCustomerTenantService = platformCustomerTenantService;
        this.tenantScopedVectorHandleResolver = tenantScopedVectorHandleResolver;
        this.deploymentTenantScopedVectorRegistryService = deploymentTenantScopedVectorRegistryService;
    }

    public DeploymentTenantScopedVectorSummary build(DeploymentEntity deployment, JsonNode providerConfig) {
        DeploymentTenantBindingSummary binding = platformCustomerTenantService.summarizeBinding(deployment);
        String vectorStrategy = ManagedDeploymentProfileCatalog.resolveVectorStrategy(providerConfig);
        String vectorProvisioningMode = ManagedDeploymentProfileCatalog.resolveVectorProvisioningMode(providerConfig);
        String vectorStoragePosture = ManagedDeploymentProfileCatalog.resolveVectorStoragePosture(providerConfig);
        boolean sharedStorage = ManagedDeploymentProfileCatalog.sharedVectorStorageRequested(providerConfig);
        boolean migrationLocked = binding != null && !binding.mutable();
        String migrationMessage = migrationLocked
            ? "Deployment history exists. Tenant reassignment or shared-handle changes require a governed migration flow."
            : "Tenant binding and shared-handle settings remain editable because this deployment has no published or applied history yet.";

        String lifecycleOwner = lifecycleOwner(vectorStoragePosture, vectorProvisioningMode);
        String backupRestorePosture = backupRestorePosture(vectorStoragePosture, vectorProvisioningMode);

        DeploymentTenantScopedVectorSummary baseSummary;
        if (!sharedStorage) {
            baseSummary = new DeploymentTenantScopedVectorSummary(
                "READY",
                vectorStrategy,
                vectorProvisioningMode,
                vectorStoragePosture,
                false,
                lifecycleOwner,
                binding == null ? null : binding.customerId(),
                binding == null ? "Unknown customer" : binding.customerName(),
                binding == null ? null : binding.tenantId(),
                binding == null ? "Unknown tenant" : binding.tenantName(),
                scopeType(vectorStoragePosture),
                null,
                null,
                null,
                null,
                null,
                migrationLocked,
                migrationMessage,
                backupRestorePosture,
                null,
                summaryForNonShared(vectorStoragePosture, vectorStrategy)
            );
        } else if (!ManagedDeploymentProfileCatalog.supportsSharedVectorStorage(vectorStrategy, vectorProvisioningMode)) {
            baseSummary = blocked(
                binding,
                vectorStrategy,
                vectorProvisioningMode,
                vectorStoragePosture,
                migrationLocked,
                migrationMessage,
                "Shared storage is not supported for this provider/provisioning combination.",
                lifecycleOwner,
                backupRestorePosture
            );
        } else if (binding == null || !StringUtils.hasText(binding.customerId()) || !StringUtils.hasText(binding.tenantId())) {
            baseSummary = blocked(
                binding,
                vectorStrategy,
                vectorProvisioningMode,
                vectorStoragePosture,
                migrationLocked,
                migrationMessage,
                "Shared storage requires a valid customer and tenant binding.",
                lifecycleOwner,
                backupRestorePosture
            );
        } else {
            baseSummary = switch (vectorStrategy) {
                case ManagedDeploymentProfileCatalog.VECTOR_STRATEGY_PINECONE -> pineconeSummary(
                    deployment,
                    providerConfig,
                    binding,
                    migrationLocked,
                    migrationMessage,
                    lifecycleOwner,
                    backupRestorePosture
                );
                case ManagedDeploymentProfileCatalog.VECTOR_STRATEGY_QDRANT -> qdrantSummary(
                    deployment,
                    providerConfig,
                    binding,
                    migrationLocked,
                    migrationMessage,
                    lifecycleOwner,
                    backupRestorePosture
                );
                case ManagedDeploymentProfileCatalog.VECTOR_STRATEGY_WEAVIATE -> weaviateSummary(
                    deployment,
                    providerConfig,
                    binding,
                    migrationLocked,
                    migrationMessage,
                    lifecycleOwner,
                    backupRestorePosture
                );
                case ManagedDeploymentProfileCatalog.VECTOR_STRATEGY_MILVUS -> milvusSummary(
                    deployment,
                    providerConfig,
                    binding,
                    migrationLocked,
                    migrationMessage,
                    lifecycleOwner,
                    backupRestorePosture
                );
                default -> blocked(
                    binding,
                    vectorStrategy,
                    vectorProvisioningMode,
                    vectorStoragePosture,
                    migrationLocked,
                    migrationMessage,
                    "Shared storage requires a provider-native isolation model. The selected vector backend does not expose one.",
                    lifecycleOwner,
                    backupRestorePosture
                );
            };
        }

        DeploymentTenantScopedVectorRegistrySummary registry =
            deploymentTenantScopedVectorRegistryService.summarizeForDeployment(deployment, baseSummary);
        return withRegistry(baseSummary, registry);
    }

    private DeploymentTenantScopedVectorSummary pineconeSummary(DeploymentEntity deployment,
                                                                JsonNode providerConfig,
                                                                DeploymentTenantBindingSummary binding,
                                                                boolean migrationLocked,
                                                                String migrationMessage,
                                                                String lifecycleOwner,
                                                                String backupRestorePosture) {
        String namespacePrefix = tenantScopedVectorHandleResolver.resolvePineconeNamespacePrefix(deployment, providerConfig);
        return new DeploymentTenantScopedVectorSummary(
            "READY",
            ManagedDeploymentProfileCatalog.VECTOR_STRATEGY_PINECONE,
            ManagedDeploymentProfileCatalog.resolveVectorProvisioningMode(providerConfig),
            ManagedDeploymentProfileCatalog.resolveVectorStoragePosture(providerConfig),
            true,
            lifecycleOwner,
            binding.customerId(),
            binding.customerName(),
            binding.tenantId(),
            binding.tenantName(),
            "NAMESPACE_PREFIX",
            "Index",
            blankToFallback(ManagedDeploymentProfileCatalog.pineconeIndexName(providerConfig), "Configured Pinecone index"),
            namespacePrefix,
            null,
            namespacePrefix + "__<entity-type>",
            migrationLocked,
            migrationMessage,
            backupRestorePosture,
            null,
            "Tenant scope is enforced through Pinecone namespaces under the configured index."
        );
    }

    private DeploymentTenantScopedVectorSummary qdrantSummary(DeploymentEntity deployment,
                                                              JsonNode providerConfig,
                                                              DeploymentTenantBindingSummary binding,
                                                              boolean migrationLocked,
                                                              String migrationMessage,
                                                              String lifecycleOwner,
                                                              String backupRestorePosture) {
        String collectionPrefix = tenantScopedVectorHandleResolver.resolveQdrantCollectionPrefix(deployment, providerConfig);
        return new DeploymentTenantScopedVectorSummary(
            "READY",
            ManagedDeploymentProfileCatalog.VECTOR_STRATEGY_QDRANT,
            ManagedDeploymentProfileCatalog.resolveVectorProvisioningMode(providerConfig),
            ManagedDeploymentProfileCatalog.resolveVectorStoragePosture(providerConfig),
            true,
            lifecycleOwner,
            binding.customerId(),
            binding.customerName(),
            binding.tenantId(),
            binding.tenantName(),
            "COLLECTION_PREFIX",
            "Endpoint",
            blankToFallback(ManagedDeploymentProfileCatalog.qdrantHost(providerConfig), "Configured Qdrant cluster"),
            collectionPrefix,
            null,
            collectionPrefix + "<entity_type>",
            migrationLocked,
            migrationMessage,
            backupRestorePosture,
            null,
            "Tenant scope is enforced through Qdrant collection naming under the configured cluster."
        );
    }

    private DeploymentTenantScopedVectorSummary weaviateSummary(DeploymentEntity deployment,
                                                                JsonNode providerConfig,
                                                                DeploymentTenantBindingSummary binding,
                                                                boolean migrationLocked,
                                                                String migrationMessage,
                                                                String lifecycleOwner,
                                                                String backupRestorePosture) {
        boolean nativeMultiTenancyEnabled = ManagedDeploymentProfileCatalog.weaviateNativeMultiTenancyEnabled(providerConfig);
        if (!nativeMultiTenancyEnabled) {
            return blocked(
                binding,
                ManagedDeploymentProfileCatalog.VECTOR_STRATEGY_WEAVIATE,
                ManagedDeploymentProfileCatalog.resolveVectorProvisioningMode(providerConfig),
                ManagedDeploymentProfileCatalog.resolveVectorStoragePosture(providerConfig),
                migrationLocked,
                migrationMessage,
                "Shared Weaviate storage requires native multi-tenancy to be enabled.",
                lifecycleOwner,
                backupRestorePosture
            );
        }
        String classPrefix = tenantScopedVectorHandleResolver.resolveWeaviateClassPrefix(deployment, providerConfig);
        String tenantName = tenantScopedVectorHandleResolver.resolveWeaviateTenantName(deployment, providerConfig);
        return new DeploymentTenantScopedVectorSummary(
            "READY",
            ManagedDeploymentProfileCatalog.VECTOR_STRATEGY_WEAVIATE,
            ManagedDeploymentProfileCatalog.resolveVectorProvisioningMode(providerConfig),
            ManagedDeploymentProfileCatalog.resolveVectorStoragePosture(providerConfig),
            true,
            lifecycleOwner,
            binding.customerId(),
            binding.customerName(),
            binding.tenantId(),
            binding.tenantName(),
            "CLASS_AND_TENANT",
            "Host",
            blankToFallback(ManagedDeploymentProfileCatalog.weaviateHost(providerConfig), "Configured Weaviate cluster"),
            classPrefix,
            tenantName,
            classPrefix + "<EntityType> @ tenant " + tenantName,
            migrationLocked,
            migrationMessage,
            backupRestorePosture,
            null,
            "Tenant scope is enforced through Weaviate native multi-tenancy plus tenant-scoped class naming."
        );
    }

    private DeploymentTenantScopedVectorSummary milvusSummary(DeploymentEntity deployment,
                                                              JsonNode providerConfig,
                                                              DeploymentTenantBindingSummary binding,
                                                              boolean migrationLocked,
                                                              String migrationMessage,
                                                              String lifecycleOwner,
                                                              String backupRestorePosture) {
        String collectionPrefix = tenantScopedVectorHandleResolver.resolveMilvusCollectionPrefix(deployment, providerConfig);
        String databaseName = blankToFallback(
            ManagedDeploymentProfileCatalog.milvusDatabaseName(providerConfig),
            "default"
        );
        return new DeploymentTenantScopedVectorSummary(
            "READY",
            ManagedDeploymentProfileCatalog.VECTOR_STRATEGY_MILVUS,
            ManagedDeploymentProfileCatalog.resolveVectorProvisioningMode(providerConfig),
            ManagedDeploymentProfileCatalog.resolveVectorStoragePosture(providerConfig),
            true,
            lifecycleOwner,
            binding.customerId(),
            binding.customerName(),
            binding.tenantId(),
            binding.tenantName(),
            "DATABASE_AND_COLLECTION_PREFIX",
            "Host",
            blankToFallback(ManagedDeploymentProfileCatalog.milvusHost(providerConfig), "Configured Milvus cluster"),
            collectionPrefix,
            null,
            databaseName + "/" + collectionPrefix + "<entity_type>",
            migrationLocked,
            migrationMessage,
            backupRestorePosture,
            null,
            "Tenant scope is enforced through a customer-bounded Milvus cluster plus database and collection boundaries."
        );
    }

    private DeploymentTenantScopedVectorSummary blocked(DeploymentTenantBindingSummary binding,
                                                        String vectorStrategy,
                                                        String vectorProvisioningMode,
                                                        String vectorStoragePosture,
                                                        boolean migrationLocked,
                                                        String migrationMessage,
                                                        String summaryMessage,
                                                        String lifecycleOwner,
                                                        String backupRestorePosture) {
        return new DeploymentTenantScopedVectorSummary(
            "BLOCKED",
            vectorStrategy,
            vectorProvisioningMode,
            vectorStoragePosture,
            ManagedDeploymentProfileCatalog.VECTOR_STORAGE_POSTURE_SHARED.equals(vectorStoragePosture),
            lifecycleOwner,
            binding == null ? null : binding.customerId(),
            binding == null ? "Unknown customer" : binding.customerName(),
            binding == null ? null : binding.tenantId(),
            binding == null ? "Unknown tenant" : binding.tenantName(),
            "UNRESOLVED",
            null,
            null,
            null,
            null,
            null,
            migrationLocked,
            migrationMessage,
            backupRestorePosture,
            null,
            summaryMessage
        );
    }

    private DeploymentTenantScopedVectorSummary withRegistry(DeploymentTenantScopedVectorSummary summary,
                                                             DeploymentTenantScopedVectorRegistrySummary registry) {
        return new DeploymentTenantScopedVectorSummary(
            summary.status(),
            summary.vectorStrategy(),
            summary.vectorProvisioningMode(),
            summary.vectorStoragePosture(),
            summary.sharedStorage(),
            summary.lifecycleOwner(),
            summary.customerId(),
            summary.customerName(),
            summary.tenantId(),
            summary.tenantName(),
            summary.scopeType(),
            summary.rootResourceLabel(),
            summary.rootResourceValue(),
            summary.scopePrefix(),
            summary.tenantHandle(),
            summary.scopePattern(),
            summary.migrationLocked(),
            summary.migrationMessage(),
            summary.backupRestorePosture(),
            registry,
            summary.summaryMessage()
        );
    }

    private String lifecycleOwner(String vectorStoragePosture, String vectorProvisioningMode) {
        if (ManagedDeploymentProfileCatalog.VECTOR_STORAGE_POSTURE_EMBEDDED.equals(vectorStoragePosture)) {
            return "RUNTIME_LOCAL_STORAGE";
        }
        if (ManagedDeploymentProfileCatalog.VECTOR_STORAGE_POSTURE_SHARED.equals(vectorStoragePosture)) {
            return ManagedDeploymentProfileCatalog.VECTOR_PROVISIONING_MODE_PLATFORM_MANAGED.equals(vectorProvisioningMode)
                ? "PLATFORM_MANAGED_SHARED_RESOURCE"
                : "CUSTOMER_MANAGED_EXTERNAL_RESOURCE";
        }
        return ManagedDeploymentProfileCatalog.VECTOR_PROVISIONING_MODE_PLATFORM_MANAGED.equals(vectorProvisioningMode)
            ? "PLATFORM_MANAGED_RESOURCE"
            : "CUSTOMER_MANAGED_RESOURCE";
    }

    private String backupRestorePosture(String vectorStoragePosture, String vectorProvisioningMode) {
        if (ManagedDeploymentProfileCatalog.VECTOR_STORAGE_POSTURE_EMBEDDED.equals(vectorStoragePosture)) {
            return "Embedded runtime storage follows deployment lifecycle and is not suitable for enterprise shared-storage backup posture.";
        }
        if (ManagedDeploymentProfileCatalog.VECTOR_STORAGE_POSTURE_SHARED.equals(vectorStoragePosture)) {
            if (ManagedDeploymentProfileCatalog.VECTOR_PROVISIONING_MODE_PLATFORM_MANAGED.equals(vectorProvisioningMode)) {
                return "The platform manages the shared vector root, while tenant isolation is enforced through scoped handles beneath that shared service.";
            }
            return "Backup and restore posture remains provider-owned outside the platform. The platform tracks tenant binding and scoped handle resolution.";
        }
        return ManagedDeploymentProfileCatalog.VECTOR_PROVISIONING_MODE_PLATFORM_MANAGED.equals(vectorProvisioningMode)
            ? "The platform provisions the resource, but backup and restore posture remains vendor-specific."
            : "Backup and restore posture remains provider-owned outside the platform.";
    }

    private String scopeType(String vectorStoragePosture) {
        if (ManagedDeploymentProfileCatalog.VECTOR_STORAGE_POSTURE_EMBEDDED.equals(vectorStoragePosture)) {
            return "EMBEDDED_RUNTIME";
        }
        if (ManagedDeploymentProfileCatalog.VECTOR_STORAGE_POSTURE_DEDICATED.equals(vectorStoragePosture)) {
            return "DEDICATED_RESOURCE";
        }
        return "UNRESOLVED";
    }

    private String summaryForNonShared(String vectorStoragePosture, String vectorStrategy) {
        return switch (vectorStoragePosture) {
            case ManagedDeploymentProfileCatalog.VECTOR_STORAGE_POSTURE_EMBEDDED ->
                "This deployment uses embedded runtime-local vector storage. Shared tenant infrastructure is not in play.";
            case ManagedDeploymentProfileCatalog.VECTOR_STORAGE_POSTURE_DEDICATED ->
                "This deployment uses a dedicated " + vectorStrategy + " resource posture rather than tenant-scoped shared storage.";
            default ->
                "Tenant-scoped shared storage is not configured for this deployment.";
        };
    }

    private String blankToFallback(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }
}
