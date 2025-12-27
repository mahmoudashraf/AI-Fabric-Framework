package com.ai.infrastructure.behavior.service;

import com.ai.infrastructure.annotation.AIProcess;
import com.ai.infrastructure.behavior.entity.BehaviorInsights;
import com.ai.infrastructure.behavior.model.BehaviorTrend;
import com.ai.infrastructure.behavior.model.ExternalEvent;
import com.ai.infrastructure.behavior.model.SentimentLabel;
import com.ai.infrastructure.behavior.model.UserEventBatch;
import com.ai.infrastructure.behavior.spi.ExternalEventProvider;
import com.ai.infrastructure.core.AICoreService;
import com.ai.infrastructure.dto.AIGenerationRequest;
import com.ai.infrastructure.dto.AIGenerationResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnBean(ExternalEventProvider.class)
public class BehaviorAnalysisService {
    
    private final ExternalEventProvider eventProvider;
    private final BehaviorStorageAdapter storageAdapter;
    private final AICoreService aiCoreService;
    private final ObjectMapper objectMapper;
    
    /**
     * CASE 1: Analyze a specific user (Targeted)
     */
    @Transactional
    public BehaviorInsights analyzeUser(UUID userId) {
        log.info("Starting targeted analysis for user: {}", userId);
        
        Optional<BehaviorInsights> existingInsight = storageAdapter.findByUserId(userId);
        List<ExternalEvent> newEvents = eventProvider.getEventsForUser(userId, null, null);
        
        if (newEvents == null || newEvents.isEmpty()) {
            log.warn("No events found for user: {}", userId);
            return existingInsight.orElse(null);
        }
        
        BehaviorInsights updatedInsight = performEvolutionaryAnalysis(
            userId,
            existingInsight.orElse(null),
            newEvents,
            null
        );
        
        return saveAndIndex(updatedInsight);
    }
    
    /**
     * CASE 2: Process the next user in queue (Discovery)
     */
    @Transactional
    public BehaviorInsights processNextUser() {
        log.debug("Fetching next user for batch processing");
        
        UserEventBatch batch = eventProvider.getNextUserEvents();
        if (batch == null || batch.getUserId() == null) {
            log.debug("No users pending analysis");
            return null;
        }
        
        List<ExternalEvent> events = batch.getEvents() != null
            ? batch.getEvents()
            : Collections.emptyList();
        
        if (events.isEmpty()) {
            log.warn("No events returned for discovery user: {}", batch.getUserId());
            return storageAdapter.findByUserId(batch.getUserId()).orElse(null);
        }
        
        log.info("Processing batch for user: {} with {} events",
            batch.getUserId(), batch.getTotalEventCount());
        
        Optional<BehaviorInsights> existingInsight = storageAdapter.findByUserId(batch.getUserId());
        
        BehaviorInsights updatedInsight = performEvolutionaryAnalysis(
            batch.getUserId(),
            existingInsight.orElse(null),
            events,
            batch.getUserContext()
        );
        
        return saveAndIndex(updatedInsight);
    }
    
    private BehaviorInsights performEvolutionaryAnalysis(
        UUID userId,
        BehaviorInsights oldInsight,
        List<ExternalEvent> newEvents,
        Map<String, Object> userContext
    ) {
        long startTime = System.currentTimeMillis();
        try {
            String prompt = buildEvolutionaryPrompt(oldInsight, newEvents, userContext);

            AIGenerationResponse response = aiCoreService.generateContent(
                AIGenerationRequest.builder()
                    .entityId(userId.toString())
                    .entityType("behavior-insight")
                    .generationType("behavioral-analysis")
                    .prompt(prompt)
                    .systemPrompt(getSystemPrompt())
                    .temperature(0.2)
                    .maxTokens(1200)
                    .build()
            );

            BehaviorInsights result = parseLLMResponse(userId, response.getContent(), oldInsight);

            // carry forward previous values for deltas
            if (oldInsight != null) {
                result.setPreviousSentimentScore(oldInsight.getSentimentScore());
                result.setPreviousChurnRisk(oldInsight.getChurnRisk());
                if (result.getTrend() == null) {
                    result.setTrend(BehaviorTrend.fromDeltas(result.getSentimentDelta(), result.getChurnDelta(), false));
                }
                result.setCreatedAt(oldInsight.getCreatedAt());
                result.setId(oldInsight.getId());
            } else if (result.getTrend() == null) {
                result.setTrend(BehaviorTrend.NEW_USER);
            }

            result.setProcessingTimeMs(System.currentTimeMillis() - startTime);
            result.setAiModelUsed(response.getModel() != null ? response.getModel() : "gpt-4o");
            result.setModelPromptVersion("3.1.0");

            logTrendAlert(userId, oldInsight, result);

            return result;
        } catch (Exception e) {
            log.error("Failed to perform evolutionary analysis for user: {}", userId, e);

            if (oldInsight != null) {
                return oldInsight;
            }

            return BehaviorInsights.builder()
                .userId(userId)
                .segment("unknown")
                .analyzedAt(LocalDateTime.now())
                .confidence(0.0)
                .aiModelUsed("fallback")
                .trend(BehaviorTrend.STABLE)
                .processingTimeMs(System.currentTimeMillis() - startTime)
                .build();
        }
    }

    private String buildEvolutionaryPrompt(
        BehaviorInsights oldInsight,
        List<ExternalEvent> newEvents,
        Map<String, Object> userContext
    ) {
        StringBuilder prompt = new StringBuilder();

        if (userContext != null && !userContext.isEmpty()) {
            prompt.append("=== USER CONTEXT ===\n");
            userContext.forEach((key, value) ->
                prompt.append("- ").append(key).append(": ").append(value).append("\n")
            );
            prompt.append("\n");
        }

        if (oldInsight != null) {
            prompt.append("=== PREVIOUS ANALYSIS ===\n");
            prompt.append("- Segment: ").append(oldInsight.getSegment()).append("\n");
            prompt.append("- Sentiment: ").append(oldInsight.getSentimentScore())
                .append(" / ").append(oldInsight.getSentimentLabel()).append("\n");
            prompt.append("- Churn Risk: ").append(oldInsight.getChurnRisk()).append("\n");
            prompt.append("- Trend: ").append(oldInsight.getTrend()).append("\n\n");
        } else {
            prompt.append("This is a NEW user with no previous analysis.\n\n");
        }

        prompt.append("=== NEW EVENTS (").append(newEvents.size()).append(") ===\n");
        newEvents.forEach(event -> prompt.append("- ")
            .append(event.getEventType())
            .append(" at ").append(event.getTimestamp())
            .append(" | Data: ").append(event.getEventData())
            .append("\n"));

        prompt.append("\nAnalyze how this user's behavior has evolved. Compare with previous values when available. ");
        prompt.append("Provide: segment, patterns, sentiment(score+label), churn(risk+reason), trend, recommendations, insights, confidence.");

        return prompt.toString();
    }

    private String getSystemPrompt() {
        return """
            You are an expert Behavioral Psychologist specializing in TREND DETECTION.

            Analyze user behavior and detect CHANGES over time.

            Output Dimensions:
            1. Segment
            2. Patterns
            3. Sentiment {score: -1..1, label: DELIGHTED|SATISFIED|NEUTRAL|CONFUSED|FRUSTRATED|CHURNING}
            4. Churn {risk: 0..1, reason: string}
            5. Trend {RAPIDLY_IMPROVING|IMPROVING|STABLE|DECLINING|RAPIDLY_DECLINING|NEW_USER}
            6. Recommendations
            7. Insights
            8. Confidence (0..1)

            Respond with valid JSON:
            {
              "segment": "string",
              "patterns": ["string"],
              "sentiment": {"score": 0.0, "label": "string"},
              "churn": {"risk": 0.0, "reason": "string"},
              "trend": "string",
              "recommendations": ["string"],
              "insights": {},
              "confidence": 0.0-1.0
            }
            """;
    }

    private BehaviorInsights parseLLMResponse(
        UUID userId,
        String llmResponse,
        BehaviorInsights oldInsight
    ) throws Exception {
        String json = extractJson(llmResponse);

        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = objectMapper.readValue(json, Map.class);

        BehaviorInsights.BehaviorInsightsBuilder builder = BehaviorInsights.builder()
            .userId(userId)
            .segment((String) parsed.get("segment"))
            .patterns((List<String>) parsed.get("patterns"))
            .recommendations((List<String>) parsed.get("recommendations"))
            .insights((Map<String, Object>) parsed.get("insights"))
            .confidence(((Number) parsed.getOrDefault("confidence", 0.5)).doubleValue())
            .analyzedAt(LocalDateTime.now());

        if (oldInsight != null) {
            builder.previousSentimentScore(oldInsight.getSentimentScore());
            builder.previousChurnRisk(oldInsight.getChurnRisk());
        }

        Map<String, Object> sentiment = (Map<String, Object>) parsed.get("sentiment");
        if (sentiment != null) {
            Double score = ((Number) sentiment.getOrDefault("score", 0.0)).doubleValue();
            String labelStr = (String) sentiment.get("label");
            score = Math.max(-1.0, Math.min(1.0, score));
            SentimentLabel label = SentimentLabel.fromString(labelStr);
            if (labelStr != null && label == SentimentLabel.NEUTRAL && !labelStr.equalsIgnoreCase("NEUTRAL")) {
                log.warn("Invalid sentiment label '{}' for user {}, defaulted to NEUTRAL", labelStr, userId);
            }
            builder.sentimentScore(score).sentimentLabel(label);
        }

        Map<String, Object> churn = (Map<String, Object>) parsed.get("churn");
        if (churn != null) {
            Double risk = ((Number) churn.getOrDefault("risk", 0.0)).doubleValue();
            String reason = (String) churn.get("reason");
            risk = Math.max(0.0, Math.min(1.0, risk));
            if (risk > 0.5 && (reason == null || reason.isBlank())) {
                log.warn("High churn risk without reason for user {}", userId);
                reason = "Behavioral drift detected";
            }
            builder.churnRisk(risk).churnReason(reason);
        }

        String trendStr = (String) parsed.get("trend");
        BehaviorTrend trend = BehaviorTrend.fromString(trendStr);
        if (trendStr != null && trend == BehaviorTrend.STABLE && !trendStr.equalsIgnoreCase("STABLE")) {
            log.warn("Invalid trend '{}' for user {}, computing from deltas", trendStr, userId);
        }

        BehaviorInsights temp = builder.build();
        if (trend == null || (oldInsight != null && trend == BehaviorTrend.STABLE)) {
            trend = BehaviorTrend.fromDeltas(temp.getSentimentDelta(), temp.getChurnDelta(), oldInsight == null);
        }
        builder.trend(trend);

        if (oldInsight != null) {
            builder.id(oldInsight.getId())
                .createdAt(oldInsight.getCreatedAt());
        }

        return builder.build();
    }

    private String extractJson(String response) {
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        if (start == -1 || end <= start) {
            throw new IllegalStateException("No valid JSON in LLM response");
        }
        return response.substring(start, end + 1);
    }

    private void logTrendAlert(UUID userId, BehaviorInsights old, BehaviorInsights current) {
        if (old == null) {
            log.info("New user analyzed: {} | Sentiment: {} | Churn: {} | Trend: {}",
                userId, current.getSentimentLabel(), current.getChurnRisk(), current.getTrend());
            return;
        }

        if (current.getTrend() != null && current.getTrend().isNegative()) {
            log.warn("User {} trend worsening: {} -> {}", userId,
                old.getTrend(), current.getTrend());
        } else {
            log.info("User {} trend: {} -> {}", userId,
                old.getTrend(), current.getTrend());
        }
    }

    @AIProcess(
        entityType = "behavior-insight",
        processType = "create"
    )
    private BehaviorInsights saveAndIndex(BehaviorInsights insight) {
        return storageAdapter.save(insight);
    }
}
