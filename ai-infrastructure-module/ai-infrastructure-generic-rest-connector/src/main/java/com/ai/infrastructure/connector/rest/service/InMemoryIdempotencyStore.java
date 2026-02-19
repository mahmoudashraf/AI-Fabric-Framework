package com.ai.infrastructure.connector.rest.service;

import com.ai.infrastructure.connector.rest.api.ActionExecuteRequestDto;
import com.ai.infrastructure.connector.rest.api.ActionResultDto;
import com.ai.infrastructure.connector.rest.config.RestRoutingConfig;
import com.ai.infrastructure.connector.rest.util.Hashing;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Service
public class InMemoryIdempotencyStore {

    private static final String ERROR_IDEMPOTENCY_CONFLICT = "IDEMPOTENCY_CONFLICT";
    private static final String ERROR_IDEMPOTENCY_IN_PROGRESS = "IDEMPOTENCY_IN_PROGRESS";
    private static final String ERROR_SERVICE_UNAVAILABLE = "SERVICE_UNAVAILABLE";

    private final RestRoutingConfig config;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, StoredEntry> entries = new ConcurrentHashMap<>();

    public InMemoryIdempotencyStore(RestRoutingConfig config, ObjectMapper objectMapper) {
        this.config = config;
        this.objectMapper = objectMapper;
    }

    public ActionResultDto executeIdempotent(ActionExecuteRequestDto request, Supplier<ActionResultDto> executor) {
        RestRoutingConfig.Idempotency cfg = config != null && config.getConnector() != null ? config.getConnector().getIdempotency() : null;
        if (cfg == null || !cfg.isEnabled()) {
            return executor.get();
        }

        String idempotencyKey = request != null ? request.idempotencyKey() : null;
        if (!StringUtils.hasText(idempotencyKey)) {
            return executor.get();
        }

        String storageKey = "idempotency:" + Hashing.sha256Hex(idempotencyKey.trim());
        String fingerprint = fingerprint(request);

        long now = System.currentTimeMillis();
        cleanupExpired(now);

        long ttlMs = Math.max(60, cfg.getTtlSeconds()) * 1000L;
        long expiresAt = now + ttlMs;

        for (int attempt = 0; attempt < 2; attempt++) {
            StoredEntry existing = entries.get(storageKey);

            if (existing == null || existing.isExpired(now)) {
                StoredEntry created = StoredEntry.inProgress(fingerprint, expiresAt);
                boolean won = existing == null
                    ? entries.putIfAbsent(storageKey, created) == null
                    : entries.replace(storageKey, existing, created);
                if (won) {
                    try {
                        ActionResultDto result = executor.get();
                        if (result != null) {
                            created.future.complete(result);
                            entries.put(storageKey, created.done());
                        } else {
                            entries.remove(storageKey);
                        }
                        return result != null ? result : ActionResultDto.failure(ERROR_SERVICE_UNAVAILABLE, "Internal service returned no result.");
                    } catch (Exception ex) {
                        entries.remove(storageKey);
                        throw ex;
                    }
                }
                continue;
            }

            if (!Objects.equals(fingerprint, existing.fingerprint)) {
                return ActionResultDto.failure(ERROR_IDEMPOTENCY_CONFLICT, "Idempotency conflict.");
            }

            if (existing.status == Status.DONE && existing.future.isDone()) {
                ActionResultDto result = existing.future.getNow(null);
                return result != null ? result : ActionResultDto.failure(ERROR_SERVICE_UNAVAILABLE, "Internal service returned no result.");
            }

            return waitForInProgress(cfg, existing);
        }

        return ActionResultDto.failure(ERROR_SERVICE_UNAVAILABLE, "Idempotency store unavailable.");
    }

    private ActionResultDto waitForInProgress(RestRoutingConfig.Idempotency cfg, StoredEntry entry) {
        int maxWaitMs = cfg != null ? cfg.getInProgressMaxWaitMs() : 0;
        if (maxWaitMs <= 0) {
            return ActionResultDto.failure(ERROR_IDEMPOTENCY_IN_PROGRESS, "Idempotency key is in progress.");
        }

        try {
            ActionResultDto result = entry.future.get(maxWaitMs, TimeUnit.MILLISECONDS);
            return result != null ? result : ActionResultDto.failure(ERROR_SERVICE_UNAVAILABLE, "Internal service returned no result.");
        } catch (Exception ex) {
            return ActionResultDto.failure(ERROR_IDEMPOTENCY_IN_PROGRESS, "Idempotency key is in progress.");
        }
    }

    private void cleanupExpired(long now) {
        if (entries.isEmpty()) {
            return;
        }
        entries.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue().isExpired(now));
    }

    private String fingerprint(ActionExecuteRequestDto request) {
        String actionId = request != null && StringUtils.hasText(request.actionId()) ? request.actionId().trim() : "";
        Map<String, Object> params = request != null && request.params() != null ? request.params() : Map.of();
        try {
            String canonical = objectMapper != null ? objectMapper.writeValueAsString(params) : params.toString();
            return Hashing.sha256Hex(actionId + "\n" + canonical);
        } catch (Exception ex) {
            return Hashing.sha256Hex(actionId + "\n" + params);
        }
    }

    private enum Status {
        IN_PROGRESS,
        DONE
    }

    private static final class StoredEntry {
        private final Status status;
        private final String fingerprint;
        private final CompletableFuture<ActionResultDto> future;
        private final long expiresAt;

        private StoredEntry(Status status, String fingerprint, CompletableFuture<ActionResultDto> future, long expiresAt) {
            this.status = status;
            this.fingerprint = fingerprint;
            this.future = future;
            this.expiresAt = expiresAt;
        }

        static StoredEntry inProgress(String fingerprint, long expiresAt) {
            return new StoredEntry(Status.IN_PROGRESS, fingerprint, new CompletableFuture<>(), expiresAt);
        }

        StoredEntry done() {
            return new StoredEntry(Status.DONE, fingerprint, future, expiresAt);
        }

        boolean isExpired(long now) {
            return expiresAt > 0 && now >= expiresAt;
        }
    }
}

