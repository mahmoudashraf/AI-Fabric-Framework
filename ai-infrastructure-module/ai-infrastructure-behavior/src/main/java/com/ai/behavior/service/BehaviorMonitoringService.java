package com.ai.behavior.service;

import com.ai.behavior.api.dto.BehaviorHealthResponse;
import com.ai.behavior.ingestion.BehaviorSignalSink;
import com.ai.behavior.ingestion.BehaviorIngestionMetrics;
import com.ai.behavior.storage.BehaviorSignalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class BehaviorMonitoringService {

    private final BehaviorSignalRepository eventRepository;
    private final BehaviorSignalSink eventSink;
    private final BehaviorIngestionMetrics ingestionMetrics;

    @Transactional(readOnly = true)
    public BehaviorHealthResponse health() {
        String sinkType = eventSink.getSinkType();
        boolean persistentSink = isPersistentSink(sinkType);

        long totalEvents = persistentSink
            ? eventRepository.count()
            : ingestionMetrics.totalCount();

        long recentEvents = persistentSink
            ? eventRepository.countByIngestedAtAfter(LocalDateTime.now().minusMinutes(5))
            : ingestionMetrics.countInLast(Duration.ofMinutes(5));

        boolean healthy = recentEvents > 0;
        String message = healthy ? "ingestion_active" : "no_recent_events";
        return BehaviorHealthResponse.builder()
            .totalEvents(totalEvents)
            .recentEvents(recentEvents)
            .sinkType(sinkType)
            .healthy(healthy)
            .message(message)
            .build();
    }

    private boolean isPersistentSink(String sinkType) {
        if (sinkType == null) {
            return false;
        }
        return "database".equalsIgnoreCase(sinkType) || "hybrid".equalsIgnoreCase(sinkType);
    }
}
