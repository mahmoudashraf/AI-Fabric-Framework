package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentDraftEntity;
import com.ai.fabric.platform.backend.deployment.model.DraftValidationIssue;
import com.ai.fabric.platform.backend.deployment.model.DraftValidationResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
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
            }
        }

        for (String actionName : actionNames) {
            if (!actionRoutes.has(actionName)) {
                issues.add(warning("routing", "ACTION_WITHOUT_ROUTE", "$.actions", "No route is configured yet for action: " + actionName));
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
}
