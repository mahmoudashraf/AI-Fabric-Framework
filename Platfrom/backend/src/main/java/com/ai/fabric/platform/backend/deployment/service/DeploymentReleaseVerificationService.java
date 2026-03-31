package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.config.PlatformVerificationProperties;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVerificationRunEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentArtifactBundleSummary;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class DeploymentReleaseVerificationService {

    private final ObjectMapper objectMapper;
    private final PlatformVerificationProperties verificationProperties;
    private final PlatformSecretService platformSecretService;
    private final DeploymentArtifactService deploymentArtifactService;
    private final HttpClient httpClient;

    public DeploymentReleaseVerificationService(ObjectMapper objectMapper,
                                                PlatformVerificationProperties verificationProperties,
                                                PlatformSecretService platformSecretService,
                                                DeploymentArtifactService deploymentArtifactService) {
        this.objectMapper = objectMapper;
        this.verificationProperties = verificationProperties;
        this.platformSecretService = platformSecretService;
        this.deploymentArtifactService = deploymentArtifactService;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(verificationProperties.timeout())
            .build();
    }

    public DeploymentVerificationRunEntity verify(DeploymentEntity deployment,
                                                  DeploymentVersionEntity version,
                                                  DeploymentReleaseEntity release,
                                                  String verificationType) {
        Instant now = Instant.now();
        ArrayNode checks = objectMapper.createArrayNode();
        DeploymentArtifactBundleSummary artifacts = deploymentArtifactService.toBundleSummary(version);
        VerificationExpectations expectations = buildExpectations(version, artifacts);

        addBooleanCheck(
            checks,
            "active_version_matches_release",
            version.getId().equals(deployment.getActiveVersionId()),
            "Deployment active version matches the applied release version."
        );
        addBooleanCheck(
            checks,
            "runtime_base_url_present",
            hasText(deployment.getRuntimeBaseUrl()),
            "Runtime base URL is populated."
        );
        addBooleanCheck(
            checks,
            "connector_base_url_present",
            hasText(deployment.getConnectorBaseUrl()),
            "Connector base URL is populated."
        );
        addBooleanCheck(
            checks,
            "provisioning_details_present",
            hasText(release.getProvisioningDetailsJson()),
            "Provisioning details were captured for this release."
        );
        addBooleanCheck(
            checks,
            "version_manifest_present",
            hasText(version.getManifestJson()),
            "Compiled manifest exists for the release version."
        );
        verifyLiveEndpoints(checks, deployment, release, expectations);

        int passed = 0;
        int failed = 0;
        int skipped = 0;
        for (JsonNode check : checks) {
            String status = check.path("status").asText();
            if ("PASSED".equals(status)) {
                passed += 1;
            } else if ("FAILED".equals(status)) {
                failed += 1;
            } else if ("SKIPPED".equals(status)) {
                skipped += 1;
            }
        }

        DeploymentVerificationRunEntity run = new DeploymentVerificationRunEntity();
        run.setId(generateId("vrf"));
        run.setDeploymentId(deployment.getId());
        run.setReleaseId(release.getId());
        run.setDeploymentVersionId(version.getId());
        run.setVerificationType(verificationType);
        run.setStatus(failed == 0 ? "PASSED" : "FAILED");
        run.setSummaryMessage(passed + " passed, " + failed + " failed, " + skipped + " skipped");
        run.setChecksJson(checks.toPrettyString());
        run.setCreatedAt(now);
        run.setCompletedAt(now);
        return run;
    }

    private void verifyLiveEndpoints(ArrayNode checks,
                                     DeploymentEntity deployment,
                                     DeploymentReleaseEntity release,
                                     VerificationExpectations expectations) {
        if ("RAILWAY_STUB".equalsIgnoreCase(release.getProvisioningTarget())) {
            addSkippedCheck(checks, "runtime_health_http_probe",
                "Live runtime probe skipped because the deployment is still using stub provisioning.");
            addSkippedCheck(checks, "connector_health_http_probe",
                "Live connector probe skipped because the deployment is still using stub provisioning.");
            addSkippedCheck(checks, "runtime_admin_overview_http_probe",
                "Runtime admin overview probe skipped because the deployment is still using stub provisioning.");
            addSkippedCheck(checks, "runtime_actions_overview_http_probe",
                "Runtime actions overview probe skipped because the deployment is still using stub provisioning.");
            addSkippedCheck(checks, "runtime_indexing_overview_http_probe",
                "Runtime indexing overview probe skipped because the deployment is still using stub provisioning.");
            addSkippedCheck(checks, "connector_admin_overview_http_probe",
                "Connector admin overview probe skipped because the deployment is still using stub provisioning.");
            addSkippedCheck(checks, "connector_actions_overview_http_probe",
                "Connector actions overview probe skipped because the deployment is still using stub provisioning.");
            addSkippedCheck(checks, "runtime_config_matches_expected",
                "Runtime config validation skipped because the deployment is still using stub provisioning.");
            addSkippedCheck(checks, "runtime_prompt_config_matches_expected",
                "Runtime prompt config validation skipped because the deployment is still using stub provisioning.");
            addSkippedCheck(checks, "runtime_actions_match_expected",
                "Runtime action validation skipped because the deployment is still using stub provisioning.");
            addSkippedCheck(checks, "runtime_entity_types_match_expected",
                "Runtime entity validation skipped because the deployment is still using stub provisioning.");
            addSkippedCheck(checks, "connector_config_matches_expected",
                "Connector config validation skipped because the deployment is still using stub provisioning.");
            addSkippedCheck(checks, "connector_actions_match_expected",
                "Connector action validation skipped because the deployment is still using stub provisioning.");
            addSkippedCheck(checks, "connector_authz_configuration_matches_expected",
                "Connector authz validation skipped because the deployment is still using stub provisioning.");
            return;
        }

        addHttpProbe(
            checks,
            "runtime_health_http_probe",
            deployment.getRuntimeBaseUrl(),
            verificationProperties.runtimeHealthPath(),
            "Runtime"
        );
        addHttpProbe(
            checks,
            "connector_health_http_probe",
            deployment.getConnectorBaseUrl(),
            verificationProperties.connectorHealthPath(),
            "Connector"
        );

        Map<String, String> runtimeAdminHeaders = runtimeAdminHeaders();
        Map<String, String> connectorAdminHeaders = connectorAdminHeaders(expectations.routingConfig());

        JsonProbeResult runtimeOverview = probeJson(
            deployment.getRuntimeBaseUrl(),
            verificationProperties.runtimeAdminOverviewPath(),
            runtimeAdminHeaders
        );
        addProbeCheck(checks, "runtime_admin_overview_http_probe", "Runtime admin overview", runtimeOverview);
        validateRuntimeOverview(checks, runtimeOverview, expectations);

        JsonProbeResult runtimeActionsOverview = probeJson(
            deployment.getRuntimeBaseUrl(),
            verificationProperties.runtimeActionsOverviewPath(),
            runtimeAdminHeaders
        );
        addProbeCheck(checks, "runtime_actions_overview_http_probe", "Runtime actions overview", runtimeActionsOverview);
        validateRuntimeActions(checks, runtimeActionsOverview, expectations);

        JsonProbeResult runtimeIndexingOverview = probeJson(
            deployment.getRuntimeBaseUrl(),
            verificationProperties.runtimeIndexingOverviewPath(),
            runtimeAdminHeaders
        );
        addProbeCheck(checks, "runtime_indexing_overview_http_probe", "Runtime indexing overview", runtimeIndexingOverview);
        validateRuntimeIndexing(checks, runtimeIndexingOverview, expectations);

        JsonProbeResult connectorOverview = probeJson(
            deployment.getConnectorBaseUrl(),
            verificationProperties.connectorAdminOverviewPath(),
            connectorAdminHeaders
        );
        addProbeCheck(checks, "connector_admin_overview_http_probe", "Connector admin overview", connectorOverview);
        validateConnectorOverview(checks, connectorOverview, expectations);
        validateConnectorAuthz(checks, connectorOverview, expectations);

        JsonProbeResult connectorActionsOverview = probeJson(
            deployment.getConnectorBaseUrl(),
            verificationProperties.connectorActionsOverviewPath(),
            connectorAdminHeaders
        );
        addProbeCheck(checks, "connector_actions_overview_http_probe", "Connector actions overview", connectorActionsOverview);
        validateConnectorActions(checks, connectorActionsOverview, expectations);
    }

    private Map<String, String> runtimeAdminHeaders() {
        String adminApiKey = platformSecretService.resolveSecret("APP_ADMIN_API_KEY");
        if (!hasText(adminApiKey)) {
            return Map.of();
        }
        return Map.of("X-ADMIN-API-KEY", adminApiKey.trim());
    }

    private Map<String, String> connectorAdminHeaders(JsonNode routingConfig) {
        String adminApiKey = platformSecretService.resolveSecret("APP_ADMIN_API_KEY");
        if (hasText(adminApiKey)) {
            return Map.of("X-ADMIN-API-KEY", adminApiKey.trim());
        }
        JsonNode inboundAuth = routingConfig.path("connector").path("inbound-auth");
        if (inboundAuth.path("allow-unauthenticated").asBoolean(false)) {
            return Map.of();
        }
        JsonNode apiKey = inboundAuth.path("api-key");
        if (!apiKey.path("enabled").asBoolean(false)) {
            return Map.of();
        }
        String header = apiKey.path("header").asText("").trim();
        String secretValue = platformSecretService.resolveSecret("CONNECTOR_API_KEY");
        if (!hasText(header) || !hasText(secretValue)) {
            return Map.of();
        }
        return Map.of(header, secretValue.trim());
    }

    private VerificationExpectations buildExpectations(DeploymentVersionEntity version,
                                                       DeploymentArtifactBundleSummary artifacts) {
        JsonNode actionsConfig = readJson(version.getActionsConfigJson());
        JsonNode entityConfig = readJson(version.getEntityConfigJson());
        JsonNode routingConfig = readJson(version.getRoutingConfigJson());

        Set<String> expectedActionNames = new LinkedHashSet<>();
        JsonNode actions = actionsConfig.path("actions");
        if (actions.isArray()) {
            for (JsonNode action : actions) {
                String name = action.path("name").asText("").trim();
                if (hasText(name)) {
                    expectedActionNames.add(name);
                }
            }
        }

        Set<String> expectedEntityTypes = new LinkedHashSet<>();
        JsonNode entities = entityConfig.path("ai-entities");
        if (entities.isObject()) {
            entities.fieldNames().forEachRemaining(name -> {
                if (hasText(name)) {
                    expectedEntityTypes.add(name.trim());
                }
            });
        }

        Set<String> expectedRoutingActions = new LinkedHashSet<>();
        JsonNode routingActions = routingConfig.path("actions");
        if (routingActions.isObject()) {
            routingActions.fieldNames().forEachRemaining(name -> {
                if (hasText(name)) {
                    expectedRoutingActions.add(name.trim());
                }
            });
        }

        boolean expectedAuthzEnabled = routingConfig.path("authz").path("enabled").asBoolean(false);

        return new VerificationExpectations(
            artifacts,
            actionsConfig,
            entityConfig,
            routingConfig,
            expectedActionNames,
            expectedEntityTypes,
            expectedRoutingActions,
            expectedAuthzEnabled
        );
    }

    private void validateRuntimeOverview(ArrayNode checks,
                                         JsonProbeResult probe,
                                         VerificationExpectations expectations) {
        if (!probe.success() || probe.body() == null) {
            addDependentCheckSkipped(checks, "runtime_config_matches_expected", "Runtime config validation skipped because the admin overview probe failed.");
            return;
        }

        String entityConfigLocation = probe.body().path("entityConfigLocation").asText("");
        String promptConfigLocation = probe.body().path("promptConfigLocation").asText("");
        Set<String> actionSourcePaths = textSet(probe.body().path("actionCatalogSources"), "path");
        int actionsCount = probe.body().path("actionsCount").asInt(-1);
        Set<String> supportedEntityTypes = textSet(probe.body().path("supportedEntityTypes"));

        ObjectNode details = objectMapper.createObjectNode();
        details.put("expectedEntityConfigLocation", expectations.artifacts().entityArtifactUrl());
        details.put("actualEntityConfigLocation", entityConfigLocation);
        details.put("expectedPromptConfigLocation", expectations.artifacts().promptArtifactUrl());
        details.put("actualPromptConfigLocation", promptConfigLocation);
        details.put("expectedActionsArtifactUrl", expectations.artifacts().actionsArtifactUrl());
        details.put("actionsCount", actionsCount);
        details.put("expectedActionsCount", expectations.expectedActionNames().size());
        details.set("actionSourcePaths", toArrayNode(actionSourcePaths));
        details.set("supportedEntityTypes", toArrayNode(supportedEntityTypes));
        details.set("expectedEntityTypes", toArrayNode(expectations.expectedEntityTypes()));

        boolean passed = probe.body().path("success").asBoolean(false)
            && expectations.artifacts().entityArtifactUrl().equals(entityConfigLocation)
            && actionSourcePaths.contains(expectations.artifacts().actionsArtifactUrl())
            && actionsCount == expectations.expectedActionNames().size()
            && supportedEntityTypes.equals(expectations.expectedEntityTypes());

        addCheck(
            checks,
            "runtime_config_matches_expected",
            passed ? "PASSED" : "FAILED",
            passed
                ? "Runtime loaded the expected action catalog source and entity configuration."
                : "Runtime admin overview does not match the published platform configuration.",
            details
        );

        boolean promptConfigPassed = probe.body().path("success").asBoolean(false)
            && expectations.artifacts().promptArtifactUrl().equals(promptConfigLocation);
        addCheck(
            checks,
            "runtime_prompt_config_matches_expected",
            promptConfigPassed ? "PASSED" : "FAILED",
            promptConfigPassed
                ? "Runtime loaded the expected deployment prompt config artifact."
                : "Runtime prompt config does not match the published platform prompt artifact.",
            details.deepCopy()
        );
    }

    private void validateRuntimeActions(ArrayNode checks,
                                        JsonProbeResult probe,
                                        VerificationExpectations expectations) {
        if (!probe.success() || probe.body() == null) {
            addDependentCheckSkipped(checks, "runtime_actions_match_expected", "Runtime action validation skipped because the actions overview probe failed.");
            return;
        }

        Set<String> loadedActionNames = textSet(probe.body().path("actions"), "name");
        int count = probe.body().path("count").asInt(-1);

        ObjectNode details = objectMapper.createObjectNode();
        details.put("count", count);
        details.put("expectedCount", expectations.expectedActionNames().size());
        details.set("loadedActionNames", toArrayNode(loadedActionNames));
        details.set("expectedActionNames", toArrayNode(expectations.expectedActionNames()));

        boolean passed = probe.body().path("success").asBoolean(false)
            && count == expectations.expectedActionNames().size()
            && loadedActionNames.equals(expectations.expectedActionNames());

        addCheck(
            checks,
            "runtime_actions_match_expected",
            passed ? "PASSED" : "FAILED",
            passed
                ? "Runtime actions overview matches the published action catalog."
                : "Runtime actions overview does not match the published action catalog.",
            details
        );
    }

    private void validateRuntimeIndexing(ArrayNode checks,
                                         JsonProbeResult probe,
                                         VerificationExpectations expectations) {
        if (!probe.success() || probe.body() == null) {
            addDependentCheckSkipped(checks, "runtime_entity_types_match_expected", "Runtime entity validation skipped because the indexing overview probe failed.");
            return;
        }

        Set<String> entityTypes = textSet(probe.body().path("entityTypes"));
        Set<String> countsByEntityType = fieldNames(probe.body().path("countsByEntityType"));

        ObjectNode details = objectMapper.createObjectNode();
        details.set("entityTypes", toArrayNode(entityTypes));
        details.set("countsByEntityTypeKeys", toArrayNode(countsByEntityType));
        details.set("expectedEntityTypes", toArrayNode(expectations.expectedEntityTypes()));
        details.put("supportsVectorScan", probe.body().path("supportsVectorScan").asBoolean(false));

        boolean passed = probe.body().path("success").asBoolean(false)
            && entityTypes.equals(expectations.expectedEntityTypes())
            && countsByEntityType.containsAll(expectations.expectedEntityTypes());

        addCheck(
            checks,
            "runtime_entity_types_match_expected",
            passed ? "PASSED" : "FAILED",
            passed
                ? "Runtime indexing overview matches the expected entity types."
                : "Runtime indexing overview does not match the expected entity types.",
            details
        );
    }

    private void validateConnectorOverview(ArrayNode checks,
                                           JsonProbeResult probe,
                                           VerificationExpectations expectations) {
        if (!probe.success() || probe.body() == null) {
            addDependentCheckSkipped(checks, "connector_config_matches_expected", "Connector config validation skipped because the admin overview probe failed.");
            return;
        }

        JsonNode runtimeProxy = probe.body().path("runtimeProxy");
        JsonNode connector = probe.body().path("connector");

        String routingConfigLocation = probe.body().path("routingConfigLocation").asText("");
        boolean runtimeProxyEnabled = runtimeProxy.path("enabled").asBoolean(false);
        String runtimeProxyBaseUrl = runtimeProxy.path("baseUrl").asText("");
        boolean apiKeyConfigured = connector.path("inboundAuth").path("apiKey").path("valueConfigured").asBoolean(false);
        int actionsCount = probe.body().path("actionsCount").asInt(-1);

        ObjectNode details = objectMapper.createObjectNode();
        details.put("expectedRoutingConfigLocation", expectations.artifacts().routingArtifactUrl());
        details.put("actualRoutingConfigLocation", routingConfigLocation);
        details.put("runtimeProxyEnabled", runtimeProxyEnabled);
        details.put("runtimeProxyBaseUrl", runtimeProxyBaseUrl);
        details.put("connectorApiKeyConfigured", apiKeyConfigured);
        details.put("actionsCount", actionsCount);
        details.put("expectedActionsCount", expectations.expectedRoutingActions().size());

        boolean passed = probe.body().path("success").asBoolean(false)
            && expectations.artifacts().routingArtifactUrl().equals(routingConfigLocation)
            && runtimeProxyEnabled
            && hasText(runtimeProxyBaseUrl)
            && actionsCount == expectations.expectedRoutingActions().size()
            && apiKeyConfigured == !expectations.routingConfig().path("connector").path("inbound-auth").path("allow-unauthenticated").asBoolean(false);

        addCheck(
            checks,
            "connector_config_matches_expected",
            passed ? "PASSED" : "FAILED",
            passed
                ? "Connector overview matches the published routing and runtime proxy configuration."
                : "Connector overview does not match the published routing and runtime proxy configuration.",
            details
        );
    }

    private void validateConnectorAuthz(ArrayNode checks,
                                        JsonProbeResult probe,
                                        VerificationExpectations expectations) {
        if (!probe.success() || probe.body() == null) {
            addDependentCheckSkipped(checks, "connector_authz_configuration_matches_expected", "Connector authz validation skipped because the admin overview probe failed.");
            return;
        }

        JsonNode authz = probe.body().path("authz");
        boolean enabled = authz.path("enabled").asBoolean(false);
        String path = authz.path("path").asText("");
        String upstreamBaseUrl = authz.path("upstream").path("baseUrl").asText("");

        ObjectNode details = objectMapper.createObjectNode();
        details.put("expectedEnabled", expectations.expectedAuthzEnabled());
        details.put("actualEnabled", enabled);
        details.put("path", path);
        details.put("upstreamBaseUrl", upstreamBaseUrl);

        boolean passed = enabled == expectations.expectedAuthzEnabled()
            && (!enabled || (hasText(path) && hasText(upstreamBaseUrl)));

        addCheck(
            checks,
            "connector_authz_configuration_matches_expected",
            passed ? "PASSED" : "FAILED",
            passed
                ? "Connector authz configuration matches the published routing configuration."
                : "Connector authz configuration does not match the published routing configuration.",
            details
        );
    }

    private void validateConnectorActions(ArrayNode checks,
                                          JsonProbeResult probe,
                                          VerificationExpectations expectations) {
        if (!probe.success() || probe.body() == null) {
            addDependentCheckSkipped(checks, "connector_actions_match_expected", "Connector action validation skipped because the actions overview probe failed.");
            return;
        }

        Set<String> routedActionIds = textSet(probe.body().path("actions"), "actionId");
        int count = probe.body().path("count").asInt(-1);

        ObjectNode details = objectMapper.createObjectNode();
        details.put("count", count);
        details.put("expectedCount", expectations.expectedRoutingActions().size());
        details.set("routedActionIds", toArrayNode(routedActionIds));
        details.set("expectedRoutingActionIds", toArrayNode(expectations.expectedRoutingActions()));

        boolean passed = probe.body().path("success").asBoolean(false)
            && count == expectations.expectedRoutingActions().size()
            && routedActionIds.equals(expectations.expectedRoutingActions());

        addCheck(
            checks,
            "connector_actions_match_expected",
            passed ? "PASSED" : "FAILED",
            passed
                ? "Connector actions overview matches the published routing configuration."
                : "Connector actions overview does not match the published routing configuration.",
            details
        );
    }

    private JsonProbeResult probeJson(String baseUrl,
                                      String path,
                                      Map<String, String> headers) {
        if (!hasText(baseUrl)) {
            return JsonProbeResult.failure("Base URL is missing.");
        }

        URI uri;
        try {
            uri = buildProbeUri(baseUrl, path);
        } catch (IllegalArgumentException ex) {
            return JsonProbeResult.failure("Probe URI is invalid: " + ex.getMessage());
        }

        long startedAt = System.nanoTime();
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(uri)
            .timeout(verificationProperties.timeout())
            .header("Accept", "application/json")
            .GET();
        headers.forEach(requestBuilder::header);

        try {
            HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
            JsonNode body = null;
            String parseError = null;
            String rawBody = response.body();
            if (hasText(rawBody)) {
                try {
                    body = objectMapper.readTree(rawBody);
                } catch (Exception ex) {
                    parseError = ex.getMessage();
                }
            }
            return new JsonProbeResult(uri, response.statusCode(), durationMs, body, rawBody, parseError, null, response.headers());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return JsonProbeResult.failure(uri, "Probe was interrupted.");
        } catch (Exception ex) {
            return JsonProbeResult.failure(uri, ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
    }

    private void addProbeCheck(ArrayNode checks,
                               String name,
                               String label,
                               JsonProbeResult probe) {
        ObjectNode details = objectMapper.createObjectNode();
        if (probe.uri() != null) {
            details.put("url", probe.uri().toString());
        }
        if (probe.httpStatus() != null) {
            details.put("httpStatus", probe.httpStatus());
        }
        if (probe.durationMs() != null) {
            details.put("durationMs", probe.durationMs());
        }
        if (probe.parseError() != null) {
            details.put("parseError", probe.parseError());
        }
        if (probe.errorMessage() != null) {
            details.put("error", probe.errorMessage());
        }
        if (probe.rawBody() != null && !probe.rawBody().isBlank()) {
            details.put("bodySnippet", abbreviate(probe.rawBody()));
        }
        addCheck(
            checks,
            name,
            probe.success() ? "PASSED" : "FAILED",
            probe.success()
                ? label + " responded with JSON successfully."
                : label + " probe failed.",
            details
        );
    }

    private void addHttpProbe(ArrayNode checks,
                              String name,
                              String baseUrl,
                              String path,
                              String label) {
        if (!hasText(baseUrl)) {
            addCheck(checks, name, "FAILED", label + " base URL is missing; cannot run live probe.", null);
            return;
        }

        URI uri;
        try {
            uri = buildProbeUri(baseUrl, path);
        } catch (IllegalArgumentException ex) {
            ObjectNode details = objectMapper.createObjectNode();
            details.put("baseUrl", baseUrl);
            details.put("path", path);
            addCheck(checks, name, "FAILED", label + " health URI is invalid: " + ex.getMessage(), details);
            return;
        }

        long startedAt = System.nanoTime();
        HttpRequest request = HttpRequest.newBuilder(uri)
            .timeout(verificationProperties.timeout())
            .GET()
            .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
            ObjectNode details = objectMapper.createObjectNode();
            details.put("url", uri.toString());
            details.put("httpStatus", response.statusCode());
            details.put("durationMs", durationMs);

            boolean healthy = isHealthyResponse(response, details);
            addCheck(
                checks,
                name,
                healthy ? "PASSED" : "FAILED",
                healthy
                    ? label + " health endpoint responded successfully."
                    : label + " health endpoint did not report a healthy state.",
                details
            );
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            ObjectNode details = objectMapper.createObjectNode();
            details.put("url", uri.toString());
            addCheck(checks, name, "FAILED", label + " health probe was interrupted.", details);
        } catch (Exception ex) {
            ObjectNode details = objectMapper.createObjectNode();
            details.put("url", uri.toString());
            details.put("error", ex.getClass().getSimpleName());
            addCheck(checks, name, "FAILED", label + " health probe failed: " + ex.getMessage(), details);
        }
    }

    private boolean isHealthyResponse(HttpResponse<String> response, ObjectNode details) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return false;
        }

        String body = response.body();
        if (!hasText(body)) {
            return true;
        }

        try {
            JsonNode payload = objectMapper.readTree(body);
            JsonNode status = payload.path("status");
            if (status.isTextual()) {
                details.put("bodyStatus", status.asText());
                return "UP".equalsIgnoreCase(status.asText());
            }
        } catch (Exception ex) {
            details.put("bodySnippet", abbreviate(body));
        }

        return true;
    }

    private URI buildProbeUri(String baseUrl, String path) {
        String normalizedBaseUrl = baseUrl.endsWith("/")
            ? baseUrl.substring(0, baseUrl.length() - 1)
            : baseUrl;
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        return URI.create(normalizedBaseUrl + normalizedPath);
    }

    private JsonNode readJson(String value) {
        try {
            return value == null || value.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse deployment version config JSON.", ex);
        }
    }

    private Set<String> textSet(JsonNode node) {
        Set<String> values = new LinkedHashSet<>();
        if (node == null || node.isMissingNode()) {
            return values;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                String value = item.asText("").trim();
                if (hasText(value)) {
                    values.add(value);
                }
            }
        }
        return values;
    }

    private Set<String> textSet(JsonNode node, String field) {
        Set<String> values = new LinkedHashSet<>();
        if (node == null || node.isMissingNode() || !node.isArray()) {
            return values;
        }
        for (JsonNode item : node) {
            String value = item.path(field).asText("").trim();
            if (hasText(value)) {
                values.add(value);
            }
        }
        return values;
    }

    private Set<String> fieldNames(JsonNode node) {
        Set<String> values = new LinkedHashSet<>();
        if (node == null || !node.isObject()) {
            return values;
        }
        node.fieldNames().forEachRemaining(values::add);
        return values;
    }

    private ArrayNode toArrayNode(Set<String> values) {
        ArrayNode arrayNode = objectMapper.createArrayNode();
        values.forEach(arrayNode::add);
        return arrayNode;
    }

    private void addBooleanCheck(ArrayNode checks, String name, boolean passed, String message) {
        addCheck(checks, name, passed ? "PASSED" : "FAILED", message, null);
    }

    private void addSkippedCheck(ArrayNode checks, String name, String message) {
        addCheck(checks, name, "SKIPPED", message, null);
    }

    private void addDependentCheckSkipped(ArrayNode checks, String name, String message) {
        addCheck(checks, name, "SKIPPED", message, null);
    }

    private void addCheck(ArrayNode checks,
                          String name,
                          String status,
                          String message,
                          ObjectNode details) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("name", name);
        node.put("status", status);
        node.put("message", message);
        if (details != null && !details.isEmpty()) {
            node.set("details", details);
        }
        checks.add(node);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String abbreviate(String value) {
        if (!hasText(value)) {
            return "";
        }
        return value.length() <= 240 ? value : value.substring(0, 237) + "...";
    }

    private String generateId(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private record JsonProbeResult(
        URI uri,
        Integer httpStatus,
        Long durationMs,
        JsonNode body,
        String rawBody,
        String parseError,
        String errorMessage,
        HttpHeaders headers
    ) {
        private static JsonProbeResult failure(String message) {
            return new JsonProbeResult(null, null, null, null, null, null, message, HttpHeaders.of(Map.of(), (a, b) -> true));
        }

        private static JsonProbeResult failure(URI uri, String message) {
            return new JsonProbeResult(uri, null, null, null, null, null, message, HttpHeaders.of(Map.of(), (a, b) -> true));
        }

        private boolean success() {
            return errorMessage == null
                && parseError == null
                && httpStatus != null
                && httpStatus >= 200
                && httpStatus < 300
                && body != null
                && !body.isMissingNode();
        }
    }

    private record VerificationExpectations(
        DeploymentArtifactBundleSummary artifacts,
        JsonNode actionsConfig,
        JsonNode entityConfig,
        JsonNode routingConfig,
        Set<String> expectedActionNames,
        Set<String> expectedEntityTypes,
        Set<String> expectedRoutingActions,
        boolean expectedAuthzEnabled
    ) {
    }
}
