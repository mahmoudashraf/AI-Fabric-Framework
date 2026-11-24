package com.ai.fabric.platform.backend.assistant;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;

/** Metadata-only exchange evidence. Never projects arbitrary provider or model output. */
final class AuthoringProviderDiagnostics {
    static final int MAX_OBSERVATIONS = 32;
    private AuthoringProviderDiagnostics() { }
    static ObjectNode project(ObjectMapper mapper, JsonNode diagnostics) {
        var result = mapper.createObjectNode(); var observations = result.putArray("observations");
        var values = diagnostics.path("observations"); int start = Math.max(0, values.size() - MAX_OBSERVATIONS);
        for (int i = start; values.isArray() && i < values.size(); i++) {
            var value = values.get(i); var item = observations.addObject();
            for (String field : List.of("operationId", "attemptId", "providerRequestId", "operation", "model", "responseModel", "finishReason")) {
                var scalar = value.path(field);
                if (scalar.isTextual() && scalar.asText().length() <= 256) item.set(field, scalar);
                else if (scalar.isNull()) item.putNull(field);
            }
            for (String field : List.of("effectiveOutputCap", "inputTokens", "outputTokens", "reasoningTokens", "contentLength")) {
                var scalar = value.path(field);
                if (scalar.isIntegralNumber() && scalar.canConvertToLong() && scalar.asLong() >= 0) item.set(field, scalar);
                else if (scalar.isNull()) item.putNull(field);
            }
            if (value.path("contentEmpty").isBoolean() || value.path("contentEmpty").isNull()) item.set("contentEmpty", value.get("contentEmpty"));
        }
        long dropped = Math.max(0, diagnostics.path("droppedObservations").asLong());
        result.put("droppedObservations", dropped > Long.MAX_VALUE - start ? Long.MAX_VALUE : dropped + start);
        return result;
    }
}
