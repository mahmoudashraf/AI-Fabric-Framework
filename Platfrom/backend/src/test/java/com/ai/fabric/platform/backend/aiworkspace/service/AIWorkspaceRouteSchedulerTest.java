package com.ai.fabric.platform.backend.aiworkspace.service;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AIWorkspaceRouteSchedulerTest {
    @Test void blockedProviderDoesNotBlockSharedSchedulerOrQueueTicks() throws Exception {
        var worker = mock(AIWorkspaceDomainRouteService.class);
        var entered = new CountDownLatch(1); var release = new CountDownLatch(1);
        var finished = new CountDownLatch(1);
        doAnswer(call -> {
            entered.countDown();
            try { release.await(5, TimeUnit.SECONDS); }
            finally { finished.countDown(); }
            return null;
        }).when(worker).runNext();
        var scheduler = new AIWorkspaceRouteScheduler(worker);
        try (var shared = Executors.newSingleThreadScheduledExecutor()) {
            shared.submit(scheduler::reconcile).get(1, TimeUnit.SECONDS);
            assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();
            for (int i = 0; i < 20; i++) shared.submit(scheduler::reconcile).get(1, TimeUnit.SECONDS);
            assertThat(shared.submit(() -> "ordinary-cleanup-dispatched").get(1, TimeUnit.SECONDS))
                .isEqualTo("ordinary-cleanup-dispatched");
            verify(worker, times(1)).runNext();
        } finally { release.countDown(); assertThat(finished.await(1, TimeUnit.SECONDS)).isTrue(); scheduler.close(); }
    }

    @Test void rejectedDispatchClearsInflightAndShutdownDoesNotDispatch() {
        var worker = mock(AIWorkspaceDomainRouteService.class);
        var executor = mock(ExecutorService.class);
        doThrow(new RejectedExecutionException()).doAnswer(call -> {
            call.getArgument(0, Runnable.class).run(); return null;
        }).when(executor).execute(any());
        var scheduler = new AIWorkspaceRouteScheduler(worker, executor);
        scheduler.reconcile(); scheduler.reconcile();
        verify(worker).runNext();
        when(executor.isShutdown()).thenReturn(true); scheduler.reconcile();
        verify(executor, times(2)).execute(any());
    }

    @Test void workerFailureDoesNotPermanentlyStickInflightOrShutdown() {
        var worker = mock(AIWorkspaceDomainRouteService.class);
        var executor = mock(ExecutorService.class);
        doThrow(new IllegalStateException("SECRET")).doNothing().when(worker).runNext();
        doAnswer(call -> { call.getArgument(0, Runnable.class).run(); return null; }).when(executor).execute(any());
        var scheduler = new AIWorkspaceRouteScheduler(worker, executor);
        scheduler.reconcile(); scheduler.reconcile(); verify(worker, times(2)).runNext();
        scheduler.close(); verify(executor).shutdownNow();
    }
}
