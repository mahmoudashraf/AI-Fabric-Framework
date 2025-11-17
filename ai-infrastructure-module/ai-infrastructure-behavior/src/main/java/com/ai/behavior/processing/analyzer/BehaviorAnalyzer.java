package com.ai.behavior.processing.analyzer;

import com.ai.behavior.model.BehaviorEvent;
import com.ai.behavior.model.BehaviorInsights;

import java.util.List;
import java.util.UUID;

public interface BehaviorAnalyzer {

    BehaviorInsights analyze(UUID userId, List<BehaviorEvent> events);

    String getAnalyzerType();

    default boolean supports(List<String> eventTypes) {
        return true;
    }
}
