package com.ai.fabric.runtime.tasks;

import ai.fabric.execution.specialist.SpecialistId;
import ai.fabric.execution.specialist.manifest.SpecialistJsonSchemaValidator;
import ai.fabric.execution.specialist.manifest.SpecialistResourceMetadata;
import ai.fabric.execution.specialist.manifest.SpecialistSchemaDefinition;
import ai.fabric.execution.specialist.manifest.SpecialistSchemaDirection;
import ai.fabric.execution.specialist.manifest.SpecialistSchemaSpec;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Published configuration only. Schemas protect execution contracts, not semantic correctness. */
@Component
public final class ManagedTasksBundle {
    public static final String CONTRACT = "LOOMAI_MANAGED_TASKS_V1";
    static final int MAX_BUNDLE_BYTES = 65_536;
    static final int MAX_INPUT_BYTES = 65_536;
    static final int MAX_OUTPUT_BYTES = 65_536;
    private static final Set<String> TASK_FIELDS = Set.of("code", "name", "description", "specialistRef",
        "requiredScopes", "inputSchema", "outputMode", "outputSchema", "deadlineSeconds", "inputBinding",
        "referenceBundleHash", "vectorSpaces", "readActions", "writeActions", "confirmationMode");
    private final JsonNode resources;
    private final JsonNode catalogue;
    private final Map<String, Task> tasks;
    private final String hash;
    private final SpecialistJsonSchemaValidator schemas = new SpecialistJsonSchemaValidator();

    public ManagedTasksBundle(ObjectMapper mapper,
        @Value("${LOOMAI_MANAGED_TASKS_BUNDLE_BASE64:}") String encoded,
        @Value("${LOOMAI_MANAGED_TASKS_BUNDLE_HASH:}") String expectedHash) {
        if (encoded == null || encoded.isBlank()) {
            if (expectedHash != null && !expectedHash.isBlank()) throw invalid();
            resources = mapper.createArrayNode(); catalogue = mapper.createArrayNode(); tasks = Map.of(); hash = null;
            return;
        }
        try {
            if (encoded.length() > 4 * ((MAX_BUNDLE_BYTES + 2) / 3)) throw invalid();
            byte[] bytes = Base64.getDecoder().decode(encoded);
            hash = "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            if (bytes.length > MAX_BUNDLE_BYTES || !hash.equals(expectedHash)) throw invalid();
            var strict = mapper.copy().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
            JsonNode content = strict.readTree(bytes);
            requireFields(content, Set.of("contractVersion", "resources", "tasks"));
            if (!CONTRACT.equals(content.path("contractVersion").asText())
                || !content.path("resources").isArray()
                || content.path("resources").size() > 64 || !content.path("tasks").isArray()
                || content.path("tasks").isEmpty() || content.path("tasks").size() > 16) throw invalid();
            resources = content.path("resources").deepCopy();
            catalogue = content.path("tasks").deepCopy();
            Set<String> resourceIds = new HashSet<>();
            for (var resource : resources) {
                if (!"ai.fabric/v1".equals(resource.path("apiVersion").asText())
                    || !Set.of("Specialist", "SpecialistSchema", "SpecialistPromptProfile").contains(resource.path("kind").asText())
                    || !resourceIds.add(resource.path("kind").asText() + ":" + resourceId(resource))) throw invalid();
            }
            var parsed = new LinkedHashMap<String, Task>();
            for (var item : content.path("tasks")) {
                requireFields(item, TASK_FIELDS);
                String code = text(item, "code", 40);
                if (!code.matches("[a-z][a-z0-9]*(?:-[a-z0-9]+)*")) throw invalid();
                String name = text(item, "name", 120);
                String description = text(item, "description", 500);
                String specialist = text(item, "specialistRef", 128);
                SpecialistId.parse(specialist);
                if (!item.path("requiredScopes").isArray() || item.path("requiredScopes").size() > 64) throw invalid();
                var scopes = new HashSet<String>();
                for (var scope : item.path("requiredScopes")) {
                    if (!scope.isTextual() || !scope.textValue().matches("[A-Za-z][A-Za-z0-9._@:/-]{0,199}")
                        || !scopes.add(scope.textValue())) throw invalid();
                }
                if (!scopes.contains("specialist:" + specialist)) throw invalid();
                String mode = text(item, "outputMode", 4);
                if (!Set.of("JSON", "TEXT").contains(mode) || !item.path("deadlineSeconds").isIntegralNumber()
                    || !item.path("deadlineSeconds").canConvertToInt()) throw invalid();
                int deadline = item.path("deadlineSeconds").intValue();
                if (deadline < 1 || deadline > 120) throw invalid();
                JsonNode input = item.path("inputSchema");
                JsonNode output = item.path("outputSchema");
                if (!input.isObject() || !"object".equals(input.path("type").asText()) || !output.isObject()
                    || !"object".equals(output.path("type").asText())) throw invalid();
                String binding = item.path("inputBinding").asText("WRAPPED");
                String confirmation = item.path("confirmationMode").asText("CALLER");
                if (!Set.of("CALLER", "HUMAN_REVIEW").contains(confirmation)) throw invalid();
                if (!Set.of("WRAPPED", "DIRECT").contains(binding)) throw invalid();
                String referenceHash = item.path("referenceBundleHash").asText(null);
                if ("DIRECT".equals(binding) && (referenceHash == null || !referenceHash.matches("sha256:[0-9a-f]{64}"))) throw invalid();
                if ("WRAPPED".equals(binding) && referenceHash != null) throw invalid();
                JsonNode spec = "WRAPPED".equals(binding) ? resource("Specialist", specialist).path("spec") : null;
                Set<String> vectors = identifiers(spec == null ? item.path("vectorSpaces") : spec.at("/capabilities/retrieval/vectorSpaces"));
                Set<String> reads = identifiers(spec == null ? item.path("readActions") : spec.at("/capabilities/actions/requestableReads"));
                Set<String> writes = identifiers(spec == null ? item.path("writeActions") : spec.at("/capabilities/actions/proposableWrites"));
                if (!java.util.Collections.disjoint(reads, writes)) throw invalid();
                for (String vector : vectors) if (!scopes.contains("vector:" + vector)) throw invalid();
                for (String action : java.util.stream.Stream.concat(reads.stream(), writes.stream()).toList())
                    if (!scopes.contains("action:" + action)) throw invalid();
                for (String field : List.of("vectorSpaces", "readActions", "writeActions")) {
                    if (spec != null && item.has(field) && !identifiers(item.path(field)).equals(switch (field) {
                        case "vectorSpaces" -> vectors;
                        case "readActions" -> reads;
                        default -> writes;
                    })) throw invalid();
                }
                var task = new Task(code, name, description, specialist, Set.copyOf(scopes), mode, deadline,
                    binding, referenceHash, confirmation, vectors, reads, writes,
                    schema(code + "-input", input, SpecialistSchemaDirection.INPUT),
                    schema(code + "-output", output, SpecialistSchemaDirection.OUTPUT));
                schemas.validateDefinition(task.inputSchema(), "managed-task-input");
                schemas.validateDefinition(task.outputSchema(), "managed-task-output");
                if ("WRAPPED".equals(binding)) verifyManifest(task);
                if (parsed.putIfAbsent(code, task) != null) throw invalid();
            }
            tasks = java.util.Collections.unmodifiableMap(parsed);
        } catch (Exception failure) {
            // Configuration can contain credentials accidentally; never include parser values in startup errors.
            throw invalid();
        }
    }

    private void verifyManifest(Task task) {
        JsonNode specialist = resource("Specialist", task.specialistRef());
        var spec = specialist.path("spec");
        if (!(task.writeActions().isEmpty() ? "DISABLED" : "ACTION_POLICY").equals(spec.at("/execution/writePolicy").asText())
            || !spec.at("/capabilities/actions/proposableWrites").isArray()
            || !"DISABLED".equals(spec.at("/conversation/binding").asText())
            || !"NON_INTERACTIVE".equals(spec.at("/conversation/interactionCapability").asText())
            || spec.at("/conversation/recordValidatedTurns").asBoolean()
            || !spec.path("delegation").path("targets").isEmpty() || !spec.path("handoff").path("targets").isEmpty()
            || !"/request".equals(spec.at("/input/primaryTextPointer").asText())
            || spec.at("/input/continuationRef").isTextual()
            || !spec.at("/input/contextPointers").isArray()
            || spec.at("/input/contextPointers").size() != 1
            || !"/taskInput".equals(spec.at("/input/contextPointers/0").asText())) throw invalid();
        JsonNode wrapper = resource("SpecialistSchema", spec.at("/input/schemaRef").asText()).at("/spec/schema");
        if (!"object".equals(wrapper.path("type").asText())
            || !wrapper.path("properties").path("taskInput").equals(relocated(task.inputSchema().spec().schema()))
            || !"string".equals(wrapper.at("/properties/request/type").asText())) throw invalid();
        JsonNode internalOutput = resource("SpecialistSchema", spec.at("/output/schemaRef").asText()).at("/spec/schema");
        if (!internalOutput.equals(task.outputSchema().spec().schema())) throw invalid();
        if ("TEXT".equals(task.outputMode()) && !"string".equals(internalOutput.at("/properties/answer/type").asText())) throw invalid();
        for (var vector : spec.at("/capabilities/retrieval/vectorSpaces"))
            if (!task.requiredScopes().contains("vector:" + vector.asText())) throw invalid();
        for (var action : spec.at("/capabilities/actions/requestableReads"))
            if (!task.requiredScopes().contains("action:" + action.asText())) throw invalid();
        for (var action : spec.at("/capabilities/actions/proposableWrites"))
            if (!task.requiredScopes().contains("action:" + action.asText())) throw invalid();
        Set<String> visible = identifiers(spec.at("/capabilities/actions/visible"), 32);
        var selected = new HashSet<>(task.readActions());
        selected.addAll(task.writeActions());
        if (!visible.equals(selected)) throw invalid();
    }

    private static JsonNode relocated(JsonNode schema) {
        var copy = schema.deepCopy();
        relocate(copy);
        return copy;
    }

    private static void relocate(JsonNode node) {
        if (node.isObject() && node.has("$ref"))
            ((com.fasterxml.jackson.databind.node.ObjectNode) node).put("$ref", "#/properties/taskInput" + node.path("$ref").asText().substring(1));
        if (node.isContainerNode()) node.elements().forEachRemaining(ManagedTasksBundle::relocate);
    }

    private JsonNode resource(String kind, String id) {
        for (var resource : resources)
            if (kind.equals(resource.path("kind").asText()) && id.equals(resourceId(resource))) return resource;
        throw invalid();
    }

    private static String resourceId(JsonNode resource) {
        return text(resource.path("metadata"), "name", 80) + "@" + text(resource.path("metadata"), "version", 40);
    }

    private static SpecialistSchemaDefinition schema(String name, JsonNode schema, SpecialistSchemaDirection direction) {
        return new SpecialistSchemaDefinition("ai.fabric/v1", "SpecialistSchema", new SpecialistResourceMetadata(name, "1"),
            new SpecialistSchemaSpec(direction, "2020-12", schema.deepCopy()));
    }

    static void requireFields(JsonNode object, Set<String> allowed) {
        if (!object.isObject()) throw invalid();
        object.fieldNames().forEachRemaining(field -> { if (!allowed.contains(field)) throw invalid(); });
    }

    private static Set<String> identifiers(JsonNode values) {
        return identifiers(values, 16);
    }

    private static Set<String> identifiers(JsonNode values, int maximum) {
        if (!values.isArray() || values.size() > maximum) throw invalid();
        Set<String> identifiers = new HashSet<>();
        for (JsonNode value : values) {
            if (!value.isTextual() || value.textValue().isBlank() || value.textValue().length() > 120
                || !identifiers.add(value.textValue())) throw invalid();
        }
        return Set.copyOf(identifiers);
    }

    private static String text(JsonNode object, String key, int max) {
        JsonNode value = object.path(key);
        if (!value.isTextual() || value.textValue().isBlank() || value.textValue().length() > max) throw invalid();
        return value.textValue();
    }

    private static IllegalStateException invalid() {
        return new IllegalStateException("Managed tasks require a bounded hash-verified LOOMAI_MANAGED_TASKS_V1 bundle with valid execution contracts.");
    }

    public boolean enabled() { return hash != null; }
    public String contentHash() { return hash; }
    public JsonNode resources() { return resources.deepCopy(); }
    public JsonNode catalogue() { return catalogue.deepCopy(); }
    public List<Task> tasks() { return List.copyOf(tasks.values()); }
    Task find(String code) { return tasks.get(code); }
    void validateInput(Task task, JsonNode input) { schemas.validate(task.inputSchema(), input); }
    void validateOutput(Task task, JsonNode output) { schemas.validate(task.outputSchema(), output); }

    public record Task(String code, String name, String description, String specialistRef, Set<String> requiredScopes,
                       String outputMode, int deadlineSeconds, String inputBinding, String referenceBundleHash, String confirmationMode,
                       Set<String> vectorSpaces, Set<String> readActions, Set<String> writeActions, SpecialistSchemaDefinition inputSchema,
                       SpecialistSchemaDefinition outputSchema) {
        String request() { return name + ": " + description; }
    }
}
