package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.PublicApiDeploymentEntity;
import com.ai.fabric.platform.backend.deployment.model.CreateDeploymentRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentDraftResponse;
import com.ai.fabric.platform.backend.deployment.model.DeploymentOverviewSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentReleaseSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentVersionSummary;
import com.ai.fabric.platform.backend.deployment.model.PublicApplyDeploymentRequest;
import com.ai.fabric.platform.backend.deployment.model.PublicApplyDeploymentResponse;
import com.ai.fabric.platform.backend.deployment.model.PublicCreateDeploymentRequest;
import com.ai.fabric.platform.backend.deployment.model.PublicDeploymentCredentialsResponse;
import com.ai.fabric.platform.backend.deployment.model.PublicDeploymentStatusResponse;
import com.ai.fabric.platform.backend.deployment.model.PublicDeploymentSummary;
import com.ai.fabric.platform.backend.deployment.repository.PublicApiDeploymentRepository;
import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.PlatformSecurityContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class PublicProvisioningApiService {

    private final PublicApiDeploymentRepository publicApiDeploymentRepository;
    private final DeploymentService deploymentService;
    private final PlatformAuditService platformAuditService;
    private final ObjectMapper objectMapper;

    public PublicProvisioningApiService(PublicApiDeploymentRepository publicApiDeploymentRepository,
                                        DeploymentService deploymentService,
                                        PlatformAuditService platformAuditService,
                                        ObjectMapper objectMapper) {
        this.publicApiDeploymentRepository = publicApiDeploymentRepository;
        this.deploymentService = deploymentService;
        this.platformAuditService = platformAuditService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PublicDeploymentSummary createDeployment(PublicCreateDeploymentRequest request) {
        String clientId = currentClientId();
        PublicApiDeploymentEntity existingBinding = publicApiDeploymentRepository
            .findByClientIdAndExternalDeploymentKey(clientId, request.externalDeploymentKey().trim())
            .orElse(null);

        if (existingBinding != null) {
            DeploymentOverviewSummary overview = deploymentService.getDeploymentOverview(existingBinding.getDeploymentId());
            validateReplayRequest(request, overview);
            ensurePublishedAndMaybeApplied(existingBinding, request.autoApply());
            platformAuditService.record(
                "PUBLIC_API_DEPLOYMENT_CREATE_REPLAYED",
                "PUBLIC_API_DEPLOYMENT",
                existingBinding.getId(),
                java.util.Map.of(
                    "clientId", clientId,
                    "externalDeploymentKey", existingBinding.getExternalDeploymentKey(),
                    "deploymentId", existingBinding.getDeploymentId()
                )
            );
            return toPublicSummary(existingBinding, deploymentService.getDeploymentOverview(existingBinding.getDeploymentId()), false);
        }

        var created = deploymentService.createDeployment(new CreateDeploymentRequest(
            request.name().trim(),
            request.environment().trim(),
            request.templateId().trim()
        ));

        PublicApiDeploymentEntity binding = new PublicApiDeploymentEntity();
        binding.setId(generateId("pub"));
        binding.setClientId(clientId);
        binding.setExternalDeploymentKey(request.externalDeploymentKey().trim());
        binding.setDeploymentId(created.id());
        binding.setCallbackMetadataJson(writeJson(request.callbackMetadata()));
        binding.setCreatedAt(Instant.now());
        binding.setUpdatedAt(Instant.now());
        publicApiDeploymentRepository.saveAndFlush(binding);

        DeploymentDraftResponse draft = deploymentService.getActiveDraftForDeployment(created.id());
        deploymentService.publishDraft(draft.id());
        platformAuditService.record(
            "PUBLIC_API_DEPLOYMENT_CREATED",
            "PUBLIC_API_DEPLOYMENT",
            binding.getId(),
            java.util.Map.of(
                "clientId", clientId,
                "externalDeploymentKey", binding.getExternalDeploymentKey(),
                "deploymentId", binding.getDeploymentId(),
                "autoApply", request.autoApply()
            )
        );

        ensurePublishedAndMaybeApplied(binding, request.autoApply());
        return toPublicSummary(binding, deploymentService.getDeploymentOverview(binding.getDeploymentId()), true);
    }

    public PublicDeploymentSummary getDeployment(String deploymentId) {
        PublicApiDeploymentEntity binding = getBindingByDeploymentId(currentClientId(), deploymentId);
        return toPublicSummary(binding, deploymentService.getDeploymentOverview(binding.getDeploymentId()), false);
    }

    public PublicDeploymentStatusResponse getDeploymentStatus(String deploymentId) {
        PublicApiDeploymentEntity binding = getBindingByDeploymentId(currentClientId(), deploymentId);
        DeploymentOverviewSummary overview = deploymentService.getDeploymentOverview(binding.getDeploymentId());
        DeploymentVersionSummary latestVersion = findLatestVersion(binding.getDeploymentId());
        return new PublicDeploymentStatusResponse(
            binding.getClientId(),
            binding.getExternalDeploymentKey(),
            binding.getDeploymentId(),
            overview.status(),
            overview.healthStatus(),
            overview.healthSummary(),
            overview.activeVersion(),
            latestVersion == null ? null : latestVersion.id(),
            latestVersion == null ? null : latestVersion.versionLabel(),
            overview.runtimeBaseUrl(),
            overview.connectorBaseUrl(),
            overview.latestRelease(),
            overview.latestVerification(),
            overview.createdAt(),
            overview.updatedAt()
        );
    }

    @Transactional
    public PublicApplyDeploymentResponse applyDeployment(String deploymentId, PublicApplyDeploymentRequest request) {
        String clientId = currentClientId();
        PublicApiDeploymentEntity binding = getBindingByDeploymentId(clientId, deploymentId);

        DeploymentVersionSummary version = resolveVersionToApply(binding.getDeploymentId(), request);
        DeploymentReleaseSummary latestForVersion = deploymentService.listReleases(binding.getDeploymentId()).stream()
            .filter(release -> version.id().equals(release.deploymentVersionId()))
            .findFirst()
            .orElse(null);

        if (latestForVersion != null && isReplayable(latestForVersion)) {
            platformAuditService.record(
                "PUBLIC_API_APPLY_REPLAYED",
                "DEPLOYMENT_RELEASE",
                latestForVersion.id(),
                java.util.Map.of(
                    "clientId", clientId,
                    "deploymentId", binding.getDeploymentId(),
                    "versionId", version.id()
                )
            );
            return new PublicApplyDeploymentResponse(
                clientId,
                binding.getExternalDeploymentKey(),
                binding.getDeploymentId(),
                version.id(),
                version.versionLabel(),
                true,
                latestForVersion
            );
        }

        DeploymentReleaseSummary release = deploymentService.applyVersion(binding.getDeploymentId(), version.id());
        platformAuditService.record(
            "PUBLIC_API_APPLY_REQUESTED",
            "DEPLOYMENT_RELEASE",
            release.id(),
            java.util.Map.of(
                "clientId", clientId,
                "deploymentId", binding.getDeploymentId(),
                "versionId", version.id()
            )
        );
        return new PublicApplyDeploymentResponse(
            clientId,
            binding.getExternalDeploymentKey(),
            binding.getDeploymentId(),
            version.id(),
            version.versionLabel(),
            false,
            release
        );
    }

    public PublicDeploymentCredentialsResponse getDeploymentCredentials(String deploymentId) {
        PublicApiDeploymentEntity binding = getBindingByDeploymentId(currentClientId(), deploymentId);
        DeploymentOverviewSummary overview = deploymentService.getDeploymentOverview(binding.getDeploymentId());
        return new PublicDeploymentCredentialsResponse(
            binding.getClientId(),
            binding.getExternalDeploymentKey(),
            binding.getDeploymentId(),
            overview.runtimeBaseUrl(),
            overview.connectorBaseUrl()
        );
    }

    private void ensurePublishedAndMaybeApplied(PublicApiDeploymentEntity binding, boolean autoApply) {
        DeploymentVersionSummary latestVersion = findLatestVersion(binding.getDeploymentId());
        if (latestVersion == null) {
            DeploymentDraftResponse draft = deploymentService.getActiveDraftForDeployment(binding.getDeploymentId());
            latestVersion = deploymentService.publishDraft(draft.id());
        }
        if (autoApply) {
            applyDeployment(binding.getDeploymentId(), new PublicApplyDeploymentRequest(latestVersion.id()));
        }
    }

    private DeploymentVersionSummary resolveVersionToApply(String deploymentId, PublicApplyDeploymentRequest request) {
        if (request != null && request.versionId() != null && !request.versionId().isBlank()) {
            return deploymentService.listVersions(deploymentId).stream()
                .filter(version -> request.versionId().trim().equals(version.id()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                    NOT_FOUND,
                    "Version not found for deployment: " + request.versionId().trim()
                ));
        }
        DeploymentVersionSummary latestVersion = findLatestVersion(deploymentId);
        if (latestVersion != null) {
            return latestVersion;
        }
        DeploymentDraftResponse draft = deploymentService.getActiveDraftForDeployment(deploymentId);
        return deploymentService.publishDraft(draft.id());
    }

    private DeploymentVersionSummary findLatestVersion(String deploymentId) {
        List<DeploymentVersionSummary> versions = deploymentService.listVersions(deploymentId);
        return versions.isEmpty() ? null : versions.get(0);
    }

    private boolean isReplayable(DeploymentReleaseSummary release) {
        return switch (release.status()) {
            case "APPLY_REQUESTED", "PROVISIONING", "VERIFYING", "APPLIED_VERIFIED", "APPLIED_VERIFICATION_FAILED" -> true;
            default -> false;
        };
    }

    private void validateReplayRequest(PublicCreateDeploymentRequest request, DeploymentOverviewSummary overview) {
        if (!overview.name().equals(request.name().trim())
            || !overview.environment().equals(request.environment().trim())
            || !overview.templateId().equals(request.templateId().trim())) {
            throw new ResponseStatusException(
                CONFLICT,
                "External deployment key is already bound to a different deployment request."
            );
        }
        if ("ARCHIVED".equalsIgnoreCase(overview.status())) {
            throw new ResponseStatusException(CONFLICT, "External deployment key is bound to an archived deployment.");
        }
    }

    private PublicApiDeploymentEntity getBindingByDeploymentId(String clientId, String deploymentId) {
        return publicApiDeploymentRepository.findByClientIdAndDeploymentId(clientId, deploymentId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Public deployment not found: " + deploymentId));
    }

    private PublicDeploymentSummary toPublicSummary(PublicApiDeploymentEntity binding,
                                                    DeploymentOverviewSummary overview,
                                                    boolean created) {
        DeploymentVersionSummary latestVersion = findLatestVersion(binding.getDeploymentId());
        return new PublicDeploymentSummary(
            binding.getClientId(),
            binding.getExternalDeploymentKey(),
            binding.getDeploymentId(),
            created,
            overview.name(),
            overview.environment(),
            overview.templateId(),
            overview.status(),
            overview.activeVersion(),
            latestVersion == null ? null : latestVersion.id(),
            latestVersion == null ? null : latestVersion.versionLabel(),
            overview.runtimeBaseUrl(),
            overview.connectorBaseUrl(),
            overview.latestRelease(),
            overview.latestVerification(),
            overview.createdAt(),
            overview.updatedAt()
        );
    }

    private String currentClientId() {
        PlatformPrincipal principal = PlatformSecurityContext.currentPrincipal();
        if (principal == null || principal.role() != com.ai.fabric.platform.backend.security.PlatformRole.PUBLIC_API_CLIENT) {
            throw new ResponseStatusException(BAD_REQUEST, "Public API client identity is missing.");
        }
        return principal.actorId();
    }

    private String writeJson(com.fasterxml.jackson.databind.JsonNode callbackMetadata) {
        try {
            return callbackMetadata == null ? null : objectMapper.writeValueAsString(callbackMetadata);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize callback metadata", ex);
        }
    }

    private String generateId(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
