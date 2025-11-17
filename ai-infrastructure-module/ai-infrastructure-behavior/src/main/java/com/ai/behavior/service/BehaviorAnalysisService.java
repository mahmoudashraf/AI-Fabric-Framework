package com.ai.behavior.service;

import com.ai.behavior.exception.BehaviorAnalysisException;
import com.ai.behavior.model.BehaviorEvent;
import com.ai.behavior.model.BehaviorInsights;
import com.ai.behavior.processing.analyzer.PatternAnalyzer;
import com.ai.behavior.storage.BehaviorEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BehaviorAnalysisService {

    private final BehaviorEventRepository eventRepository;
    private final PatternAnalyzer patternAnalyzer;

    @Transactional(readOnly = true)
    public BehaviorInsights analyze(UUID userId) {
        try {
            List<BehaviorEvent> events = eventRepository.findTop200ByUserIdOrderByTimestampDesc(userId);
            if (events.isEmpty()) {
                return patternAnalyzer.emptyInsights(userId);
            }
            return patternAnalyzer.analyze(userId, events);
        } catch (Exception ex) {
            throw new BehaviorAnalysisException("Failed to analyze behavior for user " + userId, ex);
        }
    }
}
