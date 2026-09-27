package com.ai.infrastructure.connector.rest.service;

import com.ai.infrastructure.connector.rest.config.RestRoutingConfig;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

@Service
public class ProviderRateLimiter {

    private final Clock clock;
    private final Map<String, Gate> gates = new ConcurrentHashMap<>();

    public ProviderRateLimiter(Clock clock) {
        this.clock = clock;
    }

    public Lease acquire(String profileId, RestRoutingConfig.RatePolicy policy) {
        RestRoutingConfig.RatePolicy effective = policy != null ? policy : new RestRoutingConfig.RatePolicy();
        Gate gate = gates.computeIfAbsent(profileId, ignored -> new Gate(effective.getMaxConcurrent()));
        try {
            gate.semaphore.acquire();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ProviderCallException(ProviderErrorClass.SERVICE_UNAVAILABLE, 0, "Provider request was interrupted before execution.", ex);
        }
        long delay;
        synchronized (gate) {
            long now = clock.millis();
            long availableAt = Math.max(now, Math.max(gate.nextAllowed, gate.pausedUntil));
            delay = availableAt - now;
            gate.nextAllowed = availableAt + Math.max(0, effective.getMinIntervalMs());
        }
        try {
            sleep(delay);
        } catch (RuntimeException exception) {
            gate.semaphore.release();
            throw exception;
        }
        return gate.semaphore::release;
    }

    public void pause(String profileId, RestRoutingConfig.RatePolicy policy, int status) {
        Gate gate = gates.computeIfAbsent(profileId, ignored -> new Gate(policy != null ? policy.getMaxConcurrent() : 4));
        long pause = status == 429
            ? (policy != null ? policy.getRateLimitedPauseMs() : 30_000)
            : (policy != null ? policy.getUnavailablePauseMs() : 5_000);
        synchronized (gate) {
            gate.pausedUntil = Math.max(gate.pausedUntil, clock.millis() + Math.max(0, pause));
        }
    }

    private void sleep(long delay) {
        try {
            Thread.sleep(Math.min(delay, 3_600_000));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ProviderCallException(ProviderErrorClass.SERVICE_UNAVAILABLE, 0, "Provider request wait was interrupted.", ex);
        }
    }

    private static final class Gate {
        private final Semaphore semaphore;
        private long nextAllowed;
        private long pausedUntil;

        private Gate(int maxConcurrent) {
            this.semaphore = new Semaphore(Math.max(1, Math.min(100, maxConcurrent)), true);
        }
    }

    @FunctionalInterface
    public interface Lease extends AutoCloseable {
        @Override
        void close();
    }
}
