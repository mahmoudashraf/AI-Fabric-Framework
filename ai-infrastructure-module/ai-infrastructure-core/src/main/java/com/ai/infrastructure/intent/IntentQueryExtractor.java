package com.ai.infrastructure.intent;

import com.ai.infrastructure.core.AICoreService;
import com.ai.infrastructure.dto.AIGenerationRequest;
import com.ai.infrastructure.dto.AIGenerationResponse;
import com.ai.infrastructure.dto.Intent;
import com.ai.infrastructure.dto.IntentType;
import com.ai.infrastructure.dto.MultiIntentResponse;
import com.ai.infrastructure.exception.AIServiceException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

/**
 * Calls the LLM to extract structured intents from a user query.
 */
@Slf4j
@Service
public class IntentQueryExtractor {

    private final AICoreService aiCoreService;
    private final EnrichedPromptBuilder enrichedPromptBuilder;
    private final ObjectMapper objectMapper;

    public IntentQueryExtractor(AICoreService aiCoreService,
                                EnrichedPromptBuilder enrichedPromptBuilder,
                                ObjectMapper objectMapper) {
        this.aiCoreService = aiCoreService;
        this.enrichedPromptBuilder = enrichedPromptBuilder;
        this.objectMapper = objectMapper.copy()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.ACCEPT_EMPTY_ARRAY_AS_NULL_OBJECT, true)
            .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
            .configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true);
    }

    public MultiIntentResponse extract(String query, String userId) {
        if (!StringUtils.hasText(query)) {
            throw new AIServiceException("Query cannot be blank when extracting intents");
        }

        String systemPrompt = enrichedPromptBuilder.buildSystemPrompt(userId);
        
        String userPrompt = "analyze the following request from a user and extract the user intents from it in the provided format in system prompt\n\n-----------\n\nUser's question is :( " + query +")";

        AIGenerationRequest generationRequest = AIGenerationRequest.builder()
            .entityId("intent-" + UUID.randomUUID())
            .entityType("intent_extraction")
            .generationType("intent_extraction")
            .systemPrompt(systemPrompt)
            .prompt(userPrompt)
            .userId(userId)
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
            response = attemptRepair(userId, generationRequest, sanitized, parseException);
        }

        response.normalize();
        validateResponse(response);
        if (!response.hasIntents()) {
            log.warn("Intent extractor returned no intents for query '{}'", query);
        }
        return response;
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
        String repairSystemPrompt = """
            You transform assistant responses so that they strictly comply with a JSON schema.
            Respond with JSON only, using double quotes, matching the schema exactly as previously described.
            Never wrap the JSON in markdown code fences or add commentary.
            """;

        String repairPrompt = """
            Convert the following assistant response into valid JSON using the exact schema supplied earlier for multi-intent extraction.
            If any fields are missing, infer sensible defaults (e.g. OUT_OF_SCOPE intent with neutral confidence) while keeping the schema intact.
            Assistant response:
            ```
            %s
            ```
            """.formatted(malformedContent);

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

    private void validateResponse(MultiIntentResponse response) {
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
            if (intent.getRequiresRetrieval() == null) {
                intent.setRequiresRetrieval(intent.getType() == IntentType.INFORMATION || intent.getType() == IntentType.COMPOUND);
            }
        }

        if (response.getOrchestrationStrategy() == null) {
            response.setOrchestrationStrategy(deriveOrchestrationStrategy(response));
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
