package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.config.PlatformDeliveryProperties;
import com.ai.fabric.platform.backend.config.PlatformProvisioningProperties;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.model.RailwayArtifactUrlsSummary;
import com.ai.fabric.platform.backend.deployment.model.RailwayEnvVarSummary;
import com.ai.fabric.platform.backend.deployment.model.RailwayProvisioningPlanSummary;
import com.ai.fabric.platform.backend.deployment.model.RailwayProvisioningServicesSummary;
import com.ai.fabric.platform.backend.deployment.model.RailwayProvisioningStepSummary;
import com.ai.fabric.platform.backend.deployment.model.RailwayServicePlanSummary;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class RailwayProvisioningPlanService {

    private final PlatformProvisioningProperties provisioningProperties;
    private final PlatformDeliveryProperties deliveryProperties;
    private final DeploymentArtifactService artifactService;
    private final PlatformSecretService platformSecretService;
    private final ObjectMapper objectMapper;

    public RailwayProvisioningPlanService(PlatformProvisioningProperties provisioningProperties,
                                          PlatformDeliveryProperties deliveryProperties,
                                          DeploymentArtifactService artifactService,
                                          PlatformSecretService platformSecretService,
                                          ObjectMapper objectMapper) {
        this.provisioningProperties = provisioningProperties;
        this.deliveryProperties = deliveryProperties;
        this.artifactService = artifactService;
        this.platformSecretService = platformSecretService;
        this.objectMapper = objectMapper;
    }

    public RailwayProvisioningPlanSummary buildPlan(DeploymentEntity deployment, DeploymentVersionEntity version) {
        String runtimeBaseUrl = deployment.getRuntimeBaseUrl() != null
            ? deployment.getRuntimeBaseUrl()
            : "https://runtime-" + deployment.getId() + ".placeholder.local";
        String connectorBaseUrl = deployment.getConnectorBaseUrl() != null
            ? deployment.getConnectorBaseUrl()
            : "https://connector-" + deployment.getId() + ".placeholder.local";
        JsonNode providerConfig = readJson(version.getProviderConfigJson());
        JsonNode securityConfig = readJson(version.getSecurityConfigJson());

        var artifacts = artifactService.toBundleSummary(version);
        RailwayArtifactUrlsSummary artifactUrls = new RailwayArtifactUrlsSummary(
            artifacts.actionsArtifactUrl(),
            artifacts.entityArtifactUrl(),
            artifacts.routingArtifactUrl(),
            artifacts.manifestUrl()
        );

        List<RailwayEnvVarSummary> runtimeEnv = new ArrayList<>();
        runtimeEnv.add(new RailwayEnvVarSummary("AI_ACTIONS_CATALOG_PATH", artifactUrls.actions()));
        runtimeEnv.add(new RailwayEnvVarSummary("AI_CONFIG_DEFAULT_FILE", artifactUrls.entities()));
        runtimeEnv.add(new RailwayEnvVarSummary("ACTIONS_CONNECTOR_BASE_URL", connectorBaseUrl));
        runtimeEnv.add(new RailwayEnvVarSummary("ACTIONS_CONNECTOR_API_KEY", "${secret:ACTIONS_CONNECTOR_API_KEY}"));
        runtimeEnv.add(new RailwayEnvVarSummary("OPENAI_API_KEY", "${secret:OPENAI_API_KEY}"));
        runtimeEnv.add(new RailwayEnvVarSummary("OPENAI_ENABLED", Boolean.toString(isOpenAiEnabled(providerConfig))));
        runtimeEnv.add(new RailwayEnvVarSummary(
            "AI_FABRIC_RUNTIME_DEV_DEFAULTS_ENABLED",
            Boolean.toString(provisioningProperties.runtimeDevDefaultsEnabled())
        ));
        if (securityConfig.path("adminApiKeyEnabled").asBoolean(false)
            && platformSecretService.isSecretPresent("APP_ADMIN_API_KEY")) {
            runtimeEnv.add(new RailwayEnvVarSummary("APP_ADMIN_API_KEY", "${secret:APP_ADMIN_API_KEY}"));
            runtimeEnv.add(new RailwayEnvVarSummary("APP_ADMIN_API_KEY_HEADER", "X-ADMIN-API-KEY"));
        }
        addCorsEnv(runtimeEnv);
        addOptionalEnv(runtimeEnv, "AUTHZ_BASE_URL", text(securityConfig, "authzBaseUrl"));

        RailwayServicePlanSummary runtime = new RailwayServicePlanSummary(
            provisioningProperties.runtimeServiceNamePrefix() + "-" + deployment.getId(),
            resolveRootDirectory(provisioningProperties.runtimeDockerfilePath(), provisioningProperties.runtimeServiceRoot()),
            provisioningProperties.runtimeDockerfilePath(),
            runtimeBaseUrl,
            runtimeEnv
        );

        List<RailwayEnvVarSummary> connectorEnv = new ArrayList<>();
        connectorEnv.add(new RailwayEnvVarSummary("REST_CONNECTOR_ROUTING_CONFIG_LOCATION", artifactUrls.routing()));
        connectorEnv.add(new RailwayEnvVarSummary("REST_CONNECTOR_RUNTIME_PROXY_ENABLED", "true"));
        connectorEnv.add(new RailwayEnvVarSummary("REST_CONNECTOR_RUNTIME_PROXY_BASE_URL", runtimeBaseUrl));
        connectorEnv.add(new RailwayEnvVarSummary(
            "REST_CONNECTOR_RUNTIME_PROXY_API_KEY",
            "${secret:ACTIONS_CONNECTOR_API_KEY}"
        ));
        connectorEnv.add(new RailwayEnvVarSummary(
            "REST_CONNECTOR_RUNTIME_PROXY_API_KEY_HEADER",
            "X-AIFABRIC-API-KEY"
        ));
        connectorEnv.add(new RailwayEnvVarSummary(
            "REST_CONNECTOR_RUNTIME_PROXY_TIMEOUT_MS",
            Integer.toString(provisioningProperties.runtimeProxyTimeoutMs())
        ));
        connectorEnv.add(new RailwayEnvVarSummary("CONNECTOR_API_KEY", "${secret:CONNECTOR_API_KEY}"));
        addCorsEnv(connectorEnv);

        RailwayServicePlanSummary restConnector = new RailwayServicePlanSummary(
            provisioningProperties.connectorServiceNamePrefix() + "-" + deployment.getId(),
            resolveRootDirectory(
                provisioningProperties.connectorDockerfilePath(),
                provisioningProperties.connectorServiceRoot()
            ),
            provisioningProperties.connectorDockerfilePath(),
            connectorBaseUrl,
            connectorEnv
        );

        return new RailwayProvisioningPlanSummary(
            deployment.getId(),
            deployment.getName(),
            deployment.getEnvironmentName(),
            deployment.getTemplateId(),
            version.getId(),
            version.getVersionLabel(),
            version.getConfigHash(),
            provisioningProperties.mode(),
            buildProjectName(deployment),
            provisioningProperties.repository(),
            provisioningProperties.branch(),
            provisioningProperties.workspaceId().isBlank() ? null : provisioningProperties.workspaceId(),
            artifactServiceSignedStrategy(),
            artifactUrls,
            new RailwayProvisioningServicesSummary(runtime, restConnector),
            List.of(
                new RailwayProvisioningStepSummary(1, "publish_artifacts", "Resolve immutable config artifact URLs for the selected version."),
                new RailwayProvisioningStepSummary(2, "prepare_project", "Create or reuse the Railway project for this customer environment."),
                new RailwayProvisioningStepSummary(3, "configure_runtime", "Create or update the runtime service root and its environment variables."),
                new RailwayProvisioningStepSummary(4, "configure_rest_connector", "Create or update the REST connector service root and its environment variables."),
                new RailwayProvisioningStepSummary(5, "trigger_deploy", "Commit staged changes or trigger Railway deployment/redeploy for both services."),
                new RailwayProvisioningStepSummary(6, "wait_for_active", "Wait for Railway deployment states to become active."),
                new RailwayProvisioningStepSummary(7, "run_verification", "Run post-deploy verification against runtime and connector endpoints.")
            )
        );
    }

    private String artifactServiceSignedStrategy() {
        return deliveryProperties.signedArtifactsEnabled()
            ? "SIGNED_REMOTE_CONFIG_BUNDLES"
            : "REMOTE_CONFIG_BUNDLES";
    }

    private String normalizeName(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("-{2,}", "-")
            .replaceAll("(^-|-$)", "");
    }

    private String buildProjectName(DeploymentEntity deployment) {
        String environment = fallbackName(normalizeName(deployment.getEnvironmentName()), "dev");
        String deploymentSlug = fallbackName(normalizeName(deployment.getName()), "deployment");
        String idSuffix = fallbackName(
            normalizeName(shortDeploymentId(deployment.getId())),
            "default"
        );
        String suffix = environment + "-" + idSuffix;
        int maxLength = provisioningProperties.projectNameMaxLength();
        int maxBaseLength = Math.max(1, maxLength - suffix.length() - 1);
        String truncatedBase = trimHyphenated(deploymentSlug.substring(0, Math.min(deploymentSlug.length(), maxBaseLength)));
        if (truncatedBase.isBlank()) {
            truncatedBase = "deployment";
            if (truncatedBase.length() > maxBaseLength) {
                truncatedBase = truncatedBase.substring(0, maxBaseLength);
            }
            truncatedBase = trimHyphenated(truncatedBase);
        }
        String projectName = trimHyphenated(truncatedBase + "-" + suffix);
        if (projectName.length() > maxLength) {
            projectName = trimHyphenated(projectName.substring(0, maxLength));
        }
        return fallbackName(projectName, "deployment-" + idSuffix);
    }

    private String shortDeploymentId(String deploymentId) {
        String normalized = normalizeName(deploymentId);
        if (normalized.startsWith("dep-")) {
            normalized = normalized.substring(4);
        }
        return normalized.length() <= 8 ? normalized : normalized.substring(0, 8);
    }

    private String fallbackName(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String trimHyphenated(String value) {
        return value == null ? "" : value.replaceAll("(^-|-$)", "");
    }

    private JsonNode readJson(String value) {
        try {
            return value == null || value.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse deployment version config JSON.", ex);
        }
    }

    private boolean isOpenAiEnabled(JsonNode providerConfig) {
        return "openai".equalsIgnoreCase(text(providerConfig, "llmProvider"))
            || "openai".equalsIgnoreCase(text(providerConfig, "embeddingProvider"));
    }

    private String text(JsonNode node, String field) {
        if (node == null) {
            return "";
        }
        return node.path(field).asText("").trim();
    }

    private void addCorsEnv(List<RailwayEnvVarSummary> env) {
        addOptionalEnv(env, "CORS_ALLOWED_ORIGINS", provisioningProperties.corsAllowedOrigins());
        addOptionalEnv(env, "CORS_ALLOWED_ORIGIN_PATTERNS", provisioningProperties.corsAllowedOriginPatterns());
        env.add(new RailwayEnvVarSummary(
            "CORS_ALLOW_CREDENTIALS",
            Boolean.toString(provisioningProperties.corsAllowCredentials())
        ));
    }

    private void addOptionalEnv(List<RailwayEnvVarSummary> env, String key, String value) {
        if (value != null && !value.isBlank()) {
            env.add(new RailwayEnvVarSummary(key, value));
        }
    }

    private String resolveRootDirectory(String dockerfilePath, String configuredRootDirectory) {
        if (dockerfilePath != null && !dockerfilePath.isBlank()) {
            // The Railway-specific Dockerfiles in this repo expect repo-root build context.
            return null;
        }
        return configuredRootDirectory;
    }
}
