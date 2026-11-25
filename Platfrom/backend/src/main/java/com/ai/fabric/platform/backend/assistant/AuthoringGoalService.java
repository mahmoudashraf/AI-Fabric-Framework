package com.ai.fabric.platform.backend.assistant;

import com.ai.fabric.platform.backend.security.*;
import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.security.repository.PlatformUserRepository;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

/** Durable grant and native work projection. Scheduling/semantic continuation belong to Runtime. */
@Service
public class AuthoringGoalService {
    static final Set<String> TERMINAL = Set.of("COMPLETE", "CANCELLED", "FAILED");
    static final Set<String> ACTIVE = Set.of("QUEUED", "RUNNING", "YIELDED");
    static final int MAX_REQUEST_UPDATES = 32;
    static final int MAX_REQUEST_UPDATE_BYTES = 32768;
    private final AuthoringGoalRepository goals;
    private final AuthoringGoalDirectiveRepository directives;
    private final AuthoringOperationRepository operations;
    private final AuthoringSessionRepository sessions;
    private final AuthoringCrypto crypto;
    private final AuthoringChanges changes;
    private final AuthoringResourceService resources;
    private final AuthoringGoalLoginAuthority logins;
    private final PlatformUserRepository users;
    private final AuthoringBrainBindingService bindings;
    private final ConfiguredAuthoringRuntimeClient runtime;
    private final ObjectMapper mapper;
    private final TransactionTemplate tx;
    private final PlatformAuditService audit;

    public AuthoringGoalService(AuthoringGoalRepository goals, AuthoringGoalDirectiveRepository directives,
        AuthoringOperationRepository operations, AuthoringSessionRepository sessions, AuthoringCrypto crypto,
        AuthoringChanges changes, AuthoringResourceService resources, AuthoringGoalLoginAuthority logins,
        PlatformUserRepository users, AuthoringBrainBindingService bindings, ConfiguredAuthoringRuntimeClient runtime,
        ObjectMapper mapper, PlatformTransactionManager transactions, PlatformAuditService audit) {
        this.goals = goals; this.directives = directives; this.operations = operations; this.sessions = sessions;
        this.crypto = crypto; this.changes = changes; this.resources = resources; this.logins = logins;
        this.users = users; this.bindings = bindings; this.runtime = runtime; this.mapper = mapper;
        this.tx = new TransactionTemplate(transactions);
        this.audit = audit;
    }

    @Transactional
    public String prepare(AuthoringSessionEntity session, ObjectNode content, ObjectNode turn,
        AuthoringBrainBindingService.Binding binding, AuthoringResourceService.Snapshot snapshot) {
        if (!binding.autonomousEnabled()) throw new ResponseStatusException(CONFLICT, "AUTHORING_AUTONOMY_NOT_PUBLISHED");
        var principal = PlatformSecurityContext.currentPrincipal();
        if (principal == null || principal.platformUserId() == null || "API_KEY".equals(principal.authenticationMode())) throw denied();
        users.findById(principal.platformUserId()).filter(u -> "ACTIVE".equals(u.getStatus()))
            .filter(u -> principal.role().name().equals(u.getRole())).orElseThrow(AuthoringGoalService::denied);
        var login = logins.capture(principal);
        var goal = new AuthoringGoalEntity();
        goal.id = "cag-" + UUID.randomUUID(); goal.sessionId = session.id; goal.turnId = turn.path("id").asText();
        goal.actorId = session.actorId; goal.state = "QUEUED"; goal.requestRevision = 1; goal.cancellationEpoch = 0; goal.segment = 1;
        goal.validUntil = Instant.parse(login.path("validUntil").asText());
        if (goal.validUntil.isAfter(session.expiresAt)) goal.validUntil = session.expiresAt;
        goal.createdAt = Instant.now(); goal.updatedAt = goal.createdAt; goal.nextPollAt = goal.createdAt;
        var authority = mapper.createObjectNode();
        authority.set("principal", mapper.valueToTree(principal)); authority.set("login", login);
        authority.set("binding", mapper.valueToTree(binding)); authority.set("allowedPaths", mapper.valueToTree(snapshot.allowedPaths()));
        authority.put("customerId", session.customerId).put("resourceKind", session.resourceKind).put("resourceId", session.resourceId)
            .put("task", session.task).put("resourceVersion", snapshot.version()).put("originalRequest", turn.path("query").asText())
            .put("conversationId", content.path("conversationId").asText())
            .put("draftWriteGranted", snapshot.editable() && !turn.path("previewOnly").asBoolean());
        authority.set("sourceIds", turn.path("sourceIds").deepCopy()); authority.set("operationIds", turn.path("operationIds").deepCopy());
        authority.set("sourceVersions", sourceVersions(content, turn.path("sourceIds")));
        goal.encryptedAuthority = encrypt(goal.id, authority); goal.encryptedProjection = encrypt(goal.id, mapper.createObjectNode());
        goals.save(goal);
        turn.put("goalId", goal.id).put("executionMode", "AUTONOMOUS");
        var work = turn.putObject("brainWork");
        work.put("goalId", goal.id).put("resourceVersion", snapshot.version());
        work.set("binding", mapper.valueToTree(new AuthoringDraftOperations.Binding(binding.deploymentId(), binding.releaseId(),
            binding.specialistId(), binding.bundleHash(), turn.path("previewOnly").asBoolean() ? 0 : binding.maxWrites(),
            turn.path("previewOnly").asBoolean(), binding.scopes().stream().filter(s -> s.startsWith("action:")).map(s -> s.substring(7)).toList())));
        work.set("principal", mapper.valueToTree(principal)); work.putArray("operations");
        try { work.set("baselineFindings", resources.check(session.resourceKind, session.resourceId, snapshot, snapshot.configuration())); }
        catch (ResponseStatusException invalid) { if (invalid.getStatusCode().value() != 400) throw invalid; }
        session.deadline = goal.validUntil;
        audit.record("AUTHORING_GOAL_CREATED", "AUTHORING_GOAL", goal.id,
            Map.of("sessionId", session.id, "turnId", goal.turnId, "resourceId", session.resourceId, "releaseId", binding.releaseId(), "profileId", binding.profileId()));
        return goal.id;
    }

    /** Called inside the same session/save transaction as each scoped tool invocation. */
    public AuthoringGoalEntity requireTool(AuthoringSessionEntity session, JsonNode work, AuthoringDraftOperations.Caller caller, boolean statusOnly) {
        String goalId = work.path("goalId").asText();
        if (goalId.isBlank()) return null;
        var goal = goals.lock(goalId).orElseThrow(AuthoringGoalService::denied);
        if (!goal.sessionId.equals(session.id) || !goal.turnId.equals(caller.turnId()) || goal.erasedAt != null) throw denied();
        if (!statusOnly && (!ACTIVE.contains(goal.state) || goal.pendingControl != null
            || !goal.id.equals(caller.goalId()) || goal.requestRevision != caller.requestRevision()
            || goal.cancellationEpoch != caller.cancellationEpoch() || goal.segment != caller.segment()))
            throw new ResponseStatusException(CONFLICT, "AUTHORING_GOAL_FENCED");
        validate(goal, session, !statusOnly);
        return goal;
    }

    @Transactional
    public JsonNode renew(AuthoringDraftOperations.Caller caller, Renewal request) {
        var session = sessions.lock(caller.sessionId()).orElseThrow(AuthoringGoalService::denied);
        var goal = goals.lock(request.goalId()).orElseThrow(AuthoringGoalService::denied);
        if (!session.id.equals(goal.sessionId) || !caller.turnId().equals(goal.turnId) || !ACTIVE.contains(goal.state)
            || Set.of("PAUSE", "CANCEL").contains(goal.pendingControl == null ? "" : goal.pendingControl) || request.requestRevision() != goal.requestRevision
            || request.cancellationEpoch() != goal.cancellationEpoch || request.segment() < goal.segment
            || request.segment() > goal.segment + 1) throw new ResponseStatusException(CONFLICT, "AUTHORING_GOAL_FENCED");
        var authority = validate(goal, session, true);
        var binding = binding(authority);
        if (!caller.releaseId().equals(binding.releaseId()) || !caller.deploymentId().equals(binding.deploymentId())) throw denied();
        if (request.workId() != null) {
            if (!request.workId().matches("[a-zA-Z0-9][a-zA-Z0-9:_-]{0,199}") || goal.workId != null && !goal.workId.equals(request.workId())) throw denied();
            goal.workId = request.workId();
        }
        goal.segment = request.segment(); goal.updatedAt = Instant.now();
        if (Set.of("RESUME", "DIRECTIVE").contains(goal.pendingControl == null ? "" : goal.pendingControl)) goal.pendingControl = null;
        var result = workIdentity(goal).put("validUntil", goal.validUntil.toString()).put("profileId", binding.profileId())
            .put("releaseId", binding.releaseId()).put("bundleHash", binding.bundleHash()).put("conversationId", authority.path("conversationId").asText())
            .put("workId", goal.workId).put("operationNamespace", goal.workId);
        result.set("allowedActions", mapper.valueToTree(binding.scopes().stream().filter(s -> s.startsWith("action:")).map(s -> s.substring(7)).toList()));
        result.set("sourceVersions", authority.path("sourceVersions")); result.set("sourceIds", authority.path("sourceIds"));
        result.set("operationIds", authority.path("operationIds"));
        for (var turn : content(session).path("turns")) if (goal.turnId.equals(turn.path("id").asText())) result.put("previewOnly", turn.path("previewOnly").asBoolean());
        if (goal.requestRevision > 1) {
            var effective = directiveContext(goal); result.set("directive", effective);
            for (String field : List.of("sourceIds", "operationIds")) if (effective.hasNonNull(field)) result.set(field, effective.get(field));
        }
        result.set("runtimeHeaders", mapper.valueToTree(bindings.headers(binding, session.id)));
        return result;
    }

    @Transactional
    public JsonNode control(String sessionId, String goalId, Control request) {
        var pair = owned(sessionId, goalId); var goal = pair.goal(); var session = pair.session();
        if (request == null || request.command() == null || !Set.of("PAUSE", "RESUME", "CANCEL").contains(request.command()))
            throw new ResponseStatusException(BAD_REQUEST, "Choose Pause, Resume or Cancel.");
        if (TERMINAL.contains(goal.state)) {
            if ("CANCELLED".equals(goal.state) && "CANCEL".equals(request.command())) return projection(goal);
            throw new ResponseStatusException(CONFLICT, "AUTHORING_GOAL_TERMINAL");
        }
        if (decrypt(goal.id, goal.encryptedProjection).path("terminal").asBoolean())
            throw new ResponseStatusException(CONFLICT, "AUTHORING_GOAL_TERMINAL");
        if ("RESUME".equals(request.command())) {
            if (!Set.of("PAUSED", "WAITING_FOR_INPUT", "BLOCKED").contains(goal.state) || goal.erasedAt != null) throw new ResponseStatusException(CONFLICT, "AUTHORING_GOAL_NOT_PAUSED");
            var nativeState = decrypt(goal.id, goal.encryptedProjection);
            if (nativeState.path("terminal").asBoolean() || nativeState.has("canResume") && !nativeState.path("canResume").asBoolean())
                throw new ResponseStatusException(CONFLICT, "AUTHORING_NATIVE_GOAL_NOT_RESUMABLE");
            var authority = decrypt(goal.id, goal.encryptedAuthority);
            var login = logins.capture(PlatformSecurityContext.currentPrincipal());
            authority.set("login", login); goal.validUntil = Instant.parse(login.path("validUntil").asText());
            if (goal.validUntil.isAfter(session.expiresAt)) goal.validUntil = session.expiresAt;
            goal.encryptedAuthority = encrypt(goal.id, authority); validate(goal, session, true);
            // A fresh user Resume fences prior segments; delivery retries retain this epoch.
            goal.cancellationEpoch++;
            goal.state = "QUEUED"; goal.reasonCode = null;
            session.status = "RUNNING"; session.activeTurnId = goal.turnId; session.deadline = goal.validUntil;
        } else {
            if ("PAUSE".equals(request.command()) && "PAUSED".equals(goal.state) && "USER_PAUSED".equals(goal.reasonCode)) return projection(goal);
            goal.cancellationEpoch++;
            goal.state = "CANCEL".equals(request.command()) ? "CANCELLED" : "PAUSED";
            goal.reasonCode = "CANCEL".equals(request.command()) ? "USER_CANCELLED" : "USER_PAUSED";
            updateTurn(session, goal);
        }
        goal.pendingControl = request.command(); goal.updatedAt = Instant.now(); goal.nextPollAt = Instant.now();
        audit.record("AUTHORING_GOAL_" + request.command(), "AUTHORING_GOAL", goal.id,
            Map.of("sessionId", sessionId, "requestRevision", goal.requestRevision, "cancellationEpoch", goal.cancellationEpoch));
        return projection(goal);
    }

    @Transactional
    public JsonNode directive(String sessionId, String goalId, Directive request, String key) {
        var pair = owned(sessionId, goalId); var goal = pair.goal();
        if (request == null || request.query() == null || request.query().isBlank() || request.query().length() > 8000
            || key == null || key.isBlank() || key.length() > 128) throw new ResponseStatusException(BAD_REQUEST, "Provide a bounded follow-up and request key.");
        String hash = fingerprint(mapper.valueToTree(request));
        var prior = directives.findByGoalIdAndRequestKey(goal.id, key);
        if (prior.isPresent()) {
            if (!prior.get().commandHash.equals(hash)) throw new ResponseStatusException(CONFLICT, "AUTHORING_DIRECTIVE_REPLAY_CONFLICT");
            return projection(goal);
        }
        if (TERMINAL.contains(goal.state) || decrypt(goal.id, goal.encryptedProjection).path("terminal").asBoolean()
            || goal.erasedAt != null || goal.pendingControl != null && !"DIRECTIVE".equals(goal.pendingControl))
            throw new ResponseStatusException(CONFLICT, "AUTHORING_GOAL_NOT_READY_FOR_DIRECTIVE");
        var authority = validate(goal, pair.session(), true);
        var updates = requestUpdates(goal);
        int bytes = request.query().getBytes(StandardCharsets.UTF_8).length;
        for (var update : updates) bytes += update.path("query").asText().getBytes(StandardCharsets.UTF_8).length;
        // Bound the trusted transport context without silently losing an earlier user requirement.
        if (updates.size() >= MAX_REQUEST_UPDATES || bytes > MAX_REQUEST_UPDATE_BYTES)
            throw new ResponseStatusException(CONFLICT, "AUTHORING_DIRECTIVE_CONTEXT_LIMIT");
        if (request.sourceIds() != null && !subset(request.sourceIds(), authority.path("sourceIds"))
            || request.operationIds() != null && (!permittedOperations(pair.session(), authority, request.operationIds())
                || request.operationIds().isEmpty() && !authority.path("operationIds").isEmpty()))
            throw new ResponseStatusException(FORBIDDEN, "AUTHORING_DIRECTIVE_SCOPE_EXPANSION_DENIED");
        ObjectNode effectiveRequest = mapper.valueToTree(request);
        var previousRequest = currentDirective(goal);
        for (String field : List.of("sourceIds", "operationIds")) if (!effectiveRequest.hasNonNull(field))
            effectiveRequest.set(field, previousRequest.hasNonNull(field) ? previousRequest.get(field) : authority.path(field));
        goal.requestRevision++; goal.cancellationEpoch++;
        var revision = new AuthoringGoalDirectiveEntity(); revision.id = "cad-" + UUID.randomUUID(); revision.goalId = goal.id;
        revision.requestRevision = goal.requestRevision; revision.requestKey = key; revision.commandHash = hash;
        revision.encryptedRequest = encrypt(revision.id, effectiveRequest); revision.createdAt = Instant.now(); directives.save(revision);
        if ("WAITING_FOR_INPUT".equals(goal.state)) {
            goal.state = "QUEUED"; goal.reasonCode = null; updateTurn(pair.session(), goal);
        }
        goal.pendingControl = "DIRECTIVE"; goal.updatedAt = Instant.now(); goal.nextPollAt = Instant.now();
        audit.record("AUTHORING_GOAL_DIRECTIVE", "AUTHORING_GOAL", goal.id,
            Map.of("sessionId", sessionId, "requestRevision", goal.requestRevision, "cancellationEpoch", goal.cancellationEpoch));
        return projection(goal);
    }

    @Transactional(readOnly = true)
    public JsonNode view(String sessionId) {
        return goals.findTopBySessionIdOrderByCreatedAtDesc(sessionId).<JsonNode>map(this::projection).orElse(mapper.nullNode());
    }
    @Transactional
    public JsonNode operationPage(String sessionId, String goalId, int offset, int limit) {
        var pair = owned(sessionId, goalId);
        if (offset < 0 || limit < 1 || limit > 100 || offset % limit != 0)
            throw new ResponseStatusException(BAD_REQUEST, "Use a nonnegative page offset and between one and 100 receipts.");
        var result = mapper.createObjectNode().put("goalId", pair.goal().id).put("offset", offset).put("limit", limit);
        var items = result.putArray("operations");
        for (var operation : operations.findByGoalIdOrderByCreatedAtAscIdAsc(goalId, PageRequest.of(offset / limit, limit)))
            items.add(changes.project(decrypt(operation.id, operation.encryptedReceipt)));
        long total = operations.countByGoalId(goalId); boolean more = (long) offset + items.size() < total;
        result.put("total", total).put("hasMore", more);
        if (more) result.put("nextOffset", offset + items.size());
        return result;
    }
    public boolean hasOpenGoal(String sessionId) {
        return goals.findTopBySessionIdOrderByCreatedAtDesc(sessionId)
            .map(g -> !TERMINAL.contains(g.state) && !decrypt(g.id, g.encryptedProjection).path("terminal").asBoolean()).orElse(false);
    }
    JsonNode currentDirective(AuthoringGoalEntity goal) {
        return goal.requestRevision <= 1 ? mapper.createObjectNode() : directives.findByGoalIdAndRequestRevision(goal.id, goal.requestRevision)
            .map(d -> (JsonNode) decrypt(d.id, d.encryptedRequest)).orElseThrow(AuthoringGoalService::denied);
    }
    private com.fasterxml.jackson.databind.node.ArrayNode requestUpdates(AuthoringGoalEntity goal) {
        var updates = mapper.createArrayNode();
        if (goal.erasedAt != null) return updates;
        for (var revision : directives.findByGoalIdOrderByRequestRevisionAsc(goal.id, PageRequest.of(0, MAX_REQUEST_UPDATES + 1))) {
            if (revision.requestRevision > goal.requestRevision) throw denied();
            updates.addObject().put("query", decrypt(revision.id, revision.encryptedRequest).path("query").asText())
                .put("requestRevision", revision.requestRevision).put("createdAt", revision.createdAt.toString());
        }
        if (updates.size() > MAX_REQUEST_UPDATES || updates.size() != goal.requestRevision - 1) throw denied();
        return updates;
    }
    private ObjectNode directiveContext(AuthoringGoalEntity goal) {
        var result = (ObjectNode) currentDirective(goal).deepCopy();
        result.put("requestRevision", goal.requestRevision).set("requestUpdates", requestUpdates(goal));
        return result;
    }
    @Transactional(readOnly = true)
    public JsonNode receiptsForTurn(String sessionId, String turnId) {
        var result = mapper.createArrayNode();
        for (var goal : goals.findBySessionId(sessionId)) if (goal.turnId.equals(turnId)) {
            int page = 0;
            while (true) {
                var receipts = operations.findByGoalIdOrderByCreatedAtAscIdAsc(goal.id, PageRequest.of(page++, 100));
                for (var receipt : receipts) result.add(decrypt(receipt.id, receipt.encryptedReceipt));
                if (receipts.size() < 100) break;
            }
        }
        return result;
    }
    private ObjectNode projection(AuthoringGoalEntity goal) {
        var authority = decrypt(goal.id, goal.encryptedAuthority); var result = workIdentity(goal);
        result.put("id", goal.id).put("executionMode", "AUTONOMOUS").put("state", goal.state).put("reasonCode", goal.reasonCode)
            .put("workId", goal.workId).put("updatedAt", goal.updatedAt.toString());
        for (String field : List.of("originalRequest", "resourceKind", "resourceId", "task")) result.set(field, authority.path(field));
        result.set("sourceIds", authority.path("sourceIds")); result.set("operationIds", authority.path("operationIds"));
        if (goal.erasedAt == null) for (String field : List.of("sourceIds", "operationIds")) {
            var directive = currentDirective(goal);
            if (directive.hasNonNull(field)) result.set(field, directive.get(field));
        }
        if (goal.erasedAt == null && goal.requestRevision > 1) {
            var revision = directives.findByGoalIdAndRequestRevision(goal.id, goal.requestRevision).orElseThrow(AuthoringGoalService::denied);
            result.putObject("latestRequestUpdate").put("query", decrypt(revision.id, revision.encryptedRequest).path("query").asText())
                .put("requestRevision", revision.requestRevision).put("createdAt", revision.createdAt.toString());
        }
        for (String field : List.of("profileId", "releaseId", "bundleHash")) result.set(field, authority.path("binding").path(field));
        var nativeView = decrypt(goal.id, goal.encryptedProjection);
        for (String field : List.of("progress", "usage", "nextEligibleAt", "reasonMessage", "nextAction", "providerDiagnostics"))
            if (nativeView.has(field)) result.set(field, changes.project(nativeView.get(field)));
        if (!result.has("progress")) { var progress = result.putObject("progress"); progress.putArray("workItems"); progress.putArray("remaining"); }
        boolean terminal = TERMINAL.contains(goal.state) || nativeView.path("terminal").asBoolean();
        result.put("terminal", terminal).put("canPause", !terminal && ACTIVE.contains(goal.state)).put("canResume", !terminal && Set.of("PAUSED", "WAITING_FOR_INPUT", "BLOCKED").contains(goal.state)
            && goal.erasedAt == null && !nativeView.path("terminal").asBoolean()
            && (!nativeView.has("canResume") || nativeView.path("canResume").asBoolean()))
            .put("canCancel", !terminal);
        var receipts = result.putArray("operations");
        for (var operation : operations.findByGoalIdOrderByCreatedAtAscIdAsc(goal.id, PageRequest.of(0, 100)))
            receipts.add(changes.project(decrypt(operation.id, operation.encryptedReceipt)));
        result.put("operationCount", operations.countByGoalId(goal.id));
        var progress = mapper.createObjectNode(); var workItems = progress.putArray("workItems"); var remaining = progress.putArray("remaining");
        if (goal.erasedAt == null) {
            if (nativeView.at("/progress/summary").isTextual()) progress.set("summary", nativeView.at("/progress/summary"));
            var principal = PlatformSecurityContext.currentPrincipal();
            if (principal == null || !goal.actorId.equals(principal.actorId())) throw denied();
            var session = sessions.findById(goal.sessionId).orElseThrow(AuthoringGoalService::denied);
            var snapshot = resources.load(session.resourceKind, session.resourceId, session.task);
            if (!session.customerId.equals(snapshot.customerId())) throw denied();
            var content = content(session); ObjectNode turn = null;
            for (var item : content.path("turns")) if (goal.turnId.equals(item.path("id").asText())) turn = item.deepCopy();
            if (turn != null) {
                var directive = currentDirective(goal);
                for (String field : List.of("sourceIds", "operationIds")) if (directive.hasNonNull(field)) turn.set(field, directive.get(field));
                var coverage = AuthoringSourceCoverage.inspect(mapper, content, turn, snapshot.configuration(), session.task, snapshot.catalogue().path("secretReferences"));
                if (coverage.path("required").asBoolean()) {
                    progress.putObject("coverage").put("completed", coverage.path("completed").asInt()).put("total", coverage.path("total").asInt());
                    for (var item : coverage.path("items")) {
                        String label = item.path("label").asText(item.path("operationId").asText());
                        workItems.addObject().put("id", item.path("sourceId").asText() + "#" + item.path("operationId").asText())
                            .put("label", label).put("state", item.path("state").asText());
                        if (!"SATISFIED".equals(item.path("state").asText())) remaining.add(label);
                    }
                } else for (var receipt : receipts) workItems.addObject().put("id", receipt.path("operationId").asText())
                    .put("operationId", receipt.path("operationId").asText()).put("label", receipt.path("action").asText()).put("state", "SAVED");
            }
        }
        result.set("progress", progress);
        return result;
    }

    /** Mechanical delivery reconciliation/status polling, never calls a provider or plans a next step. */
    @Scheduled(fixedDelay = 2000)
    public void refresh() {
        if (!crypto.configured()) return;
        for (var selected : goals.findTop20ByNextPollAtBeforeOrderByNextPollAtAsc(Instant.now())) refresh(selected.id);
    }
    public void refresh(String goalId) {
        Delivery delivery;
        try {
            delivery = tx.execute(ignored -> {
                var peek = goals.findById(goalId).orElse(null); if (peek == null) return null;
                var session = sessions.lock(peek.sessionId).orElse(null); if (session == null) return null;
                var goal = goals.lock(goalId).orElseThrow();
                if (goal.nextPollAt == null || goal.nextPollAt.isAfter(Instant.now())) return null;
                var authority = decrypt(goal.id, goal.encryptedAuthority); var binding = binding(authority);
                if (goal.pendingControl == null && !ACTIVE.contains(goal.state)) { goal.nextPollAt = null; return null; }
                if (goal.pendingControl == null || Set.of("RESUME", "DIRECTIVE").contains(goal.pendingControl)) validate(goal, session, true);
                var request = workIdentity(goal).put("validUntil", goal.validUntil.toString());
                for (var turn : content(session).path("turns")) if (goal.turnId.equals(turn.path("id").asText()))
                    request.put("previewOnly", turn.path("previewOnly").asBoolean());
                if ("DIRECTIVE".equals(goal.pendingControl)) {
                    request.set("directive", directiveContext(goal));
                }
                if (goal.pendingControl != null) request.put("command", goal.pendingControl);
                goal.nextPollAt = Instant.now().plusSeconds(10);
                return new Delivery(goal.id, goal.sessionId, goal.turnId, session.task, goal.workId, goal.pendingControl,
                    authority.path("originalRequest").asText(), authority.path("conversationId").asText(), binding, request,
                    goal.requestRevision, goal.cancellationEpoch);
            });
        } catch (ResponseStatusException denied) { pauseForAuthority(goalId, denied); return; }
        if (delivery == null) return;
        try {
            JsonNode response;
            if (delivery.workId() == null) {
                if (Set.of("PAUSE", "CANCEL").contains(delivery.command() == null ? "" : delivery.command())) {
                    tx.executeWithoutResult(ignored -> goals.lock(goalId).ifPresent(g -> { g.pendingControl = null; g.nextPollAt = null; }));
                    return;
                }
                response = runtime.startGoal(delivery.binding(), delivery.sessionId(), delivery.conversationId(), delivery.turnId(),
                    delivery.task(), delivery.query(), delivery.request());
            } else if (delivery.command() != null) {
                response = runtime.controlWork(delivery.binding(), delivery.sessionId(), delivery.conversationId(), delivery.workId(), delivery.request());
            } else response = runtime.work(delivery.binding(), delivery.sessionId(), delivery.conversationId(), delivery.workId());
            tx.executeWithoutResult(ignored -> {
                var session = sessions.lock(delivery.sessionId()).orElse(null); if (session == null) return;
                var goal = goals.lock(goalId).orElseThrow();
                if (goal.requestRevision != delivery.revision() || goal.cancellationEpoch != delivery.epoch()) return;
                String workId = response.path("workId").asText(goal.workId);
                if (workId == null || workId.isBlank() || goal.workId != null && !goal.workId.equals(workId))
                    throw new ResponseStatusException(BAD_GATEWAY, "AUTHORING_NATIVE_HANDLE_INVALID");
                goal.workId = workId; goal.pendingControl = null;
                if (goal.erasedAt != null) {
                    goal.state = "CANCELLED"; goal.reasonCode = "SOURCE_WITHDRAWN";
                    goal.encryptedProjection = encrypt(goal.id, mapper.createObjectNode()); goal.updatedAt = Instant.now(); goal.nextPollAt = null;
                    return;
                }
                JsonNode nativeView = response.has("goal") ? response.path("goal") : response;
                String state = normalizeState(nativeView.path("state").asText("QUEUED"));
                if (!"CANCELLED".equals(goal.state) && !("PAUSED".equals(goal.state) && !"RESUME".equals(delivery.command()))) {
                    goal.state = state;
                    goal.reasonCode = nativeView.path("reasonCode").asText(switch (nativeView.path("state").asText()) {
                        case "EXPIRED" -> "GOAL_LIFETIME_EXPIRED"; case "REJECTED" -> "NATIVE_EXECUTION_REJECTED";
                        case "UNSUPPORTED" -> "UNSUPPORTED_WORK"; default -> null;
                    });
                }
                goal.segment = Math.max(goal.segment, nativeView.path("segment").asInt(goal.segment));
                var safe = mapper.createObjectNode();
                for (String field : List.of("progress", "usage", "nextEligibleAt", "reasonMessage", "nextAction", "answer", "questions", "terminal", "canResume"))
                    if (nativeView.has(field)) safe.set(field, changes.project(nativeView.get(field)));
                if (nativeView.hasNonNull("providerDiagnostics")) safe.set("providerDiagnostics", AuthoringProviderDiagnostics.project(mapper, nativeView.get("providerDiagnostics")));
                if (safe.toString().length() > 64000) throw new ResponseStatusException(BAD_GATEWAY, "AUTHORING_NATIVE_PROJECTION_TOO_LARGE");
                goal.encryptedProjection = encrypt(goal.id, safe); goal.updatedAt = Instant.now();
                goal.nextPollAt = ACTIVE.contains(goal.state) ? Instant.now().plusSeconds(2) : null;
                updateTurn(session, goal);
            });
        } catch (RuntimeException transport) {
            // A missing acknowledgement is reconciled under the identical root/message key.
            tx.executeWithoutResult(ignored -> goals.lock(goalId).ifPresent(goal -> {
                if (goal.requestRevision != delivery.revision() || goal.cancellationEpoch != delivery.epoch()
                    || !ACTIVE.contains(goal.state) && goal.pendingControl == null) return;
                goal.reasonCode = "NATIVE_STATUS_UNAVAILABLE"; goal.nextPollAt = Instant.now().plusSeconds(10); goal.updatedAt = Instant.now();
            }));
        }
    }

    @Transactional
    public void revokeActor(String actorId) {
        for (var selected : goals.findByActorIdAndStateNotIn(actorId, TERMINAL)) {
            var session = sessions.lock(selected.sessionId).orElse(null); if (session == null) continue;
            var goal = goals.lock(selected.id).orElseThrow();
            if (decrypt(goal.id, goal.encryptedProjection).path("terminal").asBoolean()) continue;
            goal.state = "PAUSED"; goal.reasonCode = "LOGOUT_REAUTHENTICATION_REQUIRED";
            goal.cancellationEpoch++; goal.pendingControl = "PAUSE"; goal.nextPollAt = Instant.now(); goal.updatedAt = Instant.now(); updateTurn(session, goal);
        }
    }
    @Transactional
    public void erase(String sessionId) {
        var session = sessions.lock(sessionId).orElseThrow(AuthoringGoalService::denied);
        for (var selected : goals.findBySessionId(sessionId)) {
            var goal = goals.lock(selected.id).orElseThrow(); goal.state = "CANCELLED"; goal.reasonCode = "SOURCE_WITHDRAWN";
            goal.cancellationEpoch++; goal.erasedAt = Instant.now(); goal.updatedAt = Instant.now(); goal.pendingControl = "CANCEL"; goal.nextPollAt = Instant.now();
            var authority = decrypt(goal.id, goal.encryptedAuthority); authority.remove("originalRequest");
            goal.encryptedAuthority = encrypt(goal.id, authority); goal.encryptedProjection = encrypt(goal.id, mapper.createObjectNode());
            for (var directive : directives.findByGoalId(goal.id)) directive.encryptedRequest = encrypt(directive.id, mapper.createObjectNode());
            int page = 0;
            while (true) {
                var receipts = operations.findByGoalIdOrderByCreatedAtAscIdAsc(goal.id, PageRequest.of(page++, 100));
                for (var receipt : receipts) {
                    var original = decrypt(receipt.id, receipt.encryptedReceipt); var retained = mapper.createObjectNode();
                    for (String field : List.of("operationId", "action", "status", "savedAt", "published", "parameterFingerprint", "resourceFingerprint", "undoes"))
                        if (original.has(field)) retained.set(field, original.get(field));
                    var effect = retained.putObject("receipt");
                    for (String field : List.of("resourceId", "draftId", "revision", "published", "savedAt"))
                        if (original.path("receipt").has(field)) effect.set(field, original.path("receipt").get(field));
                    retained.put("protectedBodyErased", true);
                    receipt.encryptedReceipt = encrypt(receipt.id, retained);
                }
                if (receipts.size() < 100) break;
            }
            var content = content(session);
            for (var turn : content.path("turns")) if (goal.turnId.equals(turn.path("id").asText()))
                ((ObjectNode) turn).retain("id", "goalId", "executionMode", "previewOnly", "createdAt", "requestKey", "commandHash", "status");
            session.encryptedContent = encrypt(session.id, content);
            updateTurn(session, goal);
        }
    }
    @Transactional
    public void historyErased(String sessionId) {
        sessions.lock(sessionId).orElseThrow(AuthoringGoalService::denied);
        for (var selected : goals.findBySessionId(sessionId)) {
            var goal = goals.lock(selected.id).orElseThrow();
            if (goal.erasedAt != null) { goal.pendingControl = null; goal.nextPollAt = null; }
        }
    }
    private void pauseForAuthority(String goalId, ResponseStatusException failure) {
        tx.executeWithoutResult(ignored -> {
            var peek = goals.findById(goalId).orElse(null); if (peek == null) return;
            var session = sessions.lock(peek.sessionId).orElse(null); if (session == null) return;
            var goal = goals.lock(goalId).orElseThrow(); if (TERMINAL.contains(goal.state)) return;
            goal.state = "PAUSED"; goal.reasonCode = failure.getStatusCode().value() == 401 ? "REAUTHENTICATION_REQUIRED" : "AUTHORITY_UNAVAILABLE";
            goal.cancellationEpoch++; goal.pendingControl = "PAUSE"; goal.nextPollAt = Instant.now(); goal.updatedAt = Instant.now(); updateTurn(session, goal);
        });
    }

    private ObjectNode validate(AuthoringGoalEntity goal, AuthoringSessionEntity session, boolean live) {
        var authority = decrypt(goal.id, goal.encryptedAuthority);
        PlatformPrincipal principal;
        try { principal = mapper.treeToValue(authority.path("principal"), PlatformPrincipal.class); }
        catch (Exception invalid) { throw denied(); }
        if (goal.erasedAt != null || !goal.actorId.equals(session.actorId) || !goal.actorId.equals(principal.actorId())) throw denied();
        users.findById(principal.platformUserId()).filter(u -> "ACTIVE".equals(u.getStatus()))
            .filter(u -> principal.role().name().equals(u.getRole())).orElseThrow(AuthoringGoalService::denied);
        if (live) {
            if (!goal.validUntil.isAfter(Instant.now())) throw new ResponseStatusException(UNAUTHORIZED, "AUTHORING_REAUTHENTICATION_REQUIRED");
            logins.requireCurrent(authority.path("login"), principal);
        }
        as(principal, () -> {
            var current = resources.load(session.resourceKind, session.resourceId, session.task);
            if (!session.customerId.equals(current.customerId()) || !authority.path("customerId").asText().equals(current.customerId())
                || !authority.path("resourceKind").asText().equals(session.resourceKind) || !authority.path("resourceId").asText().equals(session.resourceId)
                || !authority.path("task").asText().equals(session.task)) throw denied();
            if (live && !authority.path("sourceVersions").equals(sourceVersions(content(session), authority.path("sourceIds"))))
                throw new ResponseStatusException(CONFLICT, "AUTHORING_SOURCE_VERSION_CHANGED");
            if (!subset(current.allowedPaths(), authority.path("allowedPaths"))) throw denied();
            if (live && authority.path("draftWriteGranted").asBoolean() && !current.editable())
                throw new ResponseStatusException(CONFLICT, "AUTHORING_RESOURCE_LIFECYCLE_CHANGED");
            if (live && "DEPLOYMENT_DRAFT".equals(session.resourceKind) && !authority.path("resourceVersion").asText().equals(current.version()))
                throw new ResponseStatusException(CONFLICT, "AUTHORING_RESOURCE_LIFECYCLE_CHANGED");
            return null;
        });
        if (live) {
            var pinned = binding(authority); var current = bindings.resolve(session.task);
            if (!pinned.equals(current)) throw new ResponseStatusException(CONFLICT, "AUTHORING_PINNED_DEFINITION_UNAVAILABLE");
        }
        return authority;
    }
    private Pair owned(String sessionId, String goalId) {
        var session = sessions.lock(sessionId).orElseThrow(AuthoringGoalService::denied);
        var principal = PlatformSecurityContext.currentPrincipal();
        if (principal == null || !session.actorId.equals(principal.actorId()) || !session.expiresAt.isAfter(Instant.now())) throw denied();
        var goal = goals.lock(goalId).orElseThrow(AuthoringGoalService::denied);
        if (!session.id.equals(goal.sessionId)) throw denied();
        var snapshot = resources.load(session.resourceKind, session.resourceId, session.task);
        if (!session.customerId.equals(snapshot.customerId())) throw denied();
        return new Pair(session, goal);
    }
    private void updateTurn(AuthoringSessionEntity session, AuthoringGoalEntity goal) {
        var content = content(session);
        for (var item : content.path("turns")) if (goal.turnId.equals(item.path("id").asText())) {
            var turn = (ObjectNode) item;
            String state = goal.state;
            turn.put("status", ACTIVE.contains(state) ? "RUNNING" : "COMPLETE".equals(state)
                ? operations.countByGoalId(goal.id) > 0 ? "SAVED" : "EXPLAIN" : "WAITING_FOR_INPUT".equals(state) ? "NEEDS_INPUT" : state);
            if ("COMPLETE".equals(state) && turn.path("previewOnly").asBoolean() && turn.at("/brainWork/lastCheck").isObject()) {
                turn.put("status", "PREVIEW");
                for (String field : List.of("changes", "diff", "findings")) turn.set(field, turn.at("/brainWork/lastCheck/" + field));
            }
            var projection = decrypt(goal.id, goal.encryptedProjection);
            if (projection.has("providerDiagnostics")) turn.set("providerDiagnostics", projection.get("providerDiagnostics"));
            if (!ACTIVE.contains(state) && projection.has("answer")) turn.set("message", projection.get("answer"));
            if (!ACTIVE.contains(state) && projection.path("questions").isArray()) {
                var questions = turn.putArray("questions");
                for (var question : projection.path("questions")) {
                    if (questions.size() >= 20) break;
                    String text = question.isTextual() ? question.asText() : question.path("question").asText();
                    if (!text.isBlank()) questions.addObject().put("id", "question-" + questions.size()).put("question", text);
                }
            }
        }
        if (session.activeTurnId == null || goal.turnId.equals(session.activeTurnId)) {
            if (ACTIVE.contains(goal.state)) { session.status = "RUNNING"; session.activeTurnId = goal.turnId; session.deadline = goal.validUntil; }
            else { session.status = "IDLE"; session.activeTurnId = null; session.deadline = null; }
        }
        session.encryptedContent = encrypt(session.id, content); session.updatedAt = Instant.now();
    }
    private ObjectNode workIdentity(AuthoringGoalEntity goal) {
        return mapper.createObjectNode().put("authorityId", goal.id).put("goalId", goal.id).put("sessionId", goal.sessionId)
            .put("turnId", goal.turnId).put("requestRevision", goal.requestRevision).put("cancellationEpoch", goal.cancellationEpoch).put("segment", goal.segment);
    }
    private JsonNode sourceVersions(JsonNode content, JsonNode selected) {
        var result = mapper.createObjectNode();
        for (var id : selected) {
            JsonNode found = null;
            for (var source : content.path("sources")) if (id.asText().equals(source.path("sourceId").asText())) found = source;
            if (found == null) throw new ResponseStatusException(CONFLICT, "AUTHORING_SOURCE_WITHDRAWN");
            result.put(id.asText(), AuthoringSourceVersion.of(found));
        }
        return result;
    }
    private boolean permittedOperations(AuthoringSessionEntity session, JsonNode authority, List<String> requested) {
        var permitted = new HashSet<String>();
        for (var source : content(session).path("sources")) if (contains(authority.path("sourceIds"), source.path("sourceId").asText()))
            for (var operation : source.path("operations")) if (authority.path("operationIds").isEmpty() || contains(authority.path("operationIds"), operation.path("id").asText())) permitted.add(operation.path("id").asText());
        return permitted.containsAll(requested);
    }
    private boolean subset(List<String> requested, JsonNode allowed) { return requested.stream().allMatch(id -> contains(allowed, id)); }
    private boolean contains(JsonNode values, String selected) { for (var item : values) if (selected.equals(item.asText())) return true; return false; }
    private String conversationId(AuthoringSessionEntity session) { return content(session).path("conversationId").asText(); }
    private ObjectNode content(AuthoringSessionEntity session) { return decrypt(session.id, session.encryptedContent); }
    private AuthoringBrainBindingService.Binding binding(JsonNode authority) {
        try { return mapper.treeToValue(authority.path("binding"), AuthoringBrainBindingService.Binding.class); }
        catch (Exception invalid) { throw denied(); }
    }
    private String encrypt(String id, JsonNode value) { return crypto.encrypt(id, value.toString()); }
    private ObjectNode decrypt(String id, String value) {
        try { return (ObjectNode) mapper.readTree(crypto.decrypt(id, value)); }
        catch (Exception invalid) { throw new ResponseStatusException(SERVICE_UNAVAILABLE, "AUTHORING_GOAL_CONTENT_UNAVAILABLE"); }
    }
    private String fingerprint(JsonNode value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(value))); }
        catch (Exception impossible) { throw new IllegalStateException(impossible); }
    }
    private <T> T as(PlatformPrincipal principal, Supplier<T> action) {
        var previous = SecurityContextHolder.getContext(); var current = SecurityContextHolder.createEmptyContext();
        current.setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of(new SimpleGrantedAuthority(principal.role().authority()))));
        SecurityContextHolder.setContext(current);
        try { return action.get(); } finally { SecurityContextHolder.setContext(previous); }
    }
    private String normalizeState(String state) {
        return switch (state) {
            case "NEEDS_INPUT" -> "WAITING_FOR_INPUT";
            case "UNSUPPORTED" -> "BLOCKED";
            case "EXPIRED", "REJECTED" -> "FAILED";
            case "QUEUED", "RUNNING", "YIELDED", "PAUSED", "WAITING_FOR_INPUT", "BLOCKED", "COMPLETE", "CANCELLED", "FAILED" -> state;
            default -> throw new ResponseStatusException(BAD_GATEWAY, "AUTHORING_NATIVE_STATE_INVALID");
        };
    }
    private static ResponseStatusException denied() { return new ResponseStatusException(FORBIDDEN, "AUTHORING_GOAL_ACCESS_DENIED"); }
    public record Renewal(String goalId, long requestRevision, long cancellationEpoch, int segment, String workId) {
        public Renewal(String goalId, long requestRevision, long cancellationEpoch, int segment) { this(goalId, requestRevision, cancellationEpoch, segment, null); }
    }
    public record Control(String command) { }
    public record Directive(String query, List<String> sourceIds, List<String> operationIds) { }
    private record Pair(AuthoringSessionEntity session, AuthoringGoalEntity goal) { }
    private record Delivery(String goalId, String sessionId, String turnId, String task, String workId, String command,
        String query, String conversationId, AuthoringBrainBindingService.Binding binding, ObjectNode request, long revision, long epoch) { }
}
