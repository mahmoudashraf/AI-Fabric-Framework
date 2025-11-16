package com.ai.behavior.service;

import com.ai.behavior.repository.BehaviorEmbeddingRepository;
import com.ai.behavior.repository.BehaviorEventRepository;
import com.ai.behavior.repository.BehaviorInsightsRepository;
import com.ai.behavior.repository.BehaviorMetricsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BehaviorDeletionService {

    private final BehaviorEventRepository eventRepository;
    private final BehaviorInsightsRepository insightsRepository;
    private final BehaviorMetricsRepository metricsRepository;
    private final BehaviorEmbeddingRepository embeddingRepository;
    private final BehaviorInsightsService insightsService;

    @Transactional
    public int deleteUserBehaviors(UUID userId) {
        List<UUID> eventIds = eventRepository.findIdsByUserId(userId);
        if (!eventIds.isEmpty()) {
            embeddingRepository.deleteByBehaviorEventIdIn(eventIds);
        }
        eventRepository.deleteByUserId(userId);
        insightsRepository.deleteByUserId(userId);
        metricsRepository.deleteByUserId(userId);
        insightsService.evictUserInsights(userId);
        return eventIds.size();
    }
}
