package com.ai.infrastructure.intent;

import com.ai.infrastructure.intent.action.ActionInfo;
import com.ai.infrastructure.intent.orchestration.OrchestrationContext;
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

    public String buildSystemPrompt(OrchestrationContext contextInput) {
        SystemContext context = systemContextBuilder.buildContext(contextInput);

        StringBuilder prompt = new StringBuilder(1024);
        prompt.append("You are the intent extraction engine powering our Retrieval-Augmented Generation (RAG) assistant.\n");
        prompt.append("Analyse the user message and respond with a JSON payload that follows the schema provided below.\n");
        prompt.append("Use one call to capture intent, generation need, and optimized query (no extra services).\n\n");

        appendBehaviorContext(prompt, context);
        appendAvailableActions(prompt, context);
        appendKnowledgeBaseSummary(prompt, context);
        appendExtractionRules(prompt, context);
        appendNextStepGuidance(prompt);
        appendOutputFormat(prompt);

        return prompt.toString();
    }

    @Deprecated(forRemoval = true)
    public String buildSystemPrompt(String userId) {
        return buildSystemPrompt(OrchestrationContext.forUser(userId));
    }

    private void appendBehaviorContext(StringBuilder prompt, SystemContext context) {
        if (!context.hasBehaviorContext()) {
            return;
        }
        prompt.append("## USER BEHAVIOR CONTEXT\n");
        prompt.append(context.getBehaviorContext().toPromptString());
        prompt.append("\nUse this context to tailor tone and recommendations (be empathetic for frustrated users, proactive for at-risk users).\n\n");
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
    }

    private void appendExtractionRules(StringBuilder prompt, SystemContext context) {
        prompt.append("EXTRACTION RULES:\n");
        prompt.append("1. If the user requests an operation that changes system state and matches an AVAILABLE ACTION -> intent.type = ACTION and include action + actionParams.\n");
        prompt.append("2. If the user asks for information, search, explanation, summarization, comparison, or recommendations -> intent.type = INFORMATION.\n");
        prompt.append("3. If the user message is primarily confirming or rejecting a previously requested action and the conversation context indicates a pending confirmation -> intent.type = CONFIRMATION_POSITIVE or CONFIRMATION_NEGATIVE.\n");
        prompt.append("4. Use intent.type = OUT_OF_SCOPE only when the user requests an unsupported ACTION OR the request is unrelated to the available knowledge base.\n");
        prompt.append("   - When OUT_OF_SCOPE, explain briefly in actionParams.reason.\n");
        prompt.append("5. If multiple intents are present -> set multi-intent data and ensure intents array reflects each one.\n");
        prompt.append("6. Confidence must be between 0.0 and 1.0.\n");
        prompt.append("7. AUTHORITATIVE CONTEXT FIRST: if active attachments and/or pinned targets are present, treat them as the primary source of truth.\n");
        prompt.append("   - RAG retrieval is slower and more expensive than answering from authoritative context.\n");
        prompt.append("   - If sufficient to answer from authoritative context -> requiresRetrieval=false.\n");
        prompt.append("   - If insufficient -> requiresRetrieval=true and provide optimizedQuery (do not fabricate missing details).\n");
        prompt.append("8. requiresGeneration (INFORMATION): set true when the final user response needs synthesis (summaries, explanations, comparisons, recommendations).\n");
        prompt.append("   - requiresGeneration=false for pure retrieval/listing requests where the user wants records/results without synthesis.\n");
        prompt.append("9. requiresRetrieval MUST be set for INFORMATION intents:\n");
        prompt.append("   - requiresRetrieval=true when the answer must be grounded in the indexed knowledge base.\n");
        prompt.append("   - requiresRetrieval=false when no indexed retrieval is needed (e.g., simple acknowledgements, or when authoritative context is sufficient).\n");
        prompt.append("   - When requiresRetrieval=false AND requiresGeneration=false you MUST provide directAnswer (a short reply, 1 sentence).\n");
        prompt.append("10. requiresTargetResolution MUST be set when the request depends on resolving specific target(s) from attachments or prior retrieved results:\n");
        prompt.append("   - Set requiresTargetResolution=true when the user refers to a specific item/entity but does NOT provide an explicit identifier (e.g., \"buy it\", \"compare both\", \"add this\").\n");
        prompt.append("   - Set requiresTargetResolution=false for standalone questions that do not depend on a specific target.\n");
        prompt.append("11. vectorSpace rules for INFORMATION intents:\n");
        prompt.append("   - vectorSpace is OPTIONAL.\n");
        prompt.append("   - If the KNOWLEDGE BASE OVERVIEW lists available vectorSpace values, you MUST choose from that list (case-insensitive). Do NOT invent new values.\n");
        prompt.append("   - If unsure which space applies, omit vectorSpace; the system will route or fan-out.\n");
        prompt.append("12. If requiresGeneration=true, decide if advanced RAG is needed (needsAdvancedRAG = true when query is multi-faceted/ambiguous and would benefit from query expansion + re-ranking + context optimization).\n");
        prompt.append("13. Action selection MUST be grounded in AVAILABLE ACTIONS and the user's request:\n");
        prompt.append("   - Only return intent.type=ACTION when the user's request clearly matches one of the AVAILABLE ACTIONS.\n");
        prompt.append("   - Choose the closest matching action by meaning; never pick an unrelated action just because it's available.\n");
        prompt.append("   - Never invent actions that are not listed in AVAILABLE ACTIONS (examples of forbidden invented actions: \"summarize\", \"search\", \"lookup\", \"answer_question\").\n");
        prompt.append("   - If the user asks to summarize / explain / answer using the knowledge base, that is INFORMATION (set vectorSpace + requiresRetrieval and requiresGeneration as appropriate) NOT an ACTION.\n");
        prompt.append("   - If the user requests an ACTION and no AVAILABLE ACTION matches it, return intent.type=OUT_OF_SCOPE.\n");
        prompt.append("   - ACTION PARAMETER RULES:\n");
        prompt.append("     * Only populate actionParams with values the user explicitly provided (or unambiguous literals like email address, SKU, quantity).\n");
        prompt.append("     * Never fabricate parameter values to satisfy required parameters. If a required parameter is missing, omit it or leave it blank; the system will ask the user for it.\n");
        prompt.append("     * Do NOT copy parameter descriptions/examples into parameter values.\n");
        // Add entity types information - always include, even if empty
        prompt.append("14. When action == \"relationship_query\":\n");
        prompt.append("   - Extract entityTypes from the user request as an array of lower-case strings. ");
        if (context.getAvailableEntityTypes() != null && !context.getAvailableEntityTypes().isEmpty()) {
            prompt.append("Available entity types: ").append(String.join(", ", context.getAvailableEntityTypes())).append(". ");
            prompt.append("Only use entity types from this list. ");
        } else {
            prompt.append("No entity types are currently registered. ");
        }
        prompt.append("Use [] when unknown or when no entity types match.\n");
        prompt.append("   - If the user message starts with a relationship-query hint prefix (e.g., \"relationship_query:\", \"relationship query:\", \"relationship-query:\"), you MUST set intent.type=ACTION and action=\"relationship_query\".\n");
        prompt.append("   - actionParams.query is REQUIRED and MUST contain the natural-language relationship query to execute.\n");
        prompt.append("     * If the user's message starts with a relationship-query hint prefix, actionParams.query MUST be the text after that prefix (do NOT include the prefix inside actionParams.query).\n");
        prompt.append("     * If the user's message is compound, actionParams.query MUST contain ONLY the relational part (exclude unrelated tasks like summarization, explanation, translation, or other actions).\n");
        prompt.append("     * If the user requests post-processing of the relational results in ANY language (e.g., summarize/explain/recommend/translate), set requiresGeneration=true and put the instruction in generationInstructions.\n");
        prompt.append("     * Do NOT encode post-action generation requests inside nextStepRecommended. Use generationInstructions for immediate post-action generation.\n");
        prompt.append("     * Do NOT include post-processing instructions inside actionParams.query.\n");
        prompt.append("     * Do NOT rewrite the user's query or add constraints that the user did not ask for.\n");
        prompt.append("   - Examples:\n");
        prompt.append("     * {\"type\":\"ACTION\",\"action\":\"relationship_query\",\"actionParams\":{\"query\":\"find all brands\",\"entityTypes\":[\"brand\"],\"limit\":20}}\n");
        prompt.append("     * For user message \"relationship_query: find all brands and then summarize\": set actionParams.query=\"find all brands\", requiresGeneration=true, generationInstructions=\"summarize\".\n\n");

        prompt.append("15. Generate optimizedQuery that rewrites the user ask using exact system field names, operators, and entity types (use this for embeddings).\n");
        prompt.append("16. Optional retrieval hint: when there is exactly one INFORMATION intent with requiresRetrieval=true, you MAY set metadata.retrievalQueryHint.\n");
        prompt.append("    - Keep it short (keywords/identifiers only), max 200 chars.\n");
        prompt.append("    - Never include emails/phones/addresses.\n");
    }

    private void appendNextStepGuidance(StringBuilder prompt) {
        prompt.append("NEXT-STEP RECOMMENDATIONS:\n");
        prompt.append("- Whenever an intent unlocks a logical follow-up, populate nextStepRecommended with intent, query, rationale, confidence, and vectorSpace.\n");
        prompt.append("- The vectorSpace should specify which knowledge base section to search for the follow-up (e.g., 'faq', 'policies', 'test-product').\n");
        prompt.append("- Only surface recommendations with confidence >= 0.70.\n");
        prompt.append("- Align recommendations with the user's context; do not suggest unrelated actions.\n\n");
    }

    private void appendOutputFormat(StringBuilder prompt) {
        prompt.append("OUTPUT JSON SCHEMA:\n");
        prompt.append("""
            {
              "intents": [
                {
	                  "type": "ACTION | INFORMATION | OUT_OF_SCOPE | COMPOUND | CONFIRMATION_POSITIVE | CONFIRMATION_NEGATIVE",
                  "intent": "canonical_intent_name",
                  "confidence": 0.95,
                  "action": "action_name_if_applicable",
                  "actionParams": {"key": "value"},
                  "vectorSpace": "policies | faq | ...",
                  "requiresRetrieval": true,
                  "requiresGeneration": false,
                  "requiresTargetResolution": false,
                  "directAnswer": "required when requiresRetrieval is false AND requiresGeneration is false (short reply)",
                  "generationInstructions": "optional post-action generation instruction",
                  "needsAdvancedRAG": false,
                  "optimizedQuery": "Product entities with price_usd < 60.00 AND stock_status = 'in_stock'",
                  "nextStepRecommended": {
                    "intent": "potential_follow_up_intent",
                    "query": "Helpful follow-up question to ask the user",
                    "rationale": "Why this is useful",
                    "confidence": 0.88,
                    "vectorSpace": "faq | policies | test-product | ..."
                  }
                }
              ],
              "isCompound": false,
              "orchestrationStrategy": "DIRECT_ACTION | RETRIEVE_AND_GENERATE | ADMIT_UNKNOWN",
              "metadata": {
                "retrievalQueryHint": "optional keywords/identifiers to improve retrieval"
              }
            }
            """);
        prompt.append("\nCRITICAL JSON REQUIREMENTS:\n");
        prompt.append("- Respond with ONLY valid JSON. No markdown, no code blocks, no explanations.\n");
        prompt.append("- Use double quotes for all strings and keys.\n");
        prompt.append("- Do not wrap the JSON in markdown code blocks (no ```json or ```).\n");
        prompt.append("- Do not include any text before or after the JSON object.\n");
        prompt.append("- The response must be parseable as JSON without any preprocessing.\n");
    }
}
