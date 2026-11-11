package com.ai.fabric.platform.backend.mcp.runtime;

import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/mcp/runtime/{deploymentId}/{targetId}/actions")
public class McpRuntimeController {
    private static final int MAX_REQUEST_BYTES = 262_144;
    private final McpRuntimeExecutionService execution;
    private final ObjectMapper mapper;

    public McpRuntimeController(McpRuntimeExecutionService execution, ObjectMapper mapper) {
        this.execution = execution; this.mapper = mapper.copy();
        this.mapper.getFactory().setStreamReadConstraints(StreamReadConstraints.builder()
            .maxNestingDepth(64).maxStringLength(MAX_REQUEST_BYTES).build());
        this.mapper.enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        this.mapper.enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        this.mapper.getFactory().disable(com.fasterxml.jackson.core.JsonParser.Feature.INCLUDE_SOURCE_IN_LOCATION);
    }

    @PostMapping("/execute")
    public ResponseEntity<?> execute(@PathVariable String deploymentId, @PathVariable String targetId,
        @RequestHeader(value = "X-LoomAI-MCP-Runtime-Key", required = false) String key, HttpServletRequest request) throws IOException {
        if (key == null || key.isBlank() || key.length() > 512) return response(403, "MCP_ACCESS_DENIED", "An authenticated runtime target is required.");
        byte[] bytes = request.getInputStream().readNBytes(MAX_REQUEST_BYTES + 1);
        if (bytes.length > MAX_REQUEST_BYTES) return response(413, "MCP_REQUEST_TOO_LARGE", "The MCP action request exceeds its configured limit.");
        try {
            var parsed = mapper.readTree(bytes);
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(execution.execute(deploymentId, targetId, key, parsed));
        } catch (com.fasterxml.jackson.core.JsonProcessingException malformed) {
            return response(400, "MCP_INVALID_REQUEST", "A bounded JSON action request is required.");
        } catch (ResponseStatusException failure) {
            int status = failure.getStatusCode().value();
            return response(status, status == 401 || status == 403 ? "MCP_ACCESS_DENIED" : "MCP_CONNECTION_UNAVAILABLE",
                status == 401 || status == 403 ? "The runtime or customer is not authorized for this action."
                    : "The integration is unavailable. Ask the business administrator to check its connection and applied configuration.");
        }
    }

    private ResponseEntity<?> response(int status, String code, String message) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore())
            .body(Map.of("success", false, "errorCode", code, "message", message));
    }
}
