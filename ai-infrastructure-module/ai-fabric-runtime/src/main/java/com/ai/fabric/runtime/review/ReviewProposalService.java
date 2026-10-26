package com.ai.fabric.runtime.review;

import ai.fabric.execution.gateway.AIExecutionGateway;
import ai.fabric.execution.gateway.AIExecutionRequest;
import ai.fabric.execution.gateway.AIExecutionResult;
import ai.fabric.execution.gateway.AIExecutionStatus;
import ai.fabric.execution.review.ActionReviewRequest;
import ai.fabric.execution.review.ReviewDecisionGateway;
import ai.fabric.execution.review.ReviewTaskCreationResult;
import ai.fabric.execution.review.decision.ReviewDecisionFailure;
import com.ai.fabric.runtime.auth.RuntimeResolvedIdentity;
import com.ai.fabric.runtime.budget.RuntimeBudgetTurnScopes;
import ai.fabric.intent.orchestration.control.OrchestrationControlException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@ConditionalOnProperty(name = {"ai.execution.reviews.enabled", "app.human-review.proposals.enabled"}, havingValue = "true")
public class ReviewProposalService {
    private final AIExecutionGateway executions;
    private final ReviewDecisionGateway reviews;
    private final HumanReviewService contexts;
    private final ReviewProposalTargetResolver targets;
    private RuntimeBudgetTurnScopes budgetTurns;

    public ReviewProposalService(AIExecutionGateway executions, ReviewDecisionGateway reviews, HumanReviewService contexts,
        ReviewProposalTargetResolver targets) {
        this.executions = executions;
        this.reviews = reviews;
        this.contexts = contexts;
        this.targets = targets;
    }

    @Autowired(required = false)
    public void setBudgetTurns(RuntimeBudgetTurnScopes budgetTurns) { this.budgetTurns = budgetTurns; }

    public ReviewTaskCreationResult propose(RuntimeResolvedIdentity identity, Request request, String idempotencyKey) {
        if (request == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Action request is required.");
        String question = required(request.request(), "request", 8000);
        String title = required(request.title(), "title", 160);
        String summary = required(request.summary(), "summary", 1000);
        String key = required(idempotencyKey, "Idempotency-Key", 160);
        var source = contexts.sourceContext(identity);
        var input = new ReviewProposalInput(question, targets.resolve(request.targetReference(), source));
        RuntimeBudgetTurnScopes.Turn turn;
        try {
            turn = budgetTurns == null ? null : budgetTurns.openChat(identity, null);
        } catch (OrchestrationControlException denied) {
            return new ReviewTaskCreationResult(null, false,
                new ReviewDecisionFailure(denied.code(), "Provider budget admission was denied.", false));
        }
        AIExecutionResult<String> proposal;
        try (turn) {
            if (turn != null) turn.verifyBeforeOrchestration();
            proposal = executions.execute(new AIExecutionRequest<>(
                ReviewProposalConfiguration.SPECIALIST_ID, input, source, null, null, key));
            if (turn != null && turn.interruption().isPresent()) {
                return new ReviewTaskCreationResult(null, false, new ReviewDecisionFailure(
                    RuntimeBudgetTurnScopes.INTERRUPTION_CODE, "Provider spending was interrupted. No review was submitted.", false));
            }
        }
        if (proposal.status() != AIExecutionStatus.CONFIRMATION_REQUIRED || proposal.actionProposal() == null) {
            var failure = proposal.failure();
            return new ReviewTaskCreationResult(null, false, new ReviewDecisionFailure(
                failure == null ? "ACTION_PROPOSAL_NOT_CREATED" : failure.reason(),
                failure == null ? "The specialist did not produce a governed action proposal."
                    : failure.publicMessage(), failure != null && failure.retryable()));
        }
        String safeSummary = "Proposed action: " + proposal.actionProposal().actionName() + "\n"
            + proposal.actionProposal().confirmationMessage() + "\nRequested facts: " + summary;
        if (safeSummary.length() > 1000) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "The review summary exceeds 1000 characters. Shorten the application summary or confirmation template; no action was executed.");
        }
        return reviews.createActionReview(new ActionReviewRequest(
            proposal.actionProposal().receiptId(), HumanReviewRuntimeConfiguration.STANDARD_ACTION_POLICY,
            title, safeSummary, key), source);
    }

    private String required(String value, String field, int maximum) {
        if (value == null || value.isBlank() || value.length() > maximum) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                field + " must contain 1 to " + maximum + " characters.");
        }
        return value.trim();
    }

    public record Request(String request, String title, String summary, String targetReference) {
        public Request(String request, String title, String summary) { this(request, title, summary, null); }
    }
}
