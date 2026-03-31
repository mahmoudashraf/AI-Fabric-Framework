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
            JsonNode promptNode = objectMapper.readTree(draft.getPromptConfigJson());

            List<DraftValidationIssue> issues = new ArrayList<>();
            Set<String> actionNames = validateActions(actionsNode, issues);
            validateEntities(entityNode, issues);
            validateRouting(routingNode, actionNames, issues);
            validateProviders(providerNode, issues);
            validateSecurity(securityNode, issues);
            validatePrompts(promptNode, issues);

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
        if (!llmProvider.isEmpty()
            && !ManagedDeploymentProfileCatalog.SUPPORTED_LLM_PROVIDERS.contains(llmProvider.toLowerCase(Locale.ROOT))) {
            issues.add(error(
                "providers",
                "UNSUPPORTED_LLM_PROVIDER",
                "$.llmProvider",
                "llmProvider must be one of " + ManagedDeploymentProfileCatalog.SUPPORTED_LLM_PROVIDERS + "."
            ));
        }

        String embeddingProvider = providerNode.path("embeddingProvider").asText("").trim();
        if (!embeddingProvider.isEmpty()
            && !ManagedDeploymentProfileCatalog.SUPPORTED_EMBEDDING_PROVIDERS.contains(embeddingProvider.toLowerCase(Locale.ROOT))) {
            issues.add(error(
                "providers",
                "UNSUPPORTED_EMBEDDING_PROVIDER",
                "$.embeddingProvider",
                "embeddingProvider must be one of " + ManagedDeploymentProfileCatalog.SUPPORTED_EMBEDDING_PROVIDERS + "."
            ));
        }

        String vectorStrategy = providerNode.path("vectorStrategy").asText("").trim();
        if (!vectorStrategy.isEmpty()
            && !ManagedDeploymentProfileCatalog.SUPPORTED_VECTOR_STRATEGIES.contains(vectorStrategy.toLowerCase(Locale.ROOT))) {
            issues.add(error(
                "providers",
                "UNSUPPORTED_VECTOR_STRATEGY",
                "$.vectorStrategy",
                "vectorStrategy must be one of " + ManagedDeploymentProfileCatalog.SUPPORTED_VECTOR_STRATEGIES + "."
            ));
        }

        String vectorProvisioningMode = providerNode.path("vectorProvisioningMode").asText("").trim();
        if (!vectorProvisioningMode.isEmpty()
            && !ManagedDeploymentProfileCatalog.SUPPORTED_VECTOR_PROVISIONING_MODES.contains(
                vectorProvisioningMode.toUpperCase(Locale.ROOT)
            )) {
            issues.add(error(
                "providers",
                "UNSUPPORTED_VECTOR_PROVISIONING_MODE",
                "$.vectorProvisioningMode",
                "vectorProvisioningMode must be one of " + ManagedDeploymentProfileCatalog.SUPPORTED_VECTOR_PROVISIONING_MODES + "."
            ));
        }
        String effectiveVectorProvisioningMode = ManagedDeploymentProfileCatalog.resolveVectorProvisioningMode(providerNode);
        if (!vectorStrategy.isEmpty()
            && !ManagedDeploymentProfileCatalog.supportsVectorProvisioningMode(vectorStrategy, effectiveVectorProvisioningMode)) {
            issues.add(error(
                "providers",
                "VECTOR_PROVISIONING_MODE_INVALID",
                "$.vectorProvisioningMode",
                ManagedDeploymentProfileCatalog.vectorProvisioningModeGuidance(vectorStrategy)
            ));
        }

        String runtimeProfile = providerNode.path("runtimeProfile").asText("").trim();
        if (!runtimeProfile.isEmpty()
            && !ManagedDeploymentProfileCatalog.SUPPORTED_RUNTIME_PROFILES.contains(runtimeProfile.toLowerCase(Locale.ROOT))) {
            issues.add(error(
                "providers",
                "UNSUPPORTED_RUNTIME_PROFILE",
                "$.runtimeProfile",
                "runtimeProfile must be one of " + ManagedDeploymentProfileCatalog.SUPPORTED_RUNTIME_PROFILES + "."
            ));
        }

        String connectorProfile = providerNode.path("connectorProfile").asText("").trim();
        if (!connectorProfile.isEmpty()
            && !ManagedDeploymentProfileCatalog.SUPPORTED_CONNECTOR_PROFILES.contains(connectorProfile.toLowerCase(Locale.ROOT))) {
            issues.add(error(
                "providers",
                "UNSUPPORTED_CONNECTOR_PROFILE",
                "$.connectorProfile",
                "connectorProfile must be one of " + ManagedDeploymentProfileCatalog.SUPPORTED_CONNECTOR_PROFILES + "."
            ));
        }

        validateAzureProviders(providerNode, issues);
        validateProviderBaseUrls(providerNode, issues);
        validatePurposeSpecificLlmProviders(providerNode, issues);
        validateProviderTuning(providerNode, issues);
        validateOpenAiProvider(providerNode, issues);
        validateAnthropicProvider(providerNode, issues);
        validateOnnxProvider(providerNode, issues);
        validateRestEmbeddingProvider(providerNode, issues);
        validateQdrantVectorProvider(providerNode, issues);
        validatePineconeVectorProvider(providerNode, issues);
        validateWeaviateVectorProvider(providerNode, issues);
        validateMilvusVectorProvider(providerNode, issues);
    }

    private void validateAzureProviders(JsonNode providerNode, List<DraftValidationIssue> issues) {
        String embeddingProvider = ManagedDeploymentProfileCatalog.resolveEmbeddingProvider(providerNode);
        boolean azureLlm = ManagedDeploymentProfileCatalog.usesLlmProvider(providerNode, ManagedDeploymentProfileCatalog.LLM_PROVIDER_AZURE);
        boolean azureEmbedding = ManagedDeploymentProfileCatalog.EMBEDDING_PROVIDER_AZURE.equals(embeddingProvider);

        if ((azureLlm || azureEmbedding) && ManagedDeploymentProfileCatalog.azureEndpoint(providerNode).isBlank()) {
            issues.add(error(
                "providers",
                "AZURE_ENDPOINT_REQUIRED",
                "$.azureEndpoint",
                "azureEndpoint is required when Azure is selected for LLM or embeddings."
            ));
        }
        if (azureLlm && ManagedDeploymentProfileCatalog.azureDeploymentName(providerNode).isBlank()) {
            issues.add(error(
                "providers",
                "AZURE_DEPLOYMENT_NAME_REQUIRED",
                "$.azureDeploymentName",
                "azureDeploymentName is required when llmProvider=azure."
            ));
        }
        if (azureEmbedding && ManagedDeploymentProfileCatalog.azureEmbeddingDeploymentName(providerNode).isBlank()) {
            issues.add(error(
                "providers",
                "AZURE_EMBEDDING_DEPLOYMENT_NAME_REQUIRED",
                "$.azureEmbeddingDeploymentName",
                "azureEmbeddingDeploymentName is required when embeddingProvider=azure."
            ));
        }
        if (!ManagedDeploymentProfileCatalog.azureEndpoint(providerNode).isBlank()
            && !isAbsoluteHttpUrl(ManagedDeploymentProfileCatalog.azureEndpoint(providerNode))) {
            issues.add(error(
                "providers",
                "AZURE_ENDPOINT_INVALID",
                "$.azureEndpoint",
                "azureEndpoint must be a valid absolute http(s) URL."
            ));
        }
    }

    private void validateProviderBaseUrls(JsonNode providerNode, List<DraftValidationIssue> issues) {
        validateOptionalAbsoluteHttpUrl(providerNode, "openaiBaseUrl", "OPENAI_BASE_URL_INVALID", issues);
        validateOptionalAbsoluteHttpUrl(providerNode, "anthropicBaseUrl", "ANTHROPIC_BASE_URL_INVALID", issues);
        validateOptionalAbsoluteHttpUrl(providerNode, "cohereBaseUrl", "COHERE_BASE_URL_INVALID", issues);
        validateOptionalAbsoluteHttpUrl(providerNode, "geminiBaseUrl", "GEMINI_BASE_URL_INVALID", issues);
    }

    private void validatePurposeSpecificLlmProviders(JsonNode providerNode, List<DraftValidationIssue> issues) {
        validateOptionalProviderSelection(providerNode, "orchestrationLlmProvider", "ORCHESTRATION_PROVIDER_INVALID", issues);
        validateOptionalProviderSelection(providerNode, "generationLlmProvider", "GENERATION_PROVIDER_INVALID", issues);
    }

    private void validateProviderTuning(JsonNode providerNode, List<DraftValidationIssue> issues) {
        validateBooleanIfPresent(providerNode, "enableFallback", "ENABLE_FALLBACK_BOOLEAN_REQUIRED", issues);

        validateBooleanIfPresent(providerNode, "openaiValidateOnStartup", "OPENAI_VALIDATE_ON_STARTUP_BOOLEAN_REQUIRED", issues);
        validatePositiveInteger(providerNode, "openaiMaxTokens", "providers", issues);
        validatePositiveInteger(providerNode, "openaiTimeout", "providers", issues);
        validatePositiveInteger(providerNode, "openaiPriority", "providers", issues);
        validateTemperature(providerNode, "openaiTemperature", "OPENAI_TEMPERATURE_INVALID", issues);

        validatePositiveInteger(providerNode, "anthropicMaxTokens", "providers", issues);
        validatePositiveInteger(providerNode, "anthropicTimeout", "providers", issues);
        validatePositiveInteger(providerNode, "anthropicPriority", "providers", issues);
        validateTemperature(providerNode, "anthropicTemperature", "ANTHROPIC_TEMPERATURE_INVALID", issues);

        validateBooleanIfPresent(providerNode, "azureValidateOnStartup", "AZURE_VALIDATE_ON_STARTUP_BOOLEAN_REQUIRED", issues);
        validatePositiveInteger(providerNode, "azureTimeout", "providers", issues);
        validatePositiveInteger(providerNode, "azurePriority", "providers", issues);

        validateBooleanIfPresent(providerNode, "cohereValidateOnStartup", "COHERE_VALIDATE_ON_STARTUP_BOOLEAN_REQUIRED", issues);
        validatePositiveInteger(providerNode, "cohereMaxTokens", "providers", issues);
        validatePositiveInteger(providerNode, "cohereTimeout", "providers", issues);
        validatePositiveInteger(providerNode, "coherePriority", "providers", issues);
        validateTemperature(providerNode, "cohereTemperature", "COHERE_TEMPERATURE_INVALID", issues);

        validateBooleanIfPresent(providerNode, "geminiValidateOnStartup", "GEMINI_VALIDATE_ON_STARTUP_BOOLEAN_REQUIRED", issues);
        validatePositiveInteger(providerNode, "geminiMaxTokens", "providers", issues);
        validatePositiveInteger(providerNode, "geminiTimeout", "providers", issues);
        validatePositiveInteger(providerNode, "geminiPriority", "providers", issues);
        validateTemperature(providerNode, "geminiTemperature", "GEMINI_TEMPERATURE_INVALID", issues);

        validateBooleanIfPresent(providerNode, "restEmbeddingValidateOnStartup", "REST_VALIDATE_ON_STARTUP_BOOLEAN_REQUIRED", issues);
        validatePositiveInteger(providerNode, "weaviateTimeout", "providers", issues);
        validatePositiveInteger(providerNode, "milvusTimeout", "providers", issues);

        validatePositiveInteger(providerNode, "orchestrationMaxTokens", "providers", issues);
        validatePositiveInteger(providerNode, "orchestrationTimeout", "providers", issues);
        validateTemperature(providerNode, "orchestrationTemperature", "ORCHESTRATION_TEMPERATURE_INVALID", issues);

        validatePositiveInteger(providerNode, "generationMaxTokens", "providers", issues);
        validatePositiveInteger(providerNode, "generationTimeout", "providers", issues);
        validateTemperature(providerNode, "generationTemperature", "GENERATION_TEMPERATURE_INVALID", issues);
    }

    private void validateOpenAiProvider(JsonNode providerNode, List<DraftValidationIssue> issues) {
        if (!ManagedDeploymentProfileCatalog.usesOpenAi(providerNode)) {
            return;
        }
        validatePositiveInteger(providerNode, "openaiEmbeddingDimensions", "providers", issues);
    }

    private void validateAnthropicProvider(JsonNode providerNode, List<DraftValidationIssue> issues) {
        if (!ManagedDeploymentProfileCatalog.usesAnthropic(providerNode)) {
            return;
        }
        if (ManagedDeploymentProfileCatalog.anthropicModel(providerNode).isBlank()) {
            issues.add(error(
                "providers",
                "ANTHROPIC_MODEL_REQUIRED",
                "$.anthropicModel",
                "anthropicModel must resolve to a non-empty value when llmProvider=anthropic."
            ));
        }
    }

    private void validateOnnxProvider(JsonNode providerNode, List<DraftValidationIssue> issues) {
        if (!ManagedDeploymentProfileCatalog.EMBEDDING_PROVIDER_ONNX.equals(
            ManagedDeploymentProfileCatalog.resolveEmbeddingProvider(providerNode)
        )) {
            return;
        }
        validatePositiveInteger(providerNode, "onnxMaxSequenceLength", "providers", issues);
        if (!providerNode.path("onnxUseGpu").isMissingNode()
            && !providerNode.path("onnxUseGpu").isBoolean()) {
            issues.add(error(
                "providers",
                "ONNX_USE_GPU_BOOLEAN_REQUIRED",
                "$.onnxUseGpu",
                "onnxUseGpu must be a boolean when provided."
            ));
        }
    }

    private void validateRestEmbeddingProvider(JsonNode providerNode, List<DraftValidationIssue> issues) {
        if (!ManagedDeploymentProfileCatalog.EMBEDDING_PROVIDER_REST.equals(
            ManagedDeploymentProfileCatalog.resolveEmbeddingProvider(providerNode)
        )) {
            return;
        }
        String baseUrl = ManagedDeploymentProfileCatalog.restEmbeddingBaseUrl(providerNode);
        if (baseUrl.isBlank()) {
            issues.add(error(
                "providers",
                "REST_EMBEDDING_BASE_URL_REQUIRED",
                "$.restEmbeddingBaseUrl",
                "restEmbeddingBaseUrl is required when embeddingProvider=rest."
            ));
        } else if (!isAbsoluteHttpUrl(baseUrl)) {
            issues.add(error(
                "providers",
                "REST_EMBEDDING_BASE_URL_INVALID",
                "$.restEmbeddingBaseUrl",
                "restEmbeddingBaseUrl must be a valid absolute http(s) URL."
            ));
        }
        validatePositiveInteger(providerNode, "restEmbeddingTimeoutMs", "providers", issues);
    }

    private void validatePineconeVectorProvider(JsonNode providerNode, List<DraftValidationIssue> issues) {
        if (!ManagedDeploymentProfileCatalog.VECTOR_STRATEGY_PINECONE.equals(
            ManagedDeploymentProfileCatalog.resolveVectorStrategy(providerNode)
        )) {
            return;
        }
        boolean managedIndexEnabled = ManagedDeploymentProfileCatalog.pineconeManagedIndexEnabled(providerNode);
        boolean platformManagedMode = ManagedDeploymentProfileCatalog.VECTOR_PROVISIONING_MODE_PLATFORM_MANAGED.equals(
            ManagedDeploymentProfileCatalog.resolveVectorProvisioningMode(providerNode)
        );
        String indexName = ManagedDeploymentProfileCatalog.pineconeIndexName(providerNode);
        String apiHost = ManagedDeploymentProfileCatalog.pineconeApiHost(providerNode);
        String environment = ManagedDeploymentProfileCatalog.pineconeEnvironment(providerNode);
        if (indexName.isBlank() && apiHost.isBlank()) {
            issues.add(error(
                "providers",
                "PINECONE_INDEX_OR_HOST_REQUIRED",
                "$.pineconeIndexName",
                "pineconeIndexName or pineconeApiHost is required when vectorStrategy=pinecone."
            ));
        }
        if (!managedIndexEnabled && apiHost.isBlank() && environment.isBlank()) {
            issues.add(error(
                "providers",
                "PINECONE_ENVIRONMENT_REQUIRED",
                "$.pineconeEnvironment",
                "pineconeEnvironment is required when pineconeApiHost is not provided."
            ));
        }
        if (!providerNode.path("pineconeManagedIndexEnabled").isMissingNode()
            && !providerNode.path("pineconeManagedIndexEnabled").isBoolean()) {
            issues.add(error(
                "providers",
                "PINECONE_MANAGED_INDEX_BOOLEAN_REQUIRED",
                "$.pineconeManagedIndexEnabled",
                "pineconeManagedIndexEnabled must be a boolean when provided."
            ));
        }
        if (!providerNode.path("pineconeDeletionProtectionEnabled").isMissingNode()
            && !providerNode.path("pineconeDeletionProtectionEnabled").isBoolean()) {
            issues.add(error(
                "providers",
                "PINECONE_DELETION_PROTECTION_BOOLEAN_REQUIRED",
                "$.pineconeDeletionProtectionEnabled",
                "pineconeDeletionProtectionEnabled must be a boolean when provided."
            ));
        }
        if ((managedIndexEnabled || platformManagedMode) && indexName.isBlank()) {
            issues.add(error(
                "providers",
                "PINECONE_MANAGED_INDEX_NAME_REQUIRED",
                "$.pineconeIndexName",
                "pineconeIndexName is required when Pinecone platform-managed provisioning is enabled."
            ));
        }
        if ((managedIndexEnabled || platformManagedMode) && ManagedDeploymentProfileCatalog.pineconeRegion(providerNode).isBlank()) {
            issues.add(error(
                "providers",
                "PINECONE_REGION_REQUIRED",
                "$.pineconeRegion",
                "pineconeRegion is required when Pinecone platform-managed provisioning is enabled."
            ));
        }
        String cloud = ManagedDeploymentProfileCatalog.pineconeCloud(providerNode);
        if (!cloud.isBlank() && !ManagedDeploymentProfileCatalog.SUPPORTED_PINECONE_CLOUDS.contains(cloud)) {
            issues.add(error(
                "providers",
                "PINECONE_CLOUD_INVALID",
                "$.pineconeCloud",
                "pineconeCloud must be one of " + ManagedDeploymentProfileCatalog.SUPPORTED_PINECONE_CLOUDS + "."
            ));
        }
        String metric = ManagedDeploymentProfileCatalog.pineconeMetric(providerNode);
        if (!metric.isBlank() && !ManagedDeploymentProfileCatalog.SUPPORTED_PINECONE_METRICS.contains(metric)) {
            issues.add(error(
                "providers",
                "PINECONE_METRIC_INVALID",
                "$.pineconeMetric",
                "pineconeMetric must be one of " + ManagedDeploymentProfileCatalog.SUPPORTED_PINECONE_METRICS + "."
            ));
        }
        validatePositiveInteger(providerNode, "pineconeDimensions", "providers", issues);
    }

    private void validateQdrantVectorProvider(JsonNode providerNode, List<DraftValidationIssue> issues) {
        if (!ManagedDeploymentProfileCatalog.VECTOR_STRATEGY_QDRANT.equals(
            ManagedDeploymentProfileCatalog.resolveVectorStrategy(providerNode)
        )) {
            return;
        }
        boolean platformManagedMode = ManagedDeploymentProfileCatalog.qdrantPlatformManaged(providerNode);
        if (platformManagedMode) {
            if (ManagedDeploymentProfileCatalog.qdrantCloudRegionId(providerNode).isBlank()) {
                issues.add(error(
                    "providers",
                    "QDRANT_CLOUD_REGION_REQUIRED",
                    "$.qdrantCloudRegionId",
                    "qdrantCloudRegionId is required when vectorProvisioningMode=PLATFORM_MANAGED for Qdrant."
                ));
            }
            String providerId = ManagedDeploymentProfileCatalog.qdrantCloudProviderId(providerNode);
            if (!ManagedDeploymentProfileCatalog.SUPPORTED_QDRANT_CLOUD_PROVIDERS.contains(providerId)) {
                issues.add(error(
                    "providers",
                    "QDRANT_CLOUD_PROVIDER_INVALID",
                    "$.qdrantCloudProviderId",
                    "qdrantCloudProviderId must be one of " + ManagedDeploymentProfileCatalog.SUPPORTED_QDRANT_CLOUD_PROVIDERS + "."
                ));
            }
        } else if (ManagedDeploymentProfileCatalog.qdrantHost(providerNode).isBlank()) {
            issues.add(error(
                "providers",
                "QDRANT_HOST_REQUIRED",
                "$.qdrantHost",
                "qdrantHost is required when vectorStrategy=qdrant and the deployment uses an external existing cluster."
            ));
        }
        validatePositiveInteger(providerNode, "qdrantPort", "providers", issues);
        validatePositiveInteger(providerNode, "qdrantGrpcPort", "providers", issues);
        validatePositiveInteger(providerNode, "qdrantTimeout", "providers", issues);
        if (!providerNode.path("qdrantPreferGrpc").isMissingNode()
            && !providerNode.path("qdrantPreferGrpc").isBoolean()) {
            issues.add(error(
                "providers",
                "QDRANT_PREFER_GRPC_BOOLEAN_REQUIRED",
                "$.qdrantPreferGrpc",
                "qdrantPreferGrpc must be a boolean when provided."
            ));
        }
        if (!providerNode.path("qdrantManagedCollectionsEnabled").isMissingNode()
            && !providerNode.path("qdrantManagedCollectionsEnabled").isBoolean()) {
            issues.add(error(
                "providers",
                "QDRANT_MANAGED_COLLECTIONS_BOOLEAN_REQUIRED",
                "$.qdrantManagedCollectionsEnabled",
                "qdrantManagedCollectionsEnabled must be a boolean when provided."
            ));
        }
    }

    private void validateWeaviateVectorProvider(JsonNode providerNode, List<DraftValidationIssue> issues) {
        if (!ManagedDeploymentProfileCatalog.VECTOR_STRATEGY_WEAVIATE.equals(
            ManagedDeploymentProfileCatalog.resolveVectorStrategy(providerNode)
        )) {
            return;
        }
        if (ManagedDeploymentProfileCatalog.weaviateHost(providerNode).isBlank()) {
            issues.add(error(
                "providers",
                "WEAVIATE_HOST_REQUIRED",
                "$.weaviateHost",
                "weaviateHost is required when vectorStrategy=weaviate."
            ));
        }
        validatePositiveInteger(providerNode, "weaviatePort", "providers", issues);
        if (!providerNode.path("weaviateConsistencyLevelStrong").isMissingNode()
            && !providerNode.path("weaviateConsistencyLevelStrong").isBoolean()) {
            issues.add(error(
                "providers",
                "WEAVIATE_CONSISTENCY_BOOLEAN_REQUIRED",
                "$.weaviateConsistencyLevelStrong",
                "weaviateConsistencyLevelStrong must be a boolean when provided."
            ));
        }
    }

    private void validateMilvusVectorProvider(JsonNode providerNode, List<DraftValidationIssue> issues) {
        if (!ManagedDeploymentProfileCatalog.VECTOR_STRATEGY_MILVUS.equals(
            ManagedDeploymentProfileCatalog.resolveVectorStrategy(providerNode)
        )) {
            return;
        }
        if (ManagedDeploymentProfileCatalog.milvusHost(providerNode).isBlank()) {
            issues.add(error(
                "providers",
                "MILVUS_HOST_REQUIRED",
                "$.milvusHost",
                "milvusHost is required when vectorStrategy=milvus."
            ));
        }
        validatePositiveInteger(providerNode, "milvusPort", "providers", issues);
        if (!providerNode.path("milvusSecure").isMissingNode()
            && !providerNode.path("milvusSecure").isBoolean()) {
            issues.add(error(
                "providers",
                "MILVUS_SECURE_BOOLEAN_REQUIRED",
                "$.milvusSecure",
                "milvusSecure must be a boolean when provided."
            ));
        }
        if (!providerNode.path("milvusFlushOnWrite").isMissingNode()
            && !providerNode.path("milvusFlushOnWrite").isBoolean()) {
            issues.add(error(
                "providers",
                "MILVUS_FLUSH_ON_WRITE_BOOLEAN_REQUIRED",
                "$.milvusFlushOnWrite",
                "milvusFlushOnWrite must be a boolean when provided."
            ));
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
        if (!authzMode.isEmpty()
            && !ManagedDeploymentProfileCatalog.SUPPORTED_AUTHZ_MODES.contains(authzMode.toUpperCase(Locale.ROOT))) {
            issues.add(error(
                "security",
                "UNSUPPORTED_AUTHZ_MODE",
                "$.authzMode",
                "authzMode must be one of " + ManagedDeploymentProfileCatalog.SUPPORTED_AUTHZ_MODES + "."
            ));
        }
        if ("REMOTE_HTTP".equals(authzMode) && securityNode.path("authzBaseUrl").asText("").trim().isEmpty()) {
            issues.add(warning("security", "REMOTE_AUTHZ_BASE_URL_RECOMMENDED", "$.authzBaseUrl", "REMOTE_HTTP is selected but authzBaseUrl is not configured in the draft."));
        }

        validateOptionalString(securityNode, "corsAllowedOrigins", "security", issues);
        validateOptionalString(securityNode, "corsAllowedOriginPatterns", "security", issues);
        if (!securityNode.path("corsAllowCredentials").isMissingNode()
            && !securityNode.path("corsAllowCredentials").isBoolean()) {
            issues.add(error("security", "CORS_ALLOW_CREDENTIALS_BOOLEAN_REQUIRED", "$.corsAllowCredentials", "corsAllowCredentials must be a boolean when provided."));
        }

        String allowedOrigins = securityNode.path("corsAllowedOrigins").asText("").trim();
        String allowedOriginPatterns = securityNode.path("corsAllowedOriginPatterns").asText("").trim();
        boolean allowCredentials = securityNode.path("corsAllowCredentials").asBoolean(false);

        validateCsvOrigins(allowedOrigins, "$.corsAllowedOrigins", issues);
        validateCsvOriginPatterns(allowedOriginPatterns, "$.corsAllowedOriginPatterns", issues);

        if (allowCredentials && containsWildcardOrigin(allowedOrigins)) {
            issues.add(error(
                "security",
                "CORS_WILDCARD_WITH_CREDENTIALS",
                "$.corsAllowedOrigins",
                "corsAllowedOrigins cannot contain '*' when corsAllowCredentials=true."
            ));
        }
    }

    private void validatePrompts(JsonNode promptNode, List<DraftValidationIssue> issues) {
        if (!promptNode.isObject()) {
            issues.add(error("prompts", "PROMPT_CONFIG_OBJECT_REQUIRED", "$", "promptConfig must be an object."));
            return;
        }

        List<String> promptKeys = List.of(
            "systemPrompt",
            "intentExtractionPrompt",
            "actionSelectionPrompt",
            "clarificationPrompt",
            "answerGenerationPrompt",
            "retrievalPrompt",
            "assistantUiPrompt"
        );
        int populatedCount = 0;
        for (String key : promptKeys) {
            JsonNode value = promptNode.path(key);
            if (value.isMissingNode() || value.isNull()) {
                continue;
            }
            if (!value.isTextual()) {
                issues.add(error("prompts", "PROMPT_TEXT_REQUIRED", "$." + key, key + " must be a string when provided."));
                continue;
            }
            String text = value.asText();
            if (!text.trim().isEmpty()) {
                populatedCount++;
            }
        }

        if (populatedCount == 0) {
            issues.add(warning(
                "prompts",
                "NO_PROMPTS_CONFIGURED",
                "$",
                "No prompt templates are configured yet. The deployment will continue using framework defaults."
            ));
        }
    }

    private void validateRequiredString(JsonNode node, String key, String section, List<DraftValidationIssue> issues) {
        if (node.path(key).asText("").trim().isEmpty()) {
            issues.add(error(section, "REQUIRED_VALUE_MISSING", "$." + key, key + " is required."));
        }
    }

    private void validateOptionalString(JsonNode node, String key, String section, List<DraftValidationIssue> issues) {
        JsonNode value = node.path(key);
        if (!value.isMissingNode() && !value.isNull() && !value.isTextual()) {
            issues.add(error(section, "STRING_VALUE_REQUIRED", "$." + key, key + " must be a string when provided."));
        }
    }

    private void validatePositiveInteger(JsonNode node, String key, String section, List<DraftValidationIssue> issues) {
        JsonNode value = node.path(key);
        if (value.isMissingNode() || value.isNull()) {
            return;
        }
        if (ManagedDeploymentProfileCatalog.readInt(node, key) <= 0) {
            issues.add(error(section, "POSITIVE_INTEGER_REQUIRED", "$." + key, key + " must be a positive integer when provided."));
        }
    }

    private void validateOptionalProviderSelection(JsonNode node,
                                                   String key,
                                                   String code,
                                                   List<DraftValidationIssue> issues) {
        String value = node.path(key).asText("").trim();
        if (value.isEmpty()) {
            return;
        }
        if (!ManagedDeploymentProfileCatalog.SUPPORTED_LLM_PROVIDERS.contains(value.toLowerCase(Locale.ROOT))) {
            issues.add(error(
                "providers",
                code,
                "$." + key,
                key + " must be one of " + ManagedDeploymentProfileCatalog.SUPPORTED_LLM_PROVIDERS + " when provided."
            ));
        }
    }

    private void validateBooleanIfPresent(JsonNode node,
                                          String key,
                                          String code,
                                          List<DraftValidationIssue> issues) {
        JsonNode value = node.path(key);
        if (!value.isMissingNode() && !value.isNull() && !value.isBoolean()) {
            issues.add(error("providers", code, "$." + key, key + " must be a boolean when provided."));
        }
    }

    private void validateTemperature(JsonNode node,
                                     String key,
                                     String code,
                                     List<DraftValidationIssue> issues) {
        JsonNode value = node.path(key);
        if (value.isMissingNode() || value.isNull()) {
            return;
        }
        Double parsed = ManagedDeploymentProfileCatalog.readDouble(node, key);
        if (parsed == null || parsed < 0.0 || parsed > 2.0) {
            issues.add(error("providers", code, "$." + key, key + " must be a number between 0.0 and 2.0 when provided."));
        }
    }

    private void validateOptionalAbsoluteHttpUrl(JsonNode node,
                                                 String key,
                                                 String code,
                                                 List<DraftValidationIssue> issues) {
        String value = node.path(key).asText("").trim();
        if (!value.isBlank() && !isAbsoluteHttpUrl(value)) {
            issues.add(error("providers", code, "$." + key, key + " must be a valid absolute http(s) URL."));
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

    private void validateCsvOrigins(String csv, String path, List<DraftValidationIssue> issues) {
        for (String item : splitCsv(csv)) {
            if (!isOrigin(item)) {
                issues.add(error("security", "CORS_ALLOWED_ORIGIN_INVALID", path, "Invalid CORS allowed origin: " + item));
            }
        }
    }

    private void validateCsvOriginPatterns(String csv, String path, List<DraftValidationIssue> issues) {
        for (String item : splitCsv(csv)) {
            if (!isOriginPattern(item)) {
                issues.add(error("security", "CORS_ALLOWED_ORIGIN_PATTERN_INVALID", path, "Invalid CORS allowed origin pattern: " + item));
            }
        }
    }

    private List<String> splitCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return List.of(csv.split(",")).stream()
            .map(String::trim)
            .filter(item -> !item.isEmpty())
            .toList();
    }

    private boolean containsWildcardOrigin(String csv) {
        return splitCsv(csv).stream().anyMatch("*"::equals);
    }

    private boolean isOrigin(String value) {
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme();
            if (scheme == null || uri.getHost() == null) {
                return false;
            }
            String normalized = scheme.trim().toLowerCase(Locale.ROOT);
            if (!"http".equals(normalized) && !"https".equals(normalized)) {
                return false;
            }
            String path = uri.getPath();
            return (path == null || path.isEmpty() || "/".equals(path))
                && uri.getQuery() == null
                && uri.getFragment() == null;
        } catch (Exception ex) {
            return false;
        }
    }

    private boolean isOriginPattern(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!(normalized.startsWith("http://") || normalized.startsWith("https://"))) {
            return false;
        }
        String remainder = value.substring(value.indexOf("://") + 3);
        return !remainder.isBlank()
            && !remainder.contains("/")
            && !remainder.contains("?")
            && !remainder.contains("#")
            && !remainder.contains(" ");
    }
}
