package com.ai.fabric.runtime.workflow;

import ai.fabric.chat.control.*;
import ai.fabric.execution.gateway.*;
import ai.fabric.execution.state.*;
import ai.fabric.intent.orchestration.OrchestrationContext;
import ai.fabric.intent.orchestration.control.OrchestrationControlException;
import ai.fabric.intent.orchestration.control.OrchestrationTurnGuard;
import ai.fabric.intent.orchestration.conversation.ApprovedConversationSnapshot;
import com.ai.fabric.budget.ProviderBudgetLedger.ChatReceipt;
import com.ai.fabric.runtime.auth.*;
import com.ai.fabric.runtime.budget.RuntimeBudgetTurnScopes;
import com.ai.fabric.runtime.config.RuntimeAuthProperties;
import com.ai.fabric.runtime.handover.*;
import com.ai.fabric.runtime.web.dto.ChatQueryRequest.ConfigurationWork;
import com.ai.fabric.runtime.web.dto.ChatQueryRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RuntimeDurableGoalHostTest {
    @Test void finishedGoalsDoNotBlockInterruptionOfANewerQuickEdit() throws Exception {
        for (var status : List.of(ExecutionHandleStatus.SUCCEEDED, ExecutionHandleStatus.FAILED, ExecutionHandleStatus.CANCELLED,
            ExecutionHandleStatus.REJECTED, ExecutionHandleStatus.EXPIRED)) {
            var fixture = new Fixture();
            var completed = fixture.record.completed(status, null, null, fixture.now, Duration.ofDays(7));
            when(fixture.repository.findById(fixture.id)).thenReturn(Optional.of(completed));
            var identity = RuntimeResolvedIdentity.builder().authContext(fixture.auth).warnings(List.of()).build();
            assertThatCode(() -> fixture.host.interrupt(identity, fixture.conversation, "newer-quick-edit")).doesNotThrowAnyException();
            verifyNoInteractions(fixture.gateway);
            verify(fixture.conversations, atLeastOnce()).status(any());
        }
    }
    @Test void activeGoalInterruptionRetainsItsExactTurnAndOwnerChecks() throws Exception {
        var fixture = new Fixture();
        var identity = RuntimeResolvedIdentity.builder().authContext(fixture.auth).warnings(List.of()).build();
        assertThatThrownBy(() -> fixture.host.interrupt(identity, fixture.conversation, "unrelated-turn"))
            .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(fixture.gateway);
        fixture.host.interrupt(identity, fixture.conversation, fixture.work.turnId());
        verify(fixture.gateway).controlGoal(eq(fixture.id), eq(GoalExecutionControl.CANCEL), any());
        var scope = new ConversationScope("tenant", "deployment", fixture.conversation, identity.ownerId());
        var control = fixture.conversations.status(scope);
        when(fixture.conversations.status(any())).thenReturn(control);
        when(fixture.conversations.orchestrationState(any(), eq(0L)))
            .thenReturn(Optional.of(new ConversationOrchestrationState(scope, 0, fixture.metadata.get(), fixture.now)));
        var foreign = RuntimeResolvedIdentity.builder().authContext(fixture.auth.toBuilder().subjectId("authoring:foreign").build()).warnings(List.of()).build();
        assertThatThrownBy(() -> fixture.host.interrupt(foreign, fixture.conversation, fixture.work.turnId()))
            .isInstanceOf(ResponseStatusException.class);
        verifyNoMoreInteractions(fixture.gateway);
    }
    @Test void finishedGoalStillRequiresItsOriginalOwnerBeforeItCanBeIgnored() throws Exception {
        var fixture = new Fixture();
        var completed = fixture.record.completed(ExecutionHandleStatus.CANCELLED, null, null, fixture.now, Duration.ofDays(7));
        when(fixture.repository.findById(fixture.id)).thenReturn(Optional.of(completed));
        var owner = RuntimeResolvedIdentity.builder().authContext(fixture.auth).warnings(List.of()).build();
        var scope = new ConversationScope("tenant", "deployment", fixture.conversation, owner.ownerId());
        var control = fixture.conversations.status(scope);
        when(fixture.conversations.status(any())).thenReturn(control);
        when(fixture.conversations.orchestrationState(any(), eq(0L)))
            .thenReturn(Optional.of(new ConversationOrchestrationState(scope, 0, fixture.metadata.get(), fixture.now)));
        var foreign = RuntimeResolvedIdentity.builder().authContext(fixture.auth.toBuilder().subjectId("authoring:foreign").build()).warnings(List.of()).build();
        assertThatThrownBy(() -> fixture.host.interrupt(foreign, fixture.conversation, "newer-quick-edit"))
            .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(fixture.gateway);
    }
    @Test void submissionPreparationIsLocalAndKeepsTheOriginalQueryIdentityAndDeadline() throws Exception {
        var fixture = new Fixture();
        var root = fixture.root();
        var work = root.work().renewed(1, 0, 1, fixture.freshGrant);
        fixture.root(new RuntimeDurableGoalHost.Root(work, root.conversationId(), root.userSequence(), root.interactionKey(),
            root.controlEpoch(), root.budgetReceipt(), null, root.issuer(), root.audiences(), root.customerId()));
        var prepared = fixture.host.prepareSubmission(new GoalExecutionRequest(fixture.request, fixture.goal));
        assertThat(prepared.input()).isEqualTo(fixture.request.input());
        assertThat(prepared.trustedExecutionContext()).isEqualTo(fixture.request.trustedExecutionContext());
        assertThat(prepared.deadline()).isEqualTo(fixture.request.deadline());
        assertThat(fixture.root().sourceVersions()).isNull();
        verify(fixture.platform, never()).renew(any(), any());
        var trusted = fixture.request.trustedExecutionContext();
        var narrowed = new ai.fabric.execution.context.TrustedExecutionContext(trusted.initiator(), trusted.subject(), trusted.source(),
            trusted.tenantId(), trusted.deploymentId(), Set.of("chat:messages"), null, null);
        assertThatThrownBy(() -> fixture.host.prepareSubmission(new GoalExecutionRequest(new AIExecutionRequest<>(
            fixture.request.specialistId(), fixture.request.input(), narrowed, null, fixture.freshGrant, fixture.work.turnId()), fixture.goal)))
            .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> fixture.host.prepareSubmission(new GoalExecutionRequest(new AIExecutionRequest<>(
            fixture.request.specialistId(), fixture.request.input(), trusted, null, fixture.freshGrant, "different-root"), fixture.goal)))
            .isInstanceOf(ResponseStatusException.class);
        verify(fixture.platform, never()).renew(any(), any());
    }

    @Test void firstWorkerPinsSourcesBeforeTheDelegateAndKeepsTheExistingReceipt() throws Exception {
        for (boolean sourceLess : List.of(false, true)) {
            var fixture = new Fixture(); fixture.root(fixture.root().withSources(null));
            if (sourceLess) fixture.pins.removeAll();
            when(fixture.platform.renew(any(), anyString())).thenReturn(fixture.lease());
            var result = fixture.host.executeSegment(fixture.claim, fixture.request, request -> {
                assertThat(fixture.root().sourceVersions()).isEqualTo(fixture.pins);
                assertThat(request.input()).isEqualTo(fixture.request.input());
                return fixture.success();
            });
            assertThat(result.status()).isEqualTo(AIExecutionStatus.SUCCEEDED);
            assertThat(fixture.root().budgetReceipt()).isNotNull();
            verify(fixture.budgets).openGoalSegment(any(), eq(fixture.root().budgetReceipt()), any());
        }
    }

    @Test void unavailableFirstRenewalLeavesAnUnpinnedPausedRootWithoutModelOrBudgetScope() throws Exception {
        var fixture = new Fixture(); fixture.root(fixture.root().withSources(null)); var calls = new AtomicInteger();
        when(fixture.platform.renew(any(), anyString())).thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE));
        var result = fixture.host.executeSegment(fixture.claim, fixture.request, request -> { calls.incrementAndGet(); return fixture.success(); });
        assertThat(result.status()).isEqualTo(AIExecutionStatus.PAUSED);
        assertThat(result.failure().reason()).isEqualTo("AUTHORING_AUTHORITY_UNAVAILABLE");
        assertThat(fixture.root().sourceVersions()).isNull(); assertThat(calls).hasValue(0);
        verify(fixture.budgets, never()).openGoalSegment(any(), any(), any());
    }

    @Test void unpinnedCheckpointOrExecutionProgressIsAnIntegrityDenialNotAFreshAdmission() throws Exception {
        for (String progress : List.of("checkpoint", "segmentLease", "model", "tool", "effect")) {
            var fixture = new Fixture(); fixture.root(fixture.root().withSources(null));
            if (progress.equals("checkpoint")) when(fixture.repository.findById(fixture.id))
                .thenReturn(Optional.of(fixture.record.withWorkflowCheckpoint("protected-checkpoint", fixture.now)));
            else if (progress.equals("segmentLease")) {
                var metadata = new LinkedHashMap<>(fixture.metadata.get());
                var state = new LinkedHashMap<>((Map<String, Object>) metadata.get("authoringGoal"));
                state.put("segmentLease", Map.of("leaseId", "old-worker")); metadata.put("authoringGoal", state); fixture.metadata.set(metadata);
            } else {
                var result = new LinkedHashMap<String, Object>(); result.put("status", "PAUSED");
                result.put("output", progress.equals("effect") ? Map.of("saved", true) : null); result.put("evidence", List.of());
                result.put("diagnostics", Map.of("workflow", Map.of("state", "PAUSED", "usage", Map.of(progress + "Calls", 1))));
                var protectedResult = fixture.security.protect(result, fixture.id + ":result");
                when(fixture.repository.findById(fixture.id)).thenReturn(Optional.of(fixture.record.goalTransition(
                    ExecutionHandleStatus.PAUSED, protectedResult, "fixture", null, fixture.now, false)));
            }
            var calls = new AtomicInteger();
            var result = fixture.host.executeSegment(fixture.claim, fixture.request, request -> { calls.incrementAndGet(); return fixture.success(); });
            assertThat(result.status()).isEqualTo(AIExecutionStatus.PAUSED);
            assertThat(result.failure().reason()).isEqualTo("AUTHORING_GOAL_SOURCE_PINS_MISSING");
            assertThat(calls).hasValue(0); verify(fixture.platform, never()).renew(any(), any());
            verify(fixture.budgets, never()).openGoalSegment(any(), any(), any());
        }
    }

    @Test void firstSourcePinCannotResurrectErasedRootsOrCrossAnExplicitPauseFence() throws Exception {
        for (boolean erase : List.of(false, true)) {
            var fixture = new Fixture(); fixture.root(fixture.root().withSources(null)); var calls = new AtomicInteger();
            when(fixture.platform.renew(any(), anyString())).thenAnswer(call -> {
                var metadata = new LinkedHashMap<>(fixture.metadata.get());
                if (erase) metadata.put("authoringGoalRoots", Map.of());
                else {
                    var state = new LinkedHashMap<>((Map<String, Object>) metadata.get("authoringGoal"));
                    state.put("state", "PAUSED"); state.put("cancellationEpoch", 1L); metadata.put("authoringGoal", state);
                }
                fixture.metadata.set(metadata); return fixture.lease();
            });
            var result = fixture.host.executeSegment(fixture.claim, fixture.request, request -> { calls.incrementAndGet(); return fixture.success(); });
            assertThat(result.status()).isEqualTo(AIExecutionStatus.PAUSED); assertThat(calls).hasValue(0);
            if (erase) assertThat((Map<?, ?>) fixture.metadata.get().get("authoringGoalRoots")).isEmpty();
            else assertThat(fixture.root().sourceVersions()).isNull();
            verify(fixture.budgets, never()).openGoalSegment(any(), any(), any());
        }
    }

    @Test void malformedAndChangedSourcePinsNeverReachTheDelegateAndLegacyPinnedRootsRemainValid() throws Exception {
        for (String problem : List.of("missing", "array", "digest", "oversized", "changed")) {
            var fixture = new Fixture();
            if (!problem.equals("changed")) fixture.root(fixture.root().withSources(null));
            var lease = fixture.lease().deepCopy();
            switch (problem) {
                case "missing" -> lease.remove("sourceVersions");
                case "array" -> lease.putArray("sourceVersions");
                case "digest" -> lease.putObject("sourceVersions").put("source-one", "not-a-source-hash");
                case "oversized" -> { var sources = lease.putObject("sourceVersions"); for (int i = 0; i < 11; i++) sources.put("source-" + i, "a".repeat(64)); }
                case "changed" -> lease.putObject("sourceVersions").put("source-one", "b".repeat(64));
            }
            when(fixture.platform.renew(any(), anyString())).thenReturn(lease); var calls = new AtomicInteger();
            var result = fixture.host.executeSegment(fixture.claim, fixture.request, request -> { calls.incrementAndGet(); return fixture.success(); });
            assertThat(result.status()).isEqualTo(AIExecutionStatus.PAUSED); assertThat(calls).hasValue(0);
            verify(fixture.budgets, never()).openGoalSegment(any(), any(), any());
        }
        var fixture = new Fixture(); var original = fixture.metadata.get().get("authoringGoalRoots");
        when(fixture.platform.renew(any(), anyString())).thenReturn(fixture.lease());
        assertThat(fixture.host.executeSegment(fixture.claim, fixture.request, request -> fixture.success()).status()).isEqualTo(AIExecutionStatus.SUCCEEDED);
        assertThat(fixture.metadata.get().get("authoringGoalRoots")).isEqualTo(original);
    }

    @Test void expiredLoginPausesWithoutCallsAndFreshAuthorityResumesWithinTheOriginalRootLifetime() throws Exception {
        var fixture = new Fixture(); var calls = new AtomicInteger();
        when(fixture.platform.renew(any(), anyString())).thenThrow(new ResponseStatusException(UNAUTHORIZED));
        var paused = fixture.host.executeSegment(fixture.claim, fixture.request, request -> { calls.incrementAndGet(); return fixture.success(); });
        assertThat(paused.status()).isEqualTo(AIExecutionStatus.PAUSED);
        assertThat(paused.failure().reason()).isEqualTo("AUTHORING_REAUTHENTICATION_REQUIRED");
        assertThat(calls).hasValue(0);
        assertThat(fixture.record.deadline()).isAfter(Instant.now().plusSeconds(3000));
        verify(fixture.budgets, never()).openGoalSegment(any(), any(), any());

        doReturn(fixture.lease()).when(fixture.platform).renew(any(), anyString());
        var completed = fixture.host.executeSegment(fixture.claim, fixture.request, request -> {
            assertThat(request.input()).isEqualTo(fixture.request.input());
            assertThat(request.deadline()).isEqualTo(fixture.record.deadline());
            assertThat(request.trustedExecutionContext().subject()).isEqualTo(fixture.request.trustedExecutionContext().subject());
            assertThat(request.trustedExecutionContext().callerIdentity().expiresAt()).isEqualTo(fixture.auth.getExpiresAt());
            assertThat(request.trustedExecutionContext().callerIdentity().authMode()).isEqualTo("PRIVATE_RUNTIME_BACKEND_MEDIATED");
            fixture.guard.get().verifyBeforeOrchestration(); calls.incrementAndGet(); return fixture.success();
        });
        assertThat(completed.status()).isEqualTo(AIExecutionStatus.SUCCEEDED);
        assertThat(calls).hasValue(1);
        assertThat(fixture.installed.get().authorityDeadline()).isEqualTo(fixture.freshGrant);
        assertThat(fixture.work.authorityDeadline()).isBefore(Instant.now());
        assertThatThrownBy(() -> fixture.guard.get().verifyBeforeOrchestration())
            .isInstanceOfSatisfying(OrchestrationControlException.class, failure -> assertThat(failure.code()).isEqualTo("AUTHORING_SEGMENT_RELEASED"));
        verify(fixture.turn).close(); verify(fixture.snapshots).release(any());
        assertThat(fixture.record.protectedRequest()).doesNotContain("Bearer", "fresh-fixture");
    }

    @Test void aRenewalCannotChangeThePinnedTenantOrPrincipal() throws Exception {
        var fixture = new Fixture(); var calls = new AtomicInteger();
        when(fixture.platform.renew(any(), anyString())).thenReturn(fixture.lease());
        when(fixture.assertions.validateAssertionHeader(anyString())).thenReturn(fixture.auth.toBuilder().tenantId("foreign").build());
        var result = fixture.host.executeSegment(fixture.claim, fixture.request, request -> { calls.incrementAndGet(); return fixture.success(); });
        assertThat(result.status()).isEqualTo(AIExecutionStatus.PAUSED); assertThat(calls).hasValue(0);
        verify(fixture.budgets, never()).openGoalSegment(any(), any(), any());
    }
    @Test void waitingStatusPreservesTheActualQuestionAndOffersAnswerAndResume() throws Exception {
        var fixture = new Fixture();
        var waiting = fixture.record.goalTransition(ExecutionHandleStatus.PAUSED, null, "WORKFLOW_NEEDS_INPUT", null, fixture.now, true);
        when(fixture.repository.findById(fixture.id)).thenReturn(Optional.of(waiting));
        String question = "Which owned connection should I use?";
        var output = fixture.mapper.createObjectNode(); output.putObject("work").put("state", "NEEDS_INPUT").putArray("questions").add(question);
        var result = new AIExecutionResult<>(fixture.id, fixture.request.specialistId(), AIExecutionStatus.PAUSED,
            null, List.of(), Map.of("workflow", fixture.mapper.convertValue(output.path("work"), Map.class)), null, fixture.now, fixture.now);
        var snapshot = new ExecutionSnapshot(new ExecutionHandle(fixture.id, ExecutionDurability.DURABLE, ExecutionHandleStatus.PAUSED,
            waiting.deadline(), waiting.expiresAt(), "WORKFLOW_NEEDS_INPUT"), result);
        when(fixture.gateway.find(eq(fixture.id), any())).thenReturn(Optional.of(snapshot));
        var view = fixture.host.find(RuntimeResolvedIdentity.builder().authContext(fixture.auth).warnings(List.of()).build(), fixture.conversation, fixture.id);
        assertThat(view.getGoal()).containsEntry("state", "WAITING_FOR_INPUT").containsEntry("reasonMessage", question)
            .containsEntry("answer", question).containsEntry("questions", List.of(question)).containsEntry("canResume", true)
            .containsEntry("terminal", false).containsEntry("nextAction", "ANSWER_AND_RESUME");
        verify(fixture.platform, never()).renew(any(), any());
    }

    @Test void failedNativeControlDeliveryCanRetryTheSameKeyWithoutDroppingTheCommand() throws Exception {
        var fixture = new Fixture();
        when(fixture.gateway.controlGoal(eq(fixture.id), eq(GoalExecutionControl.PAUSE), any()))
            .thenThrow(new IllegalStateException("fixture-store-unavailable")).thenReturn(true);
        when(fixture.gateway.find(eq(fixture.id), any())).thenReturn(Optional.of(new ExecutionSnapshot(new ExecutionHandle(fixture.id,
            ExecutionDurability.DURABLE, ExecutionHandleStatus.PAUSED, fixture.record.deadline(), fixture.record.expiresAt(), "GOAL_USER_PAUSED"), null)));
        var control = new RuntimeDurableGoalHost.Control("PAUSE", fixture.goal, fixture.goal, fixture.work.sessionId(), fixture.work.turnId(), 1, 1, 1, null);
        var identity = RuntimeResolvedIdentity.builder().authContext(fixture.auth).warnings(List.of()).build();
        assertThatThrownBy(() -> fixture.host.control(identity, fixture.conversation, fixture.id, control, "pause-retry"))
            .isInstanceOf(IllegalStateException.class);
        fixture.host.control(identity, fixture.conversation, fixture.id, control, "pause-retry");
        fixture.host.control(identity, fixture.conversation, fixture.id, control, "pause-retry");
        verify(fixture.gateway, times(2)).controlGoal(eq(fixture.id), eq(GoalExecutionControl.PAUSE), any());
    }

    @Test void rejectedResumeCannotLeaveCanonicalReadyStateOrAnAppliedControlReceipt() throws Exception {
        var fixture = new Fixture();
        var values = new LinkedHashMap<>(fixture.metadata.get());
        var state = new LinkedHashMap<>((Map<String, Object>) values.get("authoringGoal")); state.put("state", "PAUSED");
        values.put("authoringGoal", state); fixture.metadata.set(values);
        when(fixture.platform.renew(any(), anyString())).thenReturn(fixture.lease());
        when(fixture.gateway.controlGoal(eq(fixture.id), eq(GoalExecutionControl.RESUME), any())).thenReturn(false);
        var control = new RuntimeDurableGoalHost.Control("RESUME", fixture.goal, fixture.goal, fixture.work.sessionId(), fixture.work.turnId(), 1, 0, 1, null);
        var identity = RuntimeResolvedIdentity.builder().authContext(fixture.auth).warnings(List.of()).build();
        var failure = catchThrowableOfType(() -> fixture.host.control(identity, fixture.conversation, fixture.id, control, "rejected-resume"), ResponseStatusException.class);
        assertThat(failure.getStatusCode().value()).isEqualTo(409);
        assertThat(fixture.metadata.get()).isEqualTo(values);
        verify(fixture.budgets, never()).openGoalSegment(any(), any(), any());
    }

    @Test void lifecycleNotificationCannotOverwriteANewerNativeControlWhileWaitingForCanonicalLock() throws Exception {
        var fixture = new Fixture();
        var paused = fixture.record.controlled(ExecutionHandleStatus.PAUSED, null, null, "GOAL_USER_PAUSED", fixture.now, Duration.ofDays(7));
        when(fixture.conversations.updateOrchestrationState(any(), eq(0L), any())).thenAnswer(call -> {
            when(fixture.repository.findById(fixture.id)).thenReturn(Optional.of(paused));
            var values = new LinkedHashMap<>(fixture.metadata.get());
            var state = new LinkedHashMap<>((Map<String, Object>) values.get("authoringGoal"));
            state.put("state", "PAUSED"); state.put("cancellationEpoch", 2L); values.put("authoringGoal", state);
            UnaryOperator<Map<String, Object>> mutation = call.getArgument(2); fixture.metadata.set(mutation.apply(values));
            return new ConversationOrchestrationState(call.getArgument(0), 1, fixture.metadata.get(), fixture.now);
        });
        fixture.host.executionUpdated(new ExecutionSnapshot(new ExecutionHandle(fixture.id, ExecutionDurability.DURABLE,
            ExecutionHandleStatus.RUNNING, fixture.record.deadline(), fixture.record.expiresAt(), null), null), fixture.goal);
        assertThat((Map<String, Object>) fixture.metadata.get().get("authoringGoal"))
            .containsEntry("state", "PAUSED").containsEntry("cancellationEpoch", 2L);
    }

    @Test void latePausedNotificationCannotFenceANewerRunningClaim() throws Exception {
        var fixture = new Fixture(); var before = fixture.metadata.get();
        fixture.host.executionUpdated(new ExecutionSnapshot(new ExecutionHandle(fixture.id, ExecutionDurability.DURABLE,
            ExecutionHandleStatus.PAUSED, fixture.record.deadline(), fixture.record.expiresAt(), "GOAL_USER_PAUSED"), null), fixture.goal);
        assertThat(fixture.metadata.get()).isEqualTo(before);
        verify(fixture.conversations, never()).updateOrchestrationState(any(), anyLong(), any());
    }

    @Test void narrowedAndEmptySourceSelectionsNeverRestoreTheBroaderPinnedDelegationGrant() {
        var mapper = new ObjectMapper();
        var pins = mapper.createObjectNode().put("one", "hash-one").put("two", "hash-two");
        var lease = mapper.createObjectNode(); lease.putArray("sourceIds").add("two");
        assertThat(RuntimeDurableGoalHost.effectiveSources(pins, lease)).isEqualTo(mapper.createObjectNode().put("two", "hash-two"));
        lease.putArray("sourceIds");
        assertThat(RuntimeDurableGoalHost.effectiveSources(pins, lease).isEmpty()).isTrue();
        lease.putArray("sourceIds").add("foreign");
        assertThatThrownBy(() -> RuntimeDurableGoalHost.effectiveSources(pins, lease)).isInstanceOf(ResponseStatusException.class);
        assertThat(pins.size()).isEqualTo(2);
    }

    @Test void diagnosticReadbackNeverCrossesTheInternalAuthoringIdentityBoundary() throws Exception {
        var fixture = new Fixture();
        for (var auth : List.of(fixture.auth.toBuilder().subjectId("authoring:foreign").sessionId("authoring:foreign").build(),
            fixture.auth.toBuilder().authMode(RuntimeAuthMode.PUBLIC_RUNTIME_AUTHENTICATED).callerType(RuntimeAuthCallerType.PUBLIC_BROWSER).build(),
            fixture.auth.toBuilder().tenantId("foreign-tenant").build(), fixture.auth.toBuilder().grantedScopes(List.of("chat:messages")).build())) {
            var identity = RuntimeResolvedIdentity.builder().authContext(auth).warnings(List.of()).build();
            assertThatThrownBy(() -> fixture.host.find(identity, fixture.conversation, fixture.id))
                .isInstanceOfSatisfying(ResponseStatusException.class, failure -> assertThat(failure.getStatusCode().value()).isEqualTo(404));
        }
        verify(fixture.gateway, never()).find(anyString(), any());
    }

    @Test void rejectedSdkAdmissionDoesNotLeaveAnActiveUnaddressableCanonicalGoal() throws Exception {
        var fixture = new Fixture();
        var profile = fixture.bundle.select("ACTIONS", null);
        when(fixture.dispatcher.validate(any(), any(), any())).thenReturn(profile);
        var policy = new ai.fabric.intent.orchestration.workflow.WorkflowPolicy(true, 8, 3, 16, 2, Duration.ofSeconds(120),
            new ai.fabric.intent.orchestration.workflow.WorkflowGoalPolicy(true, Duration.ofHours(1), Duration.ZERO, 8, 4, List.of()));
        when(fixture.registry.require(profile.specialist()).executionProfile().workflowPolicy()).thenReturn(policy);
        when(fixture.platform.renew(any(), nullable(String.class))).thenReturn(fixture.lease());
        when(fixture.gateway.submitGoal(any())).thenReturn(new ExecutionHandle("exec-rejected", ExecutionDurability.DURABLE,
            ExecutionHandleStatus.REJECTED, fixture.freshGrant, fixture.freshGrant, "EXECUTION_STATE_UNAVAILABLE"));
        var scope = new ConversationScope("tenant", "deployment", fixture.conversation, "authoring:" + fixture.work.sessionId());
        var identity = RuntimeResolvedIdentity.builder().authContext(fixture.auth).warnings(List.of()).build();
        when(fixture.conversations.messages(eq(scope), eq(0L), eq(500))).thenReturn(List.of(new ConversationMessage("user-one", scope,
            1, ConversationParticipantRole.USER, identity.ownerId(), "Original immutable query", fixture.work.turnId(), Map.of(), fixture.now)));
        var metadata = new LinkedHashMap<>(fixture.metadata.get());
        metadata.put("authoringGoal", Map.of("goalId", fixture.goal, "workId", "", "state", "QUEUED", "requestRevision", 1L, "cancellationEpoch", 0L));
        fixture.metadata.set(metadata);
        var body = new ChatQueryRequest(); body.setConversationId(fixture.conversation); body.setQuery("Original immutable query"); body.setConfigurationWork(fixture.work);
        var turn = new RuntimeConversationTurn(scope, new ConversationTurnLease(scope, "root-lease", 0, fixture.now, fixture.freshGrant),
            mock(OrchestrationTurnGuard.class), null);
        assertThatThrownBy(() -> fixture.host.submit(body, identity, turn, new ChatReceipt(UUID.randomUUID(), 1, true)))
            .isInstanceOfSatisfying(ResponseStatusException.class, failure -> assertThat(failure.getReason()).isEqualTo("AUTHORING_GOAL_EXECUTION_STATE_UNAVAILABLE"));
        assertThat(fixture.mapper.valueToTree(fixture.metadata.get()).path("authoringGoal").path("state").asText()).isEqualTo("BLOCKED");
        verify(fixture.conversations, never()).appendMessage(any(), any());
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> provider(T bean) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(bean); when(provider.getIfAvailable()).thenReturn(bean); return provider;
    }

    private static final class Fixture {
        final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        final Instant now = Instant.now();
        final Instant freshGrant = now.plusSeconds(3600);
        final String id = "exec-host-fixture", goal = "cag-host-fixture", conversation = "authoring-host-fixture";
        final RuntimeAuthContext auth;
        final ConfigurationWork work;
        final AIExecutionRequest<?> request;
        final DurableExecutionRecord record;
        final GoalExecutionClaim claim;
        final RuntimeDurableGoalHost host;
        final WorkflowAssistanceBundle bundle;
        final ai.fabric.execution.specialist.SpecialistRegistry registry = mock(ai.fabric.execution.specialist.SpecialistRegistry.class, RETURNS_DEEP_STUBS);
        final ConversationControlGateway conversations = mock(ConversationControlGateway.class);
        final AtomicReference<Map<String, Object>> metadata = new AtomicReference<>();
        final AuthoringPlatformClient platform = mock(AuthoringPlatformClient.class);
        final RuntimePrivateAssertionService assertions = mock(RuntimePrivateAssertionService.class);
        final RuntimeConfiguredWorkflowDispatcher dispatcher = mock(RuntimeConfiguredWorkflowDispatcher.class);
        final RuntimeBudgetTurnScopes budgets = mock(RuntimeBudgetTurnScopes.class);
        final RuntimeBudgetTurnScopes.Turn turn = mock(RuntimeBudgetTurnScopes.Turn.class);
        final AIExecutionConversationSnapshotRegistry snapshots = mock(AIExecutionConversationSnapshotRegistry.class);
        final AtomicReference<OrchestrationTurnGuard> guard = new AtomicReference<>();
        final AtomicReference<ConfigurationWork> installed = new AtomicReference<>();
        final RuntimeAuthProperties properties = new RuntimeAuthProperties();
        final AIExecutionGateway gateway = mock(AIExecutionGateway.class);
        final DurableExecutionRepository repository = mock(DurableExecutionRepository.class);
        final ObjectNode pins = mapper.createObjectNode().put("source-one", "sha256:" + "a".repeat(64));
        final DurableExecutionSecurity security;

        Fixture() throws Exception {
            ObjectNode compiled;
            try (var input = new ClassPathResource("workflow-assistance-goal-compiler-fixture.json").getInputStream()) { compiled = (ObjectNode) mapper.readTree(input).path("compiled"); }
            bundle = WorkflowAssistanceBundleTest.bundle(compiled); var profile = bundle.select("ACTIONS", null);
            var scopes = new ArrayList<>(profile.requiredScopes()); scopes.addAll(List.of("chat:query", "chat:messages", "chat:conversations"));
            auth = RuntimeAuthContext.builder().subjectId("authoring:cas-host-fixture").sessionId("authoring:cas-host-fixture")
                .subjectType(RuntimeAuthSubjectType.END_USER).authMode(RuntimeAuthMode.PRIVATE_RUNTIME_BACKEND_MEDIATED)
                .callerType(RuntimeAuthCallerType.TRUSTED_BACKEND).tenantId("tenant").deploymentId("deployment").customerId("customer")
                .issuer("issuer").audiences(List.of("deployment")).expiresAt(now.plusSeconds(180)).grantedScopes(scopes).build();
            var identity = RuntimeResolvedIdentity.builder().authContext(auth).warnings(List.of()).build();
            var trusted = RuntimeDurableGoalHost.trusted(identity, conversation);
            work = new ConfigurationWork("cas-host-fixture", "cat-host-fixture", "ACTIONS", profile.id(), "release", bundle.contentHash(),
                false, 2, true, goal, goal, now.minusSeconds(60), 1, 0, 1);
            var receipt = new ChatReceipt(UUID.randomUUID(), 1, true);
            var root = new RuntimeDurableGoalHost.Root(work, conversation, 1, work.turnId(), 0, receipt, pins, auth.getIssuer(), auth.getAudiences(), auth.getCustomerId());
            security = new DurableExecutionSecurity(mapper, "host-test-encryption-at-least-32-characters", "host-test-fingerprint-at-least-32-characters");
            when(platform.releaseId()).thenReturn("release");
            var payload = new LinkedHashMap<String, Object>();
            payload.put("input", Map.of("query", "Original immutable query")); payload.put("principalId", identity.ownerId());
            payload.put("subjectType", "conversation"); payload.put("subjectId", conversation); payload.put("tenantId", "tenant"); payload.put("deploymentId", "deployment");
            record = DurableExecutionRecord.queued(id, profile.specialist(), "a".repeat(64), security.accessFingerprint(trusted),
                security.idempotencyFingerprint(trusted, work.turnId()), "b".repeat(64), security.protect(payload, id + ":request"),
                freshGrant, now, Duration.ofDays(7)).withGoalAuthority(goal).claimed("worker", now, now.plusSeconds(120));
            claim = new GoalExecutionClaim(id, goal, "worker", freshGrant, 1, true);
            request = new AIExecutionRequest<>(profile.specialist(), mapper.valueToTree(payload.get("input")), trusted, null, freshGrant, work.turnId());
            when(repository.findById(id)).thenReturn(Optional.of(record));
            var scope = new ConversationScope("tenant", "deployment", conversation, identity.ownerId());
            when(conversations.status(scope)).thenReturn(new ConversationControlSnapshot(scope, ConversationControlState.AI_ACTIVE, 0, 0, null, null, now));
            metadata.set(Map.of(
                "authoringGoalRoots", Map.of(goal, security.protect(Map.of("root", mapper.valueToTree(root)), "canonical-goal-root:" + goal)),
                "authoringGoal", Map.of("goalId", goal, "workId", id, "state", "QUEUED", "requestRevision", 1L, "cancellationEpoch", 0L)));
            when(conversations.orchestrationState(eq(scope), eq(0L))).thenAnswer(call -> Optional.of(new ConversationOrchestrationState(scope, 0, metadata.get(), now)));
            when(conversations.updateOrchestrationState(eq(scope), eq(0L), any())).thenAnswer(call -> {
                UnaryOperator<Map<String, Object>> mutation = call.getArgument(2); metadata.set(mutation.apply(metadata.get()));
                return new ConversationOrchestrationState(scope, 1, metadata.get(), now);
            });
            when(assertions.validateAssertionHeader(anyString())).thenReturn(auth);
            var handovers = mock(RuntimeConversationHandoverService.class);
            var canonical = new RuntimeConversationTurn(scope, new ConversationTurnLease(scope, "lease-fresh", 0, now, now.plusSeconds(120)),
                mock(OrchestrationTurnGuard.class), new ApprovedConversationSnapshot(work.turnId(), identity.ownerId(), conversation,
                    "runtime-conversation", "a".repeat(64), 0, List.of(), now));
            when(handovers.beginAiTurn(any(), eq(conversation), eq(1L), eq(work.turnId()))).thenReturn(canonical);
            when(snapshots.approve(any(), any())).thenAnswer(call -> call.getArgument(0));
            when(budgets.openGoalSegment(any(), eq(receipt), any())).thenAnswer(call -> { guard.set(call.getArgument(2)); return turn; });
            when(turn.providerDiagnostics()).thenReturn(new RuntimeBudgetTurnScopes.ProviderDiagnostics(List.of(), 0));
            when(dispatcher.installSegment(any(), any(), any(), any(), any(), any(), any())).thenAnswer(call -> {
                installed.set(call.getArgument(0)); return (AutoCloseable) () -> { };
            });
            host = new RuntimeDurableGoalHost(bundle, platform, dispatcher, provider(gateway), provider(repository),
                provider(security), provider(registry), provider(snapshots), provider(conversations),
                provider(handovers), provider(budgets), assertions, properties, mapper, Clock.systemUTC());
        }
        RuntimeDurableGoalHost.Root root() {
            var protectedRoot = ((Map<?, ?>) metadata.get().get("authoringGoalRoots")).get(goal).toString();
            return mapper.convertValue(security.unprotect(protectedRoot, "canonical-goal-root:" + goal).get("root"), RuntimeDurableGoalHost.Root.class);
        }
        void root(RuntimeDurableGoalHost.Root root) {
            var copy = new LinkedHashMap<>(metadata.get());
            copy.put("authoringGoalRoots", Map.of(goal, security.protect(Map.of("root", mapper.valueToTree(root)), "canonical-goal-root:" + goal)));
            metadata.set(copy);
        }
        ObjectNode lease() {
            var result = mapper.createObjectNode().put("authorityId", goal).put("goalId", goal).put("sessionId", work.sessionId())
                .put("turnId", work.turnId()).put("conversationId", conversation).put("profileId", work.profileId()).put("releaseId", work.releaseId())
                .put("bundleHash", work.bundleHash()).put("previewOnly", false).put("validUntil", freshGrant.toString())
                .put("requestRevision", 1).put("cancellationEpoch", 0).put("segment", 1).put("workId", id).put("operationNamespace", id);
            result.set("sourceVersions", pins);
            result.putObject("runtimeHeaders").put(properties.getIngress().getPrivateAssertions().getAuthorizationHeader(), "Bearer fresh-fixture");
            return result;
        }
        AIExecutionResult<?> success() { return new AIExecutionResult<>(id, request.specialistId(), AIExecutionStatus.SUCCEEDED,
            mapper.createObjectNode().put("answer", "Done"), List.of(), Map.of(), null, now, now); }
    }
}
