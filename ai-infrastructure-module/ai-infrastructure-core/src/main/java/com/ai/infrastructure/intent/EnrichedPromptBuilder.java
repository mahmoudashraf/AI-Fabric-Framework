package com.ai.infrastructure.intent;

import com.ai.infrastructure.intent.action.ActionInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;

/**
 * Converts the structured context into a system prompt understood by the LLM.
 */
@Service
@RequiredArgsConstructor
public class EnrichedPromptBuilder {

    private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final SystemContextBuilder systemContextBuilder;

    public String buildSystemPrompt(String userId) {
        SystemContext context = systemContextBuilder.buildContext(userId);

        StringBuilder prompt = new StringBuilder(1024);
        prompt.append("You are the intent extraction engine powering our Retrieval-Augmented Generation (RAG) assistant.\n");
        prompt.append("Analyse the user message and respond with a JSON payload that follows the schema provided below.\n\n");

        appendAvailableActions(prompt, context);
        appendKnowledgeBaseSummary(prompt, context);
        appendExtractionRules(prompt);
        appendNextStepGuidance(prompt);
        appendOutputFormat(prompt);

        return prompt.toString();
    }

    private void appendAvailableActions(StringBuilder prompt, SystemContext context) {
        prompt.append("AVAILABLE ACTIONS:\n");
        if (context.getAvailableActions().isEmpty()) {
            prompt.append("- No actions are currently registered. Use information intents only.\n\n");
            return;
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
    }

    private void appendKnowledgeBaseSummary(StringBuilder prompt, SystemContext context) {
        prompt.append("KNOWLEDGE BASE OVERVIEW:\n");
        KnowledgeBaseOverview overview = context.getKnowledgeBaseOverview();
        if (overview == null) {
            prompt.append("- Total documents: unknown\n");
            prompt.append("- Entity types: unknown\n\n");
            return;
        }

        prompt.append("- Total documents: ").append(overview.getTotalIndexedDocuments()).append("\n");
        if (!overview.getDocumentsByType().isEmpty()) {
            prompt.append("- Documents by type:\n");
            overview.getDocumentsByType().forEach((type, count) ->
                prompt.append("  • ").append(type).append(": ").append(count).append("\n")
            );
        }
        if (overview.getLastIndexUpdateTime() != null) {
            prompt.append("- Last index update: ").append(TIMESTAMP_FORMATTER.format(overview.getLastIndexUpdateTime())).append("\n");
        }
        prompt.append("\n");
    }

    private void appendExtractionRules(StringBuilder prompt) {
        prompt.append("EXTRACTION RULES:\n");
        prompt.append("1. If the user wants to execute an action -> intent.type = ACTION and include action + actionParams.\n");
        prompt.append("2. If the user is searching for information -> intent.type = INFORMATION.\n");
        prompt.append("3. If the request is unsupported -> intent.type = OUT_OF_SCOPE and explain briefly in actionParams.reason.\n");
        prompt.append("4. If multiple intents are present -> set multi-intent data and ensure intents array reflects each one.\n");
        prompt.append("5. Confidence must be between 0.0 and 1.0.\n\n");
    }

    private void appendNextStepGuidance(StringBuilder prompt) {
        prompt.append("NEXT-STEP RECOMMENDATIONS:\n");
        prompt.append("- Whenever an intent unlocks a logical follow-up, populate nextStepRecommended with intent, query, rationale, confidence.\n");
        prompt.append("- Only surface recommendations with confidence >= 0.70.\n");
        prompt.append("- Align recommendations with the user's context; do not suggest unrelated actions.\n\n");
    }

    private void appendOutputFormat(StringBuilder prompt) {
        prompt.append("OUTPUT JSON SCHEMA:\n");
        prompt.append("""
            {
              "intents": [
                {
                  "type": "ACTION | INFORMATION | OUT_OF_SCOPE | COMPOUND",
                  "intent": "canonical_intent_name",
                  "confidence": 0.95,
                  "action": "action_name_if_applicable",
                  "actionParams": {"key": "value"},
                  "vectorSpace": "policies | faq | ...",
                  "requiresRetrieval": true,
                  "nextStepRecommended": {
                    "intent": "potential_follow_up_intent",
                    "query": "Helpful follow-up question to ask the user",
                    "rationale": "Why this is useful",
                    "confidence": 0.88
                  }
                }
              ],
              "isCompound": false,
              "orchestrationStrategy": "DIRECT_ACTION | RETRIEVE_AND_GENERATE | ADMIT_UNKNOWN",
              "metadata": {}
            }
            """);
        prompt.append("\nEnsure the response is valid JSON with double quotes and no additional commentary.\n");
    }
}
