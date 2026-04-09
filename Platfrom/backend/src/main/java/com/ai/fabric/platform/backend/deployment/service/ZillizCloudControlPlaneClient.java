package com.ai.fabric.platform.backend.deployment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.List;
import java.util.Map;

@Service
public class ZillizCloudControlPlaneClient {

    static final String API_BASE_URL = "https://api.cloud.zilliz.com";

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public ZillizCloudControlPlaneClient(ObjectMapper objectMapper) {
        this(
            objectMapper,
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
        );
    }

    ZillizCloudControlPlaneClient(ObjectMapper objectMapper,
                                  HttpClient httpClient) {
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    public void verifyControlPlaneAccess(String apiKey) {
        HttpRequest request = requestBuilder(uriWithQuery(API_BASE_URL + "/v2/clusters", Map.of(
            "pageSize", "1",
            "currentPage", "1"
        )), apiKey).GET().build();
        HttpResponse<String> response = send(request);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw failure("Zilliz Cloud control-plane access check failed", response);
        }
        ensureSuccessfulApiResponse("Zilliz Cloud control-plane access check failed", readJson(response.body()));
    }

    public ZillizProjectResolution resolveProject(String configuredProjectId,
                                                  String regionId,
                                                  String apiKey) {
        List<ZillizProjectSummary> projects = listProjects(regionId, apiKey);
        if (StringUtils.hasText(configuredProjectId)) {
            return projects.stream()
                .filter(project -> configuredProjectId.equals(project.projectId()))
                .findFirst()
                .map(project -> new ZillizProjectResolution(project.projectId(), project.projectName(), false))
                .orElseThrow(() -> new RailwayProvisioningConfigurationException(
                    "zillizCloudProjectId '" + configuredProjectId + "' was not found for the supplied Zilliz Cloud API key in region '"
                        + regionId + "'."
                ));
        }
        if (projects.isEmpty()) {
            throw new RailwayProvisioningConfigurationException(
                "The supplied Zilliz Cloud API key does not expose any projects in region '" + regionId + "'."
            );
        }
        if (projects.size() > 1) {
            throw new RailwayProvisioningConfigurationException(
                "zillizCloudProjectId is required because the supplied Zilliz Cloud API key can access multiple projects in region '"
                    + regionId + "'."
            );
        }
        ZillizProjectSummary project = projects.get(0);
        return new ZillizProjectResolution(project.projectId(), project.projectName(), true);
    }

    public ZillizClusterSummary findClusterByName(String clusterName,
                                                  String apiKey) {
        int currentPage = 1;
        while (true) {
            HttpRequest request = requestBuilder(uriWithQuery(API_BASE_URL + "/v2/clusters", Map.of(
                "pageSize", "100",
                "currentPage", Integer.toString(currentPage)
            )), apiKey).GET().build();
            HttpResponse<String> response = send(request);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw failure("Zilliz Cloud cluster listing failed", response);
            }
            JsonNode payload = readJson(response.body());
            ensureSuccessfulApiResponse("Zilliz Cloud cluster listing failed", payload);
            JsonNode root = payload.path("data");
            JsonNode clusters = root.path("clusters");
            if (!clusters.isArray()) {
                break;
            }
            for (JsonNode clusterNode : clusters) {
                ZillizClusterSummary cluster = toClusterSummary(clusterNode);
                if (clusterName.equals(cluster.clusterName())) {
                    return cluster;
                }
            }
            int count = root.path("count").asInt(clusters.size());
            int pageSize = root.path("pageSize").asInt(100);
            int maxPages = pageSize <= 0 ? currentPage : (int) Math.ceil((double) count / pageSize);
            if (currentPage >= maxPages || clusters.isEmpty()) {
                break;
            }
            currentPage += 1;
        }
        return null;
    }

    public ZillizClusterCreateResult createCluster(String clusterName,
                                                   String projectId,
                                                   String regionId,
                                                   String plan,
                                                   String cuType,
                                                   int cuSize,
                                                   String apiKey) {
        String normalizedPlan = normalizePlan(plan);
        URI uri;
        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("clusterName", clusterName);
        payload.put("projectId", projectId);
        payload.put("regionId", regionId);

        if ("Free".equals(normalizedPlan)) {
            uri = URI.create(API_BASE_URL + "/v2/clusters/createFree");
        } else if ("Serverless".equals(normalizedPlan)) {
            uri = URI.create(API_BASE_URL + "/v2/clusters/createServerless");
        } else {
            uri = URI.create(API_BASE_URL + "/v2/clusters/createDedicated");
            payload.put("plan", normalizedPlan);
            payload.put("cuType", cuType);
            payload.put("cuSize", cuSize);
        }

        HttpRequest request = requestBuilder(uri, apiKey)
            .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
            .build();
        HttpResponse<String> response = send(request);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw failure("Zilliz Cloud cluster creation failed", response);
        }
        JsonNode root = readJson(response.body());
        ensureSuccessfulApiResponse("Zilliz Cloud cluster creation failed", root);
        JsonNode data = root.path("data");
        String clusterId = firstNonBlank(
            text(data, "clusterId"),
            text(data.path("cluster"), "clusterId"),
            text(data, "id"),
            text(data.path("cluster"), "id"),
            text(root, "clusterId"),
            text(root.path("cluster"), "clusterId")
        );
        if (!StringUtils.hasText(clusterId)) {
            ZillizClusterSummary createdCluster = awaitClusterCreatedByName(clusterName, apiKey);
            if (createdCluster != null) {
                clusterId = createdCluster.clusterId();
            }
        }
        if (!StringUtils.hasText(clusterId)) {
            throw new RailwayProvisioningException("Zilliz Cloud cluster creation response did not include a clusterId.");
        }
        return new ZillizClusterCreateResult(
            clusterId,
            firstNonBlank(
                text(data, "username"),
                text(data, "userName"),
                text(data.path("cluster"), "username"),
                text(root, "username"),
                text(root, "userName")
            ),
            firstNonBlank(
                text(data, "password"),
                text(data.path("cluster"), "password"),
                text(root, "password")
            )
        );
    }

    public ZillizClusterSummary awaitClusterReady(String clusterId,
                                                  String apiKey) {
        Instant deadline = Instant.now().plus(Duration.ofMinutes(5));
        ZillizClusterSummary lastSeen = null;
        while (Instant.now().isBefore(deadline)) {
            lastSeen = getCluster(clusterId, apiKey);
            if (lastSeen != null
                && "RUNNING".equalsIgnoreCase(lastSeen.status())
                && StringUtils.hasText(lastSeen.connectAddress())) {
                return lastSeen;
            }
            sleep(2_000L, "Interrupted while waiting for Zilliz Cloud cluster readiness.");
        }
        if (lastSeen != null) {
            return lastSeen;
        }
        throw new RailwayProvisioningException("Timed out waiting for Zilliz Cloud cluster '" + clusterId + "' to become reachable.");
    }

    public ZillizClusterSummary getCluster(String clusterId,
                                           String apiKey) {
        HttpRequest request = requestBuilder(URI.create(API_BASE_URL + "/v2/clusters/" + encodePath(clusterId)), apiKey)
            .GET()
            .build();
        HttpResponse<String> response = send(request);
        if (response.statusCode() == 404) {
            return null;
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw failure("Zilliz Cloud cluster lookup failed", response);
        }
        JsonNode root = readJson(response.body());
        ensureSuccessfulApiResponse("Zilliz Cloud cluster lookup failed", root);
        ZillizClusterSummary cluster = toClusterSummary(root.path("data"));
        if (isDeletedClusterStatus(cluster.status())) {
            return null;
        }
        return cluster;
    }

    public void deleteCluster(String clusterId,
                              String apiKey) {
        HttpRequest request = requestBuilder(URI.create(API_BASE_URL + "/v2/clusters/" + encodePath(clusterId) + "/drop"), apiKey)
            .DELETE()
            .build();
        HttpResponse<String> response = send(request);
        if (response.statusCode() == 404) {
            return;
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw failure("Zilliz Cloud cluster deletion failed", response);
        }
        JsonNode root = readJson(response.body());
        if (isDeleteAlreadySatisfied(root)) {
            return;
        }
        ensureSuccessfulApiResponse("Zilliz Cloud cluster deletion failed", root);
    }

    public void awaitClusterDeleted(String clusterId,
                                    String apiKey) {
        Instant deadline = Instant.now().plus(Duration.ofMinutes(5));
        while (Instant.now().isBefore(deadline)) {
            ZillizClusterSummary snapshot = getCluster(clusterId, apiKey);
            if (snapshot == null) {
                return;
            }
            sleep(2_000L, "Interrupted while waiting for Zilliz Cloud cluster deletion.");
        }
        throw new RailwayProvisioningException("Timed out waiting for Zilliz Cloud cluster '" + clusterId + "' to delete.");
    }

    private List<ZillizProjectSummary> listProjects(String regionId,
                                                    String apiKey) {
        URI baseUri = uriWithQuery(API_BASE_URL + "/v2/projects", Map.of(
            "pageSize", "100",
            "currentPage", "1"
        ));
        HttpRequest request = requestBuilder(baseUri, apiKey).GET().build();
        HttpResponse<String> response = send(request);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw failure("Zilliz Cloud project listing failed", response);
        }
        JsonNode root = readJson(response.body());
        ensureSuccessfulApiResponse("Zilliz Cloud project listing failed", root);
        JsonNode data = root.path("data");
        if (!data.isArray()) {
            return List.of();
        }
        List<ZillizProjectSummary> projects = new ArrayList<>();
        data.forEach(item -> projects.add(new ZillizProjectSummary(
            text(item, "projectId"),
            text(item, "projectName")
        )));
        return List.copyOf(projects);
    }

    private ZillizClusterSummary awaitClusterCreatedByName(String clusterName,
                                                           String apiKey) {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(30));
        while (Instant.now().isBefore(deadline)) {
            ZillizClusterSummary cluster = findClusterByName(clusterName, apiKey);
            if (cluster != null && StringUtils.hasText(cluster.clusterId())) {
                return cluster;
            }
            sleep(2_000L, "Interrupted while waiting for Zilliz Cloud cluster creation visibility.");
        }
        return null;
    }

    private ZillizClusterSummary toClusterSummary(JsonNode node) {
        return new ZillizClusterSummary(
            text(node, "clusterId"),
            text(node, "clusterName"),
            text(node, "projectId"),
            text(node, "regionId"),
            firstNonBlank(text(node, "plan"), "Serverless"),
            text(node, "cuType"),
            node.path("cuSize").asInt(0),
            text(node, "status"),
            normalizeEndpoint(text(node, "connectAddress")),
            text(node, "deploymentOption")
        );
    }

    private HttpRequest.Builder requestBuilder(URI uri,
                                               String apiKey) {
        return HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(30))
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + apiKey);
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException ex) {
            throw new RailwayProvisioningException("Zilliz Cloud control-plane request failed.", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RailwayProvisioningException("Zilliz Cloud control-plane request was interrupted.", ex);
        }
    }

    private JsonNode readJson(String value) {
        try {
            return value == null || value.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(value);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to parse Zilliz Cloud control-plane JSON.", ex);
        }
    }

    private RailwayProvisioningException failure(String action,
                                                 HttpResponse<String> response) {
        String message = action + " with HTTP " + response.statusCode();
        if (StringUtils.hasText(response.body())) {
            message += ". Upstream response body omitted.";
        }
        return new RailwayProvisioningException(message);
    }

    private void ensureSuccessfulApiResponse(String action,
                                             JsonNode root) {
        if (root == null || root.isMissingNode() || root.isNull()) {
            return;
        }
        JsonNode codeNode = root.path("code");
        if (codeNode.isMissingNode() || codeNode.isNull()) {
            return;
        }
        int code = codeNode.asInt(0);
        if (code == 0) {
            return;
        }
        String summary = firstNonBlank(
            text(root, "message"),
            text(root.path("data"), "message")
        );
        if (StringUtils.hasText(summary)) {
            throw new RailwayProvisioningException(action + ". Upstream summary: " + summary + " (code " + code + ").");
        }
        throw new RailwayProvisioningException(action + ". Upstream code: " + code + ".");
    }

    private boolean isDeleteAlreadySatisfied(JsonNode root) {
        if (root == null || root.isMissingNode() || root.isNull()) {
            return false;
        }
        int code = root.path("code").asInt(0);
        if (code != 40064) {
            return false;
        }
        String summary = firstNonBlank(
            text(root, "message"),
            text(root.path("data"), "message")
        ).toUpperCase();
        return summary.contains("STATUS IS DELETED") || summary.contains("INSTANCE STATUS IS DELETED");
    }

    private boolean isDeletedClusterStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return false;
        }
        String normalized = status.trim().toUpperCase();
        return normalized.equals("DELETED")
            || normalized.equals("DROPPED")
            || normalized.equals("TERMINATED");
    }

    private URI uriWithQuery(String baseUrl,
                             Map<String, String> queryParams) {
        StringBuilder builder = new StringBuilder(baseUrl);
        if (queryParams != null && !queryParams.isEmpty()) {
            builder.append("?");
            boolean first = true;
            for (Map.Entry<String, String> entry : queryParams.entrySet()) {
                if (!first) {
                    builder.append("&");
                }
                first = false;
                builder.append(encodePath(entry.getKey()))
                    .append("=")
                    .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
            }
        }
        return URI.create(builder.toString());
    }

    private String normalizePlan(String plan) {
        String normalized = firstNonBlank(plan, "Serverless");
        for (String candidate : ManagedDeploymentProfileCatalog.SUPPORTED_ZILLIZ_CLOUD_PLANS) {
            if (candidate.equalsIgnoreCase(normalized)) {
                return candidate;
            }
        }
        throw new RailwayProvisioningConfigurationException(
            "zillizCloudClusterPlan must be one of " + ManagedDeploymentProfileCatalog.SUPPORTED_ZILLIZ_CLOUD_PLANS + "."
        );
    }

    private void sleep(long millis, String message) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RailwayProvisioningException(message, ex);
        }
    }

    private String encodePath(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String text(JsonNode node, String field) {
        return node == null ? "" : node.path(field).asText("").trim();
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

    private String normalizeEndpoint(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.endsWith("/")) {
            return trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    public record ZillizProjectSummary(
        String projectId,
        String projectName
    ) {
    }

    public record ZillizProjectResolution(
        String projectId,
        String projectName,
        boolean autoResolved
    ) {
    }

    public record ZillizClusterSummary(
        String clusterId,
        String clusterName,
        String projectId,
        String regionId,
        String plan,
        String cuType,
        int cuSize,
        String status,
        String connectAddress,
        String deploymentOption
    ) {
    }

    public record ZillizClusterCreateResult(
        String clusterId,
        String username,
        String password
    ) {
    }
}
