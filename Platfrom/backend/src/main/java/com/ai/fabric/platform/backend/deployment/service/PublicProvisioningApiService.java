package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.entity.PublicApiDeploymentEntity;
import com.ai.fabric.platform.backend.deployment.model.CreateDeploymentRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentLifecycleSnapshotSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentIntegrationSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentDraftResponse;
import com.ai.fabric.platform.backend.deployment.model.DeploymentOverviewSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentReleaseSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentVersionSummary;
import com.ai.fabric.platform.backend.deployment.model.PublicApplyDeploymentRequest;
import com.ai.fabric.platform.backend.deployment.model.PublicApplyDeploymentResponse;
import com.ai.fabric.platform.backend.deployment.model.PublicConsumerDeploymentCredentialsResponse;
import com.ai.fabric.platform.backend.deployment.model.PublicConsumerDeploymentStatusResponse;
import com.ai.fabric.platform.backend.deployment.model.PublicConsumerDeploymentSummary;
import com.ai.fabric.platform.backend.deployment.model.PublicConsumerRuntimeAssignmentResponse;
import com.ai.fabric.platform.backend.deployment.model.PublicDeploymentAccessSummary;
import com.ai.fabric.platform.backend.deployment.model.PublicCreateDeploymentRequest;
import com.ai.fabric.platform.backend.deployment.model.PublicDeploymentCredentialsResponse;
import com.ai.fabric.platform.backend.deployment.model.PublicDeploymentIntegrationSummary;
import com.ai.fabric.platform.backend.deployment.model.PublicIntegrationPathSummary;
import com.ai.fabric.platform.backend.deployment.model.PublicDeploymentStatusResponse;
import com.ai.fabric.platform.backend.deployment.model.PublicDeploymentSummary;
import com.ai.fabric.platform.backend.deployment.model.PublicRuntimeEndpointsSummary;
import com.ai.fabric.platform.backend.deployment.model.PublicRuntimePostureSummary;
import com.ai.fabric.platform.backend.deployment.model.PublicRuntimeTokenSummary;
import com.ai.fabric.platform.backend.deployment.model.PublicTrustedBackendAccessSummary;
import com.ai.fabric.platform.backend.deployment.repository.PublicApiDeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.PlatformSecurityContext;
import com.ai.fabric.platform.backend.tenant.service.PlatformCustomerConsumerService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class PublicProvisioningApiService {

    private static final String RUNTIME_TRUSTED_BACKEND_SECRET_NAME = "AI_FABRIC_RUNTIME_TRUSTED_BACKEND_API_KEY";
    private static final String RUNTIME_PRIVATE_ASSERTION_SIGNING_KEY_SECRET_NAME = "AI_FABRIC_RUNTIME_PRIVATE_ASSERTION_SIGNING_KEY";
    private static final String RUNTIME_PUBLIC_TOKEN_SIGNING_KEY_SECRET_NAME = "AI_FABRIC_RUNTIME_PUBLIC_TOKEN_SIGNING_KEY";
    private static final String RUNTIME_TRUSTED_BACKEND_HEADER = "X-AIFABRIC-RUNTIME-API-KEY";
    private static final String RUNTIME_PRIVATE_AUTHORIZATION_HEADER = "X-AIFABRIC-RUNTIME-AUTHORIZATION";
    private static final String RUNTIME_PRIVATE_TOKEN_SCHEME = "Bearer";
    private static final String RUNTIME_PUBLIC_AUTHORIZATION_HEADER = "Authorization";
    private static final String RUNTIME_PUBLIC_TOKEN_SCHEME = "Bearer";
    private static final String AUTH_CONFIGURATION_REQUIRED = "AUTH_CONFIGURATION_REQUIRED";
    private static final String VERIFIED_AUTH_CONTEXT_PATH = "/api/chat/me/auth-context";
    private static final String DEFAULT_PRIVATE_RUNTIME_ISSUER = "platform-consumer-bridge";
    private static final int RUNTIME_ASSIGNMENT_CACHE_TTL_SECONDS = 300;

    private final PublicApiDeploymentRepository publicApiDeploymentRepository;
    private final DeploymentService deploymentService;
    private final DeploymentVersionRepository deploymentVersionRepository;
    private final PlatformAuditService platformAuditService;
    private final PlatformSecretService platformSecretService;
    private final PlatformCustomerConsumerService platformCustomerConsumerService;
    private final ObjectMapper objectMapper;

    public PublicProvisioningApiService(PublicApiDeploymentRepository publicApiDeploymentRepository,
                                        DeploymentService deploymentService,
                                        DeploymentVersionRepository deploymentVersionRepository,
                                        PlatformAuditService platformAuditService,
                                        PlatformSecretService platformSecretService,
                                        PlatformCustomerConsumerService platformCustomerConsumerService,
                                        ObjectMapper objectMapper) {
        this.publicApiDeploymentRepository = publicApiDeploymentRepository;
        this.deploymentService = deploymentService;
        this.deploymentVersionRepository = deploymentVersionRepository;
        this.platformAuditService = platformAuditService;
        this.platformSecretService = platformSecretService;
        this.platformCustomerConsumerService = platformCustomerConsumerService;
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

        DeploymentReleaseSummary release = deploymentService.applyVersionForVerifiedPublicApiBinding(
            binding.getDeploymentId(),
            version.id()
        );
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
        PublicDeploymentAccessSummary access = accessSummary(overview, latestPublishedSecurityConfig(binding.getDeploymentId()));
        return new PublicDeploymentCredentialsResponse(
            binding.getClientId(),
            binding.getExternalDeploymentKey(),
            binding.getDeploymentId(),
            overview.runtimeBaseUrl(),
            access,
            integrationSummary(access)
        );
    }

    public PublicConsumerDeploymentSummary getConsumerDeployment(String consumerId) {
        PlatformCustomerConsumerService.ResolvedPublicConsumer resolved = platformCustomerConsumerService.resolvePublicConsumer(consumerId);
        DeploymentOverviewSummary overview = overviewForResolvedConsumer(resolved);
        return toPublicConsumerSummary(resolved.consumer().getConsumerId(), overview);
    }

    public PublicConsumerDeploymentStatusResponse getConsumerDeploymentStatus(String consumerId) {
        PlatformCustomerConsumerService.ResolvedPublicConsumer resolved = platformCustomerConsumerService.resolvePublicConsumer(consumerId);
        DeploymentOverviewSummary overview = overviewForResolvedConsumer(resolved);
        return toPublicConsumerStatusResponse(resolved.consumer().getConsumerId(), overview);
    }

    public PublicConsumerDeploymentCredentialsResponse getConsumerDeploymentCredentials(String consumerId) {
        PlatformCustomerConsumerService.ResolvedPublicConsumer resolved = platformCustomerConsumerService.resolvePublicConsumer(consumerId);
        DeploymentOverviewSummary overview = overviewForResolvedConsumer(resolved);
        return toPublicConsumerCredentialsResponse(resolved.consumer().getConsumerId(), overview);
    }

    public PublicConsumerRuntimeAssignmentResponse getConsumerRuntimeAssignment(String consumerId) {
        PlatformCustomerConsumerService.ResolvedPublicConsumer resolved = platformCustomerConsumerService.resolvePublicConsumer(consumerId);
        DeploymentOverviewSummary overview = overviewForResolvedConsumer(resolved);
        com.fasterxml.jackson.databind.JsonNode securityConfig = latestPublishedSecurityConfig(overview.id());
        PublicDeploymentAccessSummary access = accessSummary(overview, securityConfig);
        PublicDeploymentIntegrationSummary integration = integrationSummary(access);
        String issuer = preferredPrivateRuntimeIssuer(securityConfig);
        String audience = preferredPrivateRuntimeAudience(resolved.consumer().getConsumerId());
        String audienceMode = "CONSUMER_ID";
        boolean externalIntegrationReady = access.trustedBackend().externalIntegrationReady()
            && csvValues(ManagedDeploymentProfileCatalog.privateRuntimeAcceptedIssuers(securityConfig)).contains(issuer)
            && csvValues(ManagedDeploymentProfileCatalog.privateRuntimeAcceptedAudiences(securityConfig)).contains(audience);
        String revision = assignmentRevision(
            resolved.consumer().getConsumerId(),
            overview.id(),
            overview.runtimeBaseUrl(),
            integration.preferredIntegrationMode(),
            access.posture().runtimeAuthMode(),
            issuer,
            audience
        );
        return new PublicConsumerRuntimeAssignmentResponse(
            resolved.consumer().getConsumerId(),
            overview.id(),
            overview.runtimeBaseUrl(),
            access.posture().runtimeAuthMode(),
            integration.preferredIntegrationMode(),
            issuer,
            audience,
            audienceMode,
            access.trustedBackend().authorizationHeader(),
            access.trustedBackend().assertionAuthorizationHeader(),
            access.trustedBackend().assertionTokenScheme(),
            externalIntegrationReady,
            revision,
            RUNTIME_ASSIGNMENT_CACHE_TTL_SECONDS,
            access.runtime(),
            integration.guidance()
        );
    }

    private DeploymentOverviewSummary overviewForResolvedConsumer(PlatformCustomerConsumerService.ResolvedPublicConsumer resolved) {
        DeploymentOverviewSummary overview = deploymentService.getDeploymentOverviewForExternalResolution(resolved.deployment().getId());
        if (resolved.release() == null) {
            return overview;
        }
        String releaseRuntimeBaseUrl = releaseRuntimeBaseUrl(resolved.release());
        if (releaseRuntimeBaseUrl == null || releaseRuntimeBaseUrl.isBlank()) {
            throw new ResponseStatusException(
                CONFLICT,
                "Consumer release binding does not include a runtime URL."
            );
        }
        return new DeploymentOverviewSummary(
            overview.id(),
            overview.name(),
            overview.environment(),
            overview.templateId(),
            overview.binding(),
            overview.source(),
            overview.access(),
            overview.status(),
            overview.activeVersion(),
            overview.healthStatus(),
            overview.healthSummary(),
            releaseRuntimeBaseUrl,
            releaseConnectorProvisioned(resolved.release()),
            overview.approvalRequiredForApply(),
            overview.approvalRequiredForDelete(),
            releaseLifecycleSnapshot(resolved.release()),
            overview.latestVerification(),
            overview.deletion(),
            overview.archivedAt(),
            overview.createdAt(),
            overview.updatedAt()
        );
    }

    private DeploymentLifecycleSnapshotSummary releaseLifecycleSnapshot(DeploymentReleaseEntity release) {
        return new DeploymentLifecycleSnapshotSummary(
            release.getId(),
            release.getDeploymentVersionId(),
            release.getStatus(),
            release.getProvisioningStatus(),
            release.getVerificationStatus(),
            release.getCurrentStepKey(),
            release.getCurrentStepDescription(),
            release.getUpdatedAt()
        );
    }

    private String releaseRuntimeBaseUrl(DeploymentReleaseEntity release) {
        JsonNode details = readJson(release.getProvisioningDetailsJson());
        return normalizeRuntimeBaseUrl(firstNonBlank(
            details.path("runtimeBaseUrl").asText(null),
            details.path("runtime").path("baseUrl").asText(null),
            details.path("railway").path("services").path("runtime").path("baseUrl").asText(null),
            details.path("runtimeFqdn").asText(null),
            details.path("fqdn").asText(null)
        ));
    }

    private boolean releaseConnectorProvisioned(DeploymentReleaseEntity release) {
        JsonNode details = readJson(release.getProvisioningDetailsJson());
        return firstNonBlank(
            details.path("connectorBaseUrl").asText(null),
            details.path("restConnector").path("baseUrl").asText(null),
            details.path("railway").path("services").path("restConnector").path("baseUrl").asText(null),
            details.path("connectorFqdn").asText(null)
        ) != null;
    }

    public DeploymentIntegrationSummary getInternalIntegrationSummary(String deploymentId) {
        DeploymentOverviewSummary overview = deploymentService.getDeploymentOverview(deploymentId);
        return internalIntegrationSummary(accessSummary(overview, latestPublishedSecurityConfig(deploymentId)));
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

    private DeploymentVersionSummary findLatestVersionForExternalResolution(String deploymentId) {
        return deploymentVersionRepository.findByDeploymentIdOrderByPublishedAtDesc(deploymentId).stream()
            .findFirst()
            .map(this::toVersionSummary)
            .orElse(null);
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

    private DeploymentVersionSummary toVersionSummary(DeploymentVersionEntity version) {
        return new DeploymentVersionSummary(
            version.getId(),
            version.getDeploymentId(),
            version.getSourceDraftId(),
            version.getVersionLabel(),
            version.getStatus(),
            version.getConfigHash(),
            version.isReindexRequired(),
            version.getPublishedAt()
        );
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
            access,
            integrationSummary(access),
            overview.latestRelease(),
            overview.latestVerification(),
            overview.createdAt(),
            overview.updatedAt()
        );
    }

    private PublicConsumerDeploymentSummary toPublicConsumerSummary(String consumerId,
                                                                    DeploymentOverviewSummary overview) {
        DeploymentVersionSummary latestVersion = findLatestVersionForExternalResolution(overview.id());
        PublicDeploymentAccessSummary access = accessSummary(overview, latestPublishedSecurityConfig(overview.id()));
        return new PublicConsumerDeploymentSummary(
            consumerId,
            overview.id(),
            overview.name(),
            overview.environment(),
            overview.templateId(),
            overview.status(),
            overview.activeVersion(),
            latestVersion == null ? null : latestVersion.id(),
            latestVersion == null ? null : latestVersion.versionLabel(),
            overview.runtimeBaseUrl(),
            access,
            integrationSummary(access),
            overview.latestRelease(),
            overview.latestVerification(),
            overview.createdAt(),
            overview.updatedAt()
        );
    }

    private PublicConsumerDeploymentStatusResponse toPublicConsumerStatusResponse(String consumerId,
                                                                                  DeploymentOverviewSummary overview) {
        DeploymentVersionSummary latestVersion = findLatestVersionForExternalResolution(overview.id());
        PublicDeploymentAccessSummary access = accessSummary(overview, latestPublishedSecurityConfig(overview.id()));
        return new PublicConsumerDeploymentStatusResponse(
            consumerId,
            overview.id(),
            overview.status(),
            overview.healthStatus(),
            overview.healthSummary(),
            overview.activeVersion(),
            latestVersion == null ? null : latestVersion.id(),
            latestVersion == null ? null : latestVersion.versionLabel(),
            overview.runtimeBaseUrl(),
            access,
            integrationSummary(access),
            overview.latestRelease(),
            overview.latestVerification(),
            overview.createdAt(),
            overview.updatedAt()
        );
    }

    private PublicConsumerDeploymentCredentialsResponse toPublicConsumerCredentialsResponse(String consumerId,
                                                                                           DeploymentOverviewSummary overview) {
        PublicDeploymentAccessSummary access = accessSummary(overview, latestPublishedSecurityConfig(overview.id()));
        return new PublicConsumerDeploymentCredentialsResponse(
            consumerId,
            overview.id(),
            overview.runtimeBaseUrl(),
            access,
            integrationSummary(access)
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
        boolean connectorProvisioned = overview.connectorProvisioned();
        boolean trustedBackendConfigured = platformSecretService.isSecretPresent(RUNTIME_TRUSTED_BACKEND_SECRET_NAME);
        boolean privateAssertionConfigured = platformSecretService.isSecretPresent(RUNTIME_PRIVATE_ASSERTION_SIGNING_KEY_SECRET_NAME);
        String privateRuntimeAcceptedIssuers =
            ManagedDeploymentProfileCatalog.effectivePrivateRuntimeAcceptedIssuers(securityConfig);
        String privateRuntimeAcceptedAudiences =
            ManagedDeploymentProfileCatalog.effectivePrivateRuntimeAcceptedAudiences(securityConfig, overview.id());
        boolean trustedBackendAcceptedIssuerPolicyConfigured =
            runtimeBaseUrl != null && trustedBackendConfigured && !privateRuntimeAcceptedIssuers.isBlank();
        boolean trustedBackendAcceptedAudiencePolicyConfigured =
            runtimeBaseUrl != null && trustedBackendConfigured && !privateRuntimeAcceptedAudiences.isBlank();
        boolean trustedBackendPlatformDefaultIssuerPolicy =
            runtimeBaseUrl != null
                && trustedBackendConfigured
                && ManagedDeploymentProfileCatalog.privateRuntimeUsesPlatformDefaultIssuerPolicy(securityConfig);
        boolean externalTrustedBackendIntegrationReady =
            privateAssertionConfigured
                && trustedBackendConfigured
                && trustedBackendAcceptedIssuerPolicyConfigured
                && trustedBackendAcceptedAudiencePolicyConfigured
                && !trustedBackendPlatformDefaultIssuerPolicy;
        boolean privateRuntimeReady =
            trustedBackendAcceptedIssuerPolicyConfigured
                && trustedBackendAcceptedAudiencePolicyConfigured
                && trustedBackendConfigured
                && privateAssertionConfigured;
        boolean publicTokenRequested = ManagedDeploymentProfileCatalog.publicRuntimeRequested(securityConfig);
        boolean publicTokenConfigured = platformSecretService.isSecretPresent(RUNTIME_PUBLIC_TOKEN_SIGNING_KEY_SECRET_NAME)
            && publicTokenRequested;
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
        } else if (publicTokenConfigured) {
            runtimeAuthMode = "PUBLIC_RUNTIME_SIGNED_TOKEN";
            hostBackedRuntimeRequired = false;
            guidance = bootstrapEnabled
                ? "Runtime can validate signed public bearer tokens and anonymous bootstrap is enabled for this deployment. Keep browser use constrained to approved origins, short-lived tokens, and low-privilege anonymous scopes."
                : "Runtime can validate signed public bearer tokens. Anonymous runtime bootstrap is not enabled by default; issue short-lived tokens from a trusted issuer or explicitly opt deployments into bootstrap later.";
        } else if (privateRuntimeReady) {
            runtimeAuthMode = "PRIVATE_RUNTIME_SIGNED_ASSERTION";
            hostBackedRuntimeRequired = true;
            guidance = "Runtime is configured for signed private-runtime integration. Route customer traffic through your host or storefront backend; do not expose the connector directly.";
            if (trustedBackendPlatformDefaultIssuerPolicy) {
                guidance += " Private-runtime verification is active, but the accepted issuer policy still uses platform-managed defaults suited to first-party callers. Set deployment security privateRuntimeAcceptedIssuers before onboarding an external storefront or customer-owned backend.";
            } else if (!externalTrustedBackendIntegrationReady) {
                guidance += " Signed private-runtime verification is enabled, but issuer/audience allowlists are not fully configured yet. Complete deployment security privateRuntimeAcceptedIssuers/privateRuntimeAcceptedAudiences before treating this as an external production integration contract.";
            } else {
                guidance += " Signed private-runtime issuer/audience allowlists are explicitly configured for external host-backed integration.";
            }
        } else {
            runtimeAuthMode = AUTH_CONFIGURATION_REQUIRED;
            hostBackedRuntimeRequired = false;
            guidance = publicTokenRequested
                ? "Runtime public-token posture was requested in deployment security config, but the shared signing key is not configured. Do not expose browser-direct runtime access until signed public-token validation is fully configured."
                : "Runtime is reachable, but neither the full signed private-runtime contract nor signed public-token validation is configured. Do not integrate customers until one supported auth posture is configured.";
            if (trustedBackendConfigured && !privateAssertionConfigured) {
                guidance += " Trusted backend machine auth is present, but AI_FABRIC_RUNTIME_PRIVATE_ASSERTION_SIGNING_KEY is still missing.";
            }
        }
        boolean runtimePublicTokenValidationConfigured = runtimeBaseUrl != null && publicTokenConfigured;
        boolean anonymousBootstrapSupported = runtimePublicTokenValidationConfigured && bootstrapEnabled;
        PublicRuntimePostureSummary posture = new PublicRuntimePostureSummary(
            runtimeAuthMode,
            verifiedAuthContextRequired(runtimeAuthMode),
            hostBackedRuntimeRequired,
            false
        );
        PublicRuntimeEndpointsSummary runtime = runtimeEndpoints(runtimeBaseUrl);
        PublicTrustedBackendAccessSummary trustedBackend = new PublicTrustedBackendAccessSummary(
            runtimeBaseUrl != null && trustedBackendConfigured,
            runtimeBaseUrl != null && trustedBackendConfigured ? RUNTIME_TRUSTED_BACKEND_HEADER : null,
            runtimeBaseUrl != null && privateAssertionConfigured,
            runtimeBaseUrl != null && privateAssertionConfigured ? RUNTIME_PRIVATE_AUTHORIZATION_HEADER : null,
            runtimeBaseUrl != null && privateAssertionConfigured ? RUNTIME_PRIVATE_TOKEN_SCHEME : null,
            trustedBackendAcceptedIssuerPolicyConfigured,
            trustedBackendAcceptedAudiencePolicyConfigured,
            trustedBackendPlatformDefaultIssuerPolicy,
            externalTrustedBackendIntegrationReady
        );
        PublicRuntimeTokenSummary publicRuntime = new PublicRuntimeTokenSummary(
            runtimePublicTokenValidationConfigured,
            anonymousBootstrapSupported,
            anonymousBootstrapSupported ? runtimeBaseUrl + "/api/public/chat/session" : null,
            runtimePublicTokenValidationConfigured ? RUNTIME_PUBLIC_AUTHORIZATION_HEADER : null,
            runtimePublicTokenValidationConfigured ? RUNTIME_PUBLIC_TOKEN_SCHEME : null,
            runtimePublicTokenValidationConfigured && !publicRuntimeAcceptedIssuers.isBlank(),
            runtimePublicTokenValidationConfigured && !publicRuntimeAcceptedAudiences.isBlank(),
            runtimePublicTokenValidationConfigured ? blankToNull(publicRuntimeTokenIssuer) : null,
            runtimePublicTokenValidationConfigured ? blankToNull(publicRuntimeDefaultAudience) : null
        );
        return new PublicDeploymentAccessSummary(
            runtimeBaseUrl == null ? "NOT_APPLIED" : "RUNTIME_ENTRYPOINT",
            connectorProvisioned ? "PRIVATE_INTERNAL_SERVICE" : "NOT_APPLIED",
            posture,
            runtime,
            trustedBackend,
            publicRuntime,
            !connectorProvisioned
                ? guidance + " Customer-facing business CRUD routes such as cart or order APIs remain host-owned unless a dedicated runtime-backed CRUD surface is explicitly published."
                : guidance + " The public API intentionally does not expose the internal connector URL; treat the connector as an internal service surface only. Customer-facing business CRUD routes such as cart or order APIs remain host-owned unless a dedicated runtime-backed CRUD surface is explicitly published."
        );
    }

    private PublicDeploymentIntegrationSummary integrationSummary(PublicDeploymentAccessSummary access) {
        if (access == null) {
            return new PublicDeploymentIntegrationSummary(
                "NOT_APPLIED",
                emptyPosture(),
                emptyRuntimeEndpoints(),
                emptyTrustedBackend(),
                emptyPublicRuntime(),
                emptyIntegrationPaths(),
                "Apply the deployment before integrating."
            );
        }
        String preferredIntegrationMode = preferredIntegrationMode(access);
        boolean browserDirectRuntimeAccessSupported =
            "PUBLIC_RUNTIME_BROWSER_TOKEN".equals(preferredIntegrationMode);
        String chatBaseUrl = blankToNull(access.runtime().chatBaseUrl());
        String crudBaseUrl = blankToNull(access.runtime().crudBaseUrl());
        String browserDirectChatBaseUrl = browserDirectRuntimeAccessSupported ? chatBaseUrl : null;
        String browserDirectCrudBaseUrl = browserDirectRuntimeAccessSupported ? crudBaseUrl : null;
        String backendMediatedRuntimeBaseUrl = "BACKEND_MEDIATED_PRIVATE_RUNTIME".equals(preferredIntegrationMode)
            ? chatBaseUrl
            : null;
        return new PublicDeploymentIntegrationSummary(
            preferredIntegrationMode,
            access.posture(),
            access.runtime(),
            access.trustedBackend(),
            access.publicRuntime(),
            new PublicIntegrationPathSummary(
                !access.posture().directConnectorAccessSupported(),
                browserDirectRuntimeAccessSupported,
                browserDirectChatBaseUrl,
                browserDirectCrudBaseUrl,
                backendMediatedRuntimeBaseUrl
            ),
            blankToNull(access.guidance())
        );
    }

    private DeploymentIntegrationSummary internalIntegrationSummary(PublicDeploymentAccessSummary access) {
        if (access == null) {
            return new DeploymentIntegrationSummary(
                "NOT_APPLIED",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                null,
                false,
                null,
                null,
                false,
                false,
                false,
                false,
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
                false,
                false,
                false,
                false,
                null,
                null,
                null,
                "Apply the deployment before integrating."
            );
        }
        String preferredIntegrationMode = preferredIntegrationMode(access);
        boolean browserDirectRuntimeAccessSupported =
            "PUBLIC_RUNTIME_BROWSER_TOKEN".equals(preferredIntegrationMode);
        String chatBaseUrl = blankToNull(access.runtime().chatBaseUrl());
        String crudBaseUrl = blankToNull(access.runtime().crudBaseUrl());
        String browserDirectChatBaseUrl = browserDirectRuntimeAccessSupported ? chatBaseUrl : null;
        String browserDirectCrudBaseUrl = browserDirectRuntimeAccessSupported ? crudBaseUrl : null;
        String backendMediatedRuntimeBaseUrl = "BACKEND_MEDIATED_PRIVATE_RUNTIME".equals(preferredIntegrationMode)
            ? chatBaseUrl
            : null;
        String runtimeBaseUrl = chatBaseUrl;
        return new DeploymentIntegrationSummary(
            preferredIntegrationMode,
            chatBaseUrl,
            crudBaseUrl,
            blankToNull(access.runtime().chatQueryUrl()),
            blankToNull(access.runtime().suggestionsUrl()),
            blankToNull(access.runtime().conversationsUrl()),
            blankToNull(access.runtime().conversationItemUrlTemplate()),
            blankToNull(access.runtime().operationalBaseUrl()),
            preferredConnectorOverviewUrl(runtimeBaseUrl),
            preferredConnectorHealthUrl(runtimeBaseUrl),
            preferredConnectorActionsOverviewUrl(runtimeBaseUrl),
            preferredConnectorConfigUrl(runtimeBaseUrl),
            preferredConnectorLogsUrl(runtimeBaseUrl),
            blankToNull(access.runtime().authContextUrl()),
            blankToNull(access.runtime().authOverviewUrl()),
            access.posture().verifiedAuthContextRequired(),
            blankToNull(access.trustedBackend().authorizationHeader()),
            access.trustedBackend().assertionValidationConfigured(),
            blankToNull(access.trustedBackend().assertionAuthorizationHeader()),
            blankToNull(access.trustedBackend().assertionTokenScheme()),
            access.trustedBackend().acceptedIssuerPolicyConfigured(),
            access.trustedBackend().acceptedAudiencePolicyConfigured(),
            access.trustedBackend().platformDefaultIssuerPolicy(),
            access.trustedBackend().externalIntegrationReady(),
            blankToNull(access.publicRuntime().bootstrapUrl()),
            blankToNull(access.publicRuntime().authorizationHeader()),
            blankToNull(access.publicRuntime().tokenScheme()),
            blankToNull(access.publicRuntime().tokenIssuerHint()),
            blankToNull(access.publicRuntime().defaultAudience()),
            blankToNull(access.posture().runtimeAuthMode()),
            access.posture().hostBackedRuntimeRequired(),
            !access.posture().directConnectorAccessSupported(),
            access.trustedBackend().callerAuthConfigured(),
            access.publicRuntime().tokenValidationConfigured(),
            access.publicRuntime().anonymousBootstrapSupported(),
            access.publicRuntime().acceptedIssuerPolicyConfigured(),
            access.publicRuntime().acceptedAudiencePolicyConfigured(),
            browserDirectRuntimeAccessSupported,
            browserDirectChatBaseUrl,
            browserDirectCrudBaseUrl,
            backendMediatedRuntimeBaseUrl,
            blankToNull(access.guidance())
        );
    }

    private PublicRuntimeEndpointsSummary runtimeEndpoints(String runtimeBaseUrl) {
        return new PublicRuntimeEndpointsSummary(
            blankToNull(runtimeBaseUrl),
            null,
            preferredChatQueryUrl(runtimeBaseUrl),
            preferredQueryOnceUrl(runtimeBaseUrl),
            preferredSuggestionsUrl(runtimeBaseUrl),
            preferredConversationsUrl(runtimeBaseUrl),
            preferredConversationItemUrlTemplate(runtimeBaseUrl),
            blankToNull(runtimeBaseUrl),
            preferredRuntimeHealthUrl(runtimeBaseUrl),
            preferredAuthContextUrl(runtimeBaseUrl),
            preferredAuthOverviewUrl(runtimeBaseUrl)
        );
    }

    private PublicRuntimePostureSummary emptyPosture() {
        return new PublicRuntimePostureSummary("NOT_APPLIED", false, false, false);
    }

    private PublicRuntimeEndpointsSummary emptyRuntimeEndpoints() {
        return new PublicRuntimeEndpointsSummary(null, null, null, null, null, null, null, null, null, null, null);
    }

    private PublicTrustedBackendAccessSummary emptyTrustedBackend() {
        return new PublicTrustedBackendAccessSummary(false, null, false, null, null, false, false, false, false);
    }

    private PublicRuntimeTokenSummary emptyPublicRuntime() {
        return new PublicRuntimeTokenSummary(false, false, null, null, null, false, false, null, null);
    }

    private PublicIntegrationPathSummary emptyIntegrationPaths() {
        return new PublicIntegrationPathSummary(true, false, null, null, null);
    }

    private String preferredChatQueryUrl(String runtimeBaseUrl) {
        String baseUrl = blankToNull(runtimeBaseUrl);
        if (baseUrl == null) {
            return null;
        }
        return baseUrl + "/api/chat/me/query";
    }

    private String preferredSuggestionsUrl(String runtimeBaseUrl) {
        String baseUrl = blankToNull(runtimeBaseUrl);
        if (baseUrl == null) {
            return null;
        }
        return baseUrl + "/api/chat/me/suggestions";
    }

    private String preferredQueryOnceUrl(String runtimeBaseUrl) {
        String baseUrl = blankToNull(runtimeBaseUrl);
        if (baseUrl == null) {
            return null;
        }
        return baseUrl + "/api/chat/me/query-once";
    }

    private String preferredConversationsUrl(String runtimeBaseUrl) {
        String baseUrl = blankToNull(runtimeBaseUrl);
        if (baseUrl == null) {
            return null;
        }
        return baseUrl + "/api/chat/me/conversations";
    }

    private String preferredConversationItemUrlTemplate(String runtimeBaseUrl) {
        String baseUrl = blankToNull(runtimeBaseUrl);
        if (baseUrl == null) {
            return null;
        }
        return baseUrl + "/api/chat/me/conversations/{conversationId}";
    }

    private String preferredAuthContextUrl(String runtimeBaseUrl) {
        String baseUrl = blankToNull(runtimeBaseUrl);
        if (baseUrl == null) {
            return null;
        }
        return baseUrl + VERIFIED_AUTH_CONTEXT_PATH;
    }

    private String preferredAuthOverviewUrl(String runtimeBaseUrl) {
        String baseUrl = blankToNull(runtimeBaseUrl);
        if (baseUrl == null) {
            return null;
        }
        return baseUrl + "/api/admin/auth/overview";
    }

    private String preferredRuntimeHealthUrl(String runtimeBaseUrl) {
        String baseUrl = blankToNull(runtimeBaseUrl);
        if (baseUrl == null) {
            return null;
        }
        return baseUrl + "/actuator/health";
    }

    private String preferredConnectorOverviewUrl(String runtimeBaseUrl) {
        return runtimeBackedUrl(runtimeBaseUrl, "/api/admin/connector/overview");
    }

    private String preferredConnectorHealthUrl(String runtimeBaseUrl) {
        return runtimeBackedUrl(runtimeBaseUrl, "/api/admin/connector/health");
    }

    private String preferredConnectorActionsOverviewUrl(String runtimeBaseUrl) {
        return runtimeBackedUrl(runtimeBaseUrl, "/api/admin/connector/actions/overview");
    }

    private String preferredConnectorConfigUrl(String runtimeBaseUrl) {
        return runtimeBackedUrl(runtimeBaseUrl, "/api/admin/connector/config");
    }

    private String preferredConnectorLogsUrl(String runtimeBaseUrl) {
        return runtimeBackedUrl(runtimeBaseUrl, "/api/admin/connector/logs");
    }

    private String runtimeBackedUrl(String runtimeBaseUrl, String path) {
        String runtime = blankToNull(runtimeBaseUrl);
        if (runtime == null) {
            return null;
        }
        return runtime + path;
    }

    private boolean verifiedAuthContextRequired(String runtimeAuthMode) {
        return !"NOT_APPLIED".equals(runtimeAuthMode);
    }

    private String preferredIntegrationMode(PublicDeploymentAccessSummary access) {
        if (access == null || access.runtime() == null || blankToNull(access.runtime().chatBaseUrl()) == null) {
            return "NOT_APPLIED";
        }
        if (AUTH_CONFIGURATION_REQUIRED.equals(access.posture().runtimeAuthMode())) {
            return AUTH_CONFIGURATION_REQUIRED;
        }
        if (access.posture().hostBackedRuntimeRequired()) {
            return "BACKEND_MEDIATED_PRIVATE_RUNTIME";
        }
        if (access.publicRuntime().tokenValidationConfigured()) {
            return "PUBLIC_RUNTIME_BROWSER_TOKEN";
        }
        return AUTH_CONFIGURATION_REQUIRED;
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
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String normalized = blankToNull(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private String normalizeRuntimeBaseUrl(String value) {
        String normalized = blankToNull(value);
        if (normalized == null) {
            return null;
        }
        if (normalized.startsWith("http://") || normalized.startsWith("https://")) {
            return normalized;
        }
        return "https://" + normalized;
    }

    private String preferredPrivateRuntimeIssuer(com.fasterxml.jackson.databind.JsonNode securityConfig) {
        List<String> configuredIssuers = csvValues(ManagedDeploymentProfileCatalog.privateRuntimeAcceptedIssuers(securityConfig));
        if (configuredIssuers.contains(DEFAULT_PRIVATE_RUNTIME_ISSUER)) {
            return DEFAULT_PRIVATE_RUNTIME_ISSUER;
        }
        if (!configuredIssuers.isEmpty()) {
            return configuredIssuers.get(0);
        }
        return DEFAULT_PRIVATE_RUNTIME_ISSUER;
    }

    private String preferredPrivateRuntimeAudience(String consumerId) {
        return consumerId;
    }

    private List<String> csvValues(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .distinct()
            .toList();
    }

    private String assignmentRevision(String consumerId,
                                      String deploymentId,
                                      String runtimeBaseUrl,
                                      String preferredIntegrationMode,
                                      String runtimeAuthMode,
                                      String issuer,
                                      String audience) {
        String material = String.join("|",
            blankToEmpty(consumerId),
            blankToEmpty(deploymentId),
            blankToEmpty(runtimeBaseUrl),
            blankToEmpty(preferredIntegrationMode),
            blankToEmpty(runtimeAuthMode),
            blankToEmpty(issuer),
            blankToEmpty(audience)
        );
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest).substring(0, 22);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to calculate runtime assignment revision.", ex);
        }
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String generateId(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }
}
