package com.ai.fabric.runtime.workflow;

import ai.fabric.execution.context.TrustedExecutionContext;
import ai.fabric.execution.gateway.*;
import ai.fabric.execution.specialist.SpecialistDefinition;
import ai.fabric.execution.specialist.SpecialistRegistry;
import ai.fabric.execution.state.*;
import ai.fabric.intent.action.*;
import ai.fabric.intent.action.invocation.*;
import ai.fabric.intent.orchestration.OrchestrationContext;
import ai.fabric.intent.orchestration.OrchestrationResult;
import ai.fabric.intent.orchestration.OrchestrationResultType;
import ai.fabric.intent.orchestration.conversation.ApprovedConversationSnapshot;
import ai.fabric.intent.orchestration.workflow.*;
import com.ai.fabric.runtime.auth.RuntimeResolvedIdentity;
import com.ai.fabric.runtime.handover.RuntimeConversationTurn;
import com.ai.fabric.runtime.web.dto.ChatQueryRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** Bridges an already-owned canonical chat turn into configured native SDK execution. */
@Component
public final class RuntimeConfiguredWorkflowDispatcher implements WorkflowExecutionBindingProvider {
    private final WorkflowAssistanceBundle bundle;
    private final AuthoringPlatformClient platform;
    private final ObjectProvider<SpecialistRegistry> registries;
    private final ObjectProvider<AIExecutionGateway> gateways;
    private final ObjectProvider<AIExecutionConversationSnapshotRegistry> snapshots;
    private final ObjectProvider<DurableExecutionRepository> repositories;
    private final ObjectProvider<DurableExecutionSecurity> securities;
    private final ObjectProvider<AIActionRegistry> actions;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final ThreadLocal<Invocation> current = new ThreadLocal<>();
    private record Invocation(ChatQueryRequest.ConfigurationWork work, RuntimeResolvedIdentity identity,
                              RuntimeConversationTurn turn, OrchestrationContext host, Instant deadline,
                              WorkflowAssistanceBundle.Profile profile, String owner) { }

    public RuntimeConfiguredWorkflowDispatcher(WorkflowAssistanceBundle bundle, AuthoringPlatformClient platform,
        ObjectProvider<SpecialistRegistry> registries, ObjectProvider<AIExecutionGateway> gateways,
        ObjectProvider<AIExecutionConversationSnapshotRegistry> snapshots, ObjectProvider<DurableExecutionRepository> repositories,
        ObjectProvider<DurableExecutionSecurity> securities, ObjectProvider<AIActionRegistry> actions, ObjectMapper mapper, Clock clock) {
        this.bundle = bundle; this.platform = platform; this.registries = registries; this.gateways = gateways;
        this.snapshots = snapshots; this.repositories = repositories; this.securities = securities;
        this.actions = actions; this.mapper = mapper; this.clock = clock;
    }

    public OrchestrationResult dispatch(ChatQueryRequest body, OrchestrationContext host, RuntimeResolvedIdentity identity,
        RuntimeConversationTurn turn, TrustedExecutionContext trusted) {
        var profile = validate(body, identity, turn);
        var work = body.getConfigurationWork();
        var auth = identity.getAuthContext();
        var definition = required(registries).require(profile.specialist());
        Instant deadline = earlier(earlier(turn.lease().expiresAt(), auth.getExpiresAt()),
            clock.instant().plus(definition.executionProfile().workflowPolicy().timeout()));
        var snapshot = turn.conversationSnapshot();
        // Reuse this exact canonical lease/history. The SDK receives a one-use
        // approval for the selected owner, not a second interactive turn.
        var approved = new ApprovedConversationSnapshot(snapshot.interactionTurnId(), snapshot.ownerId(), snapshot.conversationId(),
            profile.specialist().toString(), snapshot.revision(), snapshot.sourceTurnCount(), snapshot.historyMessages(), snapshot.capturedAt());
        var snapshotRegistry = required(snapshots);
        var binding = snapshotRegistry.approve(new ConversationBinding(identity.ownerId(), body.getConversationId()), approved);
        var invocation = new Invocation(work, identity, turn, host, deadline, profile, turn.lease().leaseId());
        current.set(invocation);
        try {
            var input = mapper.createObjectNode().put("query", body.getQuery());
            AIExecutionResult<JsonNode> result = required(gateways).execute(new AIExecutionRequest<>(profile.specialist(), input,
                trusted, binding, deadline, work.turnId()));
            var workflow = workflowResult(result, mapper);
            var published = definition.executionProfile().workflowPolicy();
            workflow.put("deadline", deadline.toString());
            workflow.putObject("limits").put("actions", published.maxActions())
                .put("writes", work.previewOnly() ? 0 : published.maxWrites())
                .put("modelCalls", published.maxModelCalls()).put("corrections", work.maxCorrections());
            workflow.putObject("selection").put("profileId", profile.id()).put("name", profile.name())
                .put("specialistRef", profile.specialist().toString()).put("releaseId", work.releaseId()).put("bundleHash", bundle.contentHash());
            String state = workflow.path("state").asText();
            OrchestrationResultType type = switch (state) {
                case "COMPLETE" -> result.succeeded() ? OrchestrationResultType.INFORMATION_PROVIDED : OrchestrationResultType.ERROR;
                case "NEEDS_INPUT" -> OrchestrationResultType.CLARIFICATION_REQUIRED;
                case "CONFIRMATION_REQUIRED" -> OrchestrationResultType.CONFIRMATION_REQUIRED;
                default -> OrchestrationResultType.ERROR;
            };
            String message = workflowMessage(result, workflow);
            var diagnostics = new java.util.LinkedHashMap<String, Object>(result.diagnostics());
            diagnostics.put("workflow", mapper.convertValue(workflow, new TypeReference<Map<String, Object>>() {}));
            var response = OrchestrationResult.builder().type(type).success(result.succeeded()).message(message)
                .errorCode(result.failure() == null ? null : result.failure().reason())
                .data(Map.of("workflow", mapper.convertValue(workflow, new TypeReference<Map<String, Object>>() {})))
                .metadata(diagnostics).build();
            finish(invocation, result);
            return response;
        } finally { snapshotRegistry.release(binding); current.remove(); }
    }

    static com.fasterxml.jackson.databind.node.ObjectNode workflowResult(AIExecutionResult<JsonNode> result, ObjectMapper mapper) {
        Object raw = result.output() == null ? result.diagnostics().get("workflow") : result.output().path("work");
        JsonNode converted = raw == null ? null : mapper.valueToTree(raw);
        var workflow = converted != null && converted.isObject()
            ? (com.fasterxml.jackson.databind.node.ObjectNode) converted : mapper.createObjectNode();
        if (result.failure() != null) {
            if (!workflow.path("state").isTextual()) workflow.put("state", "STOPPED");
            if (!workflow.path("stopCode").isTextual()) {
                String reason = result.failure().reason();
                workflow.put("stopCode", reason.matches("[A-Z][A-Z0-9_]{0,127}") ? reason : "NATIVE_EXECUTION_FAILED");
            }
            workflow.put("executionStatus", result.status().name());
        }
        return workflow;
    }

    /** Check immutable selection before financial admission or any model call. */
    public WorkflowAssistanceBundle.Profile validate(ChatQueryRequest body, RuntimeResolvedIdentity identity, RuntimeConversationTurn turn) {
        var work = Objects.requireNonNull(body.getConfigurationWork());
        var auth = identity.getAuthContext();
        if (!bundle.enabled() || !platform.enabled() || turn == null || current.get() != null
            || !valid(work.sessionId()) || !valid(work.turnId()) || auth == null
            || !("authoring:" + work.sessionId()).equals(identity.ownerId())
            || !"PRIVATE_RUNTIME_BACKEND_MEDIATED".equals(String.valueOf(auth.getAuthMode()))
            || !"TRUSTED_BACKEND".equals(String.valueOf(auth.getCallerType()))
            || !auth.getGrantedScopes().contains("configuration:author")
            || !platform.releaseId().equals(work.releaseId()) || !bundle.contentHash().equals(work.bundleHash())
            || !turn.scope().ownerId().equals(identity.ownerId()) || !turn.scope().conversationId().equals(body.getConversationId()))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "CONFIGURED_AUTHORING_BINDING_DENIED");
        WorkflowAssistanceBundle.Profile profile;
        try { profile = bundle.select(work.task(), work.profileId()); }
        catch (IllegalArgumentException invalid) { throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, invalid.getMessage()); }
        if (!auth.getGrantedScopes().containsAll(profile.requiredScopes())) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "CONFIGURED_AUTHORING_SCOPES_DENIED");
        var registry = required(registries); var definition = registry.require(profile.specialist());
        if (!definition.executionProfile().workflowPolicy().enabled() || definition.inputAdapter().recordValidatedTurns())
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "CONFIGURED_AUTHORING_PROFILE_INVALID");
        return profile;
    }

    @Override public WorkflowExecution bind(String invocationId, SpecialistDefinition<?, ?> definition,
        AIExecutionRequest<?> request, OrchestrationContext context) {
        Invocation invocation = current.get();
        if (invocation == null || !definition.id().equals(invocation.profile().specialist())
            || !request.specialistId().equals(definition.id())) throw new IllegalArgumentException("WORKFLOW_HOST_AUTHORITY_REQUIRED");
        var repository = required(repositories); var security = required(securities);
        var registered = required(registries).requireRegistered(definition.id());
        String authorityHash = security.accessFingerprint(request.trustedExecutionContext());
        String requestHash = security.canonicalHash(Map.of("input", request.input(), "work", invocation.work(), "bundleHash", bundle.contentHash()));
        String workId = invocation.work().turnId();
        var record = repository.findById(workId).orElse(null);
        if (record == null) {
            var queued = DurableExecutionRecord.queued(workId, definition.id(), registered.contentHash(), authorityHash,
                security.idempotencyFingerprint(request.trustedExecutionContext(), workId), requestHash,
                security.protect(Map.of("workId", workId, "sessionId", invocation.work().sessionId(), "releaseId", invocation.work().releaseId(),
                    "conversationId", invocation.turn().scope().conversationId()),
                    "authoring-work:" + workId), invocation.deadline(), clock.instant(), Duration.ofDays(7));
            // Canonical chat owns this execution already. Never expose a QUEUED
            // window to the independent SDK background dispatcher.
            try { record = repository.create(queued.claimed(invocation.owner(), clock.instant(), invocation.deadline())); }
            catch (DurableExecutionRepository.DuplicateExecutionException duplicate) { record = repository.findById(workId).orElseThrow(); }
        }
        // Timestamp precision belongs to the persistent store. Read it back and
        // narrow the checkpoint ceiling instead of tolerating a later deadline.
        record = repository.findById(workId).orElseThrow(() -> new IllegalStateException("WORKFLOW_EXECUTION_MISSING"));
        if (!security.sameFingerprint(authorityHash, record.accessFingerprint()) || !security.sameFingerprint(requestHash, record.requestFingerprint())
            || !security.sameFingerprint(registered.contentHash(), record.specialistContentHash())) throw new IllegalArgumentException("WORKFLOW_REPLAY_CONFLICT");
        if (record.status() == ExecutionHandleStatus.QUEUED) {
            var claimed = record.claimed(invocation.owner(), clock.instant(), invocation.deadline());
            if (!repository.compareAndSet(record, claimed)) throw new IllegalStateException("WORKFLOW_EXECUTION_BUSY");
            record = claimed;
        }
        if (!invocation.owner().equals(record.leaseOwner())) throw new IllegalStateException("WORKFLOW_EXECUTION_BUSY");
        var store = new DurableWorkflowCheckpointStore(repository, security, mapper, clock, record);
        var published = definition.executionProfile().workflowPolicy();
        if (invocation.work().maxCorrections() < 0 || invocation.work().maxCorrections() > published.maxCorrections())
            throw new IllegalArgumentException("WORKFLOW_CORRECTION_ALLOWANCE_INVALID");
        var effective = new WorkflowPolicy(true, published.maxActions(), invocation.work().previewOnly() ? 0 : published.maxWrites(),
            published.maxModelCalls(), invocation.work().maxCorrections(), published.timeout());
        var root = new BoundedWorkflowRunner.Request(workId, requestHash, authorityHash, registered.contentHash(),
            persistedDeadline(invocation.deadline(), record.deadline()), invocation.work().previewOnly());
        context.setOrchestrationTurnGuard(invocation.host().getOrchestrationTurnGuard());
        var metadata = new java.util.LinkedHashMap<>(context.getMetadata());
        metadata.put("authoringSessionId", invocation.work().sessionId()); metadata.put("authoringTurnId", workId);
        context.setMetadata(metadata);
        var authority = new WorkflowExecution.Authority() {
            public void verify(BoundedWorkflowRunner.Request ignored) {
                if (current.get() != invocation || !clock.instant().isBefore(invocation.deadline()))
                    throw new WorkflowAllowance.Stop("WORKFLOW_AUTHORITY_EXPIRED");
                context.getOrchestrationTurnGuard().verifyBeforeOrchestration();
            }
            public String fingerprint(Map<String, Object> parameters) { return security.canonicalHash(parameters); }
            public BoundedWorkflowRunner.CompletionFinding validateCompletion(WorkflowCheckpoint checkpoint) {
                return AuthoringCompletionContract.validate(checkpoint, mapper);
            }
            public Map<String, Object> observation(AIActionMetaData metadata, ActionContext actionContext, GovernedActionInvocationOutcome outcome) {
                var handler = required(actions).findHandler(metadata.getName()).orElseThrow();
                return handler.buildPostActionLlmFacts(outcome.actionResult(), actionContext).orElse(Map.of());
            }
            public boolean hasCorrectableValidationFindings(AIActionMetaData metadata, ActionContext actionContext, GovernedActionInvocationOutcome outcome) {
                if (!"authoring_check_candidate".equals(metadata.getName()) || outcome.actionResult() == null || outcome.actionResult().getData() == null) return false;
                JsonNode data = mapper.valueToTree(outcome.actionResult().getData().toMap());
                if (data.at("/result/requiresCorrection").isBoolean()) return data.at("/result/requiresCorrection").booleanValue();
                var findings = data.at("/result/findings");
                if (findings.path("valid").isBoolean() && !findings.path("valid").booleanValue()) return true;
                return findings.path("errorCount").asInt() > 0;
            }
            public boolean isCorrectableNoncommittedFailure(AIActionMetaData metadata, ActionContext actionContext, GovernedActionInvocationOutcome outcome) {
                if (outcome.actionResult() == null || outcome.actionResult().getData() == null) return false;
                JsonNode data = mapper.valueToTree(outcome.actionResult().getData().toMap());
                return "AUTHORING_VALIDATION_REJECTED".equals(data.at("/result/code").asText())
                    && data.at("/result/committed").isBoolean() && !data.at("/result/committed").booleanValue();
            }
            public BoundedWorkflowRunner.Outcome reconcile(WorkflowCheckpoint.Step reserved) {
                verify(root);
                var registry = required(actions);
                String statusAction = "authoring_get_work_status";
                if (!definition.executionProfile().requestedCapabilities().requestableReadActions().contains(statusAction)
                    || !request.trustedExecutionContext().grantedScopes().contains("action:" + statusAction))
                    return new BoundedWorkflowRunner.Outcome(WorkflowCheckpoint.Effect.OUTCOME_UNKNOWN, Map.of(), "AUTHORING_RECEIPT_SCOPE_DENIED", false);
                var statusOnly = new ai.fabric.intent.orchestration.capability.EffectiveCapabilityProfile(
                    definition.id().toString(), definition.executionProfile().mode(), false, java.util.Set.of(),
                    java.util.Set.of(statusAction), java.util.Set.of(statusAction), java.util.Set.of(), null, null,
                    security.canonicalHash(Map.of("workId", workId, "statusAction", statusAction)));
                var outcome = new DefaultGovernedActionInvocationService(registry).invoke(new GovernedActionInvocation(
                    statusAction, Map.of(), new ActionContext(context, null), request.trustedExecutionContext(), statusOnly,
                    ActionConfirmationState.NOT_CONFIRMED, List.of()));
                if (outcome.executed() && outcome.actionResult() != null && outcome.actionResult().getData() != null) {
                    var data = mapper.valueToTree(outcome.actionResult().getData().toMap());
                    for (var receipt : data.path("result")) if (reserved.operationId().equals(receipt.path("operationId").asText())
                        && reserved.action().equals(receipt.path("action").asText()) && "EXECUTED".equals(receipt.path("status").asText()))
                        return new BoundedWorkflowRunner.Outcome(WorkflowCheckpoint.Effect.EXECUTED,
                            mapper.convertValue(receipt, new TypeReference<Map<String, Object>>() {}), null, false);
                }
                return new BoundedWorkflowRunner.Outcome(WorkflowCheckpoint.Effect.OUTCOME_UNKNOWN, Map.of(), "AUTHORING_RECEIPT_UNRESOLVED", false);
            }
        };
        return new WorkflowExecution(root, effective, store, authority);
    }

    static Instant persistedDeadline(Instant requested, Instant persisted) {
        return requested.isBefore(persisted) ? requested : persisted;
    }

    static String workflowMessage(AIExecutionResult<JsonNode> result, JsonNode workflow) {
        String message = result.output() == null ? workflow.path("message").asText(null) : result.output().path("answer").asText(null);
        if ((message == null || message.isBlank()) && "NEEDS_INPUT".equals(workflow.path("state").asText())) {
            var questions = new java.util.ArrayList<String>();
            for (var question : workflow.path("questions")) if (question.isTextual() && !question.asText().isBlank()) questions.add(question.asText());
            if (!questions.isEmpty()) return String.join("\n", questions);
        }
        return message;
    }

    private void finish(Invocation invocation, AIExecutionResult<?> result) {
        var repository = required(repositories); var security = required(securities);
        var record = repository.findById(invocation.work().turnId()).orElse(null);
        if (record == null || record.status() != ExecutionHandleStatus.RUNNING || !invocation.owner().equals(record.leaseOwner())) return;
        var terminal = record.completed(result.succeeded() ? ExecutionHandleStatus.SUCCEEDED : ExecutionHandleStatus.FAILED,
            security.protect(Map.of("diagnostics", result.diagnostics()), "authoring-result:" + record.invocationId()),
            result.failure() == null ? null : result.failure().reason(), clock.instant(), Duration.ofDays(7));
        if (!repository.compareAndSet(record, terminal)) throw new IllegalStateException("WORKFLOW_RESULT_CHECKPOINT_CONFLICT");
    }
    private boolean valid(String value) { return value != null && value.matches("[a-z][a-z0-9-]{1,119}"); }
    private Instant earlier(Instant first, Instant second) { return first.isBefore(second) ? first : second; }
    private <T> T required(ObjectProvider<T> provider) {
        T value = provider.getIfAvailable(); if (value == null) throw new IllegalStateException("CONFIGURED_WORKFLOW_INFRASTRUCTURE_UNAVAILABLE"); return value;
    }
}
