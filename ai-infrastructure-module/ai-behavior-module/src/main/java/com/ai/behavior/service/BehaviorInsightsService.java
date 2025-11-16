package com.ai.behavior.service;

import com.ai.behavior.model.BehaviorInsights;
import com.ai.behavior.repository.BehaviorInsightsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BehaviorInsightsService {

    private final BehaviorInsightsRepository repository;
    private final BehaviorAnalysisService analysisService;
    private final Clock clock;

    @Cacheable(value = "behavior-insights", key = "#userId")
    public BehaviorInsights getUserInsights(UUID userId) {
        return repository.findByUserIdAndValidUntilAfter(userId, LocalDateTime.now(clock))
            .orElseGet(() -> analysisService.analyze(userId));
    }

    @CacheEvict(value = "behavior-insights", key = "#userId")
    public void evictUserInsights(UUID userId) {
        // eviction handled by annotation
    }
}
