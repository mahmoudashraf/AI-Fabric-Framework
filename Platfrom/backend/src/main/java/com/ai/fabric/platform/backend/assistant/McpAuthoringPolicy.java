package com.ai.fabric.platform.backend.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.server.ResponseStatusException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.springframework.http.HttpStatus.FORBIDDEN;

/** Untrusted catalogue assistance cannot change imported execution or identity authority. */
final class McpAuthoringPolicy {
    private McpAuthoringPolicy() { }

    static void requirePreserved(JsonNode before, JsonNode candidate) {
        var existing = named(before.path("actionsConfig").path("actions"), "name");
        for (JsonNode action : candidate.path("actionsConfig").path("actions")) {
            JsonNode old = existing.get(action.path("name").asText());
            boolean managed = action.path("execution").path("mcp").hasNonNull("connectionId")
                || old != null && old.path("execution").path("mcp").hasNonNull("connectionId");
            if (!managed) continue;
            if (old == null) throw denied();
            for (String field : List.of("adapterType", "execution", "inputSchema", "outputSchema", "accessMode",
                "requiresConfirmation", "anonymousAllowed", "groundingEligible", "groundingVectorSpaces", "readActionResolutionEligible")) {
                if (!old.path(field).equals(action.path(field))) throw denied();
            }
            var nextParams = named(action.path("params"), "name");
            for (JsonNode parameter : old.path("params")) {
                if (!parameter.path("resolveFrom").isObject() || parameter.path("resolveFrom").isEmpty()) continue;
                JsonNode next = nextParams.get(parameter.path("name").asText());
                if (next == null || !parameter.path("resolveFrom").equals(next.path("resolveFrom"))) throw denied();
            }
        }
        var servers = named(before.path("actionsConfig").path("mcpServers"), "serverRef");
        for (JsonNode next : candidate.path("actionsConfig").path("mcpServers")) {
            JsonNode old = servers.get(next.path("serverRef").asText());
            if (next.hasNonNull("connectionId") || old != null && old.hasNonNull("connectionId")) {
                if (old == null || !old.equals(next)) throw denied();
            }
        }
    }

    private static Map<String, JsonNode> named(JsonNode array, String identity) {
        Map<String, JsonNode> result = new LinkedHashMap<>();
        for (JsonNode item : array) result.put(item.path(identity).asText(), item);
        return result;
    }
    private static ResponseStatusException denied() {
        return new ResponseStatusException(FORBIDDEN,
            "MCP assistance may refine descriptions and collection preferences. Change connection, schema or execution permissions through the owned connection/action controls.");
    }
}
