package com.ai.fabric.platform.backend.aiworkspace.web;

import com.ai.fabric.platform.backend.aiworkspace.service.AIWorkspaceDomainRouteService;
import com.ai.fabric.platform.backend.aiworkspace.service.AIWorkspaceRouteStore;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@RestController
@RequestMapping("/api/platform/ai-workspaces/routes")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class AIWorkspaceRouteController {
    private final AIWorkspaceDomainRouteService service;
    private final ObjectMapper reader = new ObjectMapper(JsonFactory.builder()
        .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build());
    private static final Set<String> FIELDS = Set.of("operationKey", "expectedGeneration", "expectedFingerprint",
        "expectedInflightFingerprint", "deploymentUuid", "operatorEvidenceRef");

    public AIWorkspaceRouteController(AIWorkspaceDomainRouteService service) { this.service = service; }

    @GetMapping public List<AIWorkspaceRouteStore.Status> status() { return service.statuses(); }

    @PostMapping(path = "/{routeKey}/observe-deployment", consumes = "application/json")
    public List<AIWorkspaceRouteStore.Status> observe(@PathVariable String routeKey, @RequestBody String body) {
        AIWorkspaceRouteStore.ObserveDeployment command;
        try (var parser = reader.createParser(body)) {
            JsonNode node = reader.readTree(parser);
            if (node == null || !node.isObject() || parser.nextToken() != null) throw new IllegalArgumentException();
            Set<String> fields = new HashSet<>(); node.fieldNames().forEachRemaining(fields::add);
            if (!fields.equals(FIELDS) || !node.path("expectedGeneration").isIntegralNumber()
                || !node.path("expectedGeneration").canConvertToLong()) throw new IllegalArgumentException();
            for (String field : FIELDS) if (!field.equals("expectedGeneration") && !node.get(field).isTextual()) throw new IllegalArgumentException();
            command = reader.treeToValue(node, AIWorkspaceRouteStore.ObserveDeployment.class);
        } catch (Exception ex) { throw new ResponseStatusException(BAD_REQUEST, "Exact route observation JSON fields are required."); }
        return service.observeDeployment(routeKey, command);
    }
}
