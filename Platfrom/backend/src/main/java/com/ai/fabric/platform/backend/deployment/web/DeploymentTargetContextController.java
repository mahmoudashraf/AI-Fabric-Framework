package com.ai.fabric.platform.backend.deployment.web;

import com.ai.fabric.platform.backend.deployment.service.DeploymentTargetContextService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

import static org.springframework.http.HttpStatus.CONFLICT;

@RestController
@RequestMapping("/api/deployments/{deploymentId}/targets/{targetProfileId}/releases/{releaseId}")
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR','CUSTOMER_ADMIN')")
public class DeploymentTargetContextController {
    private final DeploymentTargetContextService service;
    private final ObjectMapper objectMapper;

    public DeploymentTargetContextController(DeploymentTargetContextService service, ObjectMapper objectMapper) {
        this.service = service;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/context")
    public Summary context(@PathVariable String deploymentId, @PathVariable String targetProfileId, @PathVariable String releaseId) {
        var context = service.resolve(deploymentId, targetProfileId, releaseId, false);
        List<String> documentDatasetIds = new ArrayList<>();
        boolean externalIntegrationConfigured = false;
        boolean conversationHandoverConfigured;
        try {
            JsonNode config = objectMapper.readTree(context.version().getMarketplaceDatasetConfigJson());
            for (JsonNode dataset : config.path("datasets")) {
                if ("EXTERNAL_SYNC_HTTP".equals(dataset.path("ingestionMode").asText())) {
                    externalIntegrationConfigured = true;
                }
                if ("EXTERNAL_DOCUMENT_STORAGE".equals(dataset.path("ingestionMode").asText())) {
                    String id = dataset.path("datasetId").asText();
                    if (id.isBlank()) throw new IllegalArgumentException();
                    documentDatasetIds.add(id);
                }
            }
            conversationHandoverConfigured = objectMapper.readTree(context.version().getBehaviorConfigJson())
                .path("conversationHandover")
                .path("enabled")
                .asBoolean(false);
        } catch (Exception error) {
            throw new ResponseStatusException(CONFLICT, "Selected release capability configuration is invalid.");
        }
        return new Summary(deploymentId, targetProfileId, releaseId, context.version().getId(),
            context.target().getEnvironmentName(), context.release().getStatus(),
            context.version().getConfigHash(), context.runtimeBaseUrl(), context.connectorBaseUrl(),
            List.copyOf(documentDatasetIds), externalIntegrationConfigured, conversationHandoverConfigured);
    }

    public record Summary(String deploymentId, String targetProfileId, String releaseId, String versionId,
                          String environmentName, String releaseStatus, String configHash,
                          String runtimeBaseUrl, String connectorBaseUrl, List<String> documentDatasetIds,
                          boolean externalIntegrationConfigured, boolean conversationHandoverConfigured) {
    }
}
