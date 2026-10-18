package com.ai.fabric.runtime.budget;

import static com.ai.fabric.runtime.budget.RuntimeBudgetFixture.*;
import static com.ai.fabric.runtime.budget.RuntimeBudgetTurnScopes.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import ai.fabric.intent.orchestration.OrchestrationContext;
import ai.fabric.intent.orchestration.OrchestrationResult;
import ai.fabric.intent.orchestration.OrchestrationResultType;
import ai.fabric.intent.orchestration.RAGOrchestrator;
import ai.fabric.intent.orchestration.control.OrchestrationControlException;
import ai.fabric.intent.orchestration.control.OrchestrationTurnGuard;
import ai.fabric.intent.orchestration.pipeline.DefaultOrchestrationPipeline;
import ai.fabric.intent.orchestration.pipeline.PipelineContext;
import ai.fabric.intent.orchestration.pipeline.PipelineStep;
import com.ai.fabric.budget.BudgetException;
import com.ai.fabric.budget.ProviderBudgetLedger.Approval;
import com.ai.fabric.budget.ProviderBudgetLedger.Limits;
import com.ai.fabric.budget.openai.OpenAiBudgetInvocation;
import com.ai.fabric.budget.openai.OpenAiBudgetException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RuntimeBudgetTurnScopesTest extends RuntimeBudgetFixture.TestScope {
    @Test
    void newChatAdmissionDenialIsTypedBeforeAnyEffects() throws Exception {
        var fixture = new RuntimeBudgetFixture();
        var approval = new Approval(APPROVAL.id(), SCOPE, new Limits(0, 100_000, 2_000_000_000L), APPROVAL.expiresAt());
        var budgets = fixture.service();
        activate(budgets, budgets.initialize(allocation(approval)));
        var turns = new RuntimeBudgetTurnScopes(budgets);
        var effects = new AtomicInteger();
        assertThatThrownBy(() -> {
            try (var turn = turns.openChat(chatIdentity(), null)) { effects.incrementAndGet(); }
        }).isInstanceOfSatisfying(OrchestrationControlException.class,
            error -> assertThat(error.code()).isEqualTo("PROVIDER_BUDGET_LIMIT_EXCEEDED"));
        assertThat(effects).hasValue(0);
        assertUnscopedDenied(turns);
        assertThat(budgets.readCurrent().stopped()).isFalse();
    }

    @Test
    void capturedOperationObserverRetainsOriginAcrossAsyncCallbackAndConcurrentTurn() throws Exception {
        var turns = new RuntimeBudgetTurnScopes(new RuntimeBudgetFixture().register());
        try (var first = turns.openChat(chatIdentity(), null)) {
            var captured = turns.capture(OpenAiBudgetInvocation.Operation.CHAT_COMPLETION);
            var failure = failure(captured);
            try (var executor = Executors.newSingleThreadExecutor()) {
                executor.submit(() -> {
                    assertUnscopedDenied(turns);
                    try (var other = turns.openChat(chatIdentity(), null)) {
                        captured.observer().onDenied(failure);
                        assertThat(other.interruption()).isEmpty();
                        var result = success();
                        other.verifyBeforeResultCommit(result);
                        assertThat(result.isSuccess()).isTrue();
                    }
                }).get(5, TimeUnit.SECONDS);
            }
            assertThat(first.interruption()).get().extracting(BudgetInterruption::providerAttemptId).isEqualTo(failure.attemptId());
        }
        assertUnscopedDenied(turns);
    }

    @Test
    void laterSwallowedProviderDenialPreservesReadAndWriteReceiptsAndExplicitlyInterruptsResult() throws Exception {
        var turns = new RuntimeBudgetTurnScopes(new RuntimeBudgetFixture().register());
        var effects = new AtomicInteger();
        var read = receipt("read-1");
        var write = receipt("write-1");
        var actions = List.of(read.getData(), write.getData());
        var children = List.of(read, write);
        var data = Map.<String, Object>of("actions", actions);
        var compound = OrchestrationResult.builder().type(OrchestrationResultType.COMPOUND_HANDLED).success(true)
            .data(data).children(children).metadata(Map.of("existing", "retained"))
            .sanitizedPayload(Map.of("actions", actions, "safeSummary", "Everything succeeded"))
            .build();
        try (var turn = turns.openChat(chatIdentity(), null)) {
            var invocation = turns.capture(OpenAiBudgetInvocation.Operation.CHAT_COMPLETION);
            var pipeline = new DefaultOrchestrationPipeline(List.of(
                step(1, context -> { turn.verifyBeforeAction(null, null); effects.incrementAndGet(); return context; }),
                step(2, context -> {
                    turn.verifyBeforeAction(null, null);
                    effects.incrementAndGet();
                    return context.toBuilder().intentResult(compound).build();
                }),
                step(3, context -> {
                    try {
                        invocation.observer().onDenied(failure(invocation));
                        throw new BudgetException(BudgetException.Code.LIMIT_EXCEEDED);
                    } catch (BudgetException swallowedByGeneration) {
                        // Model the framework's post-action generation fallback retaining its action result.
                    }
                    return context;
                })
            ));

            var result = new RAGOrchestrator(pipeline).orchestrate("complete approved actions",
                OrchestrationContext.builder().userId("owner").orchestrationTurnGuard(turn).build());

            assertThat(effects).hasValue(2);
            assertThat(result).isSameAs(compound);
            assertThat(result.isSuccess()).isFalse();
            assertThat(result.getErrorCode()).isEqualTo(INTERRUPTION_CODE);
            assertThat(result.getData()).isSameAs(data);
            assertThat(result.getChildren()).isSameAs(children);
            assertThat(result.getSanitizedPayload().get("actions")).isSameAs(actions);
            assertThat(read.isSuccess()).isTrue();
            assertThat(write.isSuccess()).isTrue();
            assertThat(result.getMetadata()).containsEntry("existing", "retained");
            assertThat(result.getMetadata().get(INTERRUPTION_KEY)).isInstanceOf(BudgetInterruption.class);
            assertThat(result.getMetadata().get(REPORTED_OUTCOME_KEY))
                .isEqualTo(new ReportedOutcome(OrchestrationResultType.COMPOUND_HANDLED, true, null));
            assertThat(result.getSanitizedPayload()).containsEntry("success", false);
            assertThat(result.getSanitizedPayload().get("safeSummary")).isNotEqualTo("Everything succeeded");
        }
    }

    @Test
    void durableStopAndObservedDenialDoNotRevokePreviouslyAuthorizedNonpaidActions() throws Exception {
        var fixture = new RuntimeBudgetFixture();
        var turns = new RuntimeBudgetTurnScopes(fixture.register());
        var authorization = mock(OrchestrationTurnGuard.class);
        try (var turn = turns.openChat(chatIdentity(), authorization)) {
            turn.verifyBeforeOrchestration();
            fixture.ledger.stop(APPROVAL.id(), SCOPE, "OWNER_STOP");
            deny(turns);
            assertThatCode(() -> turn.verifyBeforeAction(null, null)).doesNotThrowAnyException();
            verify(authorization).verifyBeforeAction(null, null);
        }
    }

    @Test
    void existingAuthorizationGuardStillDeniesAnActionWithoutNewAuthorityGrants() throws Exception {
        var turns = new RuntimeBudgetTurnScopes(new RuntimeBudgetFixture().register());
        var authorization = mock(OrchestrationTurnGuard.class);
        doThrow(new OrchestrationControlException("ACTION_DENIED", "Action denied."))
            .when(authorization).verifyBeforeAction(null, null);
        try (var turn = turns.openChat(chatIdentity(), authorization)) {
            assertThatThrownBy(() -> turn.verifyBeforeAction(null, null)).isInstanceOfSatisfying(OrchestrationControlException.class,
                error -> assertThat(error.code()).isEqualTo("ACTION_DENIED"));
        }
    }

    @Test
    void resultAwareCompositionRetainsDelegateAndNullValuedMetadata() throws Exception {
        var turns = new RuntimeBudgetTurnScopes(new RuntimeBudgetFixture().register());
        var delegate = mock(OrchestrationTurnGuard.class);
        try (var turn = turns.openChat(chatIdentity(), delegate)) {
            var metadata = new LinkedHashMap<String, Object>();
            metadata.put("optional", null);
            var result = success();
            result.setMetadata(metadata);
            deny(turns);
            assertThatCode(() -> turn.verifyBeforeResultCommit(result)).doesNotThrowAnyException();
            verify(delegate).verifyBeforeResultCommit(result);
            verify(delegate, never()).verifyBeforeResultCommit();
            assertThat(result.getMetadata()).containsEntry("optional", null);
        }
    }

    @Test
    void suggestionsScopeExposesTypedDenialAndConsumesTheSharedInteractiveAttempt() throws Exception {
        var budgets = new RuntimeBudgetFixture().register();
        var turns = new RuntimeBudgetTurnScopes(budgets);
        try (var turn = turns.openSuggestions(identity("customer", "deployment", List.of("chat:suggestions")))) {
            assertThat(turn.chatReceipt()).isPresent();
            deny(turns);
            assertThat(turn.interruption()).isPresent();
        }
        assertThat(budgets.readCurrent().chatAttempts()).isEqualTo(1);
        assertThatThrownBy(() -> turns.openSuggestions(chatIdentity())).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }

    @Test
    void fiftyQueriesAndSuggestionsCombinedExhaustOneDurableNonrefundableIngressCeiling() throws Exception {
        var fixture = new RuntimeBudgetFixture();
        var budgets = fixture.register();
        var turns = new RuntimeBudgetTurnScopes(budgets);
        var suggestionIdentity = identity("customer", "deployment", List.of("chat:suggestions"));
        for (int index = 0; index < 25; index++) {
            try (var query = turns.openChat(chatIdentity(), null)) {
                assertThat(query.chatReceipt()).isPresent();
            }
            try (var suggestions = turns.openSuggestions(suggestionIdentity)) {
                assertThat(suggestions.chatReceipt()).isPresent();
            }
        }
        assertThat(budgets.readCurrent().chatAttempts()).isEqualTo(50);
        var restarted = new RuntimeBudgetTurnScopes(fixture.service());
        assertThatThrownBy(() -> restarted.openChat(chatIdentity(), null)).isInstanceOf(OrchestrationControlException.class);
        assertThatThrownBy(() -> restarted.openSuggestions(suggestionIdentity)).isInstanceOf(OrchestrationControlException.class);
        assertThatThrownBy(() -> restarted.openSuggestions(chatIdentity()))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(budgets.readCurrent().chatAttempts()).isEqualTo(50);
        assertThat(budgets.readCurrent().stopped()).isFalse();
    }

    @Test
    void nestedScopeCannotDoubleCountAndAClosedScopeCannotContaminateTheNextRequest() throws Exception {
        var budgets = new RuntimeBudgetFixture().register();
        var turns = new RuntimeBudgetTurnScopes(budgets);
        OpenAiBudgetInvocation previousOperation;
        try (var first = turns.openChat(chatIdentity(), null)) {
            previousOperation = turns.capture(OpenAiBudgetInvocation.Operation.CHAT_COMPLETION);
            assertThatThrownBy(() -> turns.openChat(chatIdentity(), null)).isInstanceOf(IllegalStateException.class);
        }
        try (var next = turns.openChat(chatIdentity(), null)) {
            previousOperation.observer().onDenied(failure(previousOperation));
            assertThat(next.interruption()).isEmpty();
        }
        assertThat(budgets.readCurrent().chatAttempts()).isEqualTo(2);
    }

    @Test
    void noProviderInterruptionLeavesAnAlreadyAcceptedResultUnchangedEvenIfCampaignStops() throws Exception {
        var fixture = new RuntimeBudgetFixture();
        var turns = new RuntimeBudgetTurnScopes(fixture.register());
        try (var turn = turns.openChat(chatIdentity(), null)) {
            fixture.ledger.stop(APPROVAL.id(), SCOPE, "OWNER_STOP");
            var result = success();
            turn.verifyBeforeResultCommit(result);
            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getMetadata()).doesNotContainKey(INTERRUPTION_KEY);
        }
    }

    @Test
    void explicitIndexingWorkerCaptureIsEmbeddingOnlyAndCannotReportSuccessAfterDenial() throws Exception {
        var budgets = new RuntimeBudgetFixture().register();
        var turns = new RuntimeBudgetTurnScopes(budgets);
        try (var work = turns.openIndexingWork()) {
            var invocation = turns.capture(OpenAiBudgetInvocation.Operation.EMBEDDING);
            assertThatThrownBy(() -> turns.capture(OpenAiBudgetInvocation.Operation.CHAT_COMPLETION))
                .isInstanceOf(OpenAiBudgetException.class);
            try (var executor = Executors.newSingleThreadExecutor()) {
                executor.submit(() -> invocation.observer().onDenied(failure(invocation))).get(5, TimeUnit.SECONDS);
            }
            assertThat(work.interruption()).isPresent();
            assertThatThrownBy(work::verifyComplete).isInstanceOf(OrchestrationControlException.class);
        }
        assertThat(budgets.readCurrent().chatAttempts()).isZero();
        assertUnscopedDenied(turns);
    }

    private static OpenAiBudgetInvocation.Denial failure(OpenAiBudgetInvocation invocation) {
        return new OpenAiBudgetInvocation.Denial(invocation.operationId(), UUID.randomUUID(), BudgetException.Code.LIMIT_EXCEEDED, null);
    }

    private static void deny(RuntimeBudgetTurnScopes turns) {
        var invocation = turns.capture(OpenAiBudgetInvocation.Operation.CHAT_COMPLETION);
        invocation.observer().onDenied(failure(invocation));
    }

    private static void assertUnscopedDenied(RuntimeBudgetTurnScopes turns) {
        assertThatThrownBy(() -> turns.capture(OpenAiBudgetInvocation.Operation.CHAT_COMPLETION))
            .isInstanceOfSatisfying(OpenAiBudgetException.class,
                error -> assertThat(error.code()).isEqualTo(OpenAiBudgetException.Code.INVOCATION_CONTEXT_REQUIRED));
    }

    private static OrchestrationResult success() {
        return OrchestrationResult.builder().type(OrchestrationResultType.INFORMATION_PROVIDED).success(true).build();
    }

    private static OrchestrationResult receipt(String id) {
        return OrchestrationResult.builder().type(OrchestrationResultType.ACTION_EXECUTED).success(true)
            .data(Map.of("receiptId", id, "success", true)).build();
    }

    private static PipelineStep step(int order, java.util.function.UnaryOperator<PipelineContext> process) {
        return new PipelineStep() {
            @Override public PipelineContext process(PipelineContext context) { return process.apply(context); }
            @Override public String getStepName() { return "budget-turn-test-" + order; }
            @Override public int getOrder() { return order; }
        };
    }
}
