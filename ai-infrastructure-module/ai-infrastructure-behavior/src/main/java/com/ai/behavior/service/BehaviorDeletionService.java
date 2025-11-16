package com.ai.behavior.service;

import com.ai.behavior.model.BehaviorEvent;
import com.ai.behavior.storage.BehaviorAlertRepository;
import com.ai.behavior.storage.BehaviorEmbeddingRepository;
import com.ai.behavior.storage.BehaviorEventRepository;
import com.ai.behavior.storage.BehaviorInsightsRepository;
import com.ai.behavior.storage.BehaviorMetricsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BehaviorDeletionService implements com.ai.infrastructure.deletion.port.BehaviorDeletionPort {

    private final BehaviorEventRepository eventRepository;
    private final BehaviorEmbeddingRepository embeddingRepository;
    private final BehaviorInsightsRepository insightsRepository;
    private final BehaviorMetricsRepository metricsRepository;
    private final BehaviorAlertRepository alertRepository;

    @Transactional
    @Override
    public int deleteUserBehaviors(UUID userId) {
        List<UUID> eventIds = eventRepository.findByUserIdOrderByTimestampDesc(userId).stream()
            .map(BehaviorEvent::getId)
            .toList();
        if (!eventIds.isEmpty()) {
            embeddingRepository.deleteByBehaviorEventIdIn(eventIds);
        }
        alertRepository.deleteByUserId(userId);
        metricsRepository.deleteByUserId(userId);
        insightsRepository.deleteByUserId(userId);
        eventRepository.deleteByUserId(userId);
        log.info("Deleted behavior data for user {} ({} events)", userId, eventIds.size());
        return eventIds.size();
    }
}
