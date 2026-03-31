package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentManagedVectorResourceEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentManagedVectorResourceSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentManagedVectorStateSummary;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentManagedVectorResourceRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class DeploymentManagedVectorResourceService {

    static final String RESOURCE_STATUS_ACTIVE = "ACTIVE";
    static final String RESOURCE_STATUS_DETACHED = "DETACHED";
    static final String DRIFT_STATE_ALIGNED = "ALIGNED";
    static final String DRIFT_STATE_DRIFTED = "DRIFTED";
    static final String DRIFT_STATE_DETACHED_HISTORY = "DETACHED_HISTORY";

    private final DeploymentManagedVectorResourceRepository repository;
    private final PlatformAuditService platformAuditService;
    private final ObjectMapper objectMapper;

    public DeploymentManagedVectorResourceService(DeploymentManagedVectorResourceRepository repository,
                                                  PlatformAuditService platformAuditService,
                                                  ObjectMapper objectMapper) {
        this.repository = repository;
        this.platformAuditService = platformAuditService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void syncProvisionedResources(DeploymentEntity deployment,
                                         DeploymentVersionEntity version,
                                         DeploymentReleaseEntity release,
                                         ManagedVectorProvisioningResult provisioningResult) {
        List<DesiredManagedVectorResource> desiredResources = desiredResources(deployment, version, release, provisioningResult);
        List<DeploymentManagedVectorResourceEntity> existingResources = repository.findByDeploymentIdOrderByUpdatedAtDesc(deployment.getId());
        Map<String, DeploymentManagedVectorResourceEntity> existingByKey = new LinkedHashMap<>();
        for (DeploymentManagedVectorResourceEntity entity : existingResources) {
            existingByKey.put(resourceKey(entity.getVendor(), entity.getResourceType(), entity.getResourceName()), entity);
        }

        Instant now = Instant.now();
        List<DeploymentManagedVectorResourceEntity> dirtyEntities = new ArrayList<>();
        int upserted = 0;
        int detached = 0;

        for (DesiredManagedVectorResource desiredResource : desiredResources) {
            String key = resourceKey(desiredResource.vendor(), desiredResource.resourceType(), desiredResource.resourceName());
            DeploymentManagedVectorResourceEntity entity = existingByKey.remove(key);
            if (entity == null) {
                entity = new DeploymentManagedVectorResourceEntity();
                entity.setId("mvr-" + UUID.randomUUID().toString().substring(0, 8));
                entity.setCreatedAt(now);
            }
            boolean changed = applyDesiredState(entity, desiredResource, now);
            if (changed) {
                dirtyEntities.add(entity);
                upserted += 1;
            }
        }

        for (DeploymentManagedVectorResourceEntity remaining : existingByKey.values()) {
            if (!RESOURCE_STATUS_DETACHED.equals(remaining.getResourceStatus())) {
                remaining.setResourceStatus(RESOURCE_STATUS_DETACHED);
                remaining.setUpdatedAt(now);
                remaining.setDeploymentVersionId(version.getId());
                remaining.setDeploymentReleaseId(release.getId());
                dirtyEntities.add(remaining);
                detached += 1;
            }
        }

        if (!dirtyEntities.isEmpty()) {
            repository.saveAll(dirtyEntities);
            platformAuditService.record(
                "MANAGED_VECTOR_RESOURCES_SYNCED",
                "DEPLOYMENT",
                deployment.getId(),
                Map.of(
                    "deploymentId", deployment.getId(),
                    "versionId", version.getId(),
                    "releaseId", release.getId(),
                    "upsertedCount", upserted,
                    "detachedCount", detached,
                    "managedResourceCount", desiredResources.size()
                )
            );
        }
    }

    @Transactional(readOnly = true)
    public DeploymentManagedVectorStateSummary buildStateSummary(String deploymentId,
                                                                 JsonNode providerConfig,
                                                                 JsonNode entityConfig,
                                                                 String activeVersionId) {
        List<DeploymentManagedVectorResourceEntity> entities = repository.findByDeploymentIdOrderByUpdatedAtDesc(deploymentId);
        ManagedVectorDriftSummary driftSummary = summarizeDrift(deploymentId, providerConfig, entityConfig, activeVersionId, entities);
        List<DeploymentManagedVectorResourceSummary> resources = entities.stream()
            .map(entity -> {
                ManagedVectorResourceDrift drift = driftSummary.resourceDriftById().get(entity.getId());
                String driftState = RESOURCE_STATUS_DETACHED.equals(entity.getResourceStatus())
                    ? DRIFT_STATE_DETACHED_HISTORY
                    : drift == null ? DRIFT_STATE_ALIGNED : drift.state();
                String driftMessage = drift == null ? null : drift.message();
                return toSummary(entity, driftState, driftMessage);
            })
            .toList();
        List<DeploymentManagedVectorResourceSummary> activeResources = resources.stream()
            .filter(resource -> RESOURCE_STATUS_ACTIVE.equals(resource.resourceStatus()))
            .toList();
        List<DeploymentManagedVectorResourceSummary> detachedResources = resources.stream()
            .filter(resource -> RESOURCE_STATUS_DETACHED.equals(resource.resourceStatus()))
            .toList();

        boolean managedRequested = ManagedDeploymentProfileCatalog.managedVectorProvisioningRequested(providerConfig);
        String vectorStrategy = ManagedDeploymentProfileCatalog.resolveVectorStrategy(providerConfig);
        String vectorProvisioningMode = ManagedDeploymentProfileCatalog.resolveVectorProvisioningMode(providerConfig);

        String status;
        String summaryMessage;
        if (!managedRequested) {
            if (activeResources.isEmpty() && detachedResources.isEmpty()) {
                status = "INFO";
                summaryMessage = "This deployment does not currently request platform-managed external vector resources.";
            } else if (activeResources.isEmpty()) {
                status = "INFO";
                summaryMessage = "This deployment does not currently request platform-managed external vector resources. "
                    + detachedResources.size() + " detached historical resource(s) are retained for audit visibility.";
            } else {
                status = "WARNING";
                summaryMessage = "Active managed vector resources still exist even though the current draft no longer requests them. Reapply or clean up the managed resource state.";
            }
        } else if (!StringUtils.hasText(activeVersionId)) {
            status = activeResources.isEmpty()
                ? "WARNING"
                : driftSummary.detected() ? driftSummary.status() : "READY";
            summaryMessage = activeResources.isEmpty()
                ? "Managed vector resources are requested and will be created or reconciled during the next apply."
                : activeResources.size() + " managed vector resource(s) already exist before a live version is active.";
            if (driftSummary.detected()) {
                summaryMessage += " " + driftSummary.message();
            }
        } else if (activeResources.isEmpty() || "BLOCKED".equals(driftSummary.status())) {
            status = "BLOCKED";
            summaryMessage = driftSummary.detected()
                ? driftSummary.message()
                : "The live deployment expects managed vector resources, but no active resource records are registered.";
        } else if (driftSummary.detected()) {
            status = "WARNING";
            summaryMessage = driftSummary.message();
        } else {
            status = "READY";
            summaryMessage = activeResources.size() + " managed vector resource(s) are registered for the current deployment state.";
            if (!detachedResources.isEmpty()) {
                summaryMessage += " " + detachedResources.size() + " detached historical resource(s) remain visible for audit and lifecycle follow-up.";
            }
        }

        return new DeploymentManagedVectorStateSummary(
            status,
            managedRequested,
            vectorStrategy,
            vectorProvisioningMode,
            driftSummary.detected(),
            driftSummary.status(),
            activeResources.size(),
            detachedResources.size(),
            driftSummary.driftedResourceCount(),
            resources,
            driftSummary.message(),
            summaryMessage
        );
    }

    @Transactional(readOnly = true)
    public List<DeploymentManagedVectorResourceSummary> listResources(String deploymentId) {
        return repository.findByDeploymentIdOrderByUpdatedAtDesc(deploymentId).stream()
            .map(entity -> toSummary(
                entity,
                RESOURCE_STATUS_DETACHED.equals(entity.getResourceStatus()) ? DRIFT_STATE_DETACHED_HISTORY : DRIFT_STATE_ALIGNED,
                null
            ))
            .toList();
    }

    @Transactional
    public List<DeploymentManagedVectorResourceSummary> detachActiveResources(String deploymentId) {
        List<DeploymentManagedVectorResourceEntity> entities = repository.findByDeploymentIdOrderByUpdatedAtDesc(deploymentId);
        Instant now = Instant.now();
        List<DeploymentManagedVectorResourceEntity> dirtyEntities = new ArrayList<>();
        List<DeploymentManagedVectorResourceSummary> detachedResources = new ArrayList<>();
        for (DeploymentManagedVectorResourceEntity entity : entities) {
            if (!RESOURCE_STATUS_ACTIVE.equals(entity.getResourceStatus())) {
                continue;
            }
            entity.setResourceStatus(RESOURCE_STATUS_DETACHED);
            entity.setUpdatedAt(now);
            dirtyEntities.add(entity);
            detachedResources.add(toSummary(entity, DRIFT_STATE_DETACHED_HISTORY, null));
        }
        if (!dirtyEntities.isEmpty()) {
            repository.saveAll(dirtyEntities);
        }
        return List.copyOf(detachedResources);
    }

    @Transactional
    public void deleteResourceRecords(List<String> resourceIds) {
        if (resourceIds == null || resourceIds.isEmpty()) {
            return;
        }
        repository.deleteAllByIdInBatch(resourceIds);
    }

    @Transactional(readOnly = true)
    public boolean managedSecretReferencedByActiveResource(String secretName) {
        if (!StringUtils.hasText(secretName)) {
            return false;
        }
        return repository.findAll().stream()
            .filter(entity -> RESOURCE_STATUS_ACTIVE.equals(entity.getResourceStatus()))
            .anyMatch(entity -> readStringList(entity.getSecretReferenceNamesJson()).contains(secretName));
    }

    private boolean applyDesiredState(DeploymentManagedVectorResourceEntity entity,
                                      DesiredManagedVectorResource desiredResource,
                                      Instant now) {
        boolean changed = false;
        changed |= setIfDifferent(entity.getDeploymentId(), desiredResource.deploymentId(), entity::setDeploymentId);
        changed |= setIfDifferent(entity.getDeploymentVersionId(), desiredResource.deploymentVersionId(), entity::setDeploymentVersionId);
        changed |= setIfDifferent(entity.getDeploymentReleaseId(), desiredResource.deploymentReleaseId(), entity::setDeploymentReleaseId);
        changed |= setIfDifferent(entity.getVendor(), desiredResource.vendor(), entity::setVendor);
        changed |= setIfDifferent(entity.getVectorStrategy(), desiredResource.vectorStrategy(), entity::setVectorStrategy);
        changed |= setIfDifferent(entity.getVectorProvisioningMode(), desiredResource.vectorProvisioningMode(), entity::setVectorProvisioningMode);
        changed |= setIfDifferent(entity.getManagedMode(), desiredResource.managedMode(), entity::setManagedMode);
        changed |= setIfDifferent(entity.getResourceType(), desiredResource.resourceType(), entity::setResourceType);
        changed |= setIfDifferent(entity.getResourceName(), desiredResource.resourceName(), entity::setResourceName);
        changed |= setIfDifferent(entity.getResourceReference(), desiredResource.resourceReference(), entity::setResourceReference);
        changed |= setIfDifferent(entity.getEndpoint(), desiredResource.endpoint(), entity::setEndpoint);
        changed |= setIfDifferent(entity.getResourceStatus(), RESOURCE_STATUS_ACTIVE, entity::setResourceStatus);
        changed |= setIfDifferent(entity.getProvisioningState(), desiredResource.provisioningState(), entity::setProvisioningState);

        String secretReferenceNamesJson = writeJsonArray(desiredResource.secretReferenceNames());
        changed |= setIfDifferent(entity.getSecretReferenceNamesJson(), secretReferenceNamesJson, entity::setSecretReferenceNamesJson);
        String detailsJson = writeJson(desiredResource.details());
        changed |= setIfDifferent(entity.getDetailsJson(), detailsJson, entity::setDetailsJson);

        if (changed || entity.getUpdatedAt() == null) {
            entity.setUpdatedAt(now);
        }
        return changed;
    }

    private List<DesiredManagedVectorResource> desiredResources(DeploymentEntity deployment,
                                                                DeploymentVersionEntity version,
                                                                DeploymentReleaseEntity release,
                                                                ManagedVectorProvisioningResult provisioningResult) {
        JsonNode details = provisioningResult == null ? null : provisioningResult.details();
        JsonNode providerConfig = provisioningResult == null ? null : provisioningResult.effectiveProviderConfig();
        if (details == null || !details.path("enabled").asBoolean(false)) {
            return List.of();
        }

        String vectorStrategy = firstNonBlank(details.path("vectorStrategy").asText(null),
            ManagedDeploymentProfileCatalog.resolveVectorStrategy(providerConfig));
        String provisioningMode = ManagedDeploymentProfileCatalog.resolveVectorProvisioningMode(providerConfig);
        String managedMode = details.path("mode").asText("NONE");
        return switch (managedMode) {
            case "MANAGED_SERVERLESS_INDEX" -> List.of(new DesiredManagedVectorResource(
                deployment.getId(),
                version.getId(),
                release.getId(),
                "pinecone",
                vectorStrategy,
                provisioningMode,
                managedMode,
                "INDEX",
                details.path("indexName").asText(),
                details.path("indexName").asText(null),
                details.path("apiHost").asText(null),
                details.path("state").asText("UNKNOWN"),
                pineconeSecretReferences(details),
                details.deepCopy()
            ));
            case "MANAGED_CLOUD_CLUSTER" -> desiredManagedQdrantCloudResources(
                deployment.getId(),
                version.getId(),
                release.getId(),
                vectorStrategy,
                provisioningMode,
                details
            );
            case "MANAGED_COLLECTIONS" -> desiredQdrantCollections(
                deployment.getId(),
                version.getId(),
                release.getId(),
                vectorStrategy,
                provisioningMode,
                managedMode,
                details
            );
            default -> List.of();
        };
    }

    private List<String> pineconeSecretReferences(JsonNode details) {
        List<String> references = new ArrayList<>();
        references.add("PINECONE_API_KEY");
        String runtimeSecretName = details.path("runtimeApiKeySecretName").asText("");
        if (StringUtils.hasText(runtimeSecretName)) {
            references.add(runtimeSecretName);
        }
        return List.copyOf(references);
    }

    private List<DesiredManagedVectorResource> desiredQdrantCollections(String deploymentId,
                                                                        String versionId,
                                                                        String releaseId,
                                                                        String vectorStrategy,
                                                                        String provisioningMode,
                                                                        String managedMode,
                                                                        JsonNode details) {
        String baseUrl = details.path("baseUrl").asText(null);
        ArrayNode collections = details.path("collections").isArray()
            ? (ArrayNode) details.path("collections")
            : objectMapper.createArrayNode();
        List<DesiredManagedVectorResource> resources = new ArrayList<>();
        for (JsonNode collection : collections) {
            String name = collection.path("name").asText("");
            if (!StringUtils.hasText(name)) {
                continue;
            }
            resources.add(new DesiredManagedVectorResource(
                deploymentId,
                versionId,
                releaseId,
                "qdrant",
                vectorStrategy,
                provisioningMode,
                managedMode,
                "COLLECTION",
                name,
                StringUtils.hasText(baseUrl) ? normalizeBaseUrl(baseUrl) + "/collections/" + name : name,
                baseUrl,
                collection.path("state").asText("UNKNOWN"),
                List.of("QDRANT_API_KEY"),
                objectMapper.createObjectNode()
                    .put("name", name)
                    .put("state", collection.path("state").asText("UNKNOWN"))
                    .put("baseUrl", baseUrl)
            ));
        }
        return resources;
    }

    private List<DesiredManagedVectorResource> desiredManagedQdrantCloudResources(String deploymentId,
                                                                                  String versionId,
                                                                                  String releaseId,
                                                                                  String vectorStrategy,
                                                                                  String provisioningMode,
                                                                                  JsonNode details) {
        List<DesiredManagedVectorResource> resources = new ArrayList<>();
        String baseUrl = details.path("baseUrl").asText(null);
        String clusterName = details.path("clusterName").asText("");
        String clusterId = details.path("clusterId").asText("");
        String runtimeSecretName = details.path("databaseApiKeySecretName").asText("");

        if (StringUtils.hasText(clusterName) || StringUtils.hasText(clusterId)) {
            resources.add(new DesiredManagedVectorResource(
                deploymentId,
                versionId,
                releaseId,
                "qdrant",
                vectorStrategy,
                provisioningMode,
                "MANAGED_CLOUD_CLUSTER",
                "CLUSTER",
                StringUtils.hasText(clusterName) ? clusterName : clusterId,
                StringUtils.hasText(clusterId) ? clusterId : clusterName,
                baseUrl,
                details.path("clusterState").asText("UNKNOWN"),
                List.of("QDRANT_CLOUD_MANAGEMENT_API_KEY"),
                objectMapper.createObjectNode()
                    .put("clusterId", clusterId)
                    .put("clusterName", clusterName)
                    .put("accountId", details.path("accountId").asText(""))
                    .put("cloudProviderId", details.path("cloudProviderId").asText(""))
                    .put("regionId", details.path("regionId").asText(""))
                    .put("packageId", details.path("packageId").asText(""))
                    .put("baseUrl", baseUrl)
            ));
        }

        String apiKeyName = details.path("databaseApiKeyName").asText("");
        String apiKeyId = details.path("databaseApiKeyId").asText("");
        if (StringUtils.hasText(apiKeyName) || StringUtils.hasText(apiKeyId)) {
            List<String> secretReferences = new ArrayList<>();
            secretReferences.add("QDRANT_CLOUD_MANAGEMENT_API_KEY");
            if (StringUtils.hasText(runtimeSecretName)) {
                secretReferences.add(runtimeSecretName);
            }
            resources.add(new DesiredManagedVectorResource(
                deploymentId,
                versionId,
                releaseId,
                "qdrant",
                vectorStrategy,
                provisioningMode,
                "MANAGED_CLOUD_CLUSTER",
                "DATABASE_API_KEY",
                StringUtils.hasText(apiKeyName) ? apiKeyName : apiKeyId,
                StringUtils.hasText(apiKeyId) ? apiKeyId : apiKeyName,
                baseUrl,
                details.path("databaseApiKeyState").asText("UNKNOWN"),
                List.copyOf(secretReferences),
                objectMapper.createObjectNode()
                    .put("accountId", details.path("accountId").asText(""))
                    .put("databaseApiKeyId", apiKeyId)
                    .put("databaseApiKeyName", apiKeyName)
                    .put("databaseApiKeySecretName", runtimeSecretName)
                    .put("clusterId", clusterId)
            ));
        }

        ArrayNode collections = details.path("collections").isArray()
            ? (ArrayNode) details.path("collections")
            : objectMapper.createArrayNode();
        for (JsonNode collection : collections) {
            String name = collection.path("name").asText("");
            if (!StringUtils.hasText(name)) {
                continue;
            }
            List<String> secretReferences = new ArrayList<>();
            if (StringUtils.hasText(runtimeSecretName)) {
                secretReferences.add(runtimeSecretName);
            }
            resources.add(new DesiredManagedVectorResource(
                deploymentId,
                versionId,
                releaseId,
                "qdrant",
                vectorStrategy,
                provisioningMode,
                "MANAGED_CLOUD_CLUSTER",
                "COLLECTION",
                name,
                StringUtils.hasText(baseUrl) ? normalizeBaseUrl(baseUrl) + "/collections/" + name : name,
                baseUrl,
                collection.path("state").asText("UNKNOWN"),
                List.copyOf(secretReferences),
                objectMapper.createObjectNode()
                    .put("name", name)
                    .put("state", collection.path("state").asText("UNKNOWN"))
                    .put("baseUrl", baseUrl)
                    .put("clusterName", clusterName)
            ));
        }
        return resources;
    }

    private ManagedVectorDriftSummary summarizeDrift(String deploymentId,
                                                     JsonNode providerConfig,
                                                     JsonNode entityConfig,
                                                     String activeVersionId,
                                                     List<DeploymentManagedVectorResourceEntity> entities) {
        List<DeploymentManagedVectorResourceEntity> activeEntities = entities.stream()
            .filter(entity -> RESOURCE_STATUS_ACTIVE.equals(entity.getResourceStatus()))
            .toList();
        boolean managedRequested = ManagedDeploymentProfileCatalog.managedVectorProvisioningRequested(providerConfig);
        if (!managedRequested) {
            if (activeEntities.isEmpty()) {
                return new ManagedVectorDriftSummary(false, "READY", 0, null, Map.of());
            }
            Map<String, ManagedVectorResourceDrift> unexpected = new HashMap<>();
            for (DeploymentManagedVectorResourceEntity entity : activeEntities) {
                unexpected.put(
                    entity.getId(),
                    new ManagedVectorResourceDrift(
                        DRIFT_STATE_DRIFTED,
                        "This resource remains active even though the current deployment config no longer requests platform-managed vector infrastructure."
                    )
                );
            }
            return new ManagedVectorDriftSummary(
                true,
                "WARNING",
                activeEntities.size(),
                "Active managed vector resources still exist even though the current deployment config no longer requests them.",
                unexpected
            );
        }

        Map<String, ExpectedManagedVectorResource> expectedByKey = expectedResources(deploymentId, providerConfig, entityConfig).stream()
            .collect(LinkedHashMap::new, (map, item) -> map.put(item.key(), item), LinkedHashMap::putAll);
        Map<String, ManagedVectorResourceDrift> driftById = new HashMap<>();
        List<String> issues = new ArrayList<>();
        int driftedCount = 0;

        for (DeploymentManagedVectorResourceEntity entity : activeEntities) {
            String key = resourceKey(entity.getVendor(), entity.getResourceType(), entity.getResourceName());
            ExpectedManagedVectorResource expected = expectedByKey.remove(key);
            ManagedVectorResourceDrift drift = evaluateResourceDrift(entity, expected, activeVersionId);
            if (!DRIFT_STATE_ALIGNED.equals(drift.state())) {
                driftById.put(entity.getId(), drift);
                driftedCount += 1;
                issues.add(entity.getVendor() + " " + entity.getResourceType().toLowerCase(Locale.ROOT)
                    + " '" + entity.getResourceName() + "': " + drift.message());
            }
        }

        if (!expectedByKey.isEmpty()) {
            driftedCount += expectedByKey.size();
            expectedByKey.values().forEach(expected -> issues.add("Missing active resource record for "
                + expected.vendor() + " " + expected.resourceType().toLowerCase(Locale.ROOT)
                + " '" + expected.resourceName() + "'."));
        }

        if (issues.isEmpty()) {
            return new ManagedVectorDriftSummary(false, "READY", 0, null, driftById);
        }
        String status = expectedByKey.isEmpty() ? "WARNING" : "BLOCKED";
        return new ManagedVectorDriftSummary(
            true,
            status,
            driftedCount,
            "Managed vector drift detected. " + String.join(" ", issues),
            driftById
        );
    }

    private ManagedVectorResourceDrift evaluateResourceDrift(DeploymentManagedVectorResourceEntity entity,
                                                             ExpectedManagedVectorResource expected,
                                                             String activeVersionId) {
        if (expected == null) {
            return new ManagedVectorResourceDrift(
                DRIFT_STATE_DRIFTED,
                "No active deployment config expects this managed resource."
            );
        }

        List<String> issues = new ArrayList<>();
        if (StringUtils.hasText(activeVersionId)
            && StringUtils.hasText(entity.getDeploymentVersionId())
            && !Objects.equals(activeVersionId, entity.getDeploymentVersionId())) {
            issues.add("bound to deployment version '" + entity.getDeploymentVersionId()
                + "' instead of the active version '" + activeVersionId + "'");
        }
        compareText("provisioning mode", expected.vectorProvisioningMode(), entity.getVectorProvisioningMode(), issues);
        compareText("managed mode", expected.managedMode(), entity.getManagedMode(), issues);
        compareEndpoint(expected.endpoint(), entity.getEndpoint(), issues);
        compareSecretReferences(expected.secretReferenceNames(), readStringList(entity.getSecretReferenceNamesJson()), issues);
        compareExpectedDetails(expected, readJson(entity.getDetailsJson()), issues);

        if (issues.isEmpty()) {
            return new ManagedVectorResourceDrift(DRIFT_STATE_ALIGNED, null);
        }
        return new ManagedVectorResourceDrift(DRIFT_STATE_DRIFTED, String.join("; ", issues));
    }

    private void compareExpectedDetails(ExpectedManagedVectorResource expected,
                                        JsonNode actualDetails,
                                        List<String> issues) {
        if ("pinecone".equals(expected.vendor()) && "INDEX".equals(expected.resourceType())) {
            compareText("cloud", expected.detail("cloud"), actualDetails.path("cloud").asText(""), issues);
            compareText("region", expected.detail("region"), actualDetails.path("region").asText(""), issues);
            compareText("metric", expected.detail("metric"), actualDetails.path("metric").asText(""), issues);
            compareNumber("dimensions", expected.detail("dimensions"), actualDetails.path("dimensions"), issues);
            return;
        }
        if ("qdrant".equals(expected.vendor()) && "CLUSTER".equals(expected.resourceType())) {
            compareText("account", expected.detail("accountId"), actualDetails.path("accountId").asText(""), issues);
            compareText("cloud provider", expected.detail("cloudProviderId"), actualDetails.path("cloudProviderId").asText(""), issues);
            compareText("region", expected.detail("regionId"), actualDetails.path("regionId").asText(""), issues);
            compareText("package", expected.detail("packageId"), actualDetails.path("packageId").asText(""), issues);
            return;
        }
        if ("qdrant".equals(expected.vendor()) && "DATABASE_API_KEY".equals(expected.resourceType())) {
            compareText(
                "runtime secret name",
                expected.detail("databaseApiKeySecretName"),
                actualDetails.path("databaseApiKeySecretName").asText(""),
                issues
            );
        }
    }

    private List<ExpectedManagedVectorResource> expectedResources(String deploymentId,
                                                                  JsonNode providerConfig,
                                                                  JsonNode entityConfig) {
        List<ExpectedManagedVectorResource> expected = new ArrayList<>();
        if (ManagedDeploymentProfileCatalog.pineconePlatformManaged(providerConfig)) {
            String indexName = ManagedDeploymentProfileCatalog.pineconeIndexName(providerConfig);
            if (StringUtils.hasText(indexName)) {
                expected.add(new ExpectedManagedVectorResource(
                    resourceKey("pinecone", "INDEX", indexName),
                    "pinecone",
                    "INDEX",
                    indexName,
                    ManagedDeploymentProfileCatalog.VECTOR_PROVISIONING_MODE_PLATFORM_MANAGED,
                    "MANAGED_SERVERLESS_INDEX",
                    normalizeEndpoint(ManagedDeploymentProfileCatalog.pineconeApiHost(providerConfig)),
                    expectedPineconeSecretReferences(providerConfig),
                    Map.of(
                        "cloud", ManagedDeploymentProfileCatalog.pineconeCloud(providerConfig),
                        "region", ManagedDeploymentProfileCatalog.pineconeRegion(providerConfig),
                        "metric", ManagedDeploymentProfileCatalog.pineconeMetric(providerConfig),
                        "dimensions", Integer.toString(ManagedDeploymentProfileCatalog.pineconeDimensions(providerConfig))
                    )
                ));
            }
            return expected;
        }

        List<String> entityTypes = resolveEntityTypes(entityConfig);
        if (ManagedDeploymentProfileCatalog.qdrantPlatformManaged(providerConfig)) {
            String clusterName = expectedManagedQdrantClusterName(deploymentId, providerConfig);
            expected.add(new ExpectedManagedVectorResource(
                resourceKey("qdrant", "CLUSTER", clusterName),
                "qdrant",
                "CLUSTER",
                clusterName,
                ManagedDeploymentProfileCatalog.VECTOR_PROVISIONING_MODE_PLATFORM_MANAGED,
                "MANAGED_CLOUD_CLUSTER",
                normalizeBaseUrl(ManagedDeploymentProfileCatalog.qdrantHost(providerConfig)),
                List.of("QDRANT_CLOUD_MANAGEMENT_API_KEY"),
                expectedQdrantClusterDetails(providerConfig)
            ));

            String runtimeSecretName = ManagedDeploymentProfileCatalog.qdrantRuntimeApiKeySecretName(providerConfig);
            String apiKeyName = expectedManagedQdrantDatabaseApiKeyName(deploymentId);
            List<String> apiKeySecretReferences = new ArrayList<>();
            apiKeySecretReferences.add("QDRANT_CLOUD_MANAGEMENT_API_KEY");
            if (StringUtils.hasText(runtimeSecretName)) {
                apiKeySecretReferences.add(runtimeSecretName);
            }
            expected.add(new ExpectedManagedVectorResource(
                resourceKey("qdrant", "DATABASE_API_KEY", apiKeyName),
                "qdrant",
                "DATABASE_API_KEY",
                apiKeyName,
                ManagedDeploymentProfileCatalog.VECTOR_PROVISIONING_MODE_PLATFORM_MANAGED,
                "MANAGED_CLOUD_CLUSTER",
                normalizeBaseUrl(ManagedDeploymentProfileCatalog.qdrantHost(providerConfig)),
                List.copyOf(apiKeySecretReferences),
                Map.of("databaseApiKeySecretName", StringUtils.hasText(runtimeSecretName) ? runtimeSecretName : "")
            ));

            for (String entityType : entityTypes) {
                expected.add(new ExpectedManagedVectorResource(
                    resourceKey("qdrant", "COLLECTION", entityType),
                    "qdrant",
                    "COLLECTION",
                    entityType,
                    ManagedDeploymentProfileCatalog.VECTOR_PROVISIONING_MODE_PLATFORM_MANAGED,
                    "MANAGED_CLOUD_CLUSTER",
                    normalizeBaseUrl(ManagedDeploymentProfileCatalog.qdrantHost(providerConfig)),
                    StringUtils.hasText(runtimeSecretName) ? List.of(runtimeSecretName) : List.of(),
                    Map.of()
                ));
            }
            return expected;
        }

        if (ManagedDeploymentProfileCatalog.usesQdrant(providerConfig)
            && ManagedDeploymentProfileCatalog.qdrantManagedCollectionsEnabled(providerConfig)) {
            for (String entityType : entityTypes) {
                expected.add(new ExpectedManagedVectorResource(
                    resourceKey("qdrant", "COLLECTION", entityType),
                    "qdrant",
                    "COLLECTION",
                    entityType,
                    ManagedDeploymentProfileCatalog.resolveVectorProvisioningMode(providerConfig),
                    "MANAGED_COLLECTIONS",
                    normalizeBaseUrl(ManagedDeploymentProfileCatalog.qdrantHost(providerConfig)),
                    List.of("QDRANT_API_KEY"),
                    Map.of()
                ));
            }
        }
        return expected;
    }

    private List<String> expectedPineconeSecretReferences(JsonNode providerConfig) {
        List<String> references = new ArrayList<>();
        references.add("PINECONE_API_KEY");
        String runtimeSecretName = ManagedDeploymentProfileCatalog.pineconeRuntimeApiKeySecretName(providerConfig);
        if (StringUtils.hasText(runtimeSecretName)) {
            references.add(runtimeSecretName);
        }
        return List.copyOf(references);
    }

    private Map<String, String> expectedQdrantClusterDetails(JsonNode providerConfig) {
        Map<String, String> details = new LinkedHashMap<>();
        String accountId = ManagedDeploymentProfileCatalog.qdrantCloudAccountId(providerConfig);
        if (StringUtils.hasText(accountId)) {
            details.put("accountId", accountId);
        }
        details.put("cloudProviderId", ManagedDeploymentProfileCatalog.qdrantCloudProviderId(providerConfig));
        details.put("regionId", ManagedDeploymentProfileCatalog.qdrantCloudRegionId(providerConfig));
        String packageId = ManagedDeploymentProfileCatalog.qdrantCloudPackageId(providerConfig);
        if (StringUtils.hasText(packageId)) {
            details.put("packageId", packageId);
        }
        return Map.copyOf(details);
    }

    private void compareText(String label,
                             String expected,
                             String actual,
                             List<String> issues) {
        String normalizedExpected = trimToEmpty(expected);
        String normalizedActual = trimToEmpty(actual);
        if (normalizedExpected.isEmpty()) {
            return;
        }
        if (!normalizedExpected.equals(normalizedActual)) {
            issues.add(label + " is '" + normalizedActual + "' instead of '" + normalizedExpected + "'");
        }
    }

    private void compareNumber(String label,
                               String expected,
                               JsonNode actualNode,
                               List<String> issues) {
        String normalizedExpected = trimToEmpty(expected);
        if (normalizedExpected.isEmpty()) {
            return;
        }
        String actual = actualNode == null || actualNode.isMissingNode() ? "" : actualNode.asText("");
        if (!normalizedExpected.equals(actual.trim())) {
            issues.add(label + " is '" + actual.trim() + "' instead of '" + normalizedExpected + "'");
        }
    }

    private void compareEndpoint(String expected,
                                 String actual,
                                 List<String> issues) {
        String normalizedExpected = normalizeEndpoint(expected);
        String normalizedActual = normalizeEndpoint(actual);
        if (normalizedExpected.isEmpty()) {
            return;
        }
        if (!Objects.equals(normalizedExpected, normalizedActual)) {
            issues.add("endpoint is '" + normalizedActual + "' instead of '" + normalizedExpected + "'");
        }
    }

    private void compareSecretReferences(List<String> expected,
                                         List<String> actual,
                                         List<String> issues) {
        Set<String> expectedSet = new LinkedHashSet<>(expected == null ? List.of() : expected);
        Set<String> actualSet = new LinkedHashSet<>(actual == null ? List.of() : actual);
        if (!expectedSet.equals(actualSet)) {
            issues.add("secret references are " + actualSet + " instead of " + expectedSet);
        }
    }

    private List<String> resolveEntityTypes(JsonNode entityConfig) {
        JsonNode entities = entityConfig == null ? null : entityConfig.path("entities");
        if (entities == null || !entities.isArray()) {
            return List.of();
        }
        List<String> entityTypes = new ArrayList<>();
        for (JsonNode entity : entities) {
            String name = entity.path("name").asText("").trim();
            if (StringUtils.hasText(name)) {
                entityTypes.add(name);
            }
        }
        return List.copyOf(entityTypes);
    }

    private String expectedManagedQdrantClusterName(String deploymentId,
                                                    JsonNode providerConfig) {
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

    private String expectedManagedQdrantDatabaseApiKeyName(String deploymentId) {
        String suffix = deploymentId.replace("dep-", "").replaceAll("[^A-Za-z0-9]", "-").toLowerCase(Locale.ROOT);
        String value = "ai-fabric-" + suffix;
        return value.substring(0, Math.min(value.length(), 64));
    }

    private String normalizeEndpoint(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String trimmed = value.trim();
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            trimmed = "https://" + trimmed;
        }
        return normalizeBaseUrl(trimmed);
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private DeploymentManagedVectorResourceSummary toSummary(DeploymentManagedVectorResourceEntity entity,
                                                             String driftState,
                                                             String driftMessage) {
        return new DeploymentManagedVectorResourceSummary(
            entity.getId(),
            entity.getDeploymentId(),
            entity.getDeploymentVersionId(),
            entity.getDeploymentReleaseId(),
            entity.getVendor(),
            entity.getVectorStrategy(),
            entity.getVectorProvisioningMode(),
            entity.getManagedMode(),
            entity.getResourceType(),
            entity.getResourceName(),
            entity.getResourceReference(),
            entity.getEndpoint(),
            entity.getResourceStatus(),
            entity.getProvisioningState(),
            readStringList(entity.getSecretReferenceNamesJson()),
            readJson(entity.getDetailsJson()),
            driftState,
            driftMessage,
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }

    private String writeJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node == null ? objectMapper.createObjectNode() : node);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize managed vector resource details.", ex);
        }
    }

    private String writeJsonArray(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values == null ? List.of() : values);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize managed vector secret references.", ex);
        }
    }

    private JsonNode readJson(String json) {
        try {
            return objectMapper.readTree(json == null || json.isBlank() ? "{}" : json);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to read managed vector resource details.", ex);
        }
    }

    private List<String> readStringList(String json) {
        try {
            JsonNode node = objectMapper.readTree(json == null || json.isBlank() ? "[]" : json);
            if (!node.isArray()) {
                return List.of();
            }
            List<String> values = new ArrayList<>();
            node.forEach(item -> {
                String value = item.asText(null);
                if (StringUtils.hasText(value)) {
                    values.add(value);
                }
            });
            return List.copyOf(values);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to read managed vector secret references.", ex);
        }
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null) {
            return null;
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    private String firstNonBlank(String primary, String fallback) {
        return StringUtils.hasText(primary) ? primary : fallback;
    }

    private String resourceKey(String vendor, String resourceType, String resourceName) {
        return (vendor == null ? "" : vendor.trim().toLowerCase(Locale.ROOT))
            + "|"
            + (resourceType == null ? "" : resourceType.trim().toLowerCase(Locale.ROOT))
            + "|"
            + (resourceName == null ? "" : resourceName.trim().toLowerCase(Locale.ROOT));
    }

    private boolean setIfDifferent(String currentValue,
                                   String nextValue,
                                   java.util.function.Consumer<String> setter) {
        if (Objects.equals(currentValue, nextValue)) {
            return false;
        }
        setter.accept(nextValue);
        return true;
    }

    private record ExpectedManagedVectorResource(
        String key,
        String vendor,
        String resourceType,
        String resourceName,
        String vectorProvisioningMode,
        String managedMode,
        String endpoint,
        List<String> secretReferenceNames,
        Map<String, String> details
    ) {
        String detail(String key) {
            return details.getOrDefault(key, "");
        }
    }

    private record ManagedVectorResourceDrift(
        String state,
        String message
    ) {
    }

    private record ManagedVectorDriftSummary(
        boolean detected,
        String status,
        int driftedResourceCount,
        String message,
        Map<String, ManagedVectorResourceDrift> resourceDriftById
    ) {
    }

    private record DesiredManagedVectorResource(
        String deploymentId,
        String deploymentVersionId,
        String deploymentReleaseId,
        String vendor,
        String vectorStrategy,
        String vectorProvisioningMode,
        String managedMode,
        String resourceType,
        String resourceName,
        String resourceReference,
        String endpoint,
        String provisioningState,
        List<String> secretReferenceNames,
        JsonNode details
    ) {
    }
}
