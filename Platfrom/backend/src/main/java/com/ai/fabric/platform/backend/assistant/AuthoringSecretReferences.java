package com.ai.fabric.platform.backend.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Reference identities only. Values are resolved by the existing scoped materializer. */
final class AuthoringSecretReferences {
    private static final Pattern REFERENCE = Pattern.compile("\\$\\{(?:secret:)?([A-Z][A-Z0-9_]{1,127})}");
    private AuthoringSecretReferences() { }
    static Set<String> names(JsonNode catalogue) {
        var result = new HashSet<String>();
        if (catalogue != null && catalogue.isArray()) for (var value : catalogue) if (value.isTextual()) result.add(value.asText());
        return Set.copyOf(result);
    }
    static String name(JsonNode value) {
        if (value == null || !value.isTextual()) return null;
        var matcher = REFERENCE.matcher(value.asText());
        return matcher.matches() ? matcher.group(1) : null;
    }
    static boolean owned(JsonNode value, Set<String> names) {
        String name = name(value);
        return name != null && names.contains(name);
    }
    static boolean containsReference(JsonNode value) {
        return value != null && value.isTextual() && REFERENCE.matcher(value.asText()).find();
    }
    static boolean slot(List<String> path) {
        return path.size() == 5 && "routingConfig".equals(path.get(0)) && "actions".equals(path.get(1)) && "headers".equals(path.get(3))
            || path.equals(List.of("routingConfig", "connector", "upstream", "auth", "value"));
    }
    static boolean selection(JsonNode root, List<String> path, JsonNode value, Set<String> names) {
        return slot(path) && owned(value, names) && (!"connector".equals(path.get(1))
            || "API_KEY".equals(root.at("/routingConfig/connector/upstream/auth/type").asText()));
    }
    static String wire(JsonNode value) { return "${" + name(value) + "}"; }
}
