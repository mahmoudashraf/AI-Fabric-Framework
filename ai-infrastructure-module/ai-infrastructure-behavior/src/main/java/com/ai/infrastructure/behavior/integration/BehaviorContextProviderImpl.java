package com.ai.infrastructure.behavior.integration;

import com.ai.infrastructure.behavior.entity.BehaviorInsights;
import com.ai.infrastructure.behavior.service.BehaviorStorageAdapter;
import com.ai.infrastructure.intent.orchestration.OrchestrationContext;
import com.ai.infrastructure.spi.BehaviorContext;
import com.ai.infrastructure.spi.BehaviorContextProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Bridges behavior insights into the SPI contract without coupling core to behavior.
 * Accepts arbitrary string userIds; UUIDs are optional.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BehaviorContextProviderImpl implements BehaviorContextProvider {

    private static final Duration MAX_INSIGHT_AGE = Duration.ofHours(24);

    private final BehaviorStorageAdapter storageAdapter;

    @Override
    public Optional<BehaviorContext> getBehaviorContext(OrchestrationContext context) {
        if (context == null || !context.isAuthenticated() || !StringUtils.hasText(context.getUserId())) {
            log.trace("Behavior context not available for anonymous or missing user.");
            return Optional.empty();
        }

        String rawUserId = context.getUserId();
        UUID lookupId = toLookupId(rawUserId);

        try {
            Optional<BehaviorInsights> insights = storageAdapter.findByUserId(lookupId);
            if (insights.isEmpty()) {
                log.debug("No behavior insights found for userId={}", rawUserId);
                return Optional.empty();
            }

            BehaviorInsights insight = insights.get();
            if (isStale(insight)) {
                log.info("Behavior insights stale for userId={} (updatedAt={})", rawUserId, insight.getUpdatedAt());
                return Optional.empty();
            }

            BehaviorContext behaviorContext = toBehaviorContext(insight, rawUserId, context.getSessionId());
            log.debug("Behavior context loaded for userId={} segment={} sentiment={}",
                rawUserId, behaviorContext.getSegment(), behaviorContext.getSentimentLabel());
            return Optional.of(behaviorContext);
        } catch (Exception ex) {
            log.error("Failed to fetch behavior context for userId={}", rawUserId, ex);
            return Optional.empty();
        }
    }

    private boolean isStale(BehaviorInsights insights) {
        LocalDateTime analyzedAt = insights.getUpdatedAt();
        if (analyzedAt == null) {
            return true;
        }
        Duration age = Duration.between(analyzedAt, LocalDateTime.now());
        return age.compareTo(MAX_INSIGHT_AGE) > 0;
    }

    private BehaviorContext toBehaviorContext(BehaviorInsights insights, String rawUserId, String sessionId) {
        return BehaviorContext.builder()
            .userId(rawUserId)
            .sessionId(sessionId)
            .segment(insights.getSegment())
            .patterns(insights.getPatterns())
            .recommendations(insights.getRecommendations())
            .insights(insights.getInsights())
            .sentimentLabel(insights.getSentimentLabel() != null ? insights.getSentimentLabel().name() : null)
            .sentimentScore(insights.getSentimentScore())
            .churnRisk(insights.getChurnRisk())
            .churnReason(insights.getChurnReason())
            .trend(insights.getTrend() != null ? insights.getTrend().name() : null)
            .confidence(insights.getConfidence())
            .analyzedAt(insights.getUpdatedAt())
            .build();
    }

    private UUID toLookupId(String rawUserId) {
        try {
            return UUID.fromString(rawUserId);
        } catch (IllegalArgumentException ex) {
            return UUID.nameUUIDFromBytes(rawUserId.getBytes(StandardCharsets.UTF_8));
        }
    }
}
