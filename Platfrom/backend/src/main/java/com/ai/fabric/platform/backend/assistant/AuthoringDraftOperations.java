package com.ai.fabric.platform.backend.assistant;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.PlatformSecurityContext;
import com.ai.fabric.platform.backend.security.repository.PlatformUserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

/** Scoped draft operations only. No publishing, provisioning, activation or secret-value API. */
@Service
public class AuthoringDraftOperations {
    public record Binding(String deploymentId, String releaseId, String specialistId, String specialistHash,
                          int maxWrites, boolean previewOnly, List<String> allowedActions) {
        public Binding(String deploymentId, String releaseId, String specialistId, String specialistHash, int maxWrites, boolean previewOnly) {
            this(deploymentId, releaseId, specialistId, specialistHash, maxWrites, previewOnly,
                java.util.stream.Stream.concat(AuthoringNativeCapabilities.READS.stream(), AuthoringNativeCapabilities.WRITES.stream()).sorted().toList());
        }
        public Binding {
            if (deploymentId == null || deploymentId.isBlank() || releaseId == null || releaseId.isBlank()
                || specialistId == null || specialistId.isBlank() || specialistHash == null || specialistHash.isBlank()
                || maxWrites < 0 || maxWrites > 3) throw new IllegalArgumentException("Invalid authoring work binding");
            allowedActions = List.copyOf(allowedActions);
        }
    }
    /** Created only after service authentication; neither a browser DTO nor an LLM argument. */
    public record Caller(String sessionId, String turnId, String deploymentId, String releaseId, String action,
                         boolean selectedSourceReadAllowed) {
        public Caller(String sessionId, String turnId, String deploymentId, String releaseId) {
            this(sessionId, turnId, deploymentId, releaseId, null, false);
        }
        public Caller(String sessionId, String turnId, String deploymentId, String releaseId, String action) {
            this(sessionId, turnId, deploymentId, releaseId, action, false);
        }
    }

    private final AuthoringSessionRepository sessions;
    private final AuthoringCrypto crypto;
    private final AuthoringResourceService resources;
    private final AuthoringChanges changes;
    private final PlatformUserRepository users;
    private final ObjectMapper mapper;
    private final PlatformAuditService audit;
    private final AuthoringDraftLock draftLock;

    public AuthoringDraftOperations(AuthoringSessionRepository sessions, AuthoringCrypto crypto,
        AuthoringResourceService resources, AuthoringChanges changes, PlatformUserRepository users,
        ObjectMapper mapper, PlatformAuditService audit, AuthoringDraftLock draftLock) {
        this.sessions = sessions; this.crypto = crypto; this.resources = resources;
        this.changes = changes; this.users = users; this.mapper = mapper; this.audit = audit; this.draftLock = draftLock;
    }

    @Transactional
    public void bind(String sessionId, String turnId, Binding binding) {
        var principal = PlatformSecurityContext.currentPrincipal();
        if (principal == null || "API_KEY".equals(principal.authenticationMode())) throw denied();
        var user = PlatformUserRepository.findForPrincipal(users, principal).filter(u -> "ACTIVE".equals(u.getStatus()))
            .orElseThrow(AuthoringDraftOperations::denied);
        var session = sessions.lock(sessionId).orElseThrow(AuthoringDraftOperations::denied);
        if (!session.actorId.equals(principal.actorId())) throw denied();
        requireRunning(session, turnId);
        var snapshot = resources.load(session.resourceKind, session.resourceId, session.task);
        if (!session.customerId.equals(snapshot.customerId()) || binding.deploymentId().equals(session.resourceId)) throw denied();
        var content = content(session); var turn = turn(content, turnId);
        if (turn.has("brainWork")) throw new ResponseStatusException(CONFLICT, "AUTHORING_WORK_ALREADY_BOUND");
        var work = turn.putObject("brainWork");
        work.set("binding", mapper.valueToTree(binding));
        work.set("principal", mapper.valueToTree(new PlatformPrincipal(principal.actorId(), principal.role(),
            principal.displayName(), principal.authenticationMode(), user.getId())));
        work.put("resourceFingerprint", fingerprint(snapshot.configuration()));
        work.put("resourceVersion", snapshot.version());
        try { work.set("baselineFindings", resources.check(session.resourceKind, session.resourceId, snapshot, snapshot.configuration())); }
        catch (ResponseStatusException invalid) {
            if (invalid.getStatusCode().value() != 400) throw invalid;
        }
        work.putArray("operations");
        store(session, content);
    }

    @Transactional
    public JsonNode context(Caller caller) {
        return authorized(caller, false, work -> {
            var snapshot = work.snapshot();
            var result = mapper.createObjectNode();
            result.put("resourceKind", work.session().resourceKind).put("task", work.session().task)
                .put("editable", snapshot.editable()).put("readOnlyReason", snapshot.readOnlyReason())
                .put("fingerprint", fingerprint(snapshot.configuration()));
            var configuration = changes.project(snapshot.configuration());
            var contracts = snapshot.catalogue().deepCopy();
            if (List.of("ACTIONS", "CONNECTIONS").contains(work.session().task)) {
                ((ObjectNode) configuration).retain("actionsConfig", "routingConfig");
                result.put("configurationScope", "Only action and endpoint settings are shown. Unshown settings are unchanged, not absent.");
                ((ObjectNode) contracts).remove(List.of("behaviorTypes", "llmProviders", "embeddingProviders", "aiBehaviourProfiles",
                    "aiBehaviourConfigurationExample", "behaviorContract", "humanSupportContract", "specialistContract"));
            }
            result.set("configuration", configuration);
            result.set("contracts", contracts);
            result.set("binding", work.work().path("binding"));
            result.put("command", work.turn().path("command").asText());
            if (work.turn().has("editorContext")) result.set("editorContext", work.turn().path("editorContext"));
            result.set("sourceIds", work.turn().path("sourceIds"));
            result.set("operationIds", work.turn().path("operationIds"));
            result.put("selectedSourceCount", work.turn().path("sourceIds").size());
            if (caller.selectedSourceReadAllowed()
                && contains(work.work().path("binding").path("allowedActions"), "authoring_read_selected_sources")) {
                int remaining = 16000;
                var previews = result.putArray("selectedSourcePreviews");
                for (var source : selectedSourceData(work)) {
                    String text;
                    boolean prose = source.path("content").isTextual();
                    try { text = prose ? source.path("content").asText() : mapper.writeValueAsString(source.path("operations")); }
                    catch (java.io.IOException invalid) { throw new IllegalStateException("Selected documentation could not be prepared.", invalid); }
                    int length = Math.min(remaining, text.length());
                    if (length > 0 && length < text.length() && Character.isHighSurrogate(text.charAt(length - 1))) length--;
                    previews.addObject().put("sourceId", source.path("id").asText()).put("title", source.path("title").asText())
                        .put("representation", prose ? "TEXT" : "PARSED_OPERATIONS")
                        .put("excerpt", text.substring(0, length)).put("complete", length == text.length());
                    remaining -= length;
                }
            }
            result.set("currentValidation", work.work().path("baselineFindings"));
            if (List.of("ACTIONS", "BRAIN_SETUP").contains(work.session().task) && "PREPARE".equals(work.turn().path("command").asText())) {
                var scope = result.putObject("preparationScope");
                scope.put("operationSelection", work.turn().path("operationIds").isEmpty()
                    ? "ALL_SELECTED_SOURCE_OPERATIONS" : "SELECTED_OPERATION_IDS");
                var operations = scope.putArray("documentedOperations");
                for (var source : work.content().path("sources")) {
                    if (!contains(work.turn().path("sourceIds"), source.path("sourceId").asText())) continue;
                    for (var operation : source.path("operations")) {
                        if (!work.turn().path("operationIds").isEmpty()
                            && !contains(work.turn().path("operationIds"), operation.path("id").asText())) continue;
                        var item = operations.addObject().put("sourceId", source.path("sourceId").asText());
                        for (String field : List.of("id", "method", "path", "servers", "kind", "toolName"))
                            if (operation.hasNonNull(field)) item.set(field, operation.get(field));
                    }
                }
                scope.put("documentedOperationCount", operations.size());
            }
            return changes.project(result);
        });
    }

    @Transactional
    public JsonNode selectedSources(Caller caller) {
        return authorized(caller, false, this::selectedSourceData);
    }
    private JsonNode selectedSourceData(Work work) {
        var result = mapper.createArrayNode();
        for (var source : work.content().path("sources")) {
            if (!contains(work.turn().path("sourceIds"), source.path("sourceId").asText())) continue;
            var item = mapper.createObjectNode().put("id", source.path("sourceId").asText())
                .put("title", source.path("title").asText());
            if (!work.turn().path("operationIds").isEmpty() && source.path("operations").isArray() && !source.path("operations").isEmpty()) {
                var selected = mapper.createArrayNode();
                for (var operation : source.path("operations"))
                    if (contains(work.turn().path("operationIds"), operation.path("id").asText())) selected.add(operation);
                if (selected.isEmpty()) continue;
                item.set("operations", selected);
            } else {
                item.put("content", source.path("content").asText());
                if (source.path("operations").isArray()) item.set("operations", source.path("operations"));
            }
            result.add(item);
        }
        return changes.project(result);
    }
    private boolean contains(JsonNode values, String selected) {
        for (var value : values) if (selected.equals(value.asText())) return true;
        return false;
    }

    @Transactional
    public JsonNode check(Caller caller, JsonNode requestedChanges) {
        return authorized(caller, false, work -> {
            var candidate = changes.apply(work.snapshot().configuration(), requestedChanges, work.snapshot().allowedPaths());
            var result = mapper.createObjectNode();
            result.set("findings", resources.check(work.session().resourceKind, work.session().resourceId,
                work.snapshot(), candidate.candidate()));
            result.put("requiresCorrection", AuthoringFeedbackPolicy.requiresCorrection(result.path("findings"),
                work.work().get("baselineFindings"), work.session().task, work.snapshot().configuration(), candidate.candidate()));
            result.set("diff", candidate.diff());
            result.put("fingerprint", fingerprint(work.snapshot().configuration()));
            result.put("status", "CHECKED_NOT_SAVED").put("saved", false);
            result.put("hasChanges", !work.snapshot().configuration().equals(candidate.candidate()));
            result.put("draftSaveRequested", "PREPARE".equals(work.turn().path("command").asText())
                && !work.turn().path("previewOnly").asBoolean() && !work.work().at("/binding/previewOnly").asBoolean()
                && work.snapshot().editable() && contains(work.work().at("/binding/allowedActions"), "authoring_update_draft"));
            result.put("mutationFingerprint", fingerprint(mapper.createObjectNode()
                .put("action", "authoring_update_draft").set("input", requestedChanges)));
            var checked = work.work().putObject("lastCheck");
            checked.set("changes", changes.project(requestedChanges));
            checked.set("diff", result.path("diff")); checked.set("findings", result.path("findings"));
            store(work.session(), work.content());
            return changes.project(result);
        });
    }

    @Transactional
    public JsonNode save(Caller caller, String operationId, JsonNode requestedChanges) {
        return mutate(caller, operationId, "authoring_update_draft", requestedChanges, null);
    }

    @Transactional
    public JsonNode undo(Caller caller, String operationId, String savedOperationId) {
        return mutate(caller, operationId, "authoring_undo_request", mapper.getNodeFactory().textNode(savedOperationId), savedOperationId);
    }

    @Transactional
    public JsonNode status(Caller caller) {
        return authorized(caller, true, work -> changes.project(work.work().path("operations")));
    }

    private JsonNode mutate(Caller caller, String operationId, String action, JsonNode payload, String undoId) {
        if (operationId == null || operationId.length() > 200 || !operationId.startsWith(caller.turnId() + ":step:")) throw denied();
        String parameterHash = fingerprint(mapper.createObjectNode().put("action", action).set("input", payload));
        return authorized(caller, false, work -> {
            var operations = work.work().withArray("operations");
            for (var operation : operations) {
                if (!operationId.equals(operation.path("operationId").asText())) continue;
                if (!parameterHash.equals(operation.path("parameterFingerprint").asText()))
                    throw new ResponseStatusException(CONFLICT, "AUTHORING_OPERATION_REPLAY_CONFLICT");
                return changes.project(operation);
            }
            var binding = work.work().path("binding");
            if (!contains(binding.path("allowedActions"), action)) throw denied();
            if (binding.path("previewOnly").asBoolean() || work.turn().path("previewOnly").asBoolean() || !work.snapshot().editable())
                throw new ResponseStatusException(CONFLICT, "AUTHORING_WORK_READ_ONLY");
            if (operations.size() >= binding.path("maxWrites").asInt())
                throw new ResponseStatusException(TOO_MANY_REQUESTS, "AUTHORING_WRITE_LIMIT");
            AuthoringResourceService.Saved saved;
            if (undoId == null) {
                saved = resources.save(work.session().resourceKind, work.session().resourceId, work.session().task, payload);
            } else {
                JsonNode original = null;
                for (var previous : operations) if (undoId.equals(previous.path("operationId").asText())) original = previous;
                if (original == null || !"authoring_update_draft".equals(original.path("action").asText())) throw denied();
                for (var previous : operations) if (undoId.equals(previous.path("undoes").asText()))
                    throw new ResponseStatusException(CONFLICT, "AUTHORING_OPERATION_ALREADY_UNDONE");
                saved = resources.undo(work.session().resourceKind, work.session().resourceId, work.session().task, original.path("diff"));
            }
            var operation = operations.addObject().put("operationId", operationId).put("action", action)
                .put("parameterFingerprint", parameterHash).put("status", "EXECUTED")
                .put("savedAt", Instant.now().toString()).put("published", false);
            if (undoId != null) operation.put("undoes", undoId);
            operation.set("receipt", saved.receipt()); operation.set("diff", saved.diff()); operation.set("findings", saved.findings());
            operation.put("resourceFingerprint", fingerprint(saved.configuration()));
            // The session lock, normal save and encrypted receipt share this transaction.
            store(work.session(), work.content());
            audit.record("AUTHORING_WORK_DRAFT_SAVED", "AUTHORING_SESSION", work.session().id,
                Map.of("customerId", work.session().customerId, "turnId", caller.turnId(), "operationId", operationId, "action", action));
            return changes.project(operation);
        });
    }

    private JsonNode authorized(Caller caller, boolean statusOnly, Function<Work, JsonNode> operation) {
        var session = sessions.lock(caller.sessionId()).orElseThrow(AuthoringDraftOperations::denied);
        if (!session.expiresAt.isAfter(Instant.now())) throw denied();
        var content = content(session); var turn = turn(content, caller.turnId());
        if (!statusOnly) requireRunning(session, caller.turnId());
        var work = turn.path("brainWork");
        if (!work.isObject() || !caller.deploymentId().equals(work.at("/binding/deploymentId").asText())
            || !caller.releaseId().equals(work.at("/binding/releaseId").asText())) throw denied();
        if (caller.action() != null && !contains(work.at("/binding/allowedActions"), caller.action())) throw denied();
        PlatformPrincipal frozen;
        try { frozen = mapper.treeToValue(work.path("principal"), PlatformPrincipal.class); }
        catch (Exception invalid) { throw denied(); }
        users.findById(frozen.platformUserId()).filter(u -> "ACTIVE".equals(u.getStatus()))
            .filter(u -> frozen.role().name().equals(u.getRole())).orElseThrow(AuthoringDraftOperations::denied);
        if (!session.actorId.equals(frozen.actorId())) throw denied();
        var previous = SecurityContextHolder.getContext();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(frozen, null,
            List.of(new SimpleGrantedAuthority(frozen.role().authority()))));
        SecurityContextHolder.setContext(context);
        try {
            if (!statusOnly && "DEPLOYMENT_DRAFT".equals(session.resourceKind))
                draftLock.lock(session.resourceKind, session.resourceId, work.path("resourceVersion").asText());
            var snapshot = resources.load(session.resourceKind, session.resourceId, session.task);
            if (!session.customerId.equals(snapshot.customerId())) throw denied();
            return operation.apply(new Work(session, content, turn, (ObjectNode) work, snapshot));
        } finally { SecurityContextHolder.setContext(previous); }
    }
    private void requireRunning(AuthoringSessionEntity session, String turnId) {
        if (!"RUNNING".equals(session.status) || !turnId.equals(session.activeTurnId) || session.deadline == null
            || !session.deadline.isAfter(Instant.now())) throw new ResponseStatusException(CONFLICT, "AUTHORING_WORK_NOT_ACTIVE");
    }
    private ObjectNode content(AuthoringSessionEntity session) {
        try { return (ObjectNode) mapper.readTree(crypto.decrypt(session.id, session.encryptedContent)); }
        catch (Exception invalid) { throw new ResponseStatusException(SERVICE_UNAVAILABLE, "AUTHORING_WORK_UNAVAILABLE"); }
    }
    private ObjectNode turn(ObjectNode content, String turnId) {
        for (var turn : content.path("turns")) if (turnId.equals(turn.path("id").asText())) return (ObjectNode) turn;
        throw denied();
    }
    private void store(AuthoringSessionEntity session, ObjectNode content) {
        if (content.toString().length() > 1_000_000) throw new ResponseStatusException(CONFLICT, "AUTHORING_SESSION_FULL");
        session.encryptedContent = crypto.encrypt(session.id, content.toString());
        session.updatedAt = Instant.now(); sessions.save(session);
    }
    private String fingerprint(JsonNode value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical(value).toString().getBytes(StandardCharsets.UTF_8))); }
        catch (Exception impossible) { throw new IllegalStateException(impossible); }
    }
    private JsonNode canonical(JsonNode value) {
        if (value.isObject()) {
            var result = mapper.createObjectNode();
            var fields = new java.util.TreeMap<String, JsonNode>(); value.fields().forEachRemaining(e -> fields.put(e.getKey(), e.getValue()));
            fields.forEach((key, child) -> result.set(key, canonical(child))); return result;
        }
        if (value.isArray()) {
            var result = mapper.createArrayNode(); value.forEach(child -> result.add(canonical(child))); return result;
        }
        return value;
    }
    private static ResponseStatusException denied() { return new ResponseStatusException(FORBIDDEN, "AUTHORING_WORK_ACCESS_DENIED"); }
    private record Work(AuthoringSessionEntity session, ObjectNode content, ObjectNode turn, ObjectNode work,
                        AuthoringResourceService.Snapshot snapshot) { }
}
