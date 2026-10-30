package com.ai.fabric.platform.backend.aiworkspace.service;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@ConditionalOnProperty(name = "platform.ai-workspace.route-worker-enabled", havingValue = "true", matchIfMissing = true)
public class AIWorkspaceDomainVerificationScheduler {
    private static final Logger log = LoggerFactory.getLogger(AIWorkspaceDomainVerificationScheduler.class);
    private final AIWorkspaceDomainVerificationService worker;
    private final ExecutorService executor;
    private final AtomicBoolean inflight = new AtomicBoolean();

    @Autowired
    public AIWorkspaceDomainVerificationScheduler(AIWorkspaceDomainVerificationService worker) {
        this(worker, new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new SynchronousQueue<>(), task -> {
            var thread = new Thread(task, "ai-workspace-domain-verification");
            thread.setDaemon(true);
            return thread;
        }, new ThreadPoolExecutor.AbortPolicy()));
    }

    AIWorkspaceDomainVerificationScheduler(AIWorkspaceDomainVerificationService worker, ExecutorService executor) {
        this.worker = worker;
        this.executor = executor;
    }

    @Scheduled(fixedDelayString = "${platform.ai-workspace.route-poll-delay-ms:5000}")
    public void reconcile() {
        if (executor.isShutdown() || !inflight.compareAndSet(false, true)) return;
        try {
            executor.execute(() -> {
                try { worker.runNext(); }
                catch (RuntimeException ex) { log.debug("Workspace domain verification will retry on a later poll"); }
                finally { inflight.set(false); }
            });
        } catch (RejectedExecutionException ex) { inflight.set(false); }
    }

    @PreDestroy
    public void close() {
        executor.shutdownNow();
        try { executor.awaitTermination(2, TimeUnit.SECONDS); }
        catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
    }
}
