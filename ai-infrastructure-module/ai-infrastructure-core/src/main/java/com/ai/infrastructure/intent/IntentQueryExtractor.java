package com.ai.infrastructure.intent;

import com.ai.infrastructure.core.AICoreService;
import com.ai.infrastructure.dto.AIGenerationRequest;
import com.ai.infrastructure.dto.AIGenerationResponse;
import com.ai.infrastructure.dto.Intent;
import com.ai.infrastructure.dto.IntentType;
import com.ai.infrastructure.dto.MultiIntentResponse;
import com.ai.infrastructure.exception.AIServiceException;
import com.ai.infrastructure.intent.action.ActionHandlerRegistry;
import com.ai.infrastructure.intent.orchestration.OrchestrationContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Calls the LLM to extract structured intents from a user query.
 */
@Slf4j
@Service
public class IntentQueryExtractor {

    private final AICoreService aiCoreService;
    private final EnrichedPromptBuilder enrichedPromptBuilder;
    private final ActionHandlerRegistry actionHandlerRegistry;
    @Nullable
    private final KnowledgeBaseOverviewService knowledgeBaseOverviewService;
    private final ObjectMapper objectMapper;

    public IntentQueryExtractor(AICoreService aiCoreService,
                                EnrichedPromptBuilder enrichedPromptBuilder,
                                ActionHandlerRegistry actionHandlerRegistry,
                                ObjectProvider<KnowledgeBaseOverviewService> knowledgeBaseOverviewServiceProvider,
                                ObjectMapper objectMapper) {
        this.aiCoreService = aiCoreService;
        this.enrichedPromptBuilder = enrichedPromptBuilder;
        this.actionHandlerRegistry = actionHandlerRegistry;
        this.knowledgeBaseOverviewService = knowledgeBaseOverviewServiceProvider.getIfAvailable();
        this.objectMapper = objectMapper.copy()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.ACCEPT_EMPTY_ARRAY_AS_NULL_OBJECT, true)
            .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
            .configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true);
    }

    public MultiIntentResponse extract(String query, OrchestrationContext context) {
        if (!StringUtils.hasText(query)) {
            throw new AIServiceException("Query cannot be blank when extracting intents");
        }

        OrchestrationContext safeContext = context != null ? context : OrchestrationContext.anonymous();
        safeContext.validate();

        String systemPrompt = enrichedPromptBuilder.buildSystemPrompt(safeContext);
        
        String userPrompt = "analyze the following request from a user and extract the user intents from it in the provided format in system prompt\n\n-----------\n\nUser's question is :( " + query +")";

        AIGenerationRequest generationRequest = AIGenerationRequest.builder()
            .entityId("intent-" + UUID.randomUUID())
            .entityType("intent_extraction")
            .generationType("intent_extraction")
            .systemPrompt(systemPrompt)
            .prompt(userPrompt)
            .userId(safeContext.getUserId())
            .build();

        AIGenerationResponse generationResponse = aiCoreService.generateContent(generationRequest);
        String content = generationResponse != null ? generationResponse.getContent() : null;
        if (!StringUtils.hasText(content)) {
            throw new AIServiceException("Intent extraction returned an empty response from provider");
        }

        String sanitized = stripCodeFences(content);
        MultiIntentResponse response;
        try {
            response = parseResponse(sanitized);
        } catch (AIServiceException parseException) {
            log.warn("Primary intent extraction parsing failed, attempting JSON repair.", parseException);
            response = attemptRepair(safeContext.getUserId(), generationRequest, sanitized, parseException);
        }

        response.normalize();
        coerceMisclassifiedActionIntents(response);
        validateResponse(response, query);
        if (!response.hasIntents()) {
            log.warn("Intent extractor returned no intents for query '{}'", query);
        }
        return response;
    }

    /**
     * Provider-agnostic guardrail: some models mislabel "summarize / explain" requests as ACTION intents
     * with an unregistered action name (e.g., "summarize"). When the intent also clearly requests RAG
     * (requiresRetrieval + requiresGeneration + vectorSpace), treat it as INFORMATION instead so the
     * pipeline can satisfy it via retrieval/generation rather than failing with ACTION_NOT_FOUND.
     */
    private void coerceMisclassifiedActionIntents(MultiIntentResponse response) {
        if (response == null || response.getIntents() == null || response.getIntents().isEmpty()) {
            return;
        }

        for (Intent intent : response.getIntents()) {
            if (intent == null || intent.getType() != IntentType.ACTION) {
                continue;
            }

            String actionName = StringUtils.hasText(intent.getAction()) ? intent.getAction() : intent.getIntent();
            if (!StringUtils.hasText(actionName)) {
                continue;
            }

            // If we already have a handler, keep it actionable.
            if (actionHandlerRegistry != null && actionHandlerRegistry.findHandler(actionName).isPresent()) {
                continue;
            }

            boolean looksLikeRagRequest =
                Boolean.TRUE.equals(intent.getRequiresRetrieval())
                    && Boolean.TRUE.equals(intent.getRequiresGeneration())
                    && StringUtils.hasText(intent.getVectorSpace());

            if (looksLikeRagRequest) {
                log.warn(
                    "Intent extractor misclassified a RAG request as ACTION (action='{}', vectorSpace='{}'). Treating as INFORMATION.",
                    actionName,
                    intent.getVectorSpace()
                );
                intent.setType(IntentType.INFORMATION);
                intent.setAction(null);
            }
        }
    }

    private MultiIntentResponse parseResponse(String rawJson) {
        try {
            // First, try standard JSON parsing
            JsonNode root = objectMapper.readTree(rawJson);
            if (root == null || root.isNull()) {
                throw new AIServiceException("Intent extraction returned null JSON payload");
            }
            return objectMapper.treeToValue(root, MultiIntentResponse.class);
        } catch (JsonProcessingException firstAttempt) {
            // If standard parsing fails, try to extract JSON from the text
            String extractedJson = extractJsonFromText(rawJson);
            if (extractedJson != null && !extractedJson.equals(rawJson)) {
                try {
                    JsonNode root = objectMapper.readTree(extractedJson);
                    if (root == null || root.isNull()) {
                        throw new AIServiceException("Intent extraction returned null JSON payload");
                    }
                    return objectMapper.treeToValue(root, MultiIntentResponse.class);
                } catch (JsonProcessingException secondAttempt) {
                    log.error("Failed to parse extracted JSON: {}", extractedJson, secondAttempt);
                }
            }
            
            log.error("Failed to parse intent extraction response: {}", rawJson, firstAttempt);
            throw new AIServiceException("Unable to parse intent extraction response: " + firstAttempt.getMessage(), firstAttempt);
        }
    }

    private MultiIntentResponse attemptRepair(String userId,
                                              AIGenerationRequest originalRequest,
                                              String malformedContent,
                                              Exception rootCause) {
        String originalSystemPrompt = originalRequest != null ? originalRequest.getSystemPrompt() : null;
        String repairSystemPrompt = (StringUtils.hasText(originalSystemPrompt) ? originalSystemPrompt.trim() + "\n\n" : "") + """
            You are repairing a previously malformed assistant response.
            Output MUST be a single JSON object that matches the schema above exactly.
            Include ALL schema fields (use null/false/empty values where appropriate) so downstream systems can operate safely.
            Never wrap the JSON in markdown code fences and never add commentary.
            """;

        String originalUserPrompt = originalRequest != null ? originalRequest.getPrompt() : null;
        String repairPrompt = """
            Convert the malformed assistant response into valid JSON that matches the schema in the system prompt.
            Use the original user request to infer missing fields such as vectorSpace, requiresRetrieval, and requiresGeneration.
            If a value cannot be inferred, choose a safe default (e.g., OUT_OF_SCOPE with neutral confidence) but keep the schema intact.

            ORIGINAL USER REQUEST (for context):
            ---BEGIN USER REQUEST---
            %s
            ---END USER REQUEST---

            MALFORMED ASSISTANT RESPONSE:
            ---BEGIN MALFORMED---
            %s
            ---END MALFORMED---
            """.formatted(originalUserPrompt, malformedContent);

        AIGenerationRequest repairRequest = AIGenerationRequest.builder()
            .entityId(originalRequest.getEntityId() + "-repair")
            .entityType(originalRequest.getEntityType())
            .generationType("intent_extraction_repair")
            .systemPrompt(repairSystemPrompt)
            .prompt(repairPrompt)
            .userId(userId)
            .build();

        try {
            AIGenerationResponse repairResponse = aiCoreService.generateContent(repairRequest);
            String repairedContent = repairResponse != null ? repairResponse.getContent() : null;
            if (!StringUtils.hasText(repairedContent)) {
                throw new AIServiceException("Intent extraction repair attempt returned an empty response from provider");
            }
            return parseResponse(stripCodeFences(repairedContent));
        } catch (Exception repairException) {
            repairException.addSuppressed(rootCause);
            if (repairException instanceof AIServiceException aiServiceException) {
                throw aiServiceException;
            }
            throw new AIServiceException("Unable to repair intent extraction response: " + repairException.getMessage(), repairException);
        }
    }

    private String extractJsonFromText(String text) {
        // Try to find JSON object {...} in the text
        int startIdx = text.indexOf('{');
        if (startIdx >= 0) {
            int endIdx = text.lastIndexOf('}');
            if (endIdx > startIdx) {
                return text.substring(startIdx, endIdx + 1);
            }
        }
        return text;
    }

    private void validateResponse(MultiIntentResponse response, String originalQuery) {
        if (response.getIntents() == null) {
            response.setIntents(List.of());
        }

        for (Intent intent : response.getIntents()) {
            if (!intent.hasValidType()) {
                throw new AIServiceException("Intent is missing a valid type attribute");
            }
            if (!intent.hasMeaningfulName()) {
                throw new AIServiceException("Intent is missing the 'intent' or 'action' field");
            }
            validateRelationshipActionParams(intent, originalQuery);
            if (intent.getRequiresRetrieval() == null) {
                intent.setRequiresRetrieval(intent.getType() == IntentType.INFORMATION || intent.getType() == IntentType.COMPOUND);
            }
            if (Boolean.TRUE.equals(intent.getRequiresRetrieval()) && !StringUtils.hasText(intent.getVectorSpace())) {
                String inferred = inferVectorSpace(originalQuery);
                if (StringUtils.hasText(inferred)) {
                    intent.setVectorSpace(inferred);
                }
            }
        }

        if (response.getOrchestrationStrategy() == null) {
            response.setOrchestrationStrategy(deriveOrchestrationStrategy(response));
        }
    }

    private String inferVectorSpace(String originalQuery) {
        KnowledgeBaseOverview overview = safeKnowledgeBaseOverview();
        if (overview == null || overview.getEntityTypes() == null || overview.getEntityTypes().isEmpty()) {
            return null;
        }

        List<String> candidateTypes = overview.getEntityTypes();
        if (candidateTypes.size() == 1) {
            return candidateTypes.getFirst();
        }

        String query = StringUtils.hasText(originalQuery) ? originalQuery.toLowerCase(Locale.ROOT) : null;
        if (query != null) {
            for (String type : candidateTypes) {
                if (!StringUtils.hasText(type)) {
                    continue;
                }
                if (queryMentionsType(query, type)) {
                    return type;
                }
            }
        }

        Map<String, Long> counts = overview.getDocumentsByType();
        if (counts != null && !counts.isEmpty()) {
            return counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(candidateTypes.getFirst());
        }

        return candidateTypes.getFirst();
    }

    private boolean queryMentionsType(String queryLower, String type) {
        String normalized = type.toLowerCase(Locale.ROOT);
        if (queryLower.contains(normalized)) {
            return true;
        }

        String[] tokens = normalized.split("[^a-z0-9]+");
        for (String token : tokens) {
            if (token == null) {
                continue;
            }
            String trimmed = token.trim();
            if (trimmed.length() < 3) {
                continue;
            }
            if ("test".equals(trimmed) || "kb".equals(trimmed) || "doc".equals(trimmed) || "docs".equals(trimmed)) {
                continue;
            }
            if (queryLower.contains(trimmed)) {
                return true;
            }
            if (queryLower.contains(trimmed + "s")) {
                return true;
            }
            if (trimmed.endsWith("y") && trimmed.length() > 3) {
                String plural = trimmed.substring(0, trimmed.length() - 1) + "ies";
                if (queryLower.contains(plural)) {
                    return true;
                }
            }
        }
        return false;
    }

    private KnowledgeBaseOverview safeKnowledgeBaseOverview() {
        if (knowledgeBaseOverviewService == null) {
            return null;
        }
        try {
            return knowledgeBaseOverviewService.getOverview();
        } catch (Exception ex) {
            log.debug("Unable to infer vectorSpace from knowledge base overview", ex);
            return null;
        }
    }

    private String deriveOrchestrationStrategy(MultiIntentResponse response) {
        boolean hasAction = response.getIntents().stream().anyMatch(Intent::isActionable);
        boolean requiresRetrieval = response.getIntents().stream()
            .anyMatch(intent -> Boolean.TRUE.equals(intent.getRequiresRetrieval()));

        if (hasAction && !requiresRetrieval) {
            return "DIRECT_ACTION";
        }
        if (requiresRetrieval) {
            return "RETRIEVE_AND_GENERATE";
        }
        return "ADMIT_UNKNOWN";
    }

    private void validateRelationshipActionParams(Intent intent, String originalQuery) {
        if (intent.getType() != IntentType.ACTION) {
            return;
        }

        // Provider-agnostic: some models emit action="relationship query" / "relationship-query" etc.
        // Treat anything that resolves to the registered relationship_query action as relationship_query
        // for deterministic parameter normalization (especially actionParams.query).
        String actionName = StringUtils.hasText(intent.getAction()) ? intent.getAction() : intent.getIntent();
        String canonicalActionName = actionName;
        if (actionHandlerRegistry != null && StringUtils.hasText(actionName)) {
            // Be defensive: mocks can return null instead of Optional.empty().
            var metadataOpt = actionHandlerRegistry.findMetadata(actionName);
            if (metadataOpt != null) {
                canonicalActionName = metadataOpt
                    .map(com.ai.infrastructure.intent.action.AIActionMetaData::getName)
                    .orElse(actionName);
            }
        }
        if (!"relationship_query".equalsIgnoreCase(canonicalActionName)) {
            return;
        }

        Map<String, Object> params = intent.getActionParams();
        Map<String, Object> mutable = params != null ? new LinkedHashMap<>(params) : new LinkedHashMap<>();

        // The relationship_query action handler requires actionParams.query.
        // Some providers omit it, and some include the hint prefix inside it. We normalize deterministically:
        // - If present: strip known hint prefixes and trim.
        // - If missing/blank: derive from the original user query (also stripping hint prefixes).
        Object rawQuery = mutable.get("query");
        String normalizedQuery = null;
        if (rawQuery instanceof String text && StringUtils.hasText(text)) {
            normalizedQuery = normalizeRelationshipQueryText(text);
        } else {
            normalizedQuery = normalizeRelationshipQueryText(originalQuery);
        }
        if (StringUtils.hasText(normalizedQuery)) {
            mutable.put("query", normalizedQuery);
        }

        Object rawEntityTypes = mutable.get("entityTypes");

        List<String> normalizedEntityTypes;
        if (rawEntityTypes == null) {
            log.warn("Relationship query intent missing entityTypes - defaulting to empty list for actionParams");
            normalizedEntityTypes = List.of();
        } else if (rawEntityTypes instanceof List<?> list) {
            normalizedEntityTypes = list.stream()
                .filter(Objects::nonNull)
                .map(Object::toString)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(String::toLowerCase)
                .toList();
        } else if (rawEntityTypes instanceof String value) {
            String trimmed = value.trim();
            normalizedEntityTypes = trimmed.isEmpty() ? List.of() : List.of(trimmed.toLowerCase());
        } else {
            log.warn("Relationship query entityTypes should be List<String> or String but was {}", rawEntityTypes.getClass().getSimpleName());
            normalizedEntityTypes = List.of();
        }

        mutable.put("entityTypes", normalizedEntityTypes);
        intent.setActionParams(mutable);
    }

    private String normalizeRelationshipQueryText(String query) {
        if (!StringUtils.hasText(query)) {
            return null;
        }
        String trimmed = query.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        // Common hint used by tests and some callers to guide intent extraction.
        // We store the actual query without the hint prefix for the relationship_query action handler.
        String[] prefixes = { "relationship query:", "relationship_query:", "relationship-query:" };
        for (String prefix : prefixes) {
            if (lower.startsWith(prefix)) {
                String withoutPrefix = trimmed.substring(prefix.length()).trim();
                return stripTrailingNonRelationalDirective(withoutPrefix);
            }
        }
        return stripTrailingNonRelationalDirective(trimmed);
    }

    /**
     * Provider-agnostic normalization: when a user explicitly uses the "relationship query:" hint,
     * they often append a non-relational follow-up request (e.g., "and then summarize/explain").
     * The relationship_query action handler expects only the relational query text.
     *
     * <p>This is intentionally conservative: it only strips when a clear non-relational directive
     * follows a "then/and then" boundary.</p>
     */
    private String stripTrailingNonRelationalDirective(String text) {
        if (!StringUtils.hasText(text)) {
            return text;
        }
        String trimmed = text.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);

        int idx = lower.indexOf(" and then ");
        int boundaryLen = " and then ".length();
        if (idx < 0) {
            idx = lower.indexOf(" then ");
            boundaryLen = " then ".length();
        }
        if (idx < 0) {
            return trimmed;
        }

        String after = lower.substring(idx + boundaryLen);
        boolean looksNonRelational =
            after.contains("summariz")
                || after.contains("explain")
                || after.contains(" why ")
                || after.startsWith("why ")
                || after.contains("describe")
                || after.contains("analyz")
                || after.contains("recommend")
                || after.contains("write ")
                || after.contains("generate ")
                || after.contains("tell me");

        if (!looksNonRelational) {
            return trimmed;
        }

        return trimmed.substring(0, idx).trim();
    }

    @Deprecated(forRemoval = true)
    public MultiIntentResponse extract(String query, String userId) {
        return extract(query, OrchestrationContext.forUser(userId));
    }

    private String stripCodeFences(String content) {
        String trimmed = content.trim();
        while (trimmed.startsWith("###")) {
            int nextNewline = trimmed.indexOf('\n');
            if (nextNewline < 0) {
                break;
            }
            trimmed = trimmed.substring(nextNewline + 1).trim();
        }

        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline > 0) {
                trimmed = trimmed.substring(firstNewline + 1);
            }
        }

        int firstFence = trimmed.indexOf("```");
        if (firstFence >= 0) {
            int endFence = trimmed.indexOf("```", firstFence + 3);
            if (endFence > firstFence) {
                trimmed = trimmed.substring(firstFence + 3, endFence);
            }
        }

        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        return trimmed.trim();
    }
}
