package com.ai.fabric.platform.backend.desk;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.authority.DeploymentRuntimeAuthorityService;
import com.ai.fabric.platform.backend.deployment.repository.*;
import com.ai.fabric.platform.backend.deployment.service.DeploymentReleaseEndpointService;
import com.fasterxml.jackson.databind.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import static org.springframework.http.HttpStatus.*;

@RestController
@RequestMapping("/api/desk/bridge/v1/bindings/{bindingId}/reviews")
@PreAuthorize("hasAuthority('DESK_REVIEW_INTEGRATION')")
public class DeskReviewBridgeController {
    private final DeskBindingRepository bindings;
    private final DeskActivationService activations;
    private final DeploymentRepository deployments;
    private final DeploymentReleaseRepository releases;
    private final DeploymentVersionRepository versions;
    private final DeploymentReleaseEndpointService endpoints;
    private final DeploymentRuntimeAuthorityService authority;
    private final PlatformAuditService audit;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.NEVER).build();

    public DeskReviewBridgeController(DeskBindingRepository bindings, DeskActivationService activations, DeploymentRepository deployments,
        DeploymentReleaseRepository releases, DeploymentVersionRepository versions, DeploymentReleaseEndpointService endpoints,
        DeploymentRuntimeAuthorityService authority, PlatformAuditService audit, ObjectMapper mapper) {
        this.bindings = bindings; this.activations = activations; this.deployments = deployments; this.releases = releases; this.versions = versions;
        this.endpoints = endpoints; this.authority = authority; this.audit = audit; this.mapper = mapper;
    }

    @GetMapping
    public ResponseEntity<JsonNode> inbox(@PathVariable String bindingId, @AuthenticationPrincipal DeskReviewPrincipal principal,
        @RequestParam(defaultValue = "50") int limit) {
        return forward(bindingId, principal, "GET", "/api/reviews/v1/tasks?limit=" + Math.max(1, Math.min(limit, 200)), null);
    }

    @GetMapping("/{taskId}")
    public ResponseEntity<JsonNode> detail(@PathVariable String bindingId, @PathVariable String taskId,
        @AuthenticationPrincipal DeskReviewPrincipal principal) {
        return forward(bindingId, principal, "GET", "/api/reviews/v1/tasks/" + task(taskId), null);
    }

    @PostMapping("/{taskId}/decisions")
    public ResponseEntity<JsonNode> decide(@PathVariable String bindingId, @PathVariable String taskId,
        @AuthenticationPrincipal DeskReviewPrincipal principal, @RequestBody Decision body) {
        if (body == null || !Set.of("APPROVE", "REJECT").contains(body.decision()) || body.expectedVersion() < 0
            || body.decisionId() == null || body.decisionId().isBlank() || body.decisionId().length() > 160) {
            throw new ResponseStatusException(BAD_REQUEST, "A valid decision, decisionId and expectedVersion are required.");
        }
        var result = forward(bindingId, principal, "POST", "/api/reviews/v1/tasks/" + task(taskId) + "/decisions", body);
        audit.record("DESK_ACTION_REVIEW_DECISION", "DESK_BINDING", bindingId,
            Map.of("reviewerSubject", principal.subject(), "taskId", taskId, "decisionId", body.decisionId(),
                "decision", body.decision(), "runtimeStatus", result.getStatusCode().value()));
        return result;
    }

    private ResponseEntity<JsonNode> forward(String bindingId, DeskReviewPrincipal principal, String method, String path, Object body) {
        if (principal == null || !bindingId.equals(principal.bindingId())) throw new ResponseStatusException(FORBIDDEN);
        var binding = bindings.findById(bindingId).orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
        var deployment = deployments.findById(binding.getDeploymentId()).orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
        var release = activations.currentReviewRelease(binding);
        var version = versions.findById(release.getDeploymentVersionId()).orElseThrow();
        try {
            JsonNode behavior = mapper.readTree(version.getBehaviorConfigJson());
            boolean enabled = false;
            for (var extension : behavior.path("executionExtensions")) if ("HUMAN_REVIEW".equals(extension.asText())) enabled = true;
            if (!enabled) throw new ResponseStatusException(CONFLICT, "Human Review is not enabled for this deployment target.");
            var request = HttpRequest.newBuilder(URI.create(endpoints.runtimeBaseUrl(release) + path)).timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json");
            authority.issueDeskReviewerHeaders(deployment, version, release, principal.subject(), body != null).forEach(request::header);
            if (body == null) request.GET();
            else request.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
            var response = http.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
            byte[] bytes;
            try (var input = response.body()) { bytes = input.readNBytes(1048577); }
            if (bytes.length > 1048576) throw new ResponseStatusException(BAD_GATEWAY, "Review response is too large.");
            return ResponseEntity.status(response.statusCode()).body(bytes.length == 0 ? mapper.createObjectNode() : mapper.readTree(bytes));
        } catch (ResponseStatusException error) { throw error; }
        catch (InterruptedException error) {
            Thread.currentThread().interrupt(); throw new ResponseStatusException(BAD_GATEWAY, "Review request interrupted.");
        } catch (Exception error) { throw new ResponseStatusException(BAD_GATEWAY, "The deployment review endpoint is unavailable."); }
    }

    private String task(String id) {
        if (id == null || id.isBlank() || id.length() > 120 || id.contains("/") || id.contains("..")) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid task identifier.");
        }
        return URLEncoder.encode(id, StandardCharsets.UTF_8).replace("+", "%20");
    }

    public record Decision(String decisionId, String decision, long expectedVersion) { }
}
