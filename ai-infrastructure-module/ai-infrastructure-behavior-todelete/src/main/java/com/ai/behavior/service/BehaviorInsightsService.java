package com.ai.behavior.service;

import com.ai.behavior.config.BehaviorModuleProperties;
import com.ai.behavior.model.BehaviorInsights;
import com.ai.behavior.repository.BehaviorInsightsRepository;
import com.ai.behavior.storage.BehaviorSignalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BehaviorInsightsService {

    private final BehaviorInsightsRepository insightsRepository;
    private final BehaviorAnalysisService analysisService;
    private final BehaviorSignalRepository signalRepository;
    private final BehaviorModuleProperties properties;

    @Transactional(readOnly = true)
    public BehaviorInsights getUserInsights(UUID userId) {
        return insightsRepository.findTopByUserIdOrderByAnalyzedAtDesc(userId)
            .filter(BehaviorInsights::isValid)
            .orElseGet(() -> refreshInsights(userId));
    }

    @Transactional
    public BehaviorInsights refreshInsights(UUID userId) {
        log.debug("Refreshing behavior insights for user {}", userId);
        if (!hasEnoughSignals(userId)) {
            return insightsRepository.save(analysisService.emptyInsights(userId));
        }
        BehaviorInsights insights = analysisService.analyze(userId);
        return insightsRepository.save(insights);
    }

    private boolean hasEnoughSignals(UUID userId) {
        int minEvents = properties.getInsights().getMinEventsForInsights();
        if (minEvents <= 0) {
            return true;
        }
        int lookbackDays = Math.max(1, properties.getInsights().getMinEventsLookbackDays());
        LocalDateTime since = LocalDateTime.now().minusDays(lookbackDays);
        long count = signalRepository.countByUserIdAndTimestampAfter(userId, since);
        return count >= minEvents;
    }
}
