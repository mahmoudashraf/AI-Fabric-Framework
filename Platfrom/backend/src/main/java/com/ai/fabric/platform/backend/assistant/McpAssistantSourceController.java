package com.ai.fabric.platform.backend.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

@RestController
@RequestMapping("/api/configuration-assistant/sessions/{id}/sources/mcp")
@PreAuthorize("hasAnyRole('CUSTOMER_ADMIN','PLATFORM_ADMIN','PLATFORM_OPERATOR')")
public class McpAssistantSourceController {
    private final ConfigurationAssistantService assistant;
    private final McpAssistantCatalogueResolver catalogues;

    public McpAssistantSourceController(ConfigurationAssistantService assistant, McpAssistantCatalogueResolver catalogues) {
        this.assistant = assistant; this.catalogues = catalogues;
    }

    @PostMapping
    public JsonNode attach(@PathVariable String id, @RequestBody Source request, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        var session = assistant.get(id);
        if (!"DEPLOYMENT_DRAFT".equals(session.path("resourceKind").asText())
            || !List.of("ACTIONS", "CONNECTIONS").contains(session.path("task").asText())
            || request == null || request.toolNames() == null || request.toolNames().isEmpty()
            || request.toolNames().size() > 500) throw new ResponseStatusException(BAD_REQUEST, "Select a bounded MCP tool catalogue for this Brain's action assistant.");
        JsonNode catalogue = catalogues.catalogue(session.path("resourceId").asText(), request.connectionId(), request.snapshotId(), request.toolNames());
        return assistant.addSource(id, new ConfigurationAssistantService.SourceRequest("Selected MCP capabilities",
            "application/vnd.loomai.mcp+json", catalogue.toString(), null, null));
    }

    public record Source(String connectionId, String snapshotId, List<String> toolNames) { }
}
