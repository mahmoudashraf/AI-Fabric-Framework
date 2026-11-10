package com.ai.fabric.platform.backend.deployment.tasks;

import com.ai.fabric.platform.backend.deployment.service.DeploymentService;
import com.ai.fabric.platform.backend.deployment.specialist.SpecialistAssistanceAuthorityService;
import com.ai.fabric.platform.backend.deployment.specialist.SpecialistAssistanceCompiler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

/** Authoring catalogue from the owned draft, never from a global provider registry. */
@Service
public class ManagedTaskResourceService {
    private final DeploymentService deployments;
    private final SpecialistAssistanceAuthorityService authority;
    private final ObjectMapper mapper;

    public ManagedTaskResourceService(DeploymentService deployments, SpecialistAssistanceAuthorityService authority, ObjectMapper mapper) {
        this.deployments = deployments; this.authority = authority; this.mapper = mapper;
    }

    public JsonNode resources(String deploymentId) {
        var draft = deployments.getActiveDraftForDeployment(deploymentId);
        var actions = draft.actionsConfig();
        var knowledge = draft.knowledgeSourceConfig();
        var behavior = draft.behaviorConfig();
        var grants = authority.resolveTasks(deploymentId, actions, knowledge);
        var result = mapper.createObjectNode();
        var collections = result.putArray("knowledgeCollections");
        grants.vectorSpaces().stream().sorted().forEach(id -> collections.addObject().put("id", id).put("name", id));
        var capabilities = result.putArray("actions");
        for (JsonNode action : actions.path("actions")) {
            String id = action.path("name").asText();
            boolean read = grants.readActionScopes().containsKey(id);
            if (!read && !grants.writeActionScopes().containsKey(id)) continue;
            capabilities.addObject().put("id", id).put("name", action.path("displayName").asText(id))
                .put("accessMode", read ? "READ" : "WRITE")
                .put("confirmationRequired", action.path("confirmationRequired").asBoolean(true));
        }
        var specialists = result.putArray("specialists");
        boolean reviews = false;
        for (JsonNode extension : behavior.path("executionExtensions")) if ("HUMAN_REVIEW".equals(extension.asText())) reviews = true;
        result.put("reviewAvailable", reviews);
        if (SpecialistAssistanceCompiler.enabled(behavior)) {
            JsonNode compiled = new SpecialistAssistanceCompiler(mapper).compileBehavior(behavior,
                authority.resolve(deploymentId, actions, knowledge)).at("/specialistAssistance/compiled");
            for (JsonNode invocation : compiled.path("invocations")) {
                if (!"SPECIALIST".equals(invocation.path("targetType").asText())) continue;
                String ref = invocation.path("targetRef").asText();
                JsonNode spec = find(compiled.path("resources"), "Specialist", ref).path("spec");
                var entry = specialists.addObject().put("ref", ref).put("name", invocation.path("name").asText())
                    .put("description", invocation.path("description").asText()).put("outputMode", "JSON")
                    .put("deadlineSeconds", invocation.path("deadlineSeconds").asInt()).put("maxOutputTokens", spec.at("/limits/maxOutputTokens").asInt());
                entry.set("inputSchema", find(compiled.path("resources"), "SpecialistSchema", spec.at("/input/schemaRef").asText()).at("/spec/schema").deepCopy());
                entry.set("outputSchema", find(compiled.path("resources"), "SpecialistSchema", spec.at("/output/schemaRef").asText()).at("/spec/schema").deepCopy());
                entry.set("vectorSpaces", spec.at("/capabilities/retrieval/vectorSpaces").deepCopy());
                entry.set("readActions", spec.at("/capabilities/actions/requestableReads").deepCopy());
                entry.set("writeActions", spec.at("/capabilities/actions/proposableWrites").deepCopy());
            }
        }
        return result;
    }

    private JsonNode find(JsonNode resources, String kind, String ref) {
        for (JsonNode resource : resources) if (kind.equals(resource.path("kind").asText())
            && ref.equals(resource.at("/metadata/name").asText() + "@" + resource.at("/metadata/version").asText())) return resource;
        throw new IllegalStateException("Compiled specialist resource is missing.");
    }
}
