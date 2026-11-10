package com.ai.fabric.platform.backend.assistant;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.security.PlatformSecurityContext;
import com.ai.fabric.platform.backend.tenant.entity.PlatformCustomerEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PreDestroy;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.concurrent.DelegatingSecurityContextRunnable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

@Service
public class ConfigurationAssistantService {
    public static final String CONTRACT = "LOOMAI_CONFIGURATION_AUTHORING_V1";
    private final AuthoringProperties properties;
    private final AuthoringCrypto crypto;
    private final AuthoringSessionRepository sessions;
    private final AuthoringResourceService resources;
    private final AuthoringChanges changes;
    private final AuthoringDocumentImporter documents;
    private final AuthoringRuntimeClient runtime;
    private final ObjectMapper mapper;
    private final PlatformAuditService audit;
    private final EntityManager entities;
    private final TransactionTemplate tx;
    private final ThreadPoolExecutor workers = new ThreadPoolExecutor(2, 4, 30, TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(8), runnable -> { var thread = new Thread(runnable, "configuration-authoring"); thread.setDaemon(true); return thread; },
        new ThreadPoolExecutor.AbortPolicy());
    private final ConcurrentHashMap<String, FutureTask<Void>> pending = new ConcurrentHashMap<>();

    public ConfigurationAssistantService(AuthoringProperties properties, AuthoringCrypto crypto, AuthoringSessionRepository sessions,
        AuthoringResourceService resources, AuthoringChanges changes, AuthoringDocumentImporter documents,
        AuthoringRuntimeClient runtime, ObjectMapper mapper, PlatformAuditService audit,
        EntityManager entities, PlatformTransactionManager transactions) {
        this.properties = properties; this.crypto = crypto; this.sessions = sessions; this.resources = resources;
        this.changes = changes; this.documents = documents; this.runtime = runtime; this.mapper = mapper;
        this.audit = audit; this.entities = entities; this.tx = new TransactionTemplate(transactions);
    }
    public JsonNode status() {
        boolean ready = properties.isEnabled() && crypto.configured() && runtime.configured();
        var result = mapper.createObjectNode().put("enabled", properties.isEnabled()).put("ready", ready)
            .put("documentFetchEnabled", properties.isDocumentFetchEnabled())
            .put("unavailableReason", ready ? "" : "AI configuration is not configured on this environment. Manual editing remains available.");
        result.putObject("validationFeedback").put("defaultEnabled", properties.isValidationFeedbackEnabled() && properties.getValidationFeedbackMaxCorrections() > 0)
            .put("maxCorrections", Math.max(0, Math.min(AuthoringFeedbackPolicy.MAX_CORRECTIONS, properties.getValidationFeedbackMaxCorrections())));
        return result;
    }
    public JsonNode create(CreateSession request) {
        requireEnabled();
        if (request == null) throw bad("Select a configuration resource.");
        var resource = resources.load(request.resourceKind(), request.resourceId(), request.task());
        return tx.execute(ignored -> {
            var existing = sessions.findByActorIdAndResourceKindAndResourceIdOrderByUpdatedAtDesc(actor(), request.resourceKind(), request.resourceId())
                .stream().filter(s -> s.task.equals(request.task()) && s.expiresAt.isAfter(Instant.now())).findFirst();
            if (existing.isPresent()) return view(existing.get(), resource);
            if (sessions.countByActorIdAndExpiresAtAfter(actor(), Instant.now()) >= 30) throw new ResponseStatusException(TOO_MANY_REQUESTS, "Close an older assistant session before creating another.");
            var session = new AuthoringSessionEntity();
            session.id = "cas-" + UUID.randomUUID(); session.actorId = actor(); session.customerId = resource.customerId();
            session.resourceKind = request.resourceKind(); session.resourceId = request.resourceId(); session.task = request.task();
            session.status = "IDLE"; session.createdAt = Instant.now();
            var content = mapper.createObjectNode(); content.putArray("sources"); content.putArray("turns");
            write(session, content); sessions.save(session);
            audit.record("AUTHORING_SESSION_CREATED", "AUTHORING_SESSION", session.id,
                Map.of("customerId", session.customerId, "resourceKind", session.resourceKind, "resourceId", session.resourceId));
            return view(session, resource);
        });
    }
    public JsonNode get(String id) {
        return tx.execute(ignored -> { var session = owned(id, false); return view(session, resource(session)); });
    }
    public JsonNode sourceContent(String id, String sourceId) {
        return tx.execute(ignored -> {
            var session = owned(id, false); resource(session);
            for (var source : content(session).path("sources")) if (sourceId.equals(source.path("sourceId").asText()))
                return mapper.createObjectNode().put("id", sourceId).put("title", source.path("title").asText())
                    .put("content", source.path("content").asText());
            throw new ResponseStatusException(NOT_FOUND, "Assistant source not found.");
        });
    }
    public JsonNode reusableSources(String id) {
        return tx.execute(ignored -> {
            var target = owned(id, false); resource(target); var result = mapper.createArrayNode();
            for (var prior : sessions.findTop20ByActorIdAndCustomerIdAndExpiresAtAfterOrderByUpdatedAtDesc(actor(), target.customerId, Instant.now())) {
                if (prior.id.equals(id)) continue;
                try { resource(prior); }
                catch (ResponseStatusException denied) { continue; }
                for (var source : content(prior).path("sources")) result.addObject().put("sessionId", prior.id)
                    .put("sourceId", source.path("sourceId").asText()).put("title", source.path("title").asText())
                    .put("resourceKind", prior.resourceKind).put("resourceId", prior.resourceId)
                    .put("task", prior.task).put("mediaType", source.path("mediaType").asText());
            }
            return result;
        });
    }
    public JsonNode reuseSources(String id, ReuseSources request) {
        requireEnabled();
        if (request == null || request.sourceIds() == null || request.sourceIds().isEmpty() || request.sourceIds().size() > 10)
            throw bad("Select between one and ten source documents.");
        return tx.execute(ignored -> {
            var target = owned(id, true); var selectedResource = resource(target); requireIdle(target);
            var prior = owned(request.sessionId(), false); resource(prior);
            if (!target.customerId.equals(prior.customerId)) throw new ResponseStatusException(NOT_FOUND, "Assistant source not found.");
            var content = content(target); var sources = (ArrayNode) content.path("sources");
            var available = content(prior).path("sources");
            for (String sourceId : request.sourceIds()) {
                JsonNode found = null;
                for (var source : available) if (sourceId.equals(source.path("sourceId").asText())) found = source;
                if (found == null) throw new ResponseStatusException(NOT_FOUND, "Assistant source not found.");
                boolean exists = false;
                for (var source : sources) exists |= sourceId.equals(source.path("sourceId").asText());
                if (!exists) sources.add(found.deepCopy());
            }
            int characters = 0; for (var source : sources) characters += source.path("content").asText().length();
            if (sources.size() > 10 || characters > 200_000) throw bad("Selected sources exceed this session's document limits.");
            write(target, content); return view(target, selectedResource);
        });
    }
    public JsonNode addSource(String id, SourceRequest request) {
        requireEnabled();
        if (request == null) throw bad("Provide a document, example or documentation URL.");
        // Authorization precedes parsing/fetching and is checked again before persistence.
        var binding = get(id);
        String sourceId = "src-" + UUID.randomUUID();
        AuthoringDocumentImporter.ImportResult imported;
        if (request.url() != null && !request.url().isBlank()) {
            if (!properties.isDocumentFetchEnabled()) throw bad("Documentation URL fetching is disabled. Upload or paste the document instead.");
            imported = documents.fetchPublicDocument(sourceId, request.url());
        } else {
            byte[] bytes;
            try {
                if (request.base64() != null && request.base64().length() > 2_800_000) throw bad("The source exceeds 2 MiB.");
                bytes = request.base64() != null ? Base64.getDecoder().decode(request.base64())
                    : (request.content() == null ? "" : request.content()).getBytes(StandardCharsets.UTF_8);
            } catch (IllegalArgumentException invalid) { throw bad("The uploaded source is not valid base64."); }
            imported = documents.importDocument(sourceId, request.title(), request.mediaType(), bytes);
        }
        return tx.execute(ignored -> {
            var session = owned(id, true); var resource = resource(session); requireIdle(session);
            var content = content(session); var sources = (ArrayNode) content.path("sources");
            if (sources.size() >= 10) throw bad("A session supports at most 10 selected sources.");
            int size = imported.content().length();
            for (var source : sources) size += source.path("content").asText().length();
            if (size > 200_000) throw bad("Selected sources exceed 200,000 characters. Remove a source or narrow the API document.");
            sources.add(mapper.valueToTree(imported)); write(session, content);
            return view(session, resource);
        });
    }
    public JsonNode removeSource(String id, String sourceId) {
        cancel(id);
        return tx.execute(ignored -> {
            var session = owned(id, true); var resource = resource(session); var content = content(session);
            var sources = (ArrayNode) content.path("sources");
            for (int i = sources.size() - 1; i >= 0; i--) if (sourceId.equals(sources.get(i).path("sourceId").asText())) sources.remove(i);
            // Delete dependent conversational/source bodies, not legitimate already-saved configuration.
            content.putArray("turns"); session.status = "IDLE"; write(session, content);
            return view(session, resource);
        });
    }
    public JsonNode turn(String id, TurnRequest request, String key) {
        requireEnabled();
        if (request == null) throw bad("Provide a configuration request or preparation command.");
        if (key == null || key.isBlank() || key.length() > 128) throw bad("An Idempotency-Key is required.");
        var feedback = AuthoringFeedbackPolicy.resolve(request.validationFeedback(), properties);
        String commandHash = digest(mapper.valueToTree(request).toString());
        var claim = tx.execute(ignored -> {
            var session = owned(id, true); var resource = resource(session); var content = content(session);
            var intent = AuthoringTaskIntent.resolve(session.task, request.query(), request.command(), request.editorContext());
            for (JsonNode turn : content.path("turns")) if (key.equals(turn.path("requestKey").asText())) {
                if (!commandHash.equals(turn.path("commandHash").asText())) throw new ResponseStatusException(CONFLICT, "This turn key already belongs to a different request.");
                return new Claim(session.id, null, view(session, resource));
            }
            requireIdle(session);
            var sourceIds = new java.util.ArrayList<String>();
            content.path("sources").forEach(source -> sourceIds.add(source.path("sourceId").asText()));
            if (request.sourceIds() != null && !sourceIds.containsAll(request.sourceIds())) throw bad("A selected source is not part of this session.");
            var knownOperations = new java.util.HashSet<String>();
            for (var source : content.path("sources")) {
                if (request.sourceIds() != null && !request.sourceIds().contains(source.path("sourceId").asText())) continue;
                for (var operation : source.path("operations")) knownOperations.add(operation.path("id").asText());
            }
            if (request.operationIds() != null && !knownOperations.containsAll(request.operationIds())) throw bad("A selected operation is not part of the selected sources.");
            entities.find(PlatformCustomerEntity.class, session.customerId, LockModeType.PESSIMISTIC_WRITE);
            if (sessions.countByCustomerIdAndStatus(session.customerId, "RUNNING") >= 2 || sessions.countByStatus("RUNNING") >= 12)
                throw new ResponseStatusException(TOO_MANY_REQUESTS, "The assistant is busy. Your draft is unchanged; try again shortly.");
            var turns = (ArrayNode) content.path("turns");
            if (turns.size() >= 30) throw bad("This session has reached 30 turns. Start a new session to keep the configuration context bounded.");
            String turnId = "cat-" + UUID.randomUUID();
            var turn = turns.addObject().put("id", turnId).put("query", documents.redactText(intent.query()))
                .put("previewOnly", request.previewOnly()).put("requestKey", key).put("commandHash", commandHash)
                .put("status", "RUNNING").put("createdAt", Instant.now().toString());
            if (intent.command() != null) turn.put("command", intent.command()).put("hint", documents.redactText(request.query() == null ? "" : request.query()));
            turn.set("editorContext", mapper.valueToTree(intent.editorContext()));
            turn.set("validationFeedback", mapper.valueToTree(feedback)); turn.putArray("attempts");
            turn.set("sourceIds", mapper.valueToTree(request.sourceIds() == null ? sourceIds : request.sourceIds()));
            turn.set("operationIds", mapper.valueToTree(request.operationIds() == null ? List.of() : request.operationIds()));
            session.status = "RUNNING"; session.activeTurnId = turnId; session.deadline = Instant.now().plusSeconds(140);
            write(session, content);
            return new Claim(session.id, turnId, view(session, resource));
        });
        if (claim.turnId() != null) {
            var work = new FutureTask<Void>(new DelegatingSecurityContextRunnable(() -> run(claim.sessionId(), claim.turnId())), null);
            pending.put(claim.turnId(), work);
            try { workers.execute(work); }
            catch (java.util.concurrent.RejectedExecutionException full) { pending.remove(claim.turnId()); fail(id, claim.turnId(), "The assistant is busy. No draft change was made."); }
        }
        return get(id);
    }
    private void run(String id, String turnId) {
        JsonNode generated = null;
        JsonNode failedInferenceReceipt = null;
        try {
            ArrayNode accumulated = mapper.createArrayNode();
            JsonNode previousFindings = null;
            for (int number = 1; ; number++) {
                failedInferenceReceipt = null;
                final int attemptNumber = number;
                final JsonNode feedbackFindings = previousFindings;
                JsonNode inferenceInput = tx.execute(ignored -> {
                    var session = owned(id, true); var resource = resource(session); requireCurrent(session, turnId);
                    var content = content(session); var turn = findTurn(content, turnId);
                    var policy = policy(turn);
                    if (attemptNumber > policy.maxAttempts()) throw bad("The configured correction limit was reached.");
                    ObjectNode input = (ObjectNode) input(session, content, resource, turnId);
                    var candidate = changes.apply(resource.configuration(), accumulated, resource.allowedPaths()).candidate();
                    input.set("currentConfiguration", changes.project(candidate));
                    if (policy.enabled()) {
                        input.put("turnId", turnId + "-attempt-" + attemptNumber);
                        var context = ((ObjectNode) input.path("catalogue")).putObject("validationFeedback")
                            .put("attempt", attemptNumber).put("maxAttempts", policy.maxAttempts())
                            .put("deadlineAt", session.deadline.toString());
                        if (feedbackFindings != null) {
                            var diagnostic = mapper.createObjectNode(); diagnostic.set("configuration", candidate);
                            diagnostic.set("findings", feedbackFindings); diagnostic.set("changes", accumulated);
                            var safe = changes.project(diagnostic);
                            context.set("previousFindings", safe.path("findings"));
                            context.set("previousChanges", safe.path("changes"));
                            ((ObjectNode) input.path("catalogue")).set("currentValidation", safe.path("findings"));
                        }
                    }
                    if (input.toString().length() > 120_000) throw bad("The correction context is too large. Continue in the editor or narrow the request.");
                    turn.withArray("attempts").addObject().put("number", attemptNumber).put("status", "RUNNING")
                        .put("startedAt", Instant.now().toString());
                    write(session, content);
                    return input;
                });
                generated = null;
                JsonNode output;
                try { output = runtime.generate(inferenceInput); }
                catch (AuthoringRuntimeClient.AuthoringRuntimeFailure invalidOutput) {
                    if (!"AUTHORING_INVALID_OUTPUT".equals(invalidOutput.getReason())) throw invalidOutput;
                    // A concurrent cancellation/authority check must not discard paid inference evidence.
                    failedInferenceReceipt = invalidOutput.receipt();
                    var result = tx.execute(ignored -> invalidOutputAttempt(id, turnId, attemptNumber, invalidOutput.receipt()));
                    if (!result.again()) throw invalidOutput;
                    previousFindings = result.findings();
                    continue;
                }
                generated = output;
                validateEnvelope(output);
                var result = tx.execute(ignored -> evaluateAttempt(id, turnId, attemptNumber, accumulated, output));
                previousFindings = result.findings();
                if (!result.again()) {
                    generated = result.output();
                    tx.executeWithoutResult(ignored -> {
                        var session = owned(id, true); var resource = resource(session); requireCurrent(session, turnId);
                        var content = content(session); var turn = findTurn(content, turnId);
                        complete(session, content, turn, resource, result.output(), turn.path("previewOnly").asBoolean());
                    });
                    break;
                }
            }
        } catch (Exception failure) {
            String message = failure instanceof ResponseStatusException known && known.getReason() != null
                ? documents.redactText(known.getReason()) : "AI configuration could not complete. Your last saved draft remains available.";
            fail(id, turnId, message, failure instanceof AuthoringRuntimeClient.AuthoringRuntimeFailure known ? known.receipt() : failedInferenceReceipt, generated);
        } finally { pending.remove(turnId); }
    }

    private AttemptResult evaluateAttempt(String id, String turnId, int number, ArrayNode accumulated, JsonNode output) {
        var session = owned(id, true); var resource = resource(session); requireCurrent(session, turnId);
        var content = content(session); var turn = findTurn(content, turnId);
        var policy = policy(turn);
        var attempt = (ObjectNode) turn.withArray("attempts").get(number - 1);
        var safe = changes.project(output);
        for (String field : List.of("runtime", "message", "changes", "questions", "diagnostics"))
            if (safe.has(field)) attempt.set(field, safe.get(field));
        attempt.put("completedAt", Instant.now().toString());
        String state = output.path("status").asText();
        boolean writableResult = "READY".equals(state);
        // A request for information/explanation is a model stop decision, not permission to keep trying.
        if (writableResult) for (var change : output.path("changes")) accumulated.add(change.deepCopy());
        var finalOutput = output.deepCopy();
        if (writableResult) ((ObjectNode) finalOutput).set("changes", accumulated.deepCopy());
        JsonNode findings = null;
        if (writableResult && !accumulated.isEmpty()) {
            var candidate = changes.apply(resource.configuration(), accumulated, resource.allowedPaths());
            try { findings = resources.check(session.resourceKind, session.resourceId, resource, candidate.candidate()); }
            catch (ResponseStatusException invalid) {
                if (!policy.enabled() || invalid.getStatusCode().value() != 400) throw invalid;
                findings = mapper.createObjectNode().put("valid", false).put("errorCount", 1);
                ((ObjectNode) findings).putArray("issues").addObject().put("severity", "ERROR")
                    .put("code", "CONFIGURATION_CHECK_REJECTED").put("message", invalid.getReason() == null ? "The candidate configuration requires correction." : invalid.getReason());
            }
            var diagnostic = mapper.createObjectNode(); diagnostic.set("configuration", candidate.candidate()); diagnostic.set("findings", findings);
            findings = changes.project(diagnostic).path("findings");
            attempt.set("findings", findings);
        }
        boolean errors = AuthoringFeedbackPolicy.errors(findings);
        boolean again = policy.enabled() && writableResult && errors && !output.path("changes").isEmpty() && number < policy.maxAttempts();
        attempt.put("status", errors ? "VALIDATION_ERRORS" : AuthoringFeedbackPolicy.checked(findings) ? "CHECKED" : state);
        if (again) {
            turn.put("feedbackStopReason", "CORRECTING_VALIDATION_ERRORS"); write(session, content);
        } else {
            String reason = !policy.enabled() ? "DISABLED" : !writableResult ? state
                : errors ? number >= policy.maxAttempts() ? "CORRECTION_LIMIT_REACHED" : "NO_CORRECTION_PROPOSED"
                : AuthoringFeedbackPolicy.checked(findings) ? "VALIDATION_PASSED" : "CHECK_NOT_AVAILABLE";
            turn.put("feedbackStopReason", reason);
            write(session, content);
        }
        return new AttemptResult(again, findings, finalOutput);
    }

    private AttemptResult invalidOutputAttempt(String id, String turnId, int number, JsonNode receipt) {
        var session = owned(id, true); resource(session); requireCurrent(session, turnId);
        var content = content(session); var turn = findTurn(content, turnId); var policy = policy(turn);
        var findings = mapper.createObjectNode().put("valid", false).put("errorCount", 1);
        findings.putArray("issues").addObject().put("severity", "ERROR").put("code", "AUTHORING_INVALID_OUTPUT")
            .put("message", "The previous response did not meet the whole JSON authoring contract. Return the required structured envelope.");
        var attempt = (ObjectNode) turn.withArray("attempts").get(number - 1);
        attempt.put("status", "INVALID_OUTPUT").put("completedAt", Instant.now().toString());
        attempt.set("findings", findings); attempt.set("runtime", changes.project(receipt));
        boolean again = policy.enabled() && number < policy.maxAttempts();
        turn.put("feedbackStopReason", again ? "CORRECTING_VALIDATION_ERRORS" : policy.enabled() ? "CORRECTION_LIMIT_REACHED" : "DISABLED");
        write(session, content);
        return new AttemptResult(again, findings, null);
    }

    private AuthoringFeedbackPolicy policy(JsonNode turn) {
        var node = turn.path("validationFeedback");
        return new AuthoringFeedbackPolicy(node.path("enabled").asBoolean(), node.path("maxCorrections").asInt());
    }
    private JsonNode input(AuthoringSessionEntity session, ObjectNode content, AuthoringResourceService.Snapshot resource, String turnId) {
        var turn = findTurn(content, turnId);
        var input = mapper.createObjectNode().put("contractVersion", CONTRACT).put("sessionId", session.id)
            .put("turnId", turnId).put("resourceKind", session.resourceKind).put("task", session.task)
            .put("query", turn.path("query").asText());
        input.set("currentConfiguration", changes.project(resource.configuration()));
        ObjectNode catalogue = resource.catalogue().deepCopy();
        if (turn.has("editorContext")) catalogue.set("editorContext", changes.project(turn.get("editorContext")));
        JsonNode findings;
        try {
            findings = resources.check(session.resourceKind, session.resourceId, resource, resource.configuration());
        } catch (ResponseStatusException invalidCurrent) {
            if (invalidCurrent.getStatusCode().value() != 400) throw invalidCurrent;
            findings = mapper.createObjectNode().put("checked", false).put("status", 400)
                .put("message", invalidCurrent.getReason() == null ? "The current draft requires correction." : invalidCurrent.getReason());
        }
        // Include the original configuration only in the local redaction pass, never as a second model input.
        var diagnosticContext = mapper.createObjectNode();
        diagnosticContext.set("configuration", resource.configuration()); diagnosticContext.set("findings", findings);
        catalogue.set("currentValidation", changes.project(diagnosticContext).path("findings"));
        input.set("catalogue", catalogue);
        var selected = input.putArray("sources");
        for (var source : content.path("sources")) {
            if (!contains(turn.path("sourceIds"), source.path("sourceId").asText())) continue;
            var item = selected.addObject().put("id", source.path("sourceId").asText()).put("title", source.path("title").asText());
            if (!turn.path("operationIds").isEmpty() && source.path("operations").isArray() && !source.path("operations").isEmpty()) {
                var operations = mapper.createArrayNode();
                for (var operation : source.path("operations")) if (contains(turn.path("operationIds"), operation.path("id").asText())) operations.add(operation);
                if (operations.isEmpty()) { selected.remove(selected.size() - 1); continue; }
                item.put("content", operations.toString());
            } else item.put("content", source.path("content").asText());
        }
        var history = input.putArray("priorTurns");
        var turns = content.path("turns");
        for (int i = Math.max(0, turns.size() - 9); i < turns.size(); i++) {
            var prior = turns.get(i); if (turnId.equals(prior.path("id").asText())) continue;
            history.addObject().put("role", "USER").put("content", prior.path("query").asText());
            if (prior.hasNonNull("message")) history.addObject().put("role", "ASSISTANT").put("content", prior.path("message").asText());
        }
        if (input.toString().length() > 120_000) throw bad("The selected context is too large. Select fewer API operations or sources.");
        return input;
    }
    private void complete(AuthoringSessionEntity session, ObjectNode content, ObjectNode turn,
        AuthoringResourceService.Snapshot resource, JsonNode output, boolean preview) {
        validateEnvelope(output);
        String status = output.path("status").asText();
        turn.put("message", documents.redactText(output.path("message").asText()));
        for (String field : List.of("changes", "questions", "assumptions", "sourceReferences", "runtime", "diagnostics"))
            if (output.has(field)) turn.set(field, changes.project(output.get(field)));
        boolean awaitingInput = "NEEDS_INPUT".equals(status) && policy(turn).enabled();
        if (awaitingInput) turn.put("notice", "More information is needed. Proposed changes remain in the attempt history; no draft change was saved.");
        if (!awaitingInput && !output.path("changes").isEmpty()) {
            var candidate = changes.apply(resource.configuration(), output.path("changes"), resource.allowedPaths());
            turn.set("diff", candidate.diff());
            turn.set("findings", resources.check(session.resourceKind, session.resourceId, resource, candidate.candidate()));
            if (!preview && resource.editable()) {
                var saved = resources.save(session.resourceKind, session.resourceId, session.task, output.path("changes"));
                turn.set("receipt", saved.receipt()); turn.set("diff", saved.diff()); turn.set("findings", saved.findings()); status = "SAVED";
                audit.record("AUTHORING_DRAFT_SAVED", "AUTHORING_SESSION", session.id,
                    Map.of("customerId", session.customerId, "resourceId", session.resourceId, "turnId", turn.path("id").asText(), "changeCount", output.path("changes").size()));
            } else { status = "PREVIEW"; if (!resource.editable()) turn.put("notice", resource.readOnlyReason()); }
        }
        turn.put("status", status); session.status = "IDLE"; session.activeTurnId = null; session.deadline = null; write(session, content);
    }

    private void validateEnvelope(JsonNode output) {
        if (!CONTRACT.equals(output.path("contractVersion").asText()) || !Set.of("READY", "NEEDS_INPUT", "EXPLAIN", "UNSUPPORTED").contains(output.path("status").asText()))
            throw bad("The assistant returned an unsupported structured result. No draft change was made.");
        if (!output.path("message").isTextual() || output.path("message").asText().length() > 8_000 || !output.path("changes").isArray())
            throw bad("The assistant returned an incomplete structured result. No draft change was made.");
        String status = output.path("status").asText();
        if (Set.of("EXPLAIN", "UNSUPPORTED").contains(status) && !output.path("changes").isEmpty()) throw bad("An explanation cannot carry draft writes.");
    }
    public JsonNode proposal(String id, String turnId, String command) {
        requireEnabled();
        return tx.execute(ignored -> {
            var session = owned(id, true); var resource = resource(session); requireIdle(session);
            var content = content(session); var turn = findTurn(content, turnId);
            if ("apply".equals(command)) {
                if (!"PREVIEW".equals(turn.path("status").asText())) throw bad("Only an unsaved preview can be applied.");
                complete(session, content, turn, resource, turn.deepCopy().put("contractVersion", CONTRACT).put("status", "READY"), false);
            } else if ("check".equals(command)) {
                var candidate = changes.apply(resource.configuration(), turn.path("changes"), resource.allowedPaths());
                turn.set("findings", resources.check(session.resourceKind, session.resourceId, resource, candidate.candidate())); write(session, content);
            } else if ("undo".equals(command)) {
                if (!"SAVED".equals(turn.path("status").asText())) throw bad("Only a saved draft change can be undone.");
                var saved = resources.undo(session.resourceKind, session.resourceId, session.task, turn.path("diff"));
                turn.put("status", "UNDONE"); turn.set("undoReceipt", saved.receipt()); write(session, content);
                audit.record("AUTHORING_DRAFT_UNDONE", "AUTHORING_SESSION", session.id,
                    Map.of("resourceId", session.resourceId, "turnId", turnId));
            } else throw bad("Unsupported proposal operation.");
            return view(session, resource(session));
        });
    }
    public JsonNode cancel(String id) {
        return cancel(id, null);
    }
    public JsonNode cancel(String id, String expectedTurnId) {
        String turnId = tx.execute(ignored -> {
            var session = owned(id, true); resource(session);
            if (!"RUNNING".equals(session.status)) return null;
            if (expectedTurnId != null && !expectedTurnId.equals(session.activeTurnId))
                throw new ResponseStatusException(CONFLICT, "A newer assistant turn is running. Refresh before cancelling it.");
            String active = session.activeTurnId; var content = content(session);
            findTurn(content, active).put("status", "CANCELLED").put("message", "Cancelled. No further draft writes will be applied by this turn.");
            session.activeTurnId = null; session.status = "IDLE"; session.deadline = null; write(session, content); return active;
        });
        if (turnId != null) { var future = pending.remove(turnId); if (future != null) future.cancel(true); }
        return get(id);
    }
    public void delete(String id) {
        cancel(id);
        tx.executeWithoutResult(ignored -> { var session = owned(id, true); resource(session); sessions.delete(session);
            audit.record("AUTHORING_SESSION_DELETED", "AUTHORING_SESSION", id, Map.of("resourceId", session.resourceId)); });
    }
    private void fail(String id, String turnId, String message) {
        fail(id, turnId, message, null);
    }
    private void fail(String id, String turnId, String message, JsonNode receipt) {
        fail(id, turnId, message, receipt, null);
    }
    private void fail(String id, String turnId, String message, JsonNode receipt, JsonNode generated) {
        tx.executeWithoutResult(ignored -> sessions.lock(id).ifPresent(session -> {
            var content = content(session);
            ObjectNode turn = null;
            for (var candidate : content.path("turns")) if (turnId.equals(candidate.path("id").asText())) turn = (ObjectNode) candidate;
            if (turn == null) return;
            boolean current = turnId.equals(session.activeTurnId) && "RUNNING".equals(session.status);
            if (turn.path("attempts").isArray() && !turn.path("attempts").isEmpty()) {
                var attempt = (ObjectNode) turn.withArray("attempts").get(turn.path("attempts").size() - 1);
                if ("RUNNING".equals(attempt.path("status").asText())) {
                    attempt.put("status", current ? "FAILED" : turn.path("status").asText())
                        .put("completedAt", Instant.now().toString()).put("message", message);
                    if (receipt != null) attempt.set("runtime", changes.project(receipt));
                    else if (generated != null && generated.has("runtime")) attempt.set("runtime", changes.project(generated.path("runtime")));
                }
            }
            if (!current) { write(session, content); return; }
            // Completion can roll back after paid generation; preserve evidence in this separate transaction.
            if (generated != null) {
                var safe = changes.project(generated);
                for (String field : List.of("changes", "questions", "assumptions", "sourceReferences", "runtime", "diagnostics"))
                    if (safe.has(field)) turn.set(field, safe.get(field));
                if (safe.path("message").isTextual() && safe.path("message").asText().length() <= 8_000)
                    turn.put("unverifiedModelMessage", documents.redactText(safe.path("message").asText()));
                turn.put("notice", "The generated proposal was not saved. Retained model output is unverified; your last saved draft is unchanged.");
            }
            turn.put("status", "FAILED").put("message", message);
            if ("CORRECTING_VALIDATION_ERRORS".equals(turn.path("feedbackStopReason").asText())) turn.put("feedbackStopReason", "FAILED");
            if (receipt != null) turn.set("runtime", changes.project(receipt));
            session.status = "IDLE"; session.activeTurnId = null; session.deadline = null; write(session, content);
        }));
    }
    @Scheduled(fixedDelay = 60_000)
    public void cleanup() {
        if (!crypto.configured()) return;
        var expired = sessions.findByStatusAndDeadlineBefore("RUNNING", Instant.now());
        for (var session : expired) {
            var future = pending.remove(session.activeTurnId); if (future != null) future.cancel(true);
            fail(session.id, session.activeTurnId, "The configuration request timed out. No late result will be applied.");
        }
        tx.executeWithoutResult(ignored -> sessions.deleteByExpiresAtBefore(Instant.now()));
    }
    @PreDestroy public void close() { workers.shutdownNow(); }
    private AuthoringSessionEntity owned(String id, boolean lock) {
        var session = (lock ? sessions.lock(id) : sessions.findById(id)).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Assistant session not found."));
        if (!actor().equals(session.actorId) || !session.expiresAt.isAfter(Instant.now())) throw new ResponseStatusException(NOT_FOUND, "Assistant session not found.");
        return session;
    }
    private AuthoringResourceService.Snapshot resource(AuthoringSessionEntity session) {
        var resource = resources.load(session.resourceKind, session.resourceId, session.task);
        if (!session.customerId.equals(resource.customerId())) throw new ResponseStatusException(NOT_FOUND, "Assistant session not found.");
        return resource;
    }
    private void requireCurrent(AuthoringSessionEntity session, String turnId) {
        if (!"RUNNING".equals(session.status) || !turnId.equals(session.activeTurnId) || session.deadline == null || session.deadline.isBefore(Instant.now()))
            throw new ResponseStatusException(CONFLICT, "The assistant turn was cancelled, superseded or expired.");
    }
    private void requireIdle(AuthoringSessionEntity session) {
        if ("RUNNING".equals(session.status)) throw new ResponseStatusException(CONFLICT, "Finish or cancel the current assistant turn first.");
    }
    private JsonNode view(AuthoringSessionEntity session, AuthoringResourceService.Snapshot resource) {
        var content = content(session); var result = mapper.createObjectNode().put("id", session.id)
            .put("resourceKind", session.resourceKind).put("resourceId", session.resourceId).put("task", session.task)
            .put("displayName", resource.displayName()).put("status", session.status).put("activeTurnId", session.activeTurnId)
            .put("expiresAt", session.expiresAt.toString()).put("editable", resource.editable()).put("readOnlyReason", resource.readOnlyReason());
        var sourceViews = result.putArray("sources");
        for (var source : content.path("sources")) {
            ObjectNode item = source.deepCopy(); item.put("id", source.path("sourceId").asText());
            item.put("characters", source.path("content").asText().length()); item.remove("content"); sourceViews.add(item);
        }
        var turns = result.putArray("turns");
        for (var original : content.path("turns")) {
            ObjectNode turn = original.deepCopy(); turn.remove(List.of("requestKey", "commandHash", "sourceIds", "operationIds")); turns.add(turn);
        }
        return result;
    }
    private ObjectNode content(AuthoringSessionEntity session) {
        try { return (ObjectNode) mapper.readTree(crypto.decrypt(session.id, session.encryptedContent)); }
        catch (Exception failure) { throw new ResponseStatusException(SERVICE_UNAVAILABLE, "AUTHORING_SESSION_CONTENT_UNAVAILABLE"); }
    }
    private void write(AuthoringSessionEntity session, ObjectNode content) {
        String serialized = content.toString();
        if (serialized.length() > 1_000_000) throw bad("This assistant session is full. Delete an older source or start a new session.");
        session.encryptedContent = crypto.encrypt(session.id, serialized); session.updatedAt = Instant.now();
        session.expiresAt = session.updatedAt.plus(7, ChronoUnit.DAYS);
    }
    private ObjectNode findTurn(ObjectNode content, String id) {
        for (var turn : content.path("turns")) if (id.equals(turn.path("id").asText())) return (ObjectNode) turn;
        throw new ResponseStatusException(NOT_FOUND, "Assistant turn not found.");
    }
    private boolean contains(JsonNode list, String value) { for (var item : list) if (value.equals(item.asText())) return true; return false; }
    private String actor() { if (!PlatformSecurityContext.isAuthenticated()) throw new ResponseStatusException(UNAUTHORIZED, "Sign in first."); return PlatformSecurityContext.currentPrincipal().actorId(); }
    private void requireEnabled() { if (!status().path("ready").asBoolean()) throw new ResponseStatusException(SERVICE_UNAVAILABLE, "AUTHORING_NOT_CONFIGURED"); }
    private String digest(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception failure) { throw new IllegalStateException(failure); } }
    private ResponseStatusException bad(String message) { return new ResponseStatusException(BAD_REQUEST, message); }
    private record Claim(String sessionId, String turnId, JsonNode view) { }
    private record AttemptResult(boolean again, JsonNode findings, JsonNode output) { }
    public record CreateSession(String resourceKind, String resourceId, String task) { }
    public record SourceRequest(String title, String mediaType, String content, String base64, String url) { }
    public record ReuseSources(String sessionId, List<String> sourceIds) { }
    public record CancelRequest(String turnId) { }
    public record TurnRequest(String query, boolean previewOnly, List<String> sourceIds, List<String> operationIds,
                              AuthoringFeedbackPolicy validationFeedback, String command, AuthoringTaskIntent.EditorContext editorContext) {
        public TurnRequest(String query, boolean previewOnly, List<String> sourceIds, List<String> operationIds,
                           AuthoringFeedbackPolicy validationFeedback) {
            this(query, previewOnly, sourceIds, operationIds, validationFeedback, null, null);
        }
        public TurnRequest(String query, boolean previewOnly, List<String> sourceIds, List<String> operationIds) {
            this(query, previewOnly, sourceIds, operationIds, null, null, null);
        }
    }
}
