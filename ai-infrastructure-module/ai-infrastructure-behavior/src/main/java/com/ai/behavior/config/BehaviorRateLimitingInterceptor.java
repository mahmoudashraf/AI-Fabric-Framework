package com.ai.behavior.config;

import com.ai.behavior.config.BehaviorModuleProperties.Security.RateLimiting;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Bucket4j;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
public class BehaviorRateLimitingInterceptor implements HandlerInterceptor {

    private static final String USER_HEADER = "X-User-Id";

    private final BehaviorModuleProperties properties;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        BehaviorModuleProperties.Security security = properties.getSecurity();
        RateLimiting config = security != null ? security.getRateLimiting() : null;
        if (config == null || !config.isEnabled()) {
            return true;
        }

        if (!requiresRateLimit(handlerMethod)) {
            return true;
        }

        String key = resolveKey(request);
        Bucket bucket = buckets.computeIfAbsent(key, __ -> buildBucket(config));
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            response.setHeader("X-Rate-Limit-Remaining", String.valueOf(probe.getRemainingTokens()));
            return true;
        }

        reject(response, probe.getNanosToWaitForRefill());
        return false;
    }

    private boolean requiresRateLimit(HandlerMethod method) {
        return method.hasMethodAnnotation(RateLimitCheck.class)
            || method.getBeanType().isAnnotationPresent(RateLimitCheck.class);
    }

    private String resolveKey(HttpServletRequest request) {
        String headerUser = request.getHeader(USER_HEADER);
        if (headerUser != null && !headerUser.isBlank()) {
            return headerUser;
        }
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "anonymous";
    }

    private Bucket buildBucket(RateLimiting config) {
        long tokens = Math.max(1, config.getRequests());
        Duration period = config.getRefreshPeriod() != null ? config.getRefreshPeriod() : Duration.ofHours(1);
        Bandwidth limit = Bandwidth.classic(tokens, Refill.greedy(tokens, period));
        return Bucket4j.builder()
            .addLimit(limit)
            .build();
    }

    private void reject(HttpServletResponse response, long nanosToWait) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("""
            {"error":"rate_limit_exceeded","retryAfterNanos":%d}
            """.formatted(nanosToWait));
        response.flushBuffer();
    }
}
