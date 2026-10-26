package com.ai.fabric.runtime.web;

import ai.fabric.execution.review.ReviewTaskCreationResult;
import com.ai.fabric.runtime.auth.RuntimeRequestAuthResolver;
import com.ai.fabric.runtime.auth.RuntimeScopeCatalog;
import com.ai.fabric.runtime.review.ReviewProposalConfiguration;
import com.ai.fabric.runtime.review.ReviewProposalService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reviews/v1/application-proposals")
@ConditionalOnProperty(name = {"ai.execution.reviews.enabled", "app.human-review.proposals.enabled"}, havingValue = "true")
public class ReviewProposalController {
    private final RuntimeRequestAuthResolver auth;
    private final ReviewProposalService proposals;

    public ReviewProposalController(RuntimeRequestAuthResolver auth, ReviewProposalService proposals) {
        this.auth = auth;
        this.proposals = proposals;
    }

    @PostMapping
    public ResponseEntity<ReviewTaskCreationResult> propose(@RequestBody ReviewProposalService.Request body,
        @RequestHeader("Idempotency-Key") String key, HttpServletRequest request) {
        var identity = auth.resolveVerifiedPrivateContext(request, "/api/reviews/v1/application-proposals");
        auth.requireScope(identity, RuntimeScopeCatalog.REVIEW_ACTION_PROPOSAL_CREATE, "application-proposals");
        auth.requireScope(identity, ReviewProposalConfiguration.SPECIALIST_SCOPE, "application-proposals");
        var result = proposals.propose(identity, body, key);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.CONFLICT).body(result);
    }
}
