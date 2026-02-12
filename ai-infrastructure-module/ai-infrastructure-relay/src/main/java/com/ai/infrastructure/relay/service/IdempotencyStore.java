package com.ai.infrastructure.relay.service;

import com.ai.infrastructure.relay.api.ActionExecuteRequestDto;
import com.ai.infrastructure.relay.api.ActionResultDto;
import com.ai.infrastructure.relay.config.RelayProperties;
import com.ai.infrastructure.relay.error.RelayRequestRejectedException;
import com.ai.infrastructure.relay.util.Hashing;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

@Service
public class IdempotencyStore {

    private static final String ERROR_IDEMPOTENCY_CONFLICT = "IDEMPOTENCY_CONFLICT";

    private final RelayProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();

    public IdempotencyStore(RelayProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, Clock.systemUTC());
    }

    IdempotencyStore(RelayProperties properties, ObjectMapper objectMapper, Clock clock) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    public ActionResultDto executeIdempotent(ActionExecuteRequestDto request, Supplier<ActionResultDto> executor) {
        RelayProperties.Idempotency cfg = properties != null ? properties.getIdempotency() : null;
        if (cfg == null || !cfg.isEnabled()) {
            return executor.get();
        }

        String key = request != null ? request.idempotencyKey() : null;
        if (!StringUtils.hasText(key)) {
            return executor.get();
        }

        String fingerprint = fingerprint(request);
        long now = Instant.now(clock).getEpochSecond();
        int ttl = Math.max(60, cfg.getTtlSeconds());
        long expiresAt = now + ttl;

        Entry existing = entries.get(key);
        if (existing != null && existing.expiresAtEpochSeconds > now) {
            if (!existing.fingerprint.equals(fingerprint)) {
                throw new RelayRequestRejectedException(HttpStatus.CONFLICT, ERROR_IDEMPOTENCY_CONFLICT, "Idempotency conflict.");
            }
            return existing.result;
        }
        if (existing != null) {
            entries.remove(key);
        }

        ActionResultDto result = executor.get();
        if (result != null) {
            entries.put(key, new Entry(fingerprint, expiresAt, result));
        }

        cleanupIfNeeded(now);
        return result;
    }

    private void cleanupIfNeeded(long nowEpochSeconds) {
        if (entries.size() < 10_000) {
            return;
        }
        entries.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue().expiresAtEpochSeconds <= nowEpochSeconds);
    }

    private String fingerprint(ActionExecuteRequestDto request) {
        String actionId = request != null && StringUtils.hasText(request.actionId()) ? request.actionId().trim() : "";
        Map<String, Object> params = request != null && request.params() != null ? request.params() : Map.of();
        try {
            String canonicalParams = objectMapper.writeValueAsString(params);
            return Hashing.sha256Hex(actionId + "\n" + canonicalParams);
        } catch (Exception ex) {
            return Hashing.sha256Hex(actionId + "\n" + params.toString());
        }
    }

    private record Entry(String fingerprint, long expiresAtEpochSeconds, ActionResultDto result) {
    }
}
