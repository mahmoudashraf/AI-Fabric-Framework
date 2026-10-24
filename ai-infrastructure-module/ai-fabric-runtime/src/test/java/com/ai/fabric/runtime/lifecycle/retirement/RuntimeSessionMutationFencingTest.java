package com.ai.fabric.runtime.lifecycle.retirement;

import static com.ai.fabric.runtime.lifecycle.retirement.RuntimeTargetRetirementContracts.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import ai.fabric.chat.domain.ChatSession;
import ai.fabric.chat.config.ChatSessionProperties;
import ai.fabric.chat.service.ChatSessionServiceImpl;
import ai.fabric.chat.spi.ChatSessionAccessControlPolicy;
import ai.fabric.chat.spi.ChatSessionStorageProvider;
import ai.fabric.chat.strategy.MemoryStrategy;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

class RuntimeSessionMutationFencingTest {
    @Test
    void theFrameworkCanRecordConsecutiveTurnsThroughTheRuntimeWrapper() {
        var fixture = new RuntimeRetirementTestFixture();
        var delegate = mock(ChatSessionStorageProvider.class);
        var fenced = fence(fixture, delegate);
        var session = ChatSession.builder().id("owned").ownerId("alice").build();
        when(delegate.findById("owned")).thenReturn(Optional.of(session));
        when(delegate.mutateOwnedSession(eq("owned"), eq("alice"), any())).thenAnswer(invocation -> {
            Function<ChatSession, ?> mutation = invocation.getArgument(2);
            return Optional.ofNullable(mutation.apply(session));
        });
        var access = mock(ChatSessionAccessControlPolicy.class);
        when(access.canRecordTurn("alice", "owned")).thenReturn(true);
        var chat = new ChatSessionServiceImpl(fenced, access, mock(MemoryStrategy.class), new ChatSessionProperties());

        chat.recordTurn("owned", "alice", "List current items", "Three current items", Map.of());
        chat.recordTurn("owned", "alice", "Which has the lowest mileage?", "The second item", Map.of());

        assertThat(session.getTurns()).hasSize(2);
        assertThat(session.getTurns().getFirst().getUserQuery()).isEqualTo("List current items");
        assertThat(session.getTurns().getLast().getAiResponse()).isEqualTo("The second item");
        verify(delegate, times(2)).mutateOwnedSession(eq("owned"), eq("alice"), any());
        verify(delegate, never()).save(any());
    }

    @Test
    void delegatesTheExactAtomicOwnerMutationAndResult() {
        var fixture = new RuntimeRetirementTestFixture();
        var delegate = mock(ChatSessionStorageProvider.class);
        var fenced = fence(fixture, delegate);
        var session = ChatSession.builder().id("owned").ownerId("alice").build();
        Function<ChatSession, String> mutation = current -> {
            current.getSessionMetadata().put("recorded", true);
            return "recorded";
        };
        when(delegate.mutateOwnedSession("owned", "alice", mutation))
            .thenAnswer(invocation -> Optional.of(mutation.apply(session)));

        assertThat(fenced.mutateOwnedSession("owned", "alice", mutation)).contains("recorded");
        assertThat(session.getSessionMetadata()).containsEntry("recorded", true);
        verify(delegate).mutateOwnedSession("owned", "alice", mutation);
        verify(delegate, never()).save(any());
        verify(delegate, never()).findById(any());
    }

    @Test
    void missingOrForeignOwnerRemainsEmptyWithoutCallingTheMutation() {
        var fixture = new RuntimeRetirementTestFixture();
        var delegate = mock(ChatSessionStorageProvider.class);
        var fenced = fence(fixture, delegate);
        Function<ChatSession, String> mutation = current -> {
            fail("The storage provider must establish ownership before invoking the mutation");
            return "unexpected";
        };
        when(delegate.mutateOwnedSession("missing", "mallory", mutation)).thenReturn(Optional.empty());

        assertThat(fenced.mutateOwnedSession("missing", "mallory", mutation)).isEmpty();
        verify(delegate).mutateOwnedSession("missing", "mallory", mutation);
        verifyNoMoreInteractions(delegate);
    }

    @Test
    void drainRejectsNewAndForeignMutationsBeforeDelegation() {
        var fixture = new RuntimeRetirementTestFixture();
        fixture.session("owned", "alice", "ACTIVE", "{}");
        var delegate = mock(ChatSessionStorageProvider.class);
        var fenced = fence(fixture, delegate);
        fixture.service.command(fixture.command(1, 0, CommandType.BEGIN_DRAIN));

        assertThatThrownBy(() -> fenced.mutateOwnedSession("new", "alice", current -> true))
            .hasMessageContaining("TARGET_NEW_CONVERSATIONS_BLOCKED");
        assertThatThrownBy(() -> fenced.mutateOwnedSession("owned", "mallory", current -> true))
            .hasMessageContaining("TARGET_NEW_CONVERSATIONS_BLOCKED");
        verifyNoInteractions(delegate);
    }

    @Test
    void revokedAuthorityRejectsAtomicMutationBeforeDelegation() {
        var fixture = new RuntimeRetirementTestFixture();
        var delegate = mock(ChatSessionStorageProvider.class);
        var fenced = fence(fixture, delegate);
        fixture.service.command(fixture.command(1, 0, CommandType.BEGIN_DRAIN));
        fixture.service.command(fixture.command(2, 1, CommandType.RECORD_AUTHORITY_REVOCATION));

        assertThatThrownBy(() -> fenced.mutateOwnedSession("owned", "alice", current -> true))
            .hasMessageContaining("AUTHORITY_FENCED");
        verifyNoInteractions(delegate);
    }

    @Test
    void propagatesTheProviderFailureWithoutUnsafeReadSaveFallback() {
        var fixture = new RuntimeRetirementTestFixture();
        var delegate = mock(ChatSessionStorageProvider.class);
        var fenced = fence(fixture, delegate);
        Function<ChatSession, Boolean> mutation = current -> true;
        var failure = new IllegalStateException("atomic store failed");
        when(delegate.mutateOwnedSession("owned", "alice", mutation)).thenThrow(failure);

        assertThatThrownBy(() -> fenced.mutateOwnedSession("owned", "alice", mutation)).isSameAs(failure);
        verify(delegate).mutateOwnedSession("owned", "alice", mutation);
        verifyNoMoreInteractions(delegate);
    }

    @Test
    void theFenceSpansTheActualAtomicStorageOperation() throws Exception {
        var fixture = new RuntimeRetirementTestFixture();
        fixture.session("owned", "alice", "ACTIVE", "{}");
        var delegate = mock(ChatSessionStorageProvider.class);
        var fenced = fence(fixture, delegate);
        var writing = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var draining = new CountDownLatch(1);
        when(delegate.mutateOwnedSession(eq("owned"), eq("alice"), any())).thenAnswer(invocation -> {
            writing.countDown();
            if (!release.await(3, TimeUnit.SECONDS)) throw new IllegalStateException("mutation latch timed out");
            fixture.jdbc.update("UPDATE chat_sessions SET session_metadata = '{\"recorded\":true}' WHERE id = 'owned'");
            return Optional.of(true);
        });
        try (var executor = Executors.newFixedThreadPool(2)) {
            var update = executor.submit(() -> fenced.mutateOwnedSession("owned", "alice", current -> true));
            assertThat(writing.await(2, TimeUnit.SECONDS)).isTrue();
            var drain = executor.submit(() -> {
                draining.countDown();
                return fixture.service.command(fixture.command(1, 0, CommandType.BEGIN_DRAIN));
            });
            try {
                assertThat(draining.await(2, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> drain.get(100, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            } finally {
                release.countDown();
            }
            assertThat(update.get(3, TimeUnit.SECONDS)).contains(true);
            assertThat(drain.get(3, TimeUnit.SECONDS).current().state()).isEqualTo(State.DRAINING);
        }
    }

    private ChatSessionStorageProvider fence(RuntimeRetirementTestFixture fixture, ChatSessionStorageProvider delegate) {
        var beans = new DefaultListableBeanFactory();
        beans.registerSingleton("retirement", fixture.service);
        return new RuntimeRetirementStoreFencing.FencedSessions(delegate, beans.getBeanProvider(RuntimeTargetRetirementService.class));
    }
}
