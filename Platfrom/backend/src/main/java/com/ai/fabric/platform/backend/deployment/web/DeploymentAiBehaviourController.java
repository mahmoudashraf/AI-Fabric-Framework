package com.ai.fabric.platform.backend.deployment.web;

import com.ai.fabric.platform.backend.deployment.service.DeploymentAiBehaviourProfiles;
import com.ai.fabric.platform.backend.deployment.service.DeploymentCuratedModuleCatalogService;
import com.ai.fabric.platform.backend.deployment.service.DeploymentConfigCompiler;
import com.ai.fabric.platform.backend.deployment.service.DeploymentService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR','CUSTOMER_ADMIN')")
public class DeploymentAiBehaviourController {
    private final DeploymentService deployments;
    private final DeploymentCuratedModuleCatalogService curated;
    private final DeploymentConfigCompiler compiler;

    public DeploymentAiBehaviourController(DeploymentService deployments, DeploymentCuratedModuleCatalogService curated,
                                          DeploymentConfigCompiler compiler) {
        this.deployments = deployments;
        this.curated = curated;
        this.compiler = compiler;
    }

    @GetMapping("/deployment-ai-behaviour-profiles")
    public Map<String, Object> catalogue() { return DeploymentAiBehaviourProfiles.catalogue(); }

    @PostMapping("/deployments/{deploymentId}/ai-behaviour/compose")
    public Map<String, Object> compose(@PathVariable String deploymentId, @RequestBody ComposeRequest request) throws com.fasterxml.jackson.core.JsonProcessingException {
        var draft = deployments.getActiveDraftForDeployment(deploymentId);
        String module = draft.providerConfig().path("curatedModuleId").asText("default");
        var result = new java.util.LinkedHashMap<>(DeploymentAiBehaviourProfiles.compose(
            request.promptConfig(), request.selections(), curated.promptPreset(module)));
        var before = compiler.compilePromptConfig(request.promptConfig(), draft.providerConfig(), draft.knowledgeSourceConfig());
        var after = compiler.compilePromptConfig((JsonNode) result.get("promptConfig"), draft.providerConfig(), draft.knowledgeSourceConfig());
        result.put("effectivePromptConfig", after);
        result.put("changes", DeploymentAiBehaviourProfiles.changes(before, after));
        return result;
    }

    public record ComposeRequest(JsonNode promptConfig, JsonNode selections) { }
}
