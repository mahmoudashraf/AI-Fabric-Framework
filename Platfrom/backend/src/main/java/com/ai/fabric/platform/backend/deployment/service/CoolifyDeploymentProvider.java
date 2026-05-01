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
import com.ai.fabric.platform.backend.deployment.model.RailwayEnvVarSummary;
import com.ai.fabric.platform.backend.deployment.model.RailwayProvisioningPlanSummary;
import com.ai.fabric.platform.backend.deployment.model.RailwayServicePlanSummary;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentProviderResourceHandleRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

@Service
public class CoolifyDeploymentProvider implements DeploymentProvisioningProvider {

    private static final String RESOURCE_KIND_APPLICATION = "APPLICATION";
    private static final String RESOURCE_KIND_CONNECTOR_APPLICATION = "CONNECTOR_APPLICATION";
    private static final String SERVICE_ROLE_RUNTIME = "runtime";
    private static final String SERVICE_ROLE_CONNECTOR = "connector";
    private static final String DEFAULT_SERVICE_NAME = "ai-fabric-runtime";
    private static final String DEFAULT_PROMOTION_CHANNEL = "staging";
    private static final Duration DEFAULT_DEPLOY_SETTLE_TIMEOUT = Duration.ofMinutes(6);
    private static final Duration DEFAULT_DEPLOY_SETTLE_POLL_INTERVAL = Duration.ofSeconds(10);

    private final DeploymentTargetProfileRepository targetProfileRepository;
    private final DeploymentProviderResourceHandleRepository resourceHandleRepository;
    private final DeploymentSourceArtifactService sourceArtifactService;
    private final RailwayProvisioningPlanService railwayProvisioningPlanService;
    private final CoolifyTargetProfileResolver targetProfileResolver;
    private final CoolifyApiClient coolifyApiClient;
    private final PlatformSecretService platformSecretService;
    private final ObjectMapper objectMapper;

    @Autowired
    public CoolifyDeploymentProvider(DeploymentTargetProfileRepository targetProfileRepository,
                                     DeploymentProviderResourceHandleRepository resourceHandleRepository,
                                     DeploymentSourceArtifactService sourceArtifactService,
                                     RailwayProvisioningPlanService railwayProvisioningPlanService,
                                     CoolifyTargetProfileResolver targetProfileResolver,
                                     CoolifyApiClient coolifyApiClient,
                                     PlatformSecretService platformSecretService,
                                     ObjectMapper objectMapper) {
        this.targetProfileRepository = targetProfileRepository;
        this.resourceHandleRepository = resourceHandleRepository;
        this.sourceArtifactService = sourceArtifactService;
        this.railwayProvisioningPlanService = railwayProvisioningPlanService;
        this.targetProfileResolver = targetProfileResolver;
        this.coolifyApiClient = coolifyApiClient;
        this.platformSecretService = platformSecretService;
        this.objectMapper = objectMapper;
    }

    CoolifyDeploymentProvider(DeploymentTargetProfileRepository targetProfileRepository,
                              DeploymentProviderResourceHandleRepository resourceHandleRepository,
                              DeploymentSourceArtifactService sourceArtifactService,
                              RailwayProvisioningPlanService railwayProvisioningPlanService,
                              CoolifyTargetProfileResolver targetProfileResolver,
                              CoolifyApiClient coolifyApiClient,
                              ObjectMapper objectMapper) {
        this(
            targetProfileRepository,
            resourceHandleRepository,
            sourceArtifactService,
            railwayProvisioningPlanService,
            targetProfileResolver,
            coolifyApiClient,
            null,
            objectMapper
        );
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
        CoolifyProvisioningSource source = tracked(
            progressTracker,
            "resolve_coolify_source",
            "Resolve Coolify application source from target profile strategy.",
            () -> resolveProvisioningSource(profile, resourceDefaults, deployment, version, release)
        );

        String portsExposes = text(resourceDefaults, "portsExposes", connection.config().defaultPortsExposes());
        String healthCheckPath = text(resourceDefaults, "healthCheckPath", connection.config().defaultHealthCheckPath());
        String healthCheckPort = text(resourceDefaults, "healthCheckPort", connection.config().defaultHealthCheckPort());
        boolean healthCheckEnabled = booleanValue(resourceDefaults, "healthCheckEnabled", StringUtils.hasText(healthCheckPath));
        boolean autogenerateDomain = booleanValue(resourceDefaults, "autogenerateDomain", connection.config().autogenerateDomain());
        String runtimeAppName = resolveApplicationName(deployment, resourceDefaults, source, source.runtimePlan(), SERVICE_ROLE_RUNTIME);
        String runtimeDomain = resolveDomain(deployment, resourceDefaults, connection.config(), autogenerateDomain, SERVICE_ROLE_RUNTIME);

        CoolifyApplicationSummary runtimeApplication = tracked(
            progressTracker,
            "reconcile_coolify_runtime_application",
            source.gitSource()
                ? "Create or update the Coolify public Git runtime application."
                : "Create or update the Coolify Docker-image runtime application.",
            () -> reconcileApplication(
                connection,
                deployment,
                profile,
                RESOURCE_KIND_APPLICATION,
                runtimeAppName,
                source,
                source.runtimePlan(),
                source.baseDirectory(),
                source.dockerfileLocation(),
                portsExposes,
                healthCheckEnabled,
                healthCheckPath,
                healthCheckPort,
                autogenerateDomain,
                runtimeDomain
            )
        );

        CoolifyApplicationSummary connectorApplication = null;
        if (source.gitSource() && source.connectorPlan() != null) {
            String connectorAppName = resolveApplicationName(
                deployment,
                resourceDefaults,
                source,
                source.connectorPlan(),
                SERVICE_ROLE_CONNECTOR
            );
            String connectorDomain = resolveDomain(
                deployment,
                resourceDefaults,
                connection.config(),
                autogenerateDomain,
                SERVICE_ROLE_CONNECTOR
            );
            String connectorBaseDirectory = connectorBaseDirectory(source.connectorPlan(), resourceDefaults);
            String connectorDockerfileLocation = connectorDockerfileLocation(source.connectorPlan(), resourceDefaults);
            connectorApplication = tracked(
                progressTracker,
                "reconcile_coolify_connector_application",
                "Create or update the Coolify public Git REST connector application.",
                () -> reconcileApplication(
                    connection,
                    deployment,
                    profile,
                    RESOURCE_KIND_CONNECTOR_APPLICATION,
                    connectorAppName,
                    source,
                    source.connectorPlan(),
                    connectorBaseDirectory,
                    connectorDockerfileLocation,
                    portsExposes,
                    healthCheckEnabled,
                    healthCheckPath,
                    healthCheckPort,
                    autogenerateDomain,
                    connectorDomain
                )
            );
        }

        String runtimeBaseUrl = normalizeRuntimeBaseUrl(runtimeApplication.fqdn());
        String connectorBaseUrl = connectorApplication == null
            ? runtimeBaseUrl
            : normalizeRuntimeBaseUrl(connectorApplication.fqdn());

        int runtimeEnvCount = tracked(
            progressTracker,
            "configure_coolify_runtime_environment",
            "Update runtime environment variables in Coolify.",
            () -> coolifyApiClient.updateEnvironmentVariables(
                connection,
                runtimeApplication.uuid(),
                buildEnvironment(
                    deployment,
                    version,
                    release,
                    profile,
                    source,
                    source.runtimePlan(),
                    SERVICE_ROLE_RUNTIME,
                    runtimeBaseUrl,
                    connectorBaseUrl
                )
            )
        );

        int connectorEnvCount = 0;
        if (connectorApplication != null) {
            CoolifyApplicationSummary finalConnectorApplication = connectorApplication;
            connectorEnvCount = tracked(
                progressTracker,
                "configure_coolify_connector_environment",
                "Update REST connector environment variables in Coolify.",
                () -> coolifyApiClient.updateEnvironmentVariables(
                    connection,
                    finalConnectorApplication.uuid(),
                    buildEnvironment(
                        deployment,
                        version,
                        release,
                        profile,
                        source,
                        source.connectorPlan(),
                        SERVICE_ROLE_CONNECTOR,
                        runtimeBaseUrl,
                        connectorBaseUrl
                    )
                )
            );
        }

        CoolifyActionResponse connectorDeployResponse = null;
        CoolifyApplicationSummary observedConnector = null;
        if (connectorApplication != null) {
            CoolifyApplicationSummary finalConnectorApplication = connectorApplication;
            connectorDeployResponse = tracked(
                progressTracker,
                "trigger_coolify_connector_deploy",
                "Trigger Coolify deployment for the REST connector application.",
                () -> coolifyApiClient.start(connection, finalConnectorApplication.uuid(), true, true)
            );
            observedConnector = tracked(
                progressTracker,
                "wait_for_coolify_connector",
                "Wait for Coolify to report the REST connector application running.",
                () -> waitForApplicationReady(connection, finalConnectorApplication.uuid(), resourceDefaults, finalConnectorApplication)
            );
        }

        CoolifyActionResponse runtimeDeployResponse = tracked(
            progressTracker,
            "trigger_coolify_runtime_deploy",
            "Trigger Coolify deployment for the runtime application.",
            () -> coolifyApiClient.start(connection, runtimeApplication.uuid(), true, true)
        );

        CoolifyApplicationSummary observedRuntime = tracked(
            progressTracker,
            "wait_for_coolify_runtime",
            "Wait for Coolify to report the application running before Platform verification.",
            () -> waitForApplicationReady(connection, runtimeApplication.uuid(), resourceDefaults, runtimeApplication)
        );

        CoolifyApplicationSummary finalObservedConnector = observedConnector;
        int finalConnectorEnvCount = connectorEnvCount;
        CoolifyActionResponse finalConnectorDeployResponse = connectorDeployResponse;
        DeploymentProviderResourceHandleEntity connectorHandle = connectorApplication == null ? null : tracked(
            progressTracker,
            "record_coolify_connector_handle",
            "Persist Coolify REST connector resource handle for operator actions.",
            () -> upsertHandle(
                deployment,
                release,
                profile,
                connection.config(),
                RESOURCE_KIND_CONNECTOR_APPLICATION,
                finalObservedConnector,
                source,
                SERVICE_ROLE_CONNECTOR,
                finalConnectorEnvCount,
                finalConnectorDeployResponse
            )
        );
        DeploymentProviderResourceHandleEntity runtimeHandle = tracked(
            progressTracker,
            "record_coolify_runtime_handle",
            "Persist Coolify runtime resource handle for operator actions.",
            () -> upsertHandle(
                deployment,
                release,
                profile,
                connection.config(),
                RESOURCE_KIND_APPLICATION,
                observedRuntime,
                source,
                SERVICE_ROLE_RUNTIME,
                runtimeEnvCount,
                runtimeDeployResponse
            )
        );
        String details = buildProvisioningDetails(
            profile,
            runtimeHandle,
            connectorHandle,
            observedRuntime,
            finalObservedConnector,
            source,
            runtimeEnvCount,
            finalConnectorEnvCount,
            runtimeDeployResponse,
            finalConnectorDeployResponse
        );
        progressTracker.mergeDetails(details);

        return new ProvisioningResult(
            applicationsReady(observedRuntime, finalObservedConnector) ? "ACTIVE" : "DEPLOY_REQUESTED",
            DeploymentProviderType.COOLIFY.legacyTarget(),
            runtimeBaseUrl,
            connectorBaseUrl,
            details
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
        try {
            CoolifyActionResponse response = coolifyApiClient.delete(connection, handle.getProviderResourceUuid(), true, false, true, true);
            return actionSummary(handle, "DELETE", "QUEUED", response.message(), response.deploymentUuid(), response.raw());
        } catch (CoolifyApiException ex) {
            if (ex.statusCode() == 404) {
                ObjectNode details = objectMapper.createObjectNode();
                details.put("message", "Coolify application was already absent.");
                details.put("path", ex.path());
                return actionSummary(handle, "DELETE", "COMPLETED", "Coolify application was already absent.", null, details);
            }
            throw ex;
        }
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
            safeApplicationDetails(application),
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
                                                          String resourceKind,
                                                          String appName,
                                                          CoolifyProvisioningSource source,
                                                          RailwayServicePlanSummary servicePlan,
                                                          String baseDirectory,
                                                          String dockerfileLocation,
                                                          String portsExposes,
                                                          boolean healthCheckEnabled,
                                                          String healthCheckPath,
                                                          String healthCheckPort,
                                                          boolean autogenerateDomain,
                                                          String domain) {
        if (source.gitSource()) {
            CoolifyCreatePublicApplicationRequest request = publicApplicationRequest(
                connection,
                source,
                servicePlan,
                baseDirectory,
                dockerfileLocation,
                appName,
                deployment.getId(),
                portsExposes,
                healthCheckEnabled,
                healthCheckPath,
                healthCheckPort,
                autogenerateDomain,
                domain
            );
            return reconcileApplication(
                connection,
                deployment,
                profile,
                resourceKind,
                appName,
                () -> createPublicApplication(connection, request),
                uuid -> coolifyApiClient.updatePublicApplication(connection, uuid, request)
            );
        }

        CoolifyCreateDockerImageApplicationRequest request = dockerImageApplicationRequest(
            connection,
            source,
            appName,
            deployment.getId(),
            portsExposes,
            healthCheckEnabled,
            healthCheckPath,
            healthCheckPort,
            autogenerateDomain,
            domain
        );
        return reconcileApplication(
            connection,
            deployment,
            profile,
            resourceKind,
            appName,
            () -> createDockerImageApplication(connection, request),
            uuid -> coolifyApiClient.updateDockerImageApplication(connection, uuid, request)
        );
    }

    private CoolifyApplicationSummary reconcileApplication(CoolifyConnection connection,
                                                          DeploymentEntity deployment,
                                                          DeploymentTargetProfileEntity profile,
                                                          String resourceKind,
                                                          String appName,
                                                          Supplier<CoolifyApplicationSummary> creator,
                                                          Consumer<String> updater) {
        DeploymentProviderResourceHandleEntity existingHandle = resourceHandleRepository
            .findFirstByDeploymentIdAndTargetProfileIdAndResourceKindOrderByUpdatedAtDesc(
                deployment.getId(),
                profile.getId(),
                resourceKind
            )
            .orElse(null);
        if (existingHandle != null) {
            String uuid = existingHandle.getProviderResourceUuid();
            coolifyApiClient.getApplication(connection, uuid).ifPresent(application -> {
                updater.accept(uuid);
            });
            return coolifyApiClient.getApplication(connection, uuid)
                .orElseGet(creator);
        }

        CoolifyApplicationSummary namedApplication = coolifyApiClient.listApplications(connection).stream()
            .filter(application -> appName.equals(application.name()))
            .findFirst()
            .orElse(null);
        if (namedApplication != null) {
            updater.accept(namedApplication.uuid());
            return coolifyApiClient.getApplication(connection, namedApplication.uuid()).orElse(namedApplication);
        }
        return creator.get();
    }

    private CoolifyApplicationSummary createDockerImageApplication(CoolifyConnection connection,
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

    private CoolifyApplicationSummary createPublicApplication(CoolifyConnection connection,
                                                             CoolifyCreatePublicApplicationRequest request) {
        String uuid = coolifyApiClient.createPublicApplication(connection, request);
        return coolifyApiClient.getApplication(connection, uuid)
            .orElse(new CoolifyApplicationSummary(
                uuid,
                request.name(),
                request.domains(),
                "CREATED",
                null,
                null,
                objectMapper.createObjectNode()
                    .put("uuid", uuid)
                    .put("name", request.name())
                    .put("git_repository", request.gitRepository())
                    .put("git_branch", request.gitBranch())
            ));
    }

    private CoolifyCreateDockerImageApplicationRequest dockerImageApplicationRequest(CoolifyConnection connection,
                                                                                    CoolifyProvisioningSource source,
                                                                                    String appName,
                                                                                    String deploymentId,
                                                                                    String portsExposes,
                                                                                    boolean healthCheckEnabled,
                                                                                    String healthCheckPath,
                                                                                    String healthCheckPort,
                                                                                    boolean autogenerateDomain,
                                                                                    String domain) {
        DeploymentSourceArtifactEntity artifact = source.sourceArtifact();
        return new CoolifyCreateDockerImageApplicationRequest(
            connection.config().projectUuid(),
            connection.config().serverUuid(),
            connection.config().environmentName(),
            connection.config().environmentUuid(),
            artifact.getImageRepository(),
            artifact.getImageTag(),
            portsExposes,
            connection.config().destinationUuid(),
            appName,
            "Managed by AI Fabric deployment " + deploymentId,
            domain,
            healthCheckEnabled,
            healthCheckPath,
            healthCheckPort,
            false,
            connection.config().forceHttps(),
            autogenerateDomain
        );
    }

    private CoolifyCreatePublicApplicationRequest publicApplicationRequest(CoolifyConnection connection,
                                                                          CoolifyProvisioningSource source,
                                                                          RailwayServicePlanSummary servicePlan,
                                                                          String baseDirectory,
                                                                          String dockerfileLocation,
                                                                          String appName,
                                                                          String deploymentId,
                                                                          String portsExposes,
                                                                          boolean healthCheckEnabled,
                                                                          String healthCheckPath,
                                                                          String healthCheckPort,
                                                                          boolean autogenerateDomain,
                                                                          String domain) {
        return new CoolifyCreatePublicApplicationRequest(
            connection.config().projectUuid(),
            connection.config().serverUuid(),
            connection.config().environmentName(),
            connection.config().environmentUuid(),
            source.gitRepository(),
            source.gitBranch(),
            source.buildPack(),
            baseDirectory,
            dockerfileLocation,
            portsExposes,
            connection.config().destinationUuid(),
            appName,
            "Managed by AI Fabric deployment " + deploymentId,
            domain,
            healthCheckEnabled,
            healthCheckPath,
            healthCheckPort,
            false,
            false,
            connection.config().forceHttps(),
            autogenerateDomain
        );
    }

    private DeploymentProviderResourceHandleEntity upsertHandle(DeploymentEntity deployment,
                                                               DeploymentReleaseEntity release,
                                                               DeploymentTargetProfileEntity profile,
                                                               CoolifyTargetProfileConfig config,
                                                               String resourceKind,
                                                               CoolifyApplicationSummary application,
                                                               CoolifyProvisioningSource source,
                                                               String serviceRole,
                                                               int envCount,
                                                               CoolifyActionResponse deployResponse) {
        Instant now = Instant.now();
        DeploymentProviderResourceHandleEntity handle = resourceHandleRepository
            .findFirstByDeploymentIdAndTargetProfileIdAndResourceKindOrderByUpdatedAtDesc(
                deployment.getId(),
                profile.getId(),
                resourceKind
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
        handle.setResourceKind(resourceKind);
        handle.setProviderResourceUuid(application.uuid());
        handle.setProviderProjectUuid(config.projectUuid());
        handle.setProviderEnvironmentUuid(config.environmentUuid());
        handle.setProviderServerUuid(config.serverUuid());
        handle.setFqdn(application.fqdn());
        handle.setStatus(applicationReady(application) ? "ACTIVE" : "DEPLOY_REQUESTED");
        handle.setLastObservedStatus(application.status());
        handle.setLastObservedAt(now);
        handle.setMetadataJson(handleMetadata(application, source, serviceRole, envCount, deployResponse));
        handle.setUpdatedAt(now);
        return resourceHandleRepository.save(handle);
    }

    private CoolifyProvisioningSource resolveProvisioningSource(DeploymentTargetProfileEntity profile,
                                                               JsonNode resourceDefaults,
                                                               DeploymentEntity deployment,
                                                               DeploymentVersionEntity version,
                                                               DeploymentReleaseEntity release) {
        String sourceStrategy = resolveSourceStrategy(profile, resourceDefaults);
        if ("GIT_SOURCE".equals(sourceStrategy)) {
            RailwayProvisioningPlanSummary plan = railwayProvisioningPlanService.buildPlan(deployment, version);
            RailwayServicePlanSummary runtime = plan.services() == null ? null : plan.services().runtime();
            if (runtime == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Coolify Git source requires a runtime service plan.");
            }
            RailwayServicePlanSummary connector = plan.services() == null ? null : plan.services().restConnector();
            String repository = text(resourceDefaults, "gitRepository", plan.repository());
            String branch = text(resourceDefaults, "gitBranch", plan.branch());
            String dockerfilePath = text(resourceDefaults, "dockerfilePath", runtime.dockerfilePath());
            String baseDirectory = text(
                resourceDefaults,
                "baseDirectory",
                StringUtils.hasText(runtime.rootDir()) ? normalizeCoolifyDirectory(runtime.rootDir()) : "/"
            );
            String buildPack = text(resourceDefaults, "buildPack", "dockerfile");
            return new CoolifyProvisioningSource(
                sourceStrategy,
                null,
                plan,
                runtime,
                connector,
                normalizeGitRepositoryForCoolify(repository),
                requireText(branch, "Coolify Git source requires a git branch."),
                normalizeCoolifyDirectory(baseDirectory),
                normalizeDockerfileLocation(dockerfilePath),
                buildPack
            );
        }
        if ("IMAGE_SOURCE".equals(sourceStrategy)) {
            return new CoolifyProvisioningSource(
                sourceStrategy,
                resolveSourceArtifact(release, resourceDefaults),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
            );
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported Coolify source strategy: " + sourceStrategy);
    }

    private String resolveSourceStrategy(DeploymentTargetProfileEntity profile, JsonNode resourceDefaults) {
        String value = text(resourceDefaults, "sourceStrategy", profile.getSourceStrategy());
        if (!StringUtils.hasText(value)) {
            return "IMAGE_SOURCE";
        }
        return value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
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

    private String connectorBaseDirectory(RailwayServicePlanSummary connectorPlan, JsonNode resourceDefaults) {
        return normalizeCoolifyDirectory(text(
            resourceDefaults,
            "connectorBaseDirectory",
            connectorPlan != null && StringUtils.hasText(connectorPlan.rootDir())
                ? normalizeCoolifyDirectory(connectorPlan.rootDir())
                : "/"
        ));
    }

    private String connectorDockerfileLocation(RailwayServicePlanSummary connectorPlan, JsonNode resourceDefaults) {
        return normalizeDockerfileLocation(text(
            resourceDefaults,
            "connectorDockerfilePath",
            connectorPlan == null ? null : connectorPlan.dockerfilePath()
        ));
    }

    private List<CoolifyEnvVar> buildEnvironment(DeploymentEntity deployment,
                                                 DeploymentVersionEntity version,
                                                 DeploymentReleaseEntity release,
                                                 DeploymentTargetProfileEntity profile,
                                                 CoolifyProvisioningSource source,
                                                 RailwayServicePlanSummary servicePlan,
                                                 String serviceRole,
                                                 String runtimeBaseUrl,
                                                 String connectorBaseUrl) {
        LinkedHashMap<String, CoolifyEnvVar> env = new LinkedHashMap<>();
        if (servicePlan != null && servicePlan.env() != null) {
            for (RailwayEnvVarSummary serviceEnv : servicePlan.env()) {
                putEnv(
                    env,
                    serviceEnv.key(),
                    resolveEnvVarValue(serviceEnv, runtimeBaseUrl, connectorBaseUrl),
                    secretPlaceholderName(serviceEnv.value()) != null
                );
            }
        }
        putEnv(env, "PLATFORM_DEPLOYMENT_ID", deployment.getId());
        putEnv(env, "PLATFORM_DEPLOYMENT_VERSION_ID", version.getId());
        putEnv(env, "PLATFORM_DEPLOYMENT_RELEASE_ID", release.getId());
        putEnv(env, "PLATFORM_TARGET_PROFILE_ID", profile.getId());
        putEnv(env, "PLATFORM_COOLIFY_SERVICE_ROLE", serviceRole);
        putEnv(env, "PLATFORM_SOURCE_STRATEGY", source.sourceStrategy());
        if (source.sourceArtifact() != null) {
            putEnv(env, "PLATFORM_SOURCE_ARTIFACT_ID", source.sourceArtifact().getId());
        }
        if (source.gitSource()) {
            putEnv(env, "PLATFORM_SOURCE_REPOSITORY", source.gitRepository());
            putEnv(env, "PLATFORM_SOURCE_BRANCH", source.gitBranch());
            putEnv(env, "PLATFORM_DOCKERFILE_LOCATION", servicePlan != null
                ? normalizeDockerfileLocation(servicePlan.dockerfilePath())
                : source.dockerfileLocation());
        }
        putEnv(env, "PLATFORM_ENVIRONMENT_NAME", profile.getEnvironmentName());
        return new ArrayList<>(env.values());
    }

    private void putEnv(LinkedHashMap<String, CoolifyEnvVar> env, String key, String value) {
        putEnv(env, key, value, false);
    }

    private void putEnv(LinkedHashMap<String, CoolifyEnvVar> env, String key, String value, boolean sensitive) {
        if (!StringUtils.hasText(key)) {
            return;
        }
        env.put(key, env(key, value, sensitive));
    }

    private CoolifyEnvVar env(String key, String value, boolean sensitive) {
        return new CoolifyEnvVar(key, value, false, true, false, sensitive);
    }

    private String resolveEnvVarValue(RailwayEnvVarSummary envVar, String runtimeBaseUrl, String connectorBaseUrl) {
        String serviceBaseUrl = RailwayApiProvisioningProvider.resolveServiceBaseUrl(
            envVar.key(),
            runtimeBaseUrl,
            connectorBaseUrl
        );
        if (serviceBaseUrl != null) {
            return serviceBaseUrl;
        }
        String secretName = secretPlaceholderName(envVar.value());
        if (secretName == null) {
            return envVar.value();
        }
        if (platformSecretService == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Platform secret service is unavailable for Coolify provisioning.");
        }
        String resolved = platformSecretService.resolveSecret(secretName);
        if (!StringUtils.hasText(resolved)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Missing platform secret '" + secretName + "' required for Coolify provisioning."
            );
        }
        return resolved;
    }

    private String secretPlaceholderName(String value) {
        if (value == null || !value.startsWith("${secret:") || !value.endsWith("}")) {
            return null;
        }
        String secretName = value.substring("${secret:".length(), value.length() - 1).trim();
        return StringUtils.hasText(secretName) ? secretName : null;
    }

    private String resolveApplicationName(DeploymentEntity deployment,
                                          JsonNode resourceDefaults,
                                          CoolifyProvisioningSource source,
                                          RailwayServicePlanSummary servicePlan,
                                          String serviceRole) {
        String configured = SERVICE_ROLE_CONNECTOR.equals(serviceRole)
            ? text(resourceDefaults, "connectorApplicationName", null)
            : text(resourceDefaults, "applicationName", null);
        if (StringUtils.hasText(configured)) {
            return normalizeName(configured);
        }
        String prefix = SERVICE_ROLE_CONNECTOR.equals(serviceRole)
            ? text(resourceDefaults, "connectorApplicationNamePrefix", null)
            : text(resourceDefaults, "applicationNamePrefix", null);
        if (StringUtils.hasText(prefix)) {
            return normalizeName(prefix + "-" + deployment.getId());
        }
        if (source.gitSource()
            && servicePlan != null
            && StringUtils.hasText(servicePlan.serviceName())) {
            return normalizeName(servicePlan.serviceName());
        }
        return normalizeName("ai-fabric-" + serviceRole + "-" + deployment.getId());
    }

    private CoolifyApplicationSummary waitForApplicationReady(CoolifyConnection connection,
                                                              String applicationUuid,
                                                              JsonNode resourceDefaults,
                                                              CoolifyApplicationSummary fallback) {
        Duration timeout = durationSeconds(resourceDefaults, "deploySettleTimeoutSeconds", DEFAULT_DEPLOY_SETTLE_TIMEOUT);
        Duration pollInterval = durationSeconds(resourceDefaults, "deploySettlePollSeconds", DEFAULT_DEPLOY_SETTLE_POLL_INTERVAL);
        Instant deadline = Instant.now().plus(timeout);
        CoolifyApplicationSummary latest = coolifyApiClient.getApplication(connection, applicationUuid).orElse(fallback);
        while (!applicationReady(latest) && Instant.now().isBefore(deadline)) {
            try {
                Thread.sleep(pollInterval.toMillis());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return latest;
            }
            latest = coolifyApiClient.getApplication(connection, applicationUuid).orElse(latest);
        }
        return latest;
    }

    private Duration durationSeconds(JsonNode root, String field, Duration fallback) {
        if (root == null || !root.has(field)) {
            return fallback;
        }
        long seconds = root.path(field).asLong(fallback.toSeconds());
        if (seconds <= 0) {
            return Duration.ZERO;
        }
        return Duration.ofSeconds(seconds);
    }

    private boolean applicationReady(CoolifyApplicationSummary application) {
        if (application == null || !StringUtils.hasText(application.status())) {
            return false;
        }
        String normalized = normalizeStatus(application.status(), "");
        return normalized.startsWith("RUNNING") && !normalized.contains("UNHEALTHY");
    }

    private boolean applicationsReady(CoolifyApplicationSummary runtimeApplication,
                                      CoolifyApplicationSummary connectorApplication) {
        return applicationReady(runtimeApplication)
            && (connectorApplication == null || applicationReady(connectorApplication));
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
                                 boolean autogenerateDomain,
                                 String serviceRole) {
        if (autogenerateDomain) {
            return null;
        }
        String configured = SERVICE_ROLE_CONNECTOR.equals(serviceRole)
            ? text(resourceDefaults, "connectorDomain", null)
            : text(resourceDefaults, "domain", null);
        if (StringUtils.hasText(configured)) {
            return normalizeCoolifyDomain(configured, config.forceHttps());
        }
        String suffix = config.defaultPublicDomainSuffix();
        if (!StringUtils.hasText(suffix)) {
            return null;
        }
        String name = SERVICE_ROLE_CONNECTOR.equals(serviceRole)
            ? deployment.getId() + "-connector"
            : deployment.getId();
        return normalizeCoolifyDomain(normalizeName(name) + "." + suffix, config.forceHttps());
    }

    private String normalizeCoolifyDomain(String domain, boolean forceHttps) {
        if (!StringUtils.hasText(domain)) {
            return null;
        }
        String trimmed = domain.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed;
        }
        String scheme = forceHttps ? "https://" : "http://";
        return scheme + trimmed;
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
            safeActionDetails(details),
            Instant.now()
        );
    }

    private JsonNode safeApplicationDetails(CoolifyApplicationSummary application) {
        ObjectNode details = objectMapper.createObjectNode();
        putIfText(details, "uuid", application.uuid());
        putIfText(details, "name", application.name());
        putIfText(details, "status", application.status());
        putIfText(details, "fqdn", application.fqdn());
        putIfText(details, "imageRepository", application.imageRepository());
        putIfText(details, "imageTag", application.imageTag());

        JsonNode raw = application.raw();
        copyText(raw, details, "git_repository");
        copyText(raw, details, "git_branch");
        copyText(raw, details, "build_pack");
        copyText(raw, details, "base_directory");
        copyText(raw, details, "dockerfile_location");
        copyText(raw, details, "ports_exposes");
        copyText(raw, details, "health_check_enabled");
        copyText(raw, details, "health_check_path");
        copyText(raw, details, "health_check_port");
        copyText(raw, details, "created_at");
        copyText(raw, details, "updated_at");
        putIfText(details, "destinationUuid", raw.path("destination").path("uuid").asText(null));
        putIfText(details, "destinationNetwork", raw.path("destination").path("network").asText(null));
        putIfText(details, "serverUuid", raw.path("destination").path("server").path("uuid").asText(null));
        putIfText(details, "serverName", raw.path("destination").path("server").path("name").asText(null));
        return details;
    }

    private JsonNode safeActionDetails(JsonNode raw) {
        ObjectNode details = objectMapper.createObjectNode();
        if (raw == null || raw.isMissingNode() || raw.isNull()) {
            return details;
        }
        copyText(raw, details, "message");
        copyText(raw, details, "deployment_uuid");
        copyText(raw, details, "uuid");
        copyText(raw, details, "status");
        return details;
    }

    private String buildProvisioningDetails(DeploymentTargetProfileEntity profile,
                                            DeploymentProviderResourceHandleEntity runtimeHandle,
                                            DeploymentProviderResourceHandleEntity connectorHandle,
                                            CoolifyApplicationSummary runtimeApplication,
                                            CoolifyApplicationSummary connectorApplication,
                                            CoolifyProvisioningSource source,
                                            int runtimeEnvCount,
                                            int connectorEnvCount,
                                            CoolifyActionResponse runtimeDeployResponse,
                                            CoolifyActionResponse connectorDeployResponse) {
        ObjectNode details = objectMapper.createObjectNode();
        details.put("provider", "COOLIFY");
        details.put("targetProfileId", profile.getId());
        details.put("providerResourceHandleId", runtimeHandle.getId());
        details.put("applicationUuid", runtimeApplication.uuid());
        details.put("applicationName", runtimeApplication.name());
        details.put("fqdn", runtimeApplication.fqdn());
        details.put("status", runtimeApplication.status());
        details.put("runtimeProviderResourceHandleId", runtimeHandle.getId());
        details.put("runtimeApplicationUuid", runtimeApplication.uuid());
        details.put("runtimeFqdn", runtimeApplication.fqdn());
        details.put("runtimeStatus", runtimeApplication.status());
        if (connectorHandle != null && connectorApplication != null) {
            details.put("connectorProviderResourceHandleId", connectorHandle.getId());
            details.put("connectorApplicationUuid", connectorApplication.uuid());
            details.put("connectorFqdn", connectorApplication.fqdn());
            details.put("connectorStatus", connectorApplication.status());
        }
        writeSourceDetails(details, source);
        details.put("environmentVariableCount", runtimeEnvCount + connectorEnvCount);
        details.put("runtimeEnvironmentVariableCount", runtimeEnvCount);
        details.put("connectorEnvironmentVariableCount", connectorEnvCount);
        details.put("deploymentUuid", runtimeDeployResponse.deploymentUuid());
        details.put("statusMessage", runtimeDeployResponse.message());
        if (connectorDeployResponse != null) {
            details.put("connectorDeploymentUuid", connectorDeployResponse.deploymentUuid());
            details.put("connectorStatusMessage", connectorDeployResponse.message());
        }
        details.put("dnsSkipped", !StringUtils.hasText(runtimeApplication.fqdn()));
        details.put("generatedAt", Instant.now().truncatedTo(ChronoUnit.SECONDS).toString());
        return details.toPrettyString();
    }

    private String handleMetadata(CoolifyApplicationSummary application,
                                  CoolifyProvisioningSource source,
                                  String serviceRole,
                                  int envCount,
                                  CoolifyActionResponse deployResponse) {
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("applicationName", application.name());
        metadata.put("serviceRole", serviceRole);
        writeSourceDetails(metadata, source);
        metadata.put("environmentVariableCount", envCount);
        if (deployResponse != null) {
            metadata.put("deploymentUuid", deployResponse.deploymentUuid());
            metadata.put("statusMessage", deployResponse.message());
        }
        return metadata.toPrettyString();
    }

    private void writeSourceDetails(ObjectNode target, CoolifyProvisioningSource source) {
        target.put("sourceStrategy", source.sourceStrategy());
        if (source.sourceArtifact() != null) {
            target.put("sourceArtifactId", source.sourceArtifact().getId());
            target.put("imageRepository", source.sourceArtifact().getImageRepository());
            target.put("imageTag", source.sourceArtifact().getImageTag());
        }
        if (source.gitSource()) {
            target.put("gitRepository", source.gitRepository());
            target.put("gitBranch", source.gitBranch());
            target.put("buildPack", source.buildPack());
            target.put("baseDirectory", source.baseDirectory());
            target.put("dockerfileLocation", source.dockerfileLocation());
            if (source.connectorPlan() != null) {
                target.put("connectorDockerfileLocation", normalizeDockerfileLocation(source.connectorPlan().dockerfilePath()));
            }
            if (source.plan() != null) {
                target.put("railwayPlanRepository", source.plan().repository());
                target.put("railwayPlanBranch", source.plan().branch());
            }
        }
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

    private String requireText(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return value.trim();
    }

    private String normalizeGitRepositoryForCoolify(String repository) {
        String value = requireText(repository, "Coolify Git source requires a git repository.");
        if (value.startsWith("https://") || value.startsWith("http://") || value.startsWith("git@")) {
            return value;
        }
        String slug = value.replaceAll("^/+", "").replaceAll("/+$", "");
        if (!slug.contains("/")) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Coolify Git source repository must be a Git URL or GitHub slug like 'owner/repo'."
            );
        }
        return "https://github.com/" + slug + (slug.endsWith(".git") ? "" : ".git");
    }

    private String normalizeCoolifyDirectory(String value) {
        if (!StringUtils.hasText(value) || ".".equals(value.trim())) {
            return "/";
        }
        String normalized = value.trim();
        return normalized.startsWith("/") ? normalized : "/" + normalized;
    }

    private String normalizeDockerfileLocation(String value) {
        String normalized = requireText(value, "Coolify Git source requires a dockerfile path.");
        return normalized.startsWith("/") ? normalized : "/" + normalized;
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

    private void copyText(JsonNode source, ObjectNode target, String field) {
        if (source == null || source.isMissingNode() || source.isNull() || !source.has(field)) {
            return;
        }
        JsonNode value = source.path(field);
        if (value.isBoolean()) {
            target.put(field, value.asBoolean());
            return;
        }
        if (value.isNumber()) {
            target.put(field, value.asText());
            return;
        }
        putIfText(target, field, value.asText(null));
    }

    private void putIfText(ObjectNode target, String field, String value) {
        if (StringUtils.hasText(value)) {
            target.put(field, value.trim());
        }
    }

    private record CoolifyProvisioningSource(
        String sourceStrategy,
        DeploymentSourceArtifactEntity sourceArtifact,
        RailwayProvisioningPlanSummary plan,
        RailwayServicePlanSummary runtimePlan,
        RailwayServicePlanSummary connectorPlan,
        String gitRepository,
        String gitBranch,
        String baseDirectory,
        String dockerfileLocation,
        String buildPack
    ) {
        boolean gitSource() {
            return "GIT_SOURCE".equals(sourceStrategy);
        }
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
