package com.ai.fabric.platform.backend.secret.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.service.DeploymentAccessService;
import com.ai.fabric.platform.backend.secret.entity.DeploymentProviderSecretBindingEntity;
import com.ai.fabric.platform.backend.secret.entity.DeploymentProviderSecretBindingMode;
import com.ai.fabric.platform.backend.secret.entity.PlatformSecretCleanupPolicy;
import com.ai.fabric.platform.backend.secret.entity.PlatformSecretEntity;
import com.ai.fabric.platform.backend.secret.entity.PlatformSecretOwnerType;
import com.ai.fabric.platform.backend.secret.entity.PlatformSecretScopeType;
import com.ai.fabric.platform.backend.secret.model.DeploymentProviderSecretBindingCatalogSummary;
import com.ai.fabric.platform.backend.secret.model.DeploymentProviderSecretBindingSummary;
import com.ai.fabric.platform.backend.secret.model.DeploymentProviderSecretOverrideCleanupSummary;
import com.ai.fabric.platform.backend.secret.model.PlatformDeploymentOverrideSecretSummary;
import com.ai.fabric.platform.backend.secret.model.UpsertDeploymentProviderSecretBindingRequest;
import com.ai.fabric.platform.backend.secret.model.UpsertPlatformDeploymentOverrideSecretRequest;
import com.ai.fabric.platform.backend.secret.repository.DeploymentProviderSecretBindingRepository;
import com.ai.fabric.platform.backend.secret.repository.PlatformSecretRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class DeploymentProviderSecretOverrideService {

    private final PlatformSecretRepository platformSecretRepository;
    private final DeploymentProviderSecretBindingRepository bindingRepository;
    private final DeploymentRepository deploymentRepository;
    private final DeploymentAccessService deploymentAccessService;
    private final DeploymentSecretPurposeCatalog purposeCatalog;
    private final DeploymentProviderSecretResolutionService resolutionService;
    private final PlatformAuditService platformAuditService;

    public DeploymentProviderSecretOverrideService(PlatformSecretRepository platformSecretRepository,
                                                   DeploymentProviderSecretBindingRepository bindingRepository,
                                                   DeploymentRepository deploymentRepository,
                                                   DeploymentAccessService deploymentAccessService,
                                                   DeploymentSecretPurposeCatalog purposeCatalog,
                                                   DeploymentProviderSecretResolutionService resolutionService,
                                                   PlatformAuditService platformAuditService) {
        this.platformSecretRepository = platformSecretRepository;
        this.bindingRepository = bindingRepository;
        this.deploymentRepository = deploymentRepository;
        this.deploymentAccessService = deploymentAccessService;
        this.purposeCatalog = purposeCatalog;
        this.resolutionService = resolutionService;
        this.platformAuditService = platformAuditService;
    }

    public List<PlatformDeploymentOverrideSecretSummary> listOverrideSecrets() {
        return platformSecretRepository.findByScopeTypeOrderByUpdatedAtDesc(PlatformSecretScopeType.DEPLOYMENT_OVERRIDE).stream()
            .map(this::toOverrideSummary)
            .toList();
    }

    public List<String> supportedOverridePurposes() {
        return purposeCatalog.supportedOverridePurposes().stream().sorted().toList();
    }

    @Transactional
    public PlatformDeploymentOverrideSecretSummary upsertOverrideSecret(String secretName,
                                                                       UpsertPlatformDeploymentOverrideSecretRequest request) {
        String normalizedName = requireOverrideSecretName(secretName);
        DeploymentSecretPurposeCatalog.SecretPurposeDefinition definition =
            purposeCatalog.requireDefinition(request.secretPurpose());
        PlatformSecretEntity entity = platformSecretRepository.findById(normalizedName).orElseGet(PlatformSecretEntity::new);
        entity.setName(normalizedName);
        entity.setSecretValue(requireValue(request.value()));
        entity.setUpdatedAt(Instant.now());
        entity.setScopeType(PlatformSecretScopeType.DEPLOYMENT_OVERRIDE);
        entity.setDeploymentId(trimToNull(request.deploymentId()));
        entity.setSecretPurpose(definition.purpose());
        entity.setOwnerType(PlatformSecretOwnerType.DEPLOYMENT);
        entity.setManagedByPlatform(false);
        entity.setCleanupPolicy(resolveCleanupPolicy(request.cleanupPolicy(), entity.getDeploymentId()));
        platformSecretRepository.save(entity);
        Map<String, Object> auditDetails = new LinkedHashMap<>();
        auditDetails.put("secretPurpose", definition.purpose());
        auditDetails.put("deploymentId", trimToNull(request.deploymentId()));
        auditDetails.put("cleanupPolicy", entity.getCleanupPolicy().name());
        platformAuditService.record(
            "DEPLOYMENT_OVERRIDE_SECRET_UPSERTED",
            "PLATFORM_SECRET",
            normalizedName,
            auditDetails
        );
        return toOverrideSummary(entity);
    }

    @Transactional
    public PlatformDeploymentOverrideSecretSummary clearOverrideSecret(String secretName) {
        PlatformSecretEntity entity = platformSecretRepository.findById(secretName)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Deployment override secret not found: " + secretName));
        if (entity.getScopeType() != PlatformSecretScopeType.DEPLOYMENT_OVERRIDE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only deployment override secrets can be deleted through this endpoint.");
        }
        int bindingCount = countBindings(secretName);
        if (bindingCount > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Deployment override secret is still bound and cannot be deleted: " + secretName);
        }
        platformSecretRepository.delete(entity);
        Map<String, Object> auditDetails = new LinkedHashMap<>();
        auditDetails.put("secretPurpose", entity.getSecretPurpose());
        auditDetails.put("cleanupPolicy", entity.getCleanupPolicy() == null ? null : entity.getCleanupPolicy().name());
        platformAuditService.record(
            "DEPLOYMENT_OVERRIDE_SECRET_CLEARED",
            "PLATFORM_SECRET",
            secretName,
            auditDetails
        );
        return toOverrideSummary(entity);
    }

    public DeploymentProviderSecretBindingCatalogSummary listBindings(String deploymentId) {
        DeploymentEntity deployment = requireDeploymentForAdmin(deploymentId);
        List<DeploymentProviderSecretBindingSummary> bindings = bindingRepository.findByDeploymentIdOrderBySecretPurposeAsc(deployment.getId()).stream()
            .map(this::toBindingSummary)
            .toList();
        return new DeploymentProviderSecretBindingCatalogSummary(
            deployment.getId(),
            supportedOverridePurposes(),
            listOverrideSecrets(),
            bindings
        );
    }

    @Transactional
    public DeploymentProviderSecretBindingSummary upsertBinding(String deploymentId,
                                                                UpsertDeploymentProviderSecretBindingRequest request) {
        DeploymentEntity deployment = requireDeploymentForAdmin(deploymentId);
        DeploymentSecretPurposeCatalog.SecretPurposeDefinition definition =
            purposeCatalog.requireDefinition(request.secretPurpose());
        DeploymentProviderSecretBindingMode bindingMode = resolveBindingMode(request.bindingMode());
        String primarySecretName = trimToNull(request.secretName());
        String secondarySecretName = trimToNull(request.secondarySecretName());

        if (definition.paired()) {
            if (primarySecretName == null || secondarySecretName == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Paired secret purpose requires both primary and secondary secret names.");
            }
            requireOverrideSecretForPurpose(primarySecretName, definition.purpose());
            requireOverrideSecretForPurpose(secondarySecretName, definition.purpose());
        } else {
            if (primarySecretName == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A secret name is required to bind this override.");
            }
            if (secondarySecretName != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Secondary secret name is not allowed for this secret purpose.");
            }
            requireOverrideSecretForPurpose(primarySecretName, definition.purpose());
        }

        DeploymentProviderSecretBindingEntity entity = bindingRepository.findByDeploymentIdAndSecretPurpose(deployment.getId(), definition.purpose())
            .orElseGet(DeploymentProviderSecretBindingEntity::new);
        if (entity.getId() == null) {
            entity.setId("dpsb-" + UUID.randomUUID().toString().substring(0, 8));
            entity.setCreatedAt(Instant.now());
        }
        entity.setDeploymentId(deployment.getId());
        entity.setSecretPurpose(definition.purpose());
        entity.setBindingMode(bindingMode);
        entity.setSecretName(primarySecretName);
        entity.setSecondarySecretName(secondarySecretName);
        entity.setUpdatedAt(Instant.now());
        bindingRepository.save(entity);
        Map<String, Object> auditDetails = new LinkedHashMap<>();
        auditDetails.put("bindingId", entity.getId());
        auditDetails.put("secretPurpose", definition.purpose());
        auditDetails.put("bindingMode", bindingMode.name());
        auditDetails.put("secretName", primarySecretName);
        auditDetails.put("secondarySecretName", secondarySecretName);
        platformAuditService.record(
            "DEPLOYMENT_OVERRIDE_BOUND",
            "DEPLOYMENT",
            deployment.getId(),
            auditDetails
        );
        return toBindingSummary(entity);
    }

    @Transactional
    public void clearBinding(String deploymentId, String secretPurpose) {
        DeploymentEntity deployment = requireDeploymentForAdmin(deploymentId);
        DeploymentSecretPurposeCatalog.SecretPurposeDefinition definition = purposeCatalog.requireDefinition(secretPurpose);
        DeploymentProviderSecretBindingEntity entity = bindingRepository.findByDeploymentIdAndSecretPurpose(deployment.getId(), definition.purpose())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Deployment override binding not found for purpose: " + definition.purpose()));
        bindingRepository.delete(entity);
        Map<String, Object> auditDetails = new LinkedHashMap<>();
        auditDetails.put("bindingId", entity.getId());
        auditDetails.put("secretPurpose", definition.purpose());
        auditDetails.put("secretName", entity.getSecretName());
        auditDetails.put("secondarySecretName", entity.getSecondarySecretName());
        platformAuditService.record(
            "DEPLOYMENT_OVERRIDE_UNBOUND",
            "DEPLOYMENT",
            deployment.getId(),
            auditDetails
        );
    }

    @Transactional
    public DeploymentProviderSecretOverrideCleanupSummary cleanupForHardDelete(String deploymentId, String reason) {
        List<DeploymentProviderSecretBindingEntity> bindings = bindingRepository.findByDeploymentIdOrderBySecretPurposeAsc(deploymentId);
        List<String> removedBindingIds = bindings.stream().map(DeploymentProviderSecretBindingEntity::getId).toList();
        Set<String> candidateSecretNames = new LinkedHashSet<>();
        bindings.forEach(binding -> {
            if (binding.getSecretName() != null) {
                candidateSecretNames.add(binding.getSecretName());
            }
            if (binding.getSecondarySecretName() != null) {
                candidateSecretNames.add(binding.getSecondarySecretName());
            }
        });
        platformSecretRepository.findByScopeTypeAndDeploymentIdOrderByUpdatedAtDesc(PlatformSecretScopeType.DEPLOYMENT_OVERRIDE, deploymentId)
            .forEach(secret -> candidateSecretNames.add(secret.getName()));
        bindingRepository.deleteByDeploymentId(deploymentId);

        List<String> deletedSecretNames = new ArrayList<>();
        List<String> preservedSecretNames = new ArrayList<>();
        for (String secretName : candidateSecretNames) {
            PlatformSecretEntity entity = platformSecretRepository.findById(secretName).orElse(null);
            if (entity == null || entity.getScopeType() != PlatformSecretScopeType.DEPLOYMENT_OVERRIDE) {
                continue;
            }
            if (shouldDeleteOnHardDelete(entity)) {
                platformSecretRepository.delete(entity);
                deletedSecretNames.add(secretName);
            } else {
                preservedSecretNames.add(secretName);
            }
        }
        platformAuditService.record(
            "DEPLOYMENT_OVERRIDE_CLEANUP_COMPLETED",
            "DEPLOYMENT",
            deploymentId,
            Map.of(
                "reason", reason == null ? "" : reason,
                "removedBindingIds", removedBindingIds,
                "deletedSecretNames", deletedSecretNames,
                "preservedSecretNames", preservedSecretNames
            )
        );
        return new DeploymentProviderSecretOverrideCleanupSummary(
            List.copyOf(removedBindingIds),
            List.copyOf(deletedSecretNames),
            List.copyOf(preservedSecretNames)
        );
    }

    private PlatformDeploymentOverrideSecretSummary toOverrideSummary(PlatformSecretEntity entity) {
        return new PlatformDeploymentOverrideSecretSummary(
            entity.getName(),
            purposeCatalog.definition(entity.getSecretPurpose()).map(DeploymentSecretPurposeCatalog.SecretPurposeDefinition::displayName).orElse(entity.getSecretPurpose()),
            entity.getSecretPurpose(),
            entity.getDeploymentId(),
            entity.getScopeType() == null ? null : entity.getScopeType().name(),
            entity.getOwnerType() == null ? null : entity.getOwnerType().name(),
            entity.getCleanupPolicy() == null ? null : entity.getCleanupPolicy().name(),
            entity.getSecretValue() != null && !entity.getSecretValue().isBlank(),
            countBindings(entity.getName()),
            entity.getUpdatedAt() == null ? null : entity.getUpdatedAt().toString()
        );
    }

    private DeploymentProviderSecretBindingSummary toBindingSummary(DeploymentProviderSecretBindingEntity entity) {
        return new DeploymentProviderSecretBindingSummary(
            entity.getId(),
            entity.getDeploymentId(),
            entity.getSecretPurpose(),
            purposeCatalog.definition(entity.getSecretPurpose()).map(DeploymentSecretPurposeCatalog.SecretPurposeDefinition::displayName).orElse(entity.getSecretPurpose()),
            entity.getBindingMode().name(),
            entity.getSecretName(),
            entity.getSecondarySecretName(),
            resolutionService.summarize(entity.getDeploymentId(), entity.getSecretPurpose()),
            entity.getCreatedAt() == null ? null : entity.getCreatedAt().toString(),
            entity.getUpdatedAt() == null ? null : entity.getUpdatedAt().toString()
        );
    }

    private void requireOverrideSecretForPurpose(String secretName, String secretPurpose) {
        PlatformSecretEntity entity = platformSecretRepository.findById(secretName)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Deployment override secret not found: " + secretName));
        if (entity.getScopeType() != PlatformSecretScopeType.DEPLOYMENT_OVERRIDE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Secret is not a deployment override: " + secretName);
        }
        if (!secretPurpose.equals(entity.getSecretPurpose())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Secret " + secretName + " is not registered for purpose " + secretPurpose + ".");
        }
    }

    private boolean shouldDeleteOnHardDelete(PlatformSecretEntity entity) {
        PlatformSecretCleanupPolicy cleanupPolicy = entity.getCleanupPolicy();
        if (cleanupPolicy == null) {
            cleanupPolicy = entity.getDeploymentId() == null
                ? PlatformSecretCleanupPolicy.KEEP
                : PlatformSecretCleanupPolicy.DELETE_ON_HARD_DELETE;
        }
        return switch (cleanupPolicy) {
            case KEEP -> false;
            case DELETE_ON_HARD_DELETE -> entity.getDeploymentId() != null;
            case DELETE_WHEN_UNREFERENCED -> countBindings(entity.getName()) == 0;
        };
    }

    private int countBindings(String secretName) {
        long count = bindingRepository.countBySecretName(secretName) + bindingRepository.countBySecondarySecretName(secretName);
        return Math.toIntExact(count);
    }

    private PlatformSecretCleanupPolicy resolveCleanupPolicy(String requested, String deploymentId) {
        if (requested != null && !requested.isBlank()) {
            try {
                return PlatformSecretCleanupPolicy.valueOf(requested.trim().toUpperCase());
            } catch (IllegalArgumentException ex) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported cleanup policy: " + requested);
            }
        }
        return deploymentId == null ? PlatformSecretCleanupPolicy.KEEP : PlatformSecretCleanupPolicy.DELETE_ON_HARD_DELETE;
    }

    private DeploymentProviderSecretBindingMode resolveBindingMode(String requested) {
        if (requested == null || requested.isBlank()) {
            return DeploymentProviderSecretBindingMode.ALLOW_STANDARD_PRECEDENCE;
        }
        try {
            return DeploymentProviderSecretBindingMode.valueOf(requested.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported deployment override binding mode: " + requested);
        }
    }

    private String requireOverrideSecretName(String secretName) {
        String normalized = trimToNull(secretName);
        if (normalized == null || !normalized.matches("^[A-Z0-9_]+$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Deployment override secret names must use uppercase letters, digits, and underscores only.");
        }
        if (normalized.startsWith(PlatformSecretService.MANAGED_SECRET_PREFIX)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Deployment override secret names cannot use the MANAGED_ prefix.");
        }
        if (purposeCatalog.isSupportedOverridePurpose(normalized) || "MILVUS_USERNAME".equals(normalized) || "MILVUS_PASSWORD".equals(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Deployment override secret names must not reuse a global platform secret name.");
        }
        return normalized;
    }

    private String requireValue(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Secret value is required.");
        }
        return normalized;
    }

    private DeploymentEntity requireDeploymentForAdmin(String deploymentId) {
        DeploymentEntity deployment = deploymentRepository.findById(deploymentId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Deployment not found: " + deploymentId));
        return deploymentAccessService.requireDeploymentAdminAccess(deployment);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
