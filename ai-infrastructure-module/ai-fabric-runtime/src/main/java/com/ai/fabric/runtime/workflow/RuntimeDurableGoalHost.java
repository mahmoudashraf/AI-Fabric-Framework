package com.ai.fabric.runtime.workflow;

import ai.fabric.chat.control.*;
import ai.fabric.execution.context.*;
import ai.fabric.execution.gateway.*;
import ai.fabric.execution.specialist.SpecialistRegistry;
import ai.fabric.execution.state.*;
import ai.fabric.intent.orchestration.OrchestrationContext;
import ai.fabric.intent.orchestration.conversation.ApprovedConversationSnapshot;
import ai.fabric.intent.orchestration.control.OrchestrationControlException;
import ai.fabric.intent.orchestration.workflow.WorkflowCheckpoint;
import com.ai.fabric.budget.ProviderBudgetLedger.ChatReceipt;
import com.ai.fabric.runtime.auth.*;
import com.ai.fabric.runtime.budget.RuntimeBudgetTurnScopes;
import com.ai.fabric.runtime.config.RuntimeAuthProperties;
import com.ai.fabric.runtime.handover.RuntimeConversationHandoverService;
import com.ai.fabric.runtime.handover.RuntimeConversationTurn;
import com.ai.fabric.runtime.web.dto.ChatQueryRequest;
import com.ai.fabric.runtime.web.dto.ChatQueryRequest.ConfigurationWork;
import com.ai.fabric.runtime.web.dto.ChatQueryResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

/** Restores host authority on the SDK worker; owns no queue, provider loop or browser credentials. */
@Component
public final class RuntimeDurableGoalHost implements DurableGoalExecutionHost {
    private static final String ROOTS = "authoringGoalRoots";
    private static final String STATE = "authoringGoal";
    private static final Set<String> TERMINAL = Set.of("COMPLETE", "FAILED", "CANCELLED", "BLOCKED");
    private final WorkflowAssistanceBundle bundle;
    private final AuthoringPlatformClient platform;
    private final RuntimeConfiguredWorkflowDispatcher dispatcher;
    private final ObjectProvider<AIExecutionGateway> gateways;
    private final ObjectProvider<DurableExecutionRepository> repositories;
    private final ObjectProvider<DurableExecutionSecurity> securities;
    private final ObjectProvider<SpecialistRegistry> registries;
    private final ObjectProvider<AIExecutionConversationSnapshotRegistry> snapshots;
    private final ObjectProvider<ConversationControlGateway> conversations;
    private final ObjectProvider<RuntimeConversationHandoverService> handovers;
    private final ObjectProvider<RuntimeBudgetTurnScopes> budgets;
    private final RuntimePrivateAssertionService assertions;
    private final RuntimeAuthProperties authProperties;
    private final ObjectMapper mapper;
    private final ObjectMapper projectionMapper;
    private final Clock clock;

    record Root(ConfigurationWork work, String conversationId, long userSequence, String interactionKey,
                long controlEpoch, ChatReceipt budgetReceipt, JsonNode sourceVersions,
                String issuer, List<String> audiences, String customerId) {
        Root { if (sourceVersions != null && sourceVersions.isNull()) sourceVersions = null; }
        Root withSources(JsonNode sources) {
            return new Root(work, conversationId, userSequence, interactionKey, controlEpoch, budgetReceipt,
                sources == null ? null : sources.deepCopy(), issuer, audiences, customerId);
        }
    }
    public record Control(String command, String authorityId, String goalId, String sessionId, String turnId,
                          long requestRevision, long cancellationEpoch, int segment, JsonNode directive) { }

    public RuntimeDurableGoalHost(WorkflowAssistanceBundle bundle, AuthoringPlatformClient platform,
        RuntimeConfiguredWorkflowDispatcher dispatcher, ObjectProvider<AIExecutionGateway> gateways,
        ObjectProvider<DurableExecutionRepository> repositories, ObjectProvider<DurableExecutionSecurity> securities,
        ObjectProvider<SpecialistRegistry> registries, ObjectProvider<AIExecutionConversationSnapshotRegistry> snapshots,
        ObjectProvider<ConversationControlGateway> conversations, ObjectProvider<RuntimeConversationHandoverService> handovers,
        ObjectProvider<RuntimeBudgetTurnScopes> budgets, RuntimePrivateAssertionService assertions,
        RuntimeAuthProperties authProperties, ObjectMapper mapper, Clock clock) {
        this.bundle = bundle; this.platform = platform; this.dispatcher = dispatcher; this.gateways = gateways;
        this.repositories = repositories; this.securities = securities; this.registries = registries; this.snapshots = snapshots;
        this.conversations = conversations; this.handovers = handovers; this.budgets = budgets;
        this.assertions = assertions; this.authProperties = authProperties; this.mapper = mapper; this.clock = clock;
        this.projectionMapper = mapper.copy().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public ChatQueryResponse submit(ChatQueryRequest body, RuntimeResolvedIdentity identity, RuntimeConversationTurn turn,
        ChatReceipt receipt) {
        var profile = dispatcher.validate(body, identity, turn);
        var work = body.getConfigurationWork();
        var auth = identity.getAuthContext();
        if (auth.getSubjectType() != RuntimeAuthSubjectType.END_USER || auth.getExpiresAt() == null
            || !clock.instant().isBefore(auth.getExpiresAt())) throw denied();
        if (!work.goalMode() || receipt == null || body.getAttachments() != null && !body.getAttachments().isEmpty()
            || body.getActionDraftSubmission() != null || body.getContext() != null && !body.getContext().isEmpty())
            throw new ResponseStatusException(BAD_REQUEST, "AUTHORING_GOAL_REQUIRES_SCOPED_WORK_REFERENCES");
        var user = required(conversations).messages(turn.scope(), 0, 500).stream()
            .filter(message -> work.turnId().equals(message.clientMessageId()) && message.role() == ConversationParticipantRole.USER)
            .findFirst().orElseThrow(() -> new ResponseStatusException(CONFLICT, "AUTHORING_GOAL_USER_MESSAGE_MISSING"));
        var policy = required(registries).require(profile.specialist()).executionProfile().workflowPolicy();
        Instant deadline = clock.instant().plus(policy.goal().maxElapsed());
        var root = new Root(work, body.getConversationId(), user.sequence(), work.turnId(), turn.lease().controlEpoch(), receipt, null,
            auth.getIssuer(), auth.getAudiences(), auth.getCustomerId());
        installRoot(root, trusted(identity, body.getConversationId()));
        var input = mapper.createObjectNode().put("query", body.getQuery());
        ExecutionHandle handle;
        try (var detached = required(budgets).detachForDurableScheduling()) {
            handle = required(gateways).submitGoal(new GoalExecutionRequest(new AIExecutionRequest<>(profile.specialist(), input,
                trusted(identity, body.getConversationId()), null, deadline, work.turnId()), work.authorityReference()));
        } catch (RuntimeException failure) { throw failure; }
        catch (Exception failure) { throw new IllegalStateException("AUTHORING_DURABLE_SCHEDULING_UNAVAILABLE"); }
        if (handle.status() == ExecutionHandleStatus.REJECTED) {
            required(conversations).updateOrchestrationState(turn.scope(), root.controlEpoch(), values -> {
                var copy = new LinkedHashMap<>(values); var state = mapper.valueToTree(values.get(STATE));
                if (work.goalId().equals(state.path("goalId").asText()) && state.path("workId").asText().isEmpty()) {
                    var rejected = new LinkedHashMap<>(mapper.convertValue(values.get(STATE), new TypeReference<Map<String, Object>>() {}));
                    rejected.put("state", "BLOCKED"); copy.put(STATE, rejected);
                }
                return copy;
            });
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "AUTHORING_GOAL_" + safeCode(handle.failureReason(), "SUBMISSION_REJECTED"));
        }
        bindWork(root, handle.invocationId());
        return ChatQueryResponse.builder().success(true).type("WORK_ACCEPTED").conversationId(body.getConversationId())
            .workId(handle.invocationId()).goal(Map.of("state", handle.status().name(), "segment", 1,
                "usage", Map.of(), "progress", Map.of())).build();
    }

    @Override public AIExecutionRequest<?> prepareSubmission(GoalExecutionRequest submission) {
        var request = submission.request();
        Root root = lookupRoot(request.trustedExecutionContext(), submission.authorityReference());
        var work = root.work();
        var trusted = request.trustedExecutionContext();
        var profile = bundle.select(work.task(), work.profileId());
        if (!work.goalMode() || !submission.authorityReference().equals(work.authorityReference())
            || !work.goalId().equals(work.authorityReference()) || request.conversationBinding() != null
            || root.budgetReceipt() == null || root.userSequence() < 1 || !root.interactionKey().equals(work.turnId())
            || !root.interactionKey().equals(request.idempotencyKey()) || !profile.specialist().equals(request.specialistId())
            || trusted.subject() == null || !root.conversationId().equals(trusted.subject().subjectId())
            || trusted.source() != ExecutionSource.APPLICATION || trusted.initiator().principalType() != ExecutionPrincipalType.SERVICE
            || !trusted.grantedScopes().contains("configuration:author") || !trusted.grantedScopes().containsAll(profile.requiredScopes())
            || !platform.releaseId().equals(work.releaseId()) || !bundle.contentHash().equals(work.bundleHash())
            || work.authorityDeadline() == null || !clock.instant().isBefore(work.authorityDeadline())
            || request.deadline() == null || !clock.instant().isBefore(request.deadline())) throw denied();
        var query = mapper.valueToTree(request.input()).path("query");
        if (!query.isTextual() || query.textValue().isBlank()) throw denied();
        var input = mapper.createObjectNode().put("query", query.textValue());
        return new AIExecutionRequest<>(request.specialistId(), input, trusted, null,
            request.deadline(), request.idempotencyKey());
    }

    @Override public AIExecutionResult<?> executeSegment(GoalExecutionClaim claim, AIExecutionRequest<?> persisted,
        Function<AIExecutionRequest<?>, AIExecutionResult<?>> execute) {
        var record = required(repositories).findById(claim.invocationId()).orElseThrow();
        Root initial = persistedRoot(record);
        if (!claim.authorityReference().equals(initial.work().authorityReference())) throw denied();
        try {
            bindWork(initial, claim.invocationId());
            var scope = scope(persisted.trustedExecutionContext(), initial);
            var state = state(scope, initial.controlEpoch());
            if (initial.sourceVersions() == null) requireUnadmitted(record, state);
            var checkpoint = checkpoint(record);
            int segment = checkpoint.path("goal").path("segmentNumber").asInt(0) + 1;
            if ("RUNNING".equals(checkpoint.path("state").asText())) segment = Math.max(1, segment - 1);
            ConfigurationWork requestedWork = initial.work().renewed(state.path("requestRevision").asLong(initial.work().requestRevision()),
                state.path("cancellationEpoch").asLong(initial.work().cancellationEpoch()), segment, initial.work().authorityDeadline());
            var lease = platform.renew(requestedWork, claim.invocationId());
            var identity = renewedIdentity(initial, requestedWork, lease, persisted.trustedExecutionContext(), claim.invocationId());
            Root root = admitSources(initial, requestedWork, scope, claim, lease.path("sourceVersions"));
            ConfigurationWork work = requestedWork.renewed(requestedWork.requestRevision(), requestedWork.cancellationEpoch(),
                requestedWork.segment(), Instant.parse(lease.path("validUntil").asText()));
            if (!root.sourceVersions().equals(lease.path("sourceVersions"))) throw new ResponseStatusException(CONFLICT, "AUTHORING_SOURCE_VERSION_CHANGED");
            var turn = required(handovers).beginAiTurn(identity, root.conversationId(), root.userSequence(), root.interactionKey());
            try {
            if (turn.lease().controlEpoch() != root.controlEpoch()) throw new ConversationControlViolationException("AUTHORING_GOAL_CONTROL_FENCED", "Conversation control changed.");
            bindSegmentLease(root, scope, claim, turn.lease(), segment);
            var profile = bundle.select(work.task(), work.profileId());
            if (!persisted.specialistId().equals(profile.specialist())) throw denied();
            Instant segmentDeadline = min(min(claim.deadline(), turn.lease().expiresAt()),
                min(work.authorityDeadline(), identity.getAuthContext().getExpiresAt()));
            var context = hostContext(identity, root, turn);
            context.getMetadata().put("authoringSourceVersions", mapper.convertValue(effectiveSources(root.sourceVersions(), lease), Map.class));
            if (lease.path("directive").isObject()) context.getMetadata().put("authoringGoalDirective",
                mapper.convertValue(lease.path("directive"), Map.class));
            var delegate = turn.guard();
            var segmentOpen = new java.util.concurrent.atomic.AtomicBoolean(true);
            var authorityFailure = new java.util.concurrent.atomic.AtomicReference<String>();
            var guard = new ai.fabric.intent.orchestration.control.OrchestrationTurnGuard() {
                public void verifyBeforeOrchestration() {
                    if (!segmentOpen.get()) throw new OrchestrationControlException("AUTHORING_SEGMENT_RELEASED", "The segment lease was released.");
                    if (!clock.instant().isBefore(segmentDeadline))
                        throw new ai.fabric.intent.orchestration.workflow.WorkflowAllowance.Stop("WORKFLOW_AUTHORITY_EXPIRED");
                    delegate.verifyBeforeOrchestration();
                    var latest = state(scope, root.controlEpoch());
                    if (!claim.invocationId().equals(latest.path("workId").asText())
                        || work.requestRevision() != latest.path("requestRevision").asLong()
                        || work.cancellationEpoch() != latest.path("cancellationEpoch").asLong()
                        || !turn.lease().leaseId().equals(latest.path("segmentLease").path("leaseId").asText())
                        || Set.of("PAUSED", "CANCELLED").contains(latest.path("state").asText()))
                        throw new OrchestrationControlException("AUTHORING_GOAL_FENCED", "Work authority changed.");
                    try {
                        var renewed = platform.renew(work, claim.invocationId());
                        renewedIdentity(root, work, renewed, persisted.trustedExecutionContext(), claim.invocationId());
                        if (!root.sourceVersions().equals(renewed.path("sourceVersions")))
                            throw new ResponseStatusException(CONFLICT, "AUTHORING_SOURCE_VERSION_CHANGED");
                    }
                    catch (ResponseStatusException denied) {
                        String code = denied.getStatusCode().value() == 401 ? "AUTHORING_REAUTHENTICATION_REQUIRED" : "AUTHORING_AUTHORITY_UNAVAILABLE";
                        authorityFailure.compareAndSet(null, code);
                        throw new OrchestrationControlException(code, "Current authoring authority must be restored before work continues.");
                    }
                    catch (IllegalStateException unavailable) {
                        authorityFailure.compareAndSet(null, "AUTHORING_AUTHORITY_UNAVAILABLE");
                        throw new OrchestrationControlException("AUTHORING_AUTHORITY_UNAVAILABLE", "Current authoring authority is unavailable.");
                    }
                }
                public void verifyBeforeAction(ai.fabric.intent.action.AIActionMetaData metadata, ai.fabric.intent.action.ActionContext action) {
                    verifyBeforeOrchestration(); delegate.verifyBeforeAction(metadata, action);
                }
                public void verifyBeforeResultCommit() { verifyBeforeOrchestration(); delegate.verifyBeforeResultCommit(); }
            };
            context.setOrchestrationTurnGuard(guard);
            var snapshot = turn.conversationSnapshot();
            var approved = new ApprovedConversationSnapshot(snapshot.interactionTurnId(), snapshot.ownerId(), snapshot.conversationId(),
                profile.specialist().toString(), snapshot.revision(), snapshot.sourceTurnCount(), snapshot.historyMessages(), snapshot.capturedAt());
            var registry = required(snapshots);
            var binding = registry.approve(new ConversationBinding(identity.ownerId(), root.conversationId()), approved);
            try (var budget = required(budgets).openGoalSegment(identity, root.budgetReceipt(), guard)) {
                context.setOrchestrationTurnGuard(budget);
                try (var installed = dispatcher.installSegment(work, identity, turn, context, segmentDeadline, profile, claim)) {
                    var auth = identity.getAuthContext();
                    var liveCaller = new ExecutionCallerIdentity(auth.getSubjectType().name(), auth.getAuthMode().name(), auth.getCallerType().name(),
                        auth.getSessionId(), auth.getCustomerId(), auth.getIssuer(), auth.getAudiences(), auth.getExpiresAt());
                    var result = execute.apply(new AIExecutionRequest<>(persisted.specialistId(), persisted.input(),
                        trusted(identity, root.conversationId()).withCallerIdentity(liveCaller), binding,
                        min(persisted.deadline(), claim.deadline()), persisted.idempotencyKey()));
                    if (authorityFailure.get() != null && pauseAuthorityCheckpoint(claim, authorityFailure.get()))
                        result = paused(claim, persisted, authorityFailure.get());
                    var diagnostics = new LinkedHashMap<>(result.diagnostics());
                    diagnostics.put("providerDiagnostics", budget.providerDiagnostics());
                    diagnostics.put("authoringRequestRevision", work.requestRevision());
                    diagnostics.put("authoringCancellationEpoch", work.cancellationEpoch());
                    return new AIExecutionResult<>(result.invocationId(), result.specialistId(), result.status(), result.output(),
                        result.evidence(), diagnostics, result.failure(), result.startedAt(), result.completedAt(), result.actionProposal(), result.needsUserInput());
                }
            } finally { segmentOpen.set(false); registry.release(binding); }
            } finally { releaseSegmentLease(root, scope, turn.lease()); }
        } catch (ai.fabric.intent.orchestration.workflow.WorkflowAllowance.Stop failure) {
            return "WORKFLOW_AUTHORITY_EXPIRED".equals(failure.code()) && clock.instant().isBefore(claim.deadline())
                ? yielded(claim, persisted) : paused(claim, persisted, safeCode(failure.code(), "AUTHORING_GOAL_FENCED"));
        } catch (OrchestrationControlException failure) { return paused(claim, persisted, safeCode(failure.code(), "AUTHORING_GOAL_FENCED")); }
        catch (ConversationControlViolationException failure) {
            return "AI_TURN_LEASE_EXPIRED".equals(failure.code()) && clock.instant().isBefore(claim.deadline())
                ? yielded(claim, persisted) : paused(claim, persisted, safeCode(failure.code(), "CANONICAL_CONTROL_CHANGED"));
        }
        catch (ResponseStatusException failure) { return paused(claim, persisted,
            failure.getStatusCode().value() == 401 ? "AUTHORING_REAUTHENTICATION_REQUIRED" : "AUTHORING_AUTHORITY_UNAVAILABLE"); }
        catch (Exception failure) { return paused(claim, persisted, "AUTHORING_HOST_RESTORATION_UNAVAILABLE"); }
    }

    public ChatQueryResponse find(RuntimeResolvedIdentity identity, String conversationId, String workId) {
        var record = owned(identity, conversationId, workId);
        var snapshot = required(gateways).find(workId, trusted(identity, conversationId)).orElseThrow(RuntimeDurableGoalHost::missing);
        return projection(snapshot, record, conversationId);
    }

    public ChatQueryResponse control(RuntimeResolvedIdentity identity, String conversationId, String workId, Control control, String key) {
        var record = owned(identity, conversationId, workId);
        Root root = persistedRoot(record);
        if (control == null || !Set.of("PAUSE", "RESUME", "CANCEL", "DIRECTIVE").contains(control.command())
            || !root.work().goalId().equals(control.goalId()) || !root.work().authorityReference().equals(control.authorityId())
            || !root.work().sessionId().equals(control.sessionId()) || !root.work().turnId().equals(control.turnId())
            || control.requestRevision() < 1 || control.cancellationEpoch() < 0) throw denied();
        var scope = scope(trusted(identity, conversationId), root);
        var prior = state(scope, root.controlEpoch());
        String fingerprint = required(securities).canonicalHash(control);
        boolean replay = key.equals(prior.path("lastControlKey").asText());
        if (replay) {
            if (!fingerprint.equals(prior.path("lastControlHash").asText())) throw new ResponseStatusException(CONFLICT, "AUTHORING_GOAL_CONTROL_KEY_REUSED");
            if (prior.path("lastControlApplied").asBoolean()) return find(identity, conversationId, workId);
        }
        if (control.requestRevision() < prior.path("requestRevision").asLong() || control.cancellationEpoch() < prior.path("cancellationEpoch").asLong())
            throw new ResponseStatusException(CONFLICT, "AUTHORING_GOAL_STALE_CONTROL");
        boolean directiveResumes = replay ? prior.path("lastControlResume").asBoolean()
            : "DIRECTIVE".equals(control.command()) && !"PAUSED".equals(prior.path("state").asText());
        if ("RESUME".equals(control.command()) || directiveResumes) {
            var work = root.work().renewed(control.requestRevision(), control.cancellationEpoch(), Math.max(1, control.segment()), root.work().authorityDeadline());
            renewedIdentity(root, work, platform.renew(work, workId), trusted(identity, conversationId), workId);
        }
        var gateway = required(gateways);
        var trusted = trusted(identity, conversationId);
        var command = "DIRECTIVE".equals(control.command()) ? GoalExecutionControl.RESUME : GoalExecutionControl.valueOf(control.command());
        // This canonical row lock/transaction also contains the co-resident SDK JDBC CAS.
        // Workers cannot claim the queued native row before its canonical authority is ready.
        required(conversations).updateOrchestrationState(scope, root.controlEpoch(), values -> {
            var copy = new LinkedHashMap<>(values); var next = new LinkedHashMap<>(mapper.convertValue(values.get(STATE), new TypeReference<Map<String, Object>>() {}));
            if (!workId.equals(next.get("workId")) || ((Number) next.get("requestRevision")).longValue() > control.requestRevision()
                || ((Number) next.get("cancellationEpoch")).longValue() > control.cancellationEpoch())
                throw new ResponseStatusException(CONFLICT, "AUTHORING_GOAL_STALE_CONTROL");
            boolean lockedReplay = key.equals(next.get("lastControlKey"));
            if (lockedReplay) {
                if (!fingerprint.equals(next.get("lastControlHash"))) throw new ResponseStatusException(CONFLICT, "AUTHORING_GOAL_CONTROL_KEY_REUSED");
                if (Boolean.TRUE.equals(next.get("lastControlApplied"))) return copy;
            }
            var current = required(repositories).findById(workId).orElseThrow(RuntimeDurableGoalHost::missing);
            boolean alreadyApplied = lockedReplay && (command == GoalExecutionControl.RESUME
                ? Set.of(ExecutionHandleStatus.QUEUED, ExecutionHandleStatus.RUNNING, ExecutionHandleStatus.YIELDED).contains(current.status())
                : command == GoalExecutionControl.CANCEL && current.status() == ExecutionHandleStatus.CANCELLED);
            if (!alreadyApplied && "DIRECTIVE".equals(control.command())) gateway.controlGoal(workId, GoalExecutionControl.PAUSE, trusted);
            if (!alreadyApplied && !("DIRECTIVE".equals(control.command()) && !directiveResumes)
                && !gateway.controlGoal(workId, command, trusted) && (command == GoalExecutionControl.RESUME || !current.terminal()))
                throw new ResponseStatusException(CONFLICT, "AUTHORING_GOAL_CONTROL_CONFLICT");
            next.put("requestRevision", control.requestRevision()); next.put("cancellationEpoch", control.cancellationEpoch());
            next.put("lastControlKey", key); next.put("lastControlHash", fingerprint);
            next.put("lastControlApplied", true); next.put("lastControlResume", directiveResumes);
            next.remove("segmentLease");
            next.put("state", "CANCEL".equals(control.command()) ? "CANCELLED"
                : "RESUME".equals(control.command()) || directiveResumes ? "QUEUED" : "PAUSED");
            copy.put(STATE, next); return copy;
        });
        return find(identity, conversationId, workId);
    }

    @Override public void executionUpdated(ExecutionSnapshot snapshot, String authorityReference) {
        var record = required(repositories).findById(snapshot.handle().invocationId()).orElseThrow();
        if (record.status() != snapshot.handle().status()) return;
        Root root;
        try { root = persistedRoot(record); }
        catch (ResponseStatusException revoked) { return; }
        if (!authorityReference.equals(root.work().authorityReference())) throw denied();
        String lifecycle = lifecycle(snapshot.handle().status(), workflow(snapshot));
        if (!updateState(root, record, lifecycle)) return;
        if (!Set.of("COMPLETE", "FAILED", "BLOCKED").contains(lifecycle) || snapshot.result() == null) return;
        JsonNode output = mapper.valueToTree(snapshot.result().output());
        String answer = output.path("answer").asText(null);
        if (answer == null || answer.isBlank()) return;
        var scope = scopeFromRoot(record, root);
        var control = required(conversations).status(scope);
        if (control.state() != ConversationControlState.AI_ACTIVE || control.controlEpoch() != root.controlEpoch()) return;
        var state = state(scope, root.controlEpoch());
        long revision = mapper.valueToTree(snapshot.result().diagnostics()).path("authoringRequestRevision").asLong(root.work().requestRevision());
        if (!record.invocationId().equals(state.path("workId").asText()) || revision != state.path("requestRevision").asLong()) return;
        var lease = required(conversations).acquireAiTurnLease(scope, java.time.Duration.ofSeconds(15));
        var turn = new RuntimeConversationTurn(scope, lease, new ConversationTurnLeaseGuard(required(conversations), lease), null);
        required(handovers).appendAiMessage(turn, "goal-final:" + record.invocationId(), answer,
            Map.of("workId", record.invocationId(), "resultType", lifecycle));
    }

    private RuntimeResolvedIdentity renewedIdentity(Root root, ConfigurationWork work, JsonNode lease, TrustedExecutionContext expected, String workId) {
        if (!work.goalId().equals(lease.path("goalId").asText()) || !work.authorityReference().equals(lease.path("authorityId").asText())
            || !work.sessionId().equals(lease.path("sessionId").asText()) || !work.turnId().equals(lease.path("turnId").asText())
            || !root.conversationId().equals(lease.path("conversationId").asText()) || !work.profileId().equals(lease.path("profileId").asText())
            || !work.releaseId().equals(lease.path("releaseId").asText()) || !bundle.contentHash().equals(lease.path("bundleHash").asText())
            || workId != null && !workId.equals(lease.path("workId").asText())
            || workId != null && !workId.equals(lease.path("operationNamespace").asText())
            || work.requestRevision() != lease.path("requestRevision").asLong(-1)
            || work.cancellationEpoch() != lease.path("cancellationEpoch").asLong(-1)
            || work.segment() != lease.path("segment").asInt(-1)
            || work.previewOnly() != lease.path("previewOnly").asBoolean()) throw denied();
        String header = authProperties.getIngress().getPrivateAssertions().getAuthorizationHeader();
        var auth = assertions.validateAssertionHeader(lease.path("runtimeHeaders").path(header).asText());
        var identity = RuntimeResolvedIdentity.builder().authContext(auth).warnings(List.of()).build();
        var restored = trusted(identity, root.conversationId());
        if (!expected.initiator().equals(restored.initiator()) || !Objects.equals(expected.tenantId(), restored.tenantId())
            || !Objects.equals(expected.deploymentId(), restored.deploymentId()) || !expected.grantedScopes().equals(restored.grantedScopes())
            || !auth.getGrantedScopes().contains("configuration:author") || !("authoring:" + work.sessionId()).equals(identity.ownerId())
            || auth.getSubjectType() != RuntimeAuthSubjectType.END_USER || !root.issuer().equals(auth.getIssuer())
            || !Set.copyOf(root.audiences()).equals(Set.copyOf(auth.getAudiences())) || !root.customerId().equals(auth.getCustomerId())
            || auth.getCallerType() != RuntimeAuthCallerType.TRUSTED_BACKEND || auth.getAuthMode() != RuntimeAuthMode.PRIVATE_RUNTIME_BACKEND_MEDIATED
            || !clock.instant().isBefore(auth.getExpiresAt()) || !clock.instant().isBefore(Instant.parse(lease.path("validUntil").asText()))) throw denied();
        return identity;
    }

    private Root admitSources(Root initial, ConfigurationWork work, ConversationScope scope, GoalExecutionClaim claim, JsonNode sources) {
        JsonNode pins = sourcePins(sources);
        if (initial.sourceVersions() != null) {
            if (!initial.sourceVersions().equals(pins)) throw new ResponseStatusException(CONFLICT, "AUTHORING_SOURCE_VERSION_CHANGED");
            return initial;
        }
        var admitted = new java.util.concurrent.atomic.AtomicReference<Root>();
        // The canonical row lock fences erasure/control and makes the first source pin immutable.
        required(conversations).updateOrchestrationState(scope, initial.controlEpoch(), values -> {
            var state = mapper.valueToTree(values.get(STATE));
            if (!work.goalId().equals(state.path("goalId").asText()) || !claim.invocationId().equals(state.path("workId").asText())
                || work.requestRevision() != state.path("requestRevision").asLong(-1)
                || work.cancellationEpoch() != state.path("cancellationEpoch").asLong(-1)
                || Set.of("PAUSED", "CANCELLED").contains(state.path("state").asText())) throw denied();
            var latest = required(repositories).findById(claim.invocationId()).orElseThrow(RuntimeDurableGoalHost::missing);
            if (latest.status() != ExecutionHandleStatus.RUNNING || !claim.leaseOwner().equals(latest.leaseOwner())
                || latest.attemptCount() != claim.attemptCount() || latest.leaseUntil() == null
                || !clock.instant().isBefore(latest.leaseUntil())) throw denied();
            var roots = mapper.convertValue(values.get(ROOTS), new TypeReference<LinkedHashMap<String, Object>>() {});
            if (roots == null || !(roots.get(work.goalId()) instanceof String protectedRoot)) throw missing();
            var current = mapper.convertValue(required(securities).unprotect(protectedRoot, "canonical-goal-root:" + work.goalId()).get("root"), Root.class);
            if (!current.withSources(null).equals(initial.withSources(null))) throw denied();
            if (current.sourceVersions() == null) {
                requireUnadmitted(latest, state);
                current = current.withSources(pins);
                roots.put(work.goalId(), required(securities).protect(Map.of("root", mapper.valueToTree(current)), "canonical-goal-root:" + work.goalId()));
            } else if (!current.sourceVersions().equals(pins)) throw denied();
            admitted.set(current);
            var copy = new LinkedHashMap<>(values); copy.put(ROOTS, roots); return copy;
        });
        return Objects.requireNonNull(admitted.get());
    }

    private JsonNode sourcePins(JsonNode pins) {
        if (!pins.isObject() || pins.size() > 10) throw denied();
        var fields = pins.fields();
        while (fields.hasNext()) {
            var entry = fields.next();
            if (!entry.getKey().matches("[A-Za-z0-9][A-Za-z0-9:_-]{0,199}") || !entry.getValue().isTextual()
                || !entry.getValue().textValue().matches("(?:sha256:)?[a-f0-9]{64}")) throw denied();
        }
        return pins.deepCopy();
    }

    private void requireUnadmitted(DurableExecutionRecord record, JsonNode state) {
        if (record.protectedWorkflowCheckpoint() != null || state.hasNonNull("segmentLease")) throw unpinnedProgress();
        if (record.protectedResult() == null) return;
        var result = mapper.valueToTree(required(securities).unprotect(record.protectedResult(), record.invocationId() + ":result"));
        if (!"PAUSED".equals(result.path("status").asText()) || !result.path("output").isNull()
            || !result.path("evidence").isArray() || !result.path("evidence").isEmpty()
            || result.hasNonNull("actionProposal") || result.hasNonNull("needsUserInput")) throw unpinnedProgress();
        var diagnostics = result.path("diagnostics");
        if (!diagnostics.isObject()) throw unpinnedProgress();
        var fields = diagnostics.fieldNames();
        while (fields.hasNext()) if (!"workflow".equals(fields.next())) throw unpinnedProgress();
        if (diagnostics.has("workflow")) {
            var workflow = diagnostics.path("workflow");
            if (!workflow.isObject() || !"PAUSED".equals(workflow.path("state").asText())) throw unpinnedProgress();
            fields = workflow.fieldNames();
            while (fields.hasNext()) if (!Set.of("state", "stopCode").contains(fields.next())) throw unpinnedProgress();
        }
    }
    private static OrchestrationControlException unpinnedProgress() {
        return new OrchestrationControlException("AUTHORING_GOAL_SOURCE_PINS_MISSING", "Execution progress has no pinned source authority.");
    }

    private boolean pauseAuthorityCheckpoint(GoalExecutionClaim claim, String code) {
        var latest = required(repositories).findById(claim.invocationId()).orElseThrow();
        if (latest.attemptCount() != claim.attemptCount() || !claim.leaseOwner().equals(latest.leaseOwner())) throw denied();
        var store = new DurableWorkflowCheckpointStore(required(repositories), required(securities), mapper, clock, latest);
        var checkpoint = store.find(claim.invocationId()).orElse(null);
        if (checkpoint == null) return true;
        if (checkpoint.state() != WorkflowCheckpoint.State.RUNNING)
            return Set.of(WorkflowCheckpoint.State.PAUSED, WorkflowCheckpoint.State.NEEDS_INPUT, WorkflowCheckpoint.State.COMPLETE).contains(checkpoint.state());
        // Authority can fail between bootstrap and the runner's protected loop. Fence the actual
        // checkpoint through the SDK store without resetting usage or touching irreversible steps.
        var paused = new WorkflowCheckpoint(checkpoint.workId(), checkpoint.requestFingerprint(), checkpoint.authorityFingerprint(),
            checkpoint.definitionFingerprint(), checkpoint.deadline(), checkpoint.version() + 1, checkpoint.usage(), checkpoint.steps(),
            WorkflowCheckpoint.State.PAUSED, checkpoint.message(), checkpoint.questions(), code, checkpoint.goal());
        if (!store.compareAndSet(checkpoint, paused)) throw denied();
        return true;
    }

    private OrchestrationContext hostContext(RuntimeResolvedIdentity identity, Root root, RuntimeConversationTurn turn) {
        var auth = identity.getAuthContext();
        var metadata = new LinkedHashMap<String, Object>();
        metadata.put("subjectId", auth.getSubjectId()); metadata.put("subjectType", auth.getSubjectType().name());
        metadata.put("authMode", auth.getAuthMode().name()); metadata.put("callerType", auth.getCallerType().name());
        metadata.put("tenantId", auth.getTenantId()); metadata.put("deploymentId", auth.getDeploymentId());
        metadata.put("customerId", auth.getCustomerId()); metadata.put("grantedScopes", auth.getGrantedScopes());
        metadata.put("authIssuer", auth.getIssuer()); metadata.put("authAudiences", auth.getAudiences());
        metadata.put("authExpiresAt", auth.getExpiresAt().toString()); metadata.put("queryPersistenceMode", "NEVER_PERSIST");
        return OrchestrationContext.builder().conversationId(root.conversationId()).userId(identity.orchestrationUserId())
            .sessionId(identity.orchestrationSessionId()).approvedConversationSnapshot(turn.conversationSnapshot()).metadata(metadata).build();
    }

    private DurableExecutionRecord owned(RuntimeResolvedIdentity identity, String conversationId, String workId) {
        if (identity == null || identity.getAuthContext() == null) throw missing();
        var auth = identity.getAuthContext();
        if (!auth.getGrantedScopes().contains("configuration:author") || auth.getCallerType() != RuntimeAuthCallerType.TRUSTED_BACKEND
            || auth.getAuthMode() != RuntimeAuthMode.PRIVATE_RUNTIME_BACKEND_MEDIATED || auth.getSubjectType() != RuntimeAuthSubjectType.END_USER
            || !identity.ownerId().startsWith("authoring:")) throw missing();
        var record = required(repositories).findById(workId).orElseThrow(RuntimeDurableGoalHost::missing);
        if (!record.goalExecution() || !required(securities).sameFingerprint(record.accessFingerprint(),
            required(securities).accessFingerprint(trusted(identity, conversationId)))) throw missing();
        var payload = required(securities).unprotect(record.protectedRequest(), workId + ":request");
        if (Boolean.TRUE.equals(payload.get("erased")) || !"conversation".equals(payload.get("subjectType"))
            || !conversationId.equals(payload.get("subjectId")) || !identity.ownerId().equals(payload.get("principalId"))) throw missing();
        required(conversations).status(new ConversationScope(auth.getTenantId(), auth.getDeploymentId(), conversationId, identity.ownerId()));
        return record;
    }

    public void interrupt(RuntimeResolvedIdentity identity, String conversationId, String messageKey) {
        var trusted = trusted(identity, conversationId);
        var scope = new ConversationScope(trusted.tenantId(), trusted.deploymentId(), conversationId, identity.ownerId());
        var control = required(conversations).status(scope);
        var state = required(conversations).orchestrationState(scope, control.controlEpoch()).map(value -> mapper.valueToTree(value.metadata()).path(STATE)).orElse(null);
        if (state == null || !state.path("workId").isTextual() || state.path("workId").asText().isBlank()) return;
        var record = owned(identity, conversationId, state.path("workId").asText());
        // A finished goal can remain in conversation state after Quick edit starts.
        if (record.terminal()) return;
        if (!messageKey.equals(persistedRoot(record).work().turnId())) throw denied();
        required(gateways).controlGoal(record.invocationId(), GoalExecutionControl.CANCEL, trusted);
    }
    private Root persistedRoot(DurableExecutionRecord record) {
        var payload = required(securities).unprotect(record.protectedRequest(), record.invocationId() + ":request");
        if (Boolean.TRUE.equals(payload.get("erased"))) throw missing();
        var context = new TrustedExecutionContext(new ExecutionPrincipal(String.valueOf(payload.get("principalId")), ExecutionPrincipalType.SERVICE),
            new ExecutionSubjectRef(String.valueOf(payload.get("subjectType")), String.valueOf(payload.get("subjectId"))),
            ExecutionSource.APPLICATION, String.valueOf(payload.get("tenantId")), String.valueOf(payload.get("deploymentId")), Set.of(), null, null);
        return lookupRoot(context, record.goalAuthorityReference());
    }
    private Root lookupRoot(TrustedExecutionContext context, String authority) {
        if (context.subject() == null || !"conversation".equals(context.subject().subjectType())) throw denied();
        var scope = new ConversationScope(context.tenantId(), context.deploymentId(), context.subject().subjectId(), context.initiator().principalId());
        var control = required(conversations).status(scope);
        var metadata = required(conversations).orchestrationState(scope, control.controlEpoch()).orElseThrow(RuntimeDurableGoalHost::missing).metadata();
        var roots = mapper.valueToTree(metadata.get(ROOTS));
        var stored = roots.path(authority);
        if (!stored.isTextual()) throw missing();
        var root = mapper.convertValue(required(securities).unprotect(stored.textValue(), "canonical-goal-root:" + authority).get("root"), Root.class);
        if (!authority.equals(root.work().goalId()) || !authority.equals(root.work().authorityReference())
            || !root.conversationId().equals(scope.conversationId()) || !("authoring:" + root.work().sessionId()).equals(scope.ownerId())
            || root.controlEpoch() != control.controlEpoch()) throw denied();
        return root;
    }
    private ConversationScope scopeFromRoot(DurableExecutionRecord record, Root root) {
        var payload = required(securities).unprotect(record.protectedRequest(), record.invocationId() + ":request");
        return new ConversationScope(String.valueOf(payload.get("tenantId")), String.valueOf(payload.get("deploymentId")),
            root.conversationId(), "authoring:" + root.work().sessionId());
    }
    private static ConversationScope scope(TrustedExecutionContext context, Root root) {
        return new ConversationScope(context.tenantId(), context.deploymentId(), root.conversationId(), context.initiator().principalId());
    }
    private JsonNode state(ConversationScope scope, long epoch) {
        return required(conversations).orchestrationState(scope, epoch).map(value -> mapper.valueToTree(value.metadata()).path(STATE))
            .orElseThrow(() -> new OrchestrationControlException("AUTHORING_GOAL_SCOPE_MISSING", "Work scope is unavailable."));
    }
    private void installRoot(Root root, TrustedExecutionContext trusted) {
        var scope = scope(trusted, root);
        required(conversations).updateOrchestrationState(scope, root.controlEpoch(), values -> {
            var copy = new LinkedHashMap<>(values); var prior = mapper.valueToTree(values.get(STATE));
            if (prior.isObject() && !root.work().goalId().equals(prior.path("goalId").asText()) && !TERMINAL.contains(prior.path("state").asText()))
                throw new ResponseStatusException(CONFLICT, "AUTHORING_GOAL_ALREADY_ACTIVE");
            var roots = values.get(ROOTS) instanceof Map<?, ?> stored
                ? new LinkedHashMap<String, Object>(mapper.convertValue(stored, new TypeReference<Map<String, Object>>() {}))
                : new LinkedHashMap<String, Object>();
            if (!roots.containsKey(root.work().goalId())) {
                if (roots.size() >= 64) throw new ResponseStatusException(CONFLICT, "AUTHORING_GOAL_ROOT_RETENTION_LIMIT");
                roots.put(root.work().goalId(), required(securities).protect(Map.of("root", mapper.valueToTree(root)), "canonical-goal-root:" + root.work().goalId()));
            }
            copy.put(ROOTS, roots);
            if (!prior.isObject() || !root.work().goalId().equals(prior.path("goalId").asText())) copy.put(STATE, Map.of(
                "goalId", root.work().goalId(), "workId", "", "requestRevision", root.work().requestRevision(),
                "cancellationEpoch", root.work().cancellationEpoch(), "state", "QUEUED"));
            return copy;
        });
    }
    private void bindWork(Root root, String workId) {
        var record = required(repositories).findById(workId).orElse(null);
        if (record == null) return;
        var scope = scopeFromRoot(record, root);
        required(conversations).updateOrchestrationState(scope, root.controlEpoch(), values -> {
            var copy = new LinkedHashMap<>(values); var next = new LinkedHashMap<>(mapper.convertValue(values.get(STATE), new TypeReference<Map<String, Object>>() {}));
            String existing = String.valueOf(next.getOrDefault("workId", ""));
            if (!root.work().goalId().equals(next.get("goalId")) || !existing.isEmpty() && !existing.equals(workId)) throw denied();
            next.put("workId", workId); copy.put(STATE, next); return copy;
        });
    }
    private boolean updateState(Root root, DurableExecutionRecord record, String lifecycle) {
        String workId = record.invocationId();
        var scope = scopeFromRoot(record, root);
        if (required(conversations).status(scope).controlEpoch() != root.controlEpoch()) return false;
        var applied = new java.util.concurrent.atomic.AtomicBoolean();
        required(conversations).updateOrchestrationState(scope, root.controlEpoch(), values -> {
            var copy = new LinkedHashMap<>(values); var next = new LinkedHashMap<>(mapper.convertValue(values.get(STATE), new TypeReference<Map<String, Object>>() {}));
            var current = required(repositories).findById(workId).orElseThrow(RuntimeDurableGoalHost::missing);
            if (current.version() != record.version() || current.status() != record.status()) return copy;
            if (workId.equals(next.get("workId"))) {
                next.put("state", lifecycle);
                if (!"RUNNING".equals(lifecycle)) next.remove("segmentLease");
                copy.put(STATE, next);
                applied.set(true);
            }
            return copy;
        });
        return applied.get();
    }
    private void bindSegmentLease(Root root, ConversationScope scope, GoalExecutionClaim claim, ConversationTurnLease lease, int segment) {
        var record = required(repositories).findById(claim.invocationId()).orElseThrow();
        if (record.status() != ExecutionHandleStatus.RUNNING || !claim.leaseOwner().equals(record.leaseOwner()))
            throw new OrchestrationControlException("AUTHORING_GOAL_FENCED", "The native segment claim changed.");
        required(conversations).updateOrchestrationState(scope, root.controlEpoch(), values -> {
            var copy = new LinkedHashMap<>(values); var next = new LinkedHashMap<>(mapper.convertValue(values.get(STATE), new TypeReference<Map<String, Object>>() {}));
            if (!claim.invocationId().equals(next.get("workId")) || Set.of("PAUSED", "CANCELLED").contains(next.get("state")))
                throw new OrchestrationControlException("AUTHORING_GOAL_FENCED", "The canonical segment authority changed.");
            next.put("segmentLease", Map.of("leaseId", lease.leaseId(), "expiresAt", lease.expiresAt().toString(), "segment", segment));
            copy.put(STATE, next); return copy;
        });
    }
    private void releaseSegmentLease(Root root, ConversationScope scope, ConversationTurnLease lease) {
        var gateway = required(conversations);
        if (gateway.status(scope).controlEpoch() != root.controlEpoch()) return;
        gateway.updateOrchestrationState(scope, root.controlEpoch(), values -> {
            var copy = new LinkedHashMap<>(values); var current = mapper.valueToTree(values.get(STATE));
            if (lease.leaseId().equals(current.path("segmentLease").path("leaseId").asText())) {
                var next = new LinkedHashMap<>(mapper.convertValue(values.get(STATE), new TypeReference<Map<String, Object>>() {}));
                next.remove("segmentLease"); copy.put(STATE, next);
            }
            return copy;
        });
    }
    private JsonNode checkpoint(DurableExecutionRecord record) {
        return record.protectedWorkflowCheckpoint() == null ? mapper.createObjectNode()
            : mapper.valueToTree(required(securities).unprotect(record.protectedWorkflowCheckpoint(), "workflow-checkpoint:" + record.invocationId()).get("checkpoint"));
    }
    private ChatQueryResponse projection(ExecutionSnapshot snapshot, DurableExecutionRecord record, String conversationId) {
        var goal = new LinkedHashMap<String, Object>(); var checkpoint = checkpoint(record);
        var workflow = workflow(snapshot);
        String state = lifecycle(snapshot.handle().status(), workflow);
        String code = snapshot.handle().status() == ExecutionHandleStatus.EXPIRED ? "GOAL_DEADLINE_EXCEEDED"
            : snapshot.handle().failureReason() == null ? workflow.path("stopCode").asText(null) : snapshot.handle().failureReason();
        goal.put("state", state); goal.put("reasonCode", code);
        var questions = new ArrayList<String>();
        for (var question : workflow.path("questions")) if (question.isTextual()) questions.add(question.textValue());
        goal.put("questions", List.copyOf(questions));
        String message = workflow.path("message").asText(null);
        if ((message == null || message.isBlank()) && !questions.isEmpty()) message = String.join("\n", questions);
        if (message == null && snapshot.result() != null && snapshot.result().failure() != null) message = snapshot.result().failure().publicMessage();
        goal.put("reasonMessage", message);
        boolean resumable = !record.terminal() && clock.instant().isBefore(record.deadline())
            && Set.of(ExecutionHandleStatus.PAUSED, ExecutionHandleStatus.WAITING_FOR_INPUT).contains(record.status());
        goal.put("terminal", record.terminal()); goal.put("canResume", resumable);
        goal.put("nextAction", resumable ? "AUTHORING_REAUTHENTICATION_REQUIRED".equals(code) ? "REAUTHENTICATE_AND_RESUME"
            : questions.isEmpty() ? "RESUME_AFTER_REMEDIATION" : "ANSWER_AND_RESUME" : "VIEW_RESULT");
        goal.put("segment", checkpoint.path("goal").path("segmentNumber").asInt(1));
        goal.put("usage", checkpoint.path("usage")); goal.put("nextEligibleAt", record.nextEligibleAt());
        var output = snapshot.result() == null ? mapper.createObjectNode() : mapper.valueToTree(snapshot.result().output());
        goal.put("progress", workflow.path("progress"));
        if (snapshot.result() != null) {
            String answer = output.path("answer").asText(null);
            if ((answer == null || answer.isBlank()) && "WAITING_FOR_INPUT".equals(state)) answer = message;
            goal.put("answer", answer);
            goal.put("workflow", snapshot.result().diagnostics().get("workflow"));
            goal.put("providerDiagnostics", snapshot.result().diagnostics().get("providerDiagnostics"));
        }
        // Keep SDK Jackson2 trees out of Boot's Jackson3 HTTP converter.
        Map<String, Object> projection = projectionMapper.convertValue(goal, new TypeReference<LinkedHashMap<String, Object>>() {});
        return ChatQueryResponse.builder().success(true).type("WORK_STATUS").conversationId(conversationId)
            .workId(record.invocationId()).goal(Collections.unmodifiableMap(projection)).build();
    }
    static TrustedExecutionContext trusted(RuntimeResolvedIdentity identity, String conversationId) {
        var auth = identity.getAuthContext();
        return new TrustedExecutionContext(new ExecutionPrincipal(identity.ownerId(), ExecutionPrincipalType.SERVICE),
            new ExecutionSubjectRef("conversation", conversationId), ExecutionSource.APPLICATION, auth.getTenantId(), auth.getDeploymentId(),
            Set.copyOf(auth.getGrantedScopes()), null, Instant.now());
    }
    private AIExecutionResult<?> paused(GoalExecutionClaim claim, AIExecutionRequest<?> request, String code) {
        Instant now = clock.instant();
        return new AIExecutionResult<>(claim.invocationId(), request.specialistId(), AIExecutionStatus.PAUSED, null, List.of(),
            Map.of("workflow", Map.of("state", "PAUSED", "stopCode", code)),
            new AIExecutionFailure(code, "Authoring work is paused because current authority or execution state is unavailable.", false), now, now);
    }
    private AIExecutionResult<?> yielded(GoalExecutionClaim claim, AIExecutionRequest<?> request) {
        Instant now = clock.instant();
        return new AIExecutionResult<>(claim.invocationId(), request.specialistId(), AIExecutionStatus.YIELDED, null, List.of(),
            Map.of("workflow", Map.of("state", "YIELDED", "stopCode", "AUTHORING_SEGMENT_AUTHORITY_RENEWAL",
                "nextEligibleAt", now.plusSeconds(1).toString())), null, now, now);
    }
    private JsonNode workflow(ExecutionSnapshot snapshot) {
        if (snapshot.result() == null) return mapper.createObjectNode();
        JsonNode output = mapper.valueToTree(snapshot.result().output());
        return output.path("work").isObject() ? output.path("work") : mapper.valueToTree(snapshot.result().diagnostics()).path("workflow");
    }
    static String lifecycle(ExecutionHandleStatus status, JsonNode workflow) {
        return switch (status) {
            case SUCCEEDED -> "COMPLETE";
            case REJECTED -> "BLOCKED";
            case EXPIRED -> "FAILED";
            case FAILED -> "UNSUPPORTED".equals(workflow.path("state").asText()) ? "BLOCKED" : "FAILED";
            case PAUSED -> "NEEDS_INPUT".equals(workflow.path("state").asText()) ? "WAITING_FOR_INPUT"
                : "UNSUPPORTED".equals(workflow.path("state").asText()) ? "BLOCKED" : "PAUSED";
            default -> status.name();
        };
    }
    private static Instant min(Instant left, Instant right) { return left.isBefore(right) ? left : right; }
    static JsonNode effectiveSources(JsonNode pins, JsonNode lease) {
        var ids = lease.has("sourceIds") ? lease.path("sourceIds") : lease.path("directive").path("sourceIds");
        if (ids.isMissingNode()) return pins.deepCopy();
        if (!ids.isArray()) throw denied();
        var narrowed = ((ObjectNode) pins).objectNode();
        for (var id : ids) {
            if (!id.isTextual() || !pins.has(id.textValue())) throw denied();
            narrowed.set(id.textValue(), pins.get(id.textValue()));
        }
        return narrowed;
    }
    private static String safeCode(String value, String fallback) { return value != null && value.matches("[A-Z][A-Z0-9_]{0,127}") ? value : fallback; }
    private static ResponseStatusException denied() { return new ResponseStatusException(FORBIDDEN, "AUTHORING_GOAL_BINDING_DENIED"); }
    private static ResponseStatusException missing() { return new ResponseStatusException(NOT_FOUND, "AUTHORING_GOAL_NOT_FOUND"); }
    private static <T> T required(ObjectProvider<T> provider) { return provider.getObject(); }
}
