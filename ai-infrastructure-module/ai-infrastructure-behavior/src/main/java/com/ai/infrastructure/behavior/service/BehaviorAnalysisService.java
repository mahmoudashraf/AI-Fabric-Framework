package com.ai.infrastructure.behavior.service;

import com.ai.infrastructure.annotation.AIProcess;
import com.ai.infrastructure.behavior.entity.BehaviorInsights;
import com.ai.infrastructure.behavior.model.ExternalEvent;
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
        try {
            String prompt = buildEvolutionaryPrompt(oldInsight, newEvents, userContext);
            
            AIGenerationResponse response = aiCoreService.generateContent(
                AIGenerationRequest.builder()
                    .entityId(userId.toString())
                    .entityType("behavior-insight")
                    .generationType("behavioral-analysis")
                    .prompt(prompt)
                    .systemPrompt(getSystemPrompt())
                    .temperature(0.3)
                    .maxTokens(800)
                    .build()
            );
            
            return parseLLMResponse(userId, response.getContent(), oldInsight);
            
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
            prompt.append("User Context:\n");
            userContext.forEach((key, value) ->
                prompt.append("- ").append(key).append(": ").append(value).append("\n")
            );
            prompt.append("\n");
        }
        
        if (oldInsight != null) {
            prompt.append("Previous Analysis:\n");
            prompt.append("- Segment: ").append(oldInsight.getSegment()).append("\n");
            prompt.append("- Patterns: ").append(oldInsight.getPatterns()).append("\n");
            prompt.append("- Analyzed At: ").append(oldInsight.getAnalyzedAt()).append("\n\n");
        } else {
            prompt.append("This is a NEW user with no previous analysis.\n\n");
        }
        
        prompt.append("New Events (").append(newEvents.size()).append("):\n");
        newEvents.forEach(event -> prompt.append("- ")
            .append(event.getEventType())
            .append(" at ").append(event.getTimestamp())
            .append(" | Data: ").append(event.getEventData())
            .append("\n"));
        
        prompt.append("\nAnalyze how this user's behavior has evolved. ");
        prompt.append("Consider the user context when generating segments and recommendations. ");
        prompt.append("Provide: segment, patterns, recommendations, and confidence.");
        
        return prompt.toString();
    }
    
    private String getSystemPrompt() {
        return """
            You are a behavioral analyst. Your task is to analyze user actions and provide:
            1. Segment: A category (e.g., "Power User", "At Risk", "Dormant")
            2. Patterns: A list of behavioral patterns observed
            3. Recommendations: Actionable suggestions
            4. Confidence: A score from 0.0 to 1.0
            
            Respond ONLY with valid JSON:
            {
              "segment": "string",
              "patterns": ["pattern1", "pattern2"],
              "recommendations": ["rec1", "rec2"],
              "insights": {"key": "value"},
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
            .analyzedAt(LocalDateTime.now())
            .aiModelUsed("gpt-4o");
        
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
    
    @AIProcess(
        entityType = "behavior-insight",
        processType = "create"
    )
    private BehaviorInsights saveAndIndex(BehaviorInsights insight) {
        return storageAdapter.save(insight);
    }
}
