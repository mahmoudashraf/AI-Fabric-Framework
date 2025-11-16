package com.ai.behavior.processing.worker;

import com.ai.behavior.config.BehaviorProperties;
import com.ai.behavior.repository.BehaviorEventRepository;
import com.ai.behavior.service.BehaviorAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class PatternDetectionWorker {

    private final BehaviorProperties properties;
    private final BehaviorEventRepository eventRepository;
    private final BehaviorAnalysisService analysisService;
    private final Clock clock;

    @Scheduled(fixedDelayString = "#{@behaviorProperties.processing.patternDetection.fixedDelay.toMillis()}")
    public void detectPatterns() {
        if (!properties.getProcessing().getPatternDetection().isEnabled()) {
            return;
        }
        LocalDateTime cutoff = LocalDateTime.now(clock).minusHours(properties.getProcessing().getPatternDetection().getAnalysisWindowHours());
        List<UUID> activeUsers = eventRepository.findActiveUsersSince(cutoff).stream()
            .filter(userId -> userId != null)
            .distinct()
            .toList();
        if (activeUsers.isEmpty()) {
            return;
        }
        log.debug("Running pattern detection for {} active users", activeUsers.size());
        activeUsers.forEach(userId -> {
            try {
                analysisService.analyze(userId);
            } catch (Exception ex) {
                log.warn("Pattern analysis failed for user {}: {}", userId, ex.getMessage());
            }
        });
    }
}
