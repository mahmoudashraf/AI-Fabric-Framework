package com.ai.fabric.runtime.review;

import ai.fabric.execution.context.ExecutionPrincipalType;
import ai.fabric.execution.review.ActionReviewRequest;
import ai.fabric.execution.review.ReviewDecisionGateway;
import ai.fabric.execution.review.TrustedReviewerContext;
import ai.fabric.execution.review.decision.ReviewDecisionRequest;
import ai.fabric.execution.review.decision.ReviewDecisionType;
import com.ai.fabric.runtime.auth.RuntimeAuthCallerType;
import com.ai.fabric.runtime.auth.RuntimeAuthContext;
import com.ai.fabric.runtime.auth.RuntimeAuthMode;
import com.ai.fabric.runtime.auth.RuntimeAuthSubjectType;
import com.ai.fabric.runtime.auth.RuntimeResolvedIdentity;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class HumanReviewServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-19T10:00:00Z");

    @Test
    void createsActionReviewFromVerifiedDeploymentContext() {
        ReviewDecisionGateway gateway = mock(ReviewDecisionGateway.class);
        HumanReviewService service = new HumanReviewService(gateway, Clock.fixed(NOW, ZoneOffset.UTC));

        service.create(
            identity(RuntimeAuthSubjectType.END_USER, List.of("review:action-proposals:create")),
            "receipt-1",
            new HumanReviewService.CreateActionReviewRequest("Refund request", "Refund order 123 after validation."),
            "review-request-1"
        );

        ArgumentCaptor<ActionReviewRequest> request = ArgumentCaptor.forClass(ActionReviewRequest.class);
        ArgumentCaptor<ai.fabric.execution.context.TrustedExecutionContext> context =
            ArgumentCaptor.forClass(ai.fabric.execution.context.TrustedExecutionContext.class);
        verify(gateway).createActionReview(request.capture(), context.capture());
        assertThat(request.getValue().receiptId()).isEqualTo("receipt-1");
        assertThat(request.getValue().policyId()).isEqualTo(HumanReviewRuntimeConfiguration.STANDARD_ACTION_POLICY);
        assertThat(context.getValue().tenantId()).isEqualTo("tenant-1");
        assertThat(context.getValue().deploymentId()).isEqualTo("dep-1");
    }

    @Test
    void reviewerProjectionUsesEndUserIdentityAndGrantedScopes() {
        ReviewDecisionGateway gateway = mock(ReviewDecisionGateway.class);
        HumanReviewService service = new HumanReviewService(gateway, Clock.fixed(NOW, ZoneOffset.UTC));

        service.inbox(identity(RuntimeAuthSubjectType.END_USER, List.of("review:tasks:view")), 25);

        ArgumentCaptor<TrustedReviewerContext> reviewer = ArgumentCaptor.forClass(TrustedReviewerContext.class);
        verify(gateway).inbox(reviewer.capture(), org.mockito.ArgumentMatchers.eq(25));
        assertThat(reviewer.getValue().reviewer().principalType()).isEqualTo(ExecutionPrincipalType.END_USER);
        assertThat(reviewer.getValue().tenantId()).isEqualTo("tenant-1");
        assertThat(reviewer.getValue().grantedScopes()).containsExactly("review:tasks:view");
    }

    @Test
    void rejectsNonEndUserReviewerAndUnsupportedDecision() {
        ReviewDecisionGateway gateway = mock(ReviewDecisionGateway.class);
        HumanReviewService service = new HumanReviewService(gateway, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> service.inbox(identity(RuntimeAuthSubjectType.INTERNAL_PLATFORM_USER, List.of()), 10))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("authenticated end-user reviewer");

        assertThatThrownBy(() -> service.decide(
            identity(RuntimeAuthSubjectType.END_USER, List.of("review:tasks:decide")),
            "task-1",
            new HumanReviewService.SubmitReviewDecisionRequest("decision-1", ReviewDecisionType.ESCALATE, 0)
        )).isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("APPROVE or REJECT");
    }

    @Test
    void forwardsOptimisticVersionForApproveDecision() {
        ReviewDecisionGateway gateway = mock(ReviewDecisionGateway.class);
        HumanReviewService service = new HumanReviewService(gateway, Clock.fixed(NOW, ZoneOffset.UTC));

        service.decide(
            identity(RuntimeAuthSubjectType.END_USER, List.of("review:tasks:decide")),
            "task-1",
            new HumanReviewService.SubmitReviewDecisionRequest("decision-1", ReviewDecisionType.APPROVE, 4)
        );

        ArgumentCaptor<ReviewDecisionRequest> decision = ArgumentCaptor.forClass(ReviewDecisionRequest.class);
        verify(gateway).decide(decision.capture(), any(TrustedReviewerContext.class));
        assertThat(decision.getValue().taskId()).isEqualTo("task-1");
        assertThat(decision.getValue().expectedVersion()).isEqualTo(4);
        assertThat(decision.getValue().decision()).isEqualTo(ReviewDecisionType.APPROVE);
    }

    private RuntimeResolvedIdentity identity(RuntimeAuthSubjectType subjectType, List<String> scopes) {
        return RuntimeResolvedIdentity.builder()
            .authContext(RuntimeAuthContext.builder()
                .subjectId("reviewer@example.com")
                .subjectType(subjectType)
                .authMode(RuntimeAuthMode.PRIVATE_RUNTIME_BACKEND_MEDIATED)
                .callerType(RuntimeAuthCallerType.TRUSTED_BACKEND)
                .deploymentId("dep-1")
                .customerId("customer-1")
                .tenantId("tenant-1")
                .sessionId("review-session")
                .issuer("loomai-platform-customer-review")
                .expiresAt(NOW.plusSeconds(300))
                .grantedScopes(scopes)
                .build())
            .warnings(List.of())
            .build();
    }
}
