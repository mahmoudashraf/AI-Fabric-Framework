package com.ai.fabric.platform.backend.support.runtime;

import com.ai.fabric.platform.backend.support.SupportCaseService;
import com.ai.fabric.platform.backend.support.SupportContracts;
import com.ai.fabric.platform.backend.support.SupportContracts.CaseStatus;
import com.ai.fabric.platform.backend.support.SupportContracts.DeliveryStatus;
import com.ai.fabric.platform.backend.support.SupportRequesterService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.InputStream;
import java.time.Instant;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@Service
public class SupportWorkspaceService {
    private final SupportCaseService cases;
    private final SupportRequesterService requesters;
    private final SupportRuntimeAccessService access;
    private final SupportRuntimeClient runtime;
    private final ObjectMapper mapper;
    public SupportWorkspaceService(SupportCaseService cases, SupportRequesterService requesters,
        SupportRuntimeAccessService access, SupportRuntimeClient runtime, ObjectMapper mapper) {
        this.cases = cases; this.requesters = requesters; this.access = access; this.runtime = runtime; this.mapper = mapper;
    }

    public StartReceipt start(String caseId) {
        var original = cases.initialMessageForDelivery(caseId);
        var destination = access.resolve(caseId);
        try {
            var body = mapper.createObjectNode().put("query", original.body()).put("mode", "executor").put("position", "search");
            var result = runtime.exchange(destination, "POST", conversationPath(destination) + "/messages", body, original.idempotencyKey());
            observeTerminal(caseId, destination, result);
            String type = result.body().path("type").asText();
            DeliveryStatus observed = result.status() < 300 ? DeliveryStatus.COMPLETED
                : "AI_INTERACTION_IN_PROGRESS".equals(type) ? DeliveryStatus.USER_MESSAGE_PERSISTED : DeliveryStatus.FAILED;
            cases.observeInitialDelivery(caseId, original.episodeId(), observed);
            return new StartReceipt(cases.get(caseId), result.status(), result.body());
        } catch (ResponseStatusException failure) {
            cases.observeInitialDelivery(caseId, original.episodeId(), DeliveryStatus.UNKNOWN);
            return new StartReceipt(cases.get(caseId), failure.getStatusCode().value(),
                mapper.createObjectNode().put("type", "DELIVERY_UNCERTAIN").put("answer",
                    "Your request is saved. The first message has not yet been confirmed; check the conversation before retrying."));
        }
    }

    public SupportRuntimeClient.Result exchange(String caseId, String method, String route, JsonNode body, String key, long afterMessageSequence) {
        var destination = access.resolve(caseId);
        String path = route(destination, method, route);
        if (afterMessageSequence < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SUPPORT_CURSOR_INVALID");
        if ("GET".equals(method) && route.endsWith("/control")) path += "?afterMessageSequence=" + afterMessageSequence;
        if (!"GET".equals(method)) {
            requireOpen(destination);
            SupportRuntimeClient.messageKey(key);
        }
        if (route.endsWith("/messages") && "POST".equals(method)) {
            if (body == null || !body.isObject() || !body.path("query").isTextual()
                || body.path("query").asText().isBlank() || body.path("query").asText().length() > 32_000)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SUPPORT_MESSAGE_REQUIRED");
            ObjectNode safe = mapper.createObjectNode();
            for (String field : Set.of("query", "mode", "position", "attachments", "context", "actionDraftSubmission"))
                if (body.has(field)) safe.set(field, body.path(field));
            safe.put("conversationId", destination.owned().conversationId());
            body = safe;
        }
        if ("GET".equals(method) && path.equals(conversationPath(destination))
            && destination.owned().episodeNumber() > 1 && destination.owned().status() == CaseStatus.OPEN) {
            // Explicit case reopen registers a new ID; the supported control read initializes its empty canonical scope.
            var control = runtime.exchange(destination, "GET", path + "/control", null, null);
            observeTerminal(caseId, destination, control);
            if (control.status() >= 300) return control;
        }
        var result = runtime.exchange(destination, method, path, body, key);
        observeTerminal(caseId, destination, result);
        return result;
    }

    private void observeTerminal(String caseId, SupportRuntimeAccessService.Access destination, SupportRuntimeClient.Result result) {
        var control = result.body().path("metadata").path("conversationControl");
        boolean closedRead = result.status() < 300 && ("CLOSED".equals(result.body().path("controlState").asText())
            || "CLOSED".equals(result.body().path("conversation").path("controlState").asText()));
        boolean closedConflict = result.status() == 409 && "CLOSED".equals(control.path("controlState").asText())
            && destination.owned().conversationId().equals(control.path("conversationId").asText());
        if (closedRead || closedConflict) cases.observeConversationTerminal(caseId, destination.owned().conversationId());
    }

    public StreamingResponseBody events(String caseId, String conversationId, String lastEventId) {
        var destination = access.resolve(caseId);
        if (!destination.owned().conversationId().equals(conversationId))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "SUPPORT_CONVERSATION_NOT_FOUND");
        var owner = cases.requireConversationAccess(caseId, conversationId).supportCase().requester();
        var response = runtime.events(destination, conversationPath(destination) + "/events", lastEventId);
        return output -> {
            Instant until = Instant.now().plusSeconds(45);
            try (InputStream input = response.body()) {
                byte[] bytes = new byte[8192];
                int total = 0;
                while (Instant.now().isBefore(until)) {
                    int count = input.read(bytes);
                    if (count < 0 || (total += count) > 1_048_576) break;
                    requesters.requireActive(owner);
                    output.write(bytes, 0, count);
                    output.flush();
                }
            }
        };
    }

    static String route(SupportRuntimeAccessService.Access access, String method, String value) {
        String prefix = "chat/me/conversations/" + access.owned().conversationId();
        boolean read = "GET".equals(method) && (Set.of("chat/me/auth-context", "chat/me/shell-config", prefix,
            prefix + "/control").contains(value));
        boolean write = "POST".equals(method) && (Set.of(prefix + "/messages", prefix + "/handover-requests", prefix + "/ai-resume-requests").contains(value)
            || value.matches(java.util.regex.Pattern.quote(prefix) + "/handover-requests/[a-zA-Z0-9-]{1,80}/(cancel|retry)"));
        if (!read && !write) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "SUPPORT_RUNTIME_ROUTE_NOT_AVAILABLE");
        return "/api/" + value;
    }

    private static void requireOpen(SupportRuntimeAccessService.Access destination) {
        if (destination.owned().status() != CaseStatus.OPEN || destination.owned().conversationTerminal())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "SUPPORT_CASE_CLOSED");
    }
    private static String conversationPath(SupportRuntimeAccessService.Access destination) {
        return "/api/chat/me/conversations/" + destination.owned().conversationId();
    }
    public record StartReceipt(SupportContracts.CaseView supportCase, int runtimeStatus, JsonNode response) { }
}
