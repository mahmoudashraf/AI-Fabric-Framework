package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class DeploymentManagedVectorProvisioningService {

    private static final Logger log = LoggerFactory.getLogger(DeploymentManagedVectorProvisioningService.class);
    private static final String PINECONE_API_VERSION = "2025-10";
    private static final URI PINECONE_INDEXES_URI = URI.create("https://api.pinecone.io/indexes");

    private final PlatformSecretService platformSecretService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final QdrantCloudControlPlaneClient qdrantCloudControlPlaneClient;

    @Autowired
    public DeploymentManagedVectorProvisioningService(PlatformSecretService platformSecretService,
                                                      ObjectMapper objectMapper,
                                                      QdrantCloudControlPlaneClient qdrantCloudControlPlaneClient) {
        this(
            platformSecretService,
            objectMapper,
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
            qdrantCloudControlPlaneClient
        );
    }

    DeploymentManagedVectorProvisioningService(PlatformSecretService platformSecretService,
                                               ObjectMapper objectMapper,
                                               HttpClient httpClient) {
        this(
            platformSecretService,
            objectMapper,
            httpClient,
            new QdrantCloudControlPlaneClient(objectMapper, httpClient)
        );
    }

    DeploymentManagedVectorProvisioningService(PlatformSecretService platformSecretService,
                                               ObjectMapper objectMapper,
                                               HttpClient httpClient,
                                               QdrantCloudControlPlaneClient qdrantCloudControlPlaneClient) {
        this.platformSecretService = platformSecretService;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.qdrantCloudControlPlaneClient = qdrantCloudControlPlaneClient;
    }

    public boolean requiresProvisioning(DeploymentVersionEntity version) {
        JsonNode providerConfig = readJson(version.getProviderConfigJson());
        return ManagedDeploymentProfileCatalog.managedVectorProvisioningRequested(providerConfig);
    }

    public ManagedVectorProvisioningResult ensureProvisioned(DeploymentEntity deployment,
                                                             DeploymentVersionEntity version) {
        JsonNode providerConfig = readJson(version.getProviderConfigJson());
        JsonNode entityConfig = readJson(version.getEntityConfigJson());
        return ensureProvisioned(deployment.getId(), providerConfig, entityConfig);
    }

    ManagedVectorProvisioningResult ensureProvisioned(String deploymentId,
                                                      JsonNode providerConfig,
                                                      JsonNode entityConfig) {
        ObjectNode effectiveProviderConfig = providerConfig != null && providerConfig.isObject()
            ? ((ObjectNode) providerConfig).deepCopy()
            : objectMapper.createObjectNode();
        ObjectNode details = objectMapper.createObjectNode();

        if (ManagedDeploymentProfileCatalog.usesPinecone(providerConfig)
            && ManagedDeploymentProfileCatalog.managedVectorProvisioningRequested(providerConfig)) {
            details.put("enabled", true);
            details.put("vectorStrategy", ManagedDeploymentProfileCatalog.VECTOR_STRATEGY_PINECONE);
            ensureManagedPineconeIndex(deploymentId, effectiveProviderConfig, entityConfig, details);
            return new ManagedVectorProvisioningResult(effectiveProviderConfig, details);
        }

        if (ManagedDeploymentProfileCatalog.qdrantPlatformManaged(providerConfig)) {
            details.put("enabled", true);
            details.put("vectorStrategy", ManagedDeploymentProfileCatalog.VECTOR_STRATEGY_QDRANT);
            ensureManagedQdrantCloudCluster(deploymentId, effectiveProviderConfig, entityConfig, details);
            return new ManagedVectorProvisioningResult(effectiveProviderConfig, details);
        }

        if (ManagedDeploymentProfileCatalog.usesQdrant(providerConfig)
            && ManagedDeploymentProfileCatalog.qdrantManagedCollectionsEnabled(providerConfig)) {
            details.put("enabled", true);
            details.put("vectorStrategy", ManagedDeploymentProfileCatalog.VECTOR_STRATEGY_QDRANT);
            ensureManagedQdrantCollections(effectiveProviderConfig, entityConfig, details);
            return new ManagedVectorProvisioningResult(effectiveProviderConfig, details);
        }

        details.put("enabled", false);
        details.put("vectorStrategy", ManagedDeploymentProfileCatalog.resolveVectorStrategy(providerConfig));
        details.put("mode", "NONE");
        details.put("message", "No managed external vector provisioning is configured for this deployment.");
        return new ManagedVectorProvisioningResult(effectiveProviderConfig, details);
    }

    private void ensureManagedPineconeIndex(String deploymentId,
                                            ObjectNode effectiveProviderConfig,
                                            JsonNode entityConfig,
                                            ObjectNode details) {
        String apiKey = requireSecret("PINECONE_API_KEY");
        String indexName = requiredText(effectiveProviderConfig, "pineconeIndexName", "pinecone managed index");
        int dimensions = resolveVectorDimensions(entityConfig, effectiveProviderConfig);
        String metric = ManagedDeploymentProfileCatalog.pineconeMetric(effectiveProviderConfig);
        String cloud = ManagedDeploymentProfileCatalog.pineconeCloud(effectiveProviderConfig);
        String region = requiredText(effectiveProviderConfig, "pineconeRegion", "pinecone managed index");
        String deletionProtection = ManagedDeploymentProfileCatalog.pineconeDeletionProtectionEnabled(effectiveProviderConfig)
            ? "enabled"
            : "disabled";

        PineconeIndexSnapshot existing = fetchPineconeIndex(indexName, apiKey);
        boolean created = false;
        if (existing == null) {
            createPineconeIndex(indexName, dimensions, metric, cloud, region, deletionProtection, apiKey);
            created = true;
        } else {
            validatePineconeIndex(existing, indexName, dimensions, metric);
        }

        PineconeIndexSnapshot readyIndex = awaitPineconeIndex(indexName, apiKey);
        validatePineconeIndex(readyIndex, indexName, dimensions, metric);
        if (!StringUtils.hasText(readyIndex.host())) {
            throw new RailwayProvisioningException("Pinecone index '" + indexName + "' did not expose an API host.");
        }

        effectiveProviderConfig.put("pineconeIndexName", indexName);
        effectiveProviderConfig.put("pineconeApiHost", normalizePineconeHost(readyIndex.host()));
        effectiveProviderConfig.put("pineconeDimensions", dimensions);

        details.put("mode", "MANAGED_INDEX");
        details.put("deploymentId", deploymentId);
        details.put("indexName", indexName);
        details.put("apiHost", normalizePineconeHost(readyIndex.host()));
        details.put("metric", readyIndex.metric());
        details.put("dimensions", readyIndex.dimension());
        details.put("cloud", cloud);
        details.put("region", region);
        details.put("deletionProtection", deletionProtection);
        details.put("state", created ? "CREATED" : "REUSED");
        details.put("ready", readyIndex.ready());
    }

    private void ensureManagedQdrantCollections(ObjectNode effectiveProviderConfig,
                                                JsonNode entityConfig,
                                                ObjectNode details) {
        String baseUrl = buildQdrantBaseUrl(effectiveProviderConfig);
        int vectorDimensions = resolveVectorDimensions(entityConfig, effectiveProviderConfig);
        String apiKey = platformSecretService.resolveSecret("QDRANT_API_KEY");
        ArrayNode collections = reconcileQdrantCollections(baseUrl, resolveEntityTypes(entityConfig), vectorDimensions, apiKey);

        details.put("mode", "MANAGED_COLLECTIONS");
        details.put("baseUrl", baseUrl);
        details.put("vectorDimensions", vectorDimensions);
        details.set("collections", collections);
    }

    private void ensureManagedQdrantCloudCluster(String deploymentId,
                                                 ObjectNode effectiveProviderConfig,
                                                 JsonNode entityConfig,
                                                 ObjectNode details) {
        List<String> entityTypes = resolveEntityTypes(entityConfig);
        if (entityTypes.isEmpty()) {
            throw new RailwayProvisioningException(
                "Platform-managed Qdrant Cloud provisioning requires at least one configured entity type."
            );
        }

        String managementApiKey = requireSecret("QDRANT_CLOUD_MANAGEMENT_API_KEY");
        String configuredAccountId = ManagedDeploymentProfileCatalog.qdrantCloudAccountId(effectiveProviderConfig);
        QdrantCloudControlPlaneClient.QdrantCloudAccountResolution account = qdrantCloudControlPlaneClient.resolveAccount(
            configuredAccountId,
            managementApiKey
        );
        String providerId = ManagedDeploymentProfileCatalog.qdrantCloudProviderId(effectiveProviderConfig);
        String regionId = requiredText(effectiveProviderConfig, "qdrantCloudRegionId", "platform-managed Qdrant Cloud");
        QdrantCloudControlPlaneClient.QdrantCloudRegionSummary region = qdrantCloudControlPlaneClient.requireRegion(providerId, regionId);
        QdrantCloudControlPlaneClient.QdrantCloudPackageSummary pkg = qdrantCloudControlPlaneClient.resolvePackage(
            account.accountId(),
            managementApiKey,
            providerId,
            region.id(),
            ManagedDeploymentProfileCatalog.qdrantCloudPackageId(effectiveProviderConfig)
        );

        String clusterName = resolveManagedQdrantClusterName(deploymentId, effectiveProviderConfig);
        QdrantCloudControlPlaneClient.QdrantCloudClusterSummary existingCluster =
            qdrantCloudControlPlaneClient.findClusterByName(account.accountId(), clusterName, managementApiKey);
        boolean clusterCreated = false;
        QdrantCloudControlPlaneClient.QdrantCloudClusterSummary cluster;
        if (existingCluster == null) {
            cluster = qdrantCloudControlPlaneClient.createCluster(
                account.accountId(),
                deploymentId,
                clusterName,
                providerId,
                region.id(),
                pkg.id(),
                managementApiKey
            );
            clusterCreated = true;
        } else {
            validateExistingQdrantCluster(existingCluster, providerId, region.id(), pkg.id());
            cluster = existingCluster;
        }

        QdrantCloudControlPlaneClient.QdrantCloudClusterSummary readyCluster = qdrantCloudControlPlaneClient.awaitClusterReady(
            account.accountId(),
            cluster.id(),
            managementApiKey
        );
        String baseUrl = normalizeQdrantCloudBaseUrl(readyCluster.endpointUrl());
        String runtimeSecretName = managedQdrantRuntimeSecretName(deploymentId);
        String databaseApiKeyName = managedQdrantDatabaseApiKeyName(deploymentId);
        QdrantCloudControlPlaneClient.QdrantCloudDatabaseApiKeySummary existingDatabaseApiKey =
            qdrantCloudControlPlaneClient.findDatabaseApiKeyByName(
                account.accountId(),
                readyCluster.id(),
                databaseApiKeyName,
                managementApiKey
            );
        String runtimeApiKey = platformSecretService.resolveSecret(runtimeSecretName);
        boolean databaseApiKeyCreated = false;
        if (existingDatabaseApiKey == null) {
            QdrantCloudControlPlaneClient.QdrantCloudDatabaseApiKeySummary createdDatabaseApiKey =
                qdrantCloudControlPlaneClient.createCollectionDatabaseApiKey(
                    account.accountId(),
                    readyCluster.id(),
                    databaseApiKeyName,
                    entityTypes,
                    managementApiKey
                );
            runtimeApiKey = createdDatabaseApiKey.key();
            if (!StringUtils.hasText(runtimeApiKey)) {
                throw new RailwayProvisioningException(
                    "Qdrant Cloud created database API key '" + databaseApiKeyName + "' but did not return the secret value."
                );
            }
            platformSecretService.upsertManagedSecret(
                runtimeSecretName,
                runtimeApiKey,
                Map.of(
                    "deploymentId", deploymentId,
                    "vendor", "qdrant",
                    "resourceType", "DATABASE_API_KEY",
                    "resourceName", databaseApiKeyName
                )
            );
            existingDatabaseApiKey = createdDatabaseApiKey;
            databaseApiKeyCreated = true;
        } else if (!StringUtils.hasText(runtimeApiKey)) {
            throw new RailwayProvisioningConfigurationException(
                "The platform-managed Qdrant Cloud database key '" + databaseApiKeyName + "' already exists, but the managed platform secret '"
                    + runtimeSecretName + "' is missing. Restore the secret or rotate the key before re-applying."
            );
        }

        int vectorDimensions = resolveVectorDimensions(entityConfig, effectiveProviderConfig);
        ArrayNode collections = reconcileQdrantCollections(baseUrl, entityTypes, vectorDimensions, runtimeApiKey);

        effectiveProviderConfig.put("qdrantHost", baseUrl);
        if (readyCluster.restPort() > 0) {
            effectiveProviderConfig.put("qdrantPort", readyCluster.restPort());
        }
        if (readyCluster.grpcPort() > 0) {
            effectiveProviderConfig.put("qdrantGrpcPort", readyCluster.grpcPort());
        }
        effectiveProviderConfig.put("qdrantCloudAccountId", account.accountId());
        effectiveProviderConfig.put("qdrantCloudProviderId", providerId);
        effectiveProviderConfig.put("qdrantCloudRegionId", region.id());
        effectiveProviderConfig.put("qdrantCloudPackageId", pkg.id());
        effectiveProviderConfig.put("qdrantRuntimeApiKeySecretName", runtimeSecretName);

        details.put("mode", "MANAGED_CLOUD_CLUSTER");
        details.put("deploymentId", deploymentId);
        details.put("accountId", account.accountId());
        details.put("accountName", account.accountName());
        details.put("accountAutoResolved", account.autoResolved());
        details.put("cloudProviderId", providerId);
        details.put("regionId", region.id());
        details.put("regionName", region.name());
        details.put("packageId", pkg.id());
        details.put("packageName", pkg.name());
        details.put("packageType", pkg.type());
        details.put("packageHourlyPriceMillicents", pkg.unitIntPricePerHour());
        details.put("clusterId", readyCluster.id());
        details.put("clusterName", readyCluster.name());
        details.put("clusterState", clusterCreated ? "CREATED" : "REUSED");
        details.put("baseUrl", baseUrl);
        details.put("restPort", readyCluster.restPort());
        details.put("grpcPort", readyCluster.grpcPort());
        details.put("clusterPhase", readyCluster.phase());
        details.put("databaseApiKeyId", existingDatabaseApiKey.id());
        details.put("databaseApiKeyName", existingDatabaseApiKey.name());
        details.put("databaseApiKeySecretName", runtimeSecretName);
        details.put("databaseApiKeyState", databaseApiKeyCreated ? "CREATED" : "REUSED");
        details.put("vectorDimensions", vectorDimensions);
        details.set("collections", collections);
    }

    private int resolveVectorDimensions(JsonNode entityConfig, JsonNode providerConfig) {
        int configured = entityConfig.path("ai-config").path("vector-dimensions").asInt(0);
        if (configured > 0) {
            return configured;
        }
        return ManagedDeploymentProfileCatalog.defaultEmbeddingDimensions(
            ManagedDeploymentProfileCatalog.resolveEmbeddingProvider(providerConfig)
        );
    }

    private List<String> resolveEntityTypes(JsonNode entityConfig) {
        JsonNode aiEntities = entityConfig.path("ai-entities");
        if (!aiEntities.isObject()) {
            return List.of();
        }
        List<String> entityTypes = new ArrayList<>();
        Iterator<String> fieldNames = aiEntities.fieldNames();
        while (fieldNames.hasNext()) {
            String fieldName = fieldNames.next();
            if (StringUtils.hasText(fieldName)) {
                entityTypes.add(fieldName.trim());
            }
        }
        return entityTypes;
    }

    private ArrayNode reconcileQdrantCollections(String baseUrl,
                                                 List<String> entityTypes,
                                                 int vectorDimensions,
                                                 String apiKey) {
        if (entityTypes.isEmpty()) {
            throw new RailwayProvisioningException(
                "Qdrant managed collection provisioning requires at least one configured entity type."
            );
        }
        ArrayNode collections = objectMapper.createArrayNode();
        for (String entityType : entityTypes) {
            boolean existed = qdrantCollectionExists(baseUrl, entityType, vectorDimensions, apiKey);
            if (!existed) {
                createQdrantCollection(baseUrl, entityType, vectorDimensions, apiKey);
            }
            collections.add(objectMapper.createObjectNode()
                .put("name", entityType)
                .put("state", existed ? "REUSED" : "CREATED"));
        }
        return collections;
    }

    private void validateExistingQdrantCluster(QdrantCloudControlPlaneClient.QdrantCloudClusterSummary cluster,
                                               String providerId,
                                               String regionId,
                                               String packageId) {
        if (!providerId.equals(cluster.cloudProviderId())) {
            throw new RailwayProvisioningException(
                "Qdrant Cloud cluster '" + cluster.name() + "' already exists on provider '" + cluster.cloudProviderId()
                    + "' but deployment requires '" + providerId + "'."
            );
        }
        if (!regionId.equals(cluster.cloudProviderRegionId())) {
            throw new RailwayProvisioningException(
                "Qdrant Cloud cluster '" + cluster.name() + "' already exists in region '" + cluster.cloudProviderRegionId()
                    + "' but deployment requires '" + regionId + "'."
            );
        }
        if (StringUtils.hasText(packageId) && StringUtils.hasText(cluster.packageId()) && !packageId.equals(cluster.packageId())) {
            throw new RailwayProvisioningException(
                "Qdrant Cloud cluster '" + cluster.name() + "' already exists with package '" + cluster.packageId()
                    + "' but deployment requires '" + packageId + "'."
            );
        }
    }

    private String resolveManagedQdrantClusterName(String deploymentId, JsonNode providerConfig) {
        String override = ManagedDeploymentProfileCatalog.qdrantCloudClusterNameOverride(providerConfig);
        String base = StringUtils.hasText(override) ? override : "aifabric-" + deploymentId.replace("dep-", "");
        String normalized = base.trim()
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9-_]+", "-")
            .replaceAll("^-+", "")
            .replaceAll("-+$", "");
        if (normalized.length() < 2) {
            normalized = "af-" + deploymentId.replace("dep-", "");
        }
        return normalized.length() > 64 ? normalized.substring(0, 64) : normalized;
    }

    private String managedQdrantRuntimeSecretName(String deploymentId) {
        return PlatformSecretService.MANAGED_SECRET_PREFIX
            + "QDRANT_DB_API_KEY_DEP_"
            + deploymentId.replaceAll("[^A-Za-z0-9]", "_").toUpperCase(Locale.ROOT);
    }

    private String managedQdrantDatabaseApiKeyName(String deploymentId) {
        String suffix = deploymentId.replace("dep-", "").replaceAll("[^A-Za-z0-9]", "-").toLowerCase(Locale.ROOT);
        return ("ai-fabric-" + suffix).substring(0, Math.min(("ai-fabric-" + suffix).length(), 64));
    }

    private String normalizeQdrantCloudBaseUrl(String endpointUrl) {
        if (!StringUtils.hasText(endpointUrl)) {
            throw new RailwayProvisioningException("Qdrant Cloud cluster did not expose a usable endpoint URL.");
        }
        return trimTrailingSlash(endpointUrl.trim());
    }

    private PineconeIndexSnapshot fetchPineconeIndex(String indexName, String apiKey) {
        HttpRequest request = pineconeRequestBuilder(URI.create(PINECONE_INDEXES_URI + "/" + indexName), apiKey)
            .GET()
            .build();
        HttpResponse<String> response = send(request);
        if (response.statusCode() == 404) {
            return null;
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RailwayProvisioningException(
                "Pinecone describe index failed for '" + indexName + "' with HTTP " + response.statusCode()
            );
        }
        return parsePineconeIndex(readJson(response.body()), indexName);
    }

    private PineconeIndexSnapshot awaitPineconeIndex(String indexName, String apiKey) {
        Instant deadline = Instant.now().plus(Duration.ofMinutes(2));
        PineconeIndexSnapshot lastSeen = null;
        while (Instant.now().isBefore(deadline)) {
            lastSeen = fetchPineconeIndex(indexName, apiKey);
            if (lastSeen != null && lastSeen.ready() && StringUtils.hasText(lastSeen.host())) {
                return lastSeen;
            }
            try {
                Thread.sleep(2_000L);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new RailwayProvisioningException("Interrupted while waiting for Pinecone index readiness.", ex);
            }
        }
        if (lastSeen != null) {
            return lastSeen;
        }
        throw new RailwayProvisioningException("Timed out waiting for Pinecone index '" + indexName + "' to appear.");
    }

    private void createPineconeIndex(String indexName,
                                     int dimensions,
                                     String metric,
                                     String cloud,
                                     String region,
                                     String deletionProtection,
                                     String apiKey) {
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("name", indexName);
        payload.put("dimension", dimensions);
        payload.put("metric", metric);
        payload.put("deletion_protection", deletionProtection);
        ObjectNode spec = payload.putObject("spec");
        ObjectNode serverless = spec.putObject("serverless");
        serverless.put("cloud", cloud);
        serverless.put("region", region);

        HttpRequest request = pineconeRequestBuilder(PINECONE_INDEXES_URI, apiKey)
            .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
            .build();
        HttpResponse<String> response = send(request);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RailwayProvisioningException(
                "Pinecone create index failed for '" + indexName + "' with HTTP " + response.statusCode() + "."
            );
        }
        log.info("Created Pinecone index '{}'", indexName);
    }

    private void validatePineconeIndex(PineconeIndexSnapshot snapshot,
                                       String indexName,
                                       int dimensions,
                                       String metric) {
        if (snapshot == null) {
            return;
        }
        if (snapshot.dimension() > 0 && snapshot.dimension() != dimensions) {
            throw new RailwayProvisioningException(
                "Pinecone index '" + indexName + "' exists with dimension " + snapshot.dimension()
                    + " but deployment requires " + dimensions + "."
            );
        }
        if (StringUtils.hasText(snapshot.metric()) && !metric.equalsIgnoreCase(snapshot.metric())) {
            throw new RailwayProvisioningException(
                "Pinecone index '" + indexName + "' exists with metric '" + snapshot.metric()
                    + "' but deployment requires '" + metric + "'."
            );
        }
    }

    private PineconeIndexSnapshot parsePineconeIndex(JsonNode root, String fallbackIndexName) {
        JsonNode payload = root.path("name").isMissingNode() && root.path("host").isMissingNode() && root.path("status").isMissingNode()
            ? root.path("result")
            : root;
        String name = blankOrFallback(payload.path("name").asText(""), fallbackIndexName);
        String host = payload.path("host").asText("").trim();
        int dimension = payload.path("dimension").asInt(0);
        String metric = payload.path("metric").asText("").trim();
        boolean ready = payload.path("status").path("ready").asBoolean(false);
        if (!ready && payload.path("ready").isBoolean()) {
            ready = payload.path("ready").asBoolean(false);
        }
        return new PineconeIndexSnapshot(name, host, dimension, metric, ready);
    }

    private boolean qdrantCollectionExists(String baseUrl,
                                           String collectionName,
                                           int vectorDimensions,
                                           String apiKey) {
        HttpRequest request = qdrantRequestBuilder(baseUrl + "/collections/" + collectionName, apiKey)
            .GET()
            .build();
        HttpResponse<String> response = send(request);
        if (response.statusCode() == 404) {
            return false;
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RailwayProvisioningException(
                "Qdrant collection lookup failed for '" + collectionName + "' with HTTP " + response.statusCode()
            );
        }
        JsonNode root = readJson(response.body());
        int existingSize = root.path("result").path("config").path("params").path("vectors").path("size").asInt(0);
        if (existingSize > 0 && existingSize != vectorDimensions) {
            throw new RailwayProvisioningException(
                "Qdrant collection '" + collectionName + "' exists with vector size " + existingSize
                    + " but deployment requires " + vectorDimensions + "."
            );
        }
        return true;
    }

    private void createQdrantCollection(String baseUrl,
                                        String collectionName,
                                        int vectorDimensions,
                                        String apiKey) {
        ObjectNode payload = objectMapper.createObjectNode();
        ObjectNode vectors = payload.putObject("vectors");
        vectors.put("size", vectorDimensions);
        vectors.put("distance", "Cosine");

        HttpRequest request = qdrantRequestBuilder(baseUrl + "/collections/" + collectionName, apiKey)
            .PUT(HttpRequest.BodyPublishers.ofString(payload.toString()))
            .build();
        HttpResponse<String> response = send(request);
        if (response.statusCode() == 409) {
            return;
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RailwayProvisioningException(
                "Qdrant create collection failed for '" + collectionName + "' with HTTP " + response.statusCode()
            );
        }
    }

    private String buildQdrantBaseUrl(JsonNode providerConfig) {
        String rawHost = requiredText(providerConfig, "qdrantHost", "qdrant managed collections");
        if (rawHost.startsWith("https://") || rawHost.startsWith("http://")) {
            return trimTrailingSlash(rawHost);
        }
        int port = ManagedDeploymentProfileCatalog.qdrantPort(providerConfig);
        String defaulted = port > 0 ? "http://" + rawHost + ":" + port : "http://" + rawHost;
        return trimTrailingSlash(defaulted);
    }

    private HttpRequest.Builder pineconeRequestBuilder(URI uri, String apiKey) {
        return HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(30))
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("Api-Key", apiKey)
            .header("X-Pinecone-Api-Version", PINECONE_API_VERSION);
    }

    private HttpRequest.Builder qdrantRequestBuilder(String uri, String apiKey) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(uri))
            .timeout(Duration.ofSeconds(30))
            .header("Accept", "application/json")
            .header("Content-Type", "application/json");
        if (StringUtils.hasText(apiKey)) {
            builder.header("api-key", apiKey);
        }
        return builder;
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException ex) {
            throw new RailwayProvisioningException("External vector provisioning request failed.", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RailwayProvisioningException("External vector provisioning request was interrupted.", ex);
        }
    }

    private String requireSecret(String name) {
        String value = platformSecretService.resolveSecret(name);
        if (!StringUtils.hasText(value)) {
            throw new RailwayProvisioningConfigurationException(
                "Missing platform secret '" + name + "' required for managed vector provisioning."
            );
        }
        return value;
    }

    private String requiredText(JsonNode providerConfig, String field, String target) {
        String value = providerConfig.path(field).asText("").trim();
        if (!StringUtils.hasText(value)) {
            throw new RailwayProvisioningConfigurationException(
                "Missing provider config field '" + field + "' required for " + target + "."
            );
        }
        return value;
    }

    private JsonNode readJson(String value) {
        try {
            return value == null || value.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(value);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to parse deployment vector provider config JSON.", ex);
        }
    }

    private String blankOrFallback(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String trimTrailingSlash(String value) {
        if (value == null) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String normalizePineconeHost(String host) {
        if (!StringUtils.hasText(host)) {
            return "";
        }
        String trimmed = host.trim();
        if (trimmed.startsWith("https://") || trimmed.startsWith("http://")) {
            return trimmed;
        }
        return "https://" + trimmed;
    }

    private record PineconeIndexSnapshot(
        String name,
        String host,
        int dimension,
        String metric,
        boolean ready
    ) {
    }
}
