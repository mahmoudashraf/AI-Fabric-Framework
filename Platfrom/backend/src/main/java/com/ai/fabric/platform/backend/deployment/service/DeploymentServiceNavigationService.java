package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.config.PlatformProvisioningProperties;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentDraftEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentNavigationProviderSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentNavigationRelationshipSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentNavigationSurfaceSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentServiceConfigModelSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentServiceConfigSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentServiceNavigationSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentSourceSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentTemplateSummary;
import com.ai.fabric.platform.backend.deployment.model.RailwayProvisioningPlanSummary;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class DeploymentServiceNavigationService {

    private final DeploymentServiceConfigModelService deploymentServiceConfigModelService;
    private final DeploymentSourceResolver deploymentSourceResolver;
    private final RailwayProvisioningPlanService railwayProvisioningPlanService;
    private final PlatformProvisioningProperties provisioningProperties;
    private final ObjectMapper objectMapper;

    public DeploymentServiceNavigationService(DeploymentServiceConfigModelService deploymentServiceConfigModelService,
                                              DeploymentSourceResolver deploymentSourceResolver,
                                              RailwayProvisioningPlanService railwayProvisioningPlanService,
                                              PlatformProvisioningProperties provisioningProperties,
                                              ObjectMapper objectMapper) {
        this.deploymentServiceConfigModelService = deploymentServiceConfigModelService;
        this.deploymentSourceResolver = deploymentSourceResolver;
        this.railwayProvisioningPlanService = railwayProvisioningPlanService;
        this.provisioningProperties = provisioningProperties;
        this.objectMapper = objectMapper;
    }

    public DeploymentServiceNavigationSummary build(DeploymentEntity deployment,
                                                    DeploymentDraftEntity draft,
                                                    DeploymentTemplateSummary template,
                                                    DeploymentVersionEntity latestPublishedVersion,
                                                    DeploymentVersionEntity liveVersion,
                                                    DeploymentReleaseEntity latestRelease) {
        DeploymentSourceSummary source = deploymentSourceResolver.summarize(deployment);
        DeploymentServiceConfigModelSummary serviceModel = deploymentServiceConfigModelService.build(
            deployment,
            draft,
            latestPublishedVersion,
            template,
            source
        );
        Map<String, DeploymentServiceConfigSummary> servicesByKey = serviceModel.services().stream()
            .collect(Collectors.toMap(DeploymentServiceConfigSummary::key, service -> service));

        DeploymentVersionEntity referenceVersion = liveVersion != null ? liveVersion : latestPublishedVersion;
        RailwayProvisioningPlanSummary plan = referenceVersion == null
            ? null
            : railwayProvisioningPlanService.buildPlan(deployment, referenceVersion);
        JsonNode provisioningDetails = readJson(latestRelease == null ? null : latestRelease.getProvisioningDetailsJson());

        DeploymentNavigationProviderSummary provider = providerSummary(
            source,
            latestRelease,
            provisioningDetails,
            plan
        );

        List<DeploymentNavigationSurfaceSummary> surfaces = List.of(
            runtimeSurface(deployment, servicesByKey.get("runtime"), plan),
            connectorSurface(deployment, servicesByKey.get("restConnector"), plan),
            upstreamSurface(servicesByKey.get("upstreamStore")),
            browserSurface(servicesByKey.get("uiSurface"))
        );

        List<DeploymentNavigationRelationshipSummary> relationships = List.of(
            relationship(
                "browser-to-runtime",
                "Browser clients",
                "Runtime service",
                "INGRESS",
                hasText(deployment.getRuntimeBaseUrl())
                    ? "Browser and operator traffic reaches the deployment through the runtime entry point when the configured auth mode allows external access."
                    : "Apply the deployment to create the runtime entry point."
            ),
            relationship(
                "runtime-admin-to-connector",
                "Runtime admin surface and operator tools",
                "REST connector",
                "ADMIN_AND_SYNC",
                hasText(deployment.getConnectorBaseUrl())
                    ? hasText(deployment.getRuntimeBaseUrl())
                        ? "Supported connector inspection flows are exposed through runtime admin endpoints, while the connector root remains an internal operator surface."
                        : "The connector internal service exists, but runtime-backed connector admin inspection becomes available only after the runtime is applied."
                    : "Apply the deployment to create the REST connector internal service."
            ),
            relationship(
                "runtime-to-connector",
                "Runtime service",
                "REST connector",
                "ACTION_EXECUTION",
                hasText(deployment.getRuntimeBaseUrl()) && hasText(deployment.getConnectorBaseUrl())
                    ? "Runtime calls the REST connector for routed actions and data-sync operations."
                    : "Runtime-to-connector traffic is only live after the runtime and internal connector service are both applied."
            ),
            relationship(
                "connector-to-runtime",
                "REST connector",
                "Runtime admin surface",
                "RUNTIME_PROXY",
                hasText(deployment.getRuntimeBaseUrl()) && hasText(deployment.getConnectorBaseUrl())
                    ? "The connector proxies selected protected runtime operations such as indexing and admin flows."
                    : "The runtime proxy path becomes meaningful after the deployment is applied."
            ),
            relationship(
                "artifacts-to-runtime",
                "Published config artifacts",
                "Runtime service",
                "CONFIG_SOURCE",
                latestPublishedVersion != null
                    ? "Published action and entity artifacts define the immutable runtime config baseline."
                    : "Publish a version to generate immutable runtime config artifacts."
            ),
            relationship(
                "artifacts-to-connector",
                "Published routing artifact",
                "REST connector",
                "CONFIG_SOURCE",
                latestPublishedVersion != null
                    ? "Published routing artifacts control the connector's exposed action routes."
                    : "Publish a version to generate immutable connector routing artifacts."
            ),
            relationship(
                "connector-to-upstream",
                "REST connector",
                "Store and upstream API",
                "UPSTREAM_CALL",
                servicesByKey.containsKey("upstreamStore") && hasText(servicesByKey.get("upstreamStore").baseUrl())
                    ? "The connector reaches the upstream system for routed business actions and delegated authz checks."
                    : "Configure an upstream base URL before routed actions can reach the external business system."
            ),
            relationship(
                "provider-console-to-services",
                "Railway project",
                "Runtime and internal connector services",
                "PROVIDER_CONSOLE",
                provider.available()
                    ? "Use the Railway project for deployment status, provider-side logs, and service domain inspection."
                    : "Railway project navigation becomes available after the deployment has a provider-side project id."
            )
        );

        String summaryMessage = provider.available()
            ? "Provider console, platform-managed service links, external dependency references, and client-facing surface context are available from one deployment-scoped workspace."
            : "Deployment surface navigation is available, but the Railway project console link will appear only after live provider provisioning records a project id.";

        return new DeploymentServiceNavigationSummary(
            deployment.getId(),
            deployment.getName(),
            deployment.getEnvironmentName(),
            provider,
            surfaces,
            relationships,
            summaryMessage
        );
    }

    private DeploymentNavigationProviderSummary providerSummary(DeploymentSourceSummary source,
                                                                DeploymentReleaseEntity latestRelease,
                                                                JsonNode provisioningDetails,
                                                                RailwayProvisioningPlanSummary plan) {
        String mode = latestRelease != null && hasText(latestRelease.getProvisioningTarget())
            ? latestRelease.getProvisioningTarget()
            : provisioningProperties.mode();
        String projectId = firstNonBlank(
            text(provisioningDetails, "projectId"),
            text(provisioningDetails, "railway", "projectId")
        );
        String projectName = firstNonBlank(
            text(provisioningDetails, "projectName"),
            text(provisioningDetails, "railway", "projectName"),
            plan == null ? null : plan.projectName()
        );
        String workspaceId = firstNonBlank(
            text(provisioningDetails, "workspaceId"),
            text(provisioningDetails, "railway", "workspaceId"),
            provisioningProperties.workspaceId()
        );
        String projectUrl = hasText(projectId) ? "https://railway.com/project/" + projectId : null;
        boolean available = hasText(projectUrl);
        String summaryMessage = available
            ? "Provider-side project link is available for direct Railway inspection."
            : hasText(projectName)
                ? "The deployment has a resolved Railway project name, but no provider project id is recorded yet."
                : "No Railway project has been recorded for this deployment yet.";
        return new DeploymentNavigationProviderSummary(
            "Railway",
            mode,
            projectName,
            projectId,
            projectUrl,
            workspaceId,
            plan == null ? source.repository() : plan.repository(),
            plan == null ? source.branch() : plan.branch(),
            available,
            summaryMessage
        );
    }

    private DeploymentNavigationSurfaceSummary runtimeSurface(DeploymentEntity deployment,
                                                              DeploymentServiceConfigSummary config,
                                                              RailwayProvisioningPlanSummary plan) {
        return surface(
            "runtime",
            "Runtime service",
            "PROVISIONED_SERVICE",
            true,
            config == null ? "AI runtime integration surface with auth-mode-bound external access." : config.purpose(),
            plan == null ? null : plan.services().runtime().serviceName(),
            plan == null ? null : plan.services().runtime().rootDir(),
            plan == null ? null : plan.services().runtime().dockerfilePath(),
            deployment.getRuntimeBaseUrl(),
            joinUrl(deployment.getRuntimeBaseUrl(), "/api/admin/actions/overview"),
            hasText(deployment.getRuntimeBaseUrl())
                ? "Runtime entry point, admin surface, and docs are available. External access still depends on the configured auth mode."
                : "Runtime entry point has not been created yet."
        );
    }

    private DeploymentNavigationSurfaceSummary connectorSurface(DeploymentEntity deployment,
                                                                DeploymentServiceConfigSummary config,
                                                                RailwayProvisioningPlanSummary plan) {
        boolean connectorAvailable = hasText(deployment.getConnectorBaseUrl());
        String runtimeBackedAdminUrl = connectorAvailable
            ? joinUrl(deployment.getRuntimeBaseUrl(), "/api/admin/connector/overview")
            : null;
        String runtimeBackedDocsUrl = connectorAvailable ? swaggerUiUrl(deployment.getRuntimeBaseUrl()) : null;
        String runtimeBackedOpenApiUrl = connectorAvailable ? openApiUrl(deployment.getRuntimeBaseUrl()) : null;
        String summaryMessage = connectorAvailable
            ? hasText(deployment.getRuntimeBaseUrl())
                ? "REST connector is an internal service. Use runtime-backed admin overview and runtime docs for supported inspection; use the connector root only for operator-level internal debugging."
                : "REST connector internal service is available, but runtime-backed admin inspection becomes available only after the runtime is applied."
            : "REST connector internal service has not been created yet.";
        return new DeploymentNavigationSurfaceSummary(
            "restConnector",
            "REST connector",
            "PROVISIONED_SERVICE",
            true,
            config == null ? "REST connector internal execution surface." : config.purpose(),
            plan == null ? null : plan.services().restConnector().serviceName(),
            plan == null ? null : plan.services().restConnector().rootDir(),
            plan == null ? null : plan.services().restConnector().dockerfilePath(),
            null,
            runtimeBackedDocsUrl,
            runtimeBackedOpenApiUrl,
            runtimeBackedAdminUrl,
            connectorAvailable,
            summaryMessage
        );
    }

    private DeploymentNavigationSurfaceSummary upstreamSurface(DeploymentServiceConfigSummary config) {
        String baseUrl = config == null ? null : config.baseUrl();
        return new DeploymentNavigationSurfaceSummary(
            "upstreamStore",
            "Store and upstream API",
            "EXTERNAL_DEPENDENCY",
            false,
            config == null ? "External upstream service used by routed actions." : config.purpose(),
            null,
            null,
            null,
            baseUrl,
            null,
            null,
            null,
            hasText(baseUrl),
            hasText(baseUrl)
                ? "The upstream business system is configured as a reachable external target."
                : "No upstream base URL is configured yet for routed business logic."
        );
    }

    private DeploymentNavigationSurfaceSummary browserSurface(DeploymentServiceConfigSummary config) {
        return new DeploymentNavigationSurfaceSummary(
            "uiSurface",
            "UI and browser surface",
            "CLIENT_SURFACE",
            false,
            config == null ? "Browser-facing surface that consumes runtime URLs or trusted host-backed APIs while the connector remains internal." : config.purpose(),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            false,
            config == null
                ? "Browser clients should integrate through auth-mode-bound runtime URLs or trusted host-backed APIs. The connector remains an internal service."
                : config.summaryMessage()
        );
    }

    private DeploymentNavigationSurfaceSummary surface(String key,
                                                       String label,
                                                       String surfaceType,
                                                       boolean platformManaged,
                                                       String purpose,
                                                       String serviceName,
                                                       String rootDir,
                                                       String dockerfilePath,
                                                       String primaryUrl,
                                                       String adminUrl,
                                                       String summaryMessage) {
        return new DeploymentNavigationSurfaceSummary(
            key,
            label,
            surfaceType,
            platformManaged,
            purpose,
            serviceName,
            rootDir,
            dockerfilePath,
            primaryUrl,
            swaggerUiUrl(primaryUrl),
            openApiUrl(primaryUrl),
            adminUrl,
            hasText(primaryUrl),
            summaryMessage
        );
    }

    private DeploymentNavigationRelationshipSummary relationship(String key,
                                                                 String fromLabel,
                                                                 String toLabel,
                                                                 String flowType,
                                                                 String summaryMessage) {
        return new DeploymentNavigationRelationshipSummary(key, fromLabel, toLabel, flowType, summaryMessage);
    }

    private JsonNode readJson(String value) {
        if (!hasText(value)) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(value);
        } catch (Exception ex) {
            return objectMapper.createObjectNode();
        }
    }

    private String swaggerUiUrl(String baseUrl) {
        return hasText(baseUrl) ? baseUrl.replaceAll("/$", "") + "/swagger-ui/index.html" : null;
    }

    private String openApiUrl(String baseUrl) {
        return hasText(baseUrl) ? baseUrl.replaceAll("/$", "") + "/v3/api-docs" : null;
    }

    private String joinUrl(String baseUrl, String path) {
        if (!hasText(baseUrl)) {
            return null;
        }
        return baseUrl.replaceAll("/$", "") + path;
    }

    private String text(JsonNode node, String... path) {
        JsonNode current = node;
        for (String part : path) {
            current = current.path(part);
        }
        return current.isMissingNode() || current.isNull() ? null : trimToNull(current.asText());
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String trimToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }
}
