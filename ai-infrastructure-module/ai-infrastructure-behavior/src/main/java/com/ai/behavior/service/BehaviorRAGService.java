package com.ai.behavior.service;

import com.ai.behavior.model.BehaviorSignal;
import com.ai.behavior.model.BehaviorInsights;
import com.ai.behavior.storage.BehaviorDataProvider;
import com.ai.infrastructure.dto.RAGRequest;
import com.ai.infrastructure.dto.RAGResponse;
import com.ai.infrastructure.rag.RAGService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BehaviorRAGService {

    private final RAGService ragService;
    private final BehaviorDataProvider dataProvider;
    private final BehaviorInsightsService insightsService;

    public String explainUserBehavior(UUID userId, String question) {
        List<BehaviorSignal> events = dataProvider.getRecentEvents(userId, 100);
        BehaviorInsights insights = insightsService.getUserInsights(userId);
        String context = buildContext(events, insights);
        RAGRequest request = RAGRequest.builder()
            .query(question)
            .entityType("behavior_event")
            .context(java.util.Map.of("behavior_summary", context))
            .userId(userId.toString())
            .build();
        RAGResponse response = ragService.performRAGQuery(request);
        return response.getResponse();
    }

    private String buildContext(List<BehaviorSignal> events, BehaviorInsights insights) {
        StringBuilder builder = new StringBuilder();
        builder.append("Segment: ").append(insights.getSegment()).append("\n");
        builder.append("Patterns: ").append(String.join(", ", insights.getPatterns())).append("\n");
        builder.append("Scores: ").append(insights.getScores()).append("\n");
        builder.append("Preferences: ").append(insights.getPreferences()).append("\n\n");
        builder.append("Recent activity:\n");
        var grouped = events.stream()
            .collect(Collectors.groupingBy(BehaviorSignal::getSchemaId, Collectors.counting()));
        grouped.forEach((type, count) -> builder.append("- ").append(type).append(": ").append(count).append("\n"));
        return builder.toString();
    }
}
