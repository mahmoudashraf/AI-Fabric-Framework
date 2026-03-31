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

@Service
public class DeploymentManagedVectorProvisioningService {

    private static final Logger log = LoggerFactory.getLogger(DeploymentManagedVectorProvisioningService.class);
    private static final String PINECONE_API_VERSION = "2025-10";
    private static final URI PINECONE_INDEXES_URI = URI.create("https://api.pinecone.io/indexes");

    private final PlatformSecretService platformSecretService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public DeploymentManagedVectorProvisioningService(PlatformSecretService platformSecretService,
                                                      ObjectMapper objectMapper) {
        this(
            platformSecretService,
            objectMapper,
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
        );
    }

    DeploymentManagedVectorProvisioningService(PlatformSecretService platformSecretService,
                                               ObjectMapper objectMapper,
                                               HttpClient httpClient) {
        this.platformSecretService = platformSecretService;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
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
        List<String> entityTypes = resolveEntityTypes(entityConfig);
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

        details.put("mode", "MANAGED_COLLECTIONS");
        details.put("baseUrl", baseUrl);
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
