package com.ai.fabric.platform.backend.security.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PlatformLoginRateLimiter {

    private static final int MAX_FAILURES = 5;
    private static final Duration WINDOW = Duration.ofMinutes(15);

    private final Map<String, LoginAttemptWindow> failuresByKey = new ConcurrentHashMap<>();

    public void checkAllowed(String email, String clientAddress) {
        LoginAttemptWindow window = failuresByKey.get(key(email, clientAddress));
        if (window == null) {
            return;
        }

        Instant now = Instant.now();
        if (window.expiresAt().isBefore(now)) {
            failuresByKey.remove(key(email, clientAddress), window);
            return;
        }
        if (window.failures() >= MAX_FAILURES) {
            throw new ResponseStatusException(
                HttpStatus.TOO_MANY_REQUESTS,
                "Too many failed login attempts. Please wait and try again."
            );
        }
    }

    public void recordFailure(String email, String clientAddress) {
        String key = key(email, clientAddress);
        Instant now = Instant.now();
        failuresByKey.compute(key, (ignored, existing) -> {
            if (existing == null || existing.expiresAt().isBefore(now)) {
                return new LoginAttemptWindow(1, now.plus(WINDOW));
            }
            return new LoginAttemptWindow(existing.failures() + 1, existing.expiresAt());
        });
    }

    public void recordSuccess(String email, String clientAddress) {
        failuresByKey.remove(key(email, clientAddress));
    }

    private String key(String email, String clientAddress) {
        return (email == null ? "" : email.trim().toLowerCase()) + "|" + (clientAddress == null ? "" : clientAddress.trim());
    }

    private record LoginAttemptWindow(int failures, Instant expiresAt) {
    }
}
