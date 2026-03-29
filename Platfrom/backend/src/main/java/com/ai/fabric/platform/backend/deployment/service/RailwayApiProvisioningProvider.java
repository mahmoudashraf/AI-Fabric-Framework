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
                                        DeploymentReleaseEntity release) {
        validateConfiguration();

        RailwayProvisioningPlanSummary plan = railwayProvisioningPlanService.buildPlan(deployment, version);
        String environmentName = resolveEnvironmentName(deployment);

        log.info(
            "Provisioning live Railway deployment: deploymentId={}, versionId={}, projectName={}",
            deployment.getId(),
            version.getId(),
            plan.projectName()
        );

        RailwayGraphqlClient.RailwayProjectSnapshot project = railwayGraphqlClient.findProjectByName(
            provisioningProperties.workspaceId(),
            plan.projectName()
        );
        if (project == null) {
            project = railwayGraphqlClient.createProject(
                provisioningProperties.workspaceId(),
                plan.projectName(),
                environmentName
            );
        }

        RailwayGraphqlClient.RailwayEnvironmentSummary environment = project.environmentNamed(environmentName);
        if (environment == null) {
            environment = railwayGraphqlClient.createEnvironment(project.id(), environmentName);
        }

        RailwayGraphqlClient.RailwayServiceSummary runtimeService = project.serviceNamed(
            plan.services().runtime().serviceName()
        );
        if (runtimeService == null) {
            runtimeService = railwayGraphqlClient.createServiceFromRepository(
                project.id(),
                plan.services().runtime().serviceName(),
                provisioningProperties.repository(),
                provisioningProperties.branch()
            );
        }

        RailwayGraphqlClient.RailwayServiceSummary connectorService = project.serviceNamed(
            plan.services().restConnector().serviceName()
        );
        if (connectorService == null) {
            connectorService = railwayGraphqlClient.createServiceFromRepository(
                project.id(),
                plan.services().restConnector().serviceName(),
                provisioningProperties.repository(),
                provisioningProperties.branch()
            );
        }

        configureServiceInstance(
            project.id(),
            runtimeService.id(),
            environment.id(),
            plan.services().runtime(),
            verificationProperties.runtimeHealthPath(),
            runtimeService.name(),
            connectorService.name()
        );
        configureServiceInstance(
            project.id(),
            connectorService.id(),
            environment.id(),
            plan.services().restConnector(),
            verificationProperties.connectorHealthPath(),
            runtimeService.name(),
            connectorService.name()
        );

        if (railwayGraphqlClient.hasStagedChanges(environment.id())) {
            railwayGraphqlClient.commitStagedChanges(environment.id());
        }

        String runtimeDeploymentId = railwayGraphqlClient.deployService(runtimeService.id(), environment.id());
        String connectorDeploymentId = railwayGraphqlClient.deployService(connectorService.id(), environment.id());

        RailwayGraphqlClient.RailwayDeploymentSummary runtimeDeployment = awaitSuccessfulDeployment(
            runtimeDeploymentId,
            plan.services().runtime().serviceName()
        );
        RailwayGraphqlClient.RailwayDeploymentSummary connectorDeployment = awaitSuccessfulDeployment(
            connectorDeploymentId,
            plan.services().restConnector().serviceName()
        );

        String runtimeBaseUrl = ensureServiceDomain(project.id(), environment.id(), runtimeService.id(), deployment.getRuntimeBaseUrl());
        String connectorBaseUrl = ensureServiceDomain(
            project.id(),
            environment.id(),
            connectorService.id(),
            deployment.getConnectorBaseUrl()
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
        runtimeNode.put("deploymentId", runtimeDeployment.id());
        runtimeNode.put("deploymentStatus", runtimeDeployment.status());
        if (runtimeBaseUrl != null) {
            runtimeNode.put("baseUrl", runtimeBaseUrl);
        }

        ObjectNode connectorNode = servicesNode.putObject("restConnector");
        connectorNode.put("serviceId", connectorService.id());
        connectorNode.put("serviceName", connectorService.name());
        connectorNode.put("deploymentId", connectorDeployment.id());
        connectorNode.put("deploymentStatus", connectorDeployment.status());
        if (connectorBaseUrl != null) {
            connectorNode.put("baseUrl", connectorBaseUrl);
        }

        return new ProvisioningResult(
            "ACTIVE",
            "RAILWAY_API",
            runtimeBaseUrl,
            connectorBaseUrl,
            details.toPrettyString()
        );
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
}
