package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentDraftEntity;
import com.ai.fabric.platform.backend.deployment.model.DraftValidationIssue;
import com.ai.fabric.platform.backend.deployment.model.DraftValidationResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class DeploymentDraftValidationService {

    private final ObjectMapper objectMapper;

    public DeploymentDraftValidationService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public DraftValidationResponse validate(DeploymentDraftEntity draft) {
        try {
            JsonNode actionsNode = objectMapper.readTree(draft.getActionsConfigJson());
            JsonNode entityNode = objectMapper.readTree(draft.getEntityConfigJson());
            JsonNode routingNode = objectMapper.readTree(draft.getRoutingConfigJson());
            JsonNode providerNode = objectMapper.readTree(draft.getProviderConfigJson());
            JsonNode securityNode = objectMapper.readTree(draft.getSecurityConfigJson());

            List<DraftValidationIssue> issues = new ArrayList<>();
            Set<String> actionNames = validateActions(actionsNode, issues);
            validateEntities(entityNode, issues);
            validateRouting(routingNode, actionNames, issues);
            validateProviders(providerNode, issues);
            validateSecurity(securityNode, issues);

            int errorCount = countBySeverity(issues, "ERROR");
            int warningCount = countBySeverity(issues, "WARNING");

            return new DraftValidationResponse(
                draft.getId(),
                draft.getDeploymentId(),
                errorCount == 0,
                errorCount,
                warningCount,
                Instant.now(),
                List.copyOf(issues)
            );
        } catch (Exception ex) {
            List<DraftValidationIssue> issues = List.of(
                new DraftValidationIssue(
                    "ERROR",
                    "draft",
                    "DRAFT_PARSE_FAILED",
                    "$",
                    "Failed to parse draft configuration: " + ex.getMessage()
                )
            );
            return new DraftValidationResponse(
                draft.getId(),
                draft.getDeploymentId(),
                false,
                1,
                0,
                Instant.now(),
                issues
            );
        }
    }

    private Set<String> validateActions(JsonNode actionsNode, List<DraftValidationIssue> issues) {
        Set<String> actionNames = new HashSet<>();
        JsonNode actions = actionsNode.path("actions");
        if (!actions.isArray()) {
            issues.add(error("actions", "ACTIONS_ARRAY_REQUIRED", "$.actions", "actions must be an array."));
            return actionNames;
        }

        if (actions.isEmpty()) {
            issues.add(warning("actions", "NO_ACTIONS_CONFIGURED", "$.actions", "No actions are currently configured."));
            return actionNames;
        }

        for (int index = 0; index < actions.size(); index++) {
            JsonNode action = actions.get(index);
            String basePath = "$.actions[" + index + "]";
            if (!action.isObject()) {
                issues.add(error("actions", "ACTION_OBJECT_REQUIRED", basePath, "Each action entry must be an object."));
                continue;
            }

            String name = action.path("name").asText("").trim();
            if (name.isEmpty()) {
                issues.add(error("actions", "ACTION_NAME_REQUIRED", basePath + ".name", "Action name is required."));
            } else if (!actionNames.add(name)) {
                issues.add(error("actions", "DUPLICATE_ACTION_NAME", basePath + ".name", "Duplicate action name: " + name));
            }

            String description = action.path("description").asText("").trim();
            if (description.isEmpty()) {
                issues.add(warning("actions", "ACTION_DESCRIPTION_RECOMMENDED", basePath + ".description", "Action description should be provided."));
            }

            JsonNode requiredParameters = action.path("requiredParameters");
            if (!requiredParameters.isMissingNode() && !requiredParameters.isArray()) {
                issues.add(error("actions", "REQUIRED_PARAMETERS_ARRAY", basePath + ".requiredParameters", "requiredParameters must be an array of strings."));
            }
        }

        return actionNames;
    }

    private void validateEntities(JsonNode entityNode, List<DraftValidationIssue> issues) {
        JsonNode aiConfig = entityNode.path("ai-config");
        if (!aiConfig.isObject()) {
            issues.add(error("knowledge", "AI_CONFIG_REQUIRED", "$.ai-config", "ai-config object is required."));
        } else {
            int vectorDimensions = aiConfig.path("vector-dimensions").asInt(-1);
            if (vectorDimensions <= 0) {
                issues.add(error("knowledge", "VECTOR_DIMENSIONS_INVALID", "$.ai-config.vector-dimensions", "vector-dimensions must be a positive integer."));
            }
        }

        JsonNode entities = entityNode.path("ai-entities");
        if (!entities.isObject()) {
            issues.add(error("knowledge", "AI_ENTITIES_REQUIRED", "$.ai-entities", "ai-entities must be an object keyed by entity type."));
            return;
        }

        if (entities.isEmpty()) {
            issues.add(warning("knowledge", "NO_ENTITY_TYPES_CONFIGURED", "$.ai-entities", "No entity types are configured yet."));
            return;
        }

        Iterator<String> fieldNames = entities.fieldNames();
        while (fieldNames.hasNext()) {
            String entityType = fieldNames.next();
            JsonNode entity = entities.path(entityType);
            if (!entity.isObject()) {
                issues.add(error("knowledge", "ENTITY_OBJECT_REQUIRED", "$.ai-entities." + entityType, "Each entity type must map to an object."));
                continue;
            }

            JsonNode fields = entity.path("fields");
            if (!fields.isMissingNode() && !fields.isArray()) {
                issues.add(error("knowledge", "ENTITY_FIELDS_ARRAY", "$.ai-entities." + entityType + ".fields", "fields must be an array when provided."));
            }
        }
    }

    private void validateRouting(JsonNode routingNode, Set<String> actionNames, List<DraftValidationIssue> issues) {
        JsonNode connector = routingNode.path("connector");
        if (!connector.isObject()) {
            issues.add(error("routing", "CONNECTOR_OBJECT_REQUIRED", "$.connector", "connector object is required."));
        } else {
            validateConnector(connector, issues);
        }

        JsonNode authz = routingNode.path("authz");
        if (authz.isObject()) {
            validateAuthz(authz, connector, issues);
        }

        JsonNode actionRoutes = routingNode.path("actions");
        if (!actionRoutes.isObject()) {
            issues.add(error("routing", "ROUTES_OBJECT_REQUIRED", "$.actions", "actions routing object is required."));
            return;
        }

        if (actionRoutes.isEmpty()) {
            issues.add(warning("routing", "NO_ROUTES_CONFIGURED", "$.actions", "No action routes are configured yet."));
        }

        Iterator<String> routeNames = actionRoutes.fieldNames();
        while (routeNames.hasNext()) {
            String routeName = routeNames.next();
            if (!actionNames.contains(routeName)) {
                issues.add(error("routing", "ROUTE_WITHOUT_ACTION", "$.actions." + routeName, "Route exists for undefined action: " + routeName));
                continue;
            }

            JsonNode route = actionRoutes.path(routeName);
            if (!route.isObject()) {
                issues.add(error("routing", "ROUTE_OBJECT_REQUIRED", "$.actions." + routeName, "Each action route must be an object."));
                continue;
            }

            validateRoute(routeName, route, connector, issues);
        }

        for (String actionName : actionNames) {
            if (!actionRoutes.has(actionName)) {
                issues.add(warning("routing", "ACTION_WITHOUT_ROUTE", "$.actions", "No route is configured yet for action: " + actionName));
            }
        }
    }

    private void validateConnector(JsonNode connector, List<DraftValidationIssue> issues) {
        JsonNode inboundAuth = connector.path("inbound-auth");
        if (!inboundAuth.isObject()) {
            issues.add(error("routing", "INBOUND_AUTH_REQUIRED", "$.connector.inbound-auth", "connector.inbound-auth object is required."));
            return;
        }

        boolean allowUnauthenticated = inboundAuth.path("allow-unauthenticated").asBoolean(false);
        JsonNode apiKey = inboundAuth.path("api-key");
        boolean apiKeyEnabled = apiKey.path("enabled").asBoolean(false);
        if (!allowUnauthenticated && !apiKeyEnabled) {
            issues.add(error(
                "routing",
                "INBOUND_AUTH_INCOMPLETE",
                "$.connector.inbound-auth",
                "Inbound auth is not configured. Enable connector.inbound-auth.api-key.enabled or explicitly allow unauthenticated access."
            ));
        }

        if (apiKeyEnabled) {
            if (apiKey.path("header").asText("").trim().isEmpty()) {
                issues.add(error("routing", "CONNECTOR_API_KEY_HEADER_REQUIRED", "$.connector.inbound-auth.api-key.header", "connector inbound API key header is required when api-key.enabled=true."));
            }
            if (apiKey.path("value").asText("").trim().isEmpty()) {
                issues.add(error("routing", "CONNECTOR_API_KEY_VALUE_REQUIRED", "$.connector.inbound-auth.api-key.value", "connector inbound API key value is required when api-key.enabled=true."));
            }
        }

        String baseUrl = connector.path("upstream").path("base-url").asText("").trim();
        if (!baseUrl.isEmpty() && !isAbsoluteHttpUrl(baseUrl)) {
            issues.add(error("routing", "CONNECTOR_UPSTREAM_URL_INVALID", "$.connector.upstream.base-url", "connector.upstream.base-url must be a valid absolute http(s) URL."));
        }
    }

    private void validateAuthz(JsonNode authz, JsonNode connector, List<DraftValidationIssue> issues) {
        if (!authz.path("enabled").asBoolean(false)) {
            return;
        }

        String path = authz.path("path").asText("").trim();
        if (path.isEmpty()) {
            issues.add(error("routing", "AUTHZ_PATH_REQUIRED", "$.authz.path", "authz.path is required when authz.enabled=true."));
        } else if (!isRelativePath(path)) {
            issues.add(error("routing", "AUTHZ_PATH_INVALID", "$.authz.path", "authz.path must be a relative path starting with '/'."));
        }

        String authzBaseUrl = authz.path("upstream").path("base-url").asText("").trim();
        String connectorBaseUrl = connector.path("upstream").path("base-url").asText("").trim();
        String effectiveBaseUrl = !authzBaseUrl.isEmpty() ? authzBaseUrl : connectorBaseUrl;
        if (effectiveBaseUrl.isEmpty()) {
            issues.add(error("routing", "AUTHZ_BASE_URL_REQUIRED", "$.authz.upstream.base-url", "authz.enabled=true requires authz.upstream.base-url or connector.upstream.base-url."));
        } else if (!isAbsoluteHttpUrl(effectiveBaseUrl)) {
            issues.add(error("routing", "AUTHZ_BASE_URL_INVALID", "$.authz.upstream.base-url", "Authz upstream base URL must be a valid absolute http(s) URL."));
        }
    }

    private void validateRoute(String routeName,
                               JsonNode route,
                               JsonNode connector,
                               List<DraftValidationIssue> issues) {
        String basePath = "$.actions." + routeName;
        String url = route.path("url").asText("").trim();
        String path = route.path("path").asText("").trim();

        if (url.isEmpty() && path.isEmpty()) {
            issues.add(error("routing", "ROUTE_TARGET_REQUIRED", basePath, "Each action route must define either url or path."));
        } else if (!url.isEmpty()) {
            if (!isAbsoluteHttpUrl(url)) {
                issues.add(error("routing", "ROUTE_URL_INVALID", basePath + ".url", "Action route url must be a valid absolute http(s) URL."));
            }
        } else {
            String connectorBaseUrl = connector.path("upstream").path("base-url").asText("").trim();
            if (connectorBaseUrl.isEmpty()) {
                issues.add(error("routing", "ROUTE_PATH_REQUIRES_CONNECTOR_UPSTREAM", basePath + ".path", "Path-based routes require connector.upstream.base-url to be configured."));
            }
            if (!isRelativePath(path)) {
                issues.add(error("routing", "ROUTE_PATH_INVALID", basePath + ".path", "Action route path must be a relative path starting with '/'."));
            }
        }

        String method = route.path("method").asText("").trim();
        if (method.isEmpty()) {
            issues.add(error("routing", "ROUTE_METHOD_REQUIRED", basePath + ".method", "Action route method is required."));
        } else if (!Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS").contains(method.toUpperCase(Locale.ROOT))) {
            issues.add(warning("routing", "ROUTE_METHOD_UNRECOGNIZED", basePath + ".method", "Action route method is not part of the current supported template set: " + method));
        }

        JsonNode successHttpStatus = route.path("response").path("success-http-status");
        if (!successHttpStatus.isMissingNode() && !successHttpStatus.isArray()) {
            issues.add(error("routing", "SUCCESS_STATUS_ARRAY_REQUIRED", basePath + ".response.success-http-status", "response.success-http-status must be an array of HTTP status codes."));
        } else if (successHttpStatus.isArray()) {
            for (int index = 0; index < successHttpStatus.size(); index++) {
                int status = successHttpStatus.path(index).asInt(-1);
                if (status < 100 || status > 599) {
                    issues.add(error("routing", "SUCCESS_STATUS_INVALID", basePath + ".response.success-http-status[" + index + "]", "response.success-http-status must contain valid HTTP status codes."));
                }
            }
        }
    }

    private void validateProviders(JsonNode providerNode, List<DraftValidationIssue> issues) {
        validateRequiredString(providerNode, "llmProvider", "providers", issues);
        validateRequiredString(providerNode, "embeddingProvider", "providers", issues);
        validateRequiredString(providerNode, "vectorStrategy", "providers", issues);
        validateRequiredString(providerNode, "runtimeProfile", "providers", issues);
        validateRequiredString(providerNode, "connectorProfile", "providers", issues);

        String llmProvider = providerNode.path("llmProvider").asText("").trim();
        if (!llmProvider.isEmpty() && !Set.of("openai", "anthropic").contains(llmProvider)) {
            issues.add(warning("providers", "UNRECOGNIZED_LLM_PROVIDER", "$.llmProvider", "llmProvider is not part of the current built-in template set: " + llmProvider));
        }

        String vectorStrategy = providerNode.path("vectorStrategy").asText("").trim();
        if (!vectorStrategy.isEmpty() && !Set.of("lucene", "qdrant").contains(vectorStrategy)) {
            issues.add(warning("providers", "UNRECOGNIZED_VECTOR_STRATEGY", "$.vectorStrategy", "vectorStrategy is not part of the current built-in template set: " + vectorStrategy));
        }
    }

    private void validateSecurity(JsonNode securityNode, List<DraftValidationIssue> issues) {
        validateRequiredString(securityNode, "authzMode", "security", issues);

        if (!securityNode.path("adminApiKeyEnabled").isBoolean()) {
            issues.add(error("security", "ADMIN_API_KEY_BOOLEAN_REQUIRED", "$.adminApiKeyEnabled", "adminApiKeyEnabled must be a boolean."));
        }
        if (!securityNode.path("connectorApiKeyEnabled").isBoolean()) {
            issues.add(error("security", "CONNECTOR_API_KEY_BOOLEAN_REQUIRED", "$.connectorApiKeyEnabled", "connectorApiKeyEnabled must be a boolean."));
        }

        String authzMode = securityNode.path("authzMode").asText("").trim();
        if ("REMOTE_HTTP".equals(authzMode) && securityNode.path("authzBaseUrl").asText("").trim().isEmpty()) {
            issues.add(warning("security", "REMOTE_AUTHZ_BASE_URL_RECOMMENDED", "$.authzBaseUrl", "REMOTE_HTTP is selected but authzBaseUrl is not configured in the draft."));
        }
    }

    private void validateRequiredString(JsonNode node, String key, String section, List<DraftValidationIssue> issues) {
        if (node.path(key).asText("").trim().isEmpty()) {
            issues.add(error(section, "REQUIRED_VALUE_MISSING", "$." + key, key + " is required."));
        }
    }

    private int countBySeverity(List<DraftValidationIssue> issues, String severity) {
        int count = 0;
        for (DraftValidationIssue issue : issues) {
            if (severity.equals(issue.severity())) {
                count++;
            }
        }
        return count;
    }

    private DraftValidationIssue error(String section, String code, String path, String message) {
        return new DraftValidationIssue("ERROR", section, code, path, message);
    }

    private DraftValidationIssue warning(String section, String code, String path, String message) {
        return new DraftValidationIssue("WARNING", section, code, path, message);
    }

    private boolean isRelativePath(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = value.trim();
        return normalized.startsWith("/") && !normalized.contains("://");
    }

    private boolean isAbsoluteHttpUrl(String value) {
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme();
            if (scheme == null || uri.getHost() == null) {
                return false;
            }
            String normalized = scheme.trim().toLowerCase(Locale.ROOT);
            return "http".equals(normalized) || "https".equals(normalized);
        } catch (Exception ex) {
            return false;
        }
    }
}
