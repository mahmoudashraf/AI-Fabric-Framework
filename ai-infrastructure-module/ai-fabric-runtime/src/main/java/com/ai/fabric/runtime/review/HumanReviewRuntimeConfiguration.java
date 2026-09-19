package com.ai.fabric.runtime.review;

import ai.fabric.execution.context.ExecutionPrincipalType;
import ai.fabric.execution.review.auth.ReviewAuthorizationOperation;
import ai.fabric.execution.review.auth.ReviewerAuthorization;
import ai.fabric.execution.review.auth.ReviewerAuthorizer;
import ai.fabric.execution.review.decision.ReviewDecisionType;
import ai.fabric.execution.review.dispatch.ReviewDispatchResult;
import ai.fabric.execution.review.dispatch.ReviewTaskDispatcher;
import ai.fabric.execution.review.policy.ReviewPolicyDefinition;
import ai.fabric.execution.review.policy.ReviewPolicyId;
import ai.fabric.execution.review.policy.ReviewType;
import com.ai.fabric.runtime.auth.RuntimeScopeCatalog;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Set;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
    prefix = "ai.execution.reviews",
    name = "enabled",
    havingValue = "true"
)
public class HumanReviewRuntimeConfiguration {

    public static final ReviewPolicyId STANDARD_ACTION_POLICY =
        ReviewPolicyId.of("loomai-standard-action-review", "1");
    public static final String REVIEWER_AUTHORIZER =
        "loomai-runtime-reviewer-authorizer@1";
    public static final String LOCAL_INBOX_DISPATCHER =
        "loomai-local-review-inbox@1";

    @Bean
    ReviewPolicyDefinition loomAiStandardActionReviewPolicy() {
        return new ReviewPolicyDefinition(
            STANDARD_ACTION_POLICY,
            ReviewType.OPERATIONAL_REVIEW,
            Set.of(ReviewDecisionType.APPROVE, ReviewDecisionType.REJECT),
            REVIEWER_AUTHORIZER,
            LOCAL_INBOX_DISPATCHER,
            Set.of(),
            true,
            Duration.ofHours(24),
            null,
            null,
            null,
            null,
            null,
            null
        );
    }

    @Bean
    ReviewerAuthorizer loomAiRuntimeReviewerAuthorizer() {
        return new ReviewerAuthorizer() {
            @Override
            public String id() {
                return REVIEWER_AUTHORIZER;
            }

            @Override
            public ReviewerAuthorization authorize(
                ai.fabric.execution.review.auth.ReviewAuthorizationRequest request,
                ai.fabric.execution.review.TrustedReviewerContext reviewer
            ) {
                if (!STANDARD_ACTION_POLICY.equals(request.task().policyId())) {
                    return ReviewerAuthorization.deny("REVIEW_POLICY_NOT_SUPPORTED");
                }
                if (reviewer.reviewer().principalType() != ExecutionPrincipalType.END_USER) {
                    return ReviewerAuthorization.deny("REVIEWER_MUST_BE_AN_AUTHENTICATED_USER");
                }
                String requiredScope = request.operation() == ReviewAuthorizationOperation.DECIDE
                    ? RuntimeScopeCatalog.REVIEW_TASK_DECIDE
                    : RuntimeScopeCatalog.REVIEW_TASK_VIEW;
                if (!reviewer.grantedScopes().contains(requiredScope)) {
                    return ReviewerAuthorization.deny("REVIEWER_SCOPE_REQUIRED");
                }
                return ReviewerAuthorization.allow();
            }
        };
    }

    @Bean
    ReviewTaskDispatcher loomAiLocalReviewInboxDispatcher() {
        return new ReviewTaskDispatcher() {
            @Override
            public String id() {
                return LOCAL_INBOX_DISPATCHER;
            }

            @Override
            public ReviewDispatchResult dispatch(
                ai.fabric.execution.review.dispatch.ReviewDispatchRequest request
            ) {
                return ReviewDispatchResult.accepted(
                    "deployment-review-inbox:" + request.task().taskId()
                );
            }
        };
    }
}
