package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentProviderResourceHandleEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentSourceArtifactEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentTargetProfileEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderPreflightSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderResourceActionSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderResourceLogsSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderResourceStatusSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentProviderResourceHandleRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class CoolifyDeploymentProvider implements DeploymentProvisioningProvider {

    private static final String RESOURCE_KIND_APPLICATION = "APPLICATION";
    private static final String DEFAULT_SERVICE_NAME = "ai-fabric-runtime";
    private static final String DEFAULT_PROMOTION_CHANNEL = "staging";

    private final DeploymentTargetProfileRepository targetProfileRepository;
    private final DeploymentProviderResourceHandleRepository resourceHandleRepository;
    private final DeploymentSourceArtifactService sourceArtifactService;
    private final CoolifyTargetProfileResolver targetProfileResolver;
    private final CoolifyApiClient coolifyApiClient;
    private final ObjectMapper objectMapper;

    public CoolifyDeploymentProvider(DeploymentTargetProfileRepository targetProfileRepository,
                                     DeploymentProviderResourceHandleRepository resourceHandleRepository,
                                     DeploymentSourceArtifactService sourceArtifactService,
                                     CoolifyTargetProfileResolver targetProfileResolver,
                                     CoolifyApiClient coolifyApiClient,
                                     ObjectMapper objectMapper) {
        this.targetProfileRepository = targetProfileRepository;
        this.resourceHandleRepository = resourceHandleRepository;
        this.sourceArtifactService = sourceArtifactService;
        this.targetProfileResolver = targetProfileResolver;
        this.coolifyApiClient = coolifyApiClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public DeploymentProviderType providerType() {
        return DeploymentProviderType.COOLIFY;
    }

    @Override
    public DeploymentProviderPreflightSummary preflight(DeploymentTargetProfileEntity targetProfile) {
        return targetProfileResolver.preflight(targetProfile);
    }

    @Override
    public ProvisioningResult provision(DeploymentEntity deployment,
                                        DeploymentVersionEntity version,
                                        DeploymentReleaseEntity release,
                                        ProvisioningProgressTracker progressTracker) {
        DeploymentTargetProfileEntity profile = requireActiveProfile(release.getTargetProfileId());
        CoolifyConnection connection = tracked(
            progressTracker,
            "coolify_preflight",
            "Verify Coolify target profile credentials before application reconciliation.",
            () -> {
                CoolifyConnection resolved = targetProfileResolver.requireConnection(profile);
                coolifyApiClient.health(resolved);
                return resolved;
            }
        );
        JsonNode resourceDefaults = readJson(profile.getResourceDefaultsJson());
        DeploymentSourceArtifactEntity sourceArtifact = tracked(
            progressTracker,
            "resolve_image_artifact",
            "Resolve immutable Docker image artifact for Coolify deployment.",
            () -> resolveSourceArtifact(release, resourceDefaults)
        );

        String appName = resolveApplicationName(deployment, resourceDefaults);
        String portsExposes = text(resourceDefaults, "portsExposes", connection.config().defaultPortsExposes());
        String healthCheckPath = text(resourceDefaults, "healthCheckPath", connection.config().defaultHealthCheckPath());
        String healthCheckPort = text(resourceDefaults, "healthCheckPort", connection.config().defaultHealthCheckPort());
        boolean healthCheckEnabled = booleanValue(resourceDefaults, "healthCheckEnabled", StringUtils.hasText(healthCheckPath));
        boolean autogenerateDomain = booleanValue(resourceDefaults, "autogenerateDomain", connection.config().autogenerateDomain());
        String domain = resolveDomain(deployment, resourceDefaults, connection.config(), autogenerateDomain);

        CoolifyCreateDockerImageApplicationRequest applicationRequest = new CoolifyCreateDockerImageApplicationRequest(
            connection.config().projectUuid(),
            connection.config().serverUuid(),
            connection.config().environmentName(),
            connection.config().environmentUuid(),
            sourceArtifact.getImageRepository(),
            sourceArtifact.getImageTag(),
            portsExposes,
            connection.config().destinationUuid(),
            appName,
            "Managed by AI Fabric deployment " + deployment.getId(),
            domain,
            healthCheckEnabled,
            healthCheckPath,
            healthCheckPort,
            false,
            connection.config().forceHttps(),
            autogenerateDomain
        );

        CoolifyApplicationSummary application = tracked(
            progressTracker,
            "reconcile_coolify_application",
            "Create or update the Coolify Docker-image application.",
            () -> reconcileApplication(connection, deployment, profile, appName, applicationRequest)
        );

        int envCount = tracked(
            progressTracker,
            "configure_coolify_environment",
            "Update non-secret runtime metadata environment variables in Coolify.",
            () -> coolifyApiClient.updateEnvironmentVariables(
                connection,
                application.uuid(),
                buildEnvironment(deployment, version, release, profile, sourceArtifact)
            )
        );

        CoolifyActionResponse deployResponse = tracked(
            progressTracker,
            "trigger_coolify_deploy",
            "Trigger Coolify deployment for the reconciled application.",
            () -> coolifyApiClient.start(connection, application.uuid(), true, true)
        );

        CoolifyApplicationSummary observed = coolifyApiClient.getApplication(connection, application.uuid()).orElse(application);
        DeploymentProviderResourceHandleEntity handle = tracked(
            progressTracker,
            "record_coolify_handle",
            "Persist Coolify application resource handle for operator actions.",
            () -> upsertHandle(deployment, release, profile, connection.config(), observed, sourceArtifact, envCount, deployResponse)
        );
        progressTracker.mergeDetails(buildProvisioningDetails(profile, handle, observed, sourceArtifact, envCount, deployResponse));

        String runtimeBaseUrl = normalizeRuntimeBaseUrl(observed.fqdn());
        return new ProvisioningResult(
            "DEPLOY_REQUESTED",
            DeploymentProviderType.COOLIFY.legacyTarget(),
            runtimeBaseUrl,
            runtimeBaseUrl,
            buildProvisioningDetails(profile, handle, observed, sourceArtifact, envCount, deployResponse)
        );
    }

    @Override
    public DeploymentProviderResourceActionSummary start(DeploymentProviderResourceHandleEntity handle, String reason) {
        CoolifyConnection connection = connectionForHandle(handle);
        CoolifyActionResponse response = coolifyApiClient.start(connection, handle.getProviderResourceUuid(), false, true);
        return actionSummary(handle, "START", "QUEUED", response.message(), response.deploymentUuid(), response.raw());
    }

    @Override
    public DeploymentProviderResourceActionSummary stop(DeploymentProviderResourceHandleEntity handle, String reason) {
        CoolifyConnection connection = connectionForHandle(handle);
        CoolifyActionResponse response = coolifyApiClient.stop(connection, handle.getProviderResourceUuid(), true);
        return actionSummary(handle, "STOP", "QUEUED", response.message(), response.deploymentUuid(), response.raw());
    }

    @Override
    public DeploymentProviderResourceActionSummary restart(DeploymentProviderResourceHandleEntity handle, String reason) {
        CoolifyConnection connection = connectionForHandle(handle);
        CoolifyActionResponse response = coolifyApiClient.restart(connection, handle.getProviderResourceUuid());
        return actionSummary(handle, "RESTART", "QUEUED", response.message(), response.deploymentUuid(), response.raw());
    }

    @Override
    public DeploymentProviderResourceActionSummary delete(DeploymentProviderResourceHandleEntity handle, String reason) {
        CoolifyConnection connection = connectionForHandle(handle);
        CoolifyActionResponse response = coolifyApiClient.delete(connection, handle.getProviderResourceUuid(), true, false, true, true);
        return actionSummary(handle, "DELETE", "QUEUED", response.message(), response.deploymentUuid(), response.raw());
    }

    @Override
    public DeploymentProviderResourceStatusSummary status(DeploymentProviderResourceHandleEntity handle) {
        CoolifyConnection connection = connectionForHandle(handle);
        CoolifyApplicationSummary application = coolifyApiClient.getApplication(connection, handle.getProviderResourceUuid())
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Coolify application not found: " + handle.getProviderResourceUuid()
            ));
        return new DeploymentProviderResourceStatusSummary(
            handle.getId(),
            handle.getProviderType(),
            handle.getProviderResourceUuid(),
            normalizeStatus(application.status(), "OBSERVED"),
            application.status(),
            application.fqdn(),
            application.raw(),
            Instant.now()
        );
    }

    @Override
    public DeploymentProviderResourceLogsSummary logs(DeploymentProviderResourceHandleEntity handle, int lines) {
        CoolifyConnection connection = connectionForHandle(handle);
        int normalizedLines = Math.max(1, Math.min(lines, 1000));
        return new DeploymentProviderResourceLogsSummary(
            handle.getId(),
            handle.getProviderType(),
            handle.getProviderResourceUuid(),
            normalizedLines,
            coolifyApiClient.logs(connection, handle.getProviderResourceUuid(), normalizedLines),
            Instant.now()
        );
    }

    private CoolifyApplicationSummary reconcileApplication(CoolifyConnection connection,
                                                          DeploymentEntity deployment,
                                                          DeploymentTargetProfileEntity profile,
                                                          String appName,
                                                          CoolifyCreateDockerImageApplicationRequest request) {
        DeploymentProviderResourceHandleEntity existingHandle = resourceHandleRepository
            .findFirstByDeploymentIdAndTargetProfileIdAndResourceKindOrderByUpdatedAtDesc(
                deployment.getId(),
                profile.getId(),
                RESOURCE_KIND_APPLICATION
            )
            .orElse(null);
        if (existingHandle != null) {
            String uuid = existingHandle.getProviderResourceUuid();
            coolifyApiClient.getApplication(connection, uuid).ifPresent(application -> {
                coolifyApiClient.updateDockerImageApplication(connection, uuid, request);
            });
            return coolifyApiClient.getApplication(connection, uuid)
                .orElseGet(() -> createApplication(connection, request));
        }

        CoolifyApplicationSummary namedApplication = coolifyApiClient.listApplications(connection).stream()
            .filter(application -> appName.equals(application.name()))
            .findFirst()
            .orElse(null);
        if (namedApplication != null) {
            coolifyApiClient.updateDockerImageApplication(connection, namedApplication.uuid(), request);
            return coolifyApiClient.getApplication(connection, namedApplication.uuid()).orElse(namedApplication);
        }
        return createApplication(connection, request);
    }

    private CoolifyApplicationSummary createApplication(CoolifyConnection connection,
                                                       CoolifyCreateDockerImageApplicationRequest request) {
        String uuid = coolifyApiClient.createDockerImageApplication(connection, request);
        return coolifyApiClient.getApplication(connection, uuid)
            .orElse(new CoolifyApplicationSummary(
                uuid,
                request.name(),
                request.domains(),
                "CREATED",
                request.imageRepository(),
                request.imageTag(),
                objectMapper.createObjectNode().put("uuid", uuid).put("name", request.name())
            ));
    }

    private DeploymentProviderResourceHandleEntity upsertHandle(DeploymentEntity deployment,
                                                               DeploymentReleaseEntity release,
                                                               DeploymentTargetProfileEntity profile,
                                                               CoolifyTargetProfileConfig config,
                                                               CoolifyApplicationSummary application,
                                                               DeploymentSourceArtifactEntity sourceArtifact,
                                                               int envCount,
                                                               CoolifyActionResponse deployResponse) {
        Instant now = Instant.now();
        DeploymentProviderResourceHandleEntity handle = resourceHandleRepository
            .findFirstByDeploymentIdAndTargetProfileIdAndResourceKindOrderByUpdatedAtDesc(
                deployment.getId(),
                profile.getId(),
                RESOURCE_KIND_APPLICATION
            )
            .orElseGet(() -> {
                DeploymentProviderResourceHandleEntity created = new DeploymentProviderResourceHandleEntity();
                created.setId("dprh-" + UUID.randomUUID().toString().substring(0, 8));
                created.setCreatedAt(now);
                return created;
            });
        handle.setDeploymentId(deployment.getId());
        handle.setReleaseId(release.getId());
        handle.setTargetProfileId(profile.getId());
        handle.setProviderType(DeploymentProviderType.COOLIFY);
        handle.setResourceKind(RESOURCE_KIND_APPLICATION);
        handle.setProviderResourceUuid(application.uuid());
        handle.setProviderProjectUuid(config.projectUuid());
        handle.setProviderEnvironmentUuid(config.environmentUuid());
        handle.setProviderServerUuid(config.serverUuid());
        handle.setFqdn(application.fqdn());
        handle.setStatus("DEPLOY_REQUESTED");
        handle.setLastObservedStatus(application.status());
        handle.setLastObservedAt(now);
        handle.setMetadataJson(handleMetadata(application, sourceArtifact, envCount, deployResponse));
        handle.setUpdatedAt(now);
        return resourceHandleRepository.save(handle);
    }

    private DeploymentSourceArtifactEntity resolveSourceArtifact(DeploymentReleaseEntity release, JsonNode resourceDefaults) {
        if (StringUtils.hasText(release.getSourceArtifactId())) {
            DeploymentSourceArtifactEntity artifact = sourceArtifactService.require(release.getSourceArtifactId());
            validateDockerImageArtifact(artifact);
            return artifact;
        }
        String serviceName = text(resourceDefaults, "serviceName", DEFAULT_SERVICE_NAME);
        String promotionChannel = text(resourceDefaults, "promotionChannel", DEFAULT_PROMOTION_CHANNEL);
        DeploymentSourceArtifactEntity artifact = sourceArtifactService.latestPromoted(serviceName, promotionChannel);
        validateDockerImageArtifact(artifact);
        return artifact;
    }

    private void validateDockerImageArtifact(DeploymentSourceArtifactEntity artifact) {
        if (!"DOCKER_IMAGE".equalsIgnoreCase(artifact.getArtifactType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Coolify source artifact must be DOCKER_IMAGE: " + artifact.getId());
        }
        if (!StringUtils.hasText(artifact.getImageRepository()) || !StringUtils.hasText(artifact.getImageTag())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Coolify source artifact must include imageRepository and imageTag: " + artifact.getId());
        }
    }

    private List<CoolifyEnvVar> buildEnvironment(DeploymentEntity deployment,
                                                 DeploymentVersionEntity version,
                                                 DeploymentReleaseEntity release,
                                                 DeploymentTargetProfileEntity profile,
                                                 DeploymentSourceArtifactEntity sourceArtifact) {
        List<CoolifyEnvVar> env = new ArrayList<>();
        env.add(env("PLATFORM_DEPLOYMENT_ID", deployment.getId()));
        env.add(env("PLATFORM_DEPLOYMENT_VERSION_ID", version.getId()));
        env.add(env("PLATFORM_DEPLOYMENT_RELEASE_ID", release.getId()));
        env.add(env("PLATFORM_TARGET_PROFILE_ID", profile.getId()));
        env.add(env("PLATFORM_SOURCE_ARTIFACT_ID", sourceArtifact.getId()));
        env.add(env("PLATFORM_ENVIRONMENT_NAME", profile.getEnvironmentName()));
        return env;
    }

    private CoolifyEnvVar env(String key, String value) {
        return new CoolifyEnvVar(key, value, false, true, false, false);
    }

    private String resolveApplicationName(DeploymentEntity deployment, JsonNode resourceDefaults) {
        String prefix = text(resourceDefaults, "applicationNamePrefix", "ai-fabric-runtime");
        return normalizeName(prefix + "-" + deployment.getId());
    }

    private String normalizeName(String value) {
        String normalized = value == null ? "ai-fabric-runtime" : value.toLowerCase(Locale.ROOT);
        normalized = normalized.replaceAll("[^a-z0-9-]+", "-").replaceAll("-{2,}", "-");
        normalized = normalized.replaceAll("^-+", "").replaceAll("-+$", "");
        return normalized.length() > 48 ? normalized.substring(0, 48).replaceAll("-+$", "") : normalized;
    }

    private String resolveDomain(DeploymentEntity deployment,
                                 JsonNode resourceDefaults,
                                 CoolifyTargetProfileConfig config,
                                 boolean autogenerateDomain) {
        if (autogenerateDomain) {
            return null;
        }
        String configured = text(resourceDefaults, "domain", null);
        if (StringUtils.hasText(configured)) {
            return configured;
        }
        String suffix = config.defaultPublicDomainSuffix();
        if (!StringUtils.hasText(suffix)) {
            return null;
        }
        return normalizeName(deployment.getId()) + "." + suffix;
    }

    private CoolifyConnection connectionForHandle(DeploymentProviderResourceHandleEntity handle) {
        DeploymentTargetProfileEntity profile = targetProfileRepository.findById(handle.getTargetProfileId())
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Deployment target profile not found: " + handle.getTargetProfileId()
            ));
        return targetProfileResolver.requireConnection(profile);
    }

    private DeploymentTargetProfileEntity requireActiveProfile(String targetProfileId) {
        DeploymentTargetProfileEntity profile = targetProfileRepository.findById(targetProfileId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Deployment target profile not found: " + targetProfileId
            ));
        if (!profile.isActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Deployment target profile is not active: " + targetProfileId);
        }
        return profile;
    }

    private DeploymentProviderResourceActionSummary actionSummary(DeploymentProviderResourceHandleEntity handle,
                                                                  String action,
                                                                  String status,
                                                                  String message,
                                                                  String providerOperationId,
                                                                  JsonNode details) {
        return new DeploymentProviderResourceActionSummary(
            handle.getId(),
            handle.getProviderType(),
            action,
            status,
            message,
            providerOperationId,
            details == null ? objectMapper.createObjectNode() : details,
            Instant.now()
        );
    }

    private String buildProvisioningDetails(DeploymentTargetProfileEntity profile,
                                            DeploymentProviderResourceHandleEntity handle,
                                            CoolifyApplicationSummary application,
                                            DeploymentSourceArtifactEntity sourceArtifact,
                                            int envCount,
                                            CoolifyActionResponse deployResponse) {
        ObjectNode details = objectMapper.createObjectNode();
        details.put("provider", "COOLIFY");
        details.put("targetProfileId", profile.getId());
        details.put("providerResourceHandleId", handle.getId());
        details.put("applicationUuid", application.uuid());
        details.put("applicationName", application.name());
        details.put("fqdn", application.fqdn());
        details.put("status", application.status());
        details.put("sourceArtifactId", sourceArtifact.getId());
        details.put("imageRepository", sourceArtifact.getImageRepository());
        details.put("imageTag", sourceArtifact.getImageTag());
        details.put("environmentVariableCount", envCount);
        details.put("deploymentUuid", deployResponse.deploymentUuid());
        details.put("statusMessage", deployResponse.message());
        details.put("dnsSkipped", !StringUtils.hasText(application.fqdn()));
        details.put("generatedAt", Instant.now().truncatedTo(ChronoUnit.SECONDS).toString());
        return details.toPrettyString();
    }

    private String handleMetadata(CoolifyApplicationSummary application,
                                  DeploymentSourceArtifactEntity sourceArtifact,
                                  int envCount,
                                  CoolifyActionResponse deployResponse) {
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("applicationName", application.name());
        metadata.put("sourceArtifactId", sourceArtifact.getId());
        metadata.put("imageRepository", sourceArtifact.getImageRepository());
        metadata.put("imageTag", sourceArtifact.getImageTag());
        metadata.put("environmentVariableCount", envCount);
        metadata.put("deploymentUuid", deployResponse.deploymentUuid());
        metadata.put("statusMessage", deployResponse.message());
        return metadata.toPrettyString();
    }

    private JsonNode readJson(String json) {
        try {
            return objectMapper.readTree(json == null || json.isBlank() ? "{}" : json);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to read Coolify provider JSON.", ex);
        }
    }

    private String text(JsonNode json, String field, String fallback) {
        String value = json.path(field).asText(null);
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private boolean booleanValue(JsonNode json, String field, boolean fallback) {
        return json.has(field) ? json.path(field).asBoolean(fallback) : fallback;
    }

    private String normalizeRuntimeBaseUrl(String fqdn) {
        if (!StringUtils.hasText(fqdn)) {
            return null;
        }
        String trimmed = fqdn.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed;
        }
        return "https://" + trimmed;
    }

    private String normalizeStatus(String providerStatus, String fallback) {
        if (!StringUtils.hasText(providerStatus)) {
            return fallback;
        }
        return providerStatus.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_");
    }

    private <T> T tracked(ProvisioningProgressTracker progressTracker,
                          String key,
                          String description,
                          java.util.function.Supplier<T> supplier) {
        progressTracker.stepStarted(key, description);
        try {
            T result = supplier.get();
            progressTracker.stepCompleted(key, description);
            return result;
        } catch (RuntimeException ex) {
            progressTracker.stepFailed(key, description, ex.getMessage());
            throw ex;
        }
    }
}
