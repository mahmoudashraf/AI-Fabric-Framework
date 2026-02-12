package com.ai.infrastructure.relay.security;

import java.time.Clock;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class NonceStore {

    private final Clock clock;
    private final ConcurrentHashMap<String, Long> nonceToExpiresAtEpochSeconds = new ConcurrentHashMap<>();

    NonceStore(Clock clock) {
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    boolean tryUse(String nonce, int ttlSeconds) {
        if (nonce == null || nonce.isBlank()) {
            return false;
        }

        long now = Instant.now(clock).getEpochSecond();
        long expiresAt = now + Math.max(1, ttlSeconds);

        Long existing = nonceToExpiresAtEpochSeconds.putIfAbsent(nonce, expiresAt);
        if (existing != null && existing > now) {
            return false;
        }
        if (existing != null) {
            nonceToExpiresAtEpochSeconds.put(nonce, expiresAt);
        }

        cleanupIfNeeded(now);
        return true;
    }

    private void cleanupIfNeeded(long nowEpochSeconds) {
        if (nonceToExpiresAtEpochSeconds.size() < 10_000) {
            return;
        }
        Iterator<Map.Entry<String, Long>> it = nonceToExpiresAtEpochSeconds.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Long> entry = it.next();
            Long expiresAt = entry.getValue();
            if (expiresAt == null || expiresAt <= nowEpochSeconds) {
                it.remove();
            }
        }
    }
}

