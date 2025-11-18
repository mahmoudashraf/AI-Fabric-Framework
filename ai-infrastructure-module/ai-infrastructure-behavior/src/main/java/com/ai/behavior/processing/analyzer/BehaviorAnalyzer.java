package com.ai.behavior.processing.analyzer;

import com.ai.behavior.model.BehaviorSignal;
import com.ai.behavior.model.BehaviorInsights;

import java.util.List;
import java.util.UUID;

public interface BehaviorAnalyzer {

    BehaviorInsights analyze(UUID userId, List<BehaviorSignal> events);

    String getAnalyzerType();

    default boolean supports(List<String> eventTypes) {
        return true;
    }
}
