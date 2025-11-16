package com.ai.behavior.service;

import com.ai.behavior.api.dto.BehaviorHealthResponse;
import com.ai.behavior.ingestion.BehaviorEventSink;
import com.ai.behavior.storage.BehaviorEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class BehaviorMonitoringService {

    private final BehaviorEventRepository eventRepository;
    private final BehaviorEventSink eventSink;

    @Transactional(readOnly = true)
    public BehaviorHealthResponse health() {
        long totalEvents = eventRepository.count();
        long recentEvents = eventRepository.countByIngestedAtAfter(LocalDateTime.now().minusMinutes(5));
        boolean healthy = recentEvents > 0;
        String message = healthy ? "ingestion_active" : "no_recent_events";
        return BehaviorHealthResponse.builder()
            .totalEvents(totalEvents)
            .recentEvents(recentEvents)
            .sinkType(eventSink.getSinkType())
            .healthy(healthy)
            .message(message)
            .build();
    }
}
