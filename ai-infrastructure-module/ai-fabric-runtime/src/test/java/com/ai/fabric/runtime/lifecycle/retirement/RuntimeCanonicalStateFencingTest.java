package com.ai.fabric.runtime.lifecycle.retirement;

import static com.ai.fabric.runtime.lifecycle.retirement.RuntimeTargetRetirementContracts.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import ai.fabric.chat.control.ConversationControlGateway;
import ai.fabric.chat.control.ConversationOrchestrationState;
import ai.fabric.chat.control.ConversationScope;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

class RuntimeCanonicalStateFencingTest {
    private static final ConversationScope OWNED = new ConversationScope("tenant-1", "dep-123", "owned", "alice");

    @Test
    void delegatesReadUpdateAndClearAndRecordsStateFenceCoverage() {
        var f = new RuntimeRetirementTestFixture();
        ConversationControlGateway delegate = mock(ConversationControlGateway.class);
        ConversationControlGateway fenced = fence(f, delegate);
        var state = new ConversationOrchestrationState(OWNED, 1, Map.of("key", "value"), Instant.now());
        UnaryOperator<Map<String, Object>> mutation = metadata -> Map.of("key", "value");
        when(delegate.orchestrationState(OWNED, 0L)).thenReturn(Optional.of(state));
        when(delegate.updateOrchestrationState(OWNED, 0L, mutation)).thenReturn(state);

        assertThat(fenced.orchestrationState(OWNED, 0L)).contains(state);
        assertThat(fenced.updateOrchestrationState(OWNED, 0L, mutation)).isEqualTo(state);
        fenced.clearOrchestrationState(OWNED, 0L);

        verify(delegate).clearOrchestrationState(OWNED, 0L);
        assertThat(f.coverage.covers("conversation-control", "conversation-orchestration-state")).isTrue();
        assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM chat_sessions", Long.class)).isZero();
    }

    @Test
    void validatesTenantAndDeploymentBeforeDelegatingEveryStateOperation() {
        var f = new RuntimeRetirementTestFixture();
        ConversationControlGateway delegate = mock(ConversationControlGateway.class);
        ConversationControlGateway fenced = fence(f, delegate);
        for (ConversationScope scope : new ConversationScope[]{
            new ConversationScope("other-tenant", "dep-123", "owned", "alice"),
            new ConversationScope("tenant-1", "other-deployment", "owned", "alice")
        }) {
            assertThatThrownBy(() -> fenced.orchestrationState(scope, 0L)).hasMessageContaining("RUNTIME_CONVERSATION_SCOPE_MISMATCH");
            assertThatThrownBy(() -> fenced.updateOrchestrationState(scope, 0L, metadata -> metadata)).hasMessageContaining("RUNTIME_CONVERSATION_SCOPE_MISMATCH");
            assertThatThrownBy(() -> fenced.clearOrchestrationState(scope, 0L)).hasMessageContaining("RUNTIME_CONVERSATION_SCOPE_MISMATCH");
        }
        verifyNoInteractions(delegate);
    }

    @Test
    void drainAllowsOwnedContinuationAndClosedCleanupButRejectsNewOrForeignStateWrites() {
        var f = new RuntimeRetirementTestFixture();
        f.canonicalState("owned", "alice", "{}");
        f.canonicalState("closed", "alice", "{}");
        f.jdbc.update("UPDATE ai_conversation_control SET control_state = 'CLOSED' WHERE conversation_id = 'closed'");
        ConversationControlGateway delegate = mock(ConversationControlGateway.class);
        ConversationControlGateway fenced = fence(f, delegate);
        f.service.command(f.command(1, 0, CommandType.BEGIN_DRAIN));

        fenced.updateOrchestrationState(OWNED, 0L, metadata -> metadata);
        fenced.clearOrchestrationState(OWNED, 0L);
        for (ConversationScope scope : new ConversationScope[]{
            new ConversationScope("tenant-1", "dep-123", "new", "alice"),
            new ConversationScope("tenant-1", "dep-123", "owned", "mallory"),
            new ConversationScope("tenant-1", "dep-123", "closed", "alice")
        }) {
            assertThatThrownBy(() -> fenced.updateOrchestrationState(scope, 0L, metadata -> metadata))
                .hasMessageContaining("TARGET_NEW_CONVERSATIONS_BLOCKED");
        }
        verify(delegate, times(1)).updateOrchestrationState(eq(OWNED), eq(0L), any());
        verify(delegate).clearOrchestrationState(OWNED, 0L);
    }

    @Test
    void authorityRevocationRejectsLateReadUpdateAndClear() {
        var f = new RuntimeRetirementTestFixture();
        ConversationControlGateway delegate = mock(ConversationControlGateway.class);
        ConversationControlGateway fenced = fence(f, delegate);
        f.service.command(f.command(1, 0, CommandType.BEGIN_DRAIN));
        f.service.command(f.command(2, 1, CommandType.RECORD_AUTHORITY_REVOCATION));

        assertThatThrownBy(() -> fenced.orchestrationState(OWNED, 0L)).hasMessageContaining("AUTHORITY_FENCED");
        assertThatThrownBy(() -> fenced.updateOrchestrationState(OWNED, 0L, metadata -> metadata)).hasMessageContaining("AUTHORITY_FENCED");
        assertThatThrownBy(() -> fenced.clearOrchestrationState(OWNED, 0L)).hasMessageContaining("AUTHORITY_FENCED");
        verifyNoInteractions(delegate);
    }

    @Test
    void retirementLockSpansTheActualCanonicalMutation() throws Exception {
        var f = new RuntimeRetirementTestFixture();
        f.canonicalState("owned", "alice", "{}");
        ConversationControlGateway delegate = mock(ConversationControlGateway.class);
        ConversationControlGateway fenced = fence(f, delegate);
        CountDownLatch writing = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch draining = new CountDownLatch(1);
        when(delegate.updateOrchestrationState(eq(OWNED), eq(0L), any())).thenAnswer(invocation -> {
            writing.countDown();
            if (!release.await(3, TimeUnit.SECONDS)) throw new IllegalStateException("mutation latch timed out");
            f.jdbc.update("UPDATE ai_conversation_orchestration_state SET state_revision = 2 WHERE conversation_id = 'owned'");
            return new ConversationOrchestrationState(OWNED, 2, Map.of(), Instant.now());
        });
        try (var executor = Executors.newFixedThreadPool(2)) {
            var update = executor.submit(() -> fenced.updateOrchestrationState(OWNED, 0L, metadata -> metadata));
            assertThat(writing.await(2, TimeUnit.SECONDS)).isTrue();
            var drain = executor.submit(() -> {
                draining.countDown();
                return f.service.command(f.command(1, 0, CommandType.BEGIN_DRAIN));
            });
            try {
                assertThat(draining.await(2, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> drain.get(100, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            } finally {
                release.countDown();
            }
            assertThat(update.get(3, TimeUnit.SECONDS).revision()).isEqualTo(2);
            assertThat(drain.get(3, TimeUnit.SECONDS).current().state()).isEqualTo(State.DRAINING);
        }
    }

    private ConversationControlGateway fence(RuntimeRetirementTestFixture fixture, ConversationControlGateway delegate) {
        var beans = new DefaultListableBeanFactory();
        beans.registerSingleton("retirement", fixture.service);
        return (ConversationControlGateway) RuntimeRetirementStoreFencing.runtimeRetirementStorePostProcessor(
            beans.getBeanProvider(RuntimeTargetRetirementService.class), fixture.coverage)
            .postProcessAfterInitialization(delegate, "controlGateway");
    }
}
