package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.config.PlatformProvisioningProperties;
import com.ai.fabric.platform.backend.config.PlatformVerificationProperties;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.model.RailwayEnvVarSummary;
import com.ai.fabric.platform.backend.deployment.model.RailwayProvisioningPlanSummary;
import com.ai.fabric.platform.backend.deployment.model.RailwayServicePlanSummary;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

@Service
public class RailwayApiProvisioningProvider implements DeploymentProvisioningProvider {

    private static final Logger log = LoggerFactory.getLogger(RailwayApiProvisioningProvider.class);

    private final PlatformProvisioningProperties provisioningProperties;
    private final PlatformVerificationProperties verificationProperties;
    private final DeploymentManagedVectorProvisioningService deploymentManagedVectorProvisioningService;
    private final DeploymentManagedVectorResourceService deploymentManagedVectorResourceService;
    private final RailwayProvisioningPlanService railwayProvisioningPlanService;
    private final DeploymentSourceResolver deploymentSourceResolver;
    private final RailwayGraphqlClient railwayGraphqlClient;
    private final PlatformSecretService platformSecretService;
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
        this.provisioningProperties = provisioningProperties;
        this.verificationProperties = verificationProperties;
        this.deploymentManagedVectorProvisioningService = deploymentManagedVectorProvisioningService;
        this.deploymentManagedVectorResourceService = deploymentManagedVectorResourceService;
        this.railwayProvisioningPlanService = railwayProvisioningPlanService;
        this.deploymentSourceResolver = deploymentSourceResolver;
        this.railwayGraphqlClient = railwayGraphqlClient;
        this.platformSecretService = platformSecretService;
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

        RailwayProvisioningPlanSummary plan = trackedStep(
            progressTracker,
            "publish_artifacts",
            "Resolve immutable config artifact URLs for the selected version.",
            () -> railwayProvisioningPlanService.buildPlan(
                deployment,
                version,
                managedVectorProvisioningResult.effectiveProviderConfig()
            )
        );
        ObjectNode details = objectMapper.valueToTree(plan);
        details.put("provider", "RAILWAY_API");
        details.put("releaseId", release.getId());
        details.put("generatedAt", Instant.now().truncatedTo(ChronoUnit.SECONDS).toString());
        details.set("managedVectorProvisioning", managedVectorProvisioningResult.details());
        mergeTrackedDetails(progressTracker, details);
        String environmentName = resolveEnvironmentName(deployment);

        log.info(
            "Provisioning live Railway deployment: deploymentId={}, versionId={}, projectName={}",
            deployment.getId(),
            version.getId(),
            plan.projectName()
        );

        RailwayProjectContext projectContext = trackedStep(
            progressTracker,
            "prepare_project",
            "Create or reuse the Railway project for this customer environment.",
            () -> {
                RailwayGraphqlClient.RailwayProjectSnapshot existingProject = railwayGraphqlClient.findProjectByName(
                    provisioningProperties.workspaceId(),
                    plan.projectName()
                );
                if (existingProject == null) {
                    existingProject = railwayGraphqlClient.createProject(
                        provisioningProperties.workspaceId(),
                        plan.projectName(),
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
        recordRailwayContext(
            details,
            project,
            environment,
            runtimeService,
            connectorService
        );
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
            runtimeServiceBaseUrl,
            connectorServiceBaseUrl
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
                    plan.services().runtime(),
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
                    plan.services().restConnector(),
                    verificationProperties.connectorHealthPath(),
                    deployment,
                    runtimeServiceBaseUrl,
                    connectorServiceBaseUrl
                );
                return null;
            }
        );

        RailwayDeploymentContext deploymentContext = trackedStep(
            progressTracker,
            "trigger_deploy",
            "Commit staged changes or trigger Railway deployment/redeploy for both services.",
            () -> {
                Instant releaseStartedAt = release.getAppliedAt() != null ? release.getAppliedAt() : Instant.now();
                if (railwayGraphqlClient.hasStagedChanges(environment.id())) {
                    railwayGraphqlClient.commitStagedChanges(environment.id());
                }
                String runtimeDeploymentId = resolveOrTriggerDeployment(
                    runtimeService.id(),
                    environment.id(),
                    plan.services().runtime().serviceName(),
                    releaseStartedAt
                );
                String connectorDeploymentId = resolveOrTriggerDeployment(
                    connectorService.id(),
                    environment.id(),
                    plan.services().restConnector().serviceName(),
                    releaseStartedAt
                );
                recordTriggeredDeployments(details, runtimeDeploymentId, connectorDeploymentId);
                mergeTrackedDetails(progressTracker, details);
                return new RailwayDeploymentContext(runtimeDeploymentId, connectorDeploymentId);
            }
        );

        RailwayActivatedServices activatedServices = trackedStep(
            progressTracker,
            "wait_for_active",
            "Wait for Railway deployment states to become active.",
            () -> {
                RailwayGraphqlClient.RailwayDeploymentSummary runtimeDeployment = awaitSuccessfulDeployment(
                    deploymentContext.runtimeDeploymentId(),
                    plan.services().runtime().serviceName()
                );
                RailwayGraphqlClient.RailwayDeploymentSummary connectorDeployment = awaitSuccessfulDeployment(
                    deploymentContext.connectorDeploymentId(),
                    plan.services().restConnector().serviceName()
                );
                recordActivatedServices(
                    details,
                    activatedServicesDeploymentStatus(runtimeDeployment),
                    activatedServicesDeploymentStatus(connectorDeployment),
                    runtimeServiceBaseUrl,
                    connectorServiceBaseUrl
                );
                mergeTrackedDetails(progressTracker, details);
                return new RailwayActivatedServices(
                    runtimeDeployment,
                    connectorDeployment,
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
        String existingDeploymentId = findRecentServiceDeploymentId(serviceId, serviceName, releaseStartedAt);
        if (existingDeploymentId != null) {
            log.info(
                "Reusing Railway deployment already triggered during this release: serviceName={}, deploymentId={}",
                serviceName,
                existingDeploymentId
            );
            return existingDeploymentId;
        }
        return railwayGraphqlClient.deployService(serviceId, environmentId);
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
            provisioningProperties.deploymentTimeout(),
            provisioningProperties.deploymentPollInterval(),
            () -> railwayGraphqlClient.getDeployment(deploymentId),
            duration -> Thread.sleep(Math.max(duration.toMillis(), 0L)),
            ex -> log.warn(
                "Transient Railway polling failure while waiting for service '{}' deployment {}: {}",
                serviceName,
                deploymentId,
                ex.getMessage()
            )
        );
    }

    static RailwayGraphqlClient.RailwayDeploymentSummary awaitSuccessfulDeployment(String deploymentId,
                                                                                   String serviceName,
                                                                                   java.time.Duration timeout,
                                                                                   java.time.Duration pollInterval,
                                                                                   Supplier<RailwayGraphqlClient.RailwayDeploymentSummary> deploymentSupplier,
                                                                                   DeploymentPollSleeper sleeper,
                                                                                   Consumer<RailwayProvisioningException> transientErrorHandler) {
        Instant deadline = Instant.now().plus(timeout);
        RailwayProvisioningException lastPollingError = null;
        String lastObservedStatus = null;

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
                lastPollingError = null;
            } catch (RailwayProvisioningException ex) {
                lastPollingError = ex;
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

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
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

    private void recordRailwayContext(ObjectNode details,
                                      RailwayGraphqlClient.RailwayProjectSnapshot project,
                                      RailwayGraphqlClient.RailwayEnvironmentSummary environment,
                                      RailwayGraphqlClient.RailwayServiceSummary runtimeService,
                                      RailwayGraphqlClient.RailwayServiceSummary connectorService) {
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
    }

    private void recordTriggeredDeployments(ObjectNode details,
                                            String runtimeDeploymentId,
                                            String connectorDeploymentId) {
        ObjectNode services = objectNode(objectNode(details, "railway"), "services");
        objectNode(services, "runtime").put("deploymentId", runtimeDeploymentId);
        objectNode(services, "runtime").put("deploymentStatus", "TRIGGERED");
        objectNode(services, "restConnector").put("deploymentId", connectorDeploymentId);
        objectNode(services, "restConnector").put("deploymentStatus", "TRIGGERED");
    }

    private void recordActivatedServices(ObjectNode details,
                                         String runtimeDeploymentStatus,
                                         String connectorDeploymentStatus,
                                         String runtimeBaseUrl,
                                         String connectorBaseUrl) {
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
    }

    private String activatedServicesDeploymentStatus(RailwayGraphqlClient.RailwayDeploymentSummary deployment) {
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

    private record RailwayProjectContext(
        RailwayGraphqlClient.RailwayProjectSnapshot project,
        RailwayGraphqlClient.RailwayEnvironmentSummary environment
    ) {
    }

    private record RailwayDeploymentContext(
        String runtimeDeploymentId,
        String connectorDeploymentId
    ) {
    }

    private record RailwayActivatedServices(
        RailwayGraphqlClient.RailwayDeploymentSummary runtimeDeployment,
        RailwayGraphqlClient.RailwayDeploymentSummary connectorDeployment,
        String runtimeBaseUrl,
        String connectorBaseUrl
    ) {
    }
}
