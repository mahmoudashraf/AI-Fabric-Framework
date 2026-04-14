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
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DeploymentDraftValidationService {

    private static final Set<String> SUPPORTED_CONFIRMATION_TYPES = Set.of("CONFIRMATION_POSITIVE", "CONFIRMATION_NEGATIVE");
    private static final Set<String> SUPPORTED_CONFIRMATION_DECISION_TYPES = Set.of("PROMPT_ACTION", "EXECUTE_ACTION", "REPLY");
    private static final Pattern SAFE_ONCE_PARAM = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Pattern TEMPLATE_PLACEHOLDER = Pattern.compile("\\{\\{\\s*([^{}]+?)\\s*}}");
    private static final Set<String> SUPPORTED_SHELL_MODULE_IDS = Set.of(
        "search",
        "product-catalog",
        "cart",
        "orders",
        "purchase-orders",
        "policies",
        "reviews",
        "customer-account",
        "addresses",
        "support"
    );
    private static final Set<String> SUPPORTED_SHELL_CARD_IDS = Set.of(
        "product-list",
        "product-detail",
        "cart-summary",
        "order-status",
        "purchase-order-status",
        "policy-summary",
        "review-summary",
        "support-ticket-status",
        "address-summary",
        "pricing-summary"
    );

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
            JsonNode knowledgeSourceNode = objectMapper.readTree(draft.getKnowledgeSourceConfigJson());
            JsonNode shellNode = objectMapper.readTree(draft.getShellConfigJson());

            List<DraftValidationIssue> issues = new ArrayList<>();
            ActionValidationSummary actionValidation = validateActions(actionsNode, issues);
            validateEntities(entityNode, issues);
            validateRouting(routingNode, actionValidation, issues);
            validateProviders(providerNode, issues);
            validateEmbeddingDimensionsCompatibility(entityNode, providerNode, issues);
            validateSecurity(securityNode, issues);
            validatePrompts(promptNode, issues);
            validateKnowledgeSources(knowledgeSourceNode, issues);
            validateShellConfig(shellNode, issues);

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

    private ActionValidationSummary validateActions(JsonNode actionsNode, List<DraftValidationIssue> issues) {
        Set<String> actionNames = new HashSet<>();
        Set<String> confirmableActionNames = new HashSet<>();
        List<InlineActionRoute> inlineRoutes = new ArrayList<>();
        JsonNode actions = actionsNode.path("actions");
        if (!actions.isArray()) {
            issues.add(error("actions", "ACTIONS_ARRAY_REQUIRED", "$.actions", "actions must be an array."));
            return new ActionValidationSummary(actionNames, List.of());
        }

        if (actions.isEmpty()) {
            issues.add(warning("actions", "NO_ACTIONS_CONFIGURED", "$.actions", "No actions are currently configured."));
            return new ActionValidationSummary(actionNames, List.of());
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

            JsonNode anonymousAllowed = action.path("anonymousAllowed");
            if (!anonymousAllowed.isMissingNode() && !anonymousAllowed.isBoolean()) {
                issues.add(error("actions", "ANONYMOUS_ALLOWED_BOOLEAN", basePath + ".anonymousAllowed", "anonymousAllowed must be a boolean."));
            }

            JsonNode route = action.path("route");
            if (!route.isMissingNode() && !route.isNull()) {
                if (!route.isObject()) {
                    issues.add(error("actions", "ACTION_ROUTE_OBJECT_REQUIRED", basePath + ".route", "route must be an object when provided."));
                } else if (!name.isEmpty()) {
                    inlineRoutes.add(new InlineActionRoute(name, route, basePath + ".route"));
                }
            }

            if (action.path("requiresConfirmation").asBoolean(false) && !name.isEmpty()) {
                confirmableActionNames.add(name);
            }
        }

        validateConfirmationInterceptors(actionsNode.path("confirmationInterceptors"), actionNames, confirmableActionNames, issues);

        return new ActionValidationSummary(actionNames, List.copyOf(inlineRoutes));
    }

    private void validateKnowledgeSources(JsonNode knowledgeSourceNode, List<DraftValidationIssue> issues) {
        if (knowledgeSourceNode == null || !knowledgeSourceNode.isObject()) {
            issues.add(error(
                "knowledgeSources",
                "KNOWLEDGE_SOURCE_CONFIG_OBJECT_REQUIRED",
                "$.knowledgeSourceConfig",
                "knowledgeSourceConfig must be an object."
            ));
            return;
        }

        JsonNode contractVersion = knowledgeSourceNode.path("contractVersion");
        if (!contractVersion.isMissingNode() && !contractVersion.isNull() && !contractVersion.isTextual()) {
            issues.add(error(
                "knowledgeSources",
                "KNOWLEDGE_SOURCE_CONTRACT_VERSION_INVALID",
                "$.knowledgeSourceConfig.contractVersion",
                "knowledgeSourceConfig.contractVersion must be a string when provided."
            ));
        }

        JsonNode sources = knowledgeSourceNode.path("sources");
        if (!sources.isMissingNode() && !sources.isArray()) {
            issues.add(error(
                "knowledgeSources",
                "KNOWLEDGE_SOURCE_SOURCES_ARRAY_REQUIRED",
                "$.knowledgeSourceConfig.sources",
                "knowledgeSourceConfig.sources must be an array when provided."
            ));
            return;
        }

        if (!sources.isArray()) {
            return;
        }

        Set<String> ids = new HashSet<>();
        for (int index = 0; index < sources.size(); index++) {
            JsonNode source = sources.get(index);
            String basePath = "$.knowledgeSourceConfig.sources[" + index + "]";
            if (!source.isObject()) {
                issues.add(error("knowledgeSources", "KNOWLEDGE_SOURCE_OBJECT_REQUIRED", basePath, "Each knowledge source entry must be an object."));
                continue;
            }
            String id = source.path("id").asText("").trim();
            if (id.isEmpty()) {
                issues.add(warning("knowledgeSources", "KNOWLEDGE_SOURCE_ID_RECOMMENDED", basePath + ".id", "Knowledge source id should be provided."));
            } else if (!ids.add(id.toLowerCase(Locale.ROOT))) {
                issues.add(error("knowledgeSources", "DUPLICATE_KNOWLEDGE_SOURCE_ID", basePath + ".id", "Duplicate knowledge source id: " + id));
            }
            JsonNode enabled = source.path("enabled");
            if (!enabled.isMissingNode() && !enabled.isBoolean()) {
                issues.add(error("knowledgeSources", "KNOWLEDGE_SOURCE_ENABLED_BOOLEAN", basePath + ".enabled", "enabled must be a boolean when provided."));
            }
        }
    }

    private void validateShellConfig(JsonNode shellNode, List<DraftValidationIssue> issues) {
        if (shellNode == null || !shellNode.isObject()) {
            issues.add(error(
                "shell",
                "SHELL_CONFIG_OBJECT_REQUIRED",
                "$.shellConfig",
                "shellConfig must be an object."
            ));
            return;
        }

        JsonNode contractVersion = shellNode.path("contractVersion");
        if (!contractVersion.isMissingNode() && !contractVersion.isNull() && !contractVersion.isTextual()) {
            issues.add(error(
                "shell",
                "SHELL_CONTRACT_VERSION_INVALID",
                "$.shellConfig.contractVersion",
                "shellConfig.contractVersion must be a string when provided."
            ));
        }

        JsonNode modules = shellNode.path("modules");
        if (!modules.isMissingNode() && !modules.isArray()) {
            issues.add(error(
                "shell",
                "SHELL_MODULES_ARRAY_REQUIRED",
                "$.shellConfig.modules",
                "shellConfig.modules must be an array when provided."
            ));
        } else if (modules.isArray()) {
            for (int index = 0; index < modules.size(); index++) {
                JsonNode module = modules.get(index);
                String basePath = "$.shellConfig.modules[" + index + "]";
                if (!module.isObject()) {
                    issues.add(error("shell", "SHELL_MODULE_OBJECT_REQUIRED", basePath, "Each shell module entry must be an object."));
                    continue;
                }
                String id = module.path("id").asText("").trim();
                if (!id.isEmpty() && !SUPPORTED_SHELL_MODULE_IDS.contains(id)) {
                    issues.add(error("shell", "SHELL_MODULE_ID_UNSUPPORTED", basePath + ".id", "Unsupported shell module id: " + id));
                }
                JsonNode enabled = module.path("enabled");
                if (!enabled.isMissingNode() && !enabled.isBoolean()) {
                    issues.add(error("shell", "SHELL_MODULE_ENABLED_BOOLEAN", basePath + ".enabled", "shellConfig.modules[].enabled must be a boolean when provided."));
                }
            }
        }

        JsonNode cards = shellNode.path("cards");
        if (!cards.isMissingNode() && !cards.isArray()) {
            issues.add(error(
                "shell",
                "SHELL_CARDS_ARRAY_REQUIRED",
                "$.shellConfig.cards",
                "shellConfig.cards must be an array when provided."
            ));
        } else if (cards.isArray()) {
            for (int index = 0; index < cards.size(); index++) {
                JsonNode card = cards.get(index);
                String basePath = "$.shellConfig.cards[" + index + "]";
                if (!card.isObject()) {
                    issues.add(error("shell", "SHELL_CARD_OBJECT_REQUIRED", basePath, "Each shell card entry must be an object."));
                    continue;
                }
                String id = card.path("id").asText("").trim();
                if (!id.isEmpty() && !SUPPORTED_SHELL_CARD_IDS.contains(id)) {
                    issues.add(error("shell", "SHELL_CARD_ID_UNSUPPORTED", basePath + ".id", "Unsupported shell card id: " + id));
                }
                JsonNode enabled = card.path("enabled");
                if (!enabled.isMissingNode() && !enabled.isBoolean()) {
                    issues.add(error("shell", "SHELL_CARD_ENABLED_BOOLEAN", basePath + ".enabled", "shellConfig.cards[].enabled must be a boolean when provided."));
                }
            }
        }

        JsonNode greeting = shellNode.path("greeting");
        if (!greeting.isMissingNode() && !greeting.isObject()) {
            issues.add(error("shell", "SHELL_GREETING_OBJECT_REQUIRED", "$.shellConfig.greeting", "shellConfig.greeting must be an object when provided."));
        }

        JsonNode starterPrompts = shellNode.path("starterPrompts");
        if (!starterPrompts.isMissingNode() && !starterPrompts.isArray()) {
            issues.add(error(
                "shell",
                "SHELL_STARTER_PROMPTS_ARRAY_REQUIRED",
                "$.shellConfig.starterPrompts",
                "shellConfig.starterPrompts must be an array when provided."
            ));
        } else if (starterPrompts.isArray()) {
            for (int index = 0; index < starterPrompts.size(); index++) {
                JsonNode starterPrompt = starterPrompts.get(index);
                String basePath = "$.shellConfig.starterPrompts[" + index + "]";
                if (!starterPrompt.isObject()) {
                    issues.add(error("shell", "SHELL_STARTER_PROMPT_OBJECT_REQUIRED", basePath, "Each starter prompt entry must be an object."));
                    continue;
                }
                if (!starterPrompt.path("label").isTextual() || starterPrompt.path("label").asText("").trim().isEmpty()) {
                    issues.add(error("shell", "SHELL_STARTER_PROMPT_LABEL_REQUIRED", basePath + ".label", "shellConfig.starterPrompts[].label is required."));
                }
                if (!starterPrompt.path("query").isTextual() || starterPrompt.path("query").asText("").trim().isEmpty()) {
                    issues.add(error("shell", "SHELL_STARTER_PROMPT_QUERY_REQUIRED", basePath + ".query", "shellConfig.starterPrompts[].query is required."));
                }
                String moduleId = starterPrompt.path("moduleId").asText("").trim();
                if (!moduleId.isEmpty() && !SUPPORTED_SHELL_MODULE_IDS.contains(moduleId)) {
                    issues.add(error("shell", "SHELL_STARTER_PROMPT_MODULE_UNSUPPORTED", basePath + ".moduleId", "Unsupported starter prompt module id: " + moduleId));
                }
                String cardId = starterPrompt.path("cardId").asText("").trim();
                if (!cardId.isEmpty() && !SUPPORTED_SHELL_CARD_IDS.contains(cardId)) {
                    issues.add(error("shell", "SHELL_STARTER_PROMPT_CARD_UNSUPPORTED", basePath + ".cardId", "Unsupported starter prompt card id: " + cardId));
                }
            }
        }
    }

    private void validateConfirmationInterceptors(JsonNode confirmationInterceptorsNode,
                                                  Set<String> actionNames,
                                                  Set<String> confirmableActionNames,
                                                  List<DraftValidationIssue> issues) {
        if (confirmationInterceptorsNode.isMissingNode() || confirmationInterceptorsNode.isNull()) {
            return;
        }
        if (!confirmationInterceptorsNode.isArray()) {
            issues.add(error(
                "actions",
                "CONFIRMATION_INTERCEPTORS_ARRAY_REQUIRED",
                "$.confirmationInterceptors",
                "confirmationInterceptors must be an array."
            ));
            return;
        }

        Set<String> ruleNames = new HashSet<>();
        for (int index = 0; index < confirmationInterceptorsNode.size(); index++) {
            JsonNode rule = confirmationInterceptorsNode.get(index);
            String basePath = "$.confirmationInterceptors[" + index + "]";
            if (!rule.isObject()) {
                issues.add(error("actions", "CONFIRMATION_INTERCEPTOR_OBJECT_REQUIRED", basePath, "Each confirmation interceptor must be an object."));
                continue;
            }

            String name = rule.path("name").asText("").trim();
            if (name.isEmpty()) {
                issues.add(error("actions", "CONFIRMATION_INTERCEPTOR_NAME_REQUIRED", basePath + ".name", "Confirmation interceptor name is required."));
            } else if (!ruleNames.add(name.toLowerCase(Locale.ROOT))) {
                issues.add(error("actions", "DUPLICATE_CONFIRMATION_INTERCEPTOR_NAME", basePath + ".name", "Duplicate confirmation interceptor name: " + name));
            }

            JsonNode trigger = rule.path("trigger");
            if (!trigger.isObject()) {
                issues.add(error("actions", "CONFIRMATION_INTERCEPTOR_TRIGGER_REQUIRED", basePath + ".trigger", "trigger object is required."));
            } else {
                JsonNode pendingActions = trigger.path("pendingActions");
                if (!pendingActions.isArray() || pendingActions.isEmpty()) {
                    issues.add(error(
                        "actions",
                        "CONFIRMATION_INTERCEPTOR_PENDING_ACTIONS_REQUIRED",
                        basePath + ".trigger.pendingActions",
                        "trigger.pendingActions must be a non-empty array."
                    ));
                } else {
                    for (int pendingIndex = 0; pendingIndex < pendingActions.size(); pendingIndex++) {
                        String actionName = pendingActions.path(pendingIndex).asText("").trim();
                        if (actionName.isEmpty()) {
                            issues.add(error("actions", "CONFIRMATION_INTERCEPTOR_PENDING_ACTION_REQUIRED", basePath + ".trigger.pendingActions[" + pendingIndex + "]", "Pending action name is required."));
                        } else if (!actionNames.contains(actionName)) {
                            issues.add(error("actions", "CONFIRMATION_INTERCEPTOR_UNKNOWN_PENDING_ACTION", basePath + ".trigger.pendingActions[" + pendingIndex + "]", "Unknown action referenced by trigger.pendingActions: " + actionName));
                        }
                    }
                }

                String confirmation = trigger.path("confirmation").asText("").trim();
                if (!SUPPORTED_CONFIRMATION_TYPES.contains(confirmation)) {
                    issues.add(error(
                        "actions",
                        "CONFIRMATION_INTERCEPTOR_CONFIRMATION_INVALID",
                        basePath + ".trigger.confirmation",
                        "trigger.confirmation must be CONFIRMATION_POSITIVE or CONFIRMATION_NEGATIVE."
                    ));
                }

                JsonNode onceParam = trigger.path("onceParam");
                if (!onceParam.isMissingNode()) {
                    if (!onceParam.isTextual() || !SAFE_ONCE_PARAM.matcher(onceParam.asText().trim()).matches()) {
                        issues.add(error(
                            "actions",
                            "CONFIRMATION_INTERCEPTOR_ONCE_PARAM_INVALID",
                            basePath + ".trigger.onceParam",
                            "trigger.onceParam must match [A-Za-z_][A-Za-z0-9_]*."
                        ));
                    }
                }
            }

            JsonNode decision = rule.path("decision");
            String decisionType = "";
            if (!decision.isObject()) {
                issues.add(error("actions", "CONFIRMATION_INTERCEPTOR_DECISION_REQUIRED", basePath + ".decision", "decision object is required."));
            } else {
                decisionType = decision.path("type").asText("").trim();
                if (!SUPPORTED_CONFIRMATION_DECISION_TYPES.contains(decisionType)) {
                    issues.add(error(
                        "actions",
                        "CONFIRMATION_INTERCEPTOR_DECISION_TYPE_INVALID",
                        basePath + ".decision.type",
                        "decision.type must be PROMPT_ACTION, EXECUTE_ACTION, or REPLY."
                    ));
                }

                if (Set.of("PROMPT_ACTION", "EXECUTE_ACTION").contains(decisionType)) {
                    String actionName = decision.path("action").asText("").trim();
                    if (actionName.isEmpty()) {
                        issues.add(error("actions", "CONFIRMATION_INTERCEPTOR_DECISION_ACTION_REQUIRED", basePath + ".decision.action", "decision.action is required for action decisions."));
                    } else if (!actionNames.contains(actionName)) {
                        issues.add(error("actions", "CONFIRMATION_INTERCEPTOR_DECISION_UNKNOWN_ACTION", basePath + ".decision.action", "Unknown action referenced by decision.action: " + actionName));
                    } else if ("PROMPT_ACTION".equals(decisionType) && !confirmableActionNames.contains(actionName)) {
                        issues.add(error("actions", "CONFIRMATION_INTERCEPTOR_PROMPT_ACTION_NOT_CONFIRMABLE", basePath + ".decision.action", "PROMPT_ACTION targets must reference actions with requiresConfirmation=true."));
                    }
                }
                if ("REPLY".equals(decisionType) && decision.path("message").asText("").trim().isEmpty()) {
                    issues.add(error("actions", "CONFIRMATION_INTERCEPTOR_DECISION_MESSAGE_REQUIRED", basePath + ".decision.message", "decision.message is required for REPLY decisions."));
                }

                JsonNode params = decision.path("params");
                if (!params.isMissingNode() && !params.isObject()) {
                    issues.add(error("actions", "CONFIRMATION_INTERCEPTOR_PARAMS_OBJECT_REQUIRED", basePath + ".decision.params", "decision.params must be an object when provided."));
                } else if (params.isObject()) {
                    validateConfirmationTemplatePlaceholders(params, basePath + ".decision.params", issues);
                }
                if (decision.path("message").isTextual()) {
                    validateConfirmationTemplatePlaceholders(decision.path("message"), basePath + ".decision.message", issues);
                }
            }

            JsonNode stack = rule.path("stack");
            if (!stack.isMissingNode()) {
                if (!stack.isObject()) {
                    issues.add(error("actions", "CONFIRMATION_INTERCEPTOR_STACK_OBJECT_REQUIRED", basePath + ".stack", "stack must be an object when provided."));
                } else {
                    JsonNode popCurrent = stack.path("popCurrent");
                    if (!popCurrent.isMissingNode() && !popCurrent.isBoolean()) {
                        issues.add(error("actions", "CONFIRMATION_INTERCEPTOR_POP_CURRENT_BOOLEAN", basePath + ".stack.popCurrent", "stack.popCurrent must be a boolean."));
                    }
                    JsonNode popPreviousIfActionIn = stack.path("popPreviousIfActionIn");
                    if (!popPreviousIfActionIn.isMissingNode()) {
                        if (!popPreviousIfActionIn.isArray()) {
                            issues.add(error("actions", "CONFIRMATION_INTERCEPTOR_POP_PREVIOUS_ARRAY", basePath + ".stack.popPreviousIfActionIn", "stack.popPreviousIfActionIn must be an array when provided."));
                        } else {
                            for (int stackIndex = 0; stackIndex < popPreviousIfActionIn.size(); stackIndex++) {
                                String actionName = popPreviousIfActionIn.path(stackIndex).asText("").trim();
                                if (actionName.isEmpty()) {
                                    issues.add(error("actions", "CONFIRMATION_INTERCEPTOR_POP_PREVIOUS_ACTION_REQUIRED", basePath + ".stack.popPreviousIfActionIn[" + stackIndex + "]", "Action name is required."));
                                } else if (!actionNames.contains(actionName)) {
                                    issues.add(error("actions", "CONFIRMATION_INTERCEPTOR_POP_PREVIOUS_UNKNOWN_ACTION", basePath + ".stack.popPreviousIfActionIn[" + stackIndex + "]", "Unknown action referenced by stack.popPreviousIfActionIn: " + actionName));
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void validateConfirmationTemplatePlaceholders(JsonNode node,
                                                          String path,
                                                          List<DraftValidationIssue> issues) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                validateConfirmationTemplatePlaceholders(entry.getValue(), path + "." + entry.getKey(), issues);
            }
            return;
        }
        if (node.isArray()) {
            for (int index = 0; index < node.size(); index++) {
                validateConfirmationTemplatePlaceholders(node.get(index), path + "[" + index + "]", issues);
            }
            return;
        }
        if (!node.isTextual()) {
            return;
        }

        Matcher matcher = TEMPLATE_PLACEHOLDER.matcher(node.asText());
        while (matcher.find()) {
            String expression = matcher.group(1) != null ? matcher.group(1).trim() : "";
            String targetPath = expression;
            int fallbackSeparator = expression.indexOf('|');
            if (fallbackSeparator >= 0) {
                targetPath = expression.substring(0, fallbackSeparator).trim();
            }
            boolean supported = targetPath.startsWith("pending.actionParams.")
                || targetPath.startsWith("stack.previous.actionParams.");
            if (!supported) {
                issues.add(error(
                    "actions",
                    "CONFIRMATION_INTERCEPTOR_TEMPLATE_UNSUPPORTED",
                    path,
                    "Unsupported confirmation interceptor template placeholder: {{" + expression + "}}. Supported roots are pending.actionParams.* and stack.previous.actionParams.*."
                ));
            }
        }
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

    private void validateEmbeddingDimensionsCompatibility(JsonNode entityNode,
                                                          JsonNode providerNode,
                                                          List<DraftValidationIssue> issues) {
        String vectorStrategy = ManagedDeploymentProfileCatalog.resolveVectorStrategy(providerNode);
        int maxVectorDimensions = ManagedDeploymentProfileCatalog.maxVectorDimensions(vectorStrategy);
        if (maxVectorDimensions == Integer.MAX_VALUE) {
            return;
        }

        int vectorDimensions = entityNode.path("ai-config").path("vector-dimensions").asInt(0);
        if (vectorDimensions > maxVectorDimensions) {
            issues.add(error(
                "knowledge",
                "VECTOR_DIMENSIONS_EXCEED_BACKEND_LIMIT",
                "$.ai-config.vector-dimensions",
                "vector-dimensions exceeds the maximum supported by vectorStrategy=" + vectorStrategy + " (" + maxVectorDimensions + ")."
            ));
        }

        if (ManagedDeploymentProfileCatalog.EMBEDDING_PROVIDER_OPENAI.equals(
            ManagedDeploymentProfileCatalog.resolveEmbeddingProvider(providerNode)
        )) {
            int openAiEmbeddingDimensions = ManagedDeploymentProfileCatalog.configuredOpenAiEmbeddingDimensions(providerNode);
            if (openAiEmbeddingDimensions > maxVectorDimensions) {
                issues.add(error(
                    "providers",
                    "OPENAI_EMBEDDING_DIMENSIONS_EXCEED_BACKEND_LIMIT",
                    "$.openaiEmbeddingDimensions",
                    "openaiEmbeddingDimensions exceeds the maximum supported by vectorStrategy=" + vectorStrategy + " (" + maxVectorDimensions + ")."
                ));
            }
        }
    }

    private void validateRouting(JsonNode routingNode, ActionValidationSummary actionValidation, List<DraftValidationIssue> issues) {
        Set<String> actionNames = actionValidation.actionNames();
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
        if (!actionRoutes.isMissingNode() && !actionRoutes.isNull() && !actionRoutes.isObject()) {
            issues.add(error("routing", "ROUTES_OBJECT_REQUIRED", "$.actions", "actions routing object is required."));
            return;
        }
        JsonNode explicitRoutes = actionRoutes.isObject() ? actionRoutes : objectMapper.createObjectNode();

        if (explicitRoutes.isEmpty() && actionValidation.inlineRoutes().isEmpty()) {
            issues.add(warning("routing", "NO_ROUTES_CONFIGURED", "$.actions", "No action routes are configured yet."));
        }

        Iterator<String> routeNames = explicitRoutes.fieldNames();
        while (routeNames.hasNext()) {
            String routeName = routeNames.next();
            if (!actionNames.contains(routeName)) {
                issues.add(error("routing", "ROUTE_WITHOUT_ACTION", "$.actions." + routeName, "Route exists for undefined action: " + routeName));
                continue;
            }

            JsonNode route = explicitRoutes.path(routeName);
            if (!route.isObject()) {
                issues.add(error("routing", "ROUTE_OBJECT_REQUIRED", "$.actions." + routeName, "Each action route must be an object."));
                continue;
            }

            validateRoute("$.actions." + routeName, route, connector, issues);
        }

        Set<String> actionsWithInlineRoutes = new HashSet<>();
        for (InlineActionRoute inlineRoute : actionValidation.inlineRoutes()) {
            actionsWithInlineRoutes.add(inlineRoute.actionName());
            validateRoute(inlineRoute.path(), inlineRoute.route(), connector, issues);
        }

        for (String actionName : actionNames) {
            if (!explicitRoutes.has(actionName) && !actionsWithInlineRoutes.contains(actionName)) {
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

    private void validateRoute(String basePath,
                               JsonNode route,
                               JsonNode connector,
                               List<DraftValidationIssue> issues) {
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

        String vectorStoragePosture = providerNode.path("vectorStoragePosture").asText("").trim();
        if (!vectorStoragePosture.isEmpty()
            && !ManagedDeploymentProfileCatalog.SUPPORTED_VECTOR_STORAGE_POSTURES.contains(
                vectorStoragePosture.toUpperCase(Locale.ROOT)
            )) {
            issues.add(error(
                "providers",
                "UNSUPPORTED_VECTOR_STORAGE_POSTURE",
                "$.vectorStoragePosture",
                "vectorStoragePosture must be one of " + ManagedDeploymentProfileCatalog.SUPPORTED_VECTOR_STORAGE_POSTURES + "."
            ));
        }

        String effectiveVectorStoragePosture = ManagedDeploymentProfileCatalog.resolveVectorStoragePosture(providerNode);
        if (ManagedDeploymentProfileCatalog.supportsLocalManagedVector(vectorStrategy)
            && !ManagedDeploymentProfileCatalog.VECTOR_STORAGE_POSTURE_EMBEDDED.equals(effectiveVectorStoragePosture)) {
            issues.add(error(
                "providers",
                "EMBEDDED_VECTOR_STORAGE_REQUIRED",
                "$.vectorStoragePosture",
                "Local vector backends require vectorStoragePosture=EMBEDDED."
            ));
        }
        if (ManagedDeploymentProfileCatalog.supportsExternalExistingVector(vectorStrategy)
            && ManagedDeploymentProfileCatalog.VECTOR_STORAGE_POSTURE_EMBEDDED.equals(effectiveVectorStoragePosture)) {
            issues.add(error(
                "providers",
                "EMBEDDED_VECTOR_STORAGE_UNSUPPORTED",
                "$.vectorStoragePosture",
                "External vector backends require vectorStoragePosture=DEDICATED or SHARED."
            ));
        }
        if (ManagedDeploymentProfileCatalog.VECTOR_STORAGE_POSTURE_SHARED.equals(effectiveVectorStoragePosture)) {
            if (!ManagedDeploymentProfileCatalog.supportsSharedVectorStorage(vectorStrategy, effectiveVectorProvisioningMode)) {
                issues.add(error(
                    "providers",
                    "SHARED_VECTOR_STORAGE_UNSUPPORTED",
                    "$.vectorStoragePosture",
                    "Shared vector storage currently requires an EXTERNAL_EXISTING provider-native isolation target."
                ));
            }
            if (ManagedDeploymentProfileCatalog.managedVectorProvisioningRequested(providerNode)) {
                issues.add(error(
                    "providers",
                    "SHARED_VECTOR_STORAGE_MANAGED_PROVISIONING_UNSUPPORTED",
                    "$.vectorStoragePosture",
                    "Shared vector storage currently requires a customer-managed EXTERNAL_EXISTING provider target. Platform-managed shared vector roots are not supported."
                ));
            }
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
        boolean platformManagedMode = ManagedDeploymentProfileCatalog.pineconePlatformManaged(providerNode);
        boolean externalExistingMode = ManagedDeploymentProfileCatalog.pineconeExternalExisting(providerNode);
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
        if (externalExistingMode && apiHost.isBlank() && environment.isBlank()) {
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
        if (!providerNode.path("weaviateNativeMultiTenancyEnabled").isMissingNode()
            && !providerNode.path("weaviateNativeMultiTenancyEnabled").isBoolean()) {
            issues.add(error(
                "providers",
                "WEAVIATE_NATIVE_MULTI_TENANCY_BOOLEAN_REQUIRED",
                "$.weaviateNativeMultiTenancyEnabled",
                "weaviateNativeMultiTenancyEnabled must be a boolean when provided."
            ));
        }
        if (ManagedDeploymentProfileCatalog.sharedVectorStorageRequested(providerNode)
            && !ManagedDeploymentProfileCatalog.weaviateNativeMultiTenancyEnabled(providerNode)) {
            issues.add(error(
                "providers",
                "WEAVIATE_NATIVE_MULTI_TENANCY_REQUIRED",
                "$.weaviateNativeMultiTenancyEnabled",
                "Shared Weaviate storage requires native multi-tenancy to be enabled so tenant isolation is enforced by the provider."
            ));
        }
    }

    private void validateMilvusVectorProvider(JsonNode providerNode, List<DraftValidationIssue> issues) {
        if (!ManagedDeploymentProfileCatalog.VECTOR_STRATEGY_MILVUS.equals(
            ManagedDeploymentProfileCatalog.resolveVectorStrategy(providerNode)
        )) {
            return;
        }
        boolean platformManagedMode = ManagedDeploymentProfileCatalog.milvusPlatformManaged(providerNode);
        if (platformManagedMode) {
            if (ManagedDeploymentProfileCatalog.zillizCloudProjectId(providerNode).isBlank()) {
                issues.add(error(
                    "providers",
                    "ZILLIZ_CLOUD_PROJECT_REQUIRED",
                    "$.zillizCloudProjectId",
                    "zillizCloudProjectId is required when vectorProvisioningMode=PLATFORM_MANAGED for Milvus."
                ));
            }
            if (ManagedDeploymentProfileCatalog.zillizCloudRegionId(providerNode).isBlank()) {
                issues.add(error(
                    "providers",
                    "ZILLIZ_CLOUD_REGION_REQUIRED",
                    "$.zillizCloudRegionId",
                    "zillizCloudRegionId is required when vectorProvisioningMode=PLATFORM_MANAGED for Milvus."
                ));
            }
            String clusterPlan = ManagedDeploymentProfileCatalog.zillizCloudClusterPlan(providerNode);
            if (!ManagedDeploymentProfileCatalog.SUPPORTED_ZILLIZ_CLOUD_PLANS.contains(clusterPlan)) {
                issues.add(error(
                    "providers",
                    "ZILLIZ_CLOUD_PLAN_INVALID",
                    "$.zillizCloudClusterPlan",
                    "zillizCloudClusterPlan must be one of " + ManagedDeploymentProfileCatalog.SUPPORTED_ZILLIZ_CLOUD_PLANS + "."
                ));
            }
            boolean dedicatedPlan = "Standard".equals(clusterPlan) || "Enterprise".equals(clusterPlan);
            if (dedicatedPlan && ManagedDeploymentProfileCatalog.zillizCloudCuType(providerNode).isBlank()) {
                issues.add(error(
                    "providers",
                    "ZILLIZ_CLOUD_CU_TYPE_REQUIRED",
                    "$.zillizCloudCuType",
                    "zillizCloudCuType is required when zillizCloudClusterPlan is Standard or Enterprise."
                ));
            }
            String cuType = ManagedDeploymentProfileCatalog.zillizCloudCuType(providerNode);
            if (!cuType.isBlank() && !ManagedDeploymentProfileCatalog.SUPPORTED_ZILLIZ_CLOUD_CU_TYPES.contains(cuType)) {
                issues.add(error(
                    "providers",
                    "ZILLIZ_CLOUD_CU_TYPE_INVALID",
                    "$.zillizCloudCuType",
                    "zillizCloudCuType must be one of " + ManagedDeploymentProfileCatalog.SUPPORTED_ZILLIZ_CLOUD_CU_TYPES + "."
                ));
            }
            if (dedicatedPlan && ManagedDeploymentProfileCatalog.zillizCloudCuSize(providerNode) <= 0) {
                issues.add(error(
                    "providers",
                    "ZILLIZ_CLOUD_CU_SIZE_REQUIRED",
                    "$.zillizCloudCuSize",
                    "zillizCloudCuSize must be a positive integer when zillizCloudClusterPlan is Standard or Enterprise."
                ));
            }
        } else if (ManagedDeploymentProfileCatalog.milvusHost(providerNode).isBlank()) {
            issues.add(error(
                "providers",
                "MILVUS_HOST_REQUIRED",
                "$.milvusHost",
                "milvusHost is required when vectorStrategy=milvus."
            ));
        }
        validatePositiveInteger(providerNode, "milvusPort", "providers", issues);
        validatePositiveInteger(providerNode, "zillizCloudCuSize", "providers", issues);
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
        validateOptionalString(securityNode, "publicRuntimeTokenIssuer", "security", issues);
        validateOptionalString(securityNode, "publicRuntimeAcceptedIssuers", "security", issues);
        validateOptionalString(securityNode, "publicRuntimeAcceptedAudiences", "security", issues);
        validateOptionalString(securityNode, "publicRuntimeDefaultAudience", "security", issues);
        if (!securityNode.path("corsAllowCredentials").isMissingNode()
            && !securityNode.path("corsAllowCredentials").isBoolean()) {
            issues.add(error("security", "CORS_ALLOW_CREDENTIALS_BOOLEAN_REQUIRED", "$.corsAllowCredentials", "corsAllowCredentials must be a boolean when provided."));
        }
        if (!securityNode.path("publicRuntimeBootstrapEnabled").isMissingNode()
            && !securityNode.path("publicRuntimeBootstrapEnabled").isBoolean()) {
            issues.add(error("security", "PUBLIC_RUNTIME_BOOTSTRAP_BOOLEAN_REQUIRED", "$.publicRuntimeBootstrapEnabled", "publicRuntimeBootstrapEnabled must be a boolean when provided."));
        }

        String allowedOrigins = securityNode.path("corsAllowedOrigins").asText("").trim();
        String allowedOriginPatterns = securityNode.path("corsAllowedOriginPatterns").asText("").trim();
        String publicRuntimeTokenIssuer = securityNode.path("publicRuntimeTokenIssuer").asText("").trim();
        String publicRuntimeAcceptedIssuers = securityNode.path("publicRuntimeAcceptedIssuers").asText("").trim();
        String publicRuntimeAcceptedAudiences = securityNode.path("publicRuntimeAcceptedAudiences").asText("").trim();
        String publicRuntimeDefaultAudience = securityNode.path("publicRuntimeDefaultAudience").asText("").trim();
        boolean allowCredentials = securityNode.path("corsAllowCredentials").asBoolean(false);

        validateCsvOrigins(allowedOrigins, "$.corsAllowedOrigins", issues);
        validateCsvOriginPatterns(allowedOriginPatterns, "$.corsAllowedOriginPatterns", issues);
        validateSecurityTokenIdentifier(publicRuntimeTokenIssuer, "$.publicRuntimeTokenIssuer", "PUBLIC_RUNTIME_TOKEN_ISSUER_INVALID", issues);
        validateSecurityTokenIdentifier(publicRuntimeDefaultAudience, "$.publicRuntimeDefaultAudience", "PUBLIC_RUNTIME_DEFAULT_AUDIENCE_INVALID", issues);
        validateSecurityTokenIdentifierCsv(publicRuntimeAcceptedIssuers, "$.publicRuntimeAcceptedIssuers", "PUBLIC_RUNTIME_ACCEPTED_ISSUER_INVALID", issues);
        validateSecurityTokenIdentifierCsv(publicRuntimeAcceptedAudiences, "$.publicRuntimeAcceptedAudiences", "PUBLIC_RUNTIME_ACCEPTED_AUDIENCE_INVALID", issues);

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

        JsonNode ragSimilarityThreshold = promptNode.path("ragSimilarityThreshold");
        boolean thresholdConfigured = !ragSimilarityThreshold.isMissingNode() && !ragSimilarityThreshold.isNull();
        if (thresholdConfigured) {
            Double parsed = ManagedDeploymentProfileCatalog.readDouble(promptNode, "ragSimilarityThreshold");
            if (parsed == null || parsed < 0.0d || parsed > 1.0d) {
                issues.add(error(
                    "prompts",
                    "RAG_SIMILARITY_THRESHOLD_INVALID",
                    "$.ragSimilarityThreshold",
                    "ragSimilarityThreshold must be a number between 0.0 and 1.0 when provided."
                ));
            }
        }

        JsonNode ragMaxDocumentsUsedForContext = promptNode.path("ragMaxDocumentsUsedForContext");
        boolean maxDocumentsUsedForContextConfigured = !ragMaxDocumentsUsedForContext.isMissingNode() && !ragMaxDocumentsUsedForContext.isNull();
        if (maxDocumentsUsedForContextConfigured) {
            validatePositiveInteger(promptNode, "ragMaxDocumentsUsedForContext", "prompts", issues);
        }

        JsonNode ragMaxContextChars = promptNode.path("ragMaxContextChars");
        boolean maxContextCharsConfigured = !ragMaxContextChars.isMissingNode() && !ragMaxContextChars.isNull();
        if (maxContextCharsConfigured) {
            validatePositiveInteger(promptNode, "ragMaxContextChars", "prompts", issues);
        }

        JsonNode responseGenerationMaxTokensConcise = promptNode.path("responseGenerationMaxTokensConcise");
        boolean conciseGenerationTokensConfigured =
            !responseGenerationMaxTokensConcise.isMissingNode() && !responseGenerationMaxTokensConcise.isNull();
        if (conciseGenerationTokensConfigured) {
            validatePositiveInteger(promptNode, "responseGenerationMaxTokensConcise", "prompts", issues);
        }

        JsonNode responseGenerationMaxTokensStandard = promptNode.path("responseGenerationMaxTokensStandard");
        boolean standardGenerationTokensConfigured =
            !responseGenerationMaxTokensStandard.isMissingNode() && !responseGenerationMaxTokensStandard.isNull();
        if (standardGenerationTokensConfigured) {
            validatePositiveInteger(promptNode, "responseGenerationMaxTokensStandard", "prompts", issues);
        }

        JsonNode responseGenerationMaxTokensDeep = promptNode.path("responseGenerationMaxTokensDeep");
        boolean deepGenerationTokensConfigured =
            !responseGenerationMaxTokensDeep.isMissingNode() && !responseGenerationMaxTokensDeep.isNull();
        if (deepGenerationTokensConfigured) {
            validatePositiveInteger(promptNode, "responseGenerationMaxTokensDeep", "prompts", issues);
        }

        JsonNode smartSuggestionsEnabled = promptNode.path("smartSuggestionsEnabled");
        boolean suggestionsConfigured = !smartSuggestionsEnabled.isMissingNode() && !smartSuggestionsEnabled.isNull();
        if (suggestionsConfigured && !isStrictBooleanNode(smartSuggestionsEnabled)) {
            issues.add(error(
                "prompts",
                "SMART_SUGGESTIONS_ENABLED_INVALID",
                "$.smartSuggestionsEnabled",
                "smartSuggestionsEnabled must be true or false when provided."
            ));
        }

        if (populatedCount == 0
            && !thresholdConfigured
            && !maxDocumentsUsedForContextConfigured
            && !maxContextCharsConfigured
            && !conciseGenerationTokensConfigured
            && !standardGenerationTokensConfigured
            && !deepGenerationTokensConfigured
            && !suggestionsConfigured) {
            issues.add(warning(
                "prompts",
                "NO_PROMPTS_CONFIGURED",
                "$",
                "No prompt templates are configured yet. The deployment will continue using framework defaults."
            ));
        }
    }

    private boolean isStrictBooleanNode(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return false;
        }
        if (node.isBoolean()) {
            return true;
        }
        if (!node.isTextual()) {
            return false;
        }
        String value = node.asText("").trim();
        return "true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value);
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

    private void validateSecurityTokenIdentifierCsv(String csv,
                                                    String path,
                                                    String code,
                                                    List<DraftValidationIssue> issues) {
        for (String item : splitCsv(csv)) {
            validateSecurityTokenIdentifier(item, path, code, issues);
        }
    }

    private void validateSecurityTokenIdentifier(String value,
                                                 String path,
                                                 String code,
                                                 List<DraftValidationIssue> issues) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (!value.matches("[A-Za-z0-9._:/-]{1,200}")) {
            issues.add(error("security", code, path, "Invalid runtime public token identifier: " + value));
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

    private record ActionValidationSummary(Set<String> actionNames, List<InlineActionRoute> inlineRoutes) {
    }

    private record InlineActionRoute(String actionName, JsonNode route, String path) {
    }
}
