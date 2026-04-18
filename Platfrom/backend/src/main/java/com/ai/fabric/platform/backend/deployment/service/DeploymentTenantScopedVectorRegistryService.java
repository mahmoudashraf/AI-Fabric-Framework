package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.entity.TenantScopedVectorResourceEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentTenantScopedVectorRegistrySummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentTenantScopedVectorSummary;
import com.ai.fabric.platform.backend.deployment.repository.TenantScopedVectorResourceRepository;
import com.ai.fabric.platform.backend.tenant.model.PlatformTenantSharedVectorHandleSummary;
import com.ai.fabric.platform.backend.tenant.model.PlatformTenantSharedVectorSummary;
import com.ai.fabric.platform.backend.tenant.model.PurgePlatformTenantSharedVectorHandlesRequest;
import com.ai.fabric.platform.backend.tenant.model.PurgePlatformTenantSharedVectorHandlesSummary;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
public class DeploymentTenantScopedVectorRegistryService {

    private static final String PURGE_CONFIRMATION_TEXT = "PURGE DETACHED HANDLES";
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

        assertNoCrossCustomerRootConflict(summary, deployment.getCustomerId(), deployment.getId());
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
                "INFO",
                "No tenant-scoped shared-resource cleanup posture is available until the deployment resolves a tenant-scoped vector summary.",
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
                "INFO",
                "Cleanup readiness applies only to shared tenant-scoped vector resources.",
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
                "BLOCKED",
                "Resolve the tenant-scoped shared-handle configuration before attempting tenant-scoped cleanup or legal-delete preparation.",
                "Shared storage is configured, but the resolved tenant-scoped handle is blocked until the underlying provider configuration is corrected."
            );
        }

        String registryKey = registryKey(summary);
        CrossCustomerRootConflict conflict = findCrossCustomerRootConflict(summary, deployment.getCustomerId(), deployment.getId());
        if (conflict != null) {
            return new DeploymentTenantScopedVectorRegistrySummary(
                "BLOCKED",
                conflict.recordId(),
                activeCount,
                historicalCount,
                latestUpdatedAt,
                "BLOCKED",
                conflict.message(),
                conflict.message()
            );
        }
        TenantScopedVectorResourceEntity matching = repository.findByTenantIdAndRegistryKeyIgnoreCase(summary.tenantId(), registryKey)
            .orElse(null);
        if (matching == null) {
            return new DeploymentTenantScopedVectorRegistrySummary(
                "WARNING",
                null,
                activeCount,
                historicalCount,
                latestUpdatedAt,
                historicalCleanupReadinessStatus(activeCount, historicalCount),
                historicalCleanupReadinessMessage(activeCount, historicalCount),
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
                activeCleanupReadinessStatus(matching, deployment),
                activeCleanupReadinessMessage(matching, deployment, activeCount),
                message
            );
        }
        return new DeploymentTenantScopedVectorRegistrySummary(
            "WARNING",
            matching.getId(),
            activeCount,
            historicalCount,
            matching.getUpdatedAt(),
            historicalCleanupReadinessStatus(activeCount, historicalCount),
            historicalCleanupReadinessMessage(activeCount, historicalCount),
            "The resolved tenant-scoped shared handle exists only as detached history. Apply the deployment to reactivate it."
        );
    }

    @Transactional(readOnly = true)
    public PlatformTenantSharedVectorSummary summarizeTenant(String tenantId) {
        if (!StringUtils.hasText(tenantId)) {
            return new PlatformTenantSharedVectorSummary(
                0,
                0,
                "INFO",
                null,
                null,
                null,
                null,
                "INFO",
                "No tenant-scoped shared-resource cleanup work is pending because no tenant is bound.",
                "No tenant is bound."
            );
        }
        return summarizeTenants(List.of(tenantId.trim())).getOrDefault(
            tenantId.trim(),
            new PlatformTenantSharedVectorSummary(
                0,
                0,
                "INFO",
                null,
                null,
                null,
                null,
                "INFO",
                "No tenant-scoped shared-resource cleanup work is pending for this tenant.",
                "No shared vector handles are registered for this tenant yet."
            )
        );
    }

    @Transactional(readOnly = true)
    public List<PlatformTenantSharedVectorHandleSummary> listTenantHandles(String tenantId) {
        if (!StringUtils.hasText(tenantId)) {
            return List.of();
        }
        return repository.findByTenantIdOrderByUpdatedAtDesc(tenantId.trim()).stream()
            .map(this::toHandleSummary)
            .toList();
    }

    @Transactional
    public PurgePlatformTenantSharedVectorHandlesSummary purgeDetachedHandleHistory(String tenantId,
                                                                                   String tenantName,
                                                                                   PurgePlatformTenantSharedVectorHandlesRequest request) {
        if (!StringUtils.hasText(tenantId)) {
            throw new ResponseStatusException(BAD_REQUEST, "tenantId is required.");
        }
        if (request == null) {
            throw new ResponseStatusException(BAD_REQUEST, "A purge request body is required.");
        }
        if (!request.providerDeleteConfirmed()) {
            throw new ResponseStatusException(BAD_REQUEST, "Provider-side delete or retention confirmation is required before purging shared-handle history.");
        }
        String confirmationText = request.confirmationText() == null ? "" : request.confirmationText().trim();
        if (!PURGE_CONFIRMATION_TEXT.equals(confirmationText)) {
            throw new ResponseStatusException(BAD_REQUEST, "Enter '" + PURGE_CONFIRMATION_TEXT + "' to purge detached shared-handle history.");
        }
        String reason = request.reason() == null ? "" : request.reason().trim();
        if (reason.length() < 10) {
            throw new ResponseStatusException(BAD_REQUEST, "A purge reason of at least 10 characters is required.");
        }

        List<TenantScopedVectorResourceEntity> tenantRecords = repository.findByTenantIdOrderByUpdatedAtDesc(tenantId.trim());
        long activeCount = tenantRecords.stream()
            .filter(record -> RESOURCE_STATUS_ACTIVE.equalsIgnoreCase(record.getResourceStatus()))
            .count();
        if (activeCount > 0) {
            throw new ResponseStatusException(BAD_REQUEST, "Detached shared-handle history can only be purged after all active shared handles are gone.");
        }

        List<TenantScopedVectorResourceEntity> detachedRecords = tenantRecords.stream()
            .filter(record -> RESOURCE_STATUS_DETACHED.equalsIgnoreCase(record.getResourceStatus()))
            .toList();
        if (detachedRecords.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "No detached shared-handle history exists for this tenant.");
        }

        Set<String> requestedIds = normalizeHandleIds(request.handleIds());
        List<TenantScopedVectorResourceEntity> targetRecords;
        if (request.purgeAllDetached()) {
            targetRecords = detachedRecords;
        } else {
            if (requestedIds.isEmpty()) {
                throw new ResponseStatusException(BAD_REQUEST, "Select at least one detached shared handle or enable purgeAllDetached.");
            }
            targetRecords = detachedRecords.stream()
                .filter(record -> requestedIds.contains(record.getId()))
                .toList();
            if (targetRecords.size() != requestedIds.size()) {
                throw new ResponseStatusException(BAD_REQUEST, "One or more selected handle ids are not detachable history for this tenant.");
            }
        }

        repository.deleteAllInBatch(targetRecords);
        int remainingHistoricalHandleCount = detachedRecords.size() - targetRecords.size();

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("tenantId", tenantId.trim());
        details.put("tenantName", StringUtils.hasText(tenantName) ? tenantName.trim() : "Unknown tenant");
        details.put("purgedCount", targetRecords.size());
        details.put("remainingHistoricalHandleCount", remainingHistoricalHandleCount);
        details.put("purgedHandleIds", targetRecords.stream().map(TenantScopedVectorResourceEntity::getId).toList());
        details.put("reason", reason);
        platformAuditService.record(
            "TENANT_SCOPED_VECTOR_HANDLE_HISTORY_PURGED",
            "PLATFORM_TENANT",
            tenantId.trim(),
            details
        );

        String message = remainingHistoricalHandleCount > 0
            ? "Purged " + targetRecords.size() + " detached shared handle(s). " + remainingHistoricalHandleCount + " detached record(s) remain for this tenant."
            : "Purged all detached shared-handle history for this tenant.";
        return new PurgePlatformTenantSharedVectorHandlesSummary(
            tenantId.trim(),
            StringUtils.hasText(tenantName) ? tenantName.trim() : "Unknown tenant",
            targetRecords.size(),
            remainingHistoricalHandleCount,
            "PURGED",
            message
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
                "INFO",
                "No tenant-scoped shared-resource cleanup work is pending for this tenant.",
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
                "INFO",
                "No tenant-scoped shared-resource cleanup work is pending for this tenant.",
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
            tenantCleanupReadinessStatus(activeCount, historicalCount),
            tenantCleanupReadinessMessage(activeCount, historicalCount),
            latestSummary
        );
    }

    private PlatformTenantSharedVectorHandleSummary toHandleSummary(TenantScopedVectorResourceEntity entity) {
        JsonNode details = readDetails(entity.getDetailsJson());
        return new PlatformTenantSharedVectorHandleSummary(
            entity.getId(),
            entity.getResourceStatus(),
            entity.getVendor(),
            entity.getVectorStrategy(),
            entity.getVectorProvisioningMode(),
            entity.getVectorStoragePosture(),
            entity.getScopeType(),
            entity.getRootResourceLabel(),
            entity.getRootResourceValue(),
            entity.getScopePrefix(),
            entity.getTenantHandle(),
            entity.getScopePattern(),
            entity.getLifecycleOwner(),
            entity.getDeploymentId(),
            entity.getDeploymentVersionId(),
            entity.getDeploymentReleaseId(),
            readDetail(details, "summaryStatus"),
            readDetail(details, "summaryMessage"),
            entity.getCreatedAt(),
            entity.getUpdatedAt(),
            RESOURCE_STATUS_DETACHED.equalsIgnoreCase(entity.getResourceStatus())
        );
    }

    private String activeCleanupReadinessStatus(TenantScopedVectorResourceEntity matching,
                                                DeploymentEntity deployment) {
        if (matching.getDeploymentId() != null && matching.getDeploymentId().equals(deployment.getId())) {
            return "BLOCKED";
        }
        return "WARNING";
    }

    private String activeCleanupReadinessMessage(TenantScopedVectorResourceEntity matching,
                                                 DeploymentEntity deployment,
                                                 int activeCount) {
        if (matching.getDeploymentId() != null && matching.getDeploymentId().equals(deployment.getId())) {
            return activeCount > 1
                ? "Tenant-scoped cleanup is blocked while this deployment and other active shared handles still reference the tenant scope."
                : "Tenant-scoped cleanup is blocked while this deployment still owns the active shared handle.";
        }
        return "Tenant-scoped cleanup is blocked because another deployment still owns the active shared handle for this tenant.";
    }

    private String historicalCleanupReadinessStatus(int activeCount, int historicalCount) {
        if (activeCount > 0) {
            return "BLOCKED";
        }
        if (historicalCount > 0) {
            return "READY";
        }
        return "INFO";
    }

    private String historicalCleanupReadinessMessage(int activeCount, int historicalCount) {
        if (activeCount > 0) {
            return "Tenant-scoped cleanup is blocked while active shared handles still exist.";
        }
        if (historicalCount > 0) {
            return "Only detached historical shared handles remain. Tenant-scoped cleanup can proceed with provider-side delete confirmation.";
        }
        return "No tenant-scoped shared-handle history exists yet for cleanup planning.";
    }

    private String tenantCleanupReadinessStatus(int activeCount, int historicalCount) {
        if (activeCount > 0) {
            return "BLOCKED";
        }
        if (historicalCount > 0) {
            return "READY";
        }
        return "INFO";
    }

    private String tenantCleanupReadinessMessage(int activeCount, int historicalCount) {
        if (activeCount > 0) {
            return "Active shared handles still exist for this tenant. Tenant-scoped cleanup or legal delete is not ready.";
        }
        if (historicalCount > 0) {
            return "Only detached historical shared handles remain. Tenant-scoped cleanup can proceed with provider-side delete confirmation.";
        }
        return "No shared-handle cleanup work is pending for this tenant.";
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

    private JsonNode readDetails(String detailsJson) {
        if (!StringUtils.hasText(detailsJson)) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(detailsJson);
        } catch (Exception ex) {
            return objectMapper.createObjectNode();
        }
    }

    private String readDetail(JsonNode details, String key) {
        if (details == null) {
            return null;
        }
        String value = details.path(key).asText("");
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private Set<String> normalizeHandleIds(List<String> handleIds) {
        if (handleIds == null || handleIds.isEmpty()) {
            return Set.of();
        }
        Set<String> ids = new LinkedHashSet<>();
        for (String handleId : handleIds) {
            if (StringUtils.hasText(handleId)) {
                ids.add(handleId.trim());
            }
        }
        return ids;
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

    private void assertNoCrossCustomerRootConflict(DeploymentTenantScopedVectorSummary summary,
                                                   String deploymentCustomerId,
                                                   String deploymentId) {
        CrossCustomerRootConflict conflict = findCrossCustomerRootConflict(summary, deploymentCustomerId, deploymentId);
        if (conflict != null) {
            throw new IllegalStateException(conflict.message());
        }
    }

    private CrossCustomerRootConflict findCrossCustomerRootConflict(DeploymentTenantScopedVectorSummary summary,
                                                                    String deploymentCustomerId,
                                                                    String deploymentId) {
        if (summary == null
            || !summary.sharedStorage()
            || !"READY".equalsIgnoreCase(summary.status())
            || !requiresCustomerBoundaryIsolation(summary)
            || !StringUtils.hasText(summary.rootResourceValue())) {
            return null;
        }
        return repository
            .findByVectorStrategyIgnoreCaseAndRootResourceValueIgnoreCaseAndResourceStatusIgnoreCase(
                summary.vectorStrategy(),
                summary.rootResourceValue(),
                RESOURCE_STATUS_ACTIVE
            )
            .stream()
            .filter(record -> !normalize(record.getCustomerId()).equals(normalize(deploymentCustomerId)))
            .filter(record -> !normalize(record.getDeploymentId()).equals(normalize(deploymentId)))
            .findFirst()
            .map(record -> new CrossCustomerRootConflict(
                record.getId(),
                "Shared " + blankToFallback(summary.rootResourceLabel(), "root resource").toLowerCase(Locale.ROOT)
                    + " '" + summary.rootResourceValue()
                    + "' is already active for customer " + blankToFallback(record.getCustomerId(), "unknown")
                    + ". Shared vector infrastructure must not cross customer boundaries."
            ))
            .orElse(null);
    }

    private boolean requiresCustomerBoundaryIsolation(DeploymentTenantScopedVectorSummary summary) {
        return "customer_managed_external_resource".equals(normalize(summary.lifecycleOwner()));
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

    private String blankToFallback(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
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

    private record CrossCustomerRootConflict(String recordId, String message) {
    }
}
