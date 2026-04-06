package com.ai.fabric.integration.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.NullNode;

import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

public final class JsonNavigation {

    private JsonNavigation() {
    }

    public static JsonNode toJsonNode(ObjectMapper objectMapper, Object value) {
        if (value == null) {
            return NullNode.getInstance();
        }
        if (value instanceof JsonNode jsonNode) {
            return jsonNode;
        }
        return objectMapper.valueToTree(value);
    }

    public static JsonNode readPath(JsonNode root, String pathExpression) {
        if (root == null || root.isMissingNode() || root.isNull() || pathExpression == null || pathExpression.isBlank()) {
            return root == null ? MissingNode.getInstance() : root;
        }
        JsonNode current = root;
        for (String rawPart : pathExpression.trim().split("\\.")) {
            String part = rawPart.trim();
            if (part.isEmpty()) {
                continue;
            }
            if (current == null || current.isMissingNode() || current.isNull()) {
                return MissingNode.getInstance();
            }
            int bracketIndex = part.indexOf('[');
            if (bracketIndex > 0) {
                String fieldName = part.substring(0, bracketIndex);
                current = current.path(fieldName);
                String remainder = part.substring(bracketIndex);
                while (remainder.startsWith("[")) {
                    int end = remainder.indexOf(']');
                    if (end <= 1) {
                        return MissingNode.getInstance();
                    }
                    String indexValue = remainder.substring(1, end).trim();
                    int index = Integer.parseInt(indexValue);
                    current = current.path(index);
                    remainder = remainder.substring(end + 1);
                }
                continue;
            }
            current = current.path(part);
        }
        return current == null ? MissingNode.getInstance() : current;
    }

    public static String asText(JsonNode root, String pathExpression) {
        JsonNode node = readPath(root, pathExpression);
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText().trim();
        }
        if (node.isNumber() || node.isBoolean()) {
            return node.asText();
        }
        return node.toString();
    }

    public static long asLong(JsonNode root, String pathExpression, long fallback) {
        JsonNode node = readPath(root, pathExpression);
        if (node == null || node.isMissingNode() || node.isNull()) {
            return fallback;
        }
        if (node.isNumber()) {
            return node.longValue();
        }
        if (node.isTextual()) {
            try {
                return Long.parseLong(node.asText().trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    public static boolean isTruthy(JsonNode root, String pathExpression) {
        JsonNode node = readPath(root, pathExpression);
        if (node == null || node.isMissingNode() || node.isNull()) {
            return false;
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        return "true".equalsIgnoreCase(node.asText(""));
    }

    public static JsonNode firstArrayCandidate(JsonNode root, String preferredPath) {
        JsonNode preferred = readPath(root, preferredPath);
        if (preferred.isArray()) {
            return preferred;
        }
        if (root != null && root.isArray()) {
            return root;
        }
        if (root != null && root.isObject()) {
            for (String candidate : new String[]{"items", "data", "records", "results"}) {
                JsonNode node = root.path(candidate);
                if (node.isArray()) {
                    return node;
                }
            }
        }
        return MissingNode.getInstance();
    }

    public static Iterator<Map.Entry<String, JsonNode>> fields(JsonNode node) {
        return node == null || !node.isObject() ? Map.<String, JsonNode>of().entrySet().iterator() : node.fields();
    }

    public static String normalizeKey(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
