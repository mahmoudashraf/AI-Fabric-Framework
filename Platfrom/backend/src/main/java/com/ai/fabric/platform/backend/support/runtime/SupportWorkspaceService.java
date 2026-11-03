package com.ai.fabric.platform.backend.support.runtime;

import com.ai.fabric.platform.backend.support.SupportContracts.CaseStatus;
import com.ai.fabric.platform.backend.support.SupportTicketService;
import com.ai.fabric.platform.backend.support.SupportRequesterService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.time.Instant;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/** Host transport only. Runtime owns the transcript; the provider owns ticket status. */
@Service
public class SupportWorkspaceService {
    private final SupportTicketService tickets;
    private final SupportRequesterService requesters;
    private final SupportRuntimeAccessService access;
    private final SupportRuntimeClient runtime;
    private final ObjectMapper mapper;
    public SupportWorkspaceService(SupportTicketService tickets, SupportRequesterService requesters,
        SupportRuntimeAccessService access, SupportRuntimeClient runtime, ObjectMapper mapper) {
        this.tickets = tickets; this.requesters = requesters; this.access = access; this.runtime = runtime; this.mapper = mapper;
    }

    public JsonNode start(String id, SupportTicketService.Version version, String key) {
        var destination = access.resolve(id);
        return tickets.start(id, version, key, destination.headers());
    }

    public JsonNode conversationCommand(String id, String operation, SupportTicketService.Version version, String key) {
        return tickets.conversationCommand(id, operation, version, key, access.resolve(id).headers());
    }

    public SupportRuntimeClient.Result exchange(String id, String method, String route, JsonNode body, String key, long after) {
        var destination = access.resolve(id);
        var history = java.util.regex.Pattern.compile("chat/me/conversations/([A-Za-z0-9._:-]{1,128})(?:/control)?").matcher(route);
        if ("GET".equals(method) && history.matches()
            && !destination.owned().conversationId().equals(history.group(1))) {
            destination = access.resolveEpisode(id, history.group(1));
            if (destination == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "SUPPORT_CONVERSATION_NOT_FOUND");
        }
        return exchange(destination, method, route, body, key, after);
    }

    SupportRuntimeClient.Result exchange(SupportRuntimeAccessService.Access destination, String method, String route,
        JsonNode body, String key, long after) {
        String path = route(destination, method, route);
        boolean suggestions = "POST".equals(method) && "chat/me/suggestions".equals(route);
        if (after < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SUPPORT_CURSOR_INVALID");
        if ("GET".equals(method) && route.endsWith("/control")) path += "?afterMessageSequence=" + after;
        if (!"GET".equals(method)) {
            if (destination.owned().status() != CaseStatus.OPEN)
                throw new ResponseStatusException(HttpStatus.CONFLICT, "SUPPORT_REQUEST_CLOSED");
            if (!suggestions) SupportRuntimeClient.messageKey(key);
        }
        if (suggestions) {
            if (body == null || !body.isObject() || !body.path("content").isTextual()
                || body.path("content").asText().length() > 32000)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SUPPORT_SUGGESTION_CONTENT_REQUIRED");
            var safe = mapper.createObjectNode();
            for (String field : Set.of("content", "maxSuggestions", "attachments", "context"))
                if (body.has(field)) safe.set(field, body.path(field));
            body = safe;
        }
        if (route.endsWith("/messages") && "POST".equals(method)) {
            if (body == null || !body.isObject() || !body.path("query").isTextual()
                || body.path("query").asText().isBlank() || body.path("query").asText().length() > 32000)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SUPPORT_MESSAGE_REQUIRED");
            var safe = mapper.createObjectNode();
            for (String field : Set.of("query", "mode", "position", "attachments", "context", "actionDraftSubmission"))
                if (body.has(field)) safe.set(field, body.path(field));
            safe.put("conversationId", destination.owned().conversationId()); body = safe;
        }
        if ("GET".equals(method) && path.equals(conversationPath(destination))
            && (destination.owned().caseId() == null || destination.owned().episodeNumber() > 1)) {
            // The canonical control read opens an authorized empty scope without inventing a user message.
            var control = runtime.exchange(destination, "GET", path + "/control", null, null);
            if (control.status() >= 300) return control;
        }
        return runtime.exchange(destination, method, path, body, key);
    }

    public StreamingResponseBody events(String id, String conversationId, String lastEventId) {
        var destination = access.resolve(id);
        var owner = tickets.context(id, false).requester();
        return events(destination, owner, conversationId, lastEventId);
    }

    StreamingResponseBody events(SupportRuntimeAccessService.Access destination,
        com.ai.fabric.platform.backend.support.SupportContracts.Requester owner, String conversationId, String lastEventId) {
        if (!destination.owned().conversationId().equals(conversationId))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "SUPPORT_CONVERSATION_NOT_FOUND");
        var response = runtime.events(destination, conversationPath(destination) + "/events", lastEventId);
        return output -> {
            Instant until = Instant.now().plusSeconds(45);
            try (InputStream input = response.body()) {
                byte[] bytes = new byte[8192]; int total = 0;
                while (Instant.now().isBefore(until)) {
                    int count = input.read(bytes);
                    if (count < 0 || (total += count) > 1048576) break;
                    requesters.requireActive(owner); output.write(bytes, 0, count); output.flush();
                }
            }
        };
    }

    static String route(SupportRuntimeAccessService.Access access, String method, String value) {
        String prefix = "chat/me/conversations/" + access.owned().conversationId();
        boolean read = "GET".equals(method) && Set.of("chat/me/auth-context", "chat/me/shell-config", prefix, prefix + "/control").contains(value);
        boolean write = "POST".equals(method) && (Set.of("chat/me/suggestions", prefix + "/messages", prefix + "/handover-requests", prefix + "/ai-resume-requests").contains(value)
            || value.matches(java.util.regex.Pattern.quote(prefix) + "/handover-requests/[a-zA-Z0-9-]{1,80}/(cancel|retry)"));
        if (!read && !write) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "SUPPORT_RUNTIME_ROUTE_NOT_AVAILABLE");
        return "/api/" + value;
    }
    private static String conversationPath(SupportRuntimeAccessService.Access destination) {
        return "/api/chat/me/conversations/" + destination.owned().conversationId();
    }
}
