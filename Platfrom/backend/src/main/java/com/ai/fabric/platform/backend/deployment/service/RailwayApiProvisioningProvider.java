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

@Service
public class RailwayApiProvisioningProvider implements DeploymentProvisioningProvider {

    private static final Logger log = LoggerFactory.getLogger(RailwayApiProvisioningProvider.class);

    private final PlatformProvisioningProperties provisioningProperties;
    private final PlatformVerificationProperties verificationProperties;
    private final RailwayProvisioningPlanService railwayProvisioningPlanService;
    private final RailwayGraphqlClient railwayGraphqlClient;
    private final PlatformSecretService platformSecretService;
    private final ObjectMapper objectMapper;

    public RailwayApiProvisioningProvider(PlatformProvisioningProperties provisioningProperties,
                                          PlatformVerificationProperties verificationProperties,
                                          RailwayProvisioningPlanService railwayProvisioningPlanService,
                                          RailwayGraphqlClient railwayGraphqlClient,
                                          PlatformSecretService platformSecretService,
                                          ObjectMapper objectMapper) {
        this.provisioningProperties = provisioningProperties;
        this.verificationProperties = verificationProperties;
        this.railwayProvisioningPlanService = railwayProvisioningPlanService;
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

        RailwayProvisioningPlanSummary plan = trackedStep(
            progressTracker,
            "publish_artifacts",
            "Resolve immutable config artifact URLs for the selected version.",
            () -> railwayProvisioningPlanService.buildPlan(deployment, version)
        );
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
            plan.services().runtime().serviceName()
        );
        RailwayGraphqlClient.RailwayServiceSummary connectorService = ensureService(
            project,
            plan.services().restConnector().serviceName()
        );

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
                    runtimeService.name(),
                    connectorService.name()
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
                    runtimeService.name(),
                    connectorService.name()
                );
                return null;
            }
        );

        RailwayDeploymentContext deploymentContext = trackedStep(
            progressTracker,
            "trigger_deploy",
            "Commit staged changes or trigger Railway deployment/redeploy for both services.",
            () -> {
                if (railwayGraphqlClient.hasStagedChanges(environment.id())) {
                    railwayGraphqlClient.commitStagedChanges(environment.id());
                }
                String runtimeDeploymentId = railwayGraphqlClient.deployService(runtimeService.id(), environment.id());
                String connectorDeploymentId = railwayGraphqlClient.deployService(connectorService.id(), environment.id());
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
                String runtimeBaseUrl = ensureServiceDomain(project.id(), environment.id(), runtimeService.id(), deployment.getRuntimeBaseUrl());
                String connectorBaseUrl = ensureServiceDomain(
                    project.id(),
                    environment.id(),
                    connectorService.id(),
                    deployment.getConnectorBaseUrl()
                );
                return new RailwayActivatedServices(runtimeDeployment, connectorDeployment, runtimeBaseUrl, connectorBaseUrl);
            }
        );

        ObjectNode details = objectMapper.valueToTree(plan);
        details.put("provider", "RAILWAY_API");
        details.put("releaseId", release.getId());
        details.put("statusMessage", "Railway project and services reconciled successfully.");
        details.put("generatedAt", Instant.now().truncatedTo(ChronoUnit.SECONDS).toString());

        ObjectNode railway = details.putObject("railway");
        railway.put("workspaceId", provisioningProperties.workspaceId());
        railway.put("projectId", project.id());
        railway.put("projectName", project.name());
        railway.put("environmentId", environment.id());
        railway.put("environmentName", environment.name());

        ObjectNode servicesNode = railway.putObject("services");
        ObjectNode runtimeNode = servicesNode.putObject("runtime");
        runtimeNode.put("serviceId", runtimeService.id());
        runtimeNode.put("serviceName", runtimeService.name());
        runtimeNode.put("deploymentId", activatedServices.runtimeDeployment().id());
        runtimeNode.put("deploymentStatus", activatedServices.runtimeDeployment().status());
        if (activatedServices.runtimeBaseUrl() != null) {
            runtimeNode.put("baseUrl", activatedServices.runtimeBaseUrl());
        }

        ObjectNode connectorNode = servicesNode.putObject("restConnector");
        connectorNode.put("serviceId", connectorService.id());
        connectorNode.put("serviceName", connectorService.name());
        connectorNode.put("deploymentId", activatedServices.connectorDeployment().id());
        connectorNode.put("deploymentStatus", activatedServices.connectorDeployment().status());
        if (activatedServices.connectorBaseUrl() != null) {
            connectorNode.put("baseUrl", activatedServices.connectorBaseUrl());
        }

        return new ProvisioningResult(
            "ACTIVE",
            "RAILWAY_API",
            activatedServices.runtimeBaseUrl(),
            activatedServices.connectorBaseUrl(),
            details.toPrettyString()
        );
    }

    private RailwayGraphqlClient.RailwayServiceSummary ensureService(RailwayGraphqlClient.RailwayProjectSnapshot project,
                                                                     String serviceName) {
        RailwayGraphqlClient.RailwayServiceSummary service = project.serviceNamed(serviceName);
        if (service == null) {
            service = railwayGraphqlClient.createServiceFromRepository(
                project.id(),
                serviceName,
                provisioningProperties.repository(),
                provisioningProperties.branch()
            );
        }
        return service;
    }

    private void configureServiceInstance(String projectId,
                                          String serviceId,
                                          String environmentId,
                                          RailwayServicePlanSummary plan,
                                          String healthcheckPath,
                                          String runtimeServiceName,
                                          String connectorServiceName) {
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
            toEnvVarInputs(plan.env(), runtimeServiceName, connectorServiceName)
        );
    }

    private RailwayGraphqlClient.RailwayDeploymentSummary awaitSuccessfulDeployment(String deploymentId, String serviceName) {
        Instant deadline = Instant.now().plus(provisioningProperties.deploymentTimeout());

        while (Instant.now().isBefore(deadline)) {
            RailwayGraphqlClient.RailwayDeploymentSummary deployment = railwayGraphqlClient.getDeployment(deploymentId);
            String status = deployment.status() == null ? "" : deployment.status().trim().toUpperCase();
            if (isSuccessStatus(status)) {
                return deployment;
            }
            if (isFailureStatus(status)) {
                throw new RailwayProvisioningException(
                    "Railway deployment failed for service '" + serviceName + "' with status " + status
                );
            }

            try {
                Thread.sleep(provisioningProperties.deploymentPollInterval());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new RailwayProvisioningException(
                    "Interrupted while waiting for Railway deployment " + deploymentId + " to finish.",
                    ex
                );
            }
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
                                                                         String runtimeServiceName,
                                                                         String connectorServiceName) {
        return envVars.stream()
            .map(item -> new RailwayGraphqlClient.RailwayEnvVarInput(
                item.key(),
                resolveEnvVarValue(item, runtimeServiceName, connectorServiceName)
            ))
            .toList();
    }

    private String resolveEnvVarValue(RailwayEnvVarSummary envVar,
                                      String runtimeServiceName,
                                      String connectorServiceName) {
        if ("ACTIONS_CONNECTOR_BASE_URL".equals(envVar.key())) {
            return internalRailwayServiceUrl(connectorServiceName);
        }
        if ("REST_CONNECTOR_RUNTIME_PROXY_BASE_URL".equals(envVar.key())) {
            return internalRailwayServiceUrl(runtimeServiceName);
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

    private String internalRailwayServiceUrl(String serviceName) {
        return "http://${{" + serviceName + ".RAILWAY_PRIVATE_DOMAIN}}";
    }

    private boolean isSuccessStatus(String status) {
        return "SUCCESS".equals(status) || "SLEEPING".equals(status);
    }

    private boolean isFailureStatus(String status) {
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
