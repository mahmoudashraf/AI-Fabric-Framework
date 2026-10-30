package com.ai.fabric.platform.backend.support;

import com.ai.fabric.platform.backend.deployment.authority.DeploymentRuntimeAuthorityService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentTargetReference;
import com.ai.fabric.platform.backend.deployment.service.DeploymentRuntimeJsonTransport;
import com.ai.fabric.platform.backend.deployment.service.DeploymentTargetContextService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;

/** Status-only projection from the selected app's exact runtime; no source text, names, locators or errors. */
@Service
public class SupportDocumentStatusService {
    private static final Set<String> STATUSES = Set.of("REGISTERED", "PREPARING", "INDEXING", "ACTIVE", "REPLACING",
        "DELETE_PENDING", "DELETING", "DELETED", "FAILED");
    private final DeploymentTargetContextService targets;
    private final DeploymentRuntimeAuthorityService authority;
    private final DeploymentRuntimeJsonTransport transport;

    public SupportDocumentStatusService(DeploymentTargetContextService targets, DeploymentRuntimeAuthorityService authority,
                                        DeploymentRuntimeJsonTransport transport) {
        this.targets = targets; this.authority = authority; this.transport = transport;
    }

    public Map<String, Object> read(DeploymentEntity deployment, DeploymentTargetReference target, String principalId) {
        if (!deployment.getId().equals(target.deploymentId())) return unavailable("TARGET_DEPLOYMENT_MISMATCH");
        try {
            var context = targets.resolveOwnedRead(deployment.getId(), target.targetProfileId(), target.releaseId(),
                deployment.getCustomerId(), deployment.getTenantId());
            // The internal caller has already checked the request owner, selected resources and current consent.
            var headers = authority.issueReviewedIntegrationSessionHeaders(context.deployment(), context.version(),
                context.release(), principalId, List.of("documents:read"));
            var response = transport.send(context.runtimeBaseUrl(), "GET", "/api/documents/sources", headers, null, null);
            if (response.statusCode() != 200) return unavailable("RUNTIME_STATUS_UNAVAILABLE");
            JsonNode body = response.body();
            if (body == null || !body.isArray() || body.size() > 1000) return unavailable("STATUS_RESPONSE_UNREPRESENTABLE");
            var counts = new TreeMap<String, Integer>();
            int active = 0, candidateAhead = 0;
            Instant updatedAt = null;
            for (JsonNode source : body) {
                if (!source.isObject()) return unavailable("STATUS_RESPONSE_UNREPRESENTABLE");
                String raw = source.path("status").asText("");
                counts.merge(STATUSES.contains(raw) ? raw : "UNKNOWN", 1, Integer::sum);
                if (source.path("activeVersion").isIntegralNumber()) {
                    active++;
                    if (source.path("candidateVersion").isIntegralNumber()
                        && source.path("candidateVersion").asLong() > source.path("activeVersion").asLong()) candidateAhead++;
                }
                if (source.path("updatedAt").isTextual()) {
                    try {
                        Instant value = Instant.parse(source.path("updatedAt").asText());
                        if (updatedAt == null || value.isAfter(updatedAt)) updatedAt = value;
                    } catch (java.time.format.DateTimeParseException ignored) { /* No raw upstream value is exposed. */ }
                }
            }
            var result = new LinkedHashMap<String, Object>();
            result.put("availability", "CURRENT");
            result.put("observationBasis", "RUNTIME_REGISTERED_SOURCE_STATE");
            result.put("targetProfileId", target.targetProfileId());
            result.put("releaseId", target.releaseId());
            result.put("registeredSourceCount", body.size());
            result.put("sourcesByStatus", Map.copyOf(counts));
            result.put("sourcesWithActiveVersion", active);
            result.put("sourcesWithNewerCandidate", candidateAhead);
            if (updatedAt != null) result.put("latestSourceStateUpdatedAt", updatedAt);
            result.put("observedAt", Instant.now());
            result.put("upstreamStorageProbed", false);
            result.put("guidance", "Registered index state does not prove that source storage has no newer content.");
            return Map.copyOf(result);
        } catch (ResponseStatusException error) {
            return unavailable("STATUS_CHECK_UNAVAILABLE");
        }
    }

    private Map<String, Object> unavailable(String code) {
        return Map.of("availability", "INCOMPLETE", "code", code, "observedAt", Instant.now(), "upstreamStorageProbed", false);
    }
}
