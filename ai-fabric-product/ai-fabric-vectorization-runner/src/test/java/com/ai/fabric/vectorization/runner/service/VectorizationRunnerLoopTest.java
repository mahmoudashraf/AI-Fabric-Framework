package com.ai.fabric.vectorization.runner.service;

import com.ai.fabric.vectorization.runner.config.VectorizationRunnerProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VectorizationRunnerLoopTest {

    @Test
    void idleLoopRenewsSessionHeartbeatWhenNoRunIsClaimed() throws Exception {
        VectorizationRunnerProperties properties = new VectorizationRunnerProperties(
            "https://platform.example",
            "registration-token",
            "runner-a",
            "dep-1",
            "2026.04.track-b",
            "1",
            Duration.ofMillis(25),
            Duration.ofSeconds(5)
        );
        VectorizationRunnerPlatformClient platformClient = mock(VectorizationRunnerPlatformClient.class);
        VectorizationRunExecutor runExecutor = mock(VectorizationRunExecutor.class);

        when(platformClient.isConfigured()).thenReturn(true);
        when(platformClient.createSession()).thenReturn(
            new VectorizationRunnerPlatformClient.RunnerSession("session-1", "dep-1", "PLATFORM_MANAGED_AUTO", "CURRENT")
        );
        when(platformClient.claimNextRun("session-1")).thenReturn(null);
        when(platformClient.heartbeat("session-1", null)).thenReturn(
            new VectorizationRunnerPlatformClient.HeartbeatDecision("RUNNING")
        );

        VectorizationRunnerLoop loop = new VectorizationRunnerLoop(properties, platformClient, runExecutor);
        try {
            loop.start();
            verify(platformClient, timeout(1000).atLeastOnce()).heartbeat(eq("session-1"), isNull());
        } finally {
            loop.stop();
        }
    }
}
