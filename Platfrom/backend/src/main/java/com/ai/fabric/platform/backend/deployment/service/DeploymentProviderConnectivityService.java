package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentDraftEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderConnectivityProbeSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderConnectivitySummary;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class DeploymentProviderConnectivityService {

    private final PlatformSecretService platformSecretService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final QdrantCloudControlPlaneClient qdrantCloudControlPlaneClient;
    private final PineconeControlPlaneClient pineconeControlPlaneClient;
    private final ZillizCloudControlPlaneClient zillizCloudControlPlaneClient;

    @Autowired
    public DeploymentProviderConnectivityService(PlatformSecretService platformSecretService,
                                                 ObjectMapper objectMapper,
                                                 QdrantCloudControlPlaneClient qdrantCloudControlPlaneClient,
                                                 PineconeControlPlaneClient pineconeControlPlaneClient,
                                                 ZillizCloudControlPlaneClient zillizCloudControlPlaneClient) {
        this(
            platformSecretService,
            objectMapper,
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
            qdrantCloudControlPlaneClient,
            pineconeControlPlaneClient,
            zillizCloudControlPlaneClient
        );
    }

    DeploymentProviderConnectivityService(PlatformSecretService platformSecretService,
                                          ObjectMapper objectMapper,
                                          HttpClient httpClient) {
        this(
            platformSecretService,
            objectMapper,
            httpClient,
            new QdrantCloudControlPlaneClient(objectMapper, httpClient),
            new PineconeControlPlaneClient(objectMapper, httpClient),
            new ZillizCloudControlPlaneClient(objectMapper, httpClient)
        );
    }

    DeploymentProviderConnectivityService(PlatformSecretService platformSecretService,
                                          ObjectMapper objectMapper,
                                          HttpClient httpClient,
                                          QdrantCloudControlPlaneClient qdrantCloudControlPlaneClient) {
        this(
            platformSecretService,
            objectMapper,
            httpClient,
            qdrantCloudControlPlaneClient,
            new PineconeControlPlaneClient(objectMapper, httpClient),
            new ZillizCloudControlPlaneClient(objectMapper, httpClient)
        );
    }

    DeploymentProviderConnectivityService(PlatformSecretService platformSecretService,
                                          ObjectMapper objectMapper,
                                          HttpClient httpClient,
                                          PineconeControlPlaneClient pineconeControlPlaneClient) {
        this(
            platformSecretService,
            objectMapper,
            httpClient,
            new QdrantCloudControlPlaneClient(objectMapper, httpClient),
            pineconeControlPlaneClient,
            new ZillizCloudControlPlaneClient(objectMapper, httpClient)
        );
    }

    DeploymentProviderConnectivityService(PlatformSecretService platformSecretService,
                                          ObjectMapper objectMapper,
                                          HttpClient httpClient,
                                          ZillizCloudControlPlaneClient zillizCloudControlPlaneClient) {
        this(
            platformSecretService,
            objectMapper,
            httpClient,
            new QdrantCloudControlPlaneClient(objectMapper, httpClient),
            new PineconeControlPlaneClient(objectMapper, httpClient),
            zillizCloudControlPlaneClient
        );
    }

    DeploymentProviderConnectivityService(PlatformSecretService platformSecretService,
                                          ObjectMapper objectMapper,
                                          HttpClient httpClient,
                                          QdrantCloudControlPlaneClient qdrantCloudControlPlaneClient,
                                          PineconeControlPlaneClient pineconeControlPlaneClient,
                                          ZillizCloudControlPlaneClient zillizCloudControlPlaneClient) {
        this.platformSecretService = platformSecretService;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.qdrantCloudControlPlaneClient = qdrantCloudControlPlaneClient;
        this.pineconeControlPlaneClient = pineconeControlPlaneClient;
        this.zillizCloudControlPlaneClient = zillizCloudControlPlaneClient;
    }

    public DeploymentProviderConnectivitySummary probe(DeploymentEntity deployment, DeploymentDraftEntity draft) {
        return probe(
            deployment.getId(),
            deployment.getName(),
            readJson(draft.getProviderConfigJson()),
            readJson(draft.getEntityConfigJson())
        );
    }

    DeploymentProviderConnectivitySummary probe(String deploymentId,
                                                String deploymentName,
                                                JsonNode providerConfig,
                                                JsonNode entityConfig) {
        List<DeploymentProviderConnectivityProbeSummary> probes = new ArrayList<>();
        String vectorStrategy = ManagedDeploymentProfileCatalog.resolveVectorStrategy(providerConfig);
        switch (vectorStrategy) {
            case ManagedDeploymentProfileCatalog.VECTOR_STRATEGY_PINECONE -> probes.add(probePinecone(providerConfig));
            case ManagedDeploymentProfileCatalog.VECTOR_STRATEGY_QDRANT -> probes.add(
                ManagedDeploymentProfileCatalog.qdrantPlatformManaged(providerConfig)
                    ? probeQdrantCloud(providerConfig)
                    : probeQdrant(providerConfig)
            );
            case ManagedDeploymentProfileCatalog.VECTOR_STRATEGY_WEAVIATE -> probes.add(probeWeaviate(providerConfig));
            case ManagedDeploymentProfileCatalog.VECTOR_STRATEGY_MILVUS -> probes.add(
                ManagedDeploymentProfileCatalog.milvusPlatformManaged(providerConfig)
                    ? probeZillizCloud(providerConfig)
                    : new DeploymentProviderConnectivityProbeSummary(
                        "milvus_connectivity",
                        "Milvus connectivity",
                        "SKIPPED",
                        normalizeMilvusEndpoint(providerConfig),
                        "Milvus does not expose a platform-safe HTTP readiness probe in this slice. Verify host and credentials during apply."
                    )
            );
            default -> probes.add(new DeploymentProviderConnectivityProbeSummary(
                "local_vector_backend",
                "Local vector backend",
                "SKIPPED",
                vectorStrategy,
                "Selected vector backend is local to the runtime and does not require an external vendor connectivity probe."
            ));
        }

        if (ManagedDeploymentProfileCatalog.EMBEDDING_PROVIDER_REST.equals(
            ManagedDeploymentProfileCatalog.resolveEmbeddingProvider(providerConfig)
        )) {
            probes.add(probeRestEmbedding(providerConfig));
        }

        ManagedVectorSummary managedVectorSummary = summarizeManagedVectorProvisioning(providerConfig, entityConfig);
        String vectorProvisioningMode = ManagedDeploymentProfileCatalog.resolveVectorProvisioningMode(providerConfig);

        return new DeploymentProviderConnectivitySummary(
            deploymentId,
            deploymentName,
            ManagedDeploymentProfileCatalog.resolveLlmProvider(providerConfig),
            ManagedDeploymentProfileCatalog.resolveEmbeddingProvider(providerConfig),
            vectorStrategy,
            vectorProvisioningMode,
            managedVectorSummary.enabled(),
            managedVectorSummary.mode(),
            List.copyOf(managedVectorSummary.targets()),
            managedVectorSummary.message(),
            List.copyOf(probes),
            summarize(probes)
        );
    }

    private DeploymentProviderConnectivityProbeSummary probePinecone(JsonNode providerConfig) {
        String apiKey = platformSecretService.resolveSecret("PINECONE_API_KEY");
        if (!StringUtils.hasText(apiKey)) {
            return new DeploymentProviderConnectivityProbeSummary(
                "pinecone_control_plane",
                "Pinecone control plane",
                "BLOCKED",
                "https://api.pinecone.io/indexes",
                "PINECONE_API_KEY is missing, so the platform cannot verify Pinecone connectivity."
            );
        }
        try {
            pineconeControlPlaneClient.verifyControlPlaneAccess(apiKey);
            return new DeploymentProviderConnectivityProbeSummary(
                "pinecone_control_plane",
                "Pinecone control plane",
                "READY",
                PineconeControlPlaneClient.API_BASE_URL + "/indexes",
                ManagedDeploymentProfileCatalog.pineconePlatformManaged(providerConfig)
                    ? "Pinecone control-plane access is ready for platform-managed serverless index provisioning."
                    : "Pinecone control-plane access is reachable for the configured project/API key."
            );
        } catch (RuntimeException ex) {
            return new DeploymentProviderConnectivityProbeSummary(
                "pinecone_control_plane",
                "Pinecone control plane",
                "FAILED",
                PineconeControlPlaneClient.API_BASE_URL + "/indexes",
                "Pinecone control-plane probe failed: " + ex.getMessage()
            );
        }
    }

    private DeploymentProviderConnectivityProbeSummary probeQdrantCloud(JsonNode providerConfig) {
        String managementApiKey = platformSecretService.resolveSecret("QDRANT_CLOUD_MANAGEMENT_API_KEY");
        if (!StringUtils.hasText(managementApiKey)) {
            return new DeploymentProviderConnectivityProbeSummary(
                "qdrant_cloud_control_plane",
                "Qdrant Cloud control plane",
                "BLOCKED",
                "https://api.cloud.qdrant.io",
                "QDRANT_CLOUD_MANAGEMENT_API_KEY is missing, so the platform cannot verify Qdrant Cloud provisioning."
            );
        }
        String regionId = ManagedDeploymentProfileCatalog.qdrantCloudRegionId(providerConfig);
        if (!StringUtils.hasText(regionId)) {
            return new DeploymentProviderConnectivityProbeSummary(
                "qdrant_cloud_control_plane",
                "Qdrant Cloud control plane",
                "BLOCKED",
                "https://api.cloud.qdrant.io",
                "qdrantCloudRegionId is required before the platform can verify Qdrant Cloud provisioning."
            );
        }
        String providerId = ManagedDeploymentProfileCatalog.qdrantCloudProviderId(providerConfig);
        try {
            QdrantCloudControlPlaneClient.QdrantCloudAccountResolution account = qdrantCloudControlPlaneClient.resolveAccount(
                ManagedDeploymentProfileCatalog.qdrantCloudAccountId(providerConfig),
                managementApiKey
            );
            QdrantCloudControlPlaneClient.QdrantCloudRegionSummary region =
                qdrantCloudControlPlaneClient.requireRegion(providerId, regionId);
            QdrantCloudControlPlaneClient.QdrantCloudPackageSummary pkg = qdrantCloudControlPlaneClient.resolvePackage(
                account.accountId(),
                managementApiKey,
                providerId,
                region.id(),
                ManagedDeploymentProfileCatalog.qdrantCloudPackageId(providerConfig)
            );
            return new DeploymentProviderConnectivityProbeSummary(
                "qdrant_cloud_control_plane",
                "Qdrant Cloud control plane",
                "READY",
                "https://api.cloud.qdrant.io",
                "Qdrant Cloud management access resolved account " + account.accountName()
                    + " and package " + pkg.name() + " for " + providerId + "/" + region.id() + "."
            );
        } catch (RailwayProvisioningConfigurationException ex) {
            return new DeploymentProviderConnectivityProbeSummary(
                "qdrant_cloud_control_plane",
                "Qdrant Cloud control plane",
                "BLOCKED",
                "https://api.cloud.qdrant.io",
                ex.getMessage()
            );
        } catch (RuntimeException ex) {
            return new DeploymentProviderConnectivityProbeSummary(
                "qdrant_cloud_control_plane",
                "Qdrant Cloud control plane",
                "FAILED",
                "https://api.cloud.qdrant.io",
                "Qdrant Cloud control-plane probe failed: " + ex.getMessage()
            );
        }
    }

    private DeploymentProviderConnectivityProbeSummary probeQdrant(JsonNode providerConfig) {
        String host = ManagedDeploymentProfileCatalog.qdrantHost(providerConfig);
        if (!StringUtils.hasText(host)) {
            return new DeploymentProviderConnectivityProbeSummary(
                "qdrant_collections_api",
                "Qdrant collections API",
                "BLOCKED",
                "",
                "qdrantHost is missing, so the platform cannot verify Qdrant connectivity."
            );
        }
        String apiKey = platformSecretService.resolveSecret("QDRANT_API_KEY");
        String endpoint = buildQdrantBaseUrl(providerConfig) + "/collections";
        return sendProbe(
            "qdrant_collections_api",
            "Qdrant collections API",
            endpoint,
            request -> {
                if (StringUtils.hasText(apiKey)) {
                    request.header("api-key", apiKey);
                }
            },
            false
        );
    }

    private DeploymentProviderConnectivityProbeSummary probeWeaviate(JsonNode providerConfig) {
        String host = ManagedDeploymentProfileCatalog.weaviateHost(providerConfig);
        if (!StringUtils.hasText(host)) {
            return new DeploymentProviderConnectivityProbeSummary(
                "weaviate_ready_api",
                "Weaviate readiness API",
                "BLOCKED",
                "",
                "weaviateHost is missing, so the platform cannot verify Weaviate connectivity."
            );
        }
        String endpoint = buildWeaviateBaseUrl(providerConfig) + "/v1/.well-known/ready";
        String apiKey = platformSecretService.resolveSecret("WEAVIATE_API_KEY");
        return sendProbe(
            "weaviate_ready_api",
            "Weaviate readiness API",
            endpoint,
            request -> {
                if (StringUtils.hasText(apiKey)) {
                    request.header("Authorization", "Bearer " + apiKey);
                }
            },
            false
        );
    }

    private DeploymentProviderConnectivityProbeSummary probeZillizCloud(JsonNode providerConfig) {
        String apiKey = platformSecretService.resolveSecret("ZILLIZ_CLOUD_API_KEY");
        if (!StringUtils.hasText(apiKey)) {
            return new DeploymentProviderConnectivityProbeSummary(
                "zilliz_cloud_control_plane",
                "Zilliz Cloud control plane",
                "BLOCKED",
                ZillizCloudControlPlaneClient.API_BASE_URL,
                "ZILLIZ_CLOUD_API_KEY is missing, so the platform cannot verify Zilliz Cloud provisioning."
            );
        }
        String regionId = ManagedDeploymentProfileCatalog.zillizCloudRegionId(providerConfig);
        if (!StringUtils.hasText(regionId)) {
            return new DeploymentProviderConnectivityProbeSummary(
                "zilliz_cloud_control_plane",
                "Zilliz Cloud control plane",
                "BLOCKED",
                ZillizCloudControlPlaneClient.API_BASE_URL,
                "zillizCloudRegionId is required before the platform can verify Zilliz Cloud provisioning."
            );
        }
        try {
            zillizCloudControlPlaneClient.verifyControlPlaneAccess(apiKey);
            ZillizCloudControlPlaneClient.ZillizProjectResolution project = zillizCloudControlPlaneClient.resolveProject(
                ManagedDeploymentProfileCatalog.zillizCloudProjectId(providerConfig),
                regionId,
                apiKey
            );
            return new DeploymentProviderConnectivityProbeSummary(
                "zilliz_cloud_control_plane",
                "Zilliz Cloud control plane",
                "READY",
                ZillizCloudControlPlaneClient.API_BASE_URL + "/v2/clusters",
                "Zilliz Cloud management access resolved project " + project.projectName()
                    + " in region " + regionId + "."
            );
        } catch (RailwayProvisioningConfigurationException ex) {
            return new DeploymentProviderConnectivityProbeSummary(
                "zilliz_cloud_control_plane",
                "Zilliz Cloud control plane",
                "BLOCKED",
                ZillizCloudControlPlaneClient.API_BASE_URL + "/v2/clusters",
                ex.getMessage()
            );
        } catch (RuntimeException ex) {
            return new DeploymentProviderConnectivityProbeSummary(
                "zilliz_cloud_control_plane",
                "Zilliz Cloud control plane",
                "FAILED",
                ZillizCloudControlPlaneClient.API_BASE_URL + "/v2/clusters",
                "Zilliz Cloud control-plane probe failed: " + ex.getMessage()
            );
        }
    }

    private DeploymentProviderConnectivityProbeSummary probeRestEmbedding(JsonNode providerConfig) {
        String endpoint = ManagedDeploymentProfileCatalog.restEmbeddingBaseUrl(providerConfig);
        if (!StringUtils.hasText(endpoint)) {
            return new DeploymentProviderConnectivityProbeSummary(
                "rest_embedding_base_url",
                "REST embedding base URL",
                "BLOCKED",
                "",
                "restEmbeddingBaseUrl is missing, so the platform cannot verify the external embedding service."
            );
        }
        return sendProbe(
            "rest_embedding_base_url",
            "REST embedding base URL",
            endpoint,
            request -> {
            },
            false
        );
    }

    private DeploymentProviderConnectivityProbeSummary sendProbe(String key,
                                                                 String label,
                                                                 String endpoint,
                                                                 RequestCustomizer customizer,
                                                                 boolean strictSuccessOnly) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json")
                .GET();
            customizer.customize(builder);

            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();
            boolean reachable = strictSuccessOnly
                ? statusCode >= 200 && statusCode < 300
                : statusCode >= 200 && statusCode < 500;
            if (reachable) {
                return new DeploymentProviderConnectivityProbeSummary(
                    key,
                    label,
                    "READY",
                    endpoint,
                    label + " responded with HTTP " + statusCode + "."
                );
            }
            return new DeploymentProviderConnectivityProbeSummary(
                key,
                label,
                "FAILED",
                endpoint,
                label + " responded with HTTP " + statusCode + "."
            );
        } catch (Exception ex) {
            return new DeploymentProviderConnectivityProbeSummary(
                key,
                label,
                "FAILED",
                endpoint,
                label + " probe failed: " + ex.getMessage()
            );
        }
    }

    private String summarize(List<DeploymentProviderConnectivityProbeSummary> probes) {
        long ready = probes.stream().filter(item -> "READY".equals(item.status())).count();
        long blocked = probes.stream().filter(item -> "BLOCKED".equals(item.status())).count();
        long failed = probes.stream().filter(item -> "FAILED".equals(item.status())).count();
        long skipped = probes.stream().filter(item -> "SKIPPED".equals(item.status())).count();
        return ready + " ready, " + blocked + " blocked, " + failed + " failed, " + skipped + " skipped.";
    }

    private String buildQdrantBaseUrl(JsonNode providerConfig) {
        String rawHost = ManagedDeploymentProfileCatalog.qdrantHost(providerConfig);
        if (rawHost.startsWith("https://") || rawHost.startsWith("http://")) {
            return trimTrailingSlash(rawHost);
        }
        int port = ManagedDeploymentProfileCatalog.qdrantPort(providerConfig);
        return trimTrailingSlash((port > 0 ? "http://" + rawHost + ":" + port : "http://" + rawHost));
    }

    private String buildWeaviateBaseUrl(JsonNode providerConfig) {
        String scheme = ManagedDeploymentProfileCatalog.weaviateScheme(providerConfig);
        String host = ManagedDeploymentProfileCatalog.weaviateHost(providerConfig);
        int port = ManagedDeploymentProfileCatalog.weaviatePort(providerConfig);
        return trimTrailingSlash(scheme + "://" + host + ":" + port);
    }

    private String normalizeMilvusEndpoint(JsonNode providerConfig) {
        String host = ManagedDeploymentProfileCatalog.milvusHost(providerConfig);
        if (!StringUtils.hasText(host)) {
            return "";
        }
        return (ManagedDeploymentProfileCatalog.milvusSecure(providerConfig) ? "tls://" : "tcp://")
            + host + ":" + ManagedDeploymentProfileCatalog.milvusPort(providerConfig);
    }

    private ManagedVectorSummary summarizeManagedVectorProvisioning(JsonNode providerConfig,
                                                                   JsonNode entityConfig) {
        String vectorStrategy = ManagedDeploymentProfileCatalog.resolveVectorStrategy(providerConfig);
        if (ManagedDeploymentProfileCatalog.pineconePlatformManaged(providerConfig)) {
            String indexName = ManagedDeploymentProfileCatalog.pineconeIndexName(providerConfig);
            String region = ManagedDeploymentProfileCatalog.pineconeRegion(providerConfig);
            String cloud = ManagedDeploymentProfileCatalog.pineconeCloud(providerConfig);
            List<String> targets = List.of(
                (StringUtils.hasText(indexName) ? indexName : "index name not configured")
                    + " (" + cloud + "/" + (StringUtils.hasText(region) ? region : "region not configured") + ")"
            );
            return new ManagedVectorSummary(
                true,
                "MANAGED_SERVERLESS_INDEX",
                targets,
                StringUtils.hasText(indexName)
                    ? "Apply will create or reconcile the Pinecone serverless index, resolve its host, and bind runtime to the managed resource."
                    : "Platform-managed Pinecone serverless provisioning is enabled, but pineconeIndexName still needs review."
            );
        }
        if (ManagedDeploymentProfileCatalog.VECTOR_STRATEGY_QDRANT.equals(vectorStrategy)
            && ManagedDeploymentProfileCatalog.qdrantPlatformManaged(providerConfig)) {
            String providerId = ManagedDeploymentProfileCatalog.qdrantCloudProviderId(providerConfig);
            String regionId = ManagedDeploymentProfileCatalog.qdrantCloudRegionId(providerConfig);
            String clusterNameOverride = ManagedDeploymentProfileCatalog.qdrantCloudClusterNameOverride(providerConfig);
            List<String> targets = new ArrayList<>();
            targets.add((StringUtils.hasText(clusterNameOverride) ? clusterNameOverride : "deployment-managed cluster")
                + " (" + providerId + "/" + (StringUtils.hasText(regionId) ? regionId : "region not configured") + ")");
            List<String> entityTypes = resolveEntityTypes(entityConfig);
            if (!entityTypes.isEmpty()) {
                targets.addAll(entityTypes);
            }
            return new ManagedVectorSummary(
                true,
                "MANAGED_CLOUD_CLUSTER",
                targets,
                StringUtils.hasText(regionId)
                    ? "Apply will create or reconcile a Qdrant Cloud cluster, issue a deployment-scoped database key, and provision one collection per configured entity type."
                    : "Platform-managed Qdrant Cloud provisioning is enabled, but qdrantCloudRegionId still needs review."
            );
        }
        if (ManagedDeploymentProfileCatalog.VECTOR_STRATEGY_QDRANT.equals(vectorStrategy)
            && ManagedDeploymentProfileCatalog.qdrantManagedCollectionsEnabled(providerConfig)) {
            List<String> entityTypes = resolveEntityTypes(entityConfig);
            List<String> targets = entityTypes.isEmpty() ? List.of("No entity types configured") : entityTypes;
            String host = ManagedDeploymentProfileCatalog.qdrantHost(providerConfig);
            String suffix = StringUtils.hasText(host) ? " on " + host.trim() : "";
            return new ManagedVectorSummary(
                true,
                "MANAGED_COLLECTIONS",
                targets,
                entityTypes.isEmpty()
                    ? "Platform-managed Qdrant collections are enabled, but entity types still need review."
                    : "Apply will create or reconcile Qdrant collections for the configured entity types" + suffix + "."
            );
        }
        if (ManagedDeploymentProfileCatalog.VECTOR_STRATEGY_MILVUS.equals(vectorStrategy)
            && ManagedDeploymentProfileCatalog.milvusPlatformManaged(providerConfig)) {
            String regionId = ManagedDeploymentProfileCatalog.zillizCloudRegionId(providerConfig);
            String projectId = ManagedDeploymentProfileCatalog.zillizCloudProjectId(providerConfig);
            String clusterPlan = ManagedDeploymentProfileCatalog.zillizCloudClusterPlan(providerConfig);
            String clusterName = ManagedDeploymentProfileCatalog.zillizCloudClusterNameOverride(providerConfig);
            List<String> targets = List.of(
                (StringUtils.hasText(clusterName) ? clusterName : "deployment-managed cluster")
                    + " (" + (StringUtils.hasText(projectId) ? projectId : "project not configured")
                    + " / " + (StringUtils.hasText(regionId) ? regionId : "region not configured")
                    + " / " + clusterPlan + ")"
            );
            return new ManagedVectorSummary(
                true,
                "MANAGED_ZILLIZ_CLOUD_CLUSTER",
                targets,
                StringUtils.hasText(projectId) && StringUtils.hasText(regionId)
                    ? "Apply will create or reconcile a Zilliz Cloud managed Milvus cluster and bind deployment-scoped runtime credentials automatically."
                    : "Platform-managed Zilliz Cloud provisioning is enabled, but zillizCloudProjectId and zillizCloudRegionId still need review."
            );
        }
        return new ManagedVectorSummary(
            false,
            "NONE",
            List.of(),
            "Platform-managed external vector provisioning is not enabled for this draft."
        );
    }

    private List<String> resolveEntityTypes(JsonNode entityConfig) {
        JsonNode aiEntities = entityConfig.path("ai-entities");
        if (!aiEntities.isObject()) {
            return List.of();
        }
        List<String> entityTypes = new ArrayList<>();
        aiEntities.fieldNames().forEachRemaining(field -> {
            if (StringUtils.hasText(field)) {
                entityTypes.add(field.trim());
            }
        });
        return entityTypes.stream().distinct().collect(Collectors.toList());
    }

    private JsonNode readJson(String value) {
        try {
            return value == null || value.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(value);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to parse provider config JSON.", ex);
        }
    }

    private String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    @FunctionalInterface
    private interface RequestCustomizer {
        void customize(HttpRequest.Builder request);
    }

    private record ManagedVectorSummary(
        boolean enabled,
        String mode,
        List<String> targets,
        String message
    ) {
    }
}
