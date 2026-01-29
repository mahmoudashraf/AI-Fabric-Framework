package com.ai.infrastructure.intent;

import com.ai.infrastructure.intent.action.ActionInfo;
import com.ai.infrastructure.intent.orchestration.OrchestrationContext;
import com.ai.infrastructure.prompt.PromptRenderer;
import com.ai.infrastructure.prompt.PromptTemplateResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Converts the structured context into a system prompt understood by the LLM.
 */
@Service
@RequiredArgsConstructor
public class EnrichedPromptBuilder {

    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private static final String TEMPLATE_FAMILY = "intent-extraction/compound";
    private static final String TEMPLATE_SYSTEM = "system";
    private static final String TEMPLATE_USER = "user";

    private static final String PLACEHOLDER_BEHAVIOR_CONTEXT = "behavior_context_section";
    private static final String PLACEHOLDER_AVAILABLE_ACTIONS = "available_actions_section";
    private static final String PLACEHOLDER_KNOWLEDGE_BASE = "knowledge_base_overview_section";
    private static final String PLACEHOLDER_RELATIONSHIP_QUERY_ENTITY_TYPES_RULE = "relationship_query_entity_types_rule";
    private static final String PLACEHOLDER_USER_QUERY = "user_query";

    private final SystemContextBuilder systemContextBuilder;
    private final PromptTemplateResolver promptTemplateResolver;
    private final PromptRenderer promptRenderer;

    public String buildSystemPrompt(OrchestrationContext contextInput) {
        SystemContext context = systemContextBuilder.buildContext(contextInput);

        String behavior = buildBehaviorContextSection(context);
        String actions = buildAvailableActionsSection(context);
        String knowledge = buildKnowledgeBaseOverviewSection(context);
        String entityTypesRule = buildRelationshipQueryEntityTypesRule(context);

        return promptRenderer.render(
            promptTemplateResolver.resolve(TEMPLATE_FAMILY, TEMPLATE_SYSTEM).template(),
            Map.of(
                PLACEHOLDER_BEHAVIOR_CONTEXT, behavior,
                PLACEHOLDER_AVAILABLE_ACTIONS, actions,
                PLACEHOLDER_KNOWLEDGE_BASE, knowledge,
                PLACEHOLDER_RELATIONSHIP_QUERY_ENTITY_TYPES_RULE, entityTypesRule
            )
        );
    }

    @Deprecated(forRemoval = true)
    public String buildSystemPrompt(String userId) {
        return buildSystemPrompt(OrchestrationContext.forUser(userId));
    }

    public String buildUserPrompt(String userQuery) {
        String safe = userQuery != null ? userQuery : "";
        return promptRenderer.render(
            promptTemplateResolver.resolve(TEMPLATE_FAMILY, TEMPLATE_USER).template(),
            Map.of(PLACEHOLDER_USER_QUERY, safe)
        );
    }

    private String buildBehaviorContextSection(SystemContext context) {
        if (!context.hasBehaviorContext()) {
            return "";
        }
        return "## USER BEHAVIOR CONTEXT\n"
            + context.getBehaviorContext().toPromptString()
            + "\nUse this context to tailor tone and recommendations (be empathetic for frustrated users, proactive for at-risk users).\n";
    }

    private String buildAvailableActionsSection(SystemContext context) {
        StringBuilder prompt = new StringBuilder(512);
        prompt.append("AVAILABLE ACTIONS:\n");
        if (context.getAvailableActions().isEmpty()) {
            prompt.append("- No actions are currently registered. Use information intents only.\n\n");
            return prompt.toString();
        }

        for (ActionInfo action : context.getAvailableActions()) {
            prompt.append("- ").append(action.getName());
            if (action.getDescription() != null && !action.getDescription().isBlank()) {
                prompt.append(": ").append(action.getDescription());
            }
            if (action.getCategory() != null && !action.getCategory().isBlank()) {
                prompt.append(" [category=").append(action.getCategory()).append("]");
            }
            if (!action.getParameters().isEmpty()) {
                String parameters = action.getParameters().entrySet().stream()
                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                    .collect(Collectors.joining(", "));
                prompt.append(" (params: ").append(parameters).append(")");
            }
            prompt.append("\n");
        }
        prompt.append("\n");
        return prompt.toString();
    }

    private String buildKnowledgeBaseOverviewSection(SystemContext context) {
        StringBuilder prompt = new StringBuilder(512);
        prompt.append("KNOWLEDGE BASE OVERVIEW:\n");
        KnowledgeBaseOverview overview = context.getKnowledgeBaseOverview();
        if (overview == null) {
            prompt.append("- Total documents: unknown\n");
            prompt.append("- Entity types: unknown\n\n");
            return prompt.toString();
        }

        prompt.append("- Total documents: ").append(overview.getTotalIndexedDocuments()).append("\n");
        if (!overview.getDocumentsByType().isEmpty()) {
            prompt.append("- Documents by type:\n");
            overview.getDocumentsByType().forEach((type, count) ->
                prompt.append("  • ").append(type).append(": ").append(count).append("\n")
            );
        }
        var availableSpaces = overview.getEntityTypes() != null && !overview.getEntityTypes().isEmpty()
            ? overview.getEntityTypes().stream().filter(s -> s != null && !s.isBlank()).map(String::trim).distinct().toList()
            : overview.getDocumentsByType() != null && !overview.getDocumentsByType().isEmpty()
                ? overview.getDocumentsByType().keySet().stream().filter(s -> s != null && !s.isBlank()).map(String::trim).distinct().toList()
                : java.util.List.<String>of();
        if (!availableSpaces.isEmpty()) {
            prompt.append("- Available vectorSpace values: ").append(String.join(", ", availableSpaces)).append("\n");
            prompt.append("  • vectorSpace MUST be one of these values (do NOT invent new vectorSpace names). If unsure, omit vectorSpace.\n");
        }
        if (overview.getLastIndexUpdateTime() != null) {
            prompt.append("- Last index update: ").append(TIMESTAMP_FORMATTER.format(overview.getLastIndexUpdateTime())).append("\n");
        }
        prompt.append("\n");
        return prompt.toString();
    }

    private String buildRelationshipQueryEntityTypesRule(SystemContext context) {
        Set<String> types = context != null ? context.getAvailableEntityTypes() : null;
        if (types == null || types.isEmpty()) {
            return "   - Extract entityTypes from the user request as an array of lower-case strings. No entity types are currently registered. Use [] when unknown or when no entity types match.";
        }
        return "   - Extract entityTypes from the user request as an array of lower-case strings. Available entity types: "
            + String.join(", ", types)
            + ". Only use entity types from this list. Use [] when unknown or when no entity types match.";
    }
}
