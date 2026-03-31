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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class DeploymentManagedVectorResourceService {

    static final String RESOURCE_STATUS_ACTIVE = "ACTIVE";
    static final String RESOURCE_STATUS_DETACHED = "DETACHED";

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
                                                                 String activeVersionId) {
        List<DeploymentManagedVectorResourceSummary> resources = repository.findByDeploymentIdOrderByUpdatedAtDesc(deploymentId)
            .stream()
            .map(this::toSummary)
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
            status = activeResources.isEmpty() ? "WARNING" : "READY";
            summaryMessage = activeResources.isEmpty()
                ? "Managed vector resources are requested and will be created or reconciled during the next apply."
                : activeResources.size() + " managed vector resource(s) already exist before a live version is active.";
        } else if (activeResources.isEmpty()) {
            status = "BLOCKED";
            summaryMessage = "The live deployment expects managed vector resources, but no active resource records are registered.";
        } else if (activeResources.stream().anyMatch(resource ->
            StringUtils.hasText(resource.deploymentVersionId()) && !Objects.equals(activeVersionId, resource.deploymentVersionId())
        )) {
            status = "WARNING";
            summaryMessage = "Managed vector resources exist, but at least one record is bound to a different deployment version than the current live version.";
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
            activeResources.size(),
            detachedResources.size(),
            resources,
            summaryMessage
        );
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
            case "MANAGED_INDEX" -> List.of(new DesiredManagedVectorResource(
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
                List.of("PINECONE_API_KEY"),
                details.deepCopy()
            ));
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

    private DeploymentManagedVectorResourceSummary toSummary(DeploymentManagedVectorResourceEntity entity) {
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
