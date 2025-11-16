package com.ai.behavior.service;

import com.ai.behavior.exception.BehaviorAnalysisException;
import com.ai.behavior.model.BehaviorInsights;
import com.ai.behavior.storage.BehaviorInsightsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BehaviorInsightsService {

    private final BehaviorInsightsRepository insightsRepository;
    private final BehaviorAnalysisService analysisService;

    @Transactional(readOnly = true)
    public BehaviorInsights getUserInsights(UUID userId) {
        return insightsRepository.findTopByUserIdOrderByAnalyzedAtDesc(userId)
            .filter(BehaviorInsights::isValid)
            .orElseGet(() -> refreshInsights(userId));
    }

    @Transactional
    public BehaviorInsights refreshInsights(UUID userId) {
        log.debug("Refreshing behavior insights for user {}", userId);
        BehaviorInsights insights = analysisService.analyze(userId);
        return insightsRepository.save(insights);
    }
}
