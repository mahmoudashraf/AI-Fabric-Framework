package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class TenantScopedVectorHandleResolver {

    public String resolvePineconeNamespacePrefix(DeploymentEntity deployment, JsonNode providerConfig) {
        String namespacePrefix = ManagedDeploymentProfileCatalog.pineconeNamespacePrefix(providerConfig);
        if (namespacePrefix.isBlank() && ManagedDeploymentProfileCatalog.sharedVectorStorageRequested(providerConfig)) {
            namespacePrefix = defaultScopedNamespacePrefix(deployment);
        }
        return namespacePrefix;
    }

    public String resolveQdrantCollectionPrefix(DeploymentEntity deployment, JsonNode providerConfig) {
        String collectionPrefix = ManagedDeploymentProfileCatalog.qdrantCollectionPrefix(providerConfig);
        if (collectionPrefix.isBlank() && ManagedDeploymentProfileCatalog.sharedVectorStorageRequested(providerConfig)) {
            collectionPrefix = defaultScopedCollectionPrefix(deployment);
        }
        return collectionPrefix;
    }

    public String resolveWeaviateClassPrefix(DeploymentEntity deployment, JsonNode providerConfig) {
        String classPrefix = ManagedDeploymentProfileCatalog.weaviateClassPrefix(providerConfig);
        if (classPrefix.isBlank() && ManagedDeploymentProfileCatalog.sharedVectorStorageRequested(providerConfig)) {
            classPrefix = defaultWeaviateClassPrefix(deployment);
        }
        return classPrefix;
    }

    public boolean resolveWeaviateNativeMultiTenancyEnabled(JsonNode providerConfig) {
        return ManagedDeploymentProfileCatalog.sharedVectorStorageRequested(providerConfig)
            || ManagedDeploymentProfileCatalog.weaviateNativeMultiTenancyEnabled(providerConfig);
    }

    public String resolveWeaviateTenantName(DeploymentEntity deployment, JsonNode providerConfig) {
        String tenantName = ManagedDeploymentProfileCatalog.weaviateTenantName(providerConfig);
        if (tenantName.isBlank() && resolveWeaviateNativeMultiTenancyEnabled(providerConfig)) {
            tenantName = defaultTenantScopeToken(deployment);
        }
        return tenantName;
    }

    public String resolveMilvusCollectionPrefix(DeploymentEntity deployment, JsonNode providerConfig) {
        String collectionPrefix = ManagedDeploymentProfileCatalog.milvusCollectionPrefix(providerConfig);
        if (collectionPrefix.isBlank() && ManagedDeploymentProfileCatalog.sharedVectorStorageRequested(providerConfig)) {
            collectionPrefix = defaultScopedCollectionPrefix(deployment);
        }
        return collectionPrefix;
    }

    String defaultScopedNamespacePrefix(DeploymentEntity deployment) {
        return defaultCustomerScopeToken(deployment) + "--" + defaultTenantScopeToken(deployment);
    }

    String defaultScopedCollectionPrefix(DeploymentEntity deployment) {
        return underscoreScopeToken(deployment.getCustomerId(), "customer")
            + "__"
            + underscoreScopeToken(deployment.getTenantId(), "tenant")
            + "__";
    }

    String defaultWeaviateClassPrefix(DeploymentEntity deployment) {
        return underscoreScopeToken(deployment.getCustomerId(), "customer") + "_";
    }

    String defaultCustomerScopeToken(DeploymentEntity deployment) {
        return hyphenScopeToken(deployment.getCustomerId(), "customer");
    }

    String defaultTenantScopeToken(DeploymentEntity deployment) {
        return hyphenScopeToken(deployment.getTenantId(), "tenant");
    }

    String hyphenScopeToken(String raw, String fallback) {
        String normalized = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        normalized = normalized.replaceAll("[^a-z0-9]+", "-");
        normalized = normalized.replaceAll("^-+", "").replaceAll("-+$", "");
        if (normalized.isBlank()) {
            normalized = fallback;
        }
        if (Character.isDigit(normalized.charAt(0))) {
            normalized = fallback + "-" + normalized;
        }
        return normalized;
    }

    String underscoreScopeToken(String raw, String fallback) {
        String normalized = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        normalized = normalized.replaceAll("[^a-z0-9]+", "_");
        normalized = normalized.replaceAll("^_+", "").replaceAll("_+$", "");
        normalized = normalized.replaceAll("_+", "_");
        if (normalized.isBlank()) {
            normalized = fallback;
        }
        if (Character.isDigit(normalized.charAt(0))) {
            normalized = fallback + "_" + normalized;
        }
        return normalized;
    }
}
