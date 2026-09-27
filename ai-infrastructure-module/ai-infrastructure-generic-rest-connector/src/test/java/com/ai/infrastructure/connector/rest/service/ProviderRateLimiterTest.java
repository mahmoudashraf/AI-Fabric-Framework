package com.ai.infrastructure.connector.rest.service;

import com.ai.infrastructure.connector.rest.config.RestRoutingConfig;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class ProviderRateLimiterTest {

    @Test
    void reservesMinimumIntervalsAcrossSequentialRequests() {
        ProviderRateLimiter limiter = new ProviderRateLimiter(Clock.systemUTC());
        RestRoutingConfig.RatePolicy policy = new RestRoutingConfig.RatePolicy();
        policy.setMaxConcurrent(2);
        policy.setMinIntervalMs(80);

        try (ProviderRateLimiter.Lease ignored = limiter.acquire("neutral-profile", policy)) {
            // Reservation occurs when the lease is acquired.
        }
        long started = System.nanoTime();
        try (ProviderRateLimiter.Lease ignored = limiter.acquire("neutral-profile", policy)) {
            // Wait is asserted after acquisition.
        }

        assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)).isGreaterThanOrEqualTo(50);
    }

    @Test
    void appliesConfiguredProviderPauseBeforeTheNextRequest() {
        ProviderRateLimiter limiter = new ProviderRateLimiter(Clock.systemUTC());
        RestRoutingConfig.RatePolicy policy = new RestRoutingConfig.RatePolicy();
        policy.setRateLimitedPauseMs(80);
        limiter.pause("neutral-profile", policy, 429);

        long started = System.nanoTime();
        try (ProviderRateLimiter.Lease ignored = limiter.acquire("neutral-profile", policy)) {
            // Wait is asserted after acquisition.
        }

        assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)).isGreaterThanOrEqualTo(50);
    }
}
