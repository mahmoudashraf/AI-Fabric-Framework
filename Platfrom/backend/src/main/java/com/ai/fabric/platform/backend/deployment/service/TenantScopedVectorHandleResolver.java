package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.util.StringUtils;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

@Service
public class TenantScopedVectorHandleResolver {

    public JsonNode targetScopedProviderConfig(DeploymentEntity deployment, JsonNode providerConfig, String targetProfileId) {
        if (!ManagedDeploymentProfileCatalog.sharedVectorStorageRequested(providerConfig)) return providerConfig;
        if (!StringUtils.hasText(deployment.getCustomerId()) || !StringUtils.hasText(deployment.getTenantId())
            || !StringUtils.hasText(deployment.getId()) || !StringUtils.hasText(targetProfileId)) {
            throw new IllegalStateException("Shared vector storage requires explicit customer, tenant, deployment and target scope.");
        }
        ObjectMapper mapper = new ObjectMapper();
        var identity = mapper.createObjectNode().put("customer", deployment.getCustomerId()).put("tenant", deployment.getTenantId())
            .put("deployment", deployment.getId()).put("target", targetProfileId);
        String scope = DeploymentEvidenceFingerprint.hash(mapper, identity);
        ObjectNode bound = (ObjectNode) providerConfig.deepCopy();
        switch (ManagedDeploymentProfileCatalog.resolveVectorStrategy(providerConfig)) {
            case "pinecone" -> bound.put("pineconeNamespacePrefix", boundedBase(resolvePineconeNamespacePrefix(deployment, providerConfig)) + "--" + scope);
            case "qdrant" -> bound.put("qdrantCollectionPrefix", boundedBase(resolveQdrantCollectionPrefix(deployment, providerConfig)) + scope + "__");
            case "milvus" -> bound.put("milvusCollectionPrefix", boundedBase(resolveMilvusCollectionPrefix(deployment, providerConfig)) + scope + "__");
            case "weaviate" -> {
                bound.put("weaviateClassPrefix", boundedBase(resolveWeaviateClassPrefix(deployment, providerConfig)) + "Scope" + scope);
                bound.put("weaviateTenantName", scope);
            }
            default -> throw new IllegalStateException("Selected vector backend does not support reviewed target-scoped shared storage.");
        }
        return bound;
    }

    private String boundedBase(String value) {
        if (value.length() > 120) throw new IllegalStateException("Reviewed shared vector prefix is too long for target scope.");
        return value;
    }

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
        return normalizeWeaviateClassPrefix(classPrefix);
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

    String normalizeWeaviateClassPrefix(String classPrefix) {
        if (classPrefix == null || classPrefix.isBlank()) {
            return "";
        }
        return baseClassName(classPrefix);
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

    private String baseClassName(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            return "Entity_" + shortHash("");
        }

        StringBuilder base = new StringBuilder();
        boolean upperNext = true;
        for (int index = 0; index < normalized.length(); index++) {
            char current = normalized.charAt(index);
            if (Character.isLetterOrDigit(current)) {
                base.append(upperNext ? Character.toUpperCase(current) : current);
                upperNext = false;
            } else {
                upperNext = true;
            }
        }

        if (base.isEmpty()) {
            base.append("Entity");
        }
        if (!Character.isLetter(base.charAt(0))) {
            base.insert(0, "Entity");
        }
        if (!Character.isUpperCase(base.charAt(0))) {
            base.setCharAt(0, Character.toUpperCase(base.charAt(0)));
        }
        return base + "_" + shortHash(normalized);
    }

    private String shortHash(String value) {
        String compact = UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8))
            .toString()
            .replace("-", "");
        return compact.substring(0, 8);
    }
}
