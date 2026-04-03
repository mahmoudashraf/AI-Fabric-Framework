package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.entity.TenantScopedVectorResourceEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentTenantScopedVectorRegistrySummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentTenantScopedVectorSummary;
import com.ai.fabric.platform.backend.deployment.repository.TenantScopedVectorResourceRepository;
import com.ai.fabric.platform.backend.tenant.model.PlatformTenantSharedVectorSummary;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class DeploymentTenantScopedVectorRegistryService {

    static final String RESOURCE_STATUS_ACTIVE = "ACTIVE";
    static final String RESOURCE_STATUS_DETACHED = "DETACHED";

    private final TenantScopedVectorResourceRepository repository;
    private final PlatformAuditService platformAuditService;
    private final ObjectMapper objectMapper;

    public DeploymentTenantScopedVectorRegistryService(TenantScopedVectorResourceRepository repository,
                                                       PlatformAuditService platformAuditService,
                                                       ObjectMapper objectMapper) {
        this.repository = repository;
        this.platformAuditService = platformAuditService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void syncResolvedHandle(DeploymentEntity deployment,
                                   DeploymentVersionEntity version,
                                   DeploymentReleaseEntity release,
                                   DeploymentTenantScopedVectorSummary summary) {
        List<TenantScopedVectorResourceEntity> deploymentRecords = repository.findByDeploymentIdOrderByUpdatedAtDesc(deployment.getId());
        Instant now = Instant.now();

        if (summary == null || !summary.sharedStorage() || !"READY".equalsIgnoreCase(summary.status())) {
            int detached = detachActiveDeploymentRecords(
                deploymentRecords,
                version == null ? null : version.getId(),
                release == null ? null : release.getId(),
                now
            );
            if (detached > 0) {
                repository.saveAll(deploymentRecords);
                platformAuditService.record(
                    "TENANT_SCOPED_VECTOR_HANDLE_DETACHED",
                    "DEPLOYMENT",
                    deployment.getId(),
                    Map.of(
                        "deploymentId", deployment.getId(),
                        "detachedCount", detached,
                        "reason", "Current deployment state no longer resolves to an active shared tenant-scoped handle."
                    )
                );
            }
            return;
        }

        String registryKey = registryKey(summary);
        TenantScopedVectorResourceEntity entity = repository.findByTenantIdAndRegistryKeyIgnoreCase(summary.tenantId(), registryKey)
            .orElse(null);
        boolean created = false;
        boolean reactivated = false;
        if (entity == null) {
            entity = new TenantScopedVectorResourceEntity();
            entity.setId("tsv-" + UUID.randomUUID().toString().substring(0, 8));
            entity.setCreatedAt(now);
            created = true;
        } else if (RESOURCE_STATUS_DETACHED.equalsIgnoreCase(entity.getResourceStatus())) {
            reactivated = true;
        }

        boolean changed = applySummary(entity, deployment, version, release, summary, registryKey, now);
        List<TenantScopedVectorResourceEntity> dirty = new ArrayList<>();
        if (created || reactivated || changed) {
            dirty.add(entity);
        }

        int detached = 0;
        for (TenantScopedVectorResourceEntity existing : deploymentRecords) {
            if (entity.getId().equals(existing.getId())) {
                continue;
            }
            if (!RESOURCE_STATUS_ACTIVE.equalsIgnoreCase(existing.getResourceStatus())) {
                continue;
            }
            existing.setResourceStatus(RESOURCE_STATUS_DETACHED);
            existing.setDeploymentVersionId(version == null ? null : version.getId());
            existing.setDeploymentReleaseId(release == null ? null : release.getId());
            existing.setUpdatedAt(now);
            dirty.add(existing);
            detached += 1;
        }

        if (!dirty.isEmpty()) {
            repository.saveAll(dirty);
            platformAuditService.record(
                "TENANT_SCOPED_VECTOR_HANDLE_SYNCED",
                "DEPLOYMENT",
                deployment.getId(),
                Map.of(
                    "deploymentId", deployment.getId(),
                    "tenantId", summary.tenantId(),
                    "vectorStrategy", summary.vectorStrategy(),
                    "registryKey", registryKey,
                    "created", created,
                    "reactivated", reactivated,
                    "detachedCount", detached
                )
            );
        }
    }

    @Transactional
    public void detachForDeletedDeployment(DeploymentEntity deployment, String reason) {
        List<TenantScopedVectorResourceEntity> deploymentRecords = repository.findByDeploymentIdOrderByUpdatedAtDesc(deployment.getId());
        if (deploymentRecords.isEmpty()) {
            return;
        }
        Instant now = Instant.now();
        int detached = detachActiveDeploymentRecords(deploymentRecords, null, null, now);
        if (detached == 0) {
            return;
        }
        repository.saveAll(deploymentRecords);
        platformAuditService.record(
            "TENANT_SCOPED_VECTOR_HANDLE_DETACHED",
            "DEPLOYMENT",
            deployment.getId(),
            Map.of(
                "deploymentId", deployment.getId(),
                "tenantId", deployment.getTenantId(),
                "detachedCount", detached,
                "reason", StringUtils.hasText(reason) ? reason.trim() : "Deployment deleted."
            )
        );
    }

    @Transactional(readOnly = true)
    public DeploymentTenantScopedVectorRegistrySummary summarizeForDeployment(DeploymentEntity deployment,
                                                                             DeploymentTenantScopedVectorSummary summary) {
        String tenantId = summary == null ? deployment.getTenantId() : summary.tenantId();
        List<TenantScopedVectorResourceEntity> tenantRecords = StringUtils.hasText(tenantId)
            ? repository.findByTenantIdOrderByUpdatedAtDesc(tenantId)
            : List.of();
        int activeCount = (int) tenantRecords.stream()
            .filter(record -> RESOURCE_STATUS_ACTIVE.equalsIgnoreCase(record.getResourceStatus()))
            .count();
        int historicalCount = (int) tenantRecords.stream()
            .filter(record -> RESOURCE_STATUS_DETACHED.equalsIgnoreCase(record.getResourceStatus()))
            .count();
        Instant latestUpdatedAt = tenantRecords.stream()
            .map(TenantScopedVectorResourceEntity::getUpdatedAt)
            .filter(java.util.Objects::nonNull)
            .max(Instant::compareTo)
            .orElse(null);

        if (summary == null) {
            return new DeploymentTenantScopedVectorRegistrySummary(
                "INFO",
                null,
                activeCount,
                historicalCount,
                latestUpdatedAt,
                "No tenant-scoped vector summary is available for this deployment yet."
            );
        }
        if (!summary.sharedStorage()) {
            String message = historicalCount > 0
                ? "This deployment is not using shared tenant-scoped storage. Historical shared-handle records remain visible for audit."
                : "This deployment is not using shared tenant-scoped storage.";
            return new DeploymentTenantScopedVectorRegistrySummary(
                "INFO",
                null,
                activeCount,
                historicalCount,
                latestUpdatedAt,
                message
            );
        }
        if (!"READY".equalsIgnoreCase(summary.status())) {
            return new DeploymentTenantScopedVectorRegistrySummary(
                "BLOCKED",
                null,
                activeCount,
                historicalCount,
                latestUpdatedAt,
                "Shared storage is configured, but the resolved tenant-scoped handle is blocked until the underlying provider configuration is corrected."
            );
        }

        String registryKey = registryKey(summary);
        TenantScopedVectorResourceEntity matching = repository.findByTenantIdAndRegistryKeyIgnoreCase(summary.tenantId(), registryKey)
            .orElse(null);
        if (matching == null) {
            return new DeploymentTenantScopedVectorRegistrySummary(
                "WARNING",
                null,
                activeCount,
                historicalCount,
                latestUpdatedAt,
                "The tenant-scoped shared handle resolves cleanly, but no registry record exists yet. Apply the deployment to register or reconcile it."
            );
        }
        if (RESOURCE_STATUS_ACTIVE.equalsIgnoreCase(matching.getResourceStatus())) {
            String message = matching.getDeploymentId() != null && !matching.getDeploymentId().equals(deployment.getId())
                ? "The resolved tenant-scoped handle is active, but it is currently attached to a different deployment record. Reconcile the tenant binding before rollout."
                : "The resolved tenant-scoped shared handle is registered and aligned for this deployment.";
            String status = matching.getDeploymentId() != null && !matching.getDeploymentId().equals(deployment.getId()) ? "WARNING" : "READY";
            return new DeploymentTenantScopedVectorRegistrySummary(
                status,
                matching.getId(),
                activeCount,
                historicalCount,
                matching.getUpdatedAt(),
                message
            );
        }
        return new DeploymentTenantScopedVectorRegistrySummary(
            "WARNING",
            matching.getId(),
            activeCount,
            historicalCount,
            matching.getUpdatedAt(),
            "The resolved tenant-scoped shared handle exists only as detached history. Apply the deployment to reactivate it."
        );
    }

    @Transactional(readOnly = true)
    public PlatformTenantSharedVectorSummary summarizeTenant(String tenantId) {
        if (!StringUtils.hasText(tenantId)) {
            return new PlatformTenantSharedVectorSummary(0, 0, "INFO", null, null, null, null, "No tenant is bound.");
        }
        return summarizeTenants(List.of(tenantId.trim())).getOrDefault(
            tenantId.trim(),
            new PlatformTenantSharedVectorSummary(0, 0, "INFO", null, null, null, null, "No shared vector handles are registered for this tenant yet.")
        );
    }

    @Transactional(readOnly = true)
    public Map<String, PlatformTenantSharedVectorSummary> summarizeTenants(Collection<String> tenantIds) {
        if (tenantIds == null || tenantIds.isEmpty()) {
            return Map.of();
        }
        List<String> normalizedTenantIds = tenantIds.stream()
            .filter(StringUtils::hasText)
            .map(String::trim)
            .distinct()
            .toList();
        if (normalizedTenantIds.isEmpty()) {
            return Map.of();
        }
        Map<String, List<TenantScopedVectorResourceEntity>> recordsByTenant = new LinkedHashMap<>();
        for (String tenantId : normalizedTenantIds) {
            recordsByTenant.put(tenantId, new ArrayList<>());
        }
        repository.findByTenantIdInOrderByUpdatedAtDesc(normalizedTenantIds).forEach(record ->
            recordsByTenant.computeIfAbsent(record.getTenantId(), ignored -> new ArrayList<>()).add(record)
        );

        Map<String, PlatformTenantSharedVectorSummary> summaries = new LinkedHashMap<>();
        for (String tenantId : normalizedTenantIds) {
            summaries.put(tenantId, summarizeTenantRecords(recordsByTenant.getOrDefault(tenantId, List.of())));
        }
        return summaries;
    }

    private PlatformTenantSharedVectorSummary summarizeTenantRecords(List<TenantScopedVectorResourceEntity> records) {
        if (records == null || records.isEmpty()) {
            return new PlatformTenantSharedVectorSummary(
                0,
                0,
                "INFO",
                null,
                null,
                null,
                null,
                "No shared vector handles are registered for this tenant yet."
            );
        }
        int activeCount = (int) records.stream()
            .filter(record -> RESOURCE_STATUS_ACTIVE.equalsIgnoreCase(record.getResourceStatus()))
            .count();
        int historicalCount = (int) records.stream()
            .filter(record -> RESOURCE_STATUS_DETACHED.equalsIgnoreCase(record.getResourceStatus()))
            .count();
        TenantScopedVectorResourceEntity latest = records.stream().findFirst().orElse(null);
        if (latest == null) {
            return new PlatformTenantSharedVectorSummary(
                0,
                0,
                "INFO",
                null,
                null,
                null,
                null,
                "No shared vector handles are registered for this tenant yet."
            );
        }
        String latestSummary = activeCount > 0
            ? activeCount + " active shared vector handle(s) registered for this tenant."
            : historicalCount + " detached historical shared vector handle(s) retained for audit.";
        return new PlatformTenantSharedVectorSummary(
            activeCount,
            historicalCount,
            latest.getResourceStatus(),
            latest.getVectorStrategy(),
            latest.getScopeType(),
            latest.getScopePattern(),
            latest.getUpdatedAt(),
            latestSummary
        );
    }

    private int detachActiveDeploymentRecords(List<TenantScopedVectorResourceEntity> deploymentRecords,
                                              String deploymentVersionId,
                                              String deploymentReleaseId,
                                              Instant now) {
        int detached = 0;
        for (TenantScopedVectorResourceEntity existing : deploymentRecords) {
            if (!RESOURCE_STATUS_ACTIVE.equalsIgnoreCase(existing.getResourceStatus())) {
                continue;
            }
            existing.setResourceStatus(RESOURCE_STATUS_DETACHED);
            existing.setDeploymentVersionId(deploymentVersionId);
            existing.setDeploymentReleaseId(deploymentReleaseId);
            existing.setUpdatedAt(now);
            detached += 1;
        }
        return detached;
    }

    private boolean applySummary(TenantScopedVectorResourceEntity entity,
                                 DeploymentEntity deployment,
                                 DeploymentVersionEntity version,
                                 DeploymentReleaseEntity release,
                                 DeploymentTenantScopedVectorSummary summary,
                                 String registryKey,
                                 Instant now) {
        boolean changed = false;
        changed |= setIfDifferent(entity.getCustomerId(), summary.customerId(), entity::setCustomerId);
        changed |= setIfDifferent(entity.getTenantId(), summary.tenantId(), entity::setTenantId);
        changed |= setIfDifferent(entity.getDeploymentId(), deployment.getId(), entity::setDeploymentId);
        changed |= setIfDifferent(entity.getDeploymentVersionId(), version == null ? null : version.getId(), entity::setDeploymentVersionId);
        changed |= setIfDifferent(entity.getDeploymentReleaseId(), release == null ? null : release.getId(), entity::setDeploymentReleaseId);
        changed |= setIfDifferent(entity.getVendor(), vendorFor(summary.vectorStrategy()), entity::setVendor);
        changed |= setIfDifferent(entity.getVectorStrategy(), summary.vectorStrategy(), entity::setVectorStrategy);
        changed |= setIfDifferent(entity.getVectorProvisioningMode(), summary.vectorProvisioningMode(), entity::setVectorProvisioningMode);
        changed |= setIfDifferent(entity.getVectorStoragePosture(), summary.vectorStoragePosture(), entity::setVectorStoragePosture);
        changed |= setIfDifferent(entity.getResourceStatus(), RESOURCE_STATUS_ACTIVE, entity::setResourceStatus);
        changed |= setIfDifferent(entity.getScopeType(), summary.scopeType(), entity::setScopeType);
        changed |= setIfDifferent(entity.getRegistryKey(), registryKey, entity::setRegistryKey);
        changed |= setIfDifferent(entity.getRootResourceLabel(), summary.rootResourceLabel(), entity::setRootResourceLabel);
        changed |= setIfDifferent(entity.getRootResourceValue(), summary.rootResourceValue(), entity::setRootResourceValue);
        changed |= setIfDifferent(entity.getScopePrefix(), summary.scopePrefix(), entity::setScopePrefix);
        changed |= setIfDifferent(entity.getTenantHandle(), summary.tenantHandle(), entity::setTenantHandle);
        changed |= setIfDifferent(entity.getScopePattern(), summary.scopePattern(), entity::setScopePattern);
        changed |= setIfDifferent(entity.getLifecycleOwner(), summary.lifecycleOwner(), entity::setLifecycleOwner);
        changed |= setIfDifferent(entity.getDetailsJson(), detailsJson(summary), entity::setDetailsJson);
        if (changed || entity.getUpdatedAt() == null) {
            entity.setUpdatedAt(now);
        }
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(now);
        }
        return changed;
    }

    private String detailsJson(DeploymentTenantScopedVectorSummary summary) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("summaryStatus", summary.status());
        node.put("summaryMessage", summary.summaryMessage());
        node.put("migrationLocked", summary.migrationLocked());
        node.put("migrationMessage", summary.migrationMessage());
        node.put("backupRestorePosture", summary.backupRestorePosture());
        node.put("customerName", summary.customerName());
        node.put("tenantName", summary.tenantName());
        return node.toString();
    }

    private String registryKey(DeploymentTenantScopedVectorSummary summary) {
        return String.join("|",
            normalize(summary.vectorStrategy()),
            normalize(summary.scopeType()),
            normalize(summary.rootResourceValue()),
            normalize(summary.scopePrefix()),
            normalize(summary.tenantHandle())
        );
    }

    private String vendorFor(String vectorStrategy) {
        return switch (normalize(vectorStrategy)) {
            case ManagedDeploymentProfileCatalog.VECTOR_STRATEGY_MILVUS -> "zilliz";
            case ManagedDeploymentProfileCatalog.VECTOR_STRATEGY_PINECONE -> "pinecone";
            case ManagedDeploymentProfileCatalog.VECTOR_STRATEGY_QDRANT -> "qdrant";
            case ManagedDeploymentProfileCatalog.VECTOR_STRATEGY_WEAVIATE -> "weaviate";
            default -> normalize(vectorStrategy);
        };
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private boolean setIfDifferent(String current, String value, java.util.function.Consumer<String> setter) {
        String normalizedCurrent = current == null ? null : current;
        String normalizedValue = value == null ? null : value;
        if (!java.util.Objects.equals(normalizedCurrent, normalizedValue)) {
            setter.accept(normalizedValue);
            return true;
        }
        return false;
    }
}
