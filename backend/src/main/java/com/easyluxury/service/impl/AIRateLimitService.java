package com.easyluxury.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class AIRateLimitService {

    @Value("${openai.rate-limit.requests-per-minute:60}")
    private int requestsPerMinute;

    @Value("${openai.rate-limit.requests-per-hour:1000}")
    private int requestsPerHour;

    private final ConcurrentHashMap<String, AtomicInteger> minuteCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> hourCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LocalDateTime> lastResetMinute = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LocalDateTime> lastResetHour = new ConcurrentHashMap<>();

    public boolean isRateLimited(String userId) {
        String minuteKey = userId + "_minute";
        String hourKey = userId + "_hour";
        
        LocalDateTime now = LocalDateTime.now();
        
        // Check minute rate limit
        if (isMinuteRateLimited(minuteKey, now)) {
            log.warn("User {} is rate limited per minute", userId);
            return true;
        }
        
        // Check hour rate limit
        if (isHourRateLimited(hourKey, now)) {
            log.warn("User {} is rate limited per hour", userId);
            return true;
        }
        
        return false;
    }

    private boolean isMinuteRateLimited(String key, LocalDateTime now) {
        LocalDateTime lastReset = lastResetMinute.get(key);
        
        if (lastReset == null || now.isAfter(lastReset.plusMinutes(1))) {
            // Reset counter
            minuteCounters.put(key, new AtomicInteger(0));
            lastResetMinute.put(key, now);
            return false;
        }
        
        int currentCount = minuteCounters.computeIfAbsent(key, k -> new AtomicInteger(0)).incrementAndGet();
        return currentCount > requestsPerMinute;
    }

    private boolean isHourRateLimited(String key, LocalDateTime now) {
        LocalDateTime lastReset = lastResetHour.get(key);
        
        if (lastReset == null || now.isAfter(lastReset.plusHours(1))) {
            // Reset counter
            hourCounters.put(key, new AtomicInteger(0));
            lastResetHour.put(key, now);
            return false;
        }
        
        int currentCount = hourCounters.computeIfAbsent(key, k -> new AtomicInteger(0)).incrementAndGet();
        return currentCount > requestsPerHour;
    }

    public void recordRequest(String userId) {
        String minuteKey = userId + "_minute";
        String hourKey = userId + "_hour";
        
        minuteCounters.computeIfAbsent(minuteKey, k -> new AtomicInteger(0)).incrementAndGet();
        hourCounters.computeIfAbsent(hourKey, k -> new AtomicInteger(0)).incrementAndGet();
        
        log.debug("Recorded AI request for user: {}", userId);
    }
}