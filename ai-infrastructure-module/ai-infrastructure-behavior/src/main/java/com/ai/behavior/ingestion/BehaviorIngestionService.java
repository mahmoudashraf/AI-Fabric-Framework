package com.ai.behavior.ingestion;

import com.ai.behavior.config.BehaviorModuleProperties;
import com.ai.behavior.exception.BehaviorIngestionException;
import com.ai.behavior.ingestion.event.BehaviorSignalBatchIngested;
import com.ai.behavior.ingestion.event.BehaviorSignalIngested;
import com.ai.behavior.model.BehaviorSignal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BehaviorIngestionService {

    private final BehaviorSignalValidator validator;
    private final BehaviorSignalSink sink;
    private final ApplicationEventPublisher eventPublisher;
    private final BehaviorModuleProperties properties;
    private final BehaviorIngestionMetrics ingestionMetrics;

    @Transactional
    public BehaviorSignal ingest(BehaviorSignal event) {
        try {
            validator.validate(event);
            BehaviorSignal enriched = enrich(event);
            sink.accept(enriched);
            ingestionMetrics.record(enriched);
            publishEvent(enriched);
            log.debug("Ingested behavior signal {}", enriched.getId());
            return enriched;
        } catch (BehaviorIngestionException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BehaviorIngestionException("Failed to ingest behavior event", ex);
        }
    }

    @Transactional
    public void ingestBatch(List<BehaviorSignal> events) {
        if (events == null || events.isEmpty()) {
            return;
        }
        int maxBatchSize = properties.getIngestion().getMaxBatchSize();
        if (events.size() > maxBatchSize) {
            throw new BehaviorIngestionException("Batch size exceeds limit of " + maxBatchSize);
        }

        try {
            events.forEach(validator::validate);
            List<BehaviorSignal> enriched = events.stream().map(this::enrich).toList();
            sink.acceptBatch(enriched);
            ingestionMetrics.recordBatch(enriched);
            publishBatch(enriched);
            log.info("Ingested batch of {} behavior events", enriched.size());
        } catch (BehaviorIngestionException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BehaviorIngestionException("Failed to ingest behavior batch", ex);
        }
    }

    private BehaviorSignal enrich(BehaviorSignal event) {
        if (event.getId() == null) {
            event.setId(UUID.randomUUID());
        }
        if (event.getVersion() == null) {
            event.setVersion("1.0");
        }
        if (event.getTimestamp() == null) {
            event.setTimestamp(LocalDateTime.now());
        }
        if (event.getIngestedAt() == null) {
            event.setIngestedAt(LocalDateTime.now());
        }
        event.safeAttributes().put("_ingested_at", event.getIngestedAt().toString());
        event.safeAttributes().put("_version", event.getVersion());
        return event;
    }

    private void publishEvent(BehaviorSignal event) {
        if (!properties.getIngestion().isPublishApplicationEvents()) {
            return;
        }
        eventPublisher.publishEvent(new BehaviorSignalIngested(event));
    }

    private void publishBatch(List<BehaviorSignal> events) {
        if (!properties.getIngestion().isPublishApplicationEvents()) {
            return;
        }
        eventPublisher.publishEvent(new BehaviorSignalBatchIngested(events));
    }
}
