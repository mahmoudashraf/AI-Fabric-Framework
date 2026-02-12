package com.ai.infrastructure.relay.ratelimit;

import com.ai.infrastructure.relay.config.RelayProperties;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FixedWindowRateLimiterTest {

    @Test
    void check_enforcesPerUserWindow() {
        RelayProperties props = new RelayProperties();
        props.getRateLimits().getPerUser().setWindowSeconds(10);
        props.getRateLimits().getPerUser().setMaxRequests(2);

        Clock clock = Clock.fixed(Instant.ofEpochSecond(1_700_000_000L), ZoneOffset.UTC);
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(props, clock);

        limiter.check("user1", "a1");
        limiter.check("user1", "a1");

        assertThatThrownBy(() -> limiter.check("user1", "a1"))
            .isInstanceOf(RateLimitedException.class);
    }
}

