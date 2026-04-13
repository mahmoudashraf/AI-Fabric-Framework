package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentRailwayLiveEnvVarDriftSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentRailwayLiveFieldDriftSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentRailwayLiveReadbackSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentRailwayLiveServiceSummary;
import com.ai.fabric.platform.backend.deployment.model.RailwayEnvVarSummary;
import com.ai.fabric.platform.backend.deployment.model.RailwayProvisioningPlanSummary;
import com.ai.fabric.platform.backend.deployment.model.RailwayServicePlanSummary;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class DeploymentRailwayLiveReadbackService {

    private final RailwayGraphqlClient railwayGraphqlClient;
    private final PlatformSecretService platformSecretService;
    private final ObjectMapper objectMapper;

    public DeploymentRailwayLiveReadbackService(RailwayGraphqlClient railwayGraphqlClient,
                                                PlatformSecretService platformSecretService,
                                                ObjectMapper objectMapper) {
        this.railwayGraphqlClient = railwayGraphqlClient;
        this.platformSecretService = platformSecretService;
        this.objectMapper = objectMapper;
    }

    public DeploymentRailwayLiveReadbackSummary build(DeploymentEntity deployment,
                                                      DeploymentReleaseEntity latestRelease,
                                                      RailwayProvisioningPlanSummary livePlan) {
        if (livePlan == null) {
            return unavailable("No live version is applied yet, so Railway read-back is not available.");
        }
        if (!"RAILWAY_API".equalsIgnoreCase(livePlan.mode())) {
            return unavailable("Live Railway read-back is only available for RAILWAY_API deployments.");
        }
        if (livePlan.services() == null || livePlan.services().runtime() == null || livePlan.services().restConnector() == null) {
            return unavailable("Live Railway read-back requires both runtime and internal REST connector service plans.");
        }

        JsonNode provisioningDetails = readJson(latestRelease == null ? null : latestRelease.getProvisioningDetailsJson());
        String workspaceId = firstNonBlank(text(provisioningDetails, "railway", "workspaceId"), livePlan.workspaceId());
        if (!hasText(workspaceId)) {
            return unavailable("Railway workspace id is missing, so live provider read-back cannot be resolved.");
        }

        try {
            RailwayGraphqlClient.RailwayProjectSnapshot project = resolveProject(workspaceId, livePlan, provisioningDetails);
            if (project == null) {
                return unavailable("Railway project could not be resolved from live provider records.");
            }
            RailwayGraphqlClient.RailwayEnvironmentSummary environment = resolveEnvironment(project, deployment, provisioningDetails);
            if (environment == null) {
                return unavailable("Railway environment could not be resolved for the deployment.");
            }

            DeploymentRailwayLiveServiceSummary runtime = buildServiceSummary(
                "runtime",
                "Runtime service",
                project.id(),
                environment.id(),
                resolveService(project, provisioningDetails, "runtime", livePlan.services().runtime().serviceName()),
                livePlan.services().runtime(),
                livePlan.repository(),
                livePlan.branch(),
                deployment.getRuntimeBaseUrl(),
                deployment.getRuntimeBaseUrl(),
                deployment.getConnectorBaseUrl()
            );
            DeploymentRailwayLiveServiceSummary restConnector = buildServiceSummary(
                "restConnector",
                "REST connector",
                project.id(),
                environment.id(),
                resolveService(project, provisioningDetails, "restConnector", livePlan.services().restConnector().serviceName()),
                livePlan.services().restConnector(),
                livePlan.repository(),
                livePlan.branch(),
                deployment.getConnectorBaseUrl(),
                deployment.getRuntimeBaseUrl(),
                deployment.getConnectorBaseUrl()
            );
            DeploymentRailwayLiveServiceSummary vectorizationRunner =
                livePlan.services().vectorizationRunner() == null
                    ? null
                    : buildServiceSummary(
                        "vectorizationRunner",
                        "Vectorization runner",
                        project.id(),
                        environment.id(),
                        resolveService(project, provisioningDetails, "vectorizationRunner", livePlan.services().vectorizationRunner().serviceName()),
                        livePlan.services().vectorizationRunner(),
                        livePlan.repository(),
                        livePlan.branch(),
                        null,
                        deployment.getRuntimeBaseUrl(),
                        deployment.getConnectorBaseUrl()
                    );

            String status = overallStatus(runtime, restConnector, vectorizationRunner);
            String summaryMessage = switch (status) {
                case "READY" -> "Railway live read-back matches the platform-managed deployment plan.";
                case "WARNING" -> "Railway live read-back found drift between the platform plan and provider state.";
                default -> "Railway live read-back is incomplete or unavailable for at least one managed service.";
            };

            return new DeploymentRailwayLiveReadbackSummary(
                true,
                status,
                summaryMessage,
                project.id(),
                project.name(),
                environment.id(),
                environment.name(),
                runtime,
                restConnector,
                vectorizationRunner
            );
        } catch (Exception ex) {
            return unavailable("Railway live read-back failed: " + ex.getMessage());
        }
    }

    private RailwayGraphqlClient.RailwayProjectSnapshot resolveProject(String workspaceId,
                                                                       RailwayProvisioningPlanSummary livePlan,
                                                                       JsonNode provisioningDetails) {
        String projectId = firstNonBlank(
            text(provisioningDetails, "projectId"),
            text(provisioningDetails, "railway", "projectId")
        );
        if (hasText(projectId)) {
            return railwayGraphqlClient.getProject(projectId);
        }
        return railwayGraphqlClient.findProjectByName(workspaceId, livePlan.projectName());
    }

    private RailwayGraphqlClient.RailwayEnvironmentSummary resolveEnvironment(RailwayGraphqlClient.RailwayProjectSnapshot project,
                                                                              DeploymentEntity deployment,
                                                                              JsonNode provisioningDetails) {
        String environmentId = firstNonBlank(
            text(provisioningDetails, "environmentId"),
            text(provisioningDetails, "railway", "environmentId")
        );
        if (hasText(environmentId)) {
            return project.environments().stream()
                .filter(item -> environmentId.equals(item.id()))
                .findFirst()
                .orElse(null);
        }
        return project.environmentNamed(deployment.getEnvironmentName());
    }

    private RailwayGraphqlClient.RailwayServiceSummary resolveService(RailwayGraphqlClient.RailwayProjectSnapshot project,
                                                                      JsonNode provisioningDetails,
                                                                      String serviceKey,
                                                                      String plannedServiceName) {
        String serviceId = text(provisioningDetails, "railway", "services", serviceKey, "serviceId");
        if (hasText(serviceId)) {
            return project.services().stream()
                .filter(item -> serviceId.equals(item.id()))
                .findFirst()
                .orElseGet(() -> project.serviceNamed(plannedServiceName));
        }
        return project.serviceNamed(plannedServiceName);
    }

    private DeploymentRailwayLiveServiceSummary buildServiceSummary(String key,
                                                                    String label,
                                                                    String projectId,
                                                                    String environmentId,
                                                                    RailwayGraphqlClient.RailwayServiceSummary service,
                                                                    RailwayServicePlanSummary plan,
                                                                    String expectedRepository,
                                                                    String expectedBranch,
                                                                    String expectedBaseUrl,
                                                                    String runtimeBaseUrl,
                                                                    String connectorBaseUrl) {
        if (service == null) {
            return unavailableService(key, label, "Railway service could not be resolved from the live project.");
        }

        RailwayGraphqlClient.RailwayServiceInstanceSummary instance =
            railwayGraphqlClient.getServiceInstance(environmentId, service.id());
        RailwayGraphqlClient.RailwayServiceSourceSummary source = railwayGraphqlClient.getServiceSource(service.id());
        JsonNode variables = railwayGraphqlClient.getVariables(projectId, environmentId, service.id(), true);
        List<RailwayGraphqlClient.RailwayServiceDomainSummary> domains =
            railwayGraphqlClient.listServiceDomains(projectId, environmentId, service.id());

        String actualBaseUrl = domains.isEmpty() ? null : "https://" + domains.get(0).domain();
        String actualRepository = firstNonBlank(instance.sourceRepo(), firstTriggerRepository(source));
        String actualBranch = firstTriggerBranch(source);
        List<RailwayEnvVarSummary> expectedEnv = plan == null || plan.env() == null ? List.of() : plan.env();

        DeploymentRailwayLiveFieldDriftSummary rootDirectory = fieldDrift(
            "rootDirectory",
            "Root directory",
            plan == null ? null : plan.rootDir(),
            instance.rootDirectory()
        );
        DeploymentRailwayLiveFieldDriftSummary dockerfilePath = fieldDrift(
            "dockerfilePath",
            "Dockerfile path",
            plan == null ? null : plan.dockerfilePath(),
            instance.dockerfilePath()
        );
        DeploymentRailwayLiveFieldDriftSummary repository = fieldDrift(
            "repository",
            "Repository",
            firstNonBlank(expectedRepository, actualRepository),
            actualRepository
        );
        DeploymentRailwayLiveFieldDriftSummary branch = fieldDrift(
            "branch",
            "Branch",
            expectedBranch,
            actualBranch
        );
        DeploymentRailwayLiveFieldDriftSummary publicBaseUrl = fieldDrift(
            "publicBaseUrl",
            "Public base URL",
            expectedBaseUrl,
            actualBaseUrl
        );

        List<DeploymentRailwayLiveEnvVarDriftSummary> envVars = buildEnvVarDrift(
            expectedEnv,
            variables,
            runtimeBaseUrl,
            connectorBaseUrl
        );
        int matchingEnvCount = (int) envVars.stream().filter(item -> "MATCHED".equals(item.driftState())).count();
        int missingEnvCount = (int) envVars.stream().filter(item -> "MISSING".equals(item.driftState())).count();
        int mismatchedEnvCount = (int) envVars.stream().filter(item -> "MISMATCHED".equals(item.driftState())).count();

        String status = List.of(rootDirectory, dockerfilePath, repository, branch, publicBaseUrl).stream()
            .anyMatch(item -> !"MATCHED".equals(item.driftState()))
            || missingEnvCount > 0
            || mismatchedEnvCount > 0
            ? "WARNING"
            : "READY";
        String summaryMessage = status.equals("READY")
            ? label + " matches Railway live settings and variables."
            : label + " has live drift against the platform-managed Railway plan.";

        return new DeploymentRailwayLiveServiceSummary(
            key,
            label,
            service.id(),
            status,
            summaryMessage,
            rootDirectory,
            dockerfilePath,
            repository,
            branch,
            publicBaseUrl,
            expectedEnv.size(),
            matchingEnvCount,
            missingEnvCount,
            mismatchedEnvCount,
            envVars
        );
    }

    private List<DeploymentRailwayLiveEnvVarDriftSummary> buildEnvVarDrift(List<RailwayEnvVarSummary> expectedEnv,
                                                                           JsonNode actualVariables,
                                                                           String runtimeBaseUrl,
                                                                           String connectorBaseUrl) {
        List<DeploymentRailwayLiveEnvVarDriftSummary> drift = new ArrayList<>();
        Map<String, String> actual = toStringMap(actualVariables);
        for (RailwayEnvVarSummary item : expectedEnv) {
            String expectedValue = resolveExpectedEnvValue(item, runtimeBaseUrl, connectorBaseUrl);
            String actualValue = actual.get(item.key());
            boolean sensitive = isSensitiveEnvValue(item.value());
            String driftState;
            String message;
            if (!actual.containsKey(item.key())) {
                driftState = "MISSING";
                message = item.key() + " is missing from the live Railway service variables.";
            } else if (safeEquals(expectedValue, actualValue)) {
                driftState = "MATCHED";
                message = item.key() + " matches the platform-managed value.";
            } else {
                driftState = "MISMATCHED";
                message = item.key() + " differs from the platform-managed value.";
            }
            drift.add(new DeploymentRailwayLiveEnvVarDriftSummary(
                item.key(),
                sensitive,
                driftState,
                sensitive ? null : expectedValue,
                sensitive ? null : actualValue,
                message
            ));
        }
        return drift;
    }

    private String resolveExpectedEnvValue(RailwayEnvVarSummary envVar,
                                           String runtimeBaseUrl,
                                           String connectorBaseUrl) {
        String serviceBaseUrl = RailwayApiProvisioningProvider.resolveServiceBaseUrl(
            envVar.key(),
            runtimeBaseUrl,
            connectorBaseUrl
        );
        if (hasText(serviceBaseUrl)) {
            return serviceBaseUrl;
        }
        String value = envVar.value();
        if (value != null && value.startsWith("${secret:") && value.endsWith("}")) {
            String secretName = value.substring("${secret:".length(), value.length() - 1).trim();
            return platformSecretService.resolveSecret(secretName);
        }
        return value;
    }

    private boolean isSensitiveEnvValue(String value) {
        return value != null && value.startsWith("${secret:") && value.endsWith("}");
    }

    private String overallStatus(DeploymentRailwayLiveServiceSummary runtime,
                                 DeploymentRailwayLiveServiceSummary restConnector,
                                 DeploymentRailwayLiveServiceSummary vectorizationRunner) {
        if ("BLOCKED".equals(runtime.status())
            || "BLOCKED".equals(restConnector.status())
            || (vectorizationRunner != null && "BLOCKED".equals(vectorizationRunner.status()))) {
            return "BLOCKED";
        }
        if ("WARNING".equals(runtime.status())
            || "WARNING".equals(restConnector.status())
            || (vectorizationRunner != null && "WARNING".equals(vectorizationRunner.status()))) {
            return "WARNING";
        }
        return "READY";
    }

    private DeploymentRailwayLiveReadbackSummary unavailable(String message) {
        return new DeploymentRailwayLiveReadbackSummary(
            false,
            "BLOCKED",
            message,
            null,
            null,
            null,
            null,
            unavailableService("runtime", "Runtime service", message),
            unavailableService("restConnector", "REST connector", message),
            null
        );
    }

    private DeploymentRailwayLiveServiceSummary unavailableService(String key,
                                                                   String label,
                                                                   String message) {
        return new DeploymentRailwayLiveServiceSummary(
            key,
            label,
            null,
            "BLOCKED",
            message,
            emptyField("rootDirectory", "Root directory"),
            emptyField("dockerfilePath", "Dockerfile path"),
            emptyField("repository", "Repository"),
            emptyField("branch", "Branch"),
            emptyField("publicBaseUrl", "Public base URL"),
            0,
            0,
            0,
            0,
            List.of()
        );
    }

    private DeploymentRailwayLiveFieldDriftSummary emptyField(String key, String label) {
        return new DeploymentRailwayLiveFieldDriftSummary(
            key,
            label,
            null,
            null,
            "UNAVAILABLE",
            label + " is unavailable from Railway live read-back."
        );
    }

    private DeploymentRailwayLiveFieldDriftSummary fieldDrift(String key,
                                                              String label,
                                                              String expectedValue,
                                                              String actualValue) {
        String driftState;
        String summaryMessage;
        if (!hasText(expectedValue) && !hasText(actualValue)) {
            driftState = "MATCHED";
            summaryMessage = label + " is unset in both the platform plan and Railway live state.";
        } else if (!hasText(actualValue)) {
            driftState = "MISSING";
            summaryMessage = label + " is missing from Railway live state.";
        } else if (safeEquals(expectedValue, actualValue)) {
            driftState = "MATCHED";
            summaryMessage = label + " matches Railway live state.";
        } else {
            driftState = "MISMATCHED";
            summaryMessage = label + " differs between the platform plan and Railway live state.";
        }
        return new DeploymentRailwayLiveFieldDriftSummary(
            key,
            label,
            expectedValue,
            actualValue,
            driftState,
            summaryMessage
        );
    }

    private Map<String, String> toStringMap(JsonNode node) {
        Map<String, String> values = new LinkedHashMap<>();
        if (node != null && node.isObject()) {
            node.fields().forEachRemaining(entry -> values.put(entry.getKey(), textValue(entry.getValue())));
        }
        return values;
    }

    private String firstTriggerRepository(RailwayGraphqlClient.RailwayServiceSourceSummary source) {
        return source.repoTriggers().stream()
            .map(RailwayGraphqlClient.RailwayDeploymentTriggerSummary::repository)
            .filter(this::hasText)
            .findFirst()
            .orElse(null);
    }

    private String firstTriggerBranch(RailwayGraphqlClient.RailwayServiceSourceSummary source) {
        return source.repoTriggers().stream()
            .map(RailwayGraphqlClient.RailwayDeploymentTriggerSummary::branch)
            .filter(this::hasText)
            .findFirst()
            .orElse(null);
    }

    private JsonNode readJson(String value) {
        try {
            return value == null || value.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse deployment release provisioning details.", ex);
        }
    }

    private String text(JsonNode node, String... path) {
        JsonNode current = node;
        for (String part : path) {
            current = current == null ? objectMapper.createObjectNode() : current.path(part);
        }
        return textValue(current);
    }

    private String textValue(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.asText(null);
        return hasText(value) ? value : null;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private boolean safeEquals(String left, String right) {
        return normalize(left).equals(normalize(right));
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
