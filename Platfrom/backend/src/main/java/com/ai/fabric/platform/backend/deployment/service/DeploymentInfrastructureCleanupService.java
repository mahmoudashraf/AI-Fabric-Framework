package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentManagedVectorResourceSummary;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class DeploymentInfrastructureCleanupService {

    private static final Logger log = LoggerFactory.getLogger(DeploymentInfrastructureCleanupService.class);

    private final DeploymentManagedVectorResourceService deploymentManagedVectorResourceService;
    private final PineconeControlPlaneClient pineconeControlPlaneClient;
    private final QdrantCloudControlPlaneClient qdrantCloudControlPlaneClient;
    private final ZillizCloudControlPlaneClient zillizCloudControlPlaneClient;
    private final RailwayGraphqlClient railwayGraphqlClient;
    private final PlatformSecretService platformSecretService;
    private final PlatformAuditService platformAuditService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public DeploymentInfrastructureCleanupService(DeploymentManagedVectorResourceService deploymentManagedVectorResourceService,
                                                  PineconeControlPlaneClient pineconeControlPlaneClient,
                                                  QdrantCloudControlPlaneClient qdrantCloudControlPlaneClient,
                                                  ZillizCloudControlPlaneClient zillizCloudControlPlaneClient,
                                                  RailwayGraphqlClient railwayGraphqlClient,
                                                  PlatformSecretService platformSecretService,
                                                  PlatformAuditService platformAuditService,
                                                  ObjectMapper objectMapper) {
        this(
            deploymentManagedVectorResourceService,
            pineconeControlPlaneClient,
            qdrantCloudControlPlaneClient,
            zillizCloudControlPlaneClient,
            railwayGraphqlClient,
            platformSecretService,
            platformAuditService,
            objectMapper,
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
        );
    }

    DeploymentInfrastructureCleanupService(DeploymentManagedVectorResourceService deploymentManagedVectorResourceService,
                                           PineconeControlPlaneClient pineconeControlPlaneClient,
                                           QdrantCloudControlPlaneClient qdrantCloudControlPlaneClient,
                                           ZillizCloudControlPlaneClient zillizCloudControlPlaneClient,
                                           RailwayGraphqlClient railwayGraphqlClient,
                                           PlatformSecretService platformSecretService,
                                           PlatformAuditService platformAuditService,
                                           ObjectMapper objectMapper,
                                           HttpClient httpClient) {
        this.deploymentManagedVectorResourceService = deploymentManagedVectorResourceService;
        this.pineconeControlPlaneClient = pineconeControlPlaneClient;
        this.qdrantCloudControlPlaneClient = qdrantCloudControlPlaneClient;
        this.zillizCloudControlPlaneClient = zillizCloudControlPlaneClient;
        this.railwayGraphqlClient = railwayGraphqlClient;
        this.platformSecretService = platformSecretService;
        this.platformAuditService = platformAuditService;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    public DeploymentInfrastructureCleanupResult cleanupForHardDelete(DeploymentEntity deployment,
                                                                      DeploymentReleaseEntity latestRelease,
                                                                      String reason) {
        ManagedVectorCleanupResult managedVector = cleanupManagedVectorResources(deployment, reason);
        RailwayCleanupResult railway = cleanupRailwayResources(deployment, latestRelease);
        platformAuditService.record(
            "DEPLOYMENT_HARD_DELETE_INFRASTRUCTURE_CLEANED",
            "DEPLOYMENT",
            deployment.getId(),
            Map.of(
                "reason", defaultReason(reason),
                "managedVectorResourceCount", managedVector.cleanedResourceIds().size(),
                "railwayDeletedProject", railway.projectDeleted(),
                "railwayDeletedServiceCount", railway.deletedServiceIds().size()
            )
        );
        return new DeploymentInfrastructureCleanupResult(managedVector, railway);
    }

    private ManagedVectorCleanupResult cleanupManagedVectorResources(DeploymentEntity deployment,
                                                                     String reason) {
        List<DeploymentManagedVectorResourceSummary> resources = deploymentManagedVectorResourceService.listResources(deployment.getId());
        if (resources.isEmpty()) {
            return new ManagedVectorCleanupResult(List.of(), List.of());
        }

        List<DeploymentManagedVectorResourceSummary> orderedResources = resources.stream()
            .sorted((left, right) -> Integer.compare(cleanupOrder(left), cleanupOrder(right)))
            .toList();
        List<String> cleanedIds = new ArrayList<>();
        Set<String> candidateManagedSecrets = new LinkedHashSet<>();
        Map<String, String> failures = new LinkedHashMap<>();

        for (DeploymentManagedVectorResourceSummary resource : orderedResources) {
            try {
                cleanupManagedVectorResource(resource, orderedResources);
                cleanedIds.add(resource.id());
                resource.secretReferenceNames().stream()
                    .filter(platformSecretService::isManagedSecretName)
                    .forEach(candidateManagedSecrets::add);
            } catch (RuntimeException ex) {
                failures.put(resource.id(), ex.getMessage());
            }
        }

        if (!cleanedIds.isEmpty()) {
            deploymentManagedVectorResourceService.deleteResourceRecords(cleanedIds);
            for (String secretName : candidateManagedSecrets) {
                if (!deploymentManagedVectorResourceService.managedSecretReferencedByActiveResource(secretName)) {
                    platformSecretService.clearManagedSecret(
                        secretName,
                        Map.of(
                            "deploymentId", deployment.getId(),
                            "reason", defaultReason(reason),
                            "action", "HARD_DELETE_DEPLOYMENT"
                        )
                    );
                }
            }
        }

        if (!failures.isEmpty()) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Managed vector cleanup failed before hard delete: " + String.join(" | ", failures.values())
            );
        }

        return new ManagedVectorCleanupResult(List.copyOf(cleanedIds), List.copyOf(candidateManagedSecrets));
    }

    private RailwayCleanupResult cleanupRailwayResources(DeploymentEntity deployment,
                                                         DeploymentReleaseEntity latestRelease) {
        JsonNode details = readJson(latestRelease == null ? null : latestRelease.getProvisioningDetailsJson());
        String projectId = firstNonBlank(text(details, "railway", "projectId"), text(details, "projectId"));
        String runtimeServiceId = text(details, "railway", "services", "runtime", "serviceId");
        String connectorServiceId = text(details, "railway", "services", "restConnector", "serviceId");
        String runnerServiceId = text(details, "railway", "services", "vectorizationRunner", "serviceId");

        List<String> deletedServiceIds = new ArrayList<>();
        Map<String, String> requestedServiceIds = new LinkedHashMap<>();
        if (hasText(runtimeServiceId)) {
            requestedServiceIds.put(runtimeServiceId, "runtime");
        }
        if (hasText(connectorServiceId)) {
            requestedServiceIds.put(connectorServiceId, "restConnector");
        }
        if (hasText(runnerServiceId)) {
            requestedServiceIds.put(runnerServiceId, "vectorizationRunner");
        }

        RailwayGraphqlClient.RailwayProjectSnapshot project = null;
        if (hasText(projectId)) {
            try {
                boolean projectDeleted = deleteRailwayProjectIfPresent(projectId, deployment.getId());
                return new RailwayCleanupResult(projectDeleted, List.copyOf(requestedServiceIds.keySet()));
            } catch (RailwayProvisioningException ex) {
                project = getProjectIfPresent(
                    projectId,
                    "re-check Railway project '" + projectId + "' after project delete failure during hard delete for deployment '"
                        + deployment.getId() + "'"
                );
                if (project == null) {
                    log.warn(
                        "Railway project delete reported failure but project no longer exists; treating hard delete as successful: deploymentId={}, projectId={}",
                        deployment.getId(),
                        projectId
                    );
                    return new RailwayCleanupResult(false, List.copyOf(requestedServiceIds.keySet()));
                }
                log.warn(
                    "Railway project delete failed during hard delete; falling back to service-level cleanup: deploymentId={}, projectId={}, error={}",
                    deployment.getId(),
                    projectId,
                    ex.getMessage()
                );
            }
        }

        for (Map.Entry<String, String> requestedService : requestedServiceIds.entrySet()) {
            String serviceId = requestedService.getKey();
            String serviceRole = requestedService.getValue();
            if (deleteRailwayServiceIfPresent(serviceId, serviceRole, deployment.getId())) {
                deletedServiceIds.add(serviceId);
            }
        }

        boolean projectDeleted = false;
        if (project != null) {
            RailwayGraphqlClient.RailwayProjectSnapshot refreshed = getProjectIfPresent(
                project.id(),
                "refresh Railway project '" + project.id() + "' during hard delete for deployment '" + deployment.getId() + "'"
            );
            if (refreshed != null && refreshed.services().isEmpty()) {
                if (deleteRailwayProjectIfPresent(refreshed.id(), deployment.getId())) {
                    projectDeleted = true;
                }
            }
        }

        return new RailwayCleanupResult(projectDeleted, List.copyOf(deletedServiceIds));
    }

    private void cleanupManagedVectorResource(DeploymentManagedVectorResourceSummary resource,
                                              List<DeploymentManagedVectorResourceSummary> allResources) {
        if ("pinecone".equalsIgnoreCase(resource.vendor())
            && "MANAGED_SERVERLESS_INDEX".equalsIgnoreCase(resource.managedMode())
            && "INDEX".equalsIgnoreCase(resource.resourceType())) {
            cleanupPineconeIndex(resource);
            return;
        }
        if ("qdrant".equalsIgnoreCase(resource.vendor())
            && "COLLECTION".equalsIgnoreCase(resource.resourceType())) {
            cleanupQdrantCollection(resource);
            return;
        }
        if ("qdrant".equalsIgnoreCase(resource.vendor())
            && "MANAGED_CLOUD_CLUSTER".equalsIgnoreCase(resource.managedMode())
            && "DATABASE_API_KEY".equalsIgnoreCase(resource.resourceType())) {
            cleanupQdrantDatabaseApiKey(resource, allResources);
            return;
        }
        if ("qdrant".equalsIgnoreCase(resource.vendor())
            && "MANAGED_CLOUD_CLUSTER".equalsIgnoreCase(resource.managedMode())
            && "CLUSTER".equalsIgnoreCase(resource.resourceType())) {
            cleanupQdrantCluster(resource);
            return;
        }
        if ("zilliz".equalsIgnoreCase(resource.vendor())
            && "MANAGED_ZILLIZ_CLOUD_CLUSTER".equalsIgnoreCase(resource.managedMode())
            && "CLUSTER".equalsIgnoreCase(resource.resourceType())) {
            cleanupZillizCluster(resource);
        }
    }

    private void cleanupPineconeIndex(DeploymentManagedVectorResourceSummary resource) {
        String apiKey = trimToNull(platformSecretService.resolveSecret("PINECONE_API_KEY"));
        if (!hasText(apiKey)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PINECONE_API_KEY must be configured before hard delete can remove Pinecone indexes.");
        }
        String indexName = firstNonBlank(resource.resourceName(), resource.resourceReference());
        PineconeControlPlaneClient.PineconeIndexSummary existing = pineconeControlPlaneClient.findIndexByName(indexName, apiKey);
        if (existing == null) {
            return;
        }
        if ("enabled".equalsIgnoreCase(existing.deletionProtection())) {
            pineconeControlPlaneClient.configureDeletionProtection(indexName, false, apiKey);
        }
        pineconeControlPlaneClient.deleteIndex(indexName, apiKey);
        pineconeControlPlaneClient.awaitIndexDeleted(indexName, apiKey);
    }

    private void cleanupQdrantCollection(DeploymentManagedVectorResourceSummary resource) {
        String collectionName = trimToNull(resource.resourceName());
        String baseUrl = trimToNull(firstNonBlank(text(resource.details(), "baseUrl"), resource.endpoint()));
        if (!hasText(collectionName) || !hasText(baseUrl)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Qdrant collection cleanup requires both collection name and base URL.");
        }
        String apiKey = resolveQdrantDataPlaneKey(resource);
        if (!qdrantCollectionExists(baseUrl, collectionName, apiKey)) {
            return;
        }
        deleteQdrantCollection(baseUrl, collectionName, apiKey);
    }

    private void cleanupQdrantDatabaseApiKey(DeploymentManagedVectorResourceSummary resource,
                                             List<DeploymentManagedVectorResourceSummary> allResources) {
        String managementApiKey = trimToNull(platformSecretService.resolveSecret("QDRANT_CLOUD_MANAGEMENT_API_KEY"));
        if (!hasText(managementApiKey)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "QDRANT_CLOUD_MANAGEMENT_API_KEY must be configured before hard delete can remove Qdrant Cloud resources.");
        }
        JsonNode details = resource.details();
        String accountId = firstNonBlank(text(details, "accountId"), qdrantAccountIdFromClusterResource(allResources));
        String clusterId = firstNonBlank(text(details, "clusterId"), clusterIdFromQdrantClusterResource(allResources));
        String keyName = firstNonBlank(text(details, "databaseApiKeyName"), resource.resourceName());
        if (!hasText(accountId) || !hasText(clusterId) || !hasText(keyName)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Qdrant database API key cleanup is missing account, cluster, or key identifiers.");
        }
        QdrantCloudControlPlaneClient.QdrantCloudDatabaseApiKeySummary existing =
            qdrantCloudControlPlaneClient.findDatabaseApiKeyByName(accountId, clusterId, keyName, managementApiKey);
        if (existing == null) {
            return;
        }
        qdrantCloudControlPlaneClient.deleteDatabaseApiKey(accountId, clusterId, existing.id(), managementApiKey);
    }

    private void cleanupQdrantCluster(DeploymentManagedVectorResourceSummary resource) {
        String managementApiKey = trimToNull(platformSecretService.resolveSecret("QDRANT_CLOUD_MANAGEMENT_API_KEY"));
        if (!hasText(managementApiKey)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "QDRANT_CLOUD_MANAGEMENT_API_KEY must be configured before hard delete can remove Qdrant Cloud resources.");
        }
        JsonNode details = resource.details();
        String accountId = text(details, "accountId");
        String clusterId = firstNonBlank(text(details, "clusterId"), resource.resourceReference());
        if (!hasText(accountId) || !hasText(clusterId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Qdrant cluster cleanup is missing account or cluster identifiers.");
        }
        if (qdrantCloudControlPlaneClient.getCluster(accountId, clusterId, managementApiKey) == null) {
            return;
        }
        qdrantCloudControlPlaneClient.deleteCluster(accountId, clusterId, managementApiKey);
    }

    private void cleanupZillizCluster(DeploymentManagedVectorResourceSummary resource) {
        String apiKey = trimToNull(platformSecretService.resolveSecret("ZILLIZ_CLOUD_API_KEY"));
        if (!hasText(apiKey)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ZILLIZ_CLOUD_API_KEY must be configured before hard delete can remove Zilliz Cloud resources.");
        }
        String clusterId = firstNonBlank(text(resource.details(), "clusterId"), resource.resourceReference());
        if (!hasText(clusterId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Zilliz Cloud cluster cleanup is missing the cluster identifier.");
        }
        if (zillizCloudControlPlaneClient.getCluster(clusterId, apiKey) == null) {
            return;
        }
        try {
            zillizCloudControlPlaneClient.deleteCluster(clusterId, apiKey);
        } catch (RailwayProvisioningException ex) {
            if (isZillizDeleteAlreadySatisfied(ex)) {
                return;
            }
            throw ex;
        }
        try {
            zillizCloudControlPlaneClient.awaitClusterDeleted(clusterId, apiKey);
        } catch (RailwayProvisioningException ex) {
            if (isZillizDeleteAlreadySatisfied(ex)) {
                return;
            }
            ZillizCloudControlPlaneClient.ZillizClusterSummary snapshot = zillizCloudControlPlaneClient.getCluster(clusterId, apiKey);
            if (snapshot == null) {
                return;
            }
            if (isZillizDeletionInProgress(snapshot.status())) {
                log.warn(
                    "Zilliz Cloud cluster delete is still in progress after the wait window; continuing hard delete with background vendor cleanup: clusterId={}, status={}",
                    clusterId,
                    snapshot.status()
                );
                return;
            }
            throw ex;
        }
    }

    private boolean isZillizDeleteAlreadySatisfied(RuntimeException ex) {
        String message = ex == null ? null : ex.getMessage();
        if (!hasText(message)) {
            return false;
        }
        String normalized = message.toUpperCase();
        if (!normalized.contains("40064")) {
            return false;
        }
        return normalized.contains("STATUS IS DELETED")
            || normalized.contains("INSTANCE STATUS IS DELETED")
            || normalized.contains("ALREADY DELETED");
    }

    private boolean isZillizDeletionInProgress(String status) {
        if (!hasText(status)) {
            return false;
        }
        String normalized = status.trim().toUpperCase();
        return normalized.contains("DELET")
            || normalized.contains("DROP")
            || normalized.contains("TERMINAT");
    }

    private boolean qdrantCollectionExists(String baseUrl,
                                           String collectionName,
                                           String apiKey) {
        HttpRequest request = qdrantRequestBuilder(qdrantCollectionUri(baseUrl, collectionName), apiKey)
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
        return true;
    }

    private void deleteQdrantCollection(String baseUrl,
                                        String collectionName,
                                        String apiKey) {
        HttpRequest request = qdrantRequestBuilder(qdrantCollectionUri(baseUrl, collectionName), apiKey)
            .DELETE()
            .build();
        HttpResponse<String> response = send(request);
        if (response.statusCode() == 404) {
            return;
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RailwayProvisioningException(
                "Qdrant delete collection failed for '" + collectionName + "' with HTTP " + response.statusCode()
            );
        }
    }

    private String resolveQdrantDataPlaneKey(DeploymentManagedVectorResourceSummary resource) {
        for (String secretName : resource.secretReferenceNames()) {
            String resolved = trimToNull(platformSecretService.resolveSecret(secretName));
            if (hasText(resolved)) {
                return resolved;
            }
        }
        return trimToNull(platformSecretService.resolveSecret("QDRANT_API_KEY"));
    }

    private HttpRequest.Builder qdrantRequestBuilder(String uri,
                                                     String apiKey) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(uri))
            .timeout(Duration.ofSeconds(30))
            .header("Accept", "application/json")
            .header("Content-Type", "application/json");
        if (hasText(apiKey)) {
            builder.header("api-key", apiKey);
        }
        return builder;
    }

    private String qdrantCollectionUri(String baseUrl, String collectionName) {
        String normalizedBaseUrl = baseUrl != null && baseUrl.endsWith("/")
            ? baseUrl.substring(0, baseUrl.length() - 1)
            : baseUrl;
        return normalizedBaseUrl + "/collections/" + encodePathSegment(collectionName);
    }

    private String encodePathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException ex) {
            throw new RailwayProvisioningException("Qdrant data-plane request failed.", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RailwayProvisioningException("Qdrant data-plane request was interrupted.", ex);
        }
    }

    private RailwayGraphqlClient.RailwayProjectSnapshot getProjectIfPresent(String projectId,
                                                                            String operationDescription) {
        try {
            return railwayGraphqlClient.getProject(projectId);
        } catch (RailwayProvisioningException ex) {
            if (isRailwayNotFound(ex)) {
                return null;
            }
            throw contextualRailwayFailure("Failed to " + operationDescription + ".", ex);
        }
    }

    private boolean deleteRailwayServiceIfPresent(String serviceId,
                                                  String serviceRole,
                                                  String deploymentId) {
        try {
            railwayGraphqlClient.deleteService(serviceId);
            return true;
        } catch (RailwayProvisioningException ex) {
            if (isRailwayNotFound(ex)) {
                return false;
            }
            throw contextualRailwayFailure(
                "Failed to delete Railway " + serviceRole + " service '" + serviceId
                    + "' during hard delete for deployment '" + deploymentId + "'.",
                ex
            );
        }
    }

    private boolean deleteRailwayProjectIfPresent(String projectId,
                                                  String deploymentId) {
        try {
            railwayGraphqlClient.deleteProject(projectId);
            return true;
        } catch (RailwayProvisioningException ex) {
            if (isRailwayNotFound(ex)) {
                return false;
            }
            throw contextualRailwayFailure(
                "Failed to delete Railway project '" + projectId + "' during hard delete for deployment '" + deploymentId + "'.",
                ex
            );
        }
    }

    private RailwayProvisioningException contextualRailwayFailure(String context,
                                                                  RailwayProvisioningException cause) {
        String message = cause.getMessage();
        if (message == null || message.isBlank()) {
            return new RailwayProvisioningException(context, cause);
        }
        return new RailwayProvisioningException(context + " " + message, cause);
    }

    private boolean isRailwayNotFound(RailwayProvisioningException ex) {
        String message = ex.getMessage();
        return message != null && (message.contains("HTTP 404") || message.toLowerCase().contains("not found"));
    }

    private String qdrantAccountIdFromClusterResource(List<DeploymentManagedVectorResourceSummary> resources) {
        return resources.stream()
            .filter(candidate -> "qdrant".equalsIgnoreCase(candidate.vendor()))
            .filter(candidate -> "CLUSTER".equalsIgnoreCase(candidate.resourceType()))
            .map(candidate -> text(candidate.details(), "accountId"))
            .filter(this::hasText)
            .findFirst()
            .orElse(null);
    }

    private String clusterIdFromQdrantClusterResource(List<DeploymentManagedVectorResourceSummary> resources) {
        return resources.stream()
            .filter(candidate -> "qdrant".equalsIgnoreCase(candidate.vendor()))
            .filter(candidate -> "CLUSTER".equalsIgnoreCase(candidate.resourceType()))
            .map(candidate -> firstNonBlank(text(candidate.details(), "clusterId"), candidate.resourceReference()))
            .filter(this::hasText)
            .findFirst()
            .orElse(null);
    }

    private int cleanupOrder(DeploymentManagedVectorResourceSummary resource) {
        if ("qdrant".equalsIgnoreCase(resource.vendor())
            && "COLLECTION".equalsIgnoreCase(resource.resourceType())) {
            return 0;
        }
        if ("qdrant".equalsIgnoreCase(resource.vendor())
            && "DATABASE_API_KEY".equalsIgnoreCase(resource.resourceType())) {
            return 1;
        }
        if ("pinecone".equalsIgnoreCase(resource.vendor())
            && "INDEX".equalsIgnoreCase(resource.resourceType())) {
            return 2;
        }
        if ("qdrant".equalsIgnoreCase(resource.vendor())
            && "CLUSTER".equalsIgnoreCase(resource.resourceType())) {
            return 3;
        }
        if ("zilliz".equalsIgnoreCase(resource.vendor())
            && "CLUSTER".equalsIgnoreCase(resource.resourceType())) {
            return 4;
        }
        return 5;
    }

    private JsonNode readJson(String json) {
        try {
            return objectMapper.readTree(json == null || json.isBlank() ? "{}" : json);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse provisioning details JSON.", ex);
        }
    }

    private String text(JsonNode node, String... path) {
        JsonNode current = node;
        for (String field : path) {
            if (current == null) {
                return null;
            }
            current = current.path(field);
        }
        if (current == null || current.isMissingNode() || current.isNull()) {
            return null;
        }
        String value = current.asText();
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String trimToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private boolean hasText(String value) {
        return StringUtils.hasText(value);
    }

    private String defaultReason(String reason) {
        return hasText(reason) ? reason.trim() : "hard delete";
    }

    record ManagedVectorCleanupResult(
        List<String> cleanedResourceIds,
        List<String> clearedManagedSecrets
    ) {
    }

    record RailwayCleanupResult(
        boolean projectDeleted,
        List<String> deletedServiceIds
    ) {
    }

    public record DeploymentInfrastructureCleanupResult(
        ManagedVectorCleanupResult managedVector,
        RailwayCleanupResult railway
    ) {
    }
}
