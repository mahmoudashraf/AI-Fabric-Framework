package com.ai.behavior.processing.worker;

import com.ai.behavior.config.BehaviorModuleProperties;
import com.ai.behavior.storage.BehaviorEventRepository;
import com.ai.behavior.service.BehaviorInsightsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class PatternDetectionWorker {

    private final BehaviorModuleProperties properties;
    private final BehaviorEventRepository eventRepository;
    private final BehaviorInsightsService insightsService;

    @Scheduled(cron = "${ai.behavior.processing.pattern-detection.schedule:0 */5 * * * *}")
    @Transactional
    public void run() {
        if (!properties.getProcessing().getPatternDetection().isEnabled()) {
            return;
        }
        LocalDateTime windowStart = LocalDateTime.now()
            .minusHours(properties.getProcessing().getPatternDetection().getAnalysisWindowHours());
        List<UUID> userIds = eventRepository.findDistinctUserIdsSince(windowStart);
        if (userIds.isEmpty()) {
            return;
        }
        int minEvents = properties.getProcessing().getPatternDetection().getMinEvents();
        for (UUID userId : userIds) {
            long eventCount = eventRepository.countByUserId(userId);
            if (eventCount >= minEvents) {
                insightsService.refreshInsights(userId);
            }
        }
        log.debug("Pattern detection processed {} users", userIds.size());
    }
}
