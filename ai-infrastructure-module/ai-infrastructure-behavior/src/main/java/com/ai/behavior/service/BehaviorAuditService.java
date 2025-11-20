package com.ai.behavior.service;

import com.ai.behavior.model.BehaviorEventEntity;
import com.ai.behavior.model.BehaviorInsights;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BehaviorAuditService {

    private final ObjectMapper objectMapper;

    public void logEventIngested(BehaviorEventEntity event) {
        log.info("behavior_audit event=ingested payload={}", toJson(Map.of(
            "eventId", event.getId(),
            "userId", event.getUserId(),
            "eventType", event.getEventType(),
            "source", event.getSource()
        )));
    }

    public void logBatchIngested(List<BehaviorEventEntity> events) {
        log.info("behavior_audit event=batch_ingested payload={}", toJson(Map.of(
            "count", events.size(),
            "userIds", events.stream().map(BehaviorEventEntity::getUserId).distinct().toList()
        )));
    }

    public void logAnalysisCompleted(UUID userId, BehaviorInsights insights) {
        log.info("behavior_audit event=analysis_completed payload={}", toJson(Map.of(
            "userId", userId,
            "segment", insights.getSegment(),
            "confidence", insights.getScores() != null ? insights.getScores().getOrDefault("confidenceScore", 0.0) : 0.0
        )));
    }

    public void logQueryExecuted(String query, boolean success, int matches) {
        log.info("behavior_audit event=query_executed payload={}", toJson(Map.of(
            "query", query,
            "success", success,
            "matches", matches
        )));
    }

    public void logPiiDetection(String query) {
        log.warn("behavior_audit event=pii_detected payload={}", toJson(Map.of(
            "query", query
        )));
    }

    private String toJson(Map<String, ?> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            return payload.toString();
        }
    }
}
