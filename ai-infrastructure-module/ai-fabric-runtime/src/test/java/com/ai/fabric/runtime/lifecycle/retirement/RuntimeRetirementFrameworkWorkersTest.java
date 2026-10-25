package com.ai.fabric.runtime.lifecycle.retirement;

import static com.ai.fabric.runtime.lifecycle.retirement.RuntimeRetirementTestFixture.*;
import static com.ai.fabric.runtime.lifecycle.retirement.RuntimeTargetRetirementContracts.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import ai.fabric.config.AIIndexingProperties;
import ai.fabric.execution.config.AIExecutionProperties;
import ai.fabric.indexing.api.IndexingStrategy;
import ai.fabric.indexing.queue.IndexingQueueService;
import ai.fabric.indexing.worker.AsyncIndexingWorker;
import ai.fabric.indexing.worker.IndexingWorkProcessor;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

class RuntimeRetirementFrameworkWorkersTest {
    @Test
    void indexingWorkerRetainsAttemptThroughLeaseProcessAndAcknowledgementAndCannotRunAfterFence() {
        var f = new RuntimeRetirementTestFixture(true, true, List.of());
        var queue = mock(IndexingQueueService.class);
        var processor = mock(IndexingWorkProcessor.class);
        when(queue.lease(IndexingStrategy.ASYNC, 100)).thenAnswer(call -> {
            assertThat(f.service.snapshot(f.binding(1)).inFlightRequests()).isOne();
            return List.of();
        });
        var properties = new AIIndexingProperties();
        properties.getAsyncWorker().setBatchSize(100);
        var beans = beans(f);
        var wrapper = RuntimeRetirementWorkerFencing.runtimeRetirementWorkerPostProcessor(
            beans.getBeanProvider(RuntimeTargetRetirementService.class), f.coverage);
        var worker = (AsyncIndexingWorker) wrapper.postProcessAfterInitialization(
            new AsyncIndexingWorker(queue, processor, properties), "asyncIndexingWorker");
        f.service.command(f.command(1, 0, CommandType.BEGIN_DRAIN));
        worker.run();
        assertThat(f.service.snapshot(f.binding(1)).inFlightRequests()).isZero();
        f.service.command(f.command(2, 1, CommandType.FENCE_AUTHORITY));
        worker.run();
        verify(queue).lease(IndexingStrategy.ASYNC, 100);
        verifyNoInteractions(processor);
    }

    @Test
    void actualAttemptBlocksFenceEvenWhenItsFrameworkRowIsAlreadyTerminal() throws Exception {
        var f = new RuntimeRetirementTestFixture(true, true, List.of());
        var entered = new CountDownLatch(1);
        var leave = new CountDownLatch(1);
        try (var pool = Executors.newSingleThreadExecutor()) {
            var running = pool.submit(() -> f.service.withObservedWorkerBoundary("indexing", true, () -> {
                entered.countDown();
                await(leave);
                return "actual-effect-finished";
            }));
            assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue();
            f.service.command(f.command(1, 0, CommandType.BEGIN_DRAIN));
            assertThat(f.service.snapshot(f.binding(1)).inFlightRequests()).isOne();
            assertThatThrownBy(() -> f.service.command(f.command(2, 1, CommandType.FENCE_AUTHORITY)))
                .hasMessageContaining("DRAIN_NOT_PROVED_COMPLETE");
            leave.countDown();
            assertThat(running.get(3, TimeUnit.SECONDS)).isEqualTo("actual-effect-finished");
            assertThat(f.service.command(f.command(2, 1, CommandType.FENCE_AUTHORITY)).current().authorityRevoked()).isTrue();
        } finally {
            leave.countDown();
        }
    }

    @Test
    void cancelledSpecialistFutureDoesNotHideStillExecutingCode() throws Exception {
        var f = new RuntimeRetirementTestFixture(true, true, List.of());
        var entered = new CountDownLatch(1);
        var leave = new CountDownLatch(1);
        var exited = new CountDownLatch(1);
        var executor = executor(f);
        try {
            var running = executor.submit(() -> {
                entered.countDown();
                boolean done = false;
                while (!done) {
                    try { done = leave.await(3, TimeUnit.SECONDS); }
                    catch (InterruptedException ignored) { }
                }
                exited.countDown();
            });
            assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue();
            running.cancel(true);
            f.service.command(f.command(1, 0, CommandType.BEGIN_DRAIN));
            assertThat(running.isCancelled()).isTrue();
            assertThat(f.service.snapshot(f.binding(1)).inFlightRequests()).isOne();
            assertThatThrownBy(() -> f.service.command(f.command(2, 1, CommandType.FENCE_AUTHORITY)))
                .hasMessageContaining("DRAIN_NOT_PROVED_COMPLETE");
            leave.countDown();
            assertThat(exited.await(3, TimeUnit.SECONDS)).isTrue();
        } finally {
            leave.countDown();
            executor.shutdown();
        }
    }

    @Test
    void parallelSpecialistChildrenDoNotDeadlockBehindParentAttempt() throws Exception {
        var f = new RuntimeRetirementTestFixture(true, true, List.of());
        var executor = executor(f);
        try {
            var root = executor.submit(() -> executor.submit(() -> "child-complete").get(3, TimeUnit.SECONDS));
            assertThat(root.get(5, TimeUnit.SECONDS)).isEqualTo("child-complete");
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void recoveredSpecialistTaskCannotStartAfterAuthorityFence() throws Exception {
        var f = new RuntimeRetirementTestFixture(true, true, List.of());
        f.service.command(f.command(1, 0, CommandType.BEGIN_DRAIN));
        f.service.command(f.command(2, 1, CommandType.FENCE_AUTHORITY));
        var executor = executor(f);
        var effect = mock(Runnable.class);
        try {
            var rejected = executor.submit(effect);
            assertThatThrownBy(() -> rejected.get(3, TimeUnit.SECONDS)).isInstanceOf(java.util.concurrent.CancellationException.class);
            verifyNoInteractions(effect);
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void workerFailureSettlesItsOwnAttemptWithoutClearingUnrelatedPermits() {
        var f = new RuntimeRetirementTestFixture(true, true, List.of());
        var other = f.service.beginIngress("unsettled-request", false);
        assertThatThrownBy(() -> f.service.withObservedWorkerBoundary("failed-worker", true, () -> {
            throw new IllegalStateException("worker-failed");
        })).hasMessage("worker-failed");
        f.service.command(f.command(1, 0, CommandType.BEGIN_DRAIN));
        assertThat(f.service.snapshot(f.binding(1)).inFlightRequests()).isOne();
        f.service.finishTurn(other);
    }

    private DefaultListableBeanFactory beans(RuntimeRetirementTestFixture f) {
        var beans = new DefaultListableBeanFactory();
        beans.registerSingleton("retirement", f.service);
        return beans;
    }
    private ThreadPoolTaskExecutor executor(RuntimeRetirementTestFixture f) {
        var properties = new AIExecutionProperties();
        properties.getAsync().setCorePoolSize(3);
        properties.getAsync().setMaxPoolSize(3);
        var executor = new RuntimeRetirementExecutionConfiguration().retirementExecutionTaskExecutor(properties,
            beans(f).getBeanProvider(RuntimeTargetRetirementService.class), f.coverage);
        executor.initialize();
        return executor;
    }
    private void await(CountDownLatch latch) {
        try { if (!latch.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Timed out waiting for test work."); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException(exception); }
    }
}
