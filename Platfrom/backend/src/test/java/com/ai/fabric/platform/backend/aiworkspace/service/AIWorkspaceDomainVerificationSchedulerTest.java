package com.ai.fabric.platform.backend.aiworkspace.service;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AIWorkspaceDomainVerificationSchedulerTest {
    @Test
    void blockedProbeDoesNotBlockSharedSchedulerOrQueueMoreTicks() throws Exception {
        var worker = mock(AIWorkspaceDomainVerificationService.class);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var finished = new CountDownLatch(1);
        doAnswer(call -> {
            entered.countDown();
            try { release.await(5, TimeUnit.SECONDS); }
            finally { finished.countDown(); }
            return null;
        }).when(worker).runNext();
        var scheduler = new AIWorkspaceDomainVerificationScheduler(worker);
        try (var shared = Executors.newSingleThreadScheduledExecutor()) {
            shared.submit(scheduler::reconcile).get(1, TimeUnit.SECONDS);
            assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();
            for (int index = 0; index < 20; index++) shared.submit(scheduler::reconcile).get(1, TimeUnit.SECONDS);
            assertThat(shared.submit(() -> "route-worker-still-dispatchable").get(1, TimeUnit.SECONDS))
                .isEqualTo("route-worker-still-dispatchable");
            verify(worker, times(1)).runNext();
        } finally {
            release.countDown();
            assertThat(finished.await(1, TimeUnit.SECONDS)).isTrue();
            scheduler.close();
        }
    }

    @Test
    void rejectionDoesNotStickInflightAndShutdownDoesNotDispatch() {
        var worker = mock(AIWorkspaceDomainVerificationService.class);
        var executor = mock(ExecutorService.class);
        doThrow(new RejectedExecutionException()).doAnswer(call -> {
            call.getArgument(0, Runnable.class).run();
            return null;
        }).when(executor).execute(any());
        var scheduler = new AIWorkspaceDomainVerificationScheduler(worker, executor);
        scheduler.reconcile();
        scheduler.reconcile();
        verify(worker).runNext();
        when(executor.isShutdown()).thenReturn(true);
        scheduler.reconcile();
        verify(executor, times(2)).execute(any());
    }

    @Test
    void failureCanRetryWithoutLoggingExceptionContentAndCloseStopsExecutor() {
        var worker = mock(AIWorkspaceDomainVerificationService.class);
        var executor = mock(ExecutorService.class);
        doThrow(new IllegalStateException("SECRET")).doNothing().when(worker).runNext();
        doAnswer(call -> { call.getArgument(0, Runnable.class).run(); return null; }).when(executor).execute(any());
        var scheduler = new AIWorkspaceDomainVerificationScheduler(worker, executor);
        scheduler.reconcile();
        scheduler.reconcile();
        verify(worker, times(2)).runNext();
        scheduler.close();
        verify(executor).shutdownNow();
    }
}
