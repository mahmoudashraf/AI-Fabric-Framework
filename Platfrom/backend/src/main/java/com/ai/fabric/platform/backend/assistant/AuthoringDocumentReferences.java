package com.ai.fabric.platform.backend.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Only document-rooted JSON pointers are expanded. No URI, filesystem or classpath API is used. */
final class AuthoringDocumentReferences {
    private static final int MAX_EXPANSION_NODES = 20_000;
    private final JsonNode root;
    private final List<String> warnings;
    private boolean scopedIds;
    private int nodes;
    private int chars;

    AuthoringDocumentReferences(JsonNode root, List<String> warnings) {
        this.root = root;
        this.warnings = warnings;
    }

    void inspect(JsonNode source) {
        int[] counts = new int[3];
        inspect(source, counts);
        if (counts[0] > 0) {
            warn("External references unresolved: " + counts[0] + "; no network, file or classpath resolution was attempted.");
        }
        if (counts[1] > 0) {
            warn("Non-pointer/dynamic references unresolved: " + counts[1] + "; only document-rooted JSON pointers are supported.");
        }
        scopedIds = counts[2] > 0;
        if (scopedIds) {
            warn("Schema $id scopes present; references remain unresolved to avoid changing their base URI semantics.");
        }
    }

    private void inspect(JsonNode node, int[] counts) {
        if (node.hasNonNull("$id")) {
            counts[2]++;
        }
        for (String key : List.of("$ref", "$dynamicRef")) {
            JsonNode ref = node.path(key);
            if (ref.isTextual()) {
                if (!ref.asText().startsWith("#")) {
                    counts[0]++;
                } else if (!ref.asText().startsWith("#/") || key.equals("$dynamicRef")) {
                    counts[1]++;
                }
            }
        }
        node.elements().forEachRemaining(child -> inspect(child, counts));
    }

    JsonNode expand(JsonNode node) {
        nodes = 0;
        chars = 0;
        try {
            return expand(node, new HashSet<>(), 0);
        } catch (ExpansionLimit ex) {
            warn("Local reference expansion limit reached (32 levels/20000 nodes/200000 characters); affected path retains its unexpanded references.");
            return node.deepCopy();
        }
    }

    private JsonNode expand(JsonNode node, Set<String> chain, int depth) {
        chars += node.isValueNode() ? node.asText().length() + 4 : 2;
        if (++nodes > MAX_EXPANSION_NODES || depth > 32 || chars > AuthoringDocumentImporter.MAX_CONTENT_CHARS) {
            throw new ExpansionLimit();
        }
        if (node.isObject()) {
            String ref = node.path("$ref").asText();
            if (ref.startsWith("#/") && node.size() > 1) {
                warn("Reference siblings retained without expansion to preserve their original dialect semantics.");
            }
            if (!scopedIds && ref.startsWith("#/") && node.size() == 1) {
                JsonNode target;
                try {
                    target = localTarget(root, ref);
                } catch (IllegalArgumentException ex) {
                    target = null;
                }
                if (target == null || target.isMissingNode()) {
                    warn("Missing/invalid local JSON-pointer reference remains unresolved.");
                } else if (!chain.add(ref)) {
                    warn("Cyclic local reference remains unresolved at the cycle boundary.");
                } else {
                    JsonNode resolved = expand(target, chain, depth + 1);
                    chain.remove(ref);
                    return resolved;
                }
            }
            ObjectNode copy = JsonNodeFactory.instance.objectNode();
            var fields = node.fields();
            while (fields.hasNext()) {
                var field = fields.next();
                chars += field.getKey().length() + 4;
                copy.set(field.getKey(), expand(field.getValue(), chain, depth + 1));
            }
            return copy;
        }
        if (node.isArray()) {
            ArrayNode copy = JsonNodeFactory.instance.arrayNode();
            for (JsonNode child : node) {
                copy.add(expand(child, chain, depth + 1));
            }
            return copy;
        }
        return node;
    }

    private void warn(String warning) {
        if (!warnings.contains(warning)) {
            warnings.add(warning);
        }
    }

    static JsonNode localTarget(JsonNode root, String reference) {
        // Decode a URI fragment once, then delegate RFC 6901 escaping to Jackson, never a URL reader.
        String fragment = java.net.URI.create(reference).getFragment();
        return root.at(fragment);
    }

    private static final class ExpansionLimit extends RuntimeException { }
}
