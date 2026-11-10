package com.ai.fabric.runtime.tasks;

import ai.fabric.execution.context.ExecutionCallerIdentity;
import ai.fabric.execution.context.ExecutionPrincipal;
import ai.fabric.execution.context.ExecutionPrincipalType;
import ai.fabric.execution.context.ExecutionSource;
import ai.fabric.execution.context.ExecutionSubjectRef;
import ai.fabric.execution.context.TrustedExecutionContext;
import ai.fabric.execution.gateway.AIExecutionResult;
import ai.fabric.execution.gateway.AIExecutionStatus;
import ai.fabric.execution.action.ActionProposalCoordinator;
import ai.fabric.execution.action.ActionProposalDecisionRequest;
import ai.fabric.execution.action.ActionProposalDecisionResult;
import ai.fabric.execution.review.ActionReviewRequest;
import ai.fabric.execution.review.ReviewDecisionGateway;
import ai.fabric.execution.review.ReviewTaskView;
import ai.fabric.execution.specialist.SpecialistId;
import ai.fabric.execution.specialist.SpecialistRegistry;
import ai.fabric.execution.specialist.client.SpecialistClientFactory;
import ai.fabric.execution.specialist.client.SpecialistInvocation;
import ai.fabric.execution.specialist.manifest.JsonSchemaSpecialistInputAdapter;
import ai.fabric.execution.specialist.manifest.JsonSchemaSpecialistOutputAdapter;
import ai.fabric.execution.specialist.manifest.SpecialistConversationBinding;
import ai.fabric.execution.specialist.manifest.SpecialistInteractionCapability;
import ai.fabric.intent.action.AIActionRegistry;
import ai.fabric.intent.orchestration.control.OrchestrationControlException;
import com.ai.fabric.runtime.auth.RuntimeAuthCallerType;
import com.ai.fabric.runtime.auth.RuntimeAuthContext;
import com.ai.fabric.runtime.auth.RuntimeAuthMode;
import com.ai.fabric.runtime.auth.RuntimeResolvedIdentity;
import com.ai.fabric.runtime.budget.RuntimeBudgetTurnScopes;
import com.ai.fabric.runtime.review.HumanReviewRuntimeConfiguration;
import com.ai.fabric.runtime.specialist.assistance.SpecialistAssistanceBundle;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public final class ManagedTaskService {
    private final ManagedTasksBundle bundle;
    private final ObjectProvider<SpecialistRegistry> registries;
    private final ObjectProvider<SpecialistClientFactory> clients;
    private final ObjectProvider<RuntimeBudgetTurnScopes> turns;
    private final ObjectProvider<SpecialistAssistanceBundle> assistance;
    private final ObjectProvider<ActionProposalCoordinator> proposals;
    private final ObjectProvider<ReviewDecisionGateway> reviews;
    private final ObjectProvider<AIActionRegistry> actions;
    private final ManagedTaskGuard guards;
    private final ObjectMapper mapper;
    private final ExecutorService workers;
    private final String deploymentId;
    private final String tenantId;
    private final String customerId;
    private final String configurationVersion;

    public ManagedTaskService(ManagedTasksBundle bundle, ObjectProvider<SpecialistRegistry> registries,
        ObjectProvider<SpecialistClientFactory> clients, ObjectProvider<RuntimeBudgetTurnScopes> turns,
        ObjectProvider<SpecialistAssistanceBundle> assistance, ObjectProvider<ActionProposalCoordinator> proposals,
        ObjectProvider<ReviewDecisionGateway> reviews, ObjectProvider<AIActionRegistry> actions,
        ManagedTaskGuard guards, ObjectMapper mapper, @Qualifier("managedTaskExecutor") ExecutorService workers,
        @Value("${AI_FABRIC_RUNTIME_DEPLOYMENT_ID:}") String deploymentId,
        @Value("${AI_FABRIC_RUNTIME_TENANT_ID:}") String tenantId,
        @Value("${AI_FABRIC_RUNTIME_CUSTOMER_ID:}") String customerId,
        @Value("${PLATFORM_DEPLOYMENT_VERSION_ID:}") String configurationVersion) {
        this.bundle = bundle; this.registries = registries; this.clients = clients; this.turns = turns;
        this.assistance = assistance; this.proposals = proposals; this.reviews = reviews; this.actions = actions;
        this.guards = guards; this.mapper = mapper; this.workers = workers;
        this.deploymentId = deploymentId; this.tenantId = tenantId; this.customerId = customerId;
        this.configurationVersion = configurationVersion;
        if (bundle.enabled() && (deploymentId.isBlank() || tenantId.isBlank() || configurationVersion.isBlank()))
            throw new IllegalStateException("Managed tasks require configured runtime deployment, tenant and configuration version.");
    }

    public Map<String, Object> status() {
        var result = new java.util.LinkedHashMap<String, Object>();
        result.put("contractVersion", ManagedTasksBundle.CONTRACT);
        result.put("enabled", bundle.enabled()); result.put("ready", ready());
        result.put("contentHash", bundle.contentHash());
        // Plain values also serialize correctly through Boot's Jackson 3 admin converter.
        result.put("tasks", mapper.convertValue(bundle.catalogue(), List.class));
        return result;
    }

    public boolean ready() {
        if (!bundle.enabled()) return true;
        var registry = registries.getIfAvailable();
        var factory = clients.getIfAvailable();
        if (registry == null || factory == null) return false;
        try {
            for (var task : bundle.tasks()) {
                var definition = registry.require(SpecialistId.parse(task.specialistRef()));
                if (!(definition.inputAdapter() instanceof JsonSchemaSpecialistInputAdapter)
                    || definition.delegationPolicy().enabled()
                    || definition.handoffPolicy().enabled()) return false;
                if (definition.inputAdapter().conversationBinding() != SpecialistConversationBinding.DISABLED
                    || definition.inputAdapter().interactionCapability() != SpecialistInteractionCapability.NON_INTERACTIVE
                    || definition.inputAdapter().recordValidatedTurns()) return false;
                var capabilities = definition.executionProfile().requestedCapabilities();
                if (capabilities == null || !capabilities.requestedVectorSpaces().equals(task.vectorSpaces())
                    || !capabilities.requestableReadActions().equals(task.readActions())
                    || !capabilities.proposableWriteActions().equals(task.writeActions())) return false;
                if ("DIRECT".equals(task.inputBinding())) {
                    var source = assistance.getIfAvailable();
                    if (source == null || !source.enabled() || !task.referenceBundleHash().equals(source.contentHash())
                        || !((JsonSchemaSpecialistInputAdapter) definition.inputAdapter()).schemaDefinition().spec().schema().equals(task.inputSchema().spec().schema())
                        || !(definition.outputAdapter() instanceof JsonSchemaSpecialistOutputAdapter output)
                        || !output.schemaDefinition().spec().schema().equals(task.outputSchema().spec().schema())) return false;
                }
                if (!task.writeActions().isEmpty()) {
                    var catalog = actions.getIfAvailable();
                    if (catalog == null) return false;
                    for (String write : task.writeActions()) {
                        var metadata = catalog.findMetadata(write).orElse(null);
                        if (metadata == null || metadata.getAccessMode() == null || metadata.getAccessMode().isReadOnly()) return false;
                        if (metadata.isConfirmationRequired() && proposals.getIfAvailable() == null) return false;
                    }
                }
                if ("HUMAN_REVIEW".equals(task.confirmationMode()) && reviews.getIfAvailable() == null) return false;
                factory.bind(definition.id(), JsonNode.class, JsonNode.class);
            }
            return true;
        } catch (RuntimeException invalid) { return false; }
    }

    TaskCatalogue catalogue(RuntimeResolvedIdentity identity) {
        var auth = authorize(identity);
        List<TaskCatalogue.Entry> entries = bundle.tasks().stream()
            .filter(task -> scopes(auth).contains("task:" + task.code() + ":read"))
            .map(task -> new TaskCatalogue.Entry(task.code(), task.name(), task.description(), task.outputMode(),
                task.inputSchema().spec().schema().deepCopy(), task.outputSchema().spec().schema().deepCopy(), task.deadlineSeconds(),
                !task.writeActions().isEmpty(), task.confirmationMode()))
            .toList();
        return new TaskCatalogue(ManagedTasksBundle.CONTRACT, configurationVersion, entries);
    }

    ResponseEntity<ManagedTaskResponse> execute(String code, ManagedTaskRequest request, RuntimeResolvedIdentity identity) {
        RuntimeAuthContext auth = authorize(identity);
        // Grant first, before lookup, validation, binding or provider accounting. No task-existence oracle.
        if (!scopes(auth).contains("task:" + code + ":execute"))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "TASK_EXECUTE_DENIED");
        var task = bundle.find(code);
        if (task == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "TASK_NOT_FOUND");
        JsonNode input = validatedInput(task, request);
        if (!ready()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "TASKS_NOT_READY");
        var definition = registries.getObject().require(SpecialistId.parse(task.specialistRef()));
        try { definition.inputAdapter().inputType().cast(input); validateAdapter(definition.inputAdapter(), input); }
        catch (RuntimeException invalid) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "TASK_INPUT_INVALID"); }
        String runId = "task-" + UUID.randomUUID();
        Instant started = Instant.now();
        Instant deadline = started.plusSeconds(task.deadlineSeconds());
        if (auth.getExpiresAt().isBefore(deadline)) deadline = auth.getExpiresAt();
        final Instant effectiveDeadline = deadline;
        var trusted = trusted(task, auth, request.correlationId());
        var trace = guards.trace(mapper);
        Future<AIExecutionResult<JsonNode>> future = null;
        try {
            future = workers.submit(() -> invoke(task, input, identity, trusted, effectiveDeadline, trace));
            var result = future.get(Math.max(1, java.time.Duration.between(Instant.now(), deadline).toNanos()), TimeUnit.NANOSECONDS);
            if (!Instant.now().isBefore(deadline)) throw new TimeoutException();
            if (result.status() == AIExecutionStatus.CONFIRMATION_REQUIRED) {
                ReviewTaskView review = null;
                if ("HUMAN_REVIEW".equals(task.confirmationMode())) {
                    var gateway = reviews.getIfAvailable();
                    if (gateway == null) return failed(runId, task, started, HttpStatus.SERVICE_UNAVAILABLE, "TASK_REVIEW_UNAVAILABLE", trace);
                    var created = gateway.createActionReview(new ActionReviewRequest(result.actionProposal().receiptId(),
                        HumanReviewRuntimeConfiguration.STANDARD_ACTION_POLICY, task.name(), task.description(), runId), trusted);
                    if (!created.created()) {
                        var state = trace.state();
                        return response(HttpStatus.CONFLICT, new ManagedTaskResponse(runId, task.code(), "REVIEW_FAILED",
                            configurationVersion, bundle.contentHash(), "1", null, List.of(),
                            new ManagedTaskResponse.Failure("TASK_REVIEW_UNAVAILABLE", "The proposed action was not executed; its review could not be created."),
                            state.executed(), started, Instant.now(), result.actionProposal(), state.receipts(), state.sideEffectsStatus(), null, null));
                    }
                    review = created.task();
                }
                var state = trace.state();
                return response(HttpStatus.ACCEPTED, new ManagedTaskResponse(runId, task.code(), review == null ? "CONFIRMATION_REQUIRED" : "REVIEW_REQUIRED",
                    configurationVersion, bundle.contentHash(), "1", null, List.of(), null, state.executed(), started, Instant.now(),
                    result.actionProposal(), state.receipts(), state.sideEffectsStatus(), null, review));
            }
            if (result.status() == AIExecutionStatus.WAITING_FOR_INPUT || result.failure() != null
                && "CLARIFICATION_REQUIRED".equals(result.failure().reason())) {
                String message = result.failure() == null ? "Additional input is required by this task." : result.failure().publicMessage();
                var state = trace.state();
                return response(HttpStatus.OK, new ManagedTaskResponse(runId, task.code(), "INPUT_REQUIRED", configurationVersion,
                    bundle.contentHash(), "1", null, List.of(), null, state.executed(), started, Instant.now(), null,
                    state.receipts(), state.sideEffectsStatus(), mapper.createObjectNode().put("message", message.substring(0, Math.min(message.length(), 2000))), null));
            }
            if (!result.succeeded()) return failed(runId, task, started, failureStatus(result), failureCode(result), trace);
            JsonNode output = result.output();
            try {
                if (mapper.writeValueAsBytes(output).length > ManagedTasksBundle.MAX_OUTPUT_BYTES) throw new IllegalArgumentException();
                bundle.validateOutput(task, output);
            } catch (Exception invalid) { return failed(runId, task, started, HttpStatus.BAD_GATEWAY, "TASK_OUTPUT_INVALID", trace); }
            if ("TEXT".equals(task.outputMode())) {
                if (!output.path("answer").isTextual()) return failed(runId, task, started, HttpStatus.BAD_GATEWAY, "TASK_OUTPUT_INVALID", trace);
                output = output.path("answer").deepCopy();
            }
            var evidence = result.evidence().stream().limit(8)
                .map(ref -> new ManagedTaskResponse.Evidence(safeRef(ref.evidenceId()), safeRef(ref.source()), safeRef(ref.vectorSpace())))
                .filter(ref -> ref.evidenceId() != null).toList();
            var state = trace.state();
            return response(HttpStatus.OK, new ManagedTaskResponse(runId, task.code(), "SUCCEEDED", configurationVersion,
                bundle.contentHash(), "1", output, evidence, null, state.executed(), started, Instant.now(), null,
                state.receipts(), state.sideEffectsStatus(), null, null));
        } catch (TimeoutException | CancellationException timeout) {
            if (future != null) future.cancel(true);
            return failed(runId, task, started, HttpStatus.GATEWAY_TIMEOUT, "TASK_DEADLINE_EXCEEDED", trace);
        } catch (InterruptedException interrupted) {
            if (future != null) future.cancel(true);
            Thread.currentThread().interrupt();
            return failed(runId, task, started, HttpStatus.SERVICE_UNAVAILABLE, "TASK_CANCELLED", trace);
        } catch (RejectedExecutionException busy) {
            return failed(runId, task, started, HttpStatus.SERVICE_UNAVAILABLE, "TASK_BUSY", trace);
        } catch (ExecutionException failure) {
            Throwable cause = failure.getCause();
            for (int depth = 0; cause != null && depth < 12; depth++, cause = cause.getCause()) {
                if (cause instanceof OrchestrationControlException control) {
                    if (control.code().startsWith("PROVIDER_BUDGET_"))
                        return failed(runId, task, started, HttpStatus.TOO_MANY_REQUESTS, "TASK_BUDGET_INTERRUPTED", trace);
                    if ("TASK_DEADLINE_EXCEEDED".equals(control.code()))
                        return failed(runId, task, started, HttpStatus.GATEWAY_TIMEOUT, "TASK_DEADLINE_EXCEEDED", trace);
                }
            }
            return failed(runId, task, started, HttpStatus.BAD_GATEWAY, "TASK_EXECUTION_FAILED", trace);
        } catch (RuntimeException failure) {
            return failed(runId, task, started, HttpStatus.BAD_GATEWAY, "TASK_EXECUTION_FAILED", trace);
        }
    }

    ResponseEntity<ManagedTaskDecisionResponse> decide(String code, String receiptId, ManagedTaskDecisionRequest request,
                                                      RuntimeResolvedIdentity identity) {
        RuntimeAuthContext auth = authorize(identity);
        if (!scopes(auth).contains("task:" + code + ":confirm"))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "TASK_CONFIRM_DENIED");
        var task = bundle.find(code);
        if (task == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "TASK_NOT_FOUND");
        if ("HUMAN_REVIEW".equals(task.confirmationMode()) || task.writeActions().isEmpty())
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "TASK_CONFIRM_DENIED");
        if (request == null || request.decision() == null || receiptId == null
            || !receiptId.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,119}"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "TASK_DECISION_INVALID");
        var coordinator = proposals.getIfAvailable();
        if (!ready() || coordinator == null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "TASKS_NOT_READY");
        var trace = guards.trace(mapper);
        Instant now = Instant.now();
        Instant deadline = now.plusSeconds(task.deadlineSeconds());
        if (auth.getExpiresAt().isBefore(deadline)) deadline = auth.getExpiresAt();
        final Instant boundedDeadline = deadline;
        Future<ActionProposalDecisionResult> future = null;
        try {
            future = workers.submit(() -> {
                try (var guard = guards.open(task, trace, boundedDeadline, null)) {
                    guard.check();
                    return coordinator.decide(new ActionProposalDecisionRequest(receiptId, request.decision()), trusted(task, auth, null), guard.guard());
                }
            });
            var result = future.get(Math.max(1, java.time.Duration.between(Instant.now(), deadline).toNanos()), TimeUnit.NANOSECONDS);
            return decisionResponse(result.failure() == null ? HttpStatus.OK : HttpStatus.CONFLICT, task,
                result.status() == null ? "FAILED" : result.status().name(), result, trace, null);
        } catch (TimeoutException | CancellationException timeout) {
            if (future != null) future.cancel(true);
            return decisionResponse(HttpStatus.GATEWAY_TIMEOUT, task, "OUTCOME_UNKNOWN", null, trace,
                new ManagedTaskResponse.Failure("TASK_DECISION_DEADLINE_EXCEEDED", "The wait ended; the action may have executed. No automatic retry was performed."));
        } catch (InterruptedException interrupted) {
            if (future != null) future.cancel(true);
            Thread.currentThread().interrupt();
            return decisionResponse(HttpStatus.SERVICE_UNAVAILABLE, task, "OUTCOME_UNKNOWN", null, trace,
                new ManagedTaskResponse.Failure("TASK_DECISION_INTERRUPTED", "The wait was interrupted; the action may have executed. No automatic retry was performed."));
        } catch (RejectedExecutionException busy) {
            return decisionResponse(HttpStatus.SERVICE_UNAVAILABLE, task, "FAILED", null, trace,
                new ManagedTaskResponse.Failure("TASK_BUSY", "The task worker could not accept this decision."));
        } catch (ExecutionException failure) {
            return decisionResponse(HttpStatus.BAD_GATEWAY, task, "OUTCOME_UNKNOWN", null, trace,
                new ManagedTaskResponse.Failure("TASK_DECISION_FAILED", "The action decision did not return a known result. No automatic retry was performed."));
        }
    }

    private ResponseEntity<ManagedTaskDecisionResponse> decisionResponse(HttpStatus status, ManagedTasksBundle.Task task,
        String state, ActionProposalDecisionResult decision, ManagedTaskGuard.Trace trace, ManagedTaskResponse.Failure failure) {
        return ResponseEntity.status(status).header("Cache-Control", "no-store").body(new ManagedTaskDecisionResponse(task.code(),
            configurationVersion, bundle.contentHash(), state, decision, trace.snapshot(), failure));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void validateAdapter(ai.fabric.execution.specialist.SpecialistInputAdapter adapter, JsonNode input) { adapter.validate(input); }

    private AIExecutionResult<JsonNode> invoke(ManagedTasksBundle.Task task, JsonNode input, RuntimeResolvedIdentity identity,
        TrustedExecutionContext trusted, Instant deadline, ManagedTaskGuard.Trace trace) {
        var budgets = turns.getIfAvailable();
        if (!Instant.now().isBefore(deadline))
            throw new OrchestrationControlException("TASK_DEADLINE_EXCEEDED", "Task deadline exceeded.");
        try (var turn = budgets == null ? null : budgets.openTask(identity, task.code());
             var guard = guards.open(task, trace, deadline, turn)) {
            guard.check();
            var client = clients.getObject().bind(SpecialistId.parse(task.specialistRef()), JsonNode.class, JsonNode.class);
            var result = client.execute(new SpecialistInvocation<>(input, trusted, null, deadline, null));
            if (turn != null && turn.interruption().isPresent())
                throw new OrchestrationControlException("PROVIDER_BUDGET_INTERRUPTED", "Provider spending was interrupted.");
            guard.check();
            return result;
        }
    }

    private RuntimeAuthContext authorize(RuntimeResolvedIdentity identity) {
        var auth = identity == null ? null : identity.getAuthContext();
        if (auth == null || auth.getSubjectId() == null || auth.getSubjectId().isBlank() || auth.getExpiresAt() == null
            || !Instant.now().isBefore(auth.getExpiresAt())) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "TASK_PRIVATE_IDENTITY_REQUIRED");
        if (auth.getAuthMode() != RuntimeAuthMode.PRIVATE_RUNTIME_BACKEND_MEDIATED
            && auth.getAuthMode() != RuntimeAuthMode.PLATFORM_PROXY_SESSION)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "TASK_PRIVATE_IDENTITY_REQUIRED");
        if (auth.getCallerType() == null || !Set.of(RuntimeAuthCallerType.TRUSTED_BACKEND, RuntimeAuthCallerType.PLATFORM_PROXY, RuntimeAuthCallerType.SYSTEM_PROCESS)
            .contains(auth.getCallerType()) || auth.isAnonymousSession()
            || deploymentId.isBlank() || tenantId.isBlank() || !deploymentId.equals(auth.getDeploymentId())
            || !tenantId.equals(auth.getTenantId()) || (!customerId.isBlank() && !customerId.equals(auth.getCustomerId())))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "TASK_RUNTIME_IDENTITY_MISMATCH");
        return auth;
    }

    void requireAppliedVersion(String ifMatch, RuntimeResolvedIdentity identity, String taskCode, String operation) {
        if (ifMatch == null) return;
        var auth = authorize(identity);
        if (!scopes(auth).contains("task:" + taskCode + ":" + operation))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "TASK_ACCESS_DENIED");
        if (!("\"" + configurationVersion + "\"").equals(ifMatch))
            throw new ResponseStatusException(HttpStatus.PRECONDITION_FAILED, "TASK_VERSION_MISMATCH");
    }

    private JsonNode validatedInput(ManagedTasksBundle.Task task, ManagedTaskRequest request) {
        try {
            if (request == null || request.input() == null || !request.input().isObject()
                || mapper.writeValueAsBytes(request.input()).length > ManagedTasksBundle.MAX_INPUT_BYTES
                || (request.correlationId() != null && (request.correlationId().length() > 160
                    || request.correlationId().codePoints().anyMatch(Character::isISOControl))))
                throw new IllegalArgumentException();
            bundle.validateInput(task, request.input());
            if ("DIRECT".equals(task.inputBinding())) return request.input().deepCopy();
            var wrapper = mapper.createObjectNode().put("request", task.request());
            wrapper.set("taskInput", request.input().deepCopy());
            return wrapper;
        } catch (Exception invalid) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "TASK_INPUT_INVALID"); }
    }

    private TrustedExecutionContext trusted(ManagedTasksBundle.Task task, RuntimeAuthContext auth, String correlationId) {
        return new TrustedExecutionContext(new ExecutionPrincipal(auth.getSubjectId(),
            auth.getCallerType() == RuntimeAuthCallerType.SYSTEM_PROCESS ? ExecutionPrincipalType.SYSTEM : ExecutionPrincipalType.SERVICE),
            new ExecutionSubjectRef("managed-task", task.code()), ExecutionSource.APPLICATION, tenantId, deploymentId,
            task.requiredScopes(), correlationId, Instant.now(), new ExecutionCallerIdentity(
                auth.getSubjectType() == null ? null : auth.getSubjectType().name(), auth.getAuthMode().name(),
                auth.getCallerType().name(), auth.getSessionId(), auth.getCustomerId(), auth.getIssuer(), auth.getAudiences(), auth.getExpiresAt()));
    }

    private static Set<String> scopes(RuntimeAuthContext auth) {
        return auth.getGrantedScopes() == null ? Set.of() : Set.copyOf(auth.getGrantedScopes());
    }

    private static String safeRef(String value) {
        return value != null && value.matches("[A-Za-z0-9][A-Za-z0-9._:/-]{0,199}") && !value.contains("://") ? value : null;
    }

    private static HttpStatus failureStatus(AIExecutionResult<?> result) {
        return switch (result.status()) {
            case DENIED -> HttpStatus.FORBIDDEN;
            case DEADLINE_EXCEEDED -> HttpStatus.GATEWAY_TIMEOUT;
            case INVALID -> HttpStatus.UNPROCESSABLE_ENTITY;
            case CANCELLED -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.BAD_GATEWAY;
        };
    }

    private static String failureCode(AIExecutionResult<?> result) {
        return switch (result.status()) {
            case DENIED -> "TASK_EXECUTION_DENIED";
            case DEADLINE_EXCEEDED -> "TASK_DEADLINE_EXCEEDED";
            case INVALID -> "TASK_RESULT_INVALID";
            case WAITING_FOR_INPUT, CONFIRMATION_REQUIRED -> "TASK_INTERACTIVE_RESULT_UNSUPPORTED";
            case CANCELLED -> "TASK_CANCELLED";
            default -> "TASK_EXECUTION_FAILED";
        };
    }

    private ResponseEntity<ManagedTaskResponse> failed(String id, ManagedTasksBundle.Task task, Instant started, HttpStatus http, String code,
                                                     ManagedTaskGuard.Trace trace) {
        String status = switch (http) {
            case GATEWAY_TIMEOUT -> "DEADLINE_EXCEEDED";
            case FORBIDDEN -> "DENIED";
            case UNPROCESSABLE_ENTITY -> "INVALID";
            default -> "TASK_CANCELLED".equals(code) ? "CANCELLED" : "FAILED";
        };
        var state = trace.state();
        // A stopped wait can race with a dispatch that passed the final guard just before expiry.
        String effects = !task.writeActions().isEmpty()
            && (http == HttpStatus.GATEWAY_TIMEOUT || "TASK_CANCELLED".equals(code))
            ? "OUTCOME_UNKNOWN" : state.sideEffectsStatus();
        return response(http, new ManagedTaskResponse(id, task.code(), status,
            configurationVersion, bundle.contentHash(), "1", null, List.of(), new ManagedTaskResponse.Failure(code,
                "The task did not produce a validated result. Check action receipts before repeating a write."), state.executed(), started, Instant.now(),
                null, state.receipts(), effects, null, null));
    }

    private static ResponseEntity<ManagedTaskResponse> response(HttpStatus status, ManagedTaskResponse body) {
        return ResponseEntity.status(status).header("Cache-Control", "no-store").body(body);
    }

    public record TaskCatalogue(String contractVersion, String configurationVersion, List<Entry> tasks) {
        public record Entry(String code, String name, String description, String outputMode, JsonNode inputSchema,
                            JsonNode outputSchema, int deadlineSeconds, boolean mayWrite, String confirmationMode) {}
    }
}
