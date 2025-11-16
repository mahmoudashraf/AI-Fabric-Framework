package com.ai.behavior.ingestion;

import com.ai.behavior.exception.BehaviorIngestionException;
import com.ai.behavior.ingestion.event.BehaviorEventBatchIngested;
import com.ai.behavior.ingestion.event.BehaviorEventIngested;
import com.ai.behavior.ingestion.validator.BehaviorEventValidator;
import com.ai.behavior.model.BehaviorEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class BehaviorIngestionService {

    private final BehaviorEventValidator validator;
    private final BehaviorEventSink eventSink;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Transactional
    public BehaviorEvent ingest(BehaviorEvent event) {
        try {
            BehaviorEvent processed = prepare(event);
            validator.validate(processed);
            eventSink.accept(processed);
            eventPublisher.publishEvent(new BehaviorEventIngested(processed));
            return processed;
        } catch (Exception ex) {
            throw new BehaviorIngestionException("Failed to ingest behavior event", ex);
        }
    }

    @Transactional
    public List<BehaviorEvent> ingestBatch(List<BehaviorEvent> events) {
        try {
            List<BehaviorEvent> processed = events.stream()
                .map(this::prepare)
                .toList();
            processed.forEach(validator::validate);
            eventSink.acceptBatch(processed);
            eventPublisher.publishEvent(new BehaviorEventBatchIngested(processed));
            return processed;
        } catch (Exception ex) {
            throw new BehaviorIngestionException("Failed to ingest behavior events batch", ex);
        }
    }

    private BehaviorEvent prepare(BehaviorEvent event) {
        event.ensureMetadata();
        if (event.getTimestamp() == null) {
            event.setTimestamp(LocalDateTime.now(clock));
        }
        event.getMetadata().put("_ingested_at", LocalDateTime.now(clock).toString());
        event.getMetadata().putIfAbsent("_version", "1.0");
        if (event.getSessionId() == null && event.getUserId() != null) {
            event.setSessionId("user-" + event.getUserId());
        }
        return event;
    }
}
