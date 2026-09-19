package com.ai.fabric.runtime.review;

import ai.fabric.execution.context.ExecutionPrincipal;
import ai.fabric.execution.context.ExecutionPrincipalType;
import ai.fabric.execution.context.ExecutionSource;
import ai.fabric.execution.context.ExecutionSubjectRef;
import ai.fabric.execution.context.TrustedExecutionContext;
import ai.fabric.execution.review.ActionReviewRequest;
import ai.fabric.execution.review.ReviewDecisionGateway;
import ai.fabric.execution.review.ReviewTaskCreationResult;
import ai.fabric.execution.review.ReviewTaskDetailView;
import ai.fabric.execution.review.ReviewTaskView;
import ai.fabric.execution.review.TrustedReviewerContext;
import ai.fabric.execution.review.decision.ReviewDecisionRequest;
import ai.fabric.execution.review.decision.ReviewDecisionResult;
import com.ai.fabric.runtime.auth.RuntimeAuthContext;
import com.ai.fabric.runtime.auth.RuntimeAuthSubjectType;
import com.ai.fabric.runtime.auth.RuntimeResolvedIdentity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@ConditionalOnProperty(
    prefix = "ai.execution.reviews",
    name = "enabled",
    havingValue = "true"
)
public class HumanReviewService {

    private final ReviewDecisionGateway gateway;
    private final Clock clock;

    public HumanReviewService(ReviewDecisionGateway gateway, Clock clock) {
        this.gateway = gateway;
        this.clock = clock;
    }

    public ReviewTaskCreationResult create(
        RuntimeResolvedIdentity identity,
        String receiptId,
        CreateActionReviewRequest request,
        String idempotencyKey
    ) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Review request is required.");
        }
        return gateway.createActionReview(
            new ActionReviewRequest(
                requireText(receiptId, "receiptId", 120),
                HumanReviewRuntimeConfiguration.STANDARD_ACTION_POLICY,
                requireText(request.title(), "title", 160),
                requireText(request.summary(), "summary", 1000),
                requireText(idempotencyKey, "Idempotency-Key", 160)
            ),
            sourceContext(requireAuth(identity))
        );
    }

    public List<ReviewTaskView> inbox(RuntimeResolvedIdentity identity, int limit) {
        return gateway.inbox(reviewerContext(requireReviewer(identity)), limit);
    }

    public Optional<ReviewTaskDetailView> detail(
        RuntimeResolvedIdentity identity,
        String taskId
    ) {
        return gateway.findDetail(
            requireText(taskId, "taskId", 120),
            reviewerContext(requireReviewer(identity))
        );
    }

    public ReviewDecisionResult decide(
        RuntimeResolvedIdentity identity,
        String taskId,
        SubmitReviewDecisionRequest request
    ) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Review decision is required.");
        }
        if (request.decision() == null
            || (request.decision() != ai.fabric.execution.review.decision.ReviewDecisionType.APPROVE
                && request.decision() != ai.fabric.execution.review.decision.ReviewDecisionType.REJECT)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "The standard action-review policy accepts only APPROVE or REJECT."
            );
        }
        if (request.expectedVersion() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "expectedVersion must not be negative.");
        }
        return gateway.decide(
            new ReviewDecisionRequest(
                requireText(taskId, "taskId", 120),
                requireText(request.decisionId(), "decisionId", 160),
                request.decision(),
                request.expectedVersion(),
                null
            ),
            reviewerContext(requireReviewer(identity))
        );
    }

    private RuntimeAuthContext requireReviewer(RuntimeResolvedIdentity identity) {
        RuntimeAuthContext auth = requireAuth(identity);
        if (auth.getSubjectType() != RuntimeAuthSubjectType.END_USER) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Human Review requires an authenticated end-user reviewer identity."
            );
        }
        return auth;
    }

    private RuntimeAuthContext requireAuth(RuntimeResolvedIdentity identity) {
        RuntimeAuthContext auth = identity == null ? null : identity.getAuthContext();
        if (auth == null
            || !StringUtils.hasText(auth.getSubjectId())
            || !StringUtils.hasText(auth.getTenantId())
            || !StringUtils.hasText(auth.getDeploymentId())) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Verified reviewer tenant and deployment context are required."
            );
        }
        return auth;
    }

    private TrustedReviewerContext reviewerContext(RuntimeAuthContext auth) {
        return new TrustedReviewerContext(
            new ExecutionPrincipal(auth.getSubjectId().trim(), ExecutionPrincipalType.END_USER),
            auth.getTenantId(),
            scopes(auth),
            null,
            clock.instant()
        );
    }

    private TrustedExecutionContext sourceContext(RuntimeAuthContext auth) {
        boolean endUser = auth.getSubjectType() == RuntimeAuthSubjectType.END_USER;
        ExecutionPrincipalType principalType = endUser
            ? ExecutionPrincipalType.END_USER
            : auth.getSubjectType() == RuntimeAuthSubjectType.SYSTEM_PROCESS
                ? ExecutionPrincipalType.SYSTEM
                : ExecutionPrincipalType.SERVICE;
        return new TrustedExecutionContext(
            new ExecutionPrincipal(auth.getSubjectId().trim(), principalType),
            new ExecutionSubjectRef("runtime-subject", auth.getSubjectId().trim()),
            endUser ? ExecutionSource.INTERACTIVE : ExecutionSource.APPLICATION,
            auth.getTenantId(),
            auth.getDeploymentId(),
            scopes(auth),
            null,
            clock.instant()
        );
    }

    private Set<String> scopes(RuntimeAuthContext auth) {
        return auth.getGrantedScopes() == null
            ? Set.of()
            : Set.copyOf(new LinkedHashSet<>(auth.getGrantedScopes()));
    }

    private String requireText(String value, String field, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > maxLength) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                field + " is required and must not exceed " + maxLength + " characters."
            );
        }
        return normalized;
    }

    public record CreateActionReviewRequest(String title, String summary) {
    }

    public record SubmitReviewDecisionRequest(
        String decisionId,
        ai.fabric.execution.review.decision.ReviewDecisionType decision,
        long expectedVersion
    ) {
    }
}
