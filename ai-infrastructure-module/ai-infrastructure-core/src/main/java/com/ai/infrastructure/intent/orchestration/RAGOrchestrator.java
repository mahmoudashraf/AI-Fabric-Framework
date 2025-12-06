package com.ai.infrastructure.intent.orchestration;

import com.ai.infrastructure.access.AIAccessControlService;
import com.ai.infrastructure.compliance.AIComplianceService;
import com.ai.infrastructure.config.SmartSuggestionsProperties;
import com.ai.infrastructure.dto.Intent;
import com.ai.infrastructure.dto.MultiIntentResponse;
import com.ai.infrastructure.dto.NextStepRecommendation;
import com.ai.infrastructure.dto.RAGRequest;
import com.ai.infrastructure.dto.RAGResponse;
import com.ai.infrastructure.dto.AIAccessControlRequest;
import com.ai.infrastructure.dto.AIAccessControlResponse;
import com.ai.infrastructure.dto.AIComplianceRequest;
import com.ai.infrastructure.dto.AIComplianceResponse;
import com.ai.infrastructure.dto.AISecurityRequest;
import com.ai.infrastructure.dto.AISecurityResponse;
import com.ai.infrastructure.intent.IntentQueryExtractor;
import com.ai.infrastructure.intent.history.IntentHistoryService;
import com.ai.infrastructure.intent.action.AIActionMetaData;
import com.ai.infrastructure.intent.action.ActionHandler;
import com.ai.infrastructure.intent.action.ActionHandlerRegistry;
import com.ai.infrastructure.intent.action.ActionResult;
import com.ai.infrastructure.rag.RAGService;
import com.ai.infrastructure.security.AISecurityService;
import com.ai.infrastructure.security.ResponseSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RAGOrchestrator {

    private static final double DEFAULT_RAG_THRESHOLD = 0.6;
    private static final int DEFAULT_RAG_LIMIT = 5;

    private final IntentQueryExtractor intentQueryExtractor;
    private final ActionHandlerRegistry actionHandlerRegistry;
    private final RAGService ragService;
    private final ResponseSanitizer responseSanitizer;
    private final IntentHistoryService intentHistoryService;
    private final SmartSuggestionsProperties smartSuggestionsProperties;
    private final com.ai.infrastructure.privacy.pii.PIIDetectionService piiDetectionService;
    private final com.ai.infrastructure.config.PIIDetectionProperties piiDetectionProperties;
    private final AISecurityService securityService;
    private final AIAccessControlService accessControlService;
    private final AIComplianceService complianceService;
    private final Clock clock;

    public OrchestrationResult orchestrate(String query, String userId) {
        String requestId = "rag-" + UUID.randomUUID();
        LocalDateTime requestTimestamp = LocalDateTime.now(clock);

        AISecurityResponse securityResponse = securityService.analyzeRequest(
            AISecurityRequest.builder()
                .requestId(requestId)
                .userId(userId)
                .content(query)
                .operationType("INTENT_QUERY")
                .timestamp(requestTimestamp)
                .build()
        );

        if (Boolean.TRUE.equals(securityResponse.getShouldBlock())) {
            return OrchestrationResult.error("Request blocked by security controls.");
        }

        AIAccessControlResponse accessResponse = accessControlService.checkAccess(
            AIAccessControlRequest.builder()
                .requestId(requestId)
                .userId(userId)
                .resourceId("rag:intent")
                .operationType("READ")
                .context(query)
                .metadata(Map.of("entryPoint", "RAG_ORCHESTRATOR"))
                .timestamp(requestTimestamp)
                .build()
        );

        if (!Boolean.TRUE.equals(accessResponse.getAccessGranted())) {
            return OrchestrationResult.error("Access denied by policy.");
        }

        // STEP 1: Detect & Redact PII in user query (based on configuration)
        List<String> detectedPiiTypes = new ArrayList<>();
        String processedQuery = query;
        
        com.ai.infrastructure.config.PIIDetectionProperties.PIIDetectionDirection detectionDirection =
            piiDetectionProperties.getDetectionDirection();

        boolean detectInput = piiDetectionProperties.isEnabled() &&
            (detectionDirection == com.ai.infrastructure.config.PIIDetectionProperties.PIIDetectionDirection.INPUT ||
             detectionDirection == com.ai.infrastructure.config.PIIDetectionProperties.PIIDetectionDirection.INPUT_OUTPUT);
        
        if (detectInput) {
            com.ai.infrastructure.dto.PIIDetectionResult queryPiiAnalysis = piiDetectionService.analyze(query);
            if (queryPiiAnalysis.isPiiDetected()) {
                detectedPiiTypes = queryPiiAnalysis.getDetections().stream()
                    .map(com.ai.infrastructure.dto.PIIDetection::getType)
                    .filter(t -> t != null && !t.isBlank())
                    .distinct()
                    .collect(Collectors.toList());
                log.info("PII detected in user query - types: {} (mode: INPUT_REDACTION)", detectedPiiTypes);
            }
            processedQuery = queryPiiAnalysis.getProcessedQuery();
            log.debug("Original query length: {}, Redacted query length: {}", query.length(), processedQuery.length());
        } else {
            log.debug("PII INPUT detection is disabled (configuration: {})", detectionDirection);
        }
        
        AIComplianceResponse complianceResponse = complianceService.checkCompliance(
            AIComplianceRequest.builder()
                .requestId(requestId)
                .userId(userId)
                .content(processedQuery)
                .timestamp(LocalDateTime.now(clock))
                .build()
        );

        if (Boolean.FALSE.equals(complianceResponse.getOverallCompliant())) {
            return OrchestrationResult.error("Request failed compliance validation.");
        }

        // STEP 2: Send processed query to LLM for intent extraction
        MultiIntentResponse multiIntentResponse = intentQueryExtractor.extract(processedQuery, userId);

        if (!multiIntentResponse.hasIntents()) {
            log.warn("No intents extracted for query '{}'", processedQuery);
            return OrchestrationResult.error("Unable to determine user intent.");
        }

        OrchestrationResult result;
        if (multiIntentResponse.isCompound() || multiIntentResponse.getIntents().size() > 1) {
            result = handleCompoundIntents(multiIntentResponse, userId);
        } else {
            result = handleSingleIntent(multiIntentResponse.getIntents().getFirst(), userId);
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("requestId", requestId);
        metadata.put("intentsCount", multiIntentResponse.getIntents().size());
        metadata.put("compound", multiIntentResponse.isCompound());
        if (!CollectionUtils.isEmpty(multiIntentResponse.getMetadata())) {
            metadata.put("intentMetadata", multiIntentResponse.getMetadata());
        }
        result.setMetadata(Collections.unmodifiableMap(metadata));

        applySmartSuggestions(result, userId);
        
        // STEP 3: Sanitize the response (based on configuration)
        Map<String, Object> sanitizedPayload = responseSanitizer.sanitize(result, userId);
        
        boolean detectOutput = piiDetectionProperties.isEnabled() &&
            detectionDirection == com.ai.infrastructure.config.PIIDetectionProperties.PIIDetectionDirection.INPUT_OUTPUT;
        
        if (!detectOutput) {
            log.debug("PII OUTPUT detection is disabled (configuration: {})", detectionDirection);
        }
        
        // STEP 4: Add detected PII types to response metadata
        if ((!detectedPiiTypes.isEmpty() || detectOutput) && sanitizedPayload.containsKey("sanitization")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> sanitization = (Map<String, Object>) sanitizedPayload.get("sanitization");
            Map<String, Object> updatedSanitization = new LinkedHashMap<>(sanitization);
            
            @SuppressWarnings("unchecked")
            List<String> existingTypes = (List<String>) sanitization.get("detectedTypes");
            List<String> mergedTypes = new ArrayList<>();
            if (existingTypes != null) {
                mergedTypes.addAll(existingTypes);
            }
            mergedTypes.addAll(detectedPiiTypes);
            
            // Deduplicate and sort
            List<String> finalTypes = mergedTypes.stream()
                .distinct()
                .sorted()
                .collect(Collectors.toList());
            
            if (!finalTypes.isEmpty()) {
                updatedSanitization.put("detectedTypes", finalTypes);
            }
            
            // Create new payload map with updated sanitization if changed
            if (!updatedSanitization.equals(sanitization)) {
                Map<String, Object> updatedPayload = new LinkedHashMap<>(sanitizedPayload);
                updatedPayload.put("sanitization", Collections.unmodifiableMap(updatedSanitization));
                sanitizedPayload = Collections.unmodifiableMap(updatedPayload);
            }
        }
        
        result.setSanitizedPayload(sanitizedPayload);
        persistIntentHistory(query, userId, multiIntentResponse, result);

        return result;
    }

    private OrchestrationResult handleSingleIntent(Intent intent, String userId) {
        return switch (intent.getType()) {
            case ACTION -> handleAction(intent, userId);
            case INFORMATION -> handleInformation(intent, userId);
            case OUT_OF_SCOPE -> handleOutOfScope(intent);
            case COMPOUND -> handleSyntheticCompound(intent, userId);
            default -> OrchestrationResult.error("Unknown intent type: " + intent.getType());
        };
    }

    private OrchestrationResult handleAction(Intent intent, String userId) {
        String actionName = StringUtils.hasText(intent.getAction()) ? intent.getAction() : intent.getIntent();
        if (!StringUtils.hasText(actionName)) {
            return OrchestrationResult.error("Intent is missing an action name.");
        }

        Optional<ActionHandler> maybeHandler = actionHandlerRegistry.findHandler(actionName);
        if (maybeHandler.isEmpty()) {
            return OrchestrationResult.error("No action handler registered for action '" + actionName + "'");
        }

        ActionHandler handler = maybeHandler.get();
        Map<String, Object> params = intent.getActionParams();

        if (!handler.validateActionAllowed(userId)) {
            AIActionMetaData metadata = metadataForAction(actionName);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("action", actionName);
            if (metadata != null) {
                data.put("metadata", metadata);
            }
            return OrchestrationResult.builder()
                .type(OrchestrationResultType.ACTION_DENIED)
                .success(false)
                .message("Action not permitted for this user.")
                .data(Collections.unmodifiableMap(data))
                .nextSteps(extractNextSteps(intent))
                .build();
        }

        String confirmationMessage = handler.getConfirmationMessage(params);
        try {
            ActionResult actionResult = handler.executeAction(params, userId);
            boolean success = actionResult != null && actionResult.isSuccess();

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("action", actionName);
            data.put("confirmationMessage", confirmationMessage);
            data.put("metadata", metadataForAction(actionName));
            if (actionResult != null) {
                data.put("actionResult", actionResult);
            }

            return OrchestrationResult.builder()
                .type(OrchestrationResultType.ACTION_EXECUTED)
                .success(success)
                .message(actionResult != null ? actionResult.getMessage() : null)
                .data(Collections.unmodifiableMap(data))
                .nextSteps(extractNextSteps(intent))
                .build();
        } catch (Exception ex) {
            log.error("Action handler {} threw an exception executing action '{}'", handler.getClass().getName(), actionName, ex);
            ActionResult errorResult = handler.handleError(ex, userId);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("action", actionName);
            data.put("metadata", metadataForAction(actionName));
            if (errorResult != null) {
                data.put("actionResult", errorResult);
            }
            return OrchestrationResult.builder()
                .type(OrchestrationResultType.ERROR)
                .success(false)
                .message(errorResult != null ? errorResult.getMessage() : ex.getMessage())
                .data(Collections.unmodifiableMap(data))
                .nextSteps(extractNextSteps(intent))
                .build();
        }
    }

    private OrchestrationResult handleInformation(Intent intent, String userId) {
        String query = StringUtils.hasText(intent.getIntent()) ? intent.getIntent() : intent.getIntentOrAction();
        RAGRequest ragRequest = RAGRequest.builder()
            .query(query)
            .entityType(intent.getVectorSpace())
            .limit(DEFAULT_RAG_LIMIT)
            .threshold(DEFAULT_RAG_THRESHOLD)
            .metadata(Map.of(
                "source", "orchestrator",
                "userId", userId
            ))
            .build();

        RAGResponse ragResponse = ragService.performRag(ragRequest);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("answer", ragResponse.getResponse());
        data.put("documents", ragResponse.getDocuments());
        data.put("ragResponse", ragResponse);

        return OrchestrationResult.builder()
            .type(OrchestrationResultType.INFORMATION_PROVIDED)
            .success(true)
            .message(ragResponse.getResponse())
            .data(Collections.unmodifiableMap(data))
            .nextSteps(extractNextSteps(intent))
            .build();
    }

    private OrchestrationResult handleOutOfScope(Intent intent) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (!CollectionUtils.isEmpty(intent.getActionParams())) {
            data.put("details", intent.getActionParams());
        }
        return OrchestrationResult.builder()
            .type(OrchestrationResultType.OUT_OF_SCOPE)
            .success(true)
            .message("I'm not able to help with that request. Please contact support for further assistance.")
            .data(Collections.unmodifiableMap(data))
            .nextSteps(extractNextSteps(intent))
            .build();
    }

    private OrchestrationResult handleSyntheticCompound(Intent intent, String userId) {
        if (CollectionUtils.isEmpty(intent.getActionParams())) {
            return OrchestrationResult.error("Compound intent payload is missing component intents.");
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rawChildren = (List<Map<String, Object>>) intent.getActionParams().get("intents");
        if (CollectionUtils.isEmpty(rawChildren)) {
            return OrchestrationResult.error("Compound intent payload did not include any child intents.");
        }

        List<Intent> children = rawChildren.stream()
            .map(map -> Intent.builder()
                .type(intent.getType())
                .intent((String) map.get("intent"))
                .action((String) map.get("action"))
                .confidence(map.get("confidence") instanceof Number number ? number.doubleValue() : null)
                .actionParams(map instanceof Map ? (Map<String, Object>) map.get("actionParams") : Map.of())
                .build())
            .collect(Collectors.toList());

        MultiIntentResponse syntheticResponse = MultiIntentResponse.builder()
            .intents(children)
            .compound(true)
            .build();
        return handleCompoundIntents(syntheticResponse, userId);
    }

    private OrchestrationResult handleCompoundIntents(MultiIntentResponse response, String userId) {
        List<OrchestrationResult> childResults = new ArrayList<>();
        List<NextStepRecommendation> nextSteps = new ArrayList<>();

        for (Intent intent : response.getIntents()) {
            OrchestrationResult child = handleSingleIntent(intent, userId);
            childResults.add(child);
            nextSteps.addAll(child.getNextSteps());
        }

        boolean success = childResults.stream().allMatch(OrchestrationResult::isSuccess);
        Map<String, Object> data = Map.of(
            "results", childResults
        );

        return OrchestrationResult.builder()
            .type(OrchestrationResultType.COMPOUND_HANDLED)
            .success(success)
            .message(success ? "All intents processed successfully." : "Some intents failed. See results for details.")
            .children(Collections.unmodifiableList(childResults))
            .nextSteps(Collections.unmodifiableList(nextSteps))
            .data(data)
            .build();
    }

    private List<NextStepRecommendation> extractNextSteps(Intent intent) {
        if (intent.getNextStepRecommended() == null) {
            return List.of();
        }
        return List.of(intent.getNextStepRecommended());
    }

    private void applySmartSuggestions(OrchestrationResult result, String userId) {
        if (result == null || !smartSuggestionsProperties.isEnabled()) {
            return;
        }

        List<NextStepRecommendation> nextSteps = result.getNextSteps();
        if (CollectionUtils.isEmpty(nextSteps)) {
            return;
        }

        NextStepRecommendation candidate = nextSteps.stream()
            .filter(Objects::nonNull)
            .filter(step -> step.getConfidence() != null && step.getConfidence() >= smartSuggestionsProperties.getMinConfidence())
            .max(Comparator.comparingDouble(NextStepRecommendation::getConfidence))
            .orElse(null);

        if (candidate == null) {
            return;
        }

        String query = StringUtils.hasText(candidate.getQuery()) ? candidate.getQuery() : candidate.getIntent();
        if (!StringUtils.hasText(query)) {
            log.debug("Skipping smart suggestion - missing query for recommendation intent={}", candidate.getIntent());
            return;
        }

        try {
            // Use vectorSpace from the recommendation if provided, allowing the LLM to specify which KB section to search
            RAGRequest ragRequest = RAGRequest.builder()
                .query(query)
                .entityType(candidate.getVectorSpace())  // Use LLM-provided vectorSpace for precise KB targeting
                .limit(smartSuggestionsProperties.getRetrievalLimit())
                .threshold(smartSuggestionsProperties.getRetrievalThreshold())
                .metadata(Map.of(
                    "source", "smart-suggestion",
                    "suggestionIntent", candidate.getIntent(),
                    "suggestionConfidence", candidate.getConfidence(),
                    "vectorSpace", candidate.getVectorSpace() != null ? candidate.getVectorSpace() : "unspecified",
                    "userId", userId
                ))
                .userId(userId)
                .build();

            RAGResponse ragResponse = ragService.performRag(ragRequest);
            if (ragResponse == null) {
                log.debug("Smart suggestion retrieval returned no response for intent {}", candidate.getIntent());
                return;
            }

            Map<String, Object> suggestion = new LinkedHashMap<>();
            suggestion.put("intent", candidate.getIntent());
            suggestion.put("title", convertToTitle(candidate.getIntent()));
            suggestion.put("query", query);
            suggestion.put("confidence", candidate.getConfidence());
            suggestion.put("priority", candidate.getConfidence() >= smartSuggestionsProperties.getPrimaryConfidence() ? "PRIMARY" : "SECONDARY");
            if (smartSuggestionsProperties.isIncludeRationale() && StringUtils.hasText(candidate.getRationale())) {
                suggestion.put("rationale", candidate.getRationale());
            }
            suggestion.put("response", ragResponse.getResponse());
            suggestion.put("documents", ragResponse.getDocuments());
            suggestion.put("metadata", ragResponse.getMetadata());

            result.setSmartSuggestion(Collections.unmodifiableMap(suggestion));
            result.withAdditionalData(Map.of("smartSuggestion", suggestion));
        } catch (Exception ex) {
            log.warn("Failed to generate smart suggestion for intent {}: {}", candidate.getIntent(), ex.getMessage());
        }
    }

    private String convertToTitle(String intent) {
        if (!StringUtils.hasText(intent)) {
            return null;
        }
        String normalized = intent.replaceAll("[_\\-]+", " ").trim();
        if (normalized.isEmpty()) {
            return null;
        }
        StringBuilder builder = new StringBuilder(normalized.length());
        boolean capitalizeNext = true;
        for (char ch : normalized.toCharArray()) {
            if (Character.isWhitespace(ch)) {
                capitalizeNext = true;
                builder.append(ch);
            } else if (capitalizeNext) {
                builder.append(Character.toTitleCase(ch));
                capitalizeNext = false;
            } else {
                builder.append(Character.toLowerCase(ch));
            }
        }
        return builder.toString();
    }

    private AIActionMetaData metadataForAction(String actionName) {
        try {
            Optional<AIActionMetaData> optional = actionHandlerRegistry.findMetadata(actionName);
            return optional != null ? optional.orElse(null) : null;
        } catch (Exception ex) {
            log.debug("Unable to resolve metadata for action {}: {}", actionName, ex.getMessage());
            return null;
        }
    }

    private void persistIntentHistory(String originalQuery,
                                      String userId,
                                      MultiIntentResponse intents,
                                      OrchestrationResult result) {
        try {
            intentHistoryService.recordIntent(
                userId,
                result.getMetadata() != null ? (String) result.getMetadata().get("sessionId") : null,
                originalQuery,
                intents,
                result
            );
        } catch (Exception ex) {
            log.debug("Unable to persist intent history for user {}: {}", userId, ex.getMessage());
        }
    }
}
