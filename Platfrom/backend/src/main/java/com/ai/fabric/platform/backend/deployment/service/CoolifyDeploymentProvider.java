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
import com.ai.fabric.platform.backend.tenant.entity.PlatformCustomerEntity;
import com.ai.fabric.platform.backend.tenant.repository.PlatformCustomerRepository;
import com.ai.fabric.platform.backend.vectorization.service.VectorizationRunnerProvisioningService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

@Service
public class CoolifyDeploymentProvider implements DeploymentProvisioningProvider {

    private static final String RESOURCE_KIND_APPLICATION = "APPLICATION";
    private static final String RESOURCE_KIND_CONNECTOR_APPLICATION = "CONNECTOR_APPLICATION";
    private static final String RESOURCE_KIND_VECTORIZATION_RUNNER_APPLICATION = "VECTORIZATION_RUNNER_APPLICATION";
    private static final String RESOURCE_KIND_RUNTIME_POSTGRES_DATABASE = "RUNTIME_POSTGRES_DATABASE";
    private static final String SERVICE_ROLE_RUNTIME = "runtime";
    private static final String SERVICE_ROLE_CONNECTOR = "connector";
    private static final String SERVICE_ROLE_VECTORIZATION_RUNNER = "vectorization-runner";
    private static final String DEFAULT_RUNTIME_PORT = "8097";
    private static final String DEFAULT_CONNECTOR_PORT = "8082";
    private static final String DEFAULT_VECTORIZATION_RUNNER_PORT = "8099";
    private static final String RUNTIME_DATABASE_MODE_COOLIFY_POSTGRES = "COOLIFY_POSTGRES";
    private static final String DEFAULT_SERVICE_NAME = "ai-fabric-runtime";
    private static final String DEFAULT_PROMOTION_CHANNEL = "staging";
    private static final Duration DEFAULT_DEPLOY_SETTLE_TIMEOUT = Duration.ofMinutes(6);
    private static final Duration DEFAULT_DEPLOY_SETTLE_POLL_INTERVAL = Duration.ofSeconds(10);
    private static final Duration DEFAULT_STALE_DELETE_TIMEOUT = Duration.ofMinutes(2);
    private static final Duration DEFAULT_STALE_DELETE_POLL_INTERVAL = Duration.ofSeconds(5);
    private static final SecureRandom SECRET_RANDOM = new SecureRandom();

    private final DeploymentTargetProfileRepository targetProfileRepository;
    private final DeploymentProviderResourceHandleRepository resourceHandleRepository;
    private final DeploymentSourceArtifactService sourceArtifactService;
    private final RailwayProvisioningPlanService railwayProvisioningPlanService;
    private final DeploymentManagedVectorProvisioningService deploymentManagedVectorProvisioningService;
    private final DeploymentManagedVectorResourceService deploymentManagedVectorResourceService;
    private final CoolifyTargetProfileResolver targetProfileResolver;
    private final CoolifyApiClient coolifyApiClient;
    private final PlatformSecretService platformSecretService;
    private final PlatformCustomerRepository platformCustomerRepository;
    private final ObjectMapper objectMapper;
    private VectorizationRunnerProvisioningService vectorizationRunnerProvisioningService;
    private PlatformManagedProductProvisioningService platformManagedProductProvisioningService;

    @Autowired
    public CoolifyDeploymentProvider(DeploymentTargetProfileRepository targetProfileRepository,
                                     DeploymentProviderResourceHandleRepository resourceHandleRepository,
                                     DeploymentSourceArtifactService sourceArtifactService,
                                     RailwayProvisioningPlanService railwayProvisioningPlanService,
                                     DeploymentManagedVectorProvisioningService deploymentManagedVectorProvisioningService,
                                     DeploymentManagedVectorResourceService deploymentManagedVectorResourceService,
                                     CoolifyTargetProfileResolver targetProfileResolver,
                                     CoolifyApiClient coolifyApiClient,
                                     PlatformSecretService platformSecretService,
                                     PlatformCustomerRepository platformCustomerRepository,
                                     ObjectMapper objectMapper) {
        this.targetProfileRepository = targetProfileRepository;
        this.resourceHandleRepository = resourceHandleRepository;
        this.sourceArtifactService = sourceArtifactService;
        this.railwayProvisioningPlanService = railwayProvisioningPlanService;
        this.deploymentManagedVectorProvisioningService = deploymentManagedVectorProvisioningService;
        this.deploymentManagedVectorResourceService = deploymentManagedVectorResourceService;
        this.targetProfileResolver = targetProfileResolver;
        this.coolifyApiClient = coolifyApiClient;
        this.platformSecretService = platformSecretService;
        this.platformCustomerRepository = platformCustomerRepository;
        this.objectMapper = objectMapper;
    }

    @Autowired(required = false)
    void setVectorizationRunnerProvisioningService(VectorizationRunnerProvisioningService vectorizationRunnerProvisioningService) {
        this.vectorizationRunnerProvisioningService = vectorizationRunnerProvisioningService;
    }

    @Autowired(required = false)
    void setPlatformManagedProductProvisioningService(PlatformManagedProductProvisioningService platformManagedProductProvisioningService) {
        this.platformManagedProductProvisioningService = platformManagedProductProvisioningService;
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
            null,
            null,
            targetProfileResolver,
            coolifyApiClient,
            null,
            null,
            objectMapper
        );
    }

    CoolifyDeploymentProvider(DeploymentTargetProfileRepository targetProfileRepository,
                              DeploymentProviderResourceHandleRepository resourceHandleRepository,
                              DeploymentSourceArtifactService sourceArtifactService,
                              RailwayProvisioningPlanService railwayProvisioningPlanService,
                              CoolifyTargetProfileResolver targetProfileResolver,
                              CoolifyApiClient coolifyApiClient,
                              PlatformSecretService platformSecretService,
                              ObjectMapper objectMapper) {
        this(
            targetProfileRepository,
            resourceHandleRepository,
            sourceArtifactService,
            railwayProvisioningPlanService,
            null,
            null,
            targetProfileResolver,
            coolifyApiClient,
            platformSecretService,
            null,
            objectMapper
        );
    }

    CoolifyDeploymentProvider(DeploymentTargetProfileRepository targetProfileRepository,
                              DeploymentProviderResourceHandleRepository resourceHandleRepository,
                              DeploymentSourceArtifactService sourceArtifactService,
                              RailwayProvisioningPlanService railwayProvisioningPlanService,
                              CoolifyTargetProfileResolver targetProfileResolver,
                              CoolifyApiClient coolifyApiClient,
                              PlatformSecretService platformSecretService,
                              PlatformCustomerRepository platformCustomerRepository,
                              ObjectMapper objectMapper) {
        this(
            targetProfileRepository,
            resourceHandleRepository,
            sourceArtifactService,
            railwayProvisioningPlanService,
            null,
            null,
            targetProfileResolver,
            coolifyApiClient,
            platformSecretService,
            platformCustomerRepository,
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
        ManagedVectorProvisioningResult managedVectorProvisioningResult = ensureManagedVectorProvisioned(
            deployment,
            version,
            release,
            progressTracker
        );
        ensureManagedProductDependencies(profile, version, progressTracker);
        CoolifyProvisioningSource source = tracked(
            progressTracker,
            "resolve_coolify_source",
            "Resolve Coolify application source from target profile strategy.",
            () -> resolveProvisioningSource(
                profile,
                resourceDefaults,
                deployment,
                version,
                release,
                managedVectorProvisioningResult.effectiveProviderConfig()
            )
        );
        CoolifyResourceScope resourceScope = tracked(
            progressTracker,
            "resolve_coolify_customer_scope",
            "Resolve the Coolify project and environment that should group this customer's resources.",
            () -> resolveResourceScope(connection, deployment, resourceDefaults)
        );
        RailwayServicePlanSummary vectorizationRunnerPlan = source.vectorizationRunnerPlan();
        if (vectorizationRunnerPlan != null && vectorizationRunnerProvisioningService != null) {
            tracked(
                progressTracker,
                "provision_vectorization_runner_registration",
                "Create or reconcile vectorization runner registration material before runner deployment.",
                () -> vectorizationRunnerProvisioningService.ensureManagedRegistration(deployment)
            );
        } else if (vectorizationRunnerPlan == null && vectorizationRunnerProvisioningService != null) {
            vectorizationRunnerProvisioningService.clearManagedRegistrationSecret(deployment.getId());
        }

        String healthCheckPath = text(resourceDefaults, "healthCheckPath", connection.config().defaultHealthCheckPath());
        String runtimePortsExposes = servicePortsExposes(resourceDefaults, connection.config(), SERVICE_ROLE_RUNTIME);
        String runtimeHealthCheckPort = serviceHealthCheckPort(resourceDefaults, connection.config(), SERVICE_ROLE_RUNTIME);
        String connectorPortsExposes = servicePortsExposes(resourceDefaults, connection.config(), SERVICE_ROLE_CONNECTOR);
        String connectorHealthCheckPort = serviceHealthCheckPort(resourceDefaults, connection.config(), SERVICE_ROLE_CONNECTOR);
        String vectorizationRunnerPortsExposes = servicePortsExposes(
            resourceDefaults,
            connection.config(),
            SERVICE_ROLE_VECTORIZATION_RUNNER
        );
        String vectorizationRunnerHealthCheckPort = serviceHealthCheckPort(
            resourceDefaults,
            connection.config(),
            SERVICE_ROLE_VECTORIZATION_RUNNER
        );
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
                resourceScope,
                deployment,
                profile,
                RESOURCE_KIND_APPLICATION,
                runtimeAppName,
                source,
                source.runtimePlan(),
                source.baseDirectory(),
                source.dockerfileLocation(),
                runtimePortsExposes,
                healthCheckEnabled,
                healthCheckPath,
                runtimeHealthCheckPort,
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
                    resourceScope,
                    deployment,
                    profile,
                    RESOURCE_KIND_CONNECTOR_APPLICATION,
                    connectorAppName,
                    source,
                    source.connectorPlan(),
                    connectorBaseDirectory,
                    connectorDockerfileLocation,
                    connectorPortsExposes,
                    healthCheckEnabled,
                    healthCheckPath,
                    connectorHealthCheckPort,
                    autogenerateDomain,
                    connectorDomain
                )
            );
        }

        CoolifyApplicationSummary vectorizationRunnerApplication = null;
        if (source.gitSource() && vectorizationRunnerPlan != null) {
            String runnerAppName = resolveApplicationName(
                deployment,
                resourceDefaults,
                source,
                vectorizationRunnerPlan,
                SERVICE_ROLE_VECTORIZATION_RUNNER
            );
            String runnerBaseDirectory = serviceBaseDirectory(
                vectorizationRunnerPlan,
                resourceDefaults,
                "vectorizationRunnerBaseDirectory"
            );
            String runnerDockerfileLocation = serviceDockerfileLocation(
                vectorizationRunnerPlan,
                resourceDefaults,
                "vectorizationRunnerDockerfilePath"
            );
            String runnerDomain = resolveDomain(
                deployment,
                resourceDefaults,
                connection.config(),
                autogenerateDomain,
                SERVICE_ROLE_VECTORIZATION_RUNNER
            );
            vectorizationRunnerApplication = tracked(
                progressTracker,
                "reconcile_coolify_vectorization_runner_application",
                "Create or update the Coolify public Git vectorization runner application.",
                () -> reconcileApplication(
                    connection,
                    resourceScope,
                    deployment,
                    profile,
                    RESOURCE_KIND_VECTORIZATION_RUNNER_APPLICATION,
                    runnerAppName,
                    source,
                    vectorizationRunnerPlan,
                    runnerBaseDirectory,
                    runnerDockerfileLocation,
                    vectorizationRunnerPortsExposes,
                    healthCheckEnabled,
                    healthCheckPath,
                    vectorizationRunnerHealthCheckPort,
                    autogenerateDomain,
                    runnerDomain
                )
            );
        }

        String runtimeBaseUrl = normalizeRuntimeBaseUrl(runtimeApplication.fqdn());
        String connectorBaseUrl = connectorApplication == null
            ? runtimeBaseUrl
            : normalizeRuntimeBaseUrl(connectorApplication.fqdn());
        CoolifyRuntimeDatabaseBinding runtimeDatabaseBinding = tracked(
            progressTracker,
            "reconcile_coolify_runtime_database",
            "Create or reuse the profile-managed runtime PostgreSQL database when production profile requires durable chat storage.",
            () -> reconcileRuntimeDatabaseBinding(connection, resourceScope, deployment, profile, resourceDefaults)
        );

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
                    resourceScope,
                    source,
                    source.runtimePlan(),
                    SERVICE_ROLE_RUNTIME,
                    runtimeBaseUrl,
                    connectorBaseUrl,
                    runtimeDatabaseBinding
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
                        resourceScope,
                        source,
                        source.connectorPlan(),
                        SERVICE_ROLE_CONNECTOR,
                        runtimeBaseUrl,
                        connectorBaseUrl,
                        null
                    )
                )
            );
        }

        int vectorizationRunnerEnvCount = 0;
        if (vectorizationRunnerApplication != null) {
            CoolifyApplicationSummary finalVectorizationRunnerApplication = vectorizationRunnerApplication;
            vectorizationRunnerEnvCount = tracked(
                progressTracker,
                "configure_coolify_vectorization_runner_environment",
                "Update vectorization runner environment variables in Coolify.",
                () -> coolifyApiClient.updateEnvironmentVariables(
                    connection,
                    finalVectorizationRunnerApplication.uuid(),
                    buildEnvironment(
                        deployment,
                        version,
                        release,
                        profile,
                        resourceScope,
                        source,
                        vectorizationRunnerPlan,
                        SERVICE_ROLE_VECTORIZATION_RUNNER,
                        runtimeBaseUrl,
                        connectorBaseUrl,
                        null
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
            CoolifyActionResponse finalConnectorDeployResponse = connectorDeployResponse;
            observedConnector = tracked(
                progressTracker,
                "wait_for_coolify_connector",
                "Wait for Coolify to report the REST connector application running.",
                () -> waitForApplicationReady(
                    connection,
                    finalConnectorApplication.uuid(),
                    resourceDefaults,
                    finalConnectorApplication,
                    finalConnectorDeployResponse
                )
            );
        }

        CoolifyActionResponse vectorizationRunnerDeployResponse = null;
        CoolifyApplicationSummary observedVectorizationRunner = null;
        if (vectorizationRunnerApplication != null) {
            CoolifyApplicationSummary finalVectorizationRunnerApplication = vectorizationRunnerApplication;
            vectorizationRunnerDeployResponse = tracked(
                progressTracker,
                "trigger_coolify_vectorization_runner_deploy",
                "Trigger Coolify deployment for the vectorization runner application.",
                () -> coolifyApiClient.start(connection, finalVectorizationRunnerApplication.uuid(), true, true)
            );
            CoolifyActionResponse finalVectorizationRunnerDeployResponse = vectorizationRunnerDeployResponse;
            observedVectorizationRunner = tracked(
                progressTracker,
                "wait_for_coolify_vectorization_runner",
                "Wait for Coolify to report the vectorization runner application running.",
                () -> waitForApplicationReady(
                    connection,
                    finalVectorizationRunnerApplication.uuid(),
                    resourceDefaults,
                    finalVectorizationRunnerApplication,
                    finalVectorizationRunnerDeployResponse
                )
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
            () -> waitForApplicationReady(
                connection,
                runtimeApplication.uuid(),
                resourceDefaults,
                runtimeApplication,
                runtimeDeployResponse
            )
        );

        CoolifyApplicationSummary finalObservedConnector = observedConnector;
        int finalConnectorEnvCount = connectorEnvCount;
        CoolifyActionResponse finalConnectorDeployResponse = connectorDeployResponse;
        CoolifyApplicationSummary finalObservedVectorizationRunner = observedVectorizationRunner;
        int finalVectorizationRunnerEnvCount = vectorizationRunnerEnvCount;
        CoolifyActionResponse finalVectorizationRunnerDeployResponse = vectorizationRunnerDeployResponse;
        DeploymentProviderResourceHandleEntity connectorHandle = connectorApplication == null ? null : tracked(
            progressTracker,
            "record_coolify_connector_handle",
            "Persist Coolify REST connector resource handle for operator actions.",
            () -> upsertHandle(
                deployment,
                release,
                profile,
                resourceScope,
                connection.config(),
                RESOURCE_KIND_CONNECTOR_APPLICATION,
                finalObservedConnector,
                source,
                SERVICE_ROLE_CONNECTOR,
                finalConnectorEnvCount,
                finalConnectorDeployResponse
            )
        );
        DeploymentProviderResourceHandleEntity vectorizationRunnerHandle = vectorizationRunnerApplication == null ? null : tracked(
            progressTracker,
            "record_coolify_vectorization_runner_handle",
            "Persist Coolify vectorization runner resource handle for operator actions.",
            () -> upsertHandle(
                deployment,
                release,
                profile,
                resourceScope,
                connection.config(),
                RESOURCE_KIND_VECTORIZATION_RUNNER_APPLICATION,
                finalObservedVectorizationRunner,
                source,
                SERVICE_ROLE_VECTORIZATION_RUNNER,
                finalVectorizationRunnerEnvCount,
                finalVectorizationRunnerDeployResponse
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
                resourceScope,
                connection.config(),
                RESOURCE_KIND_APPLICATION,
                observedRuntime,
                source,
                SERVICE_ROLE_RUNTIME,
                runtimeEnvCount,
                runtimeDeployResponse
            )
        );
        DeploymentProviderResourceHandleEntity runtimeDatabaseHandle = runtimeDatabaseBinding == null ? null : tracked(
            progressTracker,
            "record_coolify_runtime_database_handle",
            "Persist Coolify runtime database resource handle for operator actions.",
            () -> upsertDatabaseHandle(
                deployment,
                release,
                profile,
                resourceScope,
                connection.config(),
                runtimeDatabaseBinding
            )
        );
        String details = buildProvisioningDetails(
            profile,
            runtimeHandle,
            runtimeDatabaseHandle,
            connectorHandle,
            observedRuntime,
            finalObservedConnector,
            vectorizationRunnerHandle,
            finalObservedVectorizationRunner,
            resourceScope,
            source,
            runtimeEnvCount,
            finalConnectorEnvCount,
            finalVectorizationRunnerEnvCount,
            runtimeDeployResponse,
            finalConnectorDeployResponse,
            finalVectorizationRunnerDeployResponse,
            runtimeDatabaseBinding,
            managedVectorProvisioningResult
        );
        progressTracker.mergeDetails(details);

        return new ProvisioningResult(
            applicationsReady(observedRuntime, finalObservedConnector, finalObservedVectorizationRunner) ? "ACTIVE" : "DEPLOY_REQUESTED",
            DeploymentProviderType.COOLIFY.legacyTarget(),
            runtimeBaseUrl,
            connectorBaseUrl,
            details
        );
    }

    @Override
    public DeploymentProviderResourceActionSummary start(DeploymentProviderResourceHandleEntity handle, String reason) {
        CoolifyConnection connection = connectionForHandle(handle);
        if (isRuntimePostgresDatabase(handle)) {
            CoolifyActionResponse response = coolifyApiClient.startDatabase(connection, handle.getProviderResourceUuid());
            return actionSummary(handle, "START", "QUEUED", response.message(), response.deploymentUuid(), response.raw());
        }
        CoolifyActionResponse response = coolifyApiClient.start(connection, handle.getProviderResourceUuid(), false, true);
        return actionSummary(handle, "START", "QUEUED", response.message(), response.deploymentUuid(), response.raw());
    }

    @Override
    public DeploymentProviderResourceActionSummary stop(DeploymentProviderResourceHandleEntity handle, String reason) {
        CoolifyConnection connection = connectionForHandle(handle);
        if (isRuntimePostgresDatabase(handle)) {
            CoolifyActionResponse response = coolifyApiClient.stopDatabase(connection, handle.getProviderResourceUuid());
            return actionSummary(handle, "STOP", "QUEUED", response.message(), response.deploymentUuid(), response.raw());
        }
        CoolifyActionResponse response = coolifyApiClient.stop(connection, handle.getProviderResourceUuid(), true);
        return actionSummary(handle, "STOP", "QUEUED", response.message(), response.deploymentUuid(), response.raw());
    }

    @Override
    public DeploymentProviderResourceActionSummary restart(DeploymentProviderResourceHandleEntity handle, String reason) {
        CoolifyConnection connection = connectionForHandle(handle);
        if (isRuntimePostgresDatabase(handle)) {
            CoolifyActionResponse response = coolifyApiClient.restartDatabase(connection, handle.getProviderResourceUuid());
            return actionSummary(handle, "RESTART", "QUEUED", response.message(), response.deploymentUuid(), response.raw());
        }
        CoolifyActionResponse response = coolifyApiClient.restart(connection, handle.getProviderResourceUuid());
        return actionSummary(handle, "RESTART", "QUEUED", response.message(), response.deploymentUuid(), response.raw());
    }

    @Override
    public DeploymentProviderResourceActionSummary delete(DeploymentProviderResourceHandleEntity handle, String reason) {
        CoolifyConnection connection = connectionForHandle(handle);
        try {
            if (isRuntimePostgresDatabase(handle)) {
                CoolifyActionResponse response = coolifyApiClient.deleteDatabase(connection, handle.getProviderResourceUuid(), true, true, true, true);
                clearRuntimeDatabaseSecret(handle, reason);
                return actionSummary(handle, "DELETE", "QUEUED", response.message(), response.deploymentUuid(), response.raw());
            }
            CoolifyActionResponse response = coolifyApiClient.delete(connection, handle.getProviderResourceUuid(), true, false, true, true);
            return actionSummary(handle, "DELETE", "QUEUED", response.message(), response.deploymentUuid(), response.raw());
        } catch (CoolifyApiException ex) {
            if (ex.statusCode() == 404) {
                if (isRuntimePostgresDatabase(handle)) {
                    clearRuntimeDatabaseSecret(handle, reason);
                }
                ObjectNode details = objectMapper.createObjectNode();
                details.put("message", isRuntimePostgresDatabase(handle)
                    ? "Coolify database was already absent."
                    : "Coolify application was already absent.");
                details.put("path", ex.path());
                return actionSummary(
                    handle,
                    "DELETE",
                    "COMPLETED",
                    isRuntimePostgresDatabase(handle)
                        ? "Coolify database was already absent."
                        : "Coolify application was already absent.",
                    null,
                    details
                );
            }
            throw ex;
        }
    }

    @Override
    public DeploymentProviderResourceStatusSummary status(DeploymentProviderResourceHandleEntity handle) {
        CoolifyConnection connection = connectionForHandle(handle);
        if (isRuntimePostgresDatabase(handle)) {
            CoolifyDatabaseSummary database = coolifyApiClient.getDatabase(connection, handle.getProviderResourceUuid())
                .orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Coolify database not found: " + handle.getProviderResourceUuid()
                ));
            return new DeploymentProviderResourceStatusSummary(
                handle.getId(),
                handle.getProviderType(),
                handle.getProviderResourceUuid(),
                normalizeStatus(database.status(), "OBSERVED"),
                database.status(),
                null,
                safeDatabaseDetails(database),
                Instant.now()
            );
        }
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
            isRuntimePostgresDatabase(handle)
                ? coolifyApiClient.databaseLogs(connection, handle.getProviderResourceUuid(), normalizedLines)
                : coolifyApiClient.logs(connection, handle.getProviderResourceUuid(), normalizedLines),
            Instant.now()
        );
    }

    private boolean isRuntimePostgresDatabase(DeploymentProviderResourceHandleEntity handle) {
        return handle != null && RESOURCE_KIND_RUNTIME_POSTGRES_DATABASE.equals(handle.getResourceKind());
    }

    private void clearRuntimeDatabaseSecret(DeploymentProviderResourceHandleEntity handle, String reason) {
        if (platformSecretService == null) {
            return;
        }
        String secretName = text(readJson(handle.getMetadataJson()), "passwordSecretName", null);
        if (!StringUtils.hasText(secretName) || !platformSecretService.isManagedSecretName(secretName)) {
            return;
        }
        platformSecretService.clearManagedSecret(
            secretName,
            Map.of(
                "deploymentId", handle.getDeploymentId(),
                "targetProfileId", handle.getTargetProfileId(),
                "reason", StringUtils.hasText(reason) ? reason : "provider_resource_delete",
                "action", "DELETE_RUNTIME_POSTGRES_DATABASE"
            )
        );
    }

    private CoolifyApplicationSummary reconcileApplication(CoolifyConnection connection,
                                                          CoolifyResourceScope scope,
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
                scope,
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
                scope,
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
            scope,
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
            scope,
            deployment,
            profile,
            resourceKind,
            appName,
            () -> createDockerImageApplication(connection, request),
            uuid -> coolifyApiClient.updateDockerImageApplication(connection, uuid, request)
        );
    }

    private CoolifyApplicationSummary reconcileApplication(CoolifyConnection connection,
                                                          CoolifyResourceScope scope,
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
            if (!handleMatchesScope(existingHandle, scope)) {
                deleteStaleApplication(connection, existingHandle);
            } else {
                String uuid = existingHandle.getProviderResourceUuid();
                coolifyApiClient.getApplication(connection, uuid).ifPresent(application -> {
                    updater.accept(uuid);
                });
                return coolifyApiClient.getApplication(connection, uuid)
                    .orElseGet(creator);
            }
        }

        CoolifyApplicationSummary namedApplication = coolifyApiClient.listApplications(connection).stream()
            .filter(application -> appName.equals(application.name()))
            .filter(application -> applicationMatchesScope(application, scope))
            .findFirst()
            .orElse(null);
        if (namedApplication != null) {
            updater.accept(namedApplication.uuid());
            return coolifyApiClient.getApplication(connection, namedApplication.uuid()).orElse(namedApplication);
        }
        return createWithConflictCleanup(connection, scope, appName, creator);
    }

    private CoolifyApplicationSummary createWithConflictCleanup(CoolifyConnection connection,
                                                                CoolifyResourceScope scope,
                                                                String appName,
                                                                Supplier<CoolifyApplicationSummary> creator) {
        try {
            return creator.get();
        } catch (CoolifyApiException ex) {
            if (ex.statusCode() != 409) {
                throw ex;
            }
            CoolifyApplicationSummary conflictingApplication = coolifyApiClient.listApplications(connection).stream()
                .filter(application -> appName.equals(application.name()))
                .filter(application -> !applicationMatchesScope(application, scope))
                .findFirst()
                .orElse(null);
            if (conflictingApplication == null || !StringUtils.hasText(conflictingApplication.uuid())) {
                throw ex;
            }
            deleteStaleApplication(connection, conflictingApplication.uuid());
            return creator.get();
        }
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
        coolifyApiClient.updatePublicApplication(connection, uuid, request);
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

    private CoolifyRuntimeDatabaseBinding reconcileRuntimeDatabaseBinding(CoolifyConnection connection,
                                                                          CoolifyResourceScope scope,
                                                                          DeploymentEntity deployment,
                                                                          DeploymentTargetProfileEntity profile,
                                                                          JsonNode resourceDefaults) {
        String mode = text(resourceDefaults, "runtimeDatabaseMode", null);
        if (!RUNTIME_DATABASE_MODE_COOLIFY_POSTGRES.equalsIgnoreCase(mode)) {
            return null;
        }
        if (platformSecretService == null) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Platform secret service is required for managed runtime Postgres provisioning."
            );
        }
        CoolifyCreatePostgresDatabaseRequest request = runtimePostgresDatabaseRequest(
            connection,
            scope,
            deployment,
            profile,
            resourceDefaults
        );
        CoolifyDatabaseSummary database = reconcilePostgresDatabase(connection, scope, deployment, profile, request);
        String passwordSecretName = runtimePostgresPasswordSecretName(deployment.getId(), profile.getId());
        String password = platformSecretService.resolveSecret(passwordSecretName);
        if (!StringUtils.hasText(password)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Managed runtime Postgres password secret disappeared during provisioning."
            );
        }
        String internalHost = text(
            resourceDefaults,
            "runtimeDatabaseInternalHost",
            resolveRuntimeDatabaseInternalHost(database)
        );
        String port = text(resourceDefaults, "runtimeDatabasePort", "5432");
        String jdbcDatabaseName = StringUtils.hasText(database.postgresDatabase())
            ? database.postgresDatabase()
            : request.postgresDatabase();
        String jdbcUrl = text(
            resourceDefaults,
            "runtimeDatabaseJdbcUrl",
            "jdbc:postgresql://" + internalHost + ":" + port + "/" + jdbcDatabaseName
        );
        return new CoolifyRuntimeDatabaseBinding(
            RUNTIME_DATABASE_MODE_COOLIFY_POSTGRES,
            database,
            jdbcUrl,
            request.postgresUser(),
            password,
            passwordSecretName
        );
    }

    private String resolveRuntimeDatabaseInternalHost(CoolifyDatabaseSummary database) {
        String internalDbUrl = textFirst(database.raw(), "internal_db_url", "internalDbUrl");
        if (StringUtils.hasText(internalDbUrl)) {
            try {
                URI uri = URI.create(internalDbUrl);
                if (StringUtils.hasText(uri.getHost())) {
                    return uri.getHost();
                }
            } catch (RuntimeException ignored) {
                // Fall back to Coolify identifiers below; do not propagate or log URL credentials.
            }
        }
        if (StringUtils.hasText(database.uuid())) {
            return database.uuid();
        }
        return database.name();
    }

    private CoolifyDatabaseSummary reconcilePostgresDatabase(CoolifyConnection connection,
                                                             CoolifyResourceScope scope,
                                                             DeploymentEntity deployment,
                                                             DeploymentTargetProfileEntity profile,
                                                             CoolifyCreatePostgresDatabaseRequest request) {
        Optional<DeploymentProviderResourceHandleEntity> existingHandle =
            resourceHandleRepository.findFirstByDeploymentIdAndTargetProfileIdAndResourceKindOrderByUpdatedAtDesc(
                deployment.getId(),
                profile.getId(),
                RESOURCE_KIND_RUNTIME_POSTGRES_DATABASE
            );
        if (existingHandle.isPresent() && handleMatchesScope(existingHandle.get(), scope)) {
            String uuid = existingHandle.get().getProviderResourceUuid();
            Optional<CoolifyDatabaseSummary> observed = coolifyApiClient.getDatabase(connection, uuid);
            if (observed.isPresent()) {
                coolifyApiClient.updatePostgresDatabase(connection, uuid, request);
                return coolifyApiClient.getDatabase(connection, uuid).orElse(observed.get());
            }
        }

        CoolifyDatabaseSummary namedDatabase = coolifyApiClient.listDatabases(connection).stream()
            .filter(database -> request.name().equals(database.name()))
            .filter(database -> databaseMatchesScope(database, scope))
            .findFirst()
            .orElse(null);
        if (namedDatabase != null) {
            coolifyApiClient.updatePostgresDatabase(connection, namedDatabase.uuid(), request);
            return coolifyApiClient.getDatabase(connection, namedDatabase.uuid()).orElse(namedDatabase);
        }

        try {
            String uuid = coolifyApiClient.createPostgresDatabase(connection, request);
            CoolifyDatabaseSummary database = coolifyApiClient.getDatabase(connection, uuid)
                .orElse(new CoolifyDatabaseSummary(
                    uuid,
                    request.name(),
                    "CREATED",
                    "postgresql",
                    request.postgresUser(),
                    request.postgresDatabase(),
                    objectMapper.createObjectNode()
                        .put("uuid", uuid)
                        .put("name", request.name())
                        .put("postgres_user", request.postgresUser())
                        .put("postgres_db", request.postgresDatabase())
                ));
            coolifyApiClient.startDatabase(connection, uuid);
            return database;
        } catch (CoolifyApiException ex) {
            if (ex.statusCode() != 409) {
                throw ex;
            }
            return coolifyApiClient.listDatabases(connection).stream()
                .filter(database -> request.name().equals(database.name()))
                .filter(database -> databaseMatchesScope(database, scope))
                .findFirst()
                .orElseThrow(() -> ex);
        }
    }

    private CoolifyCreatePostgresDatabaseRequest runtimePostgresDatabaseRequest(CoolifyConnection connection,
                                                                                CoolifyResourceScope scope,
                                                                                DeploymentEntity deployment,
                                                                                DeploymentTargetProfileEntity profile,
                                                                                JsonNode resourceDefaults) {
        String databaseName = normalizeDatabaseIdentifier(text(resourceDefaults, "runtimeDatabaseName", "runtime_chat"));
        String username = normalizeDatabaseIdentifier(text(resourceDefaults, "runtimeDatabaseUsername", "runtime_user"));
        String passwordSecretName = runtimePostgresPasswordSecretName(deployment.getId(), profile.getId());
        String password = platformSecretService.resolveSecret(passwordSecretName);
        if (!StringUtils.hasText(password)) {
            password = generateSecretValue();
            platformSecretService.upsertManagedSecret(
                passwordSecretName,
                password,
                Map.of(
                    "deploymentId", deployment.getId(),
                    "targetProfileId", profile.getId(),
                    "purpose", "RUNTIME_POSTGRES_PASSWORD"
                )
            );
        }
        String prefix = text(resourceDefaults, "runtimeDatabaseNamePrefix", "ai-fabric-runtime-postgres");
        String resourceName = normalizeName(prefix + "-" + deployment.getId());
        return new CoolifyCreatePostgresDatabaseRequest(
            scope.projectUuid(),
            connection.config().serverUuid(),
            scope.environmentName(),
            scope.environmentUuid(),
            connection.config().destinationUuid(),
            resourceName,
            "Durable runtime chat database for AI Fabric deployment " + deployment.getId(),
            text(resourceDefaults, "runtimeDatabaseImage", "postgres:16-alpine"),
            username,
            password,
            databaseName,
            booleanValue(resourceDefaults, "runtimeDatabasePublic", false),
            false
        );
    }

    private DeploymentProviderResourceHandleEntity upsertDatabaseHandle(DeploymentEntity deployment,
                                                                        DeploymentReleaseEntity release,
                                                                        DeploymentTargetProfileEntity profile,
                                                                        CoolifyResourceScope scope,
                                                                        CoolifyTargetProfileConfig config,
                                                                        CoolifyRuntimeDatabaseBinding binding) {
        Instant now = Instant.now();
        DeploymentProviderResourceHandleEntity handle = resourceHandleRepository
            .findFirstByDeploymentIdAndTargetProfileIdAndResourceKindOrderByUpdatedAtDesc(
                deployment.getId(),
                profile.getId(),
                RESOURCE_KIND_RUNTIME_POSTGRES_DATABASE
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
        handle.setResourceKind(RESOURCE_KIND_RUNTIME_POSTGRES_DATABASE);
        handle.setProviderResourceUuid(binding.database().uuid());
        handle.setProviderProjectUuid(scope.projectUuid());
        handle.setProviderEnvironmentUuid(scope.environmentUuid());
        handle.setProviderServerUuid(config.serverUuid());
        handle.setFqdn(null);
        handle.setStatus(databaseReady(binding.database()) ? "ACTIVE" : "PROVISIONED");
        handle.setLastObservedStatus(binding.database().status());
        handle.setLastObservedAt(now);
        handle.setMetadataJson(databaseHandleMetadata(binding, scope));
        handle.setUpdatedAt(now);
        return resourceHandleRepository.save(handle);
    }

    private CoolifyCreateDockerImageApplicationRequest dockerImageApplicationRequest(CoolifyConnection connection,
                                                                                    CoolifyResourceScope scope,
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
            scope.projectUuid(),
            connection.config().serverUuid(),
            scope.environmentName(),
            scope.environmentUuid(),
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
                                                                          CoolifyResourceScope scope,
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
            scope.projectUuid(),
            connection.config().serverUuid(),
            scope.environmentName(),
            scope.environmentUuid(),
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
                                                               CoolifyResourceScope scope,
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
        handle.setProviderProjectUuid(scope.projectUuid());
        handle.setProviderEnvironmentUuid(scope.environmentUuid());
        handle.setProviderServerUuid(config.serverUuid());
        handle.setFqdn(application.fqdn());
        handle.setStatus(applicationReady(application) ? "ACTIVE" : "DEPLOY_REQUESTED");
        handle.setLastObservedStatus(application.status());
        handle.setLastObservedAt(now);
        handle.setMetadataJson(handleMetadata(application, source, scope, serviceRole, envCount, deployResponse));
        handle.setUpdatedAt(now);
        return resourceHandleRepository.save(handle);
    }

    private CoolifyResourceScope resolveResourceScope(CoolifyConnection connection,
                                                      DeploymentEntity deployment,
                                                      JsonNode resourceDefaults) {
        boolean customerGroupingEnabled = booleanValue(
            resourceDefaults,
            "customerProjectGroupingEnabled",
            platformCustomerRepository != null
        );
        if (!customerGroupingEnabled) {
            return defaultResourceScope(connection);
        }
        CoolifyTargetProfileConfig config = connection.config();
        String projectName = resolveCustomerProjectName(deployment, resourceDefaults);
        String projectDescription = resolveCustomerProjectDescription(deployment, projectName, config.environmentName());
        CoolifyProjectSummary project = ensureProject(connection, projectName, projectDescription);
        String environmentName = text(resourceDefaults, "customerProjectEnvironmentName", config.environmentName());
        CoolifyEnvironmentSummary environment = ensureEnvironment(connection, project.uuid(), environmentName);
        return new CoolifyResourceScope(
            project.uuid(),
            project.name(),
            environment.name(),
            environment.uuid(),
            true
        );
    }

    private CoolifyResourceScope defaultResourceScope(CoolifyConnection connection) {
        CoolifyTargetProfileConfig config = connection.config();
        return new CoolifyResourceScope(
            config.projectUuid(),
            null,
            config.environmentName(),
            config.environmentUuid(),
            false
        );
    }

    private CoolifyProjectSummary ensureProject(CoolifyConnection connection, String name, String description) {
        Optional<CoolifyProjectSummary> existing = findProject(connection, name);
        if (existing.isPresent()) {
            return existing.get();
        }
        try {
            String uuid = coolifyApiClient.createProject(connection, name, description);
            return new CoolifyProjectSummary(
                uuid,
                name,
                description,
                objectMapper.createObjectNode()
                    .put("uuid", uuid)
                    .put("name", name)
                    .put("description", description)
            );
        } catch (CoolifyApiException ex) {
            if (ex.statusCode() != 409 && ex.statusCode() != 422) {
                throw ex;
            }
            return findProject(connection, name)
                .orElseThrow(() -> ex);
        }
    }

    private Optional<CoolifyProjectSummary> findProject(CoolifyConnection connection, String name) {
        return coolifyApiClient.listProjects(connection).stream()
            .filter(project -> name.equalsIgnoreCase(project.name()))
            .findFirst();
    }

    private CoolifyEnvironmentSummary ensureEnvironment(CoolifyConnection connection,
                                                        String projectUuid,
                                                        String environmentName) {
        Optional<CoolifyEnvironmentSummary> existing = findEnvironment(connection, projectUuid, environmentName);
        if (existing.isPresent()) {
            return existing.get();
        }
        try {
            String uuid = coolifyApiClient.createEnvironment(connection, projectUuid, environmentName);
            return new CoolifyEnvironmentSummary(
                uuid,
                environmentName,
                projectUuid,
                null,
                objectMapper.createObjectNode()
                    .put("uuid", uuid)
                    .put("name", environmentName)
                    .put("project_uuid", projectUuid)
            );
        } catch (CoolifyApiException ex) {
            if (ex.statusCode() != 409 && ex.statusCode() != 422) {
                throw ex;
            }
            return findEnvironment(connection, projectUuid, environmentName)
                .orElseThrow(() -> ex);
        }
    }

    private Optional<CoolifyEnvironmentSummary> findEnvironment(CoolifyConnection connection,
                                                                String projectUuid,
                                                                String environmentName) {
        Optional<CoolifyEnvironmentSummary> listed = coolifyApiClient.listEnvironments(connection, projectUuid).stream()
            .filter(environment -> environmentName.equalsIgnoreCase(environment.name()))
            .findFirst();
        if (listed.isEmpty()) {
            return Optional.empty();
        }
        if (StringUtils.hasText(listed.get().uuid())) {
            return listed;
        }
        return coolifyApiClient.getEnvironment(connection, projectUuid, environmentName)
            .or(() -> listed);
    }

    private String resolveCustomerProjectName(DeploymentEntity deployment, JsonNode resourceDefaults) {
        String configuredPrefix = text(resourceDefaults, "customerProjectNamePrefix", "customer");
        String prefix = normalizeScopedName(configuredPrefix, 32);
        String configuredSlug = text(resourceDefaults, "customerProjectSlug", null);
        String rawCustomerName = StringUtils.hasText(configuredSlug)
            ? configuredSlug
            : resolveCustomerSlug(deployment);
        String slug = normalizeScopedName(rawCustomerName, 48);
        return normalizeScopedName(prefix + "-" + slug, 80);
    }

    private String resolveCustomerSlug(DeploymentEntity deployment) {
        String customerId = deployment.getCustomerId();
        if (platformCustomerRepository != null && StringUtils.hasText(customerId)) {
            Optional<PlatformCustomerEntity> customer = platformCustomerRepository.findById(customerId);
            if (customer.isPresent()) {
                PlatformCustomerEntity entity = customer.get();
                if (StringUtils.hasText(entity.getSlug())) {
                    return entity.getSlug();
                }
                if (StringUtils.hasText(entity.getName())) {
                    return entity.getName();
                }
            }
        }
        if (StringUtils.hasText(customerId)) {
            return customerId;
        }
        if (StringUtils.hasText(deployment.getTenantId())) {
            return deployment.getTenantId();
        }
        return deployment.getId();
    }

    private String resolveCustomerProjectDescription(DeploymentEntity deployment,
                                                     String projectName,
                                                     String environmentName) {
        String customerId = StringUtils.hasText(deployment.getCustomerId())
            ? deployment.getCustomerId()
            : "unknown";
        return "Managed by AI Fabric for Platform customer " + customerId
            + " (" + projectName + "), environment " + environmentName + ".";
    }

    private ManagedVectorProvisioningResult ensureManagedVectorProvisioned(DeploymentEntity deployment,
                                                                           DeploymentVersionEntity version,
                                                                           DeploymentReleaseEntity release,
                                                                           ProvisioningProgressTracker progressTracker) {
        if (deploymentManagedVectorProvisioningService == null) {
            ObjectNode details = objectMapper.createObjectNode();
            details.put("enabled", false);
            details.put("mode", "UNAVAILABLE");
            details.put("message", "Managed vector provisioning service is not available in this Coolify provider context.");
            return new ManagedVectorProvisioningResult(null, details);
        }

        ManagedVectorProvisioningResult result = deploymentManagedVectorProvisioningService.requiresProvisioning(version)
            ? tracked(
                progressTracker,
                "ensure_vector_backend",
                "Create or reconcile managed external vector resources before runtime deployment.",
                () -> deploymentManagedVectorProvisioningService.ensureProvisioned(deployment, version)
            )
            : deploymentManagedVectorProvisioningService.ensureProvisioned(deployment, version);
        if (deploymentManagedVectorResourceService != null) {
            deploymentManagedVectorResourceService.syncProvisionedResources(deployment, version, release, result);
        }
        return result;
    }

    private void ensureManagedProductDependencies(DeploymentTargetProfileEntity profile,
                                                  DeploymentVersionEntity version,
                                                  ProvisioningProgressTracker progressTracker) {
        if (!hasMcpToolActions(readJson(version.getActionsConfigJson()))) {
            return;
        }
        if (platformManagedProductProvisioningService == null) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "MCP tool actions require managed product dependency reconciliation support."
            );
        }
        tracked(
            progressTracker,
            "ensure_managed_product_dependencies",
            "Reconcile managed product service dependencies required by runtime actions.",
            () -> {
                platformManagedProductProvisioningService.reconcile("mcp-execution-gateway", profile.getId());
                return true;
            }
        );
    }

    private boolean hasMcpToolActions(JsonNode actionsConfig) {
        JsonNode actions = actionsConfig == null ? null : actionsConfig.path("actions");
        if (actions == null || !actions.isArray()) {
            return false;
        }
        for (JsonNode action : actions) {
            String adapterType = text(action, "adapterType", null);
            if ("mcp-tool".equalsIgnoreCase(adapterType)) {
                return true;
            }
            JsonNode execution = action.path("execution");
            if (execution.isObject()) {
                String executionAdapterType = text(execution, "adapterType", null);
                if ("mcp-tool".equalsIgnoreCase(executionAdapterType) || execution.path("mcp").isObject()) {
                    return true;
                }
            }
        }
        return false;
    }

    private CoolifyProvisioningSource resolveProvisioningSource(DeploymentTargetProfileEntity profile,
                                                               JsonNode resourceDefaults,
                                                               DeploymentEntity deployment,
                                                               DeploymentVersionEntity version,
                                                               DeploymentReleaseEntity release,
                                                               JsonNode providerConfigOverride) {
        String sourceStrategy = resolveSourceStrategy(profile, resourceDefaults);
        if ("GIT_SOURCE".equals(sourceStrategy)) {
            RailwayProvisioningPlanSummary plan = providerConfigOverride == null
                ? railwayProvisioningPlanService.buildPlan(deployment, version)
                : railwayProvisioningPlanService.buildPlan(deployment, version, providerConfigOverride);
            RailwayServicePlanSummary runtime = plan.services() == null ? null : plan.services().runtime();
            if (runtime == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Coolify Git source requires a runtime service plan.");
            }
            RailwayServicePlanSummary connector = plan.services() == null ? null : plan.services().restConnector();
            RailwayServicePlanSummary vectorizationRunner = plan.services() == null ? null : plan.services().vectorizationRunner();
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
                vectorizationRunner,
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
        return serviceBaseDirectory(connectorPlan, resourceDefaults, "connectorBaseDirectory");
    }

    private String serviceBaseDirectory(RailwayServicePlanSummary servicePlan,
                                        JsonNode resourceDefaults,
                                        String overrideField) {
        return normalizeCoolifyDirectory(text(
            resourceDefaults,
            overrideField,
            servicePlan != null && StringUtils.hasText(servicePlan.rootDir())
                ? normalizeCoolifyDirectory(servicePlan.rootDir())
                : "/"
        ));
    }

    private String connectorDockerfileLocation(RailwayServicePlanSummary connectorPlan, JsonNode resourceDefaults) {
        return serviceDockerfileLocation(connectorPlan, resourceDefaults, "connectorDockerfilePath");
    }

    private String serviceDockerfileLocation(RailwayServicePlanSummary servicePlan,
                                             JsonNode resourceDefaults,
                                             String overrideField) {
        return normalizeDockerfileLocation(text(
            resourceDefaults,
            overrideField,
            servicePlan == null ? null : servicePlan.dockerfilePath()
        ));
    }

    private List<CoolifyEnvVar> buildEnvironment(DeploymentEntity deployment,
                                                 DeploymentVersionEntity version,
                                                 DeploymentReleaseEntity release,
                                                 DeploymentTargetProfileEntity profile,
                                                 CoolifyResourceScope scope,
                                                 CoolifyProvisioningSource source,
                                                 RailwayServicePlanSummary servicePlan,
                                                 String serviceRole,
                                                 String runtimeBaseUrl,
                                                 String connectorBaseUrl,
                                                 CoolifyRuntimeDatabaseBinding runtimeDatabaseBinding) {
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
        putEnv(env, "PLATFORM_COOLIFY_PROJECT_UUID", scope.projectUuid());
        putEnv(env, "PLATFORM_COOLIFY_PROJECT_NAME", scope.projectName());
        putEnv(env, "PLATFORM_COOLIFY_ENVIRONMENT_NAME", scope.environmentName());
        putEnv(env, "PLATFORM_COOLIFY_ENVIRONMENT_UUID", scope.environmentUuid());
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
        if (SERVICE_ROLE_RUNTIME.equals(serviceRole) && runtimeDatabaseBinding != null) {
            putEnv(env, "SPRING_DATASOURCE_URL", runtimeDatabaseBinding.jdbcUrl());
            putEnv(env, "SPRING_DATASOURCE_DRIVER_CLASS_NAME", "org.postgresql.Driver");
            putEnv(env, "SPRING_DATASOURCE_USERNAME", runtimeDatabaseBinding.username(), true);
            putEnv(env, "SPRING_DATASOURCE_PASSWORD", runtimeDatabaseBinding.password(), true);
            putEnv(env, "PLATFORM_RUNTIME_DATABASE_MODE", runtimeDatabaseBinding.mode());
            putEnv(env, "PLATFORM_RUNTIME_DATABASE_RESOURCE_UUID", runtimeDatabaseBinding.database().uuid());
            putEnv(env, "PLATFORM_RUNTIME_DATABASE_PASSWORD_SECRET", runtimeDatabaseBinding.passwordSecretName(), true);
        }
        putEnv(env, "PLATFORM_ENVIRONMENT_NAME", profile.getEnvironmentName());
        return withPreviewEnvironment(new ArrayList<>(env.values()));
    }

    private List<CoolifyEnvVar> withPreviewEnvironment(List<CoolifyEnvVar> envVars) {
        List<CoolifyEnvVar> expanded = new ArrayList<>();
        for (CoolifyEnvVar envVar : envVars) {
            expanded.add(envVar);
            if (!envVar.preview()) {
                expanded.add(new CoolifyEnvVar(
                    envVar.key(),
                    envVar.value(),
                    true,
                    envVar.literal(),
                    envVar.multiline(),
                    envVar.shownOnce()
                ));
            }
        }
        return expanded;
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
            : SERVICE_ROLE_VECTORIZATION_RUNNER.equals(serviceRole)
                ? text(resourceDefaults, "vectorizationRunnerApplicationName", null)
            : text(resourceDefaults, "applicationName", null);
        if (StringUtils.hasText(configured)) {
            return normalizeName(configured);
        }
        String prefix = SERVICE_ROLE_CONNECTOR.equals(serviceRole)
            ? text(resourceDefaults, "connectorApplicationNamePrefix", null)
            : SERVICE_ROLE_VECTORIZATION_RUNNER.equals(serviceRole)
                ? text(resourceDefaults, "vectorizationRunnerApplicationNamePrefix", null)
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

    private String servicePortsExposes(JsonNode resourceDefaults,
                                       CoolifyTargetProfileConfig config,
                                       String serviceRole) {
        String configured = SERVICE_ROLE_CONNECTOR.equals(serviceRole)
            ? text(resourceDefaults, "connectorPortsExposes", null)
            : SERVICE_ROLE_VECTORIZATION_RUNNER.equals(serviceRole)
                ? text(resourceDefaults, "vectorizationRunnerPortsExposes", null)
                : SERVICE_ROLE_RUNTIME.equals(serviceRole)
                    ? text(resourceDefaults, "runtimePortsExposes", null)
                    : text(resourceDefaults, "portsExposes", null);
        if (StringUtils.hasText(configured)) {
            return configured;
        }
        String defaultPort = defaultServicePort(serviceRole);
        return StringUtils.hasText(defaultPort) ? defaultPort : config.defaultPortsExposes();
    }

    private String serviceHealthCheckPort(JsonNode resourceDefaults,
                                          CoolifyTargetProfileConfig config,
                                          String serviceRole) {
        String configured = SERVICE_ROLE_CONNECTOR.equals(serviceRole)
            ? text(resourceDefaults, "connectorHealthCheckPort", null)
            : SERVICE_ROLE_VECTORIZATION_RUNNER.equals(serviceRole)
                ? text(resourceDefaults, "vectorizationRunnerHealthCheckPort", null)
                : SERVICE_ROLE_RUNTIME.equals(serviceRole)
                    ? text(resourceDefaults, "runtimeHealthCheckPort", null)
                    : text(resourceDefaults, "healthCheckPort", null);
        if (StringUtils.hasText(configured)) {
            return configured;
        }
        String defaultPort = defaultServicePort(serviceRole);
        return StringUtils.hasText(defaultPort) ? defaultPort : config.defaultHealthCheckPort();
    }

    private String defaultServicePort(String serviceRole) {
        return switch (serviceRole) {
            case SERVICE_ROLE_RUNTIME -> DEFAULT_RUNTIME_PORT;
            case SERVICE_ROLE_CONNECTOR -> DEFAULT_CONNECTOR_PORT;
            case SERVICE_ROLE_VECTORIZATION_RUNNER -> DEFAULT_VECTORIZATION_RUNNER_PORT;
            default -> null;
        };
    }

    private boolean handleMatchesScope(DeploymentProviderResourceHandleEntity handle, CoolifyResourceScope scope) {
        if (!sameText(handle.getProviderProjectUuid(), scope.projectUuid())) {
            return false;
        }
        return !StringUtils.hasText(scope.environmentUuid())
            || !StringUtils.hasText(handle.getProviderEnvironmentUuid())
            || sameText(handle.getProviderEnvironmentUuid(), scope.environmentUuid());
    }

    private void deleteStaleApplication(CoolifyConnection connection, DeploymentProviderResourceHandleEntity handle) {
        deleteStaleApplication(connection, handle.getProviderResourceUuid());
    }

    private void deleteStaleApplication(CoolifyConnection connection, String applicationUuid) {
        try {
            coolifyApiClient.delete(connection, applicationUuid, true, false, true, true);
            waitForApplicationAbsent(connection, applicationUuid);
        } catch (CoolifyApiException ex) {
            if (ex.statusCode() != 404) {
                throw ex;
            }
        }
    }

    private void waitForApplicationAbsent(CoolifyConnection connection, String applicationUuid) {
        Instant deadline = Instant.now().plus(DEFAULT_STALE_DELETE_TIMEOUT);
        while (Instant.now().isBefore(deadline)) {
            Optional<CoolifyApplicationSummary> observed = coolifyApiClient.getApplication(connection, applicationUuid);
            if (observed == null || observed.isEmpty()) {
                return;
            }
            try {
                Thread.sleep(DEFAULT_STALE_DELETE_POLL_INTERVAL.toMillis());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private boolean applicationMatchesScope(CoolifyApplicationSummary application, CoolifyResourceScope scope) {
        if (!scope.customerGrouped()) {
            return true;
        }
        JsonNode raw = application.raw();
        String projectUuid = textFirst(raw, "project_uuid", "projectUuid");
        if (!StringUtils.hasText(projectUuid)) {
            projectUuid = raw.path("project").path("uuid").asText(null);
        }
        if (!sameText(projectUuid, scope.projectUuid())) {
            return false;
        }
        String environmentUuid = textFirst(raw, "environment_uuid", "environmentUuid");
        if (!StringUtils.hasText(environmentUuid)) {
            environmentUuid = raw.path("environment").path("uuid").asText(null);
        }
        if (StringUtils.hasText(scope.environmentUuid()) && StringUtils.hasText(environmentUuid)) {
            return sameText(environmentUuid, scope.environmentUuid());
        }
        String environmentName = textFirst(raw, "environment_name", "environmentName");
        if (!StringUtils.hasText(environmentName)) {
            environmentName = raw.path("environment").path("name").asText(null);
        }
        return !StringUtils.hasText(environmentName) || sameText(environmentName, scope.environmentName());
    }

    private boolean databaseMatchesScope(CoolifyDatabaseSummary database, CoolifyResourceScope scope) {
        if (!scope.customerGrouped()) {
            return true;
        }
        JsonNode raw = database.raw();
        String projectUuid = textFirst(raw, "project_uuid", "projectUuid");
        if (!StringUtils.hasText(projectUuid)) {
            projectUuid = raw.path("project").path("uuid").asText(null);
        }
        if (!sameText(projectUuid, scope.projectUuid())) {
            return false;
        }
        String environmentUuid = textFirst(raw, "environment_uuid", "environmentUuid");
        if (!StringUtils.hasText(environmentUuid)) {
            environmentUuid = raw.path("environment").path("uuid").asText(null);
        }
        if (StringUtils.hasText(scope.environmentUuid()) && StringUtils.hasText(environmentUuid)) {
            return sameText(environmentUuid, scope.environmentUuid());
        }
        String environmentName = textFirst(raw, "environment_name", "environmentName");
        if (!StringUtils.hasText(environmentName)) {
            environmentName = raw.path("environment").path("name").asText(null);
        }
        return !StringUtils.hasText(environmentName) || sameText(environmentName, scope.environmentName());
    }

    private CoolifyApplicationSummary waitForApplicationReady(CoolifyConnection connection,
                                                              String applicationUuid,
                                                              JsonNode resourceDefaults,
                                                              CoolifyApplicationSummary fallback,
                                                              CoolifyActionResponse deployResponse) {
        Duration timeout = durationSeconds(
            resourceDefaults,
            "deploySettleTimeoutSeconds",
            positiveDurationSeconds(connection.config().deploymentTimeoutSeconds(), DEFAULT_DEPLOY_SETTLE_TIMEOUT)
        );
        Duration pollInterval = durationSeconds(
            resourceDefaults,
            "deploySettlePollSeconds",
            positiveDurationSeconds(connection.config().deploymentPollIntervalSeconds(), DEFAULT_DEPLOY_SETTLE_POLL_INTERVAL)
        );
        Instant deadline = Instant.now().plus(timeout);
        CoolifyApplicationSummary latest = coolifyApiClient.getApplication(connection, applicationUuid).orElse(fallback);
        String deploymentUuid = deployResponse == null ? null : deployResponse.deploymentUuid();
        CoolifyDeploymentSummary deployment = observeCoolifyDeployment(connection, deploymentUuid);
        while (!coolifyDeploymentReady(deployment, deploymentUuid) && Instant.now().isBefore(deadline)) {
            try {
                Thread.sleep(pollInterval.toMillis());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return latest;
            }
            latest = coolifyApiClient.getApplication(connection, applicationUuid).orElse(latest);
            deployment = observeCoolifyDeployment(connection, deploymentUuid);
        }
        if (!coolifyDeploymentReady(deployment, deploymentUuid)) {
            throw new IllegalStateException(
                "Timed out waiting for Coolify deployment to finish for application " + applicationUuid + "."
            );
        }
        if (coolifyDeploymentFailed(deployment)) {
            throw new IllegalStateException(
                "Coolify deployment failed for application " + applicationUuid
                    + " (deployment=" + deployment.deploymentUuid()
                    + ", status=" + deployment.status() + ")."
            );
        }
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

    private CoolifyDeploymentSummary observeCoolifyDeployment(CoolifyConnection connection, String deploymentUuid) {
        if (!StringUtils.hasText(deploymentUuid)) {
            return null;
        }
        return coolifyApiClient.getDeployment(connection, deploymentUuid).orElse(null);
    }

    private boolean coolifyDeploymentReady(CoolifyDeploymentSummary deployment, String deploymentUuid) {
        if (!StringUtils.hasText(deploymentUuid)) {
            return true;
        }
        return deployment != null && (coolifyDeploymentFinished(deployment) || coolifyDeploymentFailed(deployment));
    }

    private boolean coolifyDeploymentFinished(CoolifyDeploymentSummary deployment) {
        if (deployment == null) {
            return false;
        }
        String normalized = normalizeStatus(deployment.status(), "");
        return normalized.equals("FINISHED")
            || normalized.equals("SUCCESS")
            || normalized.equals("SUCCEEDED")
            || normalized.equals("COMPLETED")
            || StringUtils.hasText(deployment.finishedAt()) && !coolifyDeploymentFailed(deployment);
    }

    private boolean coolifyDeploymentFailed(CoolifyDeploymentSummary deployment) {
        if (deployment == null) {
            return false;
        }
        String normalized = normalizeStatus(deployment.status(), "");
        return normalized.contains("FAILED")
            || normalized.contains("ERROR")
            || normalized.contains("CANCELLED")
            || normalized.contains("CANCELED");
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

    private Duration positiveDurationSeconds(int seconds, Duration fallback) {
        return seconds > 0 ? Duration.ofSeconds(seconds) : fallback;
    }

    private boolean applicationReady(CoolifyApplicationSummary application) {
        if (application == null || !StringUtils.hasText(application.status())) {
            return false;
        }
        String normalized = normalizeStatus(application.status(), "");
        return normalized.startsWith("RUNNING") && !normalized.contains("UNHEALTHY");
    }

    private boolean databaseReady(CoolifyDatabaseSummary database) {
        if (database == null || !StringUtils.hasText(database.status())) {
            return false;
        }
        String normalized = normalizeStatus(database.status(), "");
        return normalized.startsWith("RUNNING")
            || normalized.equals("HEALTHY")
            || normalized.equals("STARTED")
            || normalized.equals("ACTIVE");
    }

    private boolean applicationsReady(CoolifyApplicationSummary runtimeApplication,
                                      CoolifyApplicationSummary connectorApplication,
                                      CoolifyApplicationSummary vectorizationRunnerApplication) {
        return applicationReady(runtimeApplication)
            && (connectorApplication == null || applicationReady(connectorApplication))
            && (vectorizationRunnerApplication == null || applicationReady(vectorizationRunnerApplication));
    }

    private String normalizeName(String value) {
        String normalized = value == null ? "ai-fabric-runtime" : value.toLowerCase(Locale.ROOT);
        normalized = normalized.replaceAll("[^a-z0-9-]+", "-").replaceAll("-{2,}", "-");
        normalized = normalized.replaceAll("^-+", "").replaceAll("-+$", "");
        return normalized.length() > 48 ? normalized.substring(0, 48).replaceAll("-+$", "") : normalized;
    }

    private String normalizeScopedName(String value, int maxLength) {
        String normalized = value == null ? "unknown" : value.toLowerCase(Locale.ROOT);
        normalized = normalized.replaceAll("[^a-z0-9-]+", "-").replaceAll("-{2,}", "-");
        normalized = normalized.replaceAll("^-+", "").replaceAll("-+$", "");
        if (!StringUtils.hasText(normalized)) {
            normalized = "unknown";
        }
        return normalized.length() > maxLength
            ? normalized.substring(0, maxLength).replaceAll("-+$", "")
            : normalized;
    }

    private String normalizeDatabaseIdentifier(String value) {
        String normalized = value == null ? "runtime" : value.toLowerCase(Locale.ROOT);
        normalized = normalized.replaceAll("[^a-z0-9_]+", "_").replaceAll("_{2,}", "_");
        normalized = normalized.replaceAll("^_+", "").replaceAll("_+$", "");
        if (!StringUtils.hasText(normalized)) {
            normalized = "runtime";
        }
        if (!Character.isLetter(normalized.charAt(0))) {
            normalized = "r_" + normalized;
        }
        return normalized.length() > 48 ? normalized.substring(0, 48).replaceAll("_+$", "") : normalized;
    }

    private String runtimePostgresPasswordSecretName(String deploymentId, String profileId) {
        return "MANAGED_RUNTIME_POSTGRES_PASSWORD_DEP_"
            + secretToken(deploymentId)
            + "_PROFILE_"
            + secretToken(profileId);
    }

    private String secretToken(String value) {
        String normalized = value == null ? "UNKNOWN" : value.toUpperCase(Locale.ROOT);
        normalized = normalized.replaceAll("[^A-Z0-9]+", "_").replaceAll("_{2,}", "_");
        normalized = normalized.replaceAll("^_+", "").replaceAll("_+$", "");
        return StringUtils.hasText(normalized) ? normalized : "UNKNOWN";
    }

    private String generateSecretValue() {
        byte[] bytes = new byte[36];
        SECRET_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
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
            : SERVICE_ROLE_VECTORIZATION_RUNNER.equals(serviceRole)
                ? deployment.getId() + "-vectorization-runner"
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
        copyText(raw, details, "project_uuid");
        copyText(raw, details, "environment_uuid");
        copyText(raw, details, "environment_name");
        copyText(raw, details, "created_at");
        copyText(raw, details, "updated_at");
        putIfText(details, "projectName", raw.path("project").path("name").asText(null));
        putIfText(details, "projectUuid", raw.path("project").path("uuid").asText(null));
        putIfText(details, "environmentName", raw.path("environment").path("name").asText(null));
        putIfText(details, "environmentUuid", raw.path("environment").path("uuid").asText(null));
        putIfText(details, "destinationUuid", raw.path("destination").path("uuid").asText(null));
        putIfText(details, "destinationNetwork", raw.path("destination").path("network").asText(null));
        putIfText(details, "serverUuid", raw.path("destination").path("server").path("uuid").asText(null));
        putIfText(details, "serverName", raw.path("destination").path("server").path("name").asText(null));
        return details;
    }

    private JsonNode safeDatabaseDetails(CoolifyDatabaseSummary database) {
        ObjectNode details = objectMapper.createObjectNode();
        putIfText(details, "uuid", database.uuid());
        putIfText(details, "name", database.name());
        putIfText(details, "status", database.status());
        putIfText(details, "databaseType", database.databaseType());
        putIfText(details, "postgresUser", database.postgresUser());
        putIfText(details, "postgresDatabase", database.postgresDatabase());
        JsonNode raw = database.raw();
        copyText(raw, details, "project_uuid");
        copyText(raw, details, "environment_uuid");
        copyText(raw, details, "environment_name");
        copyText(raw, details, "created_at");
        copyText(raw, details, "updated_at");
        putIfText(details, "projectName", raw.path("project").path("name").asText(null));
        putIfText(details, "projectUuid", raw.path("project").path("uuid").asText(null));
        putIfText(details, "environmentName", raw.path("environment").path("name").asText(null));
        putIfText(details, "environmentUuid", raw.path("environment").path("uuid").asText(null));
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
                                            DeploymentProviderResourceHandleEntity runtimeDatabaseHandle,
                                            DeploymentProviderResourceHandleEntity connectorHandle,
                                            CoolifyApplicationSummary runtimeApplication,
                                            CoolifyApplicationSummary connectorApplication,
                                            DeploymentProviderResourceHandleEntity vectorizationRunnerHandle,
                                            CoolifyApplicationSummary vectorizationRunnerApplication,
                                            CoolifyResourceScope scope,
                                            CoolifyProvisioningSource source,
                                            int runtimeEnvCount,
                                            int connectorEnvCount,
                                            int vectorizationRunnerEnvCount,
                                            CoolifyActionResponse runtimeDeployResponse,
                                            CoolifyActionResponse connectorDeployResponse,
                                            CoolifyActionResponse vectorizationRunnerDeployResponse,
                                            CoolifyRuntimeDatabaseBinding runtimeDatabaseBinding,
                                            ManagedVectorProvisioningResult managedVectorProvisioningResult) {
        ObjectNode details = objectMapper.createObjectNode();
        details.put("provider", "COOLIFY");
        details.put("targetProfileId", profile.getId());
        if (managedVectorProvisioningResult != null) {
            details.set("managedVectorProvisioning", managedVectorProvisioningResult.details());
            if (managedVectorProvisioningResult.effectiveProviderConfig() != null) {
                details.set("effectiveProviderConfig", managedVectorProvisioningResult.effectiveProviderConfig());
            }
        }
        writeScopeDetails(details, scope);
        details.put("providerResourceHandleId", runtimeHandle.getId());
        details.put("applicationUuid", runtimeApplication.uuid());
        details.put("applicationName", runtimeApplication.name());
        details.put("fqdn", runtimeApplication.fqdn());
        details.put("status", runtimeApplication.status());
        details.put("runtimeProviderResourceHandleId", runtimeHandle.getId());
        details.put("runtimeApplicationUuid", runtimeApplication.uuid());
        details.put("runtimeFqdn", runtimeApplication.fqdn());
        details.put("runtimeStatus", runtimeApplication.status());
        if (runtimeDatabaseHandle != null && runtimeDatabaseBinding != null) {
            details.put("runtimeDatabaseProviderResourceHandleId", runtimeDatabaseHandle.getId());
            details.put("runtimeDatabaseUuid", runtimeDatabaseBinding.database().uuid());
            details.put("runtimeDatabaseName", runtimeDatabaseBinding.database().name());
            details.put("runtimeDatabaseMode", runtimeDatabaseBinding.mode());
            details.put("runtimeDatabaseStatus", runtimeDatabaseBinding.database().status());
            details.put("runtimeDatabasePasswordSecret", runtimeDatabaseBinding.passwordSecretName());
            ObjectNode services = objectNode(objectNode(details, "coolify"), "services");
            ObjectNode database = objectNode(services, "runtimeDatabase");
            database.put("serviceId", runtimeDatabaseBinding.database().uuid());
            database.put("serviceName", runtimeDatabaseBinding.database().name());
            database.put("databaseMode", runtimeDatabaseBinding.mode());
            database.put("databaseName", runtimeDatabaseBinding.database().postgresDatabase());
            database.put("username", runtimeDatabaseBinding.database().postgresUser());
            database.put("status", runtimeDatabaseBinding.database().status());
            database.put("passwordSecret", runtimeDatabaseBinding.passwordSecretName());
        }
        if (connectorHandle != null && connectorApplication != null) {
            details.put("connectorProviderResourceHandleId", connectorHandle.getId());
            details.put("connectorApplicationUuid", connectorApplication.uuid());
            details.put("connectorFqdn", connectorApplication.fqdn());
            details.put("connectorStatus", connectorApplication.status());
        }
        if (vectorizationRunnerHandle != null && vectorizationRunnerApplication != null) {
            details.put("vectorizationRunnerProviderResourceHandleId", vectorizationRunnerHandle.getId());
            details.put("vectorizationRunnerApplicationUuid", vectorizationRunnerApplication.uuid());
            details.put("vectorizationRunnerFqdn", vectorizationRunnerApplication.fqdn());
            details.put("vectorizationRunnerStatus", vectorizationRunnerApplication.status());
            ObjectNode services = objectNode(objectNode(details, "coolify"), "services");
            ObjectNode runner = objectNode(services, "vectorizationRunner");
            runner.put("serviceId", vectorizationRunnerApplication.uuid());
            runner.put("serviceName", vectorizationRunnerApplication.name());
            runner.put("deploymentStatus", applicationReady(vectorizationRunnerApplication) ? "SUCCESS" : "DEPLOY_REQUESTED");
            if (vectorizationRunnerDeployResponse != null) {
                runner.put("deploymentId", vectorizationRunnerDeployResponse.deploymentUuid());
            }
        }
        writeSourceDetails(details, source);
        details.put("environmentVariableCount", runtimeEnvCount + connectorEnvCount + vectorizationRunnerEnvCount);
        details.put("runtimeEnvironmentVariableCount", runtimeEnvCount);
        details.put("connectorEnvironmentVariableCount", connectorEnvCount);
        details.put("vectorizationRunnerEnvironmentVariableCount", vectorizationRunnerEnvCount);
        details.put("deploymentUuid", runtimeDeployResponse.deploymentUuid());
        details.put("statusMessage", runtimeDeployResponse.message());
        if (connectorDeployResponse != null) {
            details.put("connectorDeploymentUuid", connectorDeployResponse.deploymentUuid());
            details.put("connectorStatusMessage", connectorDeployResponse.message());
        }
        if (vectorizationRunnerDeployResponse != null) {
            details.put("vectorizationRunnerDeploymentUuid", vectorizationRunnerDeployResponse.deploymentUuid());
            details.put("vectorizationRunnerStatusMessage", vectorizationRunnerDeployResponse.message());
        }
        details.put("dnsSkipped", !StringUtils.hasText(runtimeApplication.fqdn()));
        details.put("generatedAt", Instant.now().truncatedTo(ChronoUnit.SECONDS).toString());
        return details.toPrettyString();
    }

    private String handleMetadata(CoolifyApplicationSummary application,
                                  CoolifyProvisioningSource source,
                                  CoolifyResourceScope scope,
                                  String serviceRole,
                                  int envCount,
                                  CoolifyActionResponse deployResponse) {
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("applicationName", application.name());
        metadata.put("serviceRole", serviceRole);
        writeScopeDetails(metadata, scope);
        writeSourceDetails(metadata, source);
        metadata.put("environmentVariableCount", envCount);
        if (deployResponse != null) {
            metadata.put("deploymentUuid", deployResponse.deploymentUuid());
            metadata.put("statusMessage", deployResponse.message());
        }
        return metadata.toPrettyString();
    }

    private String databaseHandleMetadata(CoolifyRuntimeDatabaseBinding binding,
                                          CoolifyResourceScope scope) {
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("databaseName", binding.database().name());
        metadata.put("serviceRole", SERVICE_ROLE_RUNTIME);
        metadata.put("databaseMode", binding.mode());
        metadata.put("postgresDatabase", binding.database().postgresDatabase());
        metadata.put("postgresUser", binding.database().postgresUser());
        metadata.put("passwordSecretName", binding.passwordSecretName());
        writeScopeDetails(metadata, scope);
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
            if (source.vectorizationRunnerPlan() != null) {
                target.put(
                    "vectorizationRunnerDockerfileLocation",
                    normalizeDockerfileLocation(source.vectorizationRunnerPlan().dockerfilePath())
                );
            }
            if (source.plan() != null) {
                target.put("railwayPlanRepository", source.plan().repository());
                target.put("railwayPlanBranch", source.plan().branch());
            }
        }
    }

    private void writeScopeDetails(ObjectNode target, CoolifyResourceScope scope) {
        target.put("customerProjectGroupingEnabled", scope.customerGrouped());
        putIfText(target, "projectUuid", scope.projectUuid());
        putIfText(target, "projectName", scope.projectName());
        putIfText(target, "environmentName", scope.environmentName());
        putIfText(target, "environmentUuid", scope.environmentUuid());
    }

    private ObjectNode objectNode(ObjectNode parent, String field) {
        JsonNode existing = parent.path(field);
        if (existing instanceof ObjectNode objectNode) {
            return objectNode;
        }
        ObjectNode created = objectMapper.createObjectNode();
        parent.set(field, created);
        return created;
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

    private String textFirst(JsonNode json, String... fields) {
        if (json == null || json.isMissingNode() || json.isNull()) {
            return null;
        }
        for (String field : fields) {
            String value = json.path(field).asText(null);
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
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
        String candidate = value.trim();
        String lower = candidate.toLowerCase(Locale.ROOT);
        if (lower.startsWith("https://github.com/") || lower.startsWith("http://github.com/")) {
            candidate = candidate.substring(candidate.indexOf("github.com/") + "github.com/".length());
        } else if (lower.startsWith("ssh://git@github.com/")) {
            candidate = candidate.substring("ssh://git@github.com/".length());
        } else if (lower.startsWith("git@github.com:")) {
            candidate = candidate.substring("git@github.com:".length());
            String slug = candidate.replaceAll("^/+", "").replaceAll("/+$", "");
            if (slug.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+(\\.git)?")) {
                return "git@github.com:" + (slug.endsWith(".git") ? slug : slug + ".git");
            }
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Coolify Git source repository must be a GitHub owner/repo slug or github.com URL."
            );
        } else if (lower.contains("://") || lower.startsWith("git@")) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Coolify Git source repository must be a GitHub owner/repo slug or github.com URL."
            );
        }
        String slug = candidate.replaceAll("^/+", "").replaceAll("/+$", "");
        if (slug.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+(\\.git)?")) {
            return slug;
        }
        throw new ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "Coolify Git source repository must be a GitHub owner/repo slug or github.com URL."
        );
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

    private boolean sameText(String left, String right) {
        if (!StringUtils.hasText(left) || !StringUtils.hasText(right)) {
            return false;
        }
        return left.trim().equalsIgnoreCase(right.trim());
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
        RailwayServicePlanSummary vectorizationRunnerPlan,
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

    private record CoolifyResourceScope(
        String projectUuid,
        String projectName,
        String environmentName,
        String environmentUuid,
        boolean customerGrouped
    ) {
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
