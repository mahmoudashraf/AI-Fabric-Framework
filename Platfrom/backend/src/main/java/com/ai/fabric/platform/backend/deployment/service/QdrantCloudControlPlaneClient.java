package com.ai.fabric.platform.backend.deployment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class QdrantCloudControlPlaneClient {

    private static final String API_BASE_URL = "https://api.cloud.qdrant.io";

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public QdrantCloudControlPlaneClient(ObjectMapper objectMapper) {
        this(
            objectMapper,
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
        );
    }

    QdrantCloudControlPlaneClient(ObjectMapper objectMapper,
                                  HttpClient httpClient) {
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    public QdrantCloudAccountResolution resolveAccount(String configuredAccountId,
                                                       String managementApiKey) {
        List<QdrantCloudAccountSummary> accounts = listAccounts(managementApiKey);
        if (StringUtils.hasText(configuredAccountId)) {
            return accounts.stream()
                .filter(account -> configuredAccountId.equals(account.id()))
                .findFirst()
                .map(account -> new QdrantCloudAccountResolution(account.id(), account.name(), false))
                .orElseThrow(() -> new RailwayProvisioningConfigurationException(
                    "qdrantCloudAccountId '" + configuredAccountId + "' was not found for the supplied Qdrant Cloud management key."
                ));
        }
        if (accounts.isEmpty()) {
            throw new RailwayProvisioningConfigurationException(
                "The supplied Qdrant Cloud management key does not expose any accounts."
            );
        }
        if (accounts.size() > 1) {
            throw new RailwayProvisioningConfigurationException(
                "qdrantCloudAccountId is required because the supplied Qdrant Cloud management key can access multiple accounts."
            );
        }
        QdrantCloudAccountSummary account = accounts.get(0);
        return new QdrantCloudAccountResolution(account.id(), account.name(), true);
    }

    public QdrantCloudRegionSummary requireRegion(String cloudProviderId,
                                                  String regionId) {
        HttpRequest request = requestBuilder(
            URI.create(API_BASE_URL + "/api/platform/v1/cloud-providers/" + encodePath(cloudProviderId)
                + "/regions/" + encodePath(regionId)),
            null
        ).GET().build();
        HttpResponse<String> response = send(request);
        if (response.statusCode() == 404) {
            throw new RailwayProvisioningConfigurationException(
                "Qdrant Cloud region '" + regionId + "' for provider '" + cloudProviderId + "' was not found."
            );
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw failure("Qdrant Cloud region lookup failed", response);
        }
        JsonNode region = readJson(response.body()).path("region");
        if (!region.path("available").asBoolean(false)) {
            throw new RailwayProvisioningConfigurationException(
                "Qdrant Cloud region '" + regionId + "' for provider '" + cloudProviderId + "' is not currently available."
            );
        }
        return new QdrantCloudRegionSummary(
            region.path("id").asText(regionId),
            region.path("name").asText(regionId),
            region.path("provider").asText(cloudProviderId),
            region.path("available").asBoolean(false)
        );
    }

    public QdrantCloudPackageSummary resolvePackage(String accountId,
                                                    String managementApiKey,
                                                    String cloudProviderId,
                                                    String regionId,
                                                    String configuredPackageId) {
        HttpRequest request = requestBuilder(
            uriWithQuery(
                API_BASE_URL + "/api/booking/v1/accounts/" + encodePath(accountId) + "/packages",
                Map.of(
                    "cloud_provider_id", cloudProviderId,
                    "cloud_provider_region_id", regionId
                )
            ),
            managementApiKey
        ).GET().build();
        HttpResponse<String> response = send(request);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw failure("Qdrant Cloud package lookup failed", response);
        }

        List<QdrantCloudPackageSummary> packages = new ArrayList<>();
        JsonNode items = readJson(response.body()).path("items");
        if (items.isArray()) {
            items.forEach(item -> packages.add(new QdrantCloudPackageSummary(
                item.path("id").asText(""),
                item.path("name").asText(""),
                item.path("type").asText(""),
                item.path("currency").asText(""),
                item.path("unitIntPricePerHour").asInt(Integer.MAX_VALUE),
                item.path("status").asText(""),
                item.path("resourceConfiguration").path("ram").asText(""),
                item.path("resourceConfiguration").path("cpu").asText(""),
                item.path("resourceConfiguration").path("disk").asText("")
            )));
        }

        List<QdrantCloudPackageSummary> activePackages = packages.stream()
            .filter(pkg -> "PACKAGE_STATUS_ACTIVE".equals(pkg.status()))
            .toList();
        if (activePackages.isEmpty()) {
            throw new RailwayProvisioningConfigurationException(
                "No active Qdrant Cloud packages are available for " + cloudProviderId + "/" + regionId + "."
            );
        }

        if (StringUtils.hasText(configuredPackageId)) {
            return activePackages.stream()
                .filter(pkg -> configuredPackageId.equals(pkg.id()))
                .findFirst()
                .orElseThrow(() -> new RailwayProvisioningConfigurationException(
                    "qdrantCloudPackageId '" + configuredPackageId + "' is not available for " + cloudProviderId + "/" + regionId + "."
                ));
        }

        return activePackages.stream()
            .sorted(Comparator
                .comparingInt(QdrantCloudPackageSummary::unitIntPricePerHour)
                .thenComparing(QdrantCloudPackageSummary::name))
            .findFirst()
            .orElseThrow(() -> new RailwayProvisioningConfigurationException(
                "No active Qdrant Cloud packages are available for " + cloudProviderId + "/" + regionId + "."
            ));
    }

    public QdrantCloudClusterSummary findClusterByName(String accountId,
                                                       String clusterName,
                                                       String managementApiKey) {
        HttpRequest request = requestBuilder(
            URI.create(API_BASE_URL + "/api/cluster/v1/accounts/" + encodePath(accountId) + "/clusters"),
            managementApiKey
        ).GET().build();
        HttpResponse<String> response = send(request);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw failure("Qdrant Cloud cluster listing failed", response);
        }
        JsonNode items = readJson(response.body()).path("items");
        if (!items.isArray()) {
            return null;
        }
        for (JsonNode item : items) {
            if (clusterName.equalsIgnoreCase(item.path("name").asText(""))) {
                return toClusterSummary(item);
            }
        }
        return null;
    }

    public QdrantCloudClusterSummary createCluster(String accountId,
                                                   String deploymentId,
                                                   String clusterName,
                                                   String cloudProviderId,
                                                   String regionId,
                                                   String packageId,
                                                   String managementApiKey) {
        ObjectNode payload = objectMapper.createObjectNode();
        ObjectNode cluster = payload.putObject("cluster");
        cluster.put("accountId", accountId);
        cluster.put("name", clusterName);
        cluster.put("cloudProviderId", cloudProviderId);
        cluster.put("cloudProviderRegionId", regionId);
        ArrayNode labels = cluster.putArray("labels");
        labels.add(keyValue("managed-by", "ai-fabric-platform"));
        labels.add(keyValue("ai-fabric-deployment-id", deploymentId));

        ObjectNode configuration = cluster.putObject("configuration");
        configuration.put("numberOfNodes", 1);
        configuration.put("packageId", packageId);
        ObjectNode databaseConfiguration = configuration.putObject("databaseConfiguration");
        ObjectNode collection = databaseConfiguration.putObject("collection");
        ObjectNode vectors = collection.putObject("vectors");
        vectors.put("onDisk", true);

        HttpRequest request = requestBuilder(
            URI.create(API_BASE_URL + "/api/cluster/v1/accounts/" + encodePath(accountId) + "/clusters"),
            managementApiKey
        )
            .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
            .build();
        HttpResponse<String> response = send(request);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw failure("Qdrant Cloud cluster creation failed", response);
        }
        return toClusterSummary(readJson(response.body()).path("cluster"));
    }

    public QdrantCloudClusterSummary awaitClusterReady(String accountId,
                                                       String clusterId,
                                                       String managementApiKey) {
        Instant deadline = Instant.now().plus(Duration.ofMinutes(5));
        QdrantCloudClusterSummary lastSeen = null;
        while (Instant.now().isBefore(deadline)) {
            lastSeen = getCluster(accountId, clusterId, managementApiKey);
            if (lastSeen != null
                && StringUtils.hasText(lastSeen.endpointUrl())
                && ("CLUSTER_PHASE_HEALTHY".equals(lastSeen.phase())
                || "CLUSTER_PHASE_NOT_READY".equals(lastSeen.phase()))) {
                return lastSeen;
            }
            try {
                Thread.sleep(2_000L);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new RailwayProvisioningException("Interrupted while waiting for Qdrant Cloud cluster readiness.", ex);
            }
        }
        if (lastSeen != null) {
            return lastSeen;
        }
        throw new RailwayProvisioningException("Timed out waiting for Qdrant Cloud cluster '" + clusterId + "' to become reachable.");
    }

    public QdrantCloudClusterSummary getCluster(String accountId,
                                                String clusterId,
                                                String managementApiKey) {
        HttpRequest request = requestBuilder(
            URI.create(API_BASE_URL + "/api/cluster/v1/accounts/" + encodePath(accountId) + "/clusters/" + encodePath(clusterId)),
            managementApiKey
        ).GET().build();
        HttpResponse<String> response = send(request);
        if (response.statusCode() == 404) {
            return null;
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw failure("Qdrant Cloud cluster lookup failed", response);
        }
        return toClusterSummary(readJson(response.body()).path("cluster"));
    }

    public QdrantCloudDatabaseApiKeySummary findDatabaseApiKeyByName(String accountId,
                                                                     String clusterId,
                                                                     String keyName,
                                                                     String managementApiKey) {
        HttpRequest request = requestBuilder(
            uriWithQuery(
                API_BASE_URL + "/api/cluster/auth/v2/accounts/" + encodePath(accountId) + "/database-api-keys",
                Map.of("cluster_id", clusterId)
            ),
            managementApiKey
        ).GET().build();
        HttpResponse<String> response = send(request);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw failure("Qdrant Cloud database API key listing failed", response);
        }
        JsonNode items = readJson(response.body()).path("items");
        if (!items.isArray()) {
            return null;
        }
        for (JsonNode item : items) {
            if (keyName.equals(item.path("name").asText(""))) {
                return toDatabaseApiKeySummary(item);
            }
        }
        return null;
    }

    public QdrantCloudDatabaseApiKeySummary createCollectionDatabaseApiKey(String accountId,
                                                                           String clusterId,
                                                                           String keyName,
                                                                           List<String> collectionNames,
                                                                           String managementApiKey) {
        ObjectNode payload = objectMapper.createObjectNode();
        ObjectNode databaseApiKey = payload.putObject("databaseApiKey");
        databaseApiKey.put("accountId", accountId);
        databaseApiKey.put("clusterId", clusterId);
        databaseApiKey.put("name", keyName);
        // For deployment-managed clusters, omit explicit access rules so Qdrant Cloud
        // creates a cluster-wide manage/write database key. Collection-scoped keys
        // cannot create or reconcile collections during platform provisioning.

        HttpRequest request = requestBuilder(
            URI.create(API_BASE_URL + "/api/cluster/auth/v2/accounts/" + encodePath(accountId) + "/database-api-keys"),
            managementApiKey
        )
            .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
            .build();
        HttpResponse<String> response = send(request);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw failure("Qdrant Cloud database API key creation failed", response);
        }
        return toDatabaseApiKeySummary(readJson(response.body()).path("databaseApiKey"));
    }

    public void deleteDatabaseApiKey(String accountId,
                                     String clusterId,
                                     String databaseApiKeyId,
                                     String managementApiKey) {
        HttpRequest request = requestBuilder(
            uriWithQuery(
                API_BASE_URL + "/api/cluster/auth/v2/accounts/" + encodePath(accountId)
                    + "/database-api-keys/" + encodePath(databaseApiKeyId),
                Map.of("cluster_id", clusterId)
            ),
            managementApiKey
        ).DELETE().build();
        HttpResponse<String> response = send(request);
        if (response.statusCode() == 404) {
            return;
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw failure("Qdrant Cloud database API key deletion failed", response);
        }
    }

    public void deleteCluster(String accountId,
                              String clusterId,
                              String managementApiKey) {
        HttpRequest request = requestBuilder(
            URI.create(API_BASE_URL + "/api/cluster/v1/accounts/" + encodePath(accountId) + "/clusters/" + encodePath(clusterId)),
            managementApiKey
        ).DELETE().build();
        HttpResponse<String> response = send(request);
        if (response.statusCode() == 404) {
            return;
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw failure("Qdrant Cloud cluster deletion failed", response);
        }
    }

    private List<QdrantCloudAccountSummary> listAccounts(String managementApiKey) {
        HttpRequest request = requestBuilder(
            URI.create(API_BASE_URL + "/api/account/v1/accounts"),
            managementApiKey
        ).GET().build();
        HttpResponse<String> response = send(request);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw failure("Qdrant Cloud account lookup failed", response);
        }
        List<QdrantCloudAccountSummary> accounts = new ArrayList<>();
        JsonNode items = readJson(response.body()).path("items");
        if (items.isArray()) {
            items.forEach(item -> accounts.add(new QdrantCloudAccountSummary(
                item.path("id").asText(""),
                item.path("name").asText("")
            )));
        }
        return accounts;
    }

    private QdrantCloudClusterSummary toClusterSummary(JsonNode node) {
        JsonNode configuration = node.path("configuration");
        JsonNode state = node.path("state");
        JsonNode endpoint = state.path("endpoint");
        Map<String, String> labels = new LinkedHashMap<>();
        JsonNode labelItems = node.path("labels");
        if (labelItems.isArray()) {
            labelItems.forEach(item -> labels.put(item.path("key").asText(""), item.path("value").asText("")));
        }
        return new QdrantCloudClusterSummary(
            node.path("id").asText(""),
            node.path("accountId").asText(""),
            node.path("name").asText(""),
            node.path("cloudProviderId").asText(""),
            node.path("cloudProviderRegionId").asText(""),
            configuration.path("packageId").asText(""),
            state.path("phase").asText(""),
            state.path("reason").asText(""),
            endpoint.path("url").asText(""),
            endpoint.path("restPort").asInt(0),
            endpoint.path("grpcPort").asInt(0),
            Map.copyOf(labels)
        );
    }

    private QdrantCloudDatabaseApiKeySummary toDatabaseApiKeySummary(JsonNode node) {
        return new QdrantCloudDatabaseApiKeySummary(
            node.path("id").asText(""),
            node.path("clusterId").asText(""),
            node.path("name").asText(""),
            node.path("postfix").asText(""),
            node.path("key").asText("")
        );
    }

    private ObjectNode keyValue(String key, String value) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("key", key);
        node.put("value", value);
        return node;
    }

    private HttpRequest.Builder requestBuilder(URI uri, String managementApiKey) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(30))
            .header("Accept", "application/json")
            .header("Content-Type", "application/json");
        if (StringUtils.hasText(managementApiKey)) {
            builder.header("Authorization", "apikey " + managementApiKey);
        }
        return builder;
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException ex) {
            throw new RailwayProvisioningException("Qdrant Cloud control plane request failed.", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RailwayProvisioningException("Qdrant Cloud control plane request was interrupted.", ex);
        }
    }

    private JsonNode readJson(String body) {
        try {
            return objectMapper.readTree(body == null || body.isBlank() ? "{}" : body);
        } catch (IOException ex) {
            throw new RailwayProvisioningException("Failed to parse Qdrant Cloud control plane response.", ex);
        }
    }

    private URI uriWithQuery(String baseUrl, Map<String, String> queryParams) {
        StringBuilder builder = new StringBuilder(baseUrl);
        boolean first = true;
        for (Map.Entry<String, String> entry : queryParams.entrySet()) {
            if (!StringUtils.hasText(entry.getValue())) {
                continue;
            }
            builder.append(first ? '?' : '&');
            builder.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
            builder.append('=');
            builder.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
            first = false;
        }
        return URI.create(builder.toString());
    }

    private String encodePath(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private RailwayProvisioningException failure(String prefix, HttpResponse<String> response) {
        String safeSummary = summarizeSafeErrorBody(response.body());
        String suffix = StringUtils.hasText(safeSummary)
            ? " Upstream summary: " + safeSummary
            : (StringUtils.hasText(response.body()) ? " Upstream response body omitted." : "");
        return new RailwayProvisioningException(prefix + " with HTTP " + response.statusCode() + "." + suffix);
    }

    private String summarizeSafeErrorBody(String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            return "";
        }
        try {
            JsonNode root = readJson(responseBody);
            String summary = firstNonBlank(
                text(root, "message"),
                text(root, "error"),
                text(root, "detail"),
                text(root, "reason"),
                text(root.path("status"), "message"),
                text(root.path("error"), "message"),
                text(root.path("result"), "message"),
                text(root.path("result"), "error"),
                text(root.path("cluster"), "reason")
            );
            if (StringUtils.hasText(summary)) {
                return truncate(summary, 240);
            }
            JsonNode sanitized = sanitizeForErrorSummary(root, null, 0);
            return truncate(objectMapper.writeValueAsString(sanitized), 240);
        } catch (Exception ignored) {
            return "";
        }
    }

    private JsonNode sanitizeForErrorSummary(JsonNode node,
                                             String fieldName,
                                             int depth) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return objectMapper.nullNode();
        }
        if (depth > 4) {
            return objectMapper.getNodeFactory().textNode("[TRUNCATED]");
        }
        if (node.isObject()) {
            ObjectNode sanitized = objectMapper.createObjectNode();
            node.fields().forEachRemaining(entry ->
                sanitized.set(entry.getKey(), sanitizeForErrorSummary(entry.getValue(), entry.getKey(), depth + 1))
            );
            return sanitized;
        }
        if (node.isArray()) {
            ArrayNode sanitized = objectMapper.createArrayNode();
            int count = 0;
            for (JsonNode item : node) {
                if (count >= 5) {
                    sanitized.add("[TRUNCATED]");
                    break;
                }
                sanitized.add(sanitizeForErrorSummary(item, fieldName, depth + 1));
                count += 1;
            }
            return sanitized;
        }
        if (node.isTextual() && isSensitiveField(fieldName)) {
            return objectMapper.getNodeFactory().textNode("[REDACTED]");
        }
        return node.deepCopy();
    }

    private boolean isSensitiveField(String fieldName) {
        if (!StringUtils.hasText(fieldName)) {
            return false;
        }
        String normalized = fieldName.replace("-", "").replace("_", "").toLowerCase();
        Set<String> sensitive = Set.of("token", "apikey", "key", "secret", "password", "authorization", "bearer");
        return sensitive.contains(normalized)
            || normalized.endsWith("token")
            || normalized.endsWith("apikey")
            || normalized.endsWith("password")
            || normalized.endsWith("secret");
    }

    private String truncate(String value, int limit) {
        if (!StringUtils.hasText(value) || value.length() <= limit) {
            return value == null ? "" : value;
        }
        return value.substring(0, limit - 3) + "...";
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String text(JsonNode node, String fieldName) {
        return node == null ? "" : node.path(fieldName).asText("").trim();
    }

    public record QdrantCloudAccountResolution(
        String accountId,
        String accountName,
        boolean autoResolved
    ) {
    }

    public record QdrantCloudRegionSummary(
        String id,
        String name,
        String provider,
        boolean available
    ) {
    }

    public record QdrantCloudPackageSummary(
        String id,
        String name,
        String type,
        String currency,
        int unitIntPricePerHour,
        String status,
        String ram,
        String cpu,
        String disk
    ) {
    }

    public record QdrantCloudClusterSummary(
        String id,
        String accountId,
        String name,
        String cloudProviderId,
        String cloudProviderRegionId,
        String packageId,
        String phase,
        String reason,
        String endpointUrl,
        int restPort,
        int grpcPort,
        Map<String, String> labels
    ) {
    }

    private record QdrantCloudAccountSummary(
        String id,
        String name
    ) {
    }

    public record QdrantCloudDatabaseApiKeySummary(
        String id,
        String clusterId,
        String name,
        String postfix,
        String key
    ) {
    }
}
