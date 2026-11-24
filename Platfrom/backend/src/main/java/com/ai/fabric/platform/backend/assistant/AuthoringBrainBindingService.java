package com.ai.fabric.platform.backend.assistant;

import com.ai.fabric.platform.backend.deployment.authority.DeploymentRuntimeAuthorityService;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.deployment.service.DeploymentReleaseEndpointService;
import com.ai.fabric.platform.backend.deployment.specialist.WorkflowAssistanceCompiler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

/** Resolves only the operator's verified, normally published assistant release. */
@Service
public class AuthoringBrainBindingService {
    public record Binding(String deploymentId, String releaseId, String runtimeUrl, String profileId,
                          String specialistId, String bundleHash, int maxWrites, int maxCorrections,
                          List<String> scopes, boolean autonomousEnabled) {
        public Binding(String deploymentId, String releaseId, String runtimeUrl, String profileId,
            String specialistId, String bundleHash, int maxWrites, int maxCorrections, List<String> scopes) {
            this(deploymentId, releaseId, runtimeUrl, profileId, specialistId, bundleHash, maxWrites, maxCorrections, scopes, false);
        }
    }
    private final AuthoringProperties properties;
    private final DeploymentRepository deployments;
    private final DeploymentReleaseRepository releases;
    private final DeploymentVersionRepository versions;
    private final DeploymentReleaseEndpointService endpoints;
    private final DeploymentRuntimeAuthorityService authority;
    private final ObjectMapper mapper;
    public AuthoringBrainBindingService(AuthoringProperties properties, DeploymentRepository deployments,
        DeploymentReleaseRepository releases, DeploymentVersionRepository versions, DeploymentReleaseEndpointService endpoints,
        DeploymentRuntimeAuthorityService authority, ObjectMapper mapper) {
        this.properties = properties; this.deployments = deployments; this.releases = releases;
        this.versions = versions; this.endpoints = endpoints; this.authority = authority; this.mapper = mapper;
    }
    public Binding resolve(String task) {
        return resolve(task, false);
    }
    public Binding conversationBinding() { return resolve("CONFIGURATION", true); }
    private Binding resolve(String task, boolean conversationOnly) {
        if (!properties.isEnabled() || !conversationOnly && !properties.isConfiguredBrainEnabled()) throw unavailable();
        var release = (properties.getManagedReleaseId() == null || properties.getManagedReleaseId().isBlank()
            ? releases.findTopByDeploymentIdOrderByCreatedAtDesc(properties.getDeploymentId())
            : releases.findById(properties.getManagedReleaseId()))
            .filter(r -> properties.getDeploymentId().equals(r.getDeploymentId()) && "APPLIED_VERIFIED".equals(r.getStatus())
                && "PASSED".equals(r.getVerificationStatus())).orElseThrow(AuthoringBrainBindingService::unavailable);
        var deployment = deployments.findById(release.getDeploymentId())
            .filter(d -> properties.getCustomerId().equals(d.getCustomerId()) && properties.getTenantId().equals(d.getTenantId())
                && release.getDeploymentVersionId().equals(d.getActiveVersionId()))
            .orElseThrow(AuthoringBrainBindingService::unavailable);
        var version = versions.findById(release.getDeploymentVersionId())
            .filter(v -> deployment.getId().equals(v.getDeploymentId()) && "PUBLISHED".equals(v.getStatus()))
            .orElseThrow(AuthoringBrainBindingService::unavailable);
        JsonNode behavior;
        try { behavior = mapper.readTree(version.getBehaviorConfigJson()); }
        catch (Exception invalid) { throw unavailable(); }
        authority.requireReleaseUnchangedForProvisioning(deployment, version, release);
        if (conversationOnly) return new Binding(deployment.getId(), release.getId(), endpoints.runtimeBaseUrl(release), "", "",
            behavior.at("/workflowAssistance/contentHash").asText(), 0, 0,
            List.of("configuration:author", "chat:query", "chat:messages", "chat:conversations"));
        if (!WorkflowAssistanceCompiler.enabled(behavior)) throw unavailable();
        var config = behavior.path(WorkflowAssistanceCompiler.FIELD);
        var bundle = config.path("compiled"); String hash = config.path("contentHash").asText();
        new WorkflowAssistanceCompiler(mapper).verifyCompiled(bundle, hash);
        String profile = bundle.path("defaultProfileId").asText();
        for (var route : bundle.path("taskBindings")) if (task.equals(route.path("task").asText())) profile = route.path("profileId").asText();
        JsonNode selected = null;
        for (var item : bundle.path("profiles")) if (profile.equals(item.path("id").asText())) selected = item;
        if (selected == null) throw unavailable();
        var scopes = new LinkedHashSet<>(List.of("configuration:author", "chat:query", "chat:messages", "chat:conversations"));
        selected.path("requiredScopes").forEach(scope -> scopes.add(scope.asText()));
        return new Binding(deployment.getId(), release.getId(), endpoints.runtimeBaseUrl(release), profile,
            selected.path("specialistRef").asText(), hash,
            AuthoringNativeCapabilities.WRITES.stream().anyMatch(action -> scopes.contains("action:" + action)) ? bundle.at("/policy/maxWriteSteps").asInt() : 0,
            bundle.at("/policy/maxCorrections").asInt(), List.copyOf(scopes), bundle.at("/goal/enabled").asBoolean());
    }
    public Map<String, String> headers(Binding binding, String sessionId) {
        var current = conversationBinding();
        if (!binding.releaseId().equals(current.releaseId()) || !binding.bundleHash().equals(current.bundleHash())) throw unavailable();
        var release = releases.findById(binding.releaseId()).orElseThrow(AuthoringBrainBindingService::unavailable);
        var deployment = deployments.findById(binding.deploymentId()).orElseThrow(AuthoringBrainBindingService::unavailable);
        var version = versions.findById(release.getDeploymentVersionId()).orElseThrow(AuthoringBrainBindingService::unavailable);
        return authority.issueReviewedIntegrationSessionHeaders(deployment, version, release, "authoring:" + sessionId, binding.scopes());
    }
    public com.ai.fabric.platform.backend.deployment.authority.RuntimeAuthorityBinding identity(String releaseId) {
        var current = resolve("CONFIGURATION");
        if (!current.releaseId().equals(releaseId)) throw unavailable();
        var release = releases.findById(releaseId).orElseThrow(AuthoringBrainBindingService::unavailable);
        return authority.requireReleaseUnchangedForProvisioning(deployments.findById(current.deploymentId()).orElseThrow(),
            versions.findById(release.getDeploymentVersionId()).orElseThrow(), release).binding();
    }
    private static ResponseStatusException unavailable() { return new ResponseStatusException(SERVICE_UNAVAILABLE, "AUTHORING_MANAGED_BRAIN_UNAVAILABLE"); }
}
