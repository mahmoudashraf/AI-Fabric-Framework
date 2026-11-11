package com.ai.fabric.platform.backend.mcp.connection;

import java.time.Instant;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class McpOAuthAttemptCleanup {
    private final McpOAuthAttemptRepository attempts;
    public McpOAuthAttemptCleanup(McpOAuthAttemptRepository attempts) { this.attempts = attempts; }
    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void clearExpiredMaterial() { attempts.clearExpired(Instant.now()); }
}
