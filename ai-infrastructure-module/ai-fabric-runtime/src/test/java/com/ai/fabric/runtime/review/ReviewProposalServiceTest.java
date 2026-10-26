package com.ai.fabric.runtime.review;

import ai.fabric.execution.action.ActionProposalView;
import ai.fabric.execution.action.ActionProposalReceiptStatus;
import ai.fabric.execution.context.*;
import ai.fabric.execution.gateway.*;
import ai.fabric.execution.review.*;
import com.ai.fabric.runtime.auth.RuntimeResolvedIdentity;
import com.ai.fabric.runtime.budget.RuntimeBudgetTurnScopes;
import ai.fabric.intent.orchestration.control.OrchestrationControlException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class ReviewProposalServiceTest {
    private final AIExecutionGateway executions = mock(AIExecutionGateway.class);
    private final ReviewDecisionGateway reviews = mock(ReviewDecisionGateway.class);
    private final HumanReviewService contexts = mock(HumanReviewService.class);
    private final ReviewProposalService service = new ReviewProposalService(executions, reviews, contexts);
    private final RuntimeResolvedIdentity identity = RuntimeResolvedIdentity.builder().build();
    private final Instant now = Instant.parse("2026-10-26T09:00:00Z");
    private final TrustedExecutionContext source = new TrustedExecutionContext(
        new ExecutionPrincipal("actual-initiator", ExecutionPrincipalType.END_USER),
        new ExecutionSubjectRef("runtime-subject", "actual-initiator"), ExecutionSource.INTERACTIVE,
        "tenant-one", "deployment-one", Set.of("action:registered_write"), null, now);

    @Test
    void submitsOnlyTheGenuineReceiptUsingTheSameTrustedContextAndStableKeys() {
        when(contexts.sourceContext(identity)).thenReturn(source);
        when(executions.execute(any())).thenReturn(new AIExecutionResult<>("invocation-one",
            ReviewProposalConfiguration.SPECIALIST_ID, AIExecutionStatus.CONFIRMATION_REQUIRED, null,
            List.of(), Map.of(), null, now, now,
            new ActionProposalView("receipt-one", "registered_write", "Review this action?",
                ActionProposalReceiptStatus.PROPOSED, now, now.plusSeconds(300))));

        service.propose(identity, request(), "request-key");

        ArgumentCaptor<AIExecutionRequest<String>> execution = ArgumentCaptor.forClass(AIExecutionRequest.class);
        verify(executions).execute(execution.capture());
        assertThat(execution.getValue().specialistId()).isEqualTo(ReviewProposalConfiguration.SPECIALIST_ID);
        assertThat(execution.getValue().input()).isEqualTo("Prepare the requested action.");
        assertThat(execution.getValue().trustedExecutionContext()).isSameAs(source);
        assertThat(execution.getValue().idempotencyKey()).isEqualTo("request-key");
        ArgumentCaptor<ActionReviewRequest> review = ArgumentCaptor.forClass(ActionReviewRequest.class);
        verify(reviews).createActionReview(review.capture(), same(source));
        assertThat(review.getValue().receiptId()).isEqualTo("receipt-one");
        assertThat(review.getValue().policyId()).isEqualTo(HumanReviewRuntimeConfiguration.STANDARD_ACTION_POLICY);
        assertThat(review.getValue().idempotencyKey()).isEqualTo("request-key");
        assertThat(review.getValue().title()).isEqualTo("Review application request");
        assertThat(review.getValue().summary()).isEqualTo("Proposed action: registered_write\nReview this action?\nRequested facts: A safe application-owned summary.");
    }

    @Test
    void doesNotManufactureReviewWhenModelAnswersWithoutAProposal() {
        when(contexts.sourceContext(identity)).thenReturn(source);
        when(executions.execute(any())).thenReturn(new AIExecutionResult<>("invocation-two",
            ReviewProposalConfiguration.SPECIALIST_ID, AIExecutionStatus.SUCCEEDED,
            "Please supply the missing details.", List.of(), Map.of(), null, now, now));
        var result = service.propose(identity, request(), "request-key");
        assertThat(result.created()).isFalse();
        assertThat(result.failure().reason()).isEqualTo("ACTION_PROPOSAL_NOT_CREATED");
        verifyNoInteractions(reviews);
    }

    @Test
    void preservesVisibleNativeFailureAndNeverRetriesAutomatically() {
        when(contexts.sourceContext(identity)).thenReturn(source);
        when(executions.execute(any())).thenReturn(new AIExecutionResult<>("invocation-three",
            ReviewProposalConfiguration.SPECIALIST_ID, AIExecutionStatus.DENIED, null, List.of(), Map.of(),
            new AIExecutionFailure("ACTION_AUTHORITY_INTERSECTION_FAILED", "The caller is not authorized.", false), now, now));
        var result = service.propose(identity, request(), "request-key");
        assertThat(result.failure().reason()).isEqualTo("ACTION_AUTHORITY_INTERSECTION_FAILED");
        assertThat(result.failure().publicMessage()).isEqualTo("The caller is not authorized.");
        verify(executions, times(1)).execute(any());
        verifyNoInteractions(reviews);
    }

    @Test
    void rejectsUnboundedOrMissingInputBeforeProviderWork() {
        assertThatThrownBy(() -> service.propose(identity, request(), " "))
            .hasMessageContaining("Idempotency-Key");
        assertThatThrownBy(() -> service.propose(identity,
            new ReviewProposalService.Request("x".repeat(8001), "title", "summary"), "key"))
            .hasMessageContaining("8000");
        verifyNoInteractions(executions, reviews, contexts);
    }

    @Test
    void disabledOrEmptyConfigurationNeverGrantsImplicitAllActions() {
        assertThat(new ReviewProposalProperties(false, null).allowedActions()).isEmpty();
        assertThatThrownBy(() -> new ReviewProposalProperties(true, Set.of()))
            .hasMessageContaining("explicitly configured");
        var definition = new ReviewProposalConfiguration().applicationActionReviewSpecialist(
            new ReviewProposalProperties(true, Set.of("registered_write")));
        assertThat(definition.executionProfile().requestedCapabilities().proposableWriteActions())
            .containsExactly("registered_write");
        assertThat(definition.executionProfile().requestedCapabilities().retrievalEnabled()).isFalse();
    }

    @Test
    void opensAndClosesExistingProviderAccountingAroundGatewayInvocation() {
        var budgets = mock(RuntimeBudgetTurnScopes.class);
        var turn = mock(RuntimeBudgetTurnScopes.Turn.class);
        service.setBudgetTurns(budgets);
        when(contexts.sourceContext(identity)).thenReturn(source);
        when(budgets.openChat(identity, null)).thenReturn(turn);
        when(turn.interruption()).thenReturn(Optional.empty());
        when(executions.execute(any())).thenReturn(new AIExecutionResult<>("invocation-budget",
            ReviewProposalConfiguration.SPECIALIST_ID, AIExecutionStatus.SUCCEEDED,
            "No proposal.", List.of(), Map.of(), null, now, now));
        service.propose(identity, request(), "key");
        var order = inOrder(budgets, turn, executions);
        order.verify(budgets).openChat(identity, null);
        order.verify(turn).verifyBeforeOrchestration();
        order.verify(executions).execute(any());
        order.verify(turn).interruption();
        order.verify(turn).close();
        verifyNoInteractions(reviews);
    }

    @Test
    void budgetDenialDoesNotCallTheProviderOrCreateAReview() {
        var budgets = mock(RuntimeBudgetTurnScopes.class);
        service.setBudgetTurns(budgets);
        when(contexts.sourceContext(identity)).thenReturn(source);
        when(budgets.openChat(identity, null)).thenThrow(
            new OrchestrationControlException("PROVIDER_BUDGET_EXHAUSTED", "Denied."));
        assertThat(service.propose(identity, request(), "key").failure().reason())
            .isEqualTo("PROVIDER_BUDGET_EXHAUSTED");
        verifyNoInteractions(executions, reviews);
    }

    private ReviewProposalService.Request request() {
        return new ReviewProposalService.Request("Prepare the requested action.",
            "Review application request", "A safe application-owned summary.");
    }
}
