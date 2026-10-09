package com.ai.fabric.platform.backend.aiworkspace.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class AIWorkspaceObservability {

    private static final Logger log = LoggerFactory.getLogger(AIWorkspaceObservability.class);

    private final MeterRegistry meterRegistry;
    private final AtomicInteger assetsReady = new AtomicInteger();
    private final AtomicReference<String> lastAssetFailure = new AtomicReference<>();

    public AIWorkspaceObservability(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        meterRegistry.gauge("loomai.ai_workspace.assets.ready", assetsReady);
    }

    public void recordPublicRequest(String surface, String outcome, long startedAtNanos) {
        String safeSurface = boundedSurface(surface);
        String safeOutcome = boundedOutcome(outcome);
        Counter.builder("loomai.ai_workspace.public.requests")
            .description("AI Workspace public delivery requests")
            .tag("surface", safeSurface)
            .tag("outcome", safeOutcome)
            .register(meterRegistry)
            .increment();
        Timer.builder("loomai.ai_workspace.public.request.duration")
            .description("AI Workspace public delivery latency")
            .tag("surface", safeSurface)
            .tag("outcome", safeOutcome)
            .publishPercentileHistogram(false)
            .register(meterRegistry)
            .record(Duration.ofNanos(Math.max(0L, System.nanoTime() - startedAtNanos)));
        if (!"success".equals(safeOutcome) && !"not_modified".equals(safeOutcome)) {
            log.warn("AI Workspace public request outcome: surface={}, outcome={}", safeSurface, safeOutcome);
        }
    }

    public void recordAssetCatalog(boolean ready, String status) {
        assetsReady.set(ready ? 1 : 0);
        String safeStatus = boundedStatus(status);
        if (!ready) {
            String previous = lastAssetFailure.getAndSet(safeStatus);
            if (!safeStatus.equals(previous)) {
                log.error("AI Workspace packaged asset catalog is not ready: status={}", safeStatus);
            }
        } else if (lastAssetFailure.getAndSet(null) != null) {
            log.info("AI Workspace packaged asset catalog recovered.");
        }
    }

    private String boundedSurface(String value) {
        return switch (value == null ? "" : value) {
            case "installer", "manifest", "asset" -> value;
            default -> "unknown";
        };
    }

    private String boundedOutcome(String value) {
        return switch (value == null ? "" : value) {
            case "success", "not_modified", "origin_rejected", "rate_limited", "unavailable", "invalid_request", "error" -> value;
            default -> "error";
        };
    }

    private String boundedStatus(String value) {
        if (value == null || value.isBlank()) return "unknown";
        String normalized = value.replaceAll("[^A-Za-z0-9_. -]", "_");
        return normalized.length() <= 160 ? normalized : normalized.substring(0, 160);
    }
}
