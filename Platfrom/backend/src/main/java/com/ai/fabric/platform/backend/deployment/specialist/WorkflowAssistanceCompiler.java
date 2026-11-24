package com.ai.fabric.platform.backend.deployment.specialist;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

/** Publication of configurable workflow specialists, not another inference/execution loop. */
public final class WorkflowAssistanceCompiler {
    public static final String FIELD = "workflowAssistance";
    public static final String CONTRACT = "LOOMAI_WORKFLOW_ASSISTANCE_V1";
    public static final String CAPABILITY = "configured-workflow-assistance";
    public static final String PROJECTOR = "workflow-answer@1";
    public static final int MAX_BYTES = 65_536;
    private final ObjectMapper mapper;
    public WorkflowAssistanceCompiler(ObjectMapper mapper) { this.mapper = mapper; }
    public static boolean enabled(JsonNode behavior) { return behavior.path(FIELD).path("enabled").asBoolean(false); }

    public void validateConfiguration(JsonNode behavior) {
        var config = behavior.path(FIELD);
        if (config.isMissingNode() || config.isNull()) return;
        require(config.isObject() && config.path("enabled").isBoolean(), "Workflow assistance needs an explicit enabled flag.");
        only(config, "enabled", "profiles", "taskBindings", "defaultProfileId", "policy", "compiled", "contentHash");
        if (!enabled(behavior)) return;
        require("CONVERSATIONAL".equals(behavior.path("type").asText()), "Interactive workflow assistance requires Conversational behavior.");
        var policy = config.path("policy");
        only(policy, "maxActionSteps", "maxWriteSteps", "maxModelCalls", "maxCorrections", "deadlineSeconds");
        number(policy, "maxActionSteps", 1, 8); number(policy, "maxWriteSteps", 0, 3);
        number(policy, "maxModelCalls", 1, 16); number(policy, "maxCorrections", 0, 2);
        number(policy, "deadlineSeconds", 5, 120);
        require(policy.path("maxWriteSteps").asInt() <= policy.path("maxActionSteps").asInt(), "Writes cannot exceed the action limit.");
        var profiles = config.path("profiles");
        require(profiles.isArray() && !profiles.isEmpty() && profiles.size() <= 9, "Configure between one and nine workflow profiles.");
        var ids = new LinkedHashSet<String>();
        for (var profile : profiles) {
            only(profile, "id", "name", "purpose", "instructions", "readActions", "writeActions");
            var id = text(profile, "id", 48);
            require(id.matches("[a-z][a-z0-9]*(?:-[a-z0-9]+)*") && ids.add(id), "Workflow profile IDs must be unique lowercase identifiers.");
            text(profile, "name", 120); text(profile, "purpose", 500); text(profile, "instructions", 8000);
            var reads = strings(profile.path("readActions")); var writes = strings(profile.path("writeActions"));
            require(java.util.Collections.disjoint(reads, writes), "An action cannot be both READ and WRITE in one profile.");
        }
        require(ids.contains(text(config, "defaultProfileId", 48)), "Choose a configured default profile.");
        var bindings = config.path("taskBindings");
        require(bindings.isArray() && bindings.size() <= 32, "Configure at most 32 task bindings.");
        var tasks = new LinkedHashSet<String>();
        for (var binding : bindings) {
            only(binding, "task", "profileId");
            String task = text(binding, "task", 64);
            require(task.matches("[A-Z][A-Z0-9_]{1,63}") && tasks.add(task), "Task identifiers must be unique registered identifiers.");
            require(ids.contains(text(binding, "profileId", 48)), "Task bindings must select a configured profile.");
        }
    }

    public ObjectNode compileBehavior(JsonNode behavior, SpecialistAssistanceCompiler.Authority authority) {
        validateConfiguration(behavior);
        ObjectNode result = behavior.deepCopy();
        if (result.path(FIELD).isObject()) ((ObjectNode) result.path(FIELD)).remove(List.of("compiled", "contentHash"));
        if (!enabled(behavior)) return result;
        require(authority != null, "Workflow capabilities require installed action authority.");
        var config = behavior.path(FIELD);
        var bundle = mapper.createObjectNode().put("contractVersion", CONTRACT);
        bundle.set("policy", config.path("policy").deepCopy()); bundle.set("taskBindings", config.path("taskBindings").deepCopy());
        bundle.put("defaultProfileId", config.path("defaultProfileId").asText());
        var resources = bundle.putArray("resources"); var catalogue = bundle.putArray("profiles");
        var inputSchema = mapper.createObjectNode().put("type", "object").put("additionalProperties", false);
        inputSchema.putArray("required").add("query");
        inputSchema.putObject("properties").putObject("query").put("type", "string").put("minLength", 1).put("maxLength", 8000);
        resources.add(schema("workflow-query", "INPUT", inputSchema));
        var outputSchema = mapper.createObjectNode().put("type", "object").put("additionalProperties", false);
        outputSchema.putArray("required").add("answer").add("work");
        outputSchema.putObject("properties").putObject("answer").put("type", "string").put("maxLength", 8000);
        outputSchema.withObject("/properties").putObject("work").put("type", "object").put("additionalProperties", true);
        resources.add(schema("workflow-result", "OUTPUT", outputSchema));
        for (var profile : config.path("profiles")) {
            String id = profile.path("id").asText(); String name = "workflow-" + id;
            var reads = strings(profile.path("readActions")); var writes = strings(profile.path("writeActions"));
            require(authority.readActionScopes().keySet().containsAll(reads), "A workflow profile references an unconfigured READ action.");
            require(authority.writeActionScopes().keySet().containsAll(writes), "A workflow profile references an unconfigured WRITE action.");
            var prompt = resource("SpecialistPromptProfile", name);
            prompt.putObject("spec").put("constraints", profile.path("instructions").asText())
                .put("outputContract", "The workflow's final message is projected with actual work receipts. Never manufacture a saved or executed effect.");
            resources.add(prompt);
            var specialist = resource("Specialist", name);
            specialist.withObject("/metadata").put("displayName", profile.path("name").asText()).put("description", profile.path("purpose").asText());
            var spec = specialist.putObject("spec").put("mode", "executor");
            spec.putObject("instructions").put("objective", profile.path("purpose").asText()).put("promptProfileRef", name + "@1");
            var execution = spec.putObject("execution").put("strategy", "BOUNDED_ITERATIVE").put("writePolicy", writes.isEmpty() ? "DISABLED" : "ACTION_POLICY");
            var policy = config.path("policy");
            execution.putObject("workflow").put("enabled", true).put("maxActions", policy.path("maxActionSteps").asInt())
                .put("maxWrites", writes.isEmpty() ? 0 : policy.path("maxWriteSteps").asInt())
                .put("maxModelCalls", policy.path("maxModelCalls").asInt()).put("maxCorrections", policy.path("maxCorrections").asInt())
                .put("timeout", "PT" + policy.path("deadlineSeconds").asInt() + "S");
            var capabilities = spec.putObject("capabilities");
            capabilities.putObject("retrieval").put("enabled", false).putArray("vectorSpaces");
            var actions = capabilities.putObject("actions");
            var visible = new LinkedHashSet<>(reads); visible.addAll(writes);
            actions.set("visible", mapper.valueToTree(visible)); actions.set("requestableReads", mapper.valueToTree(reads));
            actions.set("proposableWrites", mapper.valueToTree(writes));
            var input = spec.putObject("input").put("schemaRef", "workflow-query@1").put("rendering", "PRIMARY_TEXT_WITH_JSON_CONTEXT")
                .put("primaryTextPointer", "/query").put("conversationTextPointer", "/query");
            input.putArray("contextPointers"); input.putObject("context").put("position", "resolver");
            var grounding = spec.putObject("grounding").put("requirement", reads.isEmpty() ? "NONE" : "WHEN_CAPABILITY_USED")
                .put("requireEvidenceCitations", false);
            grounding.putArray("sources"); grounding.putArray("validatorRefs");
            var output = spec.putObject("output").put("mode", "DIRECT_PROJECTION").put("schemaRef", "workflow-result@1")
                .put("directProjectorRef", PROJECTOR).put("conversationTextPointer", "/answer");
            output.putArray("finalValidatorRefs");
            spec.putObject("conversation").put("binding", "REQUIRED").put("recordValidatedTurns", false).put("interactionCapability", "DIALOGUE_CAPABLE");
            spec.putObject("limits").put("maxDuration", "PT" + policy.path("deadlineSeconds").asInt() + "S")
                .put("maxInputCharacters", 100000).put("maxGroundingCharacters", 100000).put("maxEvidenceReferences", 16)
                .put("maxOutputCharacters", 16000).put("maxOutputTokens", 4096);
            resources.add(specialist);
            var entry = catalogue.addObject().put("id", id).put("name", profile.path("name").asText())
                .put("specialistRef", name + "@1");
            var scopes = new LinkedHashSet<String>(); scopes.add("specialist:" + name + "@1");
            reads.forEach(action -> { scopes.add("action:" + action); scopes.addAll(authority.readActionScopes().get(action)); });
            writes.forEach(action -> { scopes.add("action:" + action); scopes.addAll(authority.writeActionScopes().get(action)); });
            require(scopes.size() <= 64, "The selected workflow resources exceed the execution scope limit.");
            entry.set("requiredScopes", mapper.valueToTree(scopes.stream().sorted().toList()));
        }
        require(serialized(bundle).getBytes(StandardCharsets.UTF_8).length <= MAX_BYTES, "Workflow profiles exceed the published bundle size limit.");
        ((ObjectNode) result.path(FIELD)).set("compiled", canonical(bundle)); ((ObjectNode) result.path(FIELD)).put("contentHash", hash(bundle));
        return result;
    }
    public void verifyCompiled(JsonNode bundle, String hash) {
        require(CONTRACT.equals(bundle.path("contractVersion").asText()) && bundle.path("resources").isArray()
            && serialized(bundle).getBytes(StandardCharsets.UTF_8).length <= MAX_BYTES && hash(bundle).equals(hash), "The published workflow bundle is invalid.");
    }
    public String serialized(JsonNode value) { return canonical(value).toString(); }
    private String hash(JsonNode value) {
        try { return "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(serialized(value).getBytes(StandardCharsets.UTF_8))); }
        catch (Exception impossible) { throw new IllegalStateException(impossible); }
    }
    private JsonNode canonical(JsonNode value) {
        if (value.isObject()) {
            var result = mapper.createObjectNode(); var fields = new java.util.TreeMap<String, JsonNode>();
            value.fields().forEachRemaining(e -> fields.put(e.getKey(), e.getValue()));
            fields.forEach((key, child) -> result.set(key, canonical(child))); return result;
        }
        if (value.isArray()) { var result = mapper.createArrayNode(); value.forEach(child -> result.add(canonical(child))); return result; }
        return value;
    }
    private ObjectNode resource(String kind, String name) {
        var result = mapper.createObjectNode().put("apiVersion", "ai.fabric/v1").put("kind", kind);
        result.putObject("metadata").put("name", name).put("version", "1"); return result;
    }
    private ObjectNode schema(String name, String direction, JsonNode schema) {
        var result = resource("SpecialistSchema", name); result.putObject("spec").put("direction", direction).put("draft", "2020-12").set("schema", schema); return result;
    }
    private void number(JsonNode value, String key, int min, int max) {
        require(value.path(key).isIntegralNumber() && value.path(key).canConvertToInt()
            && value.path(key).intValue() >= min && value.path(key).intValue() <= max, key + " is outside the execution allowance.");
    }
    private String text(JsonNode node, String field, int max) {
        require(node.path(field).isTextual() && !node.path(field).asText().isBlank() && node.path(field).asText().length() <= max,
            field + " is required and must fit its field limit."); return node.path(field).asText();
    }
    private List<String> strings(JsonNode values) {
        require(values.isArray() && values.size() <= 16, "Select at most 16 action names per access mode.");
        var result = new ArrayList<String>();
        for (var value : values) {
            require(value.isTextual() && !value.asText().isBlank() && value.asText().length() <= 128 && !result.contains(value.asText()), "Action names must be unique configured identifiers.");
            result.add(value.asText());
        }
        return List.copyOf(result);
    }
    private void only(JsonNode value, String... fields) {
        require(value.isObject(), "Expected a workflow configuration object.");
        var allowed = Set.of(fields); value.fieldNames().forEachRemaining(field -> require(allowed.contains(field), "Unsupported workflow field: " + field));
    }
    private static void require(boolean allowed, String reason) { if (!allowed) throw new ResponseStatusException(BAD_REQUEST, reason); }
}
