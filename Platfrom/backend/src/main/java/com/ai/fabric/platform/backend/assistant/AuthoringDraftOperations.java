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
                || maxWrites < 0 || maxWrites > 64) throw new IllegalArgumentException("Invalid authoring work binding");
            allowedActions = List.copyOf(allowedActions);
        }
    }
    /** Created only after service authentication; neither a browser DTO nor an LLM argument. */
    public record Caller(String sessionId, String turnId, String deploymentId, String releaseId, String action,
                         boolean selectedSourceReadAllowed, String goalId, long requestRevision, long cancellationEpoch, int segment) {
        public Caller(String sessionId, String turnId, String deploymentId, String releaseId, String action, boolean selectedSourceReadAllowed) {
            this(sessionId, turnId, deploymentId, releaseId, action, selectedSourceReadAllowed, null, 0, 0, 0);
        }
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
    private final AuthoringGoalService goals;
    private final AuthoringOperationRepository durableOperations;

    public AuthoringDraftOperations(AuthoringSessionRepository sessions, AuthoringCrypto crypto,
        AuthoringResourceService resources, AuthoringChanges changes, PlatformUserRepository users,
        ObjectMapper mapper, PlatformAuditService audit, AuthoringDraftLock draftLock, AuthoringGoalService goals,
        AuthoringOperationRepository durableOperations) {
        this.sessions = sessions; this.crypto = crypto; this.resources = resources;
        this.changes = changes; this.users = users; this.mapper = mapper; this.audit = audit; this.draftLock = draftLock;
        this.goals = goals; this.durableOperations = durableOperations;
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
        return context(caller, null);
    }
    @Transactional
    public JsonNode context(Caller caller, ContextRead read) {
        return authorized(caller, false, work -> {
            var snapshot = work.snapshot();
            var result = mapper.createObjectNode();
            result.put("resourceKind", work.session().resourceKind).put("task", work.session().task)
                .put("editable", snapshot.editable()).put("readOnlyReason", snapshot.readOnlyReason())
                .put("fingerprint", fingerprint(snapshot.configuration()));
            var references = snapshot.catalogue().path("secretReferences");
            var configuration = changes.project(snapshot.configuration(), references);
            if (read != null && (read.actionNames() != null && read.actionNames().size() > 20
                || read.routeNames() != null && read.routeNames().size() > 20))
                throw new AuthoringContractException("DEFINITION_READ_BOUNDS_INVALID", "Select at most 20 actions or routes per read.");
            boolean definitionSelection = read != null && (read.actionNames() != null && !read.actionNames().isEmpty()
                || read.routeNames() != null && !read.routeNames().isEmpty());
            boolean actionTask = List.of("ACTIONS", "CONNECTIONS", "BRAIN_SETUP").contains(work.session().task);
            if (definitionSelection && !actionTask) result.put("configurationSelection", "TASK_CONTEXT_NOT_ACTION_DEFINITIONS");
            if (definitionSelection && actionTask) {
                var selected = mapper.createObjectNode();
                if (read.actionNames() != null) {
                    var definitions = selected.putObject("actionsConfig").putArray("actions");
                    for (String name : read.actionNames()) {
                        JsonNode found = null;
                        for (var action : configuration.at("/actionsConfig/actions")) if (name.equals(action.path("name").asText())) found = action;
                        if (found != null) definitions.add(found);
                    }
                }
                if (read.routeNames() != null) {
                    var routes = selected.putObject("routingConfig").putObject("actions");
                    for (String name : read.routeNames()) if (configuration.at("/routingConfig/actions").has(name)) routes.set(name, configuration.at("/routingConfig/actions").get(name));
                }
                configuration = selected;
                result.put("configurationSelection", "SELECTED_DEFINITIONS");
            }
            var contracts = snapshot.catalogue().deepCopy();
            if (List.of("ACTIONS", "CONNECTIONS").contains(work.session().task)) {
                ((ObjectNode) configuration).retain("actionsConfig", "routingConfig");
                result.put("configurationScope", "Only action and endpoint settings are shown. Unshown settings are unchanged, not absent.");
                ((ObjectNode) contracts).remove(List.of("behaviorTypes", "llmProviders", "embeddingProviders", "aiBehaviourProfiles",
                    "aiBehaviourConfigurationExample", "behaviorContract", "humanSupportContract", "specialistContract"));
            } else if ("KNOWLEDGE".equals(work.session().task)) {
                ((ObjectNode) configuration).retain("entityConfig", "knowledgeSourceConfig", "marketplaceDatasetConfig");
                result.put("configurationScope", "Only complete knowledge, entity and dataset settings are shown. Unshown settings are unchanged, not absent; normal candidate validation still checks the full draft and its dependencies.");
                ((ObjectNode) contracts).remove(List.of("behaviorTypes", "llmProviders", "embeddingProviders", "aiBehaviourProfiles",
                    "aiBehaviourConfigurationExample", "actionContract", "behaviorContract", "humanSupportContract", "specialistContract"));
                if (contracts.path("collectionShapes").isObject()) ((ObjectNode) contracts.path("collectionShapes"))
                    .retain("/knowledgeSourceConfig/sources", "/marketplaceDatasetConfig/datasets");
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
            if (work.work().at("/lastCheck/sourceCoverage").isObject()) result.set("lastCandidateCoverage", work.work().at("/lastCheck/sourceCoverage"));
            result.set("sourceCoverage", AuthoringSourceCoverage.inspect(mapper, work.content(), work.turn(), snapshot.configuration(), work.session().task, references));
            if (work.goal() != null) result.set("goal", stableGoalContext(goals.view(work.session().id), result.path("sourceCoverage")));
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
            return changes.project(result, references);
        });
    }

    /** Tool facts exclude host liveness, so a renewed lease is not semantic progress. */
    private ObjectNode stableGoalContext(JsonNode view, JsonNode coverage) {
        var result = mapper.createObjectNode();
        for (String field : List.of("id", "workId", "executionMode", "originalRequest", "requestRevision", "resourceKind", "resourceId", "task",
            "sourceIds", "operationIds", "profileId", "releaseId", "bundleHash", "operationCount"))
            if (view.has(field)) result.set(field, view.get(field));
        if (view.path("latestRequestUpdate").isObject()) {
            var update = result.putObject("latestRequestUpdate");
            for (String field : List.of("query", "requestRevision")) update.set(field, view.path("latestRequestUpdate").path(field));
        }
        var receipts = result.putArray("operations");
        for (var item : view.path("operations")) {
            ObjectNode receipt = item.deepCopy(); receipt.remove("savedAt");
            if (receipt.path("receipt").isObject()) ((ObjectNode) receipt.path("receipt")).remove("savedAt");
            receipts.add(receipt);
        }
        result.set("sourceCoverage", coverage);
        return result;
    }

    @Transactional
    public JsonNode selectedSources(Caller caller) {
        return authorized(caller, false, this::selectedSourceData);
    }
    @Transactional
    public JsonNode selectedSources(Caller caller, SourceRead request) {
        return authorized(caller, false, work -> {
            int offset = request.offset() == null ? 0 : request.offset();
            int limit = request.limit() == null ? 10 : request.limit();
            int characters = request.maxCharacters() == null ? 16000 : request.maxCharacters();
            if (offset < 0 || limit < 1 || limit > 50 || characters < 256 || characters > 24000
                || request.contentOffset() != null && request.contentOffset() < 0)
                throw new AuthoringContractException("SOURCE_READ_BOUNDS_INVALID", "Use a nonnegative offset, 1-50 items and 256-24000 characters.");
            var selected = selectedSourceData(work);
            var known = new java.util.HashSet<String>();
            for (var source : selected) if (request.sourceId() == null || request.sourceId().equals(source.path("id").asText()))
                for (var operation : source.path("operations")) known.add(operation.path("id").asText());
            if (request.operationIds() != null && !known.containsAll(request.operationIds())) throw denied();
            var entries = mapper.createArrayNode(); boolean sourceFound = request.sourceId() == null;
            var result = mapper.createObjectNode().put("offset", offset).put("limit", limit);
            for (var source : selected) {
                if (request.sourceId() != null && !request.sourceId().equals(source.path("id").asText())) continue;
                sourceFound = true;
                for (var operation : source.path("operations")) {
                    if (request.operationIds() != null && !request.operationIds().isEmpty() && !request.operationIds().contains(operation.path("id").asText())) continue;
                    var entry = entries.addObject().put("sourceId", source.path("id").asText()).put("sourceVersion", source.path("sourceVersion").asText())
                        .put("operationId", operation.path("id").asText()).put("title", source.path("title").asText());
                    entry.set("operation", operation);
                }
                if (source.path("operations").isEmpty()) entries.addObject().put("sourceId", source.path("id").asText())
                    .put("sourceVersion", source.path("sourceVersion").asText()).put("title", source.path("title").asText());
                if (request.contentOffset() != null && request.sourceId() != null) {
                    String text = source.has("content") ? source.path("content").asText() : source.path("operations").toString();
                    if (request.operationIds() != null && !request.operationIds().isEmpty()) {
                        var requested = mapper.createArrayNode();
                        for (var operation : source.path("operations")) if (request.operationIds().contains(operation.path("id").asText())) requested.add(operation);
                        text = requested.toString();
                    }
                    int start = Math.min(text.length(), request.contentOffset()); int end = Math.min(text.length(), start + characters);
                    if (end < text.length() && end > start && Character.isHighSurrogate(text.charAt(end - 1))) end--;
                    var range = result.putObject("content").put("sourceId", source.path("id").asText()).put("offset", start)
                        .put("text", text.substring(start, end)).put("totalCharacters", text.length()).put("truncated", end < text.length());
                    if (end < text.length()) range.put("nextOffset", end);
                }
            }
            if (!sourceFound) throw denied();
            var items = result.putArray("items"); int next = Math.min(offset, entries.size()); int used = 0;
            while (next < entries.size() && items.size() < limit) {
                ObjectNode entry = entries.get(next).deepCopy();
                if (request.summariesOnly() && entry.has("operation")) {
                    ObjectNode operation = (ObjectNode) entry.get("operation");
                    operation.retain("id", "kind", "method", "path", "servers", "toolName", "summary");
                }
                int size = entry.toString().length();
                if (size + used > characters && !items.isEmpty()) break;
                if (size > characters) { entry.remove("operation"); entry.put("truncated", true).put("operationCharacters", size); }
                items.add(entry); used += entry.toString().length(); next++;
            }
            result.put("total", entries.size()).put("truncated", next < entries.size());
            if (next < entries.size()) result.put("nextOffset", next);
            return changes.project(result);
        });
    }
    private JsonNode selectedSourceData(Work work) {
        var result = mapper.createArrayNode();
        for (var source : work.content().path("sources")) {
            if (!contains(work.turn().path("sourceIds"), source.path("sourceId").asText())) continue;
            var item = mapper.createObjectNode().put("id", source.path("sourceId").asText())
                .put("title", source.path("title").asText()).put("sourceVersion", AuthoringSourceVersion.of(source));
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
            var references = work.snapshot().catalogue().path("secretReferences");
            var candidate = changes.apply(work.snapshot().configuration(), requestedChanges, work.snapshot().allowedPaths(), references);
            var result = mapper.createObjectNode();
            result.set("findings", resources.check(work.session().resourceKind, work.session().resourceId,
                work.snapshot(), candidate.candidate()));
            result.put("requiresCorrection", AuthoringFeedbackPolicy.requiresCorrection(result.path("findings"),
                work.work().get("baselineFindings"), work.session().task, work.snapshot().configuration(), candidate.candidate()));
            result.set("diff", candidate.diff());
            result.set("sourceCoverage", AuthoringSourceCoverage.inspect(mapper, work.content(), work.turn(), candidate.candidate(), work.session().task, references)
                .put("evidence", "CANDIDATE_NOT_SAVED"));
            result.put("fingerprint", fingerprint(work.snapshot().configuration()));
            result.put("status", "CHECKED_NOT_SAVED").put("saved", false);
            result.put("hasChanges", !work.snapshot().configuration().equals(candidate.candidate()));
            result.put("draftSaveRequested", "PREPARE".equals(work.turn().path("command").asText())
                && !work.turn().path("previewOnly").asBoolean() && !work.work().at("/binding/previewOnly").asBoolean()
                && work.snapshot().editable() && contains(work.work().at("/binding/allowedActions"), "authoring_update_draft"));
            result.put("mutationFingerprint", fingerprint(mapper.createObjectNode()
                .put("action", "authoring_update_draft").set("input", requestedChanges)));
            var checked = work.work().putObject("lastCheck");
            checked.set("changes", changes.project(requestedChanges, references));
            checked.set("diff", result.path("diff")); checked.set("findings", result.path("findings"));
            checked.set("sourceCoverage", result.path("sourceCoverage"));
            store(work.session(), work.content());
            return changes.project(result, references);
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
        return authorized(caller, true, work -> {
            if (work.goal() == null) return changes.project(receipts(work, 0, 100));
            var result = mapper.createObjectNode(); result.set("operations", receipts(work, 0, 100));
            result.set("sourceCoverage", AuthoringSourceCoverage.inspect(mapper, work.content(), work.turn(), work.snapshot().configuration(), work.session().task, work.snapshot().catalogue().path("secretReferences")));
            if (work.work().at("/lastCheck/sourceCoverage").isObject()) result.set("lastCandidateCoverage", work.work().at("/lastCheck/sourceCoverage"));
            long total = durableOperations.countByGoalId(work.goal().id);
            result.put("total", total).put("offset", 0).put("truncated", total > 100);
            if (total > 100) result.put("nextOffset", 100);
            return changes.project(result);
        });
    }
    @Transactional
    public JsonNode status(Caller caller, ReceiptRead request) {
        return authorized(caller, true, work -> {
            int offset = request.offset() == null ? 0 : request.offset(); int limit = request.limit() == null ? 20 : request.limit();
            if (offset < 0 || limit < 1 || limit > 100 || offset % limit != 0)
                throw new AuthoringContractException("RECEIPT_READ_BOUNDS_INVALID", "Use a nonnegative page offset and 1-100 receipts.");
            var result = mapper.createObjectNode(); var items = result.putArray("operations");
            result.set("sourceCoverage", AuthoringSourceCoverage.inspect(mapper, work.content(), work.turn(), work.snapshot().configuration(), work.session().task, work.snapshot().catalogue().path("secretReferences")));
            if (work.work().at("/lastCheck/sourceCoverage").isObject()) result.set("lastCandidateCoverage", work.work().at("/lastCheck/sourceCoverage"));
            if (request.operationId() != null) {
                if (work.goal() != null) durableOperations.findByGoalIdAndOperationId(work.goal().id, request.operationId()).ifPresent(o -> items.add(receipt(o)));
                else for (var receipt : receipts(work, 0, 100)) if (request.operationId().equals(receipt.path("operationId").asText())) items.add(receipt);
                result.put("total", items.size());
            } else {
                receipts(work, offset, limit).forEach(items::add);
                long count = work.goal() == null ? work.work().path("operations").size() : durableOperations.countByGoalId(work.goal().id);
                result.put("total", count).put("offset", offset).put("truncated", count > offset + items.size());
                if (count > offset + items.size()) result.put("nextOffset", offset + items.size());
            }
            return changes.project(result);
        });
    }

    private JsonNode mutate(Caller caller, String operationId, String action, JsonNode payload, String undoId) {
        if (operationId == null || operationId.length() > 200) throw denied();
        String parameterHash = fingerprint(mapper.createObjectNode().put("action", action).set("input", payload));
        return authorized(caller, false, work -> {
            String namespace = work.goal() == null ? caller.turnId() : work.goal().workId;
            if (namespace == null || !operationId.startsWith(namespace + ":step:")) throw denied();
            var operations = receipts(work, 0, 100);
            var durable = work.goal() == null ? java.util.Optional.<AuthoringOperationEntity>empty()
                : durableOperations.findByGoalIdAndOperationId(work.goal().id, operationId);
            if (durable.isPresent()) {
                if (!parameterHash.equals(durable.get().parameterFingerprint)) throw new ResponseStatusException(CONFLICT, "AUTHORING_OPERATION_REPLAY_CONFLICT");
                return changes.project(receipt(durable.get()), work.snapshot().catalogue().path("secretReferences"));
            }
            for (var operation : operations) {
                if (!operationId.equals(operation.path("operationId").asText())) continue;
                if (!parameterHash.equals(operation.path("parameterFingerprint").asText()))
                    throw new ResponseStatusException(CONFLICT, "AUTHORING_OPERATION_REPLAY_CONFLICT");
                return changes.project(operation, work.snapshot().catalogue().path("secretReferences"));
            }
            var binding = work.work().path("binding");
            if (!contains(binding.path("allowedActions"), action)) throw denied();
            if (binding.path("previewOnly").asBoolean() || work.turn().path("previewOnly").asBoolean() || !work.snapshot().editable())
                throw new ResponseStatusException(CONFLICT, "AUTHORING_WORK_READ_ONLY");
            long writeCount = work.goal() == null ? operations.size() : durableOperations.countByGoalIdAndSegment(work.goal().id, work.goal().segment);
            if (writeCount >= binding.path("maxWrites").asInt())
                throw new ResponseStatusException(TOO_MANY_REQUESTS, "AUTHORING_WRITE_LIMIT");
            AuthoringResourceService.Saved saved;
            if (undoId == null) {
                saved = resources.save(work.session().resourceKind, work.session().resourceId, work.session().task, payload);
            } else {
                JsonNode original = null;
                if (work.goal() != null) original = durableOperations.findByGoalIdAndOperationId(work.goal().id, undoId).map(this::receipt).orElse(null);
                else for (var previous : operations) if (undoId.equals(previous.path("operationId").asText())) original = previous;
                if (original == null || !"authoring_update_draft".equals(original.path("action").asText())) throw denied();
                if (work.goal() != null && durableOperations.existsByGoalIdAndUndoes(work.goal().id, undoId))
                    throw new ResponseStatusException(CONFLICT, "AUTHORING_OPERATION_ALREADY_UNDONE");
                for (var previous : operations) if (undoId.equals(previous.path("undoes").asText()))
                    throw new ResponseStatusException(CONFLICT, "AUTHORING_OPERATION_ALREADY_UNDONE");
                saved = resources.undo(work.session().resourceKind, work.session().resourceId, work.session().task, original.path("diff"));
            }
            var operation = mapper.createObjectNode().put("operationId", operationId).put("action", action)
                .put("parameterFingerprint", parameterHash).put("status", "EXECUTED")
                .put("savedAt", Instant.now().toString()).put("published", false);
            if (undoId != null) operation.put("undoes", undoId);
            operation.set("receipt", saved.receipt()); operation.set("diff", saved.diff()); operation.set("findings", saved.findings());
            operation.put("resourceFingerprint", fingerprint(saved.configuration()));
            // The session lock, normal save and encrypted receipt share this transaction.
            if (work.goal() == null) { work.work().withArray("operations").add(operation); store(work.session(), work.content()); }
            else {
                var record = new AuthoringOperationEntity(); record.id = "cao-" + java.util.UUID.randomUUID(); record.goalId = work.goal().id;
                record.operationId = operationId; record.parameterFingerprint = parameterHash; record.segment = work.goal().segment;
                record.undoes = undoId;
                record.createdAt = Instant.now(); record.encryptedReceipt = crypto.encrypt(record.id, operation.toString()); durableOperations.save(record);
            }
            audit.record("AUTHORING_WORK_DRAFT_SAVED", "AUTHORING_SESSION", work.session().id,
                Map.of("customerId", work.session().customerId, "turnId", caller.turnId(), "operationId", operationId, "action", action));
            return changes.project(operation, work.snapshot().catalogue().path("secretReferences"));
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
        var goal = goals.requireTool(session, work, caller, statusOnly);
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
            ObjectNode effectiveTurn = turn;
            if (goal != null) {
                effectiveTurn = turn.deepCopy(); var directive = goals.currentDirective(goal);
                for (String field : List.of("sourceIds", "operationIds")) if (directive.hasNonNull(field)) effectiveTurn.set(field, directive.get(field));
            }
            return operation.apply(new Work(session, content, effectiveTurn, (ObjectNode) work, snapshot, goal));
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
    private com.fasterxml.jackson.databind.node.ArrayNode receipts(Work work, int offset, int limit) {
        if (work.goal() == null) return work.work().withArray("operations");
        var result = mapper.createArrayNode();
        for (var item : durableOperations.findByGoalIdOrderByCreatedAtAscIdAsc(work.goal().id,
            org.springframework.data.domain.PageRequest.of(offset / limit, limit))) result.add(receipt(item));
        return result;
    }
    private ObjectNode receipt(AuthoringOperationEntity operation) {
        try { return (ObjectNode) mapper.readTree(crypto.decrypt(operation.id, operation.encryptedReceipt)); }
        catch (Exception invalid) { throw new ResponseStatusException(SERVICE_UNAVAILABLE, "AUTHORING_RECEIPT_UNAVAILABLE"); }
    }
    private record Work(AuthoringSessionEntity session, ObjectNode content, ObjectNode turn, ObjectNode work,
                        AuthoringResourceService.Snapshot snapshot, AuthoringGoalEntity goal) { }
    public record SourceRead(String sourceId, List<String> operationIds, Integer offset, Integer limit,
        Integer maxCharacters, Integer contentOffset, boolean summariesOnly) { }
    public record ContextRead(List<String> actionNames, List<String> routeNames) { }
    public record ReceiptRead(String operationId, Integer offset, Integer limit) { }
}
