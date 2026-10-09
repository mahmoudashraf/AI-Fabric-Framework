package com.ai.fabric.platform.backend.aiworkspace.service;

import com.ai.fabric.platform.backend.config.PlatformAIWorkspaceProperties;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AIWorkspaceManifestRateLimiter {

    private static final int MAX_WINDOWS = 20_000;

    private final int limit;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public AIWorkspaceManifestRateLimiter(PlatformAIWorkspaceProperties properties) {
        this.limit = properties.manifestRequestsPerMinute();
    }

    public boolean allow(String key) {
        long minute = Instant.now().getEpochSecond() / 60;
        if (!windows.containsKey(key) && windows.size() >= MAX_WINDOWS) {
            windows.entrySet().removeIf(entry -> entry.getValue().minute < minute);
            if (windows.size() >= MAX_WINDOWS) return false;
        }
        Window window = windows.compute(key, (ignored, current) -> {
            if (current == null || current.minute != minute) return new Window(minute, 1);
            return new Window(minute, current.count + 1);
        });
        if (windows.size() > MAX_WINDOWS) {
            windows.entrySet().removeIf(entry -> entry.getValue().minute < minute - 2);
        }
        return window.count <= limit;
    }

    private record Window(long minute, int count) {
    }
}
