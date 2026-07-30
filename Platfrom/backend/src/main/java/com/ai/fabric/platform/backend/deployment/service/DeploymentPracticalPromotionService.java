package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentProviderResourceHandleEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentTargetProfileEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentPracticalPromotionActivationRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentPracticalPromotionRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentPracticalPromotionRollbackRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentPracticalPromotionSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderResourceLifecycleSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderResourceOrphanScanSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentReleaseSummary;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentProviderResourceHandleRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.tenant.entity.PlatformConsumerEntity;
import com.ai.fabric.platform.backend.tenant.model.PlatformConsumerSummary;
import com.ai.fabric.platform.backend.tenant.repository.PlatformConsumerRepository;
import com.ai.fabric.platform.backend.tenant.service.PlatformCustomerConsumerService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class DeploymentPracticalPromotionService {

    static final String DEFAULT_CUSTOMER_STAGING_TARGET_PROFILE_ID = "dtp-coolify-prod-staging";
    static final String DEFAULT_PRODUCTION_TARGET_PROFILE_ID = "dtp-coolify-production";

    private static final String APPLIED_VERIFIED = "APPLIED_VERIFIED";
    private static final String PASSED = "PASSED";
    private static final Set<String> RESOURCE_LIFECYCLE_STATUSES = Set.of(
        "ACTIVE",
        "SUPERSEDED",
        "ROLLBACK_RESERVED",
        "FAILED_DIAGNOSTIC_HOLD",
        "ORPHANED",
        "RETIRED",
        "DELETED"
    );
    private static final Set<String> PROTECTED_RELEASE_STATUSES = Set.of(
        "APPLY_REQUESTED",
        "PRE_APPLY_VERIFYING",
        "PROVISIONING",
        "VERIFYING",
        APPLIED_VERIFIED
    );
    private static final Set<String> PROTECTED_RESOURCE_STATUSES = Set.of(
        "SUPERSEDED",
        "ROLLBACK_RESERVED",
        "FAILED_DIAGNOSTIC_HOLD",
        "RETIRED",
        "DELETED",
        "DELETE_REQUESTED"
    );

    private final DeploymentRepository deploymentRepository;
    private final DeploymentVersionRepository deploymentVersionRepository;
    private final DeploymentReleaseRepository deploymentReleaseRepository;
    private final DeploymentTargetProfileRepository deploymentTargetProfileRepository;
    private final DeploymentProviderResourceHandleRepository resourceHandleRepository;
    private final PlatformConsumerRepository platformConsumerRepository;
    private final DeploymentAccessService deploymentAccessService;
    private final DeploymentService deploymentService;
    private final PlatformCustomerConsumerService platformCustomerConsumerService;
    private final PlatformAuditService platformAuditService;
    private final ObjectMapper objectMapper;

    public DeploymentPracticalPromotionService(DeploymentRepository deploymentRepository,
                                               DeploymentVersionRepository deploymentVersionRepository,
                                               DeploymentReleaseRepository deploymentReleaseRepository,
                                               DeploymentTargetProfileRepository deploymentTargetProfileRepository,
                                               DeploymentProviderResourceHandleRepository resourceHandleRepository,
                                               PlatformConsumerRepository platformConsumerRepository,
                                               DeploymentAccessService deploymentAccessService,
                                               DeploymentService deploymentService,
                                               PlatformCustomerConsumerService platformCustomerConsumerService,
                                               PlatformAuditService platformAuditService,
                                               ObjectMapper objectMapper) {
        this.deploymentRepository = deploymentRepository;
        this.deploymentVersionRepository = deploymentVersionRepository;
        this.deploymentReleaseRepository = deploymentReleaseRepository;
        this.deploymentTargetProfileRepository = deploymentTargetProfileRepository;
        this.resourceHandleRepository = resourceHandleRepository;
        this.platformConsumerRepository = platformConsumerRepository;
        this.deploymentAccessService = deploymentAccessService;
        this.deploymentService = deploymentService;
        this.platformCustomerConsumerService = platformCustomerConsumerService;
        this.platformAuditService = platformAuditService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public DeploymentPracticalPromotionSummary planPromotion(String deploymentId,
                                                             DeploymentPracticalPromotionRequest request) {
        PromotionContext context = requirePromotionContext(deploymentId, request);
        return summary(
            "READY",
            "Verified customer staging release is ready for production promotion.",
            context,
            null,
            null,
            List.of()
        );
    }

    @Transactional
    public DeploymentPracticalPromotionSummary requestProductionPromotion(String deploymentId,
                                                                          DeploymentPracticalPromotionRequest request) {
        PromotionContext context = requirePromotionContext(deploymentId, request);
        String sourceArtifactId = normalizeOptional(request == null ? null : request.sourceArtifactId());
        if (sourceArtifactId == null) {
            sourceArtifactId = normalizeOptional(context.stagingRelease().getSourceArtifactId());
        }
        DeploymentReleaseSummary productionRelease = deploymentService.applyVersionForTrustedCaller(
            context.deployment().getId(),
            context.version().getId(),
            context.productionTargetProfileId(),
            sourceArtifactId
        );
        platformAuditService.record(
            "DEPLOYMENT_PRACTICAL_PRODUCTION_PROMOTION_REQUESTED",
            "DEPLOYMENT",
            context.deployment().getId(),
            auditDetails(
                "versionId", context.version().getId(),
                "stagingReleaseId", context.stagingRelease().getId(),
                "productionReleaseId", productionRelease.id(),
                "stagingTargetProfileId", context.stagingTargetProfileId(),
                "productionTargetProfileId", context.productionTargetProfileId()
            )
        );
        return summary(
            "PRODUCTION_APPLY_REQUESTED",
            "Production apply was queued. Activate the production consumer only after verification passes.",
            context,
            productionRelease.id(),
            productionRelease.status(),
            List.of()
        );
    }

    @Transactional
    public DeploymentPracticalPromotionSummary activateProductionConsumer(String deploymentId,
                                                                          DeploymentPracticalPromotionActivationRequest request) {
        DeploymentEntity deployment = requireOperatorDeployment(deploymentId);
        String productionReleaseId = requireText(request == null ? null : request.productionReleaseId(), "productionReleaseId");
        String consumerId = requireText(request == null ? null : request.consumerId(), "consumerId");
        DeploymentReleaseEntity productionRelease = requireVerifiedReleaseForDeployment(deployment.getId(), productionReleaseId);
        if (!DEFAULT_PRODUCTION_TARGET_PROFILE_ID.equals(productionRelease.getTargetProfileId())) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Production activation requires release target profile " + DEFAULT_PRODUCTION_TARGET_PROFILE_ID + "."
            );
        }

        PlatformConsumerEntity consumer = requireCustomerConsumer(deployment.getCustomerId(), consumerId);
        String previousDeploymentId = consumer.getBoundDeploymentId();
        String previousReleaseId = consumer.getBoundReleaseId();
        PlatformConsumerSummary updatedConsumer = platformCustomerConsumerService.updateBindingForTrustedPromotion(
            deployment.getCustomerId(),
            consumerId,
            deployment.getId(),
            productionRelease.getId(),
            productionRelease.getTargetProfileId(),
            reason(request == null ? null : request.reason(), "production_promotion_activation")
        );

        List<DeploymentProviderResourceLifecycleSummary> lifecycleChanges = new ArrayList<>();
        lifecycleChanges.addAll(markHandlesByRelease(productionRelease.getId(), "ACTIVE", "Production consumer activated."));
        if (StringUtils.hasText(previousReleaseId) && !productionRelease.getId().equals(previousReleaseId)) {
            lifecycleChanges.addAll(markHandlesByRelease(previousReleaseId, "ROLLBACK_RESERVED", "Previous production release kept for rollback."));
        } else if (StringUtils.hasText(previousDeploymentId) && !deployment.getId().equals(previousDeploymentId)) {
            lifecycleChanges.addAll(markHandlesForDeployment(previousDeploymentId, "ROLLBACK_RESERVED", "Previous deployment kept for rollback."));
        }
        if (request != null && request.markStagingSuperseded()) {
            lifecycleChanges.addAll(markHandlesForDeploymentAndTarget(
                deployment.getId(),
                DEFAULT_CUSTOMER_STAGING_TARGET_PROFILE_ID,
                "SUPERSEDED",
                "Customer staging release superseded by production activation."
            ));
        }

        platformAuditService.record(
            "DEPLOYMENT_PRACTICAL_PRODUCTION_CONSUMER_ACTIVATED",
            "PLATFORM_CONSUMER",
            updatedConsumer.consumerId(),
            auditDetails(
                "deploymentId", deployment.getId(),
                "productionReleaseId", productionRelease.getId(),
                "previousDeploymentId", previousDeploymentId,
                "previousReleaseId", previousReleaseId,
                "resourceLifecycleChanges", lifecycleChanges.size()
            )
        );
        return new DeploymentPracticalPromotionSummary(
            "PRODUCTION_CONSUMER_ACTIVATED",
            "Production consumer now resolves the verified production release.",
            deployment.getId(),
            productionRelease.getDeploymentVersionId(),
            DEFAULT_CUSTOMER_STAGING_TARGET_PROFILE_ID,
            productionRelease.getTargetProfileId(),
            null,
            null,
            productionRelease.getId(),
            productionRelease.getStatus(),
            updatedConsumer,
            lifecycleChanges
        );
    }

    @Transactional
    public DeploymentPracticalPromotionSummary rollbackProductionConsumer(String deploymentId,
                                                                          DeploymentPracticalPromotionRollbackRequest request) {
        DeploymentEntity deployment = requireOperatorDeployment(deploymentId);
        String consumerId = requireText(request == null ? null : request.consumerId(), "consumerId");
        String rollbackDeploymentId = requireText(request == null ? null : request.rollbackDeploymentId(), "rollbackDeploymentId");
        String rollbackReleaseId = requireText(request == null ? null : request.rollbackReleaseId(), "rollbackReleaseId");
        DeploymentEntity rollbackDeployment = requireOperatorDeployment(rollbackDeploymentId);
        if (!deployment.getCustomerId().equals(rollbackDeployment.getCustomerId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rollback deployment belongs to a different customer.");
        }
        DeploymentReleaseEntity rollbackRelease = requireVerifiedReleaseForDeployment(rollbackDeployment.getId(), rollbackReleaseId);
        String requestedTargetProfileId = normalizeOptional(request == null ? null : request.rollbackTargetProfileId());
        if (requestedTargetProfileId != null && !requestedTargetProfileId.equals(rollbackRelease.getTargetProfileId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rollback release target profile does not match request.");
        }

        PlatformConsumerEntity consumer = requireCustomerConsumer(deployment.getCustomerId(), consumerId);
        String currentReleaseId = consumer.getBoundReleaseId();
        PlatformConsumerSummary updatedConsumer = platformCustomerConsumerService.updateBindingForTrustedPromotion(
            rollbackDeployment.getCustomerId(),
            consumerId,
            rollbackDeployment.getId(),
            rollbackRelease.getId(),
            rollbackRelease.getTargetProfileId(),
            reason(request == null ? null : request.reason(), "production_assignment_rollback")
        );
        List<DeploymentProviderResourceLifecycleSummary> lifecycleChanges = new ArrayList<>();
        lifecycleChanges.addAll(markHandlesByRelease(rollbackRelease.getId(), "ACTIVE", "Rollback release reactivated."));
        if (StringUtils.hasText(currentReleaseId) && !rollbackRelease.getId().equals(currentReleaseId)) {
            lifecycleChanges.addAll(markHandlesByRelease(currentReleaseId, "ROLLBACK_RESERVED", "Rolled-back release retained for diagnostics."));
        }

        platformAuditService.record(
            "DEPLOYMENT_PRACTICAL_PRODUCTION_CONSUMER_ROLLED_BACK",
            "PLATFORM_CONSUMER",
            updatedConsumer.consumerId(),
            auditDetails(
                "fromDeploymentId", deployment.getId(),
                "toDeploymentId", rollbackDeployment.getId(),
                "toReleaseId", rollbackRelease.getId(),
                "resourceLifecycleChanges", lifecycleChanges.size()
            )
        );
        return new DeploymentPracticalPromotionSummary(
            "PRODUCTION_CONSUMER_ROLLED_BACK",
            "Production consumer now resolves the verified rollback release.",
            rollbackDeployment.getId(),
            rollbackRelease.getDeploymentVersionId(),
            DEFAULT_CUSTOMER_STAGING_TARGET_PROFILE_ID,
            rollbackRelease.getTargetProfileId(),
            null,
            null,
            rollbackRelease.getId(),
            rollbackRelease.getStatus(),
            updatedConsumer,
            lifecycleChanges
        );
    }

    @Transactional
    public DeploymentProviderResourceOrphanScanSummary scanResourceOrphans(String deploymentId, boolean mark) {
        DeploymentEntity deployment = requireOperatorDeployment(deploymentId);
        List<DeploymentProviderResourceHandleEntity> handles = resourceHandleRepository.findByDeploymentIdOrderByUpdatedAtDesc(deployment.getId());
        Map<String, DeploymentReleaseEntity> releasesById = releasesById(handles);
        Set<String> boundReleaseIds = platformConsumerRepository.findByBoundDeploymentIdOrderByUpdatedAtDesc(deployment.getId())
            .stream()
            .map(PlatformConsumerEntity::getBoundReleaseId)
            .filter(StringUtils::hasText)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        List<DeploymentProviderResourceLifecycleSummary> candidates = new ArrayList<>();
        for (DeploymentProviderResourceHandleEntity handle : handles) {
            if (!isOrphanCandidate(handle, releasesById.get(handle.getReleaseId()), boundReleaseIds)) {
                continue;
            }
            candidates.add(mark
                ? markHandle(handle, "ORPHANED", "Provider resource is not assigned to a consumer or protected release.")
                : lifecycleSummary(handle, handle.getStatus(), handle.getStatus(), "MARK_ORPHANED", "Provider resource is not assigned to a consumer or protected release."));
        }
        if (mark && !candidates.isEmpty()) {
            platformAuditService.record(
                "DEPLOYMENT_PROVIDER_RESOURCE_ORPHANS_MARKED",
                "DEPLOYMENT",
                deployment.getId(),
                auditDetails("candidateCount", candidates.size())
            );
        }
        return new DeploymentProviderResourceOrphanScanSummary(
            deployment.getId(),
            mark,
            candidates.size(),
            candidates,
            candidates.isEmpty()
                ? "No orphan candidates found."
                : (mark ? "Orphan candidates were marked ORPHANED." : "Orphan candidates found; rerun with mark=true to mark them.")
        );
    }

    private PromotionContext requirePromotionContext(String deploymentId, DeploymentPracticalPromotionRequest request) {
        DeploymentEntity deployment = requireOperatorDeployment(deploymentId);
        String stagingTargetProfileId = normalizeOptional(request == null ? null : request.stagingTargetProfileId());
        if (stagingTargetProfileId == null) {
            stagingTargetProfileId = DEFAULT_CUSTOMER_STAGING_TARGET_PROFILE_ID;
        }
        String productionTargetProfileId = normalizeOptional(request == null ? null : request.productionTargetProfileId());
        if (productionTargetProfileId == null) {
            productionTargetProfileId = DEFAULT_PRODUCTION_TARGET_PROFILE_ID;
        }
        if (stagingTargetProfileId.equals(productionTargetProfileId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Staging and production target profiles must be different.");
        }
        DeploymentTargetProfileEntity stagingTargetProfile = requireActiveTargetProfile(stagingTargetProfileId);
        DeploymentTargetProfileEntity productionTargetProfile = requireActiveTargetProfile(productionTargetProfileId);
        if (stagingTargetProfile.getProviderType() != productionTargetProfile.getProviderType()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Staging and production target profiles must use the same provider type.");
        }

        String requestedVersionId = normalizeOptional(request == null ? null : request.versionId());
        DeploymentReleaseEntity stagingRelease = requestedVersionId == null
            ? latestVerifiedReleaseForTarget(deployment.getId(), stagingTargetProfileId)
            : latestVerifiedReleaseForTargetAndVersion(deployment.getId(), requestedVersionId, stagingTargetProfileId);
        if (stagingRelease == null) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "No verified customer staging release exists for target profile " + stagingTargetProfileId + "."
            );
        }
        DeploymentVersionEntity version = deploymentVersionRepository.findById(stagingRelease.getDeploymentVersionId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Version not found: " + stagingRelease.getDeploymentVersionId()));
        if (!deployment.getId().equals(version.getDeploymentId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Version does not belong to deployment.");
        }
        return new PromotionContext(
            deployment,
            version,
            stagingRelease,
            stagingTargetProfileId,
            productionTargetProfileId
        );
    }

    private DeploymentEntity requireOperatorDeployment(String deploymentId) {
        String normalized = requireText(deploymentId, "deploymentId");
        DeploymentEntity deployment = deploymentRepository.findById(normalized)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Deployment not found: " + normalized));
        return deploymentAccessService.requireDeploymentOperatorAccess(deployment);
    }

    private DeploymentTargetProfileEntity requireActiveTargetProfile(String targetProfileId) {
        DeploymentTargetProfileEntity profile = deploymentTargetProfileRepository.findById(targetProfileId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Target profile not found: " + targetProfileId));
        if (!profile.isActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Target profile is not active: " + targetProfileId);
        }
        return profile;
    }

    private DeploymentReleaseEntity latestVerifiedReleaseForTarget(String deploymentId, String targetProfileId) {
        return deploymentReleaseRepository.findByDeploymentIdAndTargetProfileIdOrderByCreatedAtDesc(deploymentId, targetProfileId)
            .stream()
            .filter(this::verifiedRelease)
            .findFirst()
            .orElse(null);
    }

    private DeploymentReleaseEntity latestVerifiedReleaseForTargetAndVersion(String deploymentId,
                                                                             String versionId,
                                                                             String targetProfileId) {
        return deploymentReleaseRepository.findTopByDeploymentIdAndDeploymentVersionIdAndTargetProfileIdOrderByCreatedAtDesc(
                deploymentId,
                versionId,
                targetProfileId
            )
            .filter(this::verifiedRelease)
            .orElse(null);
    }

    private DeploymentReleaseEntity requireVerifiedReleaseForDeployment(String deploymentId, String releaseId) {
        DeploymentReleaseEntity release = deploymentReleaseRepository.findById(releaseId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Release not found: " + releaseId));
        if (!deploymentId.equals(release.getDeploymentId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Release does not belong to the selected deployment.");
        }
        if (!verifiedRelease(release)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Release must be verified before production activation.");
        }
        return release;
    }

    private boolean verifiedRelease(DeploymentReleaseEntity release) {
        return release != null
            && APPLIED_VERIFIED.equalsIgnoreCase(release.getStatus())
            && PASSED.equalsIgnoreCase(release.getVerificationStatus());
    }

    private PlatformConsumerEntity requireCustomerConsumer(String customerId, String consumerId) {
        return platformConsumerRepository.findByCustomerIdAndConsumerIdIgnoreCase(customerId, consumerId.trim().toLowerCase(Locale.ROOT))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Consumer not found: " + consumerId));
    }

    private List<DeploymentProviderResourceLifecycleSummary> markHandlesByRelease(String releaseId,
                                                                                  String status,
                                                                                  String reason) {
        return resourceHandleRepository.findByReleaseIdOrderByUpdatedAtDesc(releaseId)
            .stream()
            .map(handle -> markHandle(handle, status, reason))
            .toList();
    }

    private List<DeploymentProviderResourceLifecycleSummary> markHandlesForDeployment(String deploymentId,
                                                                                      String status,
                                                                                      String reason) {
        return resourceHandleRepository.findByDeploymentIdOrderByUpdatedAtDesc(deploymentId)
            .stream()
            .map(handle -> markHandle(handle, status, reason))
            .toList();
    }

    private List<DeploymentProviderResourceLifecycleSummary> markHandlesForDeploymentAndTarget(String deploymentId,
                                                                                               String targetProfileId,
                                                                                               String status,
                                                                                               String reason) {
        return resourceHandleRepository.findByDeploymentIdAndTargetProfileIdOrderByUpdatedAtDesc(deploymentId, targetProfileId)
            .stream()
            .map(handle -> markHandle(handle, status, reason))
            .toList();
    }

    private DeploymentProviderResourceLifecycleSummary markHandle(DeploymentProviderResourceHandleEntity handle,
                                                                 String status,
                                                                 String reason) {
        String normalizedStatus = normalizeLifecycleStatus(status);
        String previousStatus = handle.getStatus();
        handle.setStatus(normalizedStatus);
        handle.setUpdatedAt(Instant.now());
        resourceHandleRepository.save(handle);
        return lifecycleSummary(handle, previousStatus, normalizedStatus, "LIFECYCLE_STATUS_UPDATED", reason);
    }

    private String normalizeLifecycleStatus(String status) {
        String normalized = requireText(status, "status").toUpperCase(Locale.ROOT);
        if (!RESOURCE_LIFECYCLE_STATUSES.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported lifecycle status: " + status);
        }
        return normalized;
    }

    private boolean isOrphanCandidate(DeploymentProviderResourceHandleEntity handle,
                                      DeploymentReleaseEntity release,
                                      Set<String> boundReleaseIds) {
        if (handle == null || !StringUtils.hasText(handle.getReleaseId())) {
            return handle != null && !PROTECTED_RESOURCE_STATUSES.contains(normalized(handle.getStatus()));
        }
        if (boundReleaseIds.contains(handle.getReleaseId())) {
            return false;
        }
        if (PROTECTED_RESOURCE_STATUSES.contains(normalized(handle.getStatus()))) {
            return false;
        }
        return release == null || !PROTECTED_RELEASE_STATUSES.contains(normalized(release.getStatus()));
    }

    private Map<String, DeploymentReleaseEntity> releasesById(List<DeploymentProviderResourceHandleEntity> handles) {
        List<String> releaseIds = handles.stream()
            .map(DeploymentProviderResourceHandleEntity::getReleaseId)
            .filter(StringUtils::hasText)
            .distinct()
            .toList();
        if (releaseIds.isEmpty()) {
            return Map.of();
        }
        return deploymentReleaseRepository.findAllById(releaseIds)
            .stream()
            .collect(java.util.stream.Collectors.toMap(
                DeploymentReleaseEntity::getId,
                release -> release,
                (left, right) -> left,
                LinkedHashMap::new
            ));
    }

    private DeploymentProviderResourceLifecycleSummary lifecycleSummary(DeploymentProviderResourceHandleEntity handle,
                                                                       String previousStatus,
                                                                       String status,
                                                                       String proposedAction,
                                                                       String reason) {
        return new DeploymentProviderResourceLifecycleSummary(
            handle.getId(),
            handle.getDeploymentId(),
            handle.getReleaseId(),
            handle.getTargetProfileId(),
            handle.getProviderType(),
            handle.getResourceKind(),
            handle.getFqdn(),
            previousStatus,
            status,
            proposedAction,
            reason,
            readJson(handle.getMetadataJson()),
            handle.getUpdatedAt()
        );
    }

    private DeploymentPracticalPromotionSummary summary(String status,
                                                        String message,
                                                        PromotionContext context,
                                                        String productionReleaseId,
                                                        String productionReleaseStatus,
                                                        List<DeploymentProviderResourceLifecycleSummary> resources) {
        return new DeploymentPracticalPromotionSummary(
            status,
            message,
            context.deployment().getId(),
            context.version().getId(),
            context.stagingTargetProfileId(),
            context.productionTargetProfileId(),
            context.stagingRelease().getId(),
            context.stagingRelease().getStatus(),
            productionReleaseId,
            productionReleaseStatus,
            null,
            resources
        );
    }

    private JsonNode readJson(String json) {
        try {
            return objectMapper.readTree(json == null || json.isBlank() ? "{}" : json);
        } catch (Exception ex) {
            return objectMapper.createObjectNode();
        }
    }

    private String normalizeOptional(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String requireText(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " is required.");
        }
        return value.trim();
    }

    private String reason(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String normalized(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "";
    }

    private Map<String, Object> auditDetails(Object... entries) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index + 1 < entries.length; index += 2) {
            if (entries[index] instanceof String key) {
                result.put(key, entries[index + 1] == null ? "" : entries[index + 1]);
            }
        }
        return result;
    }

    private record PromotionContext(
        DeploymentEntity deployment,
        DeploymentVersionEntity version,
        DeploymentReleaseEntity stagingRelease,
        String stagingTargetProfileId,
        String productionTargetProfileId
    ) {
    }
}
