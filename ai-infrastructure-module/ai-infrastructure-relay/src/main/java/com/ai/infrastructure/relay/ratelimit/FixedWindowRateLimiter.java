package com.ai.infrastructure.relay.ratelimit;

import com.ai.infrastructure.relay.config.RelayProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class FixedWindowRateLimiter {

    private final RelayProperties properties;
    private final Clock clock;
    private final ConcurrentHashMap<String, WindowCounter> counters = new ConcurrentHashMap<>();

    public FixedWindowRateLimiter(RelayProperties properties) {
        this(properties, Clock.systemUTC());
    }

    FixedWindowRateLimiter(RelayProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    public void check(String userKey, String actionId) {
        String user = StringUtils.hasText(userKey) ? userKey.trim() : "unknown";
        String action = StringUtils.hasText(actionId) ? actionId.trim() : "unknown";
        checkWindow("user:" + user, perUserConfig());
        checkWindow("action:" + user + ":" + action, perActionConfig(action));
    }

    private void checkWindow(String key, RelayProperties.Window window) {
        if (window == null) {
            return;
        }
        int windowSeconds = Math.max(1, window.getWindowSeconds());
        int maxRequests = Math.max(1, window.getMaxRequests());

        long now = Instant.now(clock).getEpochSecond();
        counters.compute(key, (k, existing) -> {
            WindowCounter counter = existing != null ? existing : new WindowCounter(now, 0);
            if (now >= counter.windowStartEpochSeconds + windowSeconds) {
                counter = new WindowCounter(now, 0);
            }

            int next = counter.count + 1;
            if (next > maxRequests) {
                long retryAfter = (counter.windowStartEpochSeconds + windowSeconds) - now;
                throw new RateLimitedException((int) Math.max(1, retryAfter));
            }
            return new WindowCounter(counter.windowStartEpochSeconds, next);
        });
    }

    private RelayProperties.Window perUserConfig() {
        RelayProperties.RateLimits rateLimits = properties != null ? properties.getRateLimits() : null;
        return rateLimits != null ? rateLimits.getPerUser() : null;
    }

    private RelayProperties.Window perActionConfig(String actionId) {
        RelayProperties.RateLimits rateLimits = properties != null ? properties.getRateLimits() : null;
        Map<String, RelayProperties.Window> perAction = rateLimits != null ? rateLimits.getPerAction() : null;
        if (perAction == null || perAction.isEmpty() || !StringUtils.hasText(actionId)) {
            return null;
        }
        return perAction.entrySet().stream()
            .filter(entry -> entry.getKey() != null && entry.getKey().equals(actionId))
            .map(Map.Entry::getValue)
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);
    }

    private record WindowCounter(long windowStartEpochSeconds, int count) {
    }
}

