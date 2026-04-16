package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.config.PlatformProvisioningProperties;
import com.ai.fabric.platform.backend.config.PlatformVerificationProperties;
import com.ai.fabric.platform.backend.config.PlatformInferenceProvisioningProperties;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.model.RailwayEnvVarSummary;
import com.ai.fabric.platform.backend.deployment.model.RailwayProvisioningPlanSummary;
import com.ai.fabric.platform.backend.deployment.model.RailwayServicePlanSummary;
import com.ai.fabric.platform.backend.marketplace.entity.PlatformManagedInferenceEndpointEntity;
import com.ai.fabric.platform.backend.marketplace.entity.PlatformManagedInferenceServiceEntity;
import com.ai.fabric.platform.backend.marketplace.repository.PlatformManagedInferenceEndpointRepository;
import com.ai.fabric.platform.backend.marketplace.repository.PlatformManagedInferenceServiceRepository;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.vectorization.service.VectorizationRunnerProvisioningService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

@Service
public class RailwayApiProvisioningProvider implements DeploymentProvisioningProvider {

    private static final Logger log = LoggerFactory.getLogger(RailwayApiProvisioningProvider.class);
    private static final Duration HEALTH_FALLBACK_GRACE_PERIOD = Duration.ofSeconds(45);
    private static final int HEALTH_FALLBACK_REQUIRED_SUCCESSES = 2;
    private static final HttpClient HEALTHCHECK_HTTP_CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    private final PlatformProvisioningProperties provisioningProperties;
    private final PlatformVerificationProperties verificationProperties;
    private final PlatformInferenceProvisioningProperties inferenceProvisioningProperties;
    private final DeploymentManagedVectorProvisioningService deploymentManagedVectorProvisioningService;
    private final DeploymentManagedVectorResourceService deploymentManagedVectorResourceService;
    private final RailwayProvisioningPlanService railwayProvisioningPlanService;
    private final DeploymentSourceResolver deploymentSourceResolver;
    private final RailwayGraphqlClient railwayGraphqlClient;
    private final PlatformSecretService platformSecretService;
    private final PlatformManagedInferenceServiceRepository platformManagedInferenceServiceRepository;
    private final PlatformManagedInferenceEndpointRepository platformManagedInferenceEndpointRepository;
    private final VectorizationRunnerProvisioningService vectorizationRunnerProvisioningService;
    private final ObjectMapper objectMapper;

    public RailwayApiProvisioningProvider(PlatformProvisioningProperties provisioningProperties,
                                          PlatformVerificationProperties verificationProperties,
                                          DeploymentManagedVectorProvisioningService deploymentManagedVectorProvisioningService,
                                          DeploymentManagedVectorResourceService deploymentManagedVectorResourceService,
                                          RailwayProvisioningPlanService railwayProvisioningPlanService,
                                          DeploymentSourceResolver deploymentSourceResolver,
                                          RailwayGraphqlClient railwayGraphqlClient,
                                          PlatformSecretService platformSecretService,
                                          ObjectMapper objectMapper) {
        this(
            provisioningProperties,
            verificationProperties,
            new PlatformInferenceProvisioningProperties(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null),
            deploymentManagedVectorProvisioningService,
            deploymentManagedVectorResourceService,
            railwayProvisioningPlanService,
            deploymentSourceResolver,
            railwayGraphqlClient,
            platformSecretService,
            null,
            null,
            null,
            objectMapper
        );
    }

    @Autowired
    public RailwayApiProvisioningProvider(PlatformProvisioningProperties provisioningProperties,
                                          PlatformVerificationProperties verificationProperties,
                                          PlatformInferenceProvisioningProperties inferenceProvisioningProperties,
                                          DeploymentManagedVectorProvisioningService deploymentManagedVectorProvisioningService,
                                          DeploymentManagedVectorResourceService deploymentManagedVectorResourceService,
                                          RailwayProvisioningPlanService railwayProvisioningPlanService,
                                          DeploymentSourceResolver deploymentSourceResolver,
                                          RailwayGraphqlClient railwayGraphqlClient,
                                          PlatformSecretService platformSecretService,
                                          PlatformManagedInferenceServiceRepository platformManagedInferenceServiceRepository,
                                          PlatformManagedInferenceEndpointRepository platformManagedInferenceEndpointRepository,
                                          VectorizationRunnerProvisioningService vectorizationRunnerProvisioningService,
                                          ObjectMapper objectMapper) {
        this.provisioningProperties = provisioningProperties;
        this.verificationProperties = verificationProperties;
        this.inferenceProvisioningProperties = inferenceProvisioningProperties;
        this.deploymentManagedVectorProvisioningService = deploymentManagedVectorProvisioningService;
        this.deploymentManagedVectorResourceService = deploymentManagedVectorResourceService;
        this.railwayProvisioningPlanService = railwayProvisioningPlanService;
        this.deploymentSourceResolver = deploymentSourceResolver;
        this.railwayGraphqlClient = railwayGraphqlClient;
        this.platformSecretService = platformSecretService;
        this.platformManagedInferenceServiceRepository = platformManagedInferenceServiceRepository;
        this.platformManagedInferenceEndpointRepository = platformManagedInferenceEndpointRepository;
        this.vectorizationRunnerProvisioningService = vectorizationRunnerProvisioningService;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(String mode) {
        return "RAILWAY_API".equalsIgnoreCase(mode);
    }

    @Override
    public ProvisioningResult provision(DeploymentEntity deployment,
                                        DeploymentVersionEntity version,
                                        DeploymentReleaseEntity release,
                                        ProvisioningProgressTracker progressTracker) {
        validateConfiguration();
        String sourceRepository = deploymentSourceResolver.resolveRepository(deployment);
        String sourceBranch = deploymentSourceResolver.resolveBranch(deployment);
        deploymentSourceResolver.validateEffectiveSource(sourceRepository, sourceBranch);

        ManagedVectorProvisioningResult managedVectorProvisioningResult = deploymentManagedVectorProvisioningService
            .requiresProvisioning(version)
            ? trackedStep(
                progressTracker,
                "ensure_vector_backend",
                "Create or reconcile managed external vector resources before runtime deployment.",
                () -> deploymentManagedVectorProvisioningService.ensureProvisioned(deployment, version)
            )
            : deploymentManagedVectorProvisioningService.ensureProvisioned(deployment, version);
        deploymentManagedVectorResourceService.syncProvisionedResources(
            deployment,
            version,
            release,
            managedVectorProvisioningResult
        );

        JsonNode effectiveProviderConfig = managedVectorProvisioningResult.effectiveProviderConfig();
        JsonNode initialProviderConfig = effectiveProviderConfig;
        RailwayProvisioningPlanSummary plan = trackedStep(
            progressTracker,
            "publish_artifacts",
            "Resolve immutable config artifact URLs for the selected version.",
            () -> railwayProvisioningPlanService.buildPlan(
                deployment,
                version,
                initialProviderConfig
            )
        );
        ObjectNode details = objectMapper.createObjectNode();
        mergePlanDetails(details, plan);
        details.put("provider", "RAILWAY_API");
        details.put("releaseId", release.getId());
        details.put("generatedAt", Instant.now().truncatedTo(ChronoUnit.SECONDS).toString());
        details.set("managedVectorProvisioning", managedVectorProvisioningResult.details());
        details.set("effectiveProviderConfig", copyJson(effectiveProviderConfig));
        mergeTrackedDetails(progressTracker, details);
        String environmentName = resolveEnvironmentName(deployment);

        log.info(
            "Provisioning live Railway deployment: deploymentId={}, versionId={}, projectName={}",
            deployment.getId(),
            version.getId(),
            plan.projectName()
        );

        String projectName = plan.projectName();
        RailwayProjectContext projectContext = trackedStep(
            progressTracker,
            "prepare_project",
            "Create or reuse the Railway project for this customer environment.",
            () -> {
                RailwayGraphqlClient.RailwayProjectSnapshot existingProject = railwayGraphqlClient.findProjectByName(
                    provisioningProperties.workspaceId(),
                    projectName
                );
                if (existingProject == null) {
                    existingProject = railwayGraphqlClient.createProject(
                        provisioningProperties.workspaceId(),
                        projectName,
                        environmentName
                    );
                }
                RailwayGraphqlClient.RailwayEnvironmentSummary environment = existingProject.environmentNamed(environmentName);
                if (environment == null) {
                    environment = railwayGraphqlClient.createEnvironment(existingProject.id(), environmentName);
                }
                return new RailwayProjectContext(existingProject, environment);
            }
        );
        RailwayGraphqlClient.RailwayProjectSnapshot project = projectContext.project();
        RailwayGraphqlClient.RailwayEnvironmentSummary environment = projectContext.environment();

        DedicatedEmbeddingWorkerProvisioningResult embeddingWorkerResult = null;
        if (plan.services().embeddingWorker() != null) {
            JsonNode providerConfigBeforeWorker = effectiveProviderConfig;
            RailwayServicePlanSummary embeddingWorkerPlan = plan.services().embeddingWorker();
            embeddingWorkerResult = trackedStep(
                progressTracker,
                "configure_embedding_worker",
                "Provision or reconcile the deployment-dedicated embedding worker before runtime deployment.",
                () -> provisionDedicatedEmbeddingWorker(
                    deployment,
                    version,
                    project,
                    environment,
                    embeddingWorkerPlan,
                    providerConfigBeforeWorker
                )
            );
            effectiveProviderConfig = embeddingWorkerResult.effectiveProviderConfig();
            JsonNode rebuiltProviderConfig = effectiveProviderConfig;
            plan = trackedStep(
                progressTracker,
                "publish_artifacts",
                "Rebuild deployment plan with resolved dedicated embedding worker endpoint settings.",
                () -> railwayProvisioningPlanService.buildPlan(
                    deployment,
                    version,
                    rebuiltProviderConfig
                )
            );
            mergePlanDetails(details, plan);
            details.set("effectiveProviderConfig", copyJson(effectiveProviderConfig));
            mergeTrackedDetails(progressTracker, details);
        }

        RailwayProvisioningPlanSummary finalPlan = plan;
        RailwayServicePlanSummary runnerPlan = plan.services().vectorizationRunner();
        if (runnerPlan != null) {
            if (vectorizationRunnerProvisioningService != null) {
                vectorizationRunnerProvisioningService.ensureManagedRegistration(deployment);
            }
        } else if (vectorizationRunnerProvisioningService != null) {
            vectorizationRunnerProvisioningService.clearManagedRegistrationSecret(deployment.getId());
        }

        RailwayGraphqlClient.RailwayServiceSummary runtimeService = ensureService(
            project,
            deployment,
            plan.services().runtime().serviceName()
        );
        RailwayGraphqlClient.RailwayServiceSummary connectorService = ensureService(
            project,
            deployment,
            plan.services().restConnector().serviceName()
        );
        RailwayGraphqlClient.RailwayServiceSummary runnerService = null;
        if (runnerPlan != null) {
            runnerService = ensureService(project, deployment, runnerPlan.serviceName());
        } else {
            RailwayGraphqlClient.RailwayServiceSummary staleRunner = project.serviceNamed(
                railwayProvisioningPlanService.vectorizationRunnerServiceName(deployment)
            );
            if (staleRunner != null) {
                railwayGraphqlClient.deleteService(staleRunner.id());
            }
        }
        recordRailwayContext(
            details,
            project,
            environment,
            runtimeService,
            connectorService,
            runnerService,
            embeddingWorkerResult == null ? null : embeddingWorkerResult.service()
        );
        final RailwayGraphqlClient.RailwayServiceSummary deploymentRunnerService = runnerService;
        final RailwayServicePlanSummary deploymentRunnerPlan = runnerPlan;
        final DedicatedEmbeddingWorkerProvisioningResult finalEmbeddingWorkerResult = embeddingWorkerResult;
        String runtimeServiceBaseUrl = ensureServiceDomain(
            project.id(),
            environment.id(),
            runtimeService.id(),
            deployment.getRuntimeBaseUrl()
        );
        String connectorServiceBaseUrl = ensureServiceDomain(
            project.id(),
            environment.id(),
            connectorService.id(),
            deployment.getConnectorBaseUrl()
        );
        recordActivatedServices(
            details,
            "PENDING",
            "PENDING",
            runnerService == null ? null : "PENDING",
            embeddingWorkerResult == null ? null : embeddingWorkerResult.deploymentStatus(),
            runtimeServiceBaseUrl,
            connectorServiceBaseUrl,
            embeddingWorkerResult == null ? null : embeddingWorkerResult.baseUrl()
        );
        mergeTrackedDetails(progressTracker, details);

        trackedStep(
            progressTracker,
            "configure_runtime",
            "Create or update the runtime service root and its environment variables.",
            () -> {
                configureServiceInstance(
                    project.id(),
                    runtimeService.id(),
                    environment.id(),
                    finalPlan.services().runtime(),
                    verificationProperties.runtimeHealthPath(),
                    deployment,
                    runtimeServiceBaseUrl,
                    connectorServiceBaseUrl
                );
                return null;
            }
        );
        trackedStep(
            progressTracker,
            "configure_rest_connector",
            "Create or update the REST connector service root and its environment variables.",
            () -> {
                configureServiceInstance(
                    project.id(),
                    connectorService.id(),
                    environment.id(),
                    finalPlan.services().restConnector(),
                    verificationProperties.connectorHealthPath(),
                    deployment,
                    runtimeServiceBaseUrl,
                    connectorServiceBaseUrl
                );
                return null;
            }
        );
        if (runnerPlan != null && runnerService != null) {
            RailwayGraphqlClient.RailwayServiceSummary finalRunnerService = runnerService;
            trackedStep(
                progressTracker,
                "configure_vectorization_runner",
                "Create or update the vectorization runner service root and its environment variables.",
                () -> {
                    configureServiceInstance(
                        project.id(),
                        finalRunnerService.id(),
                        environment.id(),
                        runnerPlan,
                        verificationProperties.runtimeHealthPath(),
                        deployment,
                        runtimeServiceBaseUrl,
                        connectorServiceBaseUrl
                    );
                    return null;
                }
            );
        }

        RailwayDeploymentContext deploymentContext = trackedStep(
            progressTracker,
            "trigger_deploy",
            runnerPlan == null
                ? "Commit staged changes or trigger Railway deployment/redeploy for runtime and REST connector."
                : "Commit staged changes or trigger Railway deployment/redeploy for runtime, REST connector, and vectorization runner.",
            () -> {
                Instant releaseStartedAt = release.getAppliedAt() != null ? release.getAppliedAt() : Instant.now();
                if (railwayGraphqlClient.hasStagedChanges(environment.id())) {
                    railwayGraphqlClient.commitStagedChanges(environment.id());
                }
                String runtimeDeploymentId = resolveOrTriggerDeployment(
                    runtimeService.id(),
                    environment.id(),
                    finalPlan.services().runtime().serviceName(),
                    releaseStartedAt
                );
                String connectorDeploymentId = resolveOrTriggerDeployment(
                    connectorService.id(),
                    environment.id(),
                    finalPlan.services().restConnector().serviceName(),
                    releaseStartedAt
                );
                String runnerDeploymentId = deploymentRunnerPlan == null || deploymentRunnerService == null
                    ? null
                    : resolveOrTriggerDeployment(
                        deploymentRunnerService.id(),
                        environment.id(),
                        deploymentRunnerPlan.serviceName(),
                        releaseStartedAt
                    );
                recordTriggeredDeployments(details, runtimeDeploymentId, connectorDeploymentId, runnerDeploymentId);
                mergeTrackedDetails(progressTracker, details);
                return new RailwayDeploymentContext(runtimeDeploymentId, connectorDeploymentId, runnerDeploymentId);
            }
        );

        RailwayActivatedServices activatedServices = trackedStep(
            progressTracker,
            "wait_for_active",
            "Wait for Railway deployment states to become active.",
            () -> {
                RailwayGraphqlClient.RailwayDeploymentSummary runtimeDeployment = awaitSuccessfulDeployment(
                    deploymentContext.runtimeDeploymentId(),
                    finalPlan.services().runtime().serviceName(),
                    runtimeServiceBaseUrl,
                    verificationProperties.runtimeHealthPath()
                );
                RailwayGraphqlClient.RailwayDeploymentSummary connectorDeployment = awaitSuccessfulDeployment(
                    deploymentContext.connectorDeploymentId(),
                    finalPlan.services().restConnector().serviceName(),
                    connectorServiceBaseUrl,
                    verificationProperties.connectorHealthPath()
                );
                RailwayGraphqlClient.RailwayDeploymentSummary runnerDeployment = runnerPlan == null
                    || deploymentContext.vectorizationRunnerDeploymentId() == null
                    ? null
                    : awaitSuccessfulDeployment(
                        deploymentContext.vectorizationRunnerDeploymentId(),
                        runnerPlan.serviceName()
                    );
                recordActivatedServices(
                    details,
                    activatedServicesDeploymentStatus(runtimeDeployment),
                    activatedServicesDeploymentStatus(connectorDeployment),
                    activatedServicesDeploymentStatus(runnerDeployment),
                    finalEmbeddingWorkerResult == null ? null : finalEmbeddingWorkerResult.deploymentStatus(),
                    runtimeServiceBaseUrl,
                    connectorServiceBaseUrl,
                    finalEmbeddingWorkerResult == null ? null : finalEmbeddingWorkerResult.baseUrl()
                );
                mergeTrackedDetails(progressTracker, details);
                return new RailwayActivatedServices(
                    runtimeDeployment,
                    connectorDeployment,
                    runnerDeployment,
                    runtimeServiceBaseUrl,
                    connectorServiceBaseUrl
                );
            }
        );

        details.put("statusMessage", "Railway project and services reconciled successfully.");
        mergeTrackedDetails(progressTracker, details);

        return new ProvisioningResult(
            "ACTIVE",
            "RAILWAY_API",
            activatedServices.runtimeBaseUrl(),
            activatedServices.connectorBaseUrl(),
            details.toPrettyString()
        );
    }

    private RailwayGraphqlClient.RailwayServiceSummary ensureService(RailwayGraphqlClient.RailwayProjectSnapshot project,
                                                                     DeploymentEntity deployment,
                                                                     String serviceName) {
        RailwayGraphqlClient.RailwayServiceSummary service = project.serviceNamed(serviceName);
        if (service == null) {
            service = railwayGraphqlClient.createServiceFromRepository(
                project.id(),
                serviceName,
                deploymentSourceResolver.resolveRepository(deployment),
                deploymentSourceResolver.resolveBranch(deployment)
            );
        }
        return service;
    }

    private void configureServiceInstance(String projectId,
                                          String serviceId,
                                          String environmentId,
                                          RailwayServicePlanSummary plan,
                                          String healthcheckPath,
                                          DeploymentEntity deployment,
                                          String runtimeBaseUrl,
                                          String connectorBaseUrl) {
        railwayGraphqlClient.connectServiceToRepository(
            serviceId,
            deploymentSourceResolver.resolveRepository(deployment),
            deploymentSourceResolver.resolveBranch(deployment)
        );
        railwayGraphqlClient.updateServiceInstance(
            serviceId,
            environmentId,
            plan.rootDir(),
            plan.dockerfilePath(),
            healthcheckPath
        );
        railwayGraphqlClient.upsertVariables(
            projectId,
            environmentId,
            serviceId,
            toEnvVarInputs(plan.env(), runtimeBaseUrl, connectorBaseUrl)
        );
    }

    String resolveOrTriggerDeployment(String serviceId,
                                      String environmentId,
                                      String serviceName,
                                      Instant releaseStartedAt) {
        try {
            return railwayGraphqlClient.deployService(serviceId, environmentId);
        } catch (RailwayProvisioningException ex) {
            String existingDeploymentId = findRecentServiceDeploymentId(serviceId, serviceName, releaseStartedAt);
            if (existingDeploymentId != null) {
                log.warn(
                    "Railway deploy trigger failed for service '{}'; reusing in-flight deployment {} started during this release: {}",
                    serviceName,
                    existingDeploymentId,
                    ex.getMessage()
                );
                return existingDeploymentId;
            }
            throw ex;
        }
    }

    String findRecentServiceDeploymentId(String serviceId,
                                         String serviceName,
                                         Instant releaseStartedAt) {
        Instant deadline = Instant.now().plus(provisioningProperties.deploymentPollInterval().multipliedBy(3));
        while (Instant.now().isBefore(deadline)) {
            try {
                List<RailwayGraphqlClient.RailwayDeploymentSummary> deployments = railwayGraphqlClient.listServiceDeployments(serviceId, 5);
                for (RailwayGraphqlClient.RailwayDeploymentSummary deployment : deployments) {
                    Instant createdAt = parseDeploymentCreatedAt(deployment.createdAt());
                    if (createdAt != null && !createdAt.isBefore(releaseStartedAt.minusSeconds(1))) {
                        return deployment.id();
                    }
                }
            } catch (RailwayProvisioningException ex) {
                log.warn(
                    "Unable to inspect Railway deployments before deciding whether to trigger service '{}': {}",
                    serviceName,
                    ex.getMessage()
                );
            }

            try {
                Thread.sleep(Math.max(provisioningProperties.deploymentPollInterval().toMillis(), 250L));
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new RailwayProvisioningException(
                    "Interrupted while resolving Railway deployment trigger state for service '" + serviceName + "'.",
                    ex
                );
            }
        }
        return null;
    }

    private RailwayGraphqlClient.RailwayDeploymentSummary awaitSuccessfulDeployment(String deploymentId, String serviceName) {
        return awaitSuccessfulDeployment(
            deploymentId,
            serviceName,
            null,
            null
        );
    }

    private RailwayGraphqlClient.RailwayDeploymentSummary awaitSuccessfulDeployment(String deploymentId,
                                                                                    String serviceName,
                                                                                    String serviceBaseUrl,
                                                                                    String healthPath) {
        return awaitSuccessfulDeployment(
            deploymentId,
            serviceName,
            provisioningProperties.deploymentTimeout(),
            provisioningProperties.deploymentPollInterval(),
            () -> railwayGraphqlClient.getDeployment(deploymentId),
            duration -> Thread.sleep(Math.max(duration.toMillis(), 0L)),
            ex -> log.warn(
                "Transient Railway polling failure while waiting for service '{}' deployment {}: {}",
                serviceName,
                deploymentId,
                ex.getMessage()
            ),
            hasText(serviceBaseUrl) && hasText(healthPath)
                ? () -> isServiceHealthConfirmed(serviceBaseUrl, healthPath)
                : null,
            HEALTH_FALLBACK_GRACE_PERIOD,
            HEALTH_FALLBACK_REQUIRED_SUCCESSES
        );
    }

    static RailwayGraphqlClient.RailwayDeploymentSummary awaitSuccessfulDeployment(String deploymentId,
                                                                                   String serviceName,
                                                                                   java.time.Duration timeout,
                                                                                   java.time.Duration pollInterval,
                                                                                   Supplier<RailwayGraphqlClient.RailwayDeploymentSummary> deploymentSupplier,
                                                                                   DeploymentPollSleeper sleeper,
                                                                                   Consumer<RailwayProvisioningException> transientErrorHandler) {
        return awaitSuccessfulDeployment(
            deploymentId,
            serviceName,
            timeout,
            pollInterval,
            deploymentSupplier,
            sleeper,
            transientErrorHandler,
            null,
            HEALTH_FALLBACK_GRACE_PERIOD,
            HEALTH_FALLBACK_REQUIRED_SUCCESSES
        );
    }

    static RailwayGraphqlClient.RailwayDeploymentSummary awaitSuccessfulDeployment(String deploymentId,
                                                                                   String serviceName,
                                                                                   java.time.Duration timeout,
                                                                                   java.time.Duration pollInterval,
                                                                                   Supplier<RailwayGraphqlClient.RailwayDeploymentSummary> deploymentSupplier,
                                                                                   DeploymentPollSleeper sleeper,
                                                                                   Consumer<RailwayProvisioningException> transientErrorHandler,
                                                                                   DeploymentHealthProbe healthProbe,
                                                                                   java.time.Duration healthFallbackGracePeriod,
                                                                                   int healthFallbackRequiredSuccesses) {
        Instant deadline = Instant.now().plus(timeout);
        RailwayProvisioningException lastPollingError = null;
        String lastObservedStatus = null;
        Instant firstObservedAt = Instant.now();
        int consecutiveHealthyFallbacks = 0;

        while (Instant.now().isBefore(deadline)) {
            try {
                RailwayGraphqlClient.RailwayDeploymentSummary deployment = deploymentSupplier.get();
                String status = deployment.status() == null ? "" : deployment.status().trim().toUpperCase();
                lastObservedStatus = status;
                if (isSuccessStatus(status)) {
                    return deployment;
                }
                if (isFailureStatus(status)) {
                    throw new RailwayProvisioningException(
                        "Railway deployment failed for service '" + serviceName + "' with status " + status
                    );
                }
                if (shouldAcceptHealthFallback(
                    deployment,
                    firstObservedAt,
                    healthFallbackGracePeriod,
                    healthProbe
                )) {
                    consecutiveHealthyFallbacks++;
                    if (consecutiveHealthyFallbacks >= Math.max(1, healthFallbackRequiredSuccesses)) {
                        return deployment;
                    }
                } else {
                    consecutiveHealthyFallbacks = 0;
                }
                lastPollingError = null;
            } catch (RailwayProvisioningException ex) {
                lastPollingError = ex;
                consecutiveHealthyFallbacks = 0;
                transientErrorHandler.accept(ex);
            }

            try {
                sleeper.sleep(pollInterval);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new RailwayProvisioningException(
                    "Interrupted while waiting for Railway deployment " + deploymentId + " to finish.",
                    ex
                );
            }
        }

        if (lastPollingError != null) {
            throw new RailwayProvisioningException(
                "Timed out waiting for Railway deployment " + deploymentId
                    + " to become active after Railway API polling errors. Last error: "
                    + lastPollingError.getMessage(),
                lastPollingError
            );
        }

        if (lastObservedStatus != null && !lastObservedStatus.isBlank()) {
            throw new RailwayActivationUnconfirmedException(
                "Timed out waiting for Railway deployment " + deploymentId
                    + " to become active. Last observed Railway status for service '"
                    + serviceName
                    + "' was "
                    + lastObservedStatus
                    + "."
            );
        }

        throw new RailwayProvisioningException(
            "Timed out waiting for Railway deployment " + deploymentId + " to become active."
        );
    }

    private static boolean shouldAcceptHealthFallback(RailwayGraphqlClient.RailwayDeploymentSummary deployment,
                                                      Instant firstObservedAt,
                                                      Duration gracePeriod,
                                                      DeploymentHealthProbe healthProbe) {
        if (deployment == null || healthProbe == null) {
            return false;
        }
        Instant baseline = deployment.createdAt() == null || deployment.createdAt().isBlank()
            ? firstObservedAt
            : parseInstantOr(firstObservedAt, deployment.createdAt());
        if (Instant.now().isBefore(baseline.plus(gracePeriod))) {
            return false;
        }
        try {
            return healthProbe.isHealthy();
        } catch (Exception ex) {
            return false;
        }
    }

    private void validateConfiguration() {
        if (provisioningProperties.workspaceId().isBlank()) {
            throw new RailwayProvisioningConfigurationException(
                "RAILWAY_API mode requires RAILWAY_WORKSPACE_ID so the platform knows where to provision projects."
            );
        }
        if (provisioningProperties.railwayApiToken().isBlank()) {
            throw new RailwayProvisioningConfigurationException(
                "RAILWAY_API mode requires RAILWAY_API_TOKEN."
            );
        }
        if (!provisioningProperties.repository().contains("/")) {
            throw new RailwayProvisioningConfigurationException(
                "PLATFORM_DEPLOY_REPOSITORY must be a GitHub repository slug like 'owner/repo' in RAILWAY_API mode."
            );
        }
        if (provisioningProperties.branch().isBlank()) {
            throw new RailwayProvisioningConfigurationException(
                "PLATFORM_DEPLOY_BRANCH must not be blank in RAILWAY_API mode."
            );
        }
    }

    private DedicatedEmbeddingWorkerProvisioningResult provisionDedicatedEmbeddingWorker(DeploymentEntity deployment,
                                                                                         DeploymentVersionEntity version,
                                                                                         RailwayGraphqlClient.RailwayProjectSnapshot project,
                                                                                         RailwayGraphqlClient.RailwayEnvironmentSummary environment,
                                                                                         RailwayServicePlanSummary workerPlan,
                                                                                         JsonNode providerConfig) {
        if (platformManagedInferenceServiceRepository == null || platformManagedInferenceEndpointRepository == null) {
            throw new RailwayProvisioningConfigurationException(
                "Deployment-dedicated embedding workers require managed inference repositories to be available."
            );
        }

        String serviceRef = firstNonBlank(
            ManagedDeploymentProfileCatalog.embeddingManagedServiceRef(providerConfig),
            "dedicated-embedding-" + deployment.getId()
        );
        String endpointProfileRef = firstNonBlank(
            ManagedDeploymentProfileCatalog.embeddingEndpointProfile(providerConfig),
            "dep-" + deployment.getId() + "-embedding-worker"
        );
        String secretName = firstNonBlank(
            ManagedDeploymentProfileCatalog.embeddingApiKeySecretRef(providerConfig),
            managedEmbeddingWorkerSecretName(deployment)
        );

        ensureManagedEmbeddingWorkerSecret(deployment, serviceRef, secretName);
        PlatformManagedInferenceServiceEntity serviceEntity = upsertDedicatedEmbeddingServiceEntity(
            deployment,
            workerPlan,
            providerConfig,
            serviceRef,
            secretName
        );
        PlatformManagedInferenceEndpointEntity endpointEntity = upsertDedicatedEmbeddingEndpointEntity(
            serviceEntity,
            endpointProfileRef,
            secretName
        );

        RailwayGraphqlClient.RailwayServiceSummary workerService = ensureService(
            project,
            deployment,
            workerPlan.serviceName()
        );
        railwayGraphqlClient.connectServiceToRepository(
            workerService.id(),
            deploymentSourceResolver.resolveRepository(deployment),
            deploymentSourceResolver.resolveBranch(deployment)
        );
        railwayGraphqlClient.updateServiceInstance(
            workerService.id(),
            environment.id(),
            workerPlan.rootDir(),
            workerPlan.dockerfilePath(),
            firstNonBlank(inferenceProvisioningProperties.dedicatedEmbeddingHealthPath(), "/actuator/health")
        );

        List<RailwayEnvVarSummary> workerEnv = new ArrayList<>(workerPlan.env());
        workerEnv.add(new RailwayEnvVarSummary(
            "AI_FABRIC_EMBEDDING_WORKER_API_KEY",
            "${secret:" + secretName + "}"
        ));
        railwayGraphqlClient.upsertVariables(
            project.id(),
            environment.id(),
            workerService.id(),
            toEnvVarInputs(workerEnv, null, null)
        );
        if (railwayGraphqlClient.hasStagedChanges(environment.id())) {
            railwayGraphqlClient.commitStagedChanges(environment.id());
        }

        String deploymentId = railwayGraphqlClient.deployService(workerService.id(), environment.id());
        RailwayGraphqlClient.RailwayDeploymentSummary deploymentSummary = awaitSuccessfulDeployment(
            deploymentId,
            workerPlan.serviceName()
        );
        String publicBaseUrl = ensureServiceDomain(project.id(), environment.id(), workerService.id(), null);
        RailwayGraphqlClient.RailwayServiceInstanceSummary instance = railwayGraphqlClient.getServiceInstance(
            environment.id(),
            workerService.id()
        );
        String privateNetworkUrl = trimToNull(instance.upstreamUrl());
        String resolvedEndpointBaseUrl = publicBaseUrl + "/v1";

        serviceEntity.setDeploymentId(deployment.getId());
        serviceEntity.setRailwayProjectId(project.id());
        serviceEntity.setRailwayEnvironmentId(environment.id());
        serviceEntity.setRailwayServiceId(workerService.id());
        serviceEntity.setDesiredReplicas(1);
        serviceEntity.setActualReplicas(1);
        serviceEntity.setBaseUrl(publicBaseUrl);
        serviceEntity.setPrivateNetworkUrl(privateNetworkUrl);
        serviceEntity.setHealthPath(firstNonBlank(inferenceProvisioningProperties.dedicatedEmbeddingHealthPath(), "/actuator/health"));
        serviceEntity.setSecretName(secretName);
        serviceEntity.setStatus("ACTIVE");
        serviceEntity.setDetailsJson(objectMapper.createObjectNode()
            .put("deploymentId", deployment.getId())
            .put("versionId", version.getId())
            .put("serviceRef", serviceRef)
            .put("endpointProfileRef", endpointProfileRef)
            .put("deploymentMode", ManagedDeploymentProfileCatalog.INFERENCE_SERVICE_MODE_DEPLOYMENT_DEDICATED_SERVICE)
            .put("railwayDeploymentId", deploymentId)
            .put("reconciledAt", Instant.now().toString())
            .toPrettyString());
        serviceEntity.setUpdatedAt(Instant.now());
        platformManagedInferenceServiceRepository.save(serviceEntity);

        endpointEntity.setServiceId(serviceEntity.getId());
        endpointEntity.setEndpointPurpose("EMBEDDING");
        endpointEntity.setDisplayName("Dedicated Embedding Worker");
        endpointEntity.setProviderType(ManagedDeploymentProfileCatalog.EMBEDDING_PROVIDER_OPENAI);
        endpointEntity.setProtocolType(ManagedDeploymentProfileCatalog.INFERENCE_PROTOCOL_OPENAI_COMPATIBLE);
        endpointEntity.setBaseUrl(resolvedEndpointBaseUrl);
        endpointEntity.setSecretName(secretName);
        endpointEntity.setStatus("ACTIVE");
        endpointEntity.setUpdatedAt(Instant.now());
        platformManagedInferenceEndpointRepository.save(endpointEntity);

        ObjectNode effective = providerConfig != null && providerConfig.isObject()
            ? ((ObjectNode) providerConfig).deepCopy()
            : objectMapper.createObjectNode();
        effective.put("embeddingProvider", ManagedDeploymentProfileCatalog.EMBEDDING_PROVIDER_OPENAI);
        effective.put("embeddingServiceMode", ManagedDeploymentProfileCatalog.INFERENCE_SERVICE_MODE_DEPLOYMENT_DEDICATED_SERVICE);
        effective.put("embeddingManagedServiceRef", serviceRef);
        effective.put("embeddingEndpointProfile", endpointProfileRef);
        effective.put("embeddingBaseUrl", resolvedEndpointBaseUrl);
        effective.put("embeddingApiKeySecretRef", secretName);
        effective.put("openaiEmbeddingBaseUrl", resolvedEndpointBaseUrl);

        return new DedicatedEmbeddingWorkerProvisioningResult(
            effective,
            workerService,
            deploymentId,
            activatedServicesDeploymentStatus(deploymentSummary),
            publicBaseUrl,
            serviceRef,
            endpointProfileRef
        );
    }

    private PlatformManagedInferenceServiceEntity upsertDedicatedEmbeddingServiceEntity(DeploymentEntity deployment,
                                                                                        RailwayServicePlanSummary workerPlan,
                                                                                        JsonNode providerConfig,
                                                                                        String serviceRef,
                                                                                        String secretName) {
        PlatformManagedInferenceServiceEntity entity = platformManagedInferenceServiceRepository
            .findByServiceRefIgnoreCase(serviceRef)
            .orElseGet(PlatformManagedInferenceServiceEntity::new);
        if (entity.getId() == null || entity.getId().isBlank()) {
            entity.setId("pmis-" + serviceRef);
            entity.setCreatedAt(Instant.now());
        }
        entity.setServiceRef(serviceRef);
        entity.setDisplayName("Dedicated Embedding Worker");
        entity.setServiceKind("DEDICATED_EMBEDDING_SERVICE");
        entity.setDeploymentMode(ManagedDeploymentProfileCatalog.INFERENCE_SERVICE_MODE_DEPLOYMENT_DEDICATED_SERVICE);
        entity.setProviderType(ManagedDeploymentProfileCatalog.EMBEDDING_PROVIDER_OPENAI);
        entity.setProtocolType(ManagedDeploymentProfileCatalog.INFERENCE_PROTOCOL_OPENAI_COMPATIBLE);
        entity.setModelId(firstNonBlank(
            ManagedDeploymentProfileCatalog.openAiEmbeddingModel(providerConfig),
            ManagedDeploymentProfileCatalog.onnxModelAlias(providerConfig),
            "bge-small-en-v1.5"
        ));
        entity.setEnvironmentScope(resolveEnvironmentName(deployment));
        entity.setTierScope("dedicated");
        entity.setDeploymentId(deployment.getId());
        entity.setDesiredReplicas(1);
        entity.setActualReplicas(0);
        entity.setMinReplicas(1);
        entity.setMaxReplicas(1);
        entity.setAutoscalingMode(ManagedDeploymentProfileCatalog.INFERENCE_SERVICE_AUTOSCALING_MANUAL);
        entity.setHealthPath(firstNonBlank(inferenceProvisioningProperties.dedicatedEmbeddingHealthPath(), "/actuator/health"));
        entity.setSecretName(secretName);
        entity.setStatus("PROVISIONING");
        entity.setUpdatedAt(Instant.now());
        return platformManagedInferenceServiceRepository.save(entity);
    }

    private PlatformManagedInferenceEndpointEntity upsertDedicatedEmbeddingEndpointEntity(PlatformManagedInferenceServiceEntity serviceEntity,
                                                                                          String endpointProfileRef,
                                                                                          String secretName) {
        PlatformManagedInferenceEndpointEntity endpoint = platformManagedInferenceEndpointRepository
            .findByProfileRefIgnoreCase(endpointProfileRef)
            .orElseGet(PlatformManagedInferenceEndpointEntity::new);
        if (endpoint.getId() == null || endpoint.getId().isBlank()) {
            endpoint.setId("pmie-" + endpointProfileRef);
            endpoint.setCreatedAt(Instant.now());
        }
        endpoint.setProfileRef(endpointProfileRef);
        endpoint.setServiceId(serviceEntity.getId());
        endpoint.setEndpointPurpose("EMBEDDING");
        endpoint.setDisplayName("Dedicated Embedding Worker");
        endpoint.setProviderType(ManagedDeploymentProfileCatalog.EMBEDDING_PROVIDER_OPENAI);
        endpoint.setProtocolType(ManagedDeploymentProfileCatalog.INFERENCE_PROTOCOL_OPENAI_COMPATIBLE);
        endpoint.setSecretName(secretName);
        endpoint.setStatus("PROVISIONING");
        endpoint.setUpdatedAt(Instant.now());
        return platformManagedInferenceEndpointRepository.save(endpoint);
    }

    private void ensureManagedEmbeddingWorkerSecret(DeploymentEntity deployment,
                                                    String serviceRef,
                                                    String secretName) {
        if (platformSecretService.isSecretPresent(secretName)) {
            return;
        }
        platformSecretService.upsertManagedSecret(
            secretName,
            UUID.randomUUID().toString().replace("-", ""),
            Map.of(
                "deploymentId", deployment.getId(),
                "serviceRef", serviceRef,
                "purpose", "DEPLOYMENT_DEDICATED_EMBEDDING_WORKER"
            )
        );
    }

    private String managedEmbeddingWorkerSecretName(DeploymentEntity deployment) {
        return "MANAGED_DEPLOYMENT_EMBEDDING_WORKER_API_KEY_DEP_" + deployment.getId().replace('-', '_').toUpperCase();
    }

    private void mergePlanDetails(ObjectNode details, RailwayProvisioningPlanSummary plan) {
        ObjectNode planNode = objectMapper.valueToTree(plan);
        planNode.fields().forEachRemaining(entry -> details.set(entry.getKey(), entry.getValue()));
    }

    private JsonNode copyJson(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return objectMapper.createObjectNode();
        }
        return node.deepCopy();
    }

    private String resolveEnvironmentName(DeploymentEntity deployment) {
        if (deployment.getEnvironmentName() != null && !deployment.getEnvironmentName().isBlank()) {
            return deployment.getEnvironmentName().trim();
        }
        return provisioningProperties.environmentName();
    }

    private String ensureServiceDomain(String projectId,
                                       String environmentId,
                                       String serviceId,
                                       String existingBaseUrl) {
        List<RailwayGraphqlClient.RailwayServiceDomainSummary> existingDomains = railwayGraphqlClient.listServiceDomains(
            projectId,
            environmentId,
            serviceId
        );
        RailwayGraphqlClient.RailwayServiceDomainSummary domain = existingDomains.isEmpty()
            ? railwayGraphqlClient.createServiceDomain(serviceId, environmentId)
            : existingDomains.get(0);
        return firstNonBlank(
            domain.domain() == null ? null : "https://" + domain.domain(),
            existingBaseUrl
        );
    }

    private List<RailwayGraphqlClient.RailwayEnvVarInput> toEnvVarInputs(List<RailwayEnvVarSummary> envVars,
                                                                         String runtimeBaseUrl,
                                                                         String connectorBaseUrl) {
        return envVars.stream()
            .map(item -> new RailwayGraphqlClient.RailwayEnvVarInput(
                item.key(),
                resolveEnvVarValue(item, runtimeBaseUrl, connectorBaseUrl)
            ))
            .toList();
    }

    private String resolveEnvVarValue(RailwayEnvVarSummary envVar,
                                      String runtimeBaseUrl,
                                      String connectorBaseUrl) {
        String serviceBaseUrl = resolveServiceBaseUrl(envVar.key(), runtimeBaseUrl, connectorBaseUrl);
        if (serviceBaseUrl != null) {
            return serviceBaseUrl;
        }
        return resolveSecretPlaceholder(envVar.value());
    }

    private String resolveSecretPlaceholder(String value) {
        if (value == null || !value.startsWith("${secret:") || !value.endsWith("}")) {
            return value;
        }
        String secretName = value.substring("${secret:".length(), value.length() - 1).trim();
        String resolved = platformSecretService.resolveSecret(secretName);
        if (resolved == null || resolved.isBlank()) {
            throw new RailwayProvisioningConfigurationException(
                "Missing platform secret '" + secretName + "' required for Railway provisioning."
            );
        }
        return resolved;
    }

    static String resolveServiceBaseUrl(String envKey, String runtimeBaseUrl, String connectorBaseUrl) {
        if ("ACTIONS_CONNECTOR_BASE_URL".equals(envKey)) {
            return connectorBaseUrl;
        }
        if ("REST_CONNECTOR_RUNTIME_PROXY_BASE_URL".equals(envKey)) {
            return runtimeBaseUrl;
        }
        return null;
    }

    private static boolean isSuccessStatus(String status) {
        return "SUCCESS".equals(status) || "SLEEPING".equals(status);
    }

    private static boolean isFailureStatus(String status) {
        return "FAILED".equals(status)
            || "CRASHED".equals(status)
            || "REMOVED".equals(status)
            || "CANCELED".equals(status);
    }

    private boolean isServiceHealthConfirmed(String serviceBaseUrl, String healthPath) {
        try {
            URI uri = buildProbeUri(serviceBaseUrl, healthPath);
            HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(verificationProperties.timeout())
                .GET()
                .build();
            HttpResponse<String> response = HEALTHCHECK_HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return false;
            }
            String body = response.body();
            if (body == null || body.isBlank()) {
                return true;
            }
            try {
                JsonNode payload = objectMapper.readTree(body);
                JsonNode status = payload.path("status");
                if (status.isTextual()) {
                    return "UP".equalsIgnoreCase(status.asText(""));
                }
            } catch (Exception ignored) {
                return true;
            }
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private URI buildProbeUri(String baseUrl, String path) {
        String normalizedBaseUrl = baseUrl.endsWith("/")
            ? baseUrl.substring(0, baseUrl.length() - 1)
            : baseUrl;
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        return URI.create(normalizedBaseUrl + normalizedPath);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Instant parseDeploymentCreatedAt(String createdAt) {
        if (createdAt == null || createdAt.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(createdAt);
        } catch (RuntimeException ex) {
            log.warn("Unable to parse Railway deployment creation timestamp '{}'.", createdAt);
            return null;
        }
    }

    private static Instant parseInstantOr(Instant fallback, String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return fallback;
        }
        try {
            return Instant.parse(rawValue);
        } catch (RuntimeException ex) {
            return fallback;
        }
    }

    private void recordRailwayContext(ObjectNode details,
                                      RailwayGraphqlClient.RailwayProjectSnapshot project,
                                      RailwayGraphqlClient.RailwayEnvironmentSummary environment,
                                      RailwayGraphqlClient.RailwayServiceSummary runtimeService,
                                      RailwayGraphqlClient.RailwayServiceSummary connectorService,
                                      RailwayGraphqlClient.RailwayServiceSummary runnerService,
                                      RailwayGraphqlClient.RailwayServiceSummary embeddingWorkerService) {
        ObjectNode railway = objectNode(details, "railway");
        railway.put("workspaceId", provisioningProperties.workspaceId());
        railway.put("projectId", project.id());
        railway.put("projectName", project.name());
        railway.put("environmentId", environment.id());
        railway.put("environmentName", environment.name());

        ObjectNode services = objectNode(railway, "services");
        ObjectNode runtime = objectNode(services, "runtime");
        runtime.put("serviceId", runtimeService.id());
        runtime.put("serviceName", runtimeService.name());

        ObjectNode restConnector = objectNode(services, "restConnector");
        restConnector.put("serviceId", connectorService.id());
        restConnector.put("serviceName", connectorService.name());

        if (runnerService != null) {
            ObjectNode vectorizationRunner = objectNode(services, "vectorizationRunner");
            vectorizationRunner.put("serviceId", runnerService.id());
            vectorizationRunner.put("serviceName", runnerService.name());
        }
        if (embeddingWorkerService != null) {
            ObjectNode embeddingWorker = objectNode(services, "embeddingWorker");
            embeddingWorker.put("serviceId", embeddingWorkerService.id());
            embeddingWorker.put("serviceName", embeddingWorkerService.name());
        }
    }

    private void recordTriggeredDeployments(ObjectNode details,
                                            String runtimeDeploymentId,
                                            String connectorDeploymentId,
                                            String runnerDeploymentId) {
        ObjectNode services = objectNode(objectNode(details, "railway"), "services");
        objectNode(services, "runtime").put("deploymentId", runtimeDeploymentId);
        objectNode(services, "runtime").put("deploymentStatus", "TRIGGERED");
        objectNode(services, "restConnector").put("deploymentId", connectorDeploymentId);
        objectNode(services, "restConnector").put("deploymentStatus", "TRIGGERED");
        if (runnerDeploymentId != null) {
            objectNode(services, "vectorizationRunner").put("deploymentId", runnerDeploymentId);
            objectNode(services, "vectorizationRunner").put("deploymentStatus", "TRIGGERED");
        }
    }

    private void recordActivatedServices(ObjectNode details,
                                         String runtimeDeploymentStatus,
                                         String connectorDeploymentStatus,
                                         String runnerDeploymentStatus,
                                         String embeddingWorkerDeploymentStatus,
                                         String runtimeBaseUrl,
                                         String connectorBaseUrl,
                                         String embeddingWorkerBaseUrl) {
        ObjectNode services = objectNode(objectNode(details, "railway"), "services");
        ObjectNode runtime = objectNode(services, "runtime");
        runtime.put("deploymentStatus", runtimeDeploymentStatus);
        if (runtimeBaseUrl != null) {
            runtime.put("baseUrl", runtimeBaseUrl);
        }

        ObjectNode restConnector = objectNode(services, "restConnector");
        restConnector.put("deploymentStatus", connectorDeploymentStatus);
        if (connectorBaseUrl != null) {
            restConnector.put("baseUrl", connectorBaseUrl);
        }

        if (runnerDeploymentStatus != null) {
            ObjectNode vectorizationRunner = objectNode(services, "vectorizationRunner");
            vectorizationRunner.put("deploymentStatus", runnerDeploymentStatus);
        }
        if (embeddingWorkerDeploymentStatus != null) {
            ObjectNode embeddingWorker = objectNode(services, "embeddingWorker");
            embeddingWorker.put("deploymentStatus", embeddingWorkerDeploymentStatus);
            if (embeddingWorkerBaseUrl != null) {
                embeddingWorker.put("baseUrl", embeddingWorkerBaseUrl);
            }
        }
    }

    private String activatedServicesDeploymentStatus(RailwayGraphqlClient.RailwayDeploymentSummary deployment) {
        if (deployment == null) {
            return null;
        }
        return deployment.status() == null || deployment.status().isBlank() ? "UNKNOWN" : deployment.status();
    }

    private void mergeTrackedDetails(ProvisioningProgressTracker tracker, ObjectNode details) {
        tracker.mergeDetails(details.toPrettyString());
    }

    private ObjectNode objectNode(ObjectNode parent, String fieldName) {
        if (parent.path(fieldName).isObject()) {
            return (ObjectNode) parent.path(fieldName);
        }
        ObjectNode created = objectMapper.createObjectNode();
        parent.set(fieldName, created);
        return created;
    }

    private <T> T trackedStep(ProvisioningProgressTracker tracker,
                              String key,
                              String description,
                              RailwayStepSupplier<T> supplier) {
        tracker.stepStarted(key, description);
        try {
            T result = supplier.get();
            tracker.stepCompleted(key, description);
            return result;
        } catch (Exception ex) {
            tracker.stepFailed(key, description, ex.getMessage());
            throw ex;
        }
    }

    @FunctionalInterface
    private interface RailwayStepSupplier<T> {
        T get();
    }

    @FunctionalInterface
    interface DeploymentPollSleeper {
        void sleep(java.time.Duration duration) throws InterruptedException;
    }

    @FunctionalInterface
    interface DeploymentHealthProbe {
        boolean isHealthy();
    }

    private record RailwayProjectContext(
        RailwayGraphqlClient.RailwayProjectSnapshot project,
        RailwayGraphqlClient.RailwayEnvironmentSummary environment
    ) {
    }

    private record RailwayDeploymentContext(
        String runtimeDeploymentId,
        String connectorDeploymentId,
        String vectorizationRunnerDeploymentId
    ) {
    }

    private record RailwayActivatedServices(
        RailwayGraphqlClient.RailwayDeploymentSummary runtimeDeployment,
        RailwayGraphqlClient.RailwayDeploymentSummary connectorDeployment,
        RailwayGraphqlClient.RailwayDeploymentSummary vectorizationRunnerDeployment,
        String runtimeBaseUrl,
        String connectorBaseUrl
    ) {
    }

    private record DedicatedEmbeddingWorkerProvisioningResult(
        JsonNode effectiveProviderConfig,
        RailwayGraphqlClient.RailwayServiceSummary service,
        String deploymentId,
        String deploymentStatus,
        String baseUrl,
        String managedServiceRef,
        String endpointProfileRef
    ) {
    }
}
