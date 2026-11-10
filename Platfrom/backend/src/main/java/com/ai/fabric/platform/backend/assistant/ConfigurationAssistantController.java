package com.ai.fabric.platform.backend.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/configuration-assistant")
@PreAuthorize("hasAnyRole('CUSTOMER_ADMIN','PLATFORM_ADMIN','PLATFORM_OPERATOR')")
public class ConfigurationAssistantController {
    private final ConfigurationAssistantService service;
    public ConfigurationAssistantController(ConfigurationAssistantService service) { this.service = service; }
    @ModelAttribute void noCache(jakarta.servlet.http.HttpServletResponse response) { response.setHeader("Cache-Control", "no-store"); }
    @GetMapping("/status") public JsonNode status() { return service.status(); }
    @PostMapping("/sessions") public JsonNode create(@RequestBody ConfigurationAssistantService.CreateSession request) { return service.create(request); }
    @GetMapping("/sessions/{id}") public JsonNode get(@PathVariable String id) { return service.get(id); }
    @GetMapping("/sessions/{id}/sources/{sourceId}/content") public JsonNode sourceContent(@PathVariable String id, @PathVariable String sourceId) { return service.sourceContent(id, sourceId); }
    @GetMapping("/sessions/{id}/reusable-sources") public JsonNode reusableSources(@PathVariable String id) { return service.reusableSources(id); }
    @PostMapping("/sessions/{id}/sources/reuse") public JsonNode reuseSources(@PathVariable String id, @RequestBody ConfigurationAssistantService.ReuseSources request) { return service.reuseSources(id, request); }
    @PostMapping("/sessions/{id}/sources") public JsonNode source(@PathVariable String id, @RequestBody ConfigurationAssistantService.SourceRequest request) { return service.addSource(id, request); }
    @DeleteMapping("/sessions/{id}/sources/{sourceId}") public JsonNode removeSource(@PathVariable String id, @PathVariable String sourceId) { return service.removeSource(id, sourceId); }
    @PostMapping("/sessions/{id}/turns") public JsonNode turn(@PathVariable String id, @RequestBody ConfigurationAssistantService.TurnRequest request,
        @RequestHeader("Idempotency-Key") String key) { return service.turn(id, request, key); }
    @GetMapping("/sessions/{id}/turns/{turnId}") public JsonNode turn(@PathVariable String id, @PathVariable String turnId) { return service.get(id); }
    @PostMapping("/sessions/{id}/cancel") public JsonNode cancel(@PathVariable String id,
        @RequestBody(required = false) ConfigurationAssistantService.CancelRequest request) { return service.cancel(id, request == null ? null : request.turnId()); }
    @PostMapping("/sessions/{id}/proposals/{turnId}/check") public JsonNode check(@PathVariable String id, @PathVariable String turnId) { return service.proposal(id, turnId, "check"); }
    @PostMapping("/sessions/{id}/proposals/{turnId}/apply") public JsonNode apply(@PathVariable String id, @PathVariable String turnId) { return service.proposal(id, turnId, "apply"); }
    @PostMapping("/sessions/{id}/proposals/{turnId}/undo") public JsonNode undo(@PathVariable String id, @PathVariable String turnId) { return service.proposal(id, turnId, "undo"); }
    @DeleteMapping("/sessions/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable String id) { service.delete(id); }
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> invalidImport(IllegalArgumentException invalid) {
        return ResponseEntity.badRequest().body(Map.of("message", invalid.getMessage() == null ? "Invalid configuration source." : invalid.getMessage()));
    }
}
