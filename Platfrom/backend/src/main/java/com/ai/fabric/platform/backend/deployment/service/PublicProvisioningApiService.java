package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.entity.PublicApiDeploymentEntity;
import com.ai.fabric.platform.backend.deployment.model.CreateDeploymentRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentDraftResponse;
import com.ai.fabric.platform.backend.deployment.model.DeploymentOverviewSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentReleaseSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentVersionSummary;
import com.ai.fabric.platform.backend.deployment.model.PublicApplyDeploymentRequest;
import com.ai.fabric.platform.backend.deployment.model.PublicApplyDeploymentResponse;
import com.ai.fabric.platform.backend.deployment.model.PublicDeploymentAccessSummary;
import com.ai.fabric.platform.backend.deployment.model.PublicCreateDeploymentRequest;
import com.ai.fabric.platform.backend.deployment.model.PublicDeploymentCredentialsResponse;
import com.ai.fabric.platform.backend.deployment.model.PublicDeploymentIntegrationSummary;
import com.ai.fabric.platform.backend.deployment.model.PublicDeploymentStatusResponse;
import com.ai.fabric.platform.backend.deployment.model.PublicDeploymentSummary;
import com.ai.fabric.platform.backend.deployment.repository.PublicApiDeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
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

    private static final String RUNTIME_TRUSTED_BACKEND_SECRET_NAME = "AI_FABRIC_RUNTIME_TRUSTED_BACKEND_API_KEY";
    private static final String RUNTIME_PUBLIC_TOKEN_SIGNING_KEY_SECRET_NAME = "AI_FABRIC_RUNTIME_PUBLIC_TOKEN_SIGNING_KEY";
    private static final String RUNTIME_PUBLIC_AUTHORIZATION_HEADER = "Authorization";
    private static final String RUNTIME_PUBLIC_TOKEN_SCHEME = "Bearer";

    private final PublicApiDeploymentRepository publicApiDeploymentRepository;
    private final DeploymentService deploymentService;
    private final DeploymentVersionRepository deploymentVersionRepository;
    private final PlatformAuditService platformAuditService;
    private final PlatformSecretService platformSecretService;
    private final ObjectMapper objectMapper;

    public PublicProvisioningApiService(PublicApiDeploymentRepository publicApiDeploymentRepository,
                                        DeploymentService deploymentService,
                                        DeploymentVersionRepository deploymentVersionRepository,
                                        PlatformAuditService platformAuditService,
                                        PlatformSecretService platformSecretService,
                                        ObjectMapper objectMapper) {
        this.publicApiDeploymentRepository = publicApiDeploymentRepository;
        this.deploymentService = deploymentService;
        this.deploymentVersionRepository = deploymentVersionRepository;
        this.platformAuditService = platformAuditService;
        this.platformSecretService = platformSecretService;
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
            request.templateId().trim(),
            request.curatedModuleId(),
            request.vectorProvisioningMode()
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
        deploymentService.publishDraftForPublicApi(draft.id());
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
        PublicDeploymentAccessSummary access = accessSummary(overview, latestPublishedSecurityConfig(binding.getDeploymentId()));
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
            null,
            access,
            integrationSummary(access),
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

        DeploymentReleaseSummary release = deploymentService.applyVersionForPublicApi(binding.getDeploymentId(), version.id());
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
        PublicDeploymentAccessSummary access = accessSummary(overview, latestPublishedSecurityConfig(deploymentId));
        return new PublicDeploymentCredentialsResponse(
            binding.getClientId(),
            binding.getExternalDeploymentKey(),
            binding.getDeploymentId(),
            overview.runtimeBaseUrl(),
            null,
            access,
            integrationSummary(access)
        );
    }

    private void ensurePublishedAndMaybeApplied(PublicApiDeploymentEntity binding, boolean autoApply) {
        DeploymentVersionSummary latestVersion = findLatestVersion(binding.getDeploymentId());
        if (latestVersion == null) {
            DeploymentDraftResponse draft = deploymentService.getActiveDraftForDeployment(binding.getDeploymentId());
            latestVersion = deploymentService.publishDraftForPublicApi(draft.id());
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
        return deploymentService.publishDraftForPublicApi(draft.id());
    }

    private DeploymentVersionSummary findLatestVersion(String deploymentId) {
        List<DeploymentVersionSummary> versions = deploymentService.listVersions(deploymentId);
        return versions.isEmpty() ? null : versions.get(0);
    }

    private boolean isReplayable(DeploymentReleaseSummary release) {
        return switch (release.status()) {
            case "APPLY_REQUESTED", "PRE_APPLY_VERIFYING", "PROVISIONING", "VERIFYING", "APPLIED_VERIFIED", "APPLIED_VERIFICATION_FAILED" -> true;
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
        var latestSecurityConfig = latestPublishedSecurityConfig(binding.getDeploymentId());
        PublicDeploymentAccessSummary access = accessSummary(overview, latestSecurityConfig);
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
            null,
            access,
            integrationSummary(access),
            overview.latestRelease(),
            overview.latestVerification(),
            overview.createdAt(),
            overview.updatedAt()
        );
    }

    private com.fasterxml.jackson.databind.JsonNode latestPublishedSecurityConfig(String deploymentId) {
        DeploymentVersionEntity version = deploymentVersionRepository.findByDeploymentIdOrderByPublishedAtDesc(deploymentId).stream()
            .findFirst()
            .orElse(null);
        return version == null ? objectMapper.createObjectNode() : readJson(version.getSecurityConfigJson());
    }

    private PublicDeploymentAccessSummary accessSummary(DeploymentOverviewSummary overview,
                                                        com.fasterxml.jackson.databind.JsonNode securityConfig) {
        String runtimeBaseUrl = overview.runtimeBaseUrl();
        String connectorBaseUrl = overview.connectorBaseUrl();
        boolean trustedBackendConfigured = platformSecretService.isSecretPresent(RUNTIME_TRUSTED_BACKEND_SECRET_NAME);
        boolean publicTokenConfigured = platformSecretService.isSecretPresent(RUNTIME_PUBLIC_TOKEN_SIGNING_KEY_SECRET_NAME);
        boolean bootstrapEnabled = ManagedDeploymentProfileCatalog.publicRuntimeBootstrapEnabled(securityConfig);
        String publicRuntimeTokenIssuer = ManagedDeploymentProfileCatalog.publicRuntimeTokenIssuer(securityConfig);
        String publicRuntimeAcceptedIssuers = ManagedDeploymentProfileCatalog.publicRuntimeAcceptedIssuers(securityConfig);
        String publicRuntimeAcceptedAudiences = ManagedDeploymentProfileCatalog.publicRuntimeAcceptedAudiences(securityConfig);
        String publicRuntimeDefaultAudience = ManagedDeploymentProfileCatalog.publicRuntimeDefaultAudience(securityConfig);
        String runtimeAuthMode;
        String guidance;
        boolean hostBackedRuntimeRequired;
        if (runtimeBaseUrl == null) {
            runtimeAuthMode = "NOT_APPLIED";
            hostBackedRuntimeRequired = false;
            guidance = "Apply the deployment before integrating. Customer-facing chat and operational reads should target runtime or a host-backed facade.";
        } else if (trustedBackendConfigured) {
            runtimeAuthMode = "PRIVATE_RUNTIME_TRUSTED_BACKEND";
            hostBackedRuntimeRequired = true;
            guidance = "Runtime is configured for trusted-backend/private-runtime integration. Route customer traffic through your host or storefront backend; do not expose the connector directly.";
        } else if (publicTokenConfigured) {
            runtimeAuthMode = "PUBLIC_RUNTIME_SIGNED_TOKEN";
            hostBackedRuntimeRequired = false;
            guidance = bootstrapEnabled
                ? "Runtime can validate signed public bearer tokens and anonymous bootstrap is enabled for this deployment. Keep browser use constrained to approved origins, short-lived tokens, and low-privilege anonymous scopes."
                : "Runtime can validate signed public bearer tokens. Anonymous runtime bootstrap is not enabled by default; issue short-lived tokens from a trusted issuer or explicitly opt deployments into bootstrap later.";
        } else {
            runtimeAuthMode = "DIRECT_RUNTIME_COMPATIBILITY";
            hostBackedRuntimeRequired = false;
            guidance = "Runtime is reachable, but trusted-backend private-runtime auth is not configured yet. Treat direct runtime access as compatibility posture and plan migration to host-backed integration.";
        }
        boolean runtimePublicTokenValidationConfigured = runtimeBaseUrl != null && publicTokenConfigured;
        boolean anonymousBootstrapSupported = runtimePublicTokenValidationConfigured && bootstrapEnabled;
        return new PublicDeploymentAccessSummary(
            runtimeBaseUrl == null ? "NOT_APPLIED" : "RUNTIME_ENTRYPOINT",
            connectorBaseUrl == null ? "NOT_APPLIED" : "PRIVATE_INTERNAL_SERVICE",
            runtimeAuthMode,
            runtimeBaseUrl,
            runtimeBaseUrl,
            hostBackedRuntimeRequired,
            false,
            runtimePublicTokenValidationConfigured,
            anonymousBootstrapSupported,
            anonymousBootstrapSupported ? runtimeBaseUrl + "/api/public/chat/session" : null,
            runtimePublicTokenValidationConfigured ? RUNTIME_PUBLIC_AUTHORIZATION_HEADER : null,
            runtimePublicTokenValidationConfigured ? RUNTIME_PUBLIC_TOKEN_SCHEME : null,
            runtimePublicTokenValidationConfigured && !publicRuntimeAcceptedIssuers.isBlank(),
            runtimePublicTokenValidationConfigured && !publicRuntimeAcceptedAudiences.isBlank(),
            runtimePublicTokenValidationConfigured ? blankToNull(publicRuntimeTokenIssuer) : null,
            runtimePublicTokenValidationConfigured ? blankToNull(publicRuntimeDefaultAudience) : null,
            connectorBaseUrl == null
                ? guidance
                : guidance + " The public API intentionally does not expose the internal connector URL; treat the connector as an internal service surface only."
        );
    }

    private PublicDeploymentIntegrationSummary integrationSummary(PublicDeploymentAccessSummary access) {
        if (access == null) {
            return new PublicDeploymentIntegrationSummary(
                "NOT_APPLIED",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "NOT_APPLIED",
                false,
                true,
                false,
                false,
                "Apply the deployment before integrating."
            );
        }
        String preferredIntegrationMode = preferredIntegrationMode(access);
        return new PublicDeploymentIntegrationSummary(
            preferredIntegrationMode,
            blankToNull(access.recommendedChatBaseUrl()),
            blankToNull(access.recommendedCrudBaseUrl()),
            blankToNull(access.publicRuntimeBootstrapUrl()),
            blankToNull(access.publicRuntimeAuthorizationHeader()),
            blankToNull(access.publicRuntimeTokenScheme()),
            blankToNull(access.publicRuntimeTokenIssuerHint()),
            blankToNull(access.publicRuntimeDefaultAudience()),
            blankToNull(access.runtimeAuthMode()),
            access.hostBackedRuntimeRequired(),
            !access.directConnectorAccessSupported(),
            access.publicRuntimeTokenValidationConfigured(),
            access.anonymousBootstrapSupported(),
            blankToNull(access.guidance())
        );
    }

    private String preferredIntegrationMode(PublicDeploymentAccessSummary access) {
        if (access == null || blankToNull(access.recommendedChatBaseUrl()) == null) {
            return "NOT_APPLIED";
        }
        if (access.hostBackedRuntimeRequired()) {
            return "BACKEND_MEDIATED_PRIVATE_RUNTIME";
        }
        if (access.publicRuntimeTokenValidationConfigured()) {
            return "PUBLIC_RUNTIME_BROWSER_TOKEN";
        }
        return "DIRECT_RUNTIME_COMPATIBILITY";
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

    private com.fasterxml.jackson.databind.JsonNode readJson(String raw) {
        try {
            return raw == null || raw.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(raw);
        } catch (Exception ex) {
            return objectMapper.createObjectNode();
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String generateId(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
