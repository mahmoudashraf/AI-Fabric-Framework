package com.ai.fabric.runtime.authoring;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.HashSet;
import java.util.Set;
import org.springframework.http.HttpStatus;

/** Hard checks protect bounded inference and the minimum lossless proposal execution contract. */
final class AuthoringJsonContract {
    static final int MAX_INPUT_BYTES = 262_144;
    static final int MAX_PROMPT_CHARS = 120_000;
    static final int MAX_RESPONSE_CHARS = 96_000;
    static final String METADATA_RECOVERY_POLICY = "REPORT_AND_PRESERVE";
    private final ObjectMapper mapper;

    AuthoringJsonContract(ObjectMapper mapper) { this.mapper = mapper; }

    String input(ConfigurationAuthoringRequest request) {
        try {
            var node = mapper.valueToTree(request);
            boundedTree(node, 0, new int[]{0});
            String input = mapper.writeValueAsString(node);
            if (input.length() > MAX_PROMPT_CHARS) throw new IllegalArgumentException();
            var ids = new HashSet<String>();
            for (var source : request.sources()) {
                if (!ids.add(source.id())) throw new IllegalArgumentException();
            }
            ((ObjectNode) node).remove("priorTurns");
            return mapper.writeValueAsString(node);
        } catch (Exception invalid) {
            throw new AuthoringFailure(HttpStatus.BAD_REQUEST, "AUTHORING_INPUT_LIMIT", "Authoring input exceeds bounds or has duplicate source IDs.");
        }
    }

    ObjectNode strictResponse(String raw, ConfigurationAuthoringRequest request) {
        try {
            if (raw == null || raw.length() > MAX_RESPONSE_CHARS) throw new IllegalArgumentException();
            // This MUST run on the entire provider response before the framework's permissive extractor.
            JsonNode root = mapper.readerFor(JsonNode.class)
                .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .with(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).readValue(raw);
            if (!(root instanceof ObjectNode object)) throw new IllegalArgumentException();
            boundedTree(root, 0, new int[]{0});
            if (!ConfigurationAuthoringRequest.CONTRACT_VERSION.equals(text(root, "contractVersion", 64))
                || !Set.of("READY", "NEEDS_INPUT", "EXPLAIN", "UNSUPPORTED").contains(text(root, "status", 32))) {
                throw new IllegalArgumentException();
            }
            text(root, "message", 8_000);
            for (var change : array(root, "changes", 128)) {
                pointer(text(change, "path", 2_048));
                String operation = text(change, "operation", 32);
                if (!Set.of("SET", "REMOVE", "UPSERT", "REMOVE_ITEM").contains(operation)) throw new IllegalArgumentException();
                if ((operation.equals("UPSERT") || operation.equals("REMOVE_ITEM"))
                    && text(change, "key", 512).isBlank()) throw new IllegalArgumentException();
                if ((operation.equals("SET") || operation.equals("UPSERT")) && !change.has("value")) throw new IllegalArgumentException();
            }
            advisoryMetadata(object, request);
            return object;
        } catch (Exception invalid) {
            throw new AuthoringFailure(HttpStatus.BAD_GATEWAY, "AUTHORING_INVALID_OUTPUT", "Provider did not return a valid whole JSON authoring envelope.");
        }
    }

    // Advisory metadata is not mutation authority. Keep safe consumer arrays and every original defect.
    private void advisoryMetadata(ObjectNode object, ConfigurationAuthoringRequest request) {
        var diagnostics = mapper.createArrayNode();
        if (object.has("diagnostics")) report(diagnostics, "AUTHORING_PROVIDER_DIAGNOSTICS", "/diagnostics",
            "Provider-supplied diagnostics are unverified metadata, not runtime diagnostics.", object.get("diagnostics"));
        var questions = advisoryArray(object, "questions", diagnostics);
        for (int i = 0; i < questions.size(); i++) {
            var question = questions.get(i);
            String field = "/questions/" + i;
            if (!question.isObject() || !nonblank(question.get("id")) || !nonblank(question.get("question"))) {
                report(diagnostics, "AUTHORING_METADATA_INVALID", field,
                    "Question omitted from the safe array; its original value is preserved here.", question);
                continue;
            }
            ObjectNode safe = question.deepCopy();
            if (safe.has("field") && !safe.path("field").isTextual()) {
                report(diagnostics, "AUTHORING_METADATA_INVALID", field + "/field",
                    "Question field is not text; its original value is preserved here.", safe.remove("field"));
            }
            object.withArray("questions").add(safe);
        }
        var assumptions = advisoryArray(object, "assumptions", diagnostics);
        for (int i = 0; i < assumptions.size(); i++) {
            var value = assumptions.get(i);
            if (value.isTextual()) object.withArray("assumptions").add(value);
            else report(diagnostics, "AUTHORING_METADATA_INVALID", "/assumptions/" + i,
                "Assumption is not text; its original value is preserved here.", value);
        }
        Set<String> sourceIds = new HashSet<>();
        request.sources().forEach(source -> sourceIds.add(source.id()));
        var references = advisoryArray(object, "sourceReferences", diagnostics);
        for (int i = 0; i < references.size(); i++) {
            var value = references.get(i);
            if (!value.isTextual()) report(diagnostics, "AUTHORING_METADATA_INVALID", "/sourceReferences/" + i,
                "Source reference is not text; its original value is preserved here.", value);
            else if (sourceIds.contains(value.textValue())) object.withArray("sourceReferences").add(value);
            else report(diagnostics, "AUTHORING_SOURCE_REFERENCE_UNVERIFIED", "/sourceReferences/" + i,
                "Unverified source reference: not a supplied source ID. Do not resolve, link or authorize it.", value)
                .put("verified", false);
        }
        if (!diagnostics.isEmpty()) object.set("diagnostics", diagnostics);
    }

    private ArrayNode advisoryArray(ObjectNode object, String field, ArrayNode diagnostics) {
        JsonNode original = object.get(field);
        object.putArray(field);
        if (original != null && original.isArray()) return (ArrayNode) original;
        report(diagnostics, original == null ? "AUTHORING_METADATA_MISSING" : "AUTHORING_METADATA_INVALID", "/" + field,
            "Advisory metadata is unavailable as an array; a safe empty array is returned with this diagnostic.", original);
        return mapper.createArrayNode();
    }

    private ObjectNode report(ArrayNode diagnostics, String code, String field, String message, JsonNode original) {
        var diagnostic = diagnostics.addObject().put("code", code).put("field", field).put("message", message)
            .put("policy", METADATA_RECOVERY_POLICY);
        if (original == null) diagnostic.put("originalMissing", true);
        else diagnostic.set("originalValue", original.deepCopy());
        return diagnostic;
    }

    private static boolean nonblank(JsonNode value) { return value != null && value.isTextual() && !value.textValue().isBlank(); }

    private static void boundedTree(JsonNode node, int depth, int[] count) {
        if (depth > 32 || ++count[0] > 20_000) throw new IllegalArgumentException();
        if (node.isContainerNode()) {
            if (node.size() > 4_096) throw new IllegalArgumentException();
            node.elements().forEachRemaining(child -> boundedTree(child, depth + 1, count));
        }
    }

    private static JsonNode array(JsonNode node, String name, int max) {
        JsonNode value = node.get(name);
        if (value == null || !value.isArray() || value.size() > max) throw new IllegalArgumentException();
        return value;
    }
    private static String text(JsonNode node, String name, int max) { return string(node.get(name), max); }
    private static String string(JsonNode value, int max) {
        if (value == null || !value.isTextual() || value.textValue().length() > max) throw new IllegalArgumentException();
        return value.textValue();
    }
    private static void pointer(String path) {
        if (!path.isEmpty() && path.charAt(0) != '/') throw new IllegalArgumentException();
        for (int i = 0; i < path.length(); i++) {
            if (path.charAt(i) == '~' && (++i == path.length() || (path.charAt(i) != '0' && path.charAt(i) != '1'))) {
                throw new IllegalArgumentException();
            }
        }
    }
}
