package com.ai.fabric.runtime.authoring;

import ai.fabric.core.AICoreService;
import ai.fabric.core.LlmPurpose;
import ai.fabric.dto.AIAccessSubjectContext;
import ai.fabric.dto.AIChatMessage;
import ai.fabric.dto.AIGenerationRequest;
import ai.fabric.dto.AIGenerationResponse;
import ai.fabric.intent.orchestration.control.OrchestrationControlException;
import ai.fabric.llm.structured.StructuredJsonCallExecutor;
import ai.fabric.llm.structured.StructuredJsonCallSpec;
import ai.fabric.llm.structured.StructuredJsonProviderHints;
import com.ai.fabric.budget.BudgetException;
import com.ai.fabric.budget.openai.OpenAiBudgetException;
import com.ai.fabric.runtime.auth.RuntimeAuthContext;
import com.ai.fabric.runtime.auth.RuntimeResolvedIdentity;
import com.ai.fabric.runtime.budget.RuntimeBudgetBindingException;
import com.ai.fabric.runtime.budget.RuntimeBudgetBindingService;
import com.ai.fabric.runtime.budget.RuntimeBudgetTurnScopes;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public final class ConfigurationAuthoringService {
    private final AICoreService aiCore;
    private final StructuredJsonCallExecutor structured;
    private final ObjectMapper mapper;
    private final AuthoringJsonContract contract;
    private final RuntimeBudgetTurnScopes turns;
    private final RuntimeBudgetBindingService budgets;
    private final ExecutorService executor;
    private final long deadlineMs;
    private final int maxOutputTokens;

    ConfigurationAuthoringService(AICoreService aiCore, StructuredJsonCallExecutor structured, ObjectMapper mapper,
        RuntimeBudgetTurnScopes turns, RuntimeBudgetBindingService budgets, ExecutorService executor,
        long deadlineMs, int maxOutputTokens) {
        if (deadlineMs < 100 || deadlineMs > 120_000 || maxOutputTokens < 256 || maxOutputTokens > 8_192) {
            throw new IllegalArgumentException("AUTHORING_FINITE_DEADLINE_AND_OUTPUT_CAP_REQUIRED");
        }
        this.aiCore = aiCore;
        this.structured = structured;
        this.mapper = mapper;
        this.contract = new AuthoringJsonContract(mapper);
        this.turns = turns;
        this.budgets = budgets;
        this.executor = executor;
        this.deadlineMs = deadlineMs;
        this.maxOutputTokens = maxOutputTokens;
    }

    ResponseEntity<ObjectNode> generate(ConfigurationAuthoringRequest request, RuntimeResolvedIdentity identity) {
        String input = contract.input(request);
        // The dedicated service allocation, not the edited customer's deployment, owns this spend.
        budgets.requireVerifiedDeployment(identity);
        long allowedMs = Math.min(deadlineMs, identity.getAuthContext().getExpiresAt().toEpochMilli() - System.currentTimeMillis());
        var attempt = new Attempt(Instant.now().plusMillis(Math.max(0, allowedMs)), System.nanoTime(),
            System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(Math.max(0, allowedMs)));
        java.util.concurrent.Future<ObjectNode> future = null;
        try {
            checkDeadline(attempt);
            future = executor.submit(() -> infer(request, identity, input, attempt));
            var result = future.get(Math.max(1, attempt.deadlineNanos - System.nanoTime()), TimeUnit.NANOSECONDS);
            checkDeadline(attempt);
            result.set("runtime", receipt(attempt));
            return response(HttpStatus.OK, result);
        } catch (TimeoutException | CancellationException timeout) {
            if (future != null) future.cancel(true);
            return failure(request, attempt, new AuthoringFailure(HttpStatus.GATEWAY_TIMEOUT, "AUTHORING_TIMEOUT", "Authoring deadline exceeded; no proposal was delivered."));
        } catch (InterruptedException interrupted) {
            if (future != null) future.cancel(true);
            Thread.currentThread().interrupt();
            return failure(request, attempt, new AuthoringFailure(HttpStatus.SERVICE_UNAVAILABLE, "AUTHORING_CANCELLED", "Authoring request was interrupted."));
        } catch (RejectedExecutionException busy) {
            return failure(request, attempt, new AuthoringFailure(HttpStatus.SERVICE_UNAVAILABLE, "AUTHORING_BUSY", "Authoring capacity is busy."));
        } catch (ExecutionException failed) {
            return failure(request, attempt, classify(failed.getCause()));
        } catch (AuthoringFailure failed) {
            if (future != null) future.cancel(true);
            return failure(request, attempt, failed);
        }
    }

    private ObjectNode infer(ConfigurationAuthoringRequest request, RuntimeResolvedIdentity identity, String input, Attempt attempt) {
        checkDeadline(attempt);
        try (var turn = turns.openAuthoring(identity)) {
            attempt.accounting = turn.chatReceipt().orElse(null);
            turn.verifyBeforeOrchestration();
            checkDeadline(attempt);
            var generation = AIGenerationRequest.builder()
                .entityId(attempt.id).entityType("configuration-authoring").generationType("configuration_authoring_v1")
                .systemPrompt(AuthoringInstructions.system(request)).prompt(input)
                .messages(request.priorTurns().stream().map(turnInput -> turnInput.role() == ConfigurationAuthoringRequest.Role.USER
                    ? AIChatMessage.user(turnInput.content()) : AIChatMessage.assistant(turnInput.content())).toList())
                .authContext(access(identity.getAuthContext())).maxTokens(maxOutputTokens)
                .parameters(StructuredJsonProviderHints.schemaGuidedJsonObjectResponseParameters(AuthoringInstructions.RESPONSE_SCHEMA))
                .build();
            attempt.calls.incrementAndGet();
            AIGenerationResponse response;
            try {
                response = aiCore.generateContent(generation, LlmPurpose.GENERATION);
                attempt.response = response;
            } catch (RuntimeException failure) {
                if (turn.interruption().isPresent()) throw budgetInterrupted();
                throw failure;
            }
            if (turn.interruption().isPresent()) throw budgetInterrupted();
            checkDeadline(attempt);
            if (response == null || (response.getError() != null && !response.getError().isBlank())
                || (response.getStatus() != null && !response.getStatus().isBlank() && !"SUCCESS".equals(response.getStatus()))) {
                throw new AuthoringFailure(HttpStatus.BAD_GATEWAY, "AUTHORING_PROVIDER_ERROR", "Authoring provider failed.");
            }
            ObjectNode whole = contract.strictResponse(response.getContent(), request);
            var parsed = structured.execute(StructuredJsonCallSpec.<JsonNode>builder()
                .callName("loomai_configuration_authoring_v1").maxAttempts(1).retryOnCallError(false)
                .targetType(JsonNode.class).caller(ignored -> response).responseConverter(ignored -> whole).build());
            if (!parsed.isSuccess()) {
                throw new AuthoringFailure(HttpStatus.BAD_GATEWAY, "AUTHORING_INVALID_OUTPUT", "Structured authoring response was invalid.");
            }
            checkDeadline(attempt);
            return ((ObjectNode) parsed.getValue()).deepCopy();
        }
    }

    private static void checkDeadline(Attempt attempt) {
        if (Thread.currentThread().isInterrupted() || System.nanoTime() >= attempt.deadlineNanos) {
            throw new AuthoringFailure(HttpStatus.GATEWAY_TIMEOUT, "AUTHORING_TIMEOUT", "Authoring deadline exceeded; no proposal was delivered.");
        }
    }

    private static AuthoringFailure classify(Throwable failure) {
        for (int depth = 0; failure != null && depth < 16; depth++, failure = failure.getCause()) {
            Throwable cause = failure;
            if (cause instanceof AuthoringFailure known) return known;
            if (cause instanceof OrchestrationControlException control && control.code().startsWith("PROVIDER_BUDGET_")) {
                return new AuthoringFailure(HttpStatus.TOO_MANY_REQUESTS, control.code(), "Authoring provider budget denied or interrupted the request.");
            }
            if (cause instanceof BudgetException budget) return new AuthoringFailure(HttpStatus.TOO_MANY_REQUESTS,
                "PROVIDER_BUDGET_" + budget.code().name(), "Authoring provider budget denied the request.");
            if (cause instanceof RuntimeBudgetBindingException binding) return new AuthoringFailure(HttpStatus.SERVICE_UNAVAILABLE,
                "PROVIDER_BUDGET_" + binding.code().name(), "Authoring accounting binding is unavailable.");
            if (cause instanceof OpenAiBudgetException) return budgetInterrupted();
            if (cause instanceof TimeoutException || cause instanceof java.net.SocketTimeoutException
                || cause instanceof java.net.http.HttpTimeoutException) {
                return new AuthoringFailure(HttpStatus.GATEWAY_TIMEOUT, "AUTHORING_TIMEOUT", "Authoring provider timed out.");
            }
        }
        return new AuthoringFailure(HttpStatus.BAD_GATEWAY, "AUTHORING_PROVIDER_ERROR", "Authoring provider failed.");
    }

    private static AuthoringFailure budgetInterrupted() {
        return new AuthoringFailure(HttpStatus.TOO_MANY_REQUESTS, "PROVIDER_BUDGET_INTERRUPTED", "Authoring provider spending was interrupted.");
    }

    private ResponseEntity<ObjectNode> failure(ConfigurationAuthoringRequest request, Attempt attempt, AuthoringFailure failed) {
        var body = mapper.createObjectNode().put("contractVersion", request.contractVersion()).put("turnId", request.turnId());
        body.putObject("error").put("code", failed.code).put("message", failed.getMessage());
        body.set("runtime", receipt(attempt));
        return response(failed.status, body);
    }

    private ObjectNode receipt(Attempt attempt) {
        var node = mapper.createObjectNode().put("requestId", attempt.id).put("modelCalls", attempt.calls.get())
            .put("deadlineAt", attempt.deadlineAt.toString()).put("elapsedMs", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - attempt.startedNanos))
            .put("accountingPath", "RUNTIME_PROVIDER_BUDGET");
        node.put("metadataRecoveryPolicy", AuthoringJsonContract.METADATA_RECOVERY_POLICY);
        node.set("accountingReceipt", mapper.valueToTree(attempt.accounting));
        AIGenerationResponse response = attempt.response;
        node.set("usage", mapper.valueToTree(response == null ? null : response.getUsage()));
        node.set("tokensUsed", mapper.valueToTree(response == null ? null : response.getTokensUsed()));
        node.put("model", response == null ? null : response.getModel());
        node.put("providerStatus", response == null ? null : response.getStatus());
        node.put("providerError", response != null && response.getError() != null && !response.getError().isBlank());
        node.put("providerRequestId", response == null ? null : response.getRequestId());
        node.set("providerProcessingTimeMs", mapper.valueToTree(response == null ? null : response.getProcessingTimeMs()));
        return node;
    }

    private static ResponseEntity<ObjectNode> response(HttpStatus status, ObjectNode body) {
        return ResponseEntity.status(status).header("Cache-Control", "no-store").body(body);
    }

    private static AIAccessSubjectContext access(RuntimeAuthContext auth) {
        return AIAccessSubjectContext.builder().subjectId(auth.getSubjectId()).sessionId(auth.getSessionId())
            .subjectType(auth.getSubjectType().name()).authMode(auth.getAuthMode().name()).callerType(auth.getCallerType().name())
            .deploymentId(auth.getDeploymentId()).customerId(auth.getCustomerId()).tenantId(auth.getTenantId())
            .issuer(auth.getIssuer()).audiences(auth.getAudiences()).grantedScopes(auth.getGrantedScopes())
            .expiresAt(auth.getExpiresAt().toString()).build();
    }

    private static final class Attempt {
        final String id = UUID.randomUUID().toString();
        final Instant deadlineAt;
        final long startedNanos;
        final long deadlineNanos;
        final AtomicInteger calls = new AtomicInteger();
        volatile Object accounting;
        volatile AIGenerationResponse response;
        Attempt(Instant deadlineAt, long startedNanos, long deadlineNanos) {
            this.deadlineAt = deadlineAt;
            this.startedNanos = startedNanos;
            this.deadlineNanos = deadlineNanos;
        }
    }
}
