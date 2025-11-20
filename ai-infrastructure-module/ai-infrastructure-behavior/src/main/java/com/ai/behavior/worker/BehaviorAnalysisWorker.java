package com.ai.behavior.worker;

import com.ai.behavior.config.BehaviorModuleProperties;
import com.ai.behavior.model.BehaviorEventEntity;
import com.ai.behavior.model.BehaviorEventProcessingStatus;
import com.ai.behavior.repository.BehaviorEventRepository;
import com.ai.behavior.service.BehaviorAnalyzerService;
import com.ai.behavior.service.BehaviorEventIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class BehaviorAnalysisWorker {

    private final BehaviorEventIngestionService ingestionService;
    private final BehaviorAnalyzerService analyzerService;
    private final BehaviorModuleProperties properties;
    private final BehaviorEventRepository behaviorEventRepository;

    @Scheduled(fixedDelayString = "#{T(java.lang.Long).parseLong('${ai.behavior.processing.worker.delay-seconds:300}') * 1000}")
    public void processUnprocessedEvents() {
        int fetchSize = Math.max(1, properties.getEvents().getBatchSize());
        List<BehaviorEventEntity> events = ingestionService.getUnprocessedEvents(fetchSize);
        if (CollectionUtils.isEmpty(events)) {
            return;
        }

        Map<UUID, List<BehaviorEventEntity>> eventsByUser = events.stream()
            .collect(Collectors.groupingBy(BehaviorEventEntity::getUserId));

        eventsByUser.forEach((userId, userEvents) -> {
            try {
                analyzerService.analyzeUserBehavior(userId, userEvents);
                userEvents.forEach(event ->
                    ingestionService.markProcessed(event.getId(), BehaviorEventProcessingStatus.COMPLETED));
            } catch (Exception ex) {
                log.error("Failed to analyze behavior for user {}", userId, ex);
                userEvents.forEach(event -> handleFailure(event, ex));
            }
        });
    }

    @Transactional
    @Scheduled(cron = "${ai.behavior.retention.cleanup-schedule:0 3 * * *}")
    public void cleanupExpiredEvents() {
        OffsetDateTime cutoff = OffsetDateTime.now();
        long deleted = behaviorEventRepository.deleteByExpiresAtBefore(cutoff);
        if (deleted > 0) {
            log.info("Cleaned up {} expired behavior events", deleted);
        }
    }

    private void handleFailure(BehaviorEventEntity event, Exception ex) {
        int maxRetries = Math.max(1, properties.getProcessing().getWorker().getMaxRetries());
        if (event.getRetryCount() + 1 >= maxRetries) {
            ingestionService.markProcessed(event.getId(), BehaviorEventProcessingStatus.FAILED);
            log.warn("Event {} exceeded retry attempts and was marked as FAILED", event.getId());
        } else {
            ingestionService.markFailed(event.getId(), ex.getMessage());
        }
    }
}
