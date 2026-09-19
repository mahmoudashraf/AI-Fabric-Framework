package com.ai.fabric.runtime.web;

import ai.fabric.execution.review.ReviewTaskCreationResult;
import ai.fabric.execution.review.ReviewTaskDetailView;
import ai.fabric.execution.review.ReviewTaskView;
import ai.fabric.execution.review.decision.ReviewDecisionResult;
import com.ai.fabric.runtime.auth.RuntimeRequestAuthResolver;
import com.ai.fabric.runtime.auth.RuntimeResolvedIdentity;
import com.ai.fabric.runtime.auth.RuntimeScopeCatalog;
import com.ai.fabric.runtime.review.HumanReviewService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/reviews/v1")
@ConditionalOnProperty(
    prefix = "ai.execution.reviews",
    name = "enabled",
    havingValue = "true"
)
public class HumanReviewController {

    private final RuntimeRequestAuthResolver authResolver;
    private final HumanReviewService reviewService;

    public HumanReviewController(
        RuntimeRequestAuthResolver authResolver,
        HumanReviewService reviewService
    ) {
        this.authResolver = authResolver;
        this.reviewService = reviewService;
    }

    @PostMapping("/action-proposals/{receiptId}")
    public ResponseEntity<ReviewTaskCreationResult> create(
        @PathVariable String receiptId,
        @RequestBody HumanReviewService.CreateActionReviewRequest body,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        HttpServletRequest request
    ) {
        RuntimeResolvedIdentity identity = authorize(
            request,
            RuntimeScopeCatalog.REVIEW_ACTION_PROPOSAL_CREATE,
            "/api/reviews/v1/action-proposals/{receiptId}"
        );
        ReviewTaskCreationResult result = reviewService.create(
            identity,
            receiptId,
            body,
            idempotencyKey
        );
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.CONFLICT).body(result);
    }

    @GetMapping("/tasks")
    public List<ReviewTaskView> inbox(
        @RequestParam(defaultValue = "50") int limit,
        HttpServletRequest request
    ) {
        RuntimeResolvedIdentity identity = authorize(
            request,
            RuntimeScopeCatalog.REVIEW_TASK_VIEW,
            "/api/reviews/v1/tasks"
        );
        return reviewService.inbox(identity, limit);
    }

    @GetMapping("/tasks/{taskId}")
    public ReviewTaskDetailView detail(
        @PathVariable String taskId,
        HttpServletRequest request
    ) {
        RuntimeResolvedIdentity identity = authorize(
            request,
            RuntimeScopeCatalog.REVIEW_TASK_VIEW,
            "/api/reviews/v1/tasks/{taskId}"
        );
        return reviewService.detail(identity, taskId).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Review task was not found.")
        );
    }

    @PostMapping("/tasks/{taskId}/decisions")
    public ResponseEntity<ReviewDecisionResult> decide(
        @PathVariable String taskId,
        @RequestBody HumanReviewService.SubmitReviewDecisionRequest body,
        HttpServletRequest request
    ) {
        RuntimeResolvedIdentity identity = authorize(
            request,
            RuntimeScopeCatalog.REVIEW_TASK_DECIDE,
            "/api/reviews/v1/tasks/{taskId}/decisions"
        );
        ReviewDecisionResult result = reviewService.decide(identity, taskId, body);
        return ResponseEntity.status(result.failure() == null ? HttpStatus.OK : HttpStatus.CONFLICT).body(result);
    }

    private RuntimeResolvedIdentity authorize(
        HttpServletRequest request,
        String scope,
        String surface
    ) {
        RuntimeResolvedIdentity identity = authResolver.resolveVerifiedPrivateContext(request, surface);
        authResolver.requireScope(identity, scope, surface);
        return identity;
    }
}
