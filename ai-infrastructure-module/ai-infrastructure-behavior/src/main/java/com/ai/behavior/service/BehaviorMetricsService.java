package com.ai.behavior.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class BehaviorMetricsService {

    private final MeterRegistry meterRegistry;
    private Counter ingestionCounter;
    private Counter analysisCounter;
    private Counter searchCounter;
    private Counter piiCounter;
    private Timer analysisTimer;
    private Timer searchTimer;

    private Counter ingestionCounter() {
        if (ingestionCounter == null) {
            ingestionCounter = Counter.builder("ai.behavior.events.ingested")
                .description("Total number of ingested behavior events")
                .register(meterRegistry);
        }
        return ingestionCounter;
    }

    private Counter analysisCounter() {
        if (analysisCounter == null) {
            analysisCounter = Counter.builder("ai.behavior.analysis.count")
                .description("Number of analysis executions")
                .register(meterRegistry);
        }
        return analysisCounter;
    }

    private Counter searchCounter() {
        if (searchCounter == null) {
            searchCounter = Counter.builder("ai.behavior.search.count")
                .description("Number of orchestrated searches executed")
                .register(meterRegistry);
        }
        return searchCounter;
    }

    private Counter piiCounter() {
        if (piiCounter == null) {
            piiCounter = Counter.builder("ai.behavior.pii.detections")
                .description("Number of queries blocked due to PII detection")
                .register(meterRegistry);
        }
        return piiCounter;
    }

    private Timer analysisTimer() {
        if (analysisTimer == null) {
            analysisTimer = Timer.builder("ai.behavior.analysis.latency")
                .description("Latency of analysis pipeline")
                .register(meterRegistry);
        }
        return analysisTimer;
    }

    private Timer searchTimer() {
        if (searchTimer == null) {
            searchTimer = Timer.builder("ai.behavior.search.latency")
                .description("Latency of orchestrated search calls")
                .register(meterRegistry);
        }
        return searchTimer;
    }

    public void incrementEventIngested(int count) {
        if (count > 0) {
            ingestionCounter().increment(count);
        }
    }

    public void incrementPiiDetections() {
        piiCounter().increment();
    }

    public <T> T recordAnalysis(Supplier<T> supplier) {
        Objects.requireNonNull(supplier, "supplier must not be null");
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            T result = supplier.get();
            analysisCounter().increment();
            return result;
        } finally {
            sample.stop(analysisTimer());
        }
    }

    public <T> T recordSearch(Supplier<T> supplier) {
        Objects.requireNonNull(supplier, "supplier must not be null");
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            T result = supplier.get();
            searchCounter().increment();
            return result;
        } finally {
            sample.stop(searchTimer());
        }
    }
}
