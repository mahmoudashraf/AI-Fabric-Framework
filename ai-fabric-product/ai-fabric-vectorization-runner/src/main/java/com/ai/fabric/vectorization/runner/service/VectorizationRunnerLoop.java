package com.ai.fabric.vectorization.runner.service;

import com.ai.fabric.vectorization.runner.config.VectorizationRunnerProperties;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class VectorizationRunnerLoop {

    private static final Logger log = LoggerFactory.getLogger(VectorizationRunnerLoop.class);

    private final VectorizationRunnerProperties properties;
    private final VectorizationRunnerPlatformClient platformClient;
    private final VectorizationRunExecutor runExecutor;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    private volatile boolean running;

    public VectorizationRunnerLoop(VectorizationRunnerProperties properties,
                                   VectorizationRunnerPlatformClient platformClient,
                                   VectorizationRunExecutor runExecutor) {
        this.properties = properties;
        this.platformClient = platformClient;
        this.runExecutor = runExecutor;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!platformClient.isConfigured()) {
            log.info("Vectorization runner is idle because platformBaseUrl or registrationToken is not configured.");
            return;
        }
        running = true;
        executorService.submit(this::pollLoop);
    }

    @PreDestroy
    public void stop() {
        running = false;
        executorService.shutdownNow();
    }

    private void pollLoop() {
        String sessionToken = null;
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                if (sessionToken == null) {
                    VectorizationRunnerPlatformClient.RunnerSession session = platformClient.createSession();
                    sessionToken = session.sessionToken();
                    log.info(
                        "Vectorization runner session established: deploymentId={}, runnerMode={}, compatibilityStatus={}",
                        session.deploymentId(),
                        session.runnerMode(),
                        session.compatibilityStatus()
                    );
                }
                VectorizationRunnerPlatformClient.ClaimedRun claimedRun = platformClient.claimNextRun(sessionToken);
                if (claimedRun == null) {
                    sleep();
                    continue;
                }
                log.info("Claimed vectorization run: runId={}, reason={}, status={}", claimedRun.runId(), claimedRun.reason(), claimedRun.status());
                runExecutor.execute(sessionToken, claimedRun);
            } catch (VectorizationRunnerPlatformClient.RunnerAuthException ex) {
                log.warn("Runner auth/session expired; creating a fresh platform session.");
                sessionToken = null;
                sleep();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception ex) {
                log.error("Vectorization runner loop error: {}", ex.getMessage(), ex);
                sleep();
            }
        }
    }

    private void sleep() {
        try {
            Thread.sleep(properties.pollInterval().toMillis());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
