package com.ai.behavior.ingestion;

import com.ai.behavior.model.BehaviorSignal;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Lightweight in-memory tracker that records ingestion activity for sinks that
 * do not persist events to the relational store (e.g., Kafka, Redis, S3).
 */
@Component
public class BehaviorIngestionMetrics {

    private final AtomicLong totalIngested = new AtomicLong();
    private final ConcurrentLinkedQueue<Instant> ingestedInstants = new ConcurrentLinkedQueue<>();

    public void record(BehaviorSignal event) {
        if (event == null) {
            return;
        }
        totalIngested.incrementAndGet();
        ingestedInstants.add(toInstant(event.getIngestedAt()));
    }

    public void recordBatch(List<BehaviorSignal> events) {
        if (events == null || events.isEmpty()) {
            return;
        }
        events.forEach(this::record);
    }

    public long totalCount() {
        return totalIngested.get();
    }

    public long countInLast(Duration window) {
        if (window == null || window.isNegative() || window.isZero()) {
            return 0;
        }
        Instant cutoff = Instant.now().minus(window);
        prune(cutoff);
        return ingestedInstants.size();
    }

    private void prune(Instant cutoff) {
        while (true) {
            Instant head = ingestedInstants.peek();
            if (head == null || head.isAfter(cutoff)) {
                break;
            }
            ingestedInstants.poll();
        }
    }

    private Instant toInstant(LocalDateTime ingestedAt) {
        LocalDateTime timestamp = Optional.ofNullable(ingestedAt).orElse(LocalDateTime.now());
        return timestamp.atZone(ZoneId.systemDefault()).toInstant();
    }
}
