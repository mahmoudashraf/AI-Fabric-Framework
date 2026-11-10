package com.ai.fabric.platform.backend.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Formal paths and credential fields only; never interprets natural-language intent. */
@Component
public class AuthoringChanges {
    public static final String REDACTED = "[REDACTED]";
    private static final Set<String> NAMED_COLLECTIONS = Set.of("actions", "sources", "datasets", "params", "knowledgeSources");
    private static final Set<String> CREDENTIAL_FIELDS = Set.of("apiKey", "api-key", "api_key", "password", "secret",
        "clientSecret", "accessToken", "refreshToken", "signingKey", "privateKey", "Authorization", "authorization",
        "X-API-Key", "X-API-KEY", "X-ADMIN-API-KEY", "token", "secretValue", "credentialValue",
        "access_token", "refresh_token", "client_secret", "apiKeyValue", "openaiApiKey", "anthropicApiKey");
    private final ObjectMapper mapper;
    public AuthoringChanges(ObjectMapper mapper) { this.mapper = mapper; }

    public JsonNode project(JsonNode value) {
        return safeProject(value, List.of());
    }
    private JsonNode safeProject(JsonNode value, List<String> path) {
        var literals = new java.util.HashSet<String>();
        var fields = new java.util.TreeMap<String, String>(); collectCredentials(value, path, fields);
        for (String serialized : fields.values()) {
            try { collectLiterals(mapper.readTree(serialized), literals); }
            catch (java.io.IOException impossible) { throw new IllegalStateException("Credential projection could not be prepared."); }
        }
        var ordered = literals.stream().sorted(java.util.Comparator.comparingInt(String::length).reversed()).toList();
        var matcher = ordered.isEmpty() ? null : java.util.regex.Pattern.compile(java.util.regex.Pattern.quote(REDACTED) + "|"
            + ordered.stream().map(java.util.regex.Pattern::quote).collect(java.util.stream.Collectors.joining("|")));
        return redactLiterals(project(value, path), matcher);
    }
    private void collectLiterals(JsonNode value, Set<String> literals) {
        if (value.isTextual() && !value.asText().isBlank() && !REDACTED.equals(value.asText())) literals.add(value.asText());
        else if (value.isContainerNode()) value.forEach(child -> collectLiterals(child, literals));
    }
    private JsonNode redactLiterals(JsonNode value, java.util.regex.Pattern literals) {
        if (literals == null) return value;
        if (value.isTextual()) {
            return mapper.getNodeFactory().textNode(literals.matcher(value.asText()).replaceAll(java.util.regex.Matcher.quoteReplacement(REDACTED)));
        }
        if (value.isObject()) value.fields().forEachRemaining(field -> ((ObjectNode) value).set(field.getKey(), redactLiterals(field.getValue(), literals)));
        else if (value.isArray()) for (int i = 0; i < value.size(); i++) ((ArrayNode) value).set(i, redactLiterals(value.get(i), literals));
        return value;
    }
    private JsonNode project(JsonNode value, List<String> path) {
        if (value == null) return mapper.createObjectNode();
        JsonNode copy = value.deepCopy();
        if (copy.isObject()) {
            var fields = copy.fields();
            while (fields.hasNext()) {
                var field = fields.next();
                ((ObjectNode) copy).set(field.getKey(), credential(path, field.getKey(), field.getValue(), value)
                    ? mapper.getNodeFactory().textNode(REDACTED) : project(field.getValue(), child(path, field.getKey())));
            }
        } else if (copy.isArray()) {
            for (int i = 0; i < copy.size(); i++) ((ArrayNode) copy).set(i, project(copy.get(i), child(path, Integer.toString(i))));
        }
        return copy;
    }

    public Result apply(JsonNode current, JsonNode changes, List<String> allowedPaths) {
        return apply(current, changes, allowedPaths, false);
    }
    public Result undo(JsonNode current, JsonNode diff, List<String> allowedPaths) {
        return apply(current, inverse(diff), allowedPaths, true);
    }
    private Result apply(JsonNode current, JsonNode changes, List<String> allowedPaths, boolean restoring) {
        if (changes == null || !changes.isArray() || changes.size() > 30) throw invalid("Expected at most 30 structured changes.");
        var candidate = (ObjectNode) current.deepCopy();
        var diffs = mapper.createArrayNode();
        for (var change : changes) {
            String path = text(change, "path");
            if (!path.startsWith("/") || path.length() > 600 || !allowedPaths.stream()
                .anyMatch(allowed -> path.equals(allowed) || path.startsWith(allowed + "/")))
                throw invalid("The proposed field is outside this configuration task.");
            List<String> parts = pointers(path);
            String operation = text(change, "operation");
            JsonNode before = candidate.at(path).deepCopy();
            JsonNode parent = candidate;
            for (int i = 0; i < parts.size() - 1; i++) {
                String part = parts.get(i);
                if (!parent.isObject()) throw invalid("Use an UPSERT operation to change a named array item.");
                if (!parent.has(part)) ((ObjectNode) parent).set(part, mapper.createObjectNode());
                parent = parent.get(part);
            }
            if (!parent.isObject()) throw invalid("Only declared object fields and named array items can be edited.");
            String field = parts.getLast();
            var target = (ObjectNode) parent;
            if (credential(parts.subList(0, parts.size() - 1), field, target.get(field), target))
                throw invalid("Use the secure connection form for credentials.");
            switch (operation) {
                case "SET" -> {
                    if (!change.has("value")) throw invalid("SET requires a value.");
                    if (!restoring && NAMED_COLLECTIONS.contains(field) && change.path("value").isArray())
                        throw invalid("Use UPSERT or REMOVE_ITEM for named items, preserving the other entries.");
                    target.set(field, merge(target.get(field), change.get("value"), parts, restoring));
                }
                case "REMOVE" -> {
                    if (containsCredentials(target.get(field), parts)) throw invalid("Credential-bearing fields cannot be removed by the assistant.");
                    target.remove(field);
                }
                case "UPSERT", "REMOVE_ITEM" -> {
                    String key = text(change, "key");
                    if (key.isBlank() || key.length() > 200) throw invalid("A stable item key is required.");
                    if (target.has(field) && !target.path(field).isArray()) throw invalid("Named-item changes require an array field.");
                    ArrayNode array = target.has(field) ? (ArrayNode) target.get(field) : target.putArray(field);
                    String identity = identity(field);
                    JsonNode value = change.get("value");
                    if ("UPSERT".equals(operation) && (value == null || !value.isObject() || !key.equals(value.path(identity).asText())))
                        throw invalid("The item's stable identity must match its key.");
                    int found = -1;
                    for (int i = 0; i < array.size(); i++) if (key.equals(array.get(i).path(identity).asText())) { found = i; break; }
                    if ("REMOVE_ITEM".equals(operation)) {
                        if (found >= 0) {
                            if (containsCredentials(array.get(found), parts)) throw invalid("Use the secure form to remove credential-bearing items.");
                            array.remove(found);
                        }
                    } else if (found >= 0) array.set(found, merge(array.get(found), value, parts, restoring));
                    else array.add(merge(null, value, parts, restoring));
                }
                default -> throw invalid("Unsupported structured change operation.");
            }
            var diff = diffs.addObject().put("path", path).put("operation", operation);
            if (change.has("key")) diff.set("key", change.get("key"));
            JsonNode after = candidate.at(path);
            if (change.has("key") && Set.of("UPSERT", "REMOVE_ITEM").contains(operation)) {
                String identity = identity(field);
                before = item(before, identity, change.path("key").asText());
                after = item(after, identity, change.path("key").asText());
            }
            diff.put("existed", !before.isMissingNode());
            diff.set("before", safeProject(before, parts)); diff.set("after", safeProject(after, parts));
        }
        if (!credentials(current).equals(credentials(candidate)))
            throw invalid("Credential values cannot be added, changed or removed by the assistant. Use a secure connection form.");
        return new Result(candidate, diffs);
    }

    private JsonNode merge(JsonNode before, JsonNode proposed, List<String> path, boolean restoring) {
        if (proposed.isTextual() && REDACTED.equals(proposed.textValue())) {
            if (before == null) throw invalid("A credential reference is missing.");
            return before.deepCopy();
        }
        if (proposed.isObject()) {
            ObjectNode result = !restoring && before != null && before.isObject() ? before.deepCopy() : mapper.createObjectNode();
            proposed.fields().forEachRemaining(field -> {
                JsonNode previous = before == null ? null : before.get(field.getKey());
                if (credential(path, field.getKey(), field.getValue(), proposed)
                    || credential(path, field.getKey(), field.getValue(), before)) {
                    if (!field.getValue().isTextual() || !REDACTED.equals(field.getValue().textValue()) || previous == null)
                        throw invalid("The assistant cannot supply credential values. Select a stored connection instead.");
                    result.set(field.getKey(), previous.deepCopy());
                } else result.set(field.getKey(), merge(previous, field.getValue(), child(path, field.getKey()), restoring));
            });
            return result;
        }
        if (proposed.isArray()) {
            if (!restoring && !path.isEmpty() && NAMED_COLLECTIONS.contains(path.getLast())) {
                ArrayNode result = before != null && before.isArray() ? before.deepCopy() : mapper.createArrayNode();
                String identity = identity(path.getLast());
                for (var value : proposed) {
                    if (!value.isObject() || !value.path(identity).isTextual() || value.path(identity).asText().isBlank())
                        throw invalid("Named collection items require a stable " + identity + ".");
                    String key = value.path(identity).asText(); int found = -1;
                    for (int i = 0; i < result.size(); i++) if (key.equals(result.get(i).path(identity).asText())) { found = i; break; }
                    if (found < 0) result.add(merge(null, value, path, false));
                    else result.set(found, merge(result.get(found), value, path, false));
                }
                return result;
            }
            var result = mapper.createArrayNode();
            for (int i = 0; i < proposed.size(); i++) result.add(merge(before != null && before.isArray() && before.size() > i ? before.get(i) : null,
                proposed.get(i), child(path, Integer.toString(i)), restoring));
            return result;
        }
        if (containsCredentials(before, path)) throw invalid("Use the secure form for credential-bearing values.");
        return proposed.deepCopy();
    }

    public JsonNode inverse(JsonNode diff) {
        if (!diff.isArray()) throw invalid("No saved change diff is available.");
        var result = mapper.createArrayNode();
        for (int i = diff.size() - 1; i >= 0; i--) {
            var original = diff.get(i); boolean existed = original.path("existed").asBoolean();
            var change = result.addObject().put("path", original.path("path").asText());
            if (original.has("key")) {
                change.set("key", original.get("key")); change.put("operation", existed ? "UPSERT" : "REMOVE_ITEM");
            } else change.put("operation", existed ? "SET" : "REMOVE");
            if (existed) change.set("value", original.get("before"));
        }
        return result;
    }
    private JsonNode item(JsonNode array, String identity, String key) {
        if (array.isArray()) for (var value : array) if (key.equals(value.path(identity).asText())) return value;
        return mapper.getNodeFactory().missingNode();
    }

    private boolean containsCredentials(JsonNode value, List<String> path) {
        if (value == null) return false;
        if (value.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = value.fields();
            while (fields.hasNext()) { var f = fields.next(); if (credential(path, f.getKey(), f.getValue(), value) || containsCredentials(f.getValue(), child(path, f.getKey()))) return true; }
        } else if (value.isArray()) for (JsonNode element : value) if (containsCredentials(element, path)) return true;
        return false;
    }
    private String identity(String field) {
        return switch (field) { case "actions", "params" -> "name"; case "knowledgeSources" -> "sourceKey"; case "datasets" -> "datasetId"; default -> "id"; };
    }
    private List<String> child(List<String> path, String key) { var next = new ArrayList<>(path); next.add(key); return next; }
    private boolean credential(List<String> path, String key, JsonNode value, JsonNode parentNode) {
        String parent = path.isEmpty() ? "" : path.getLast();
        if ("properties".equals(parent) && value != null && value.isObject()
            && (value.has("type") || value.has("$ref") || value.has("properties") || value.has("items"))) return false;
        if (Set.of("defaultValue", "allowedValues", "default", "example", "examples", "enum").contains(key)) {
            boolean declaration = path.size() > 1 && "properties".equals(path.get(path.size() - 2)) && CREDENTIAL_FIELDS.contains(parent);
            if (declaration || parentNode != null && (parentNode.path("sensitive").asBoolean()
                || CREDENTIAL_FIELDS.contains(parentNode.path("name").asText()))) return true;
        }
        if (Set.of("api-key", "apiKey").contains(key) && value != null && value.isObject()) return false;
        if (CREDENTIAL_FIELDS.contains(key)) return true;
        if ("value".equals(key) && Set.of("auth", "api-key", "apiKey", "credentials").contains(parent)) return true;
        return Set.of("headers", "default-headers", "defaultHeaders").contains(parent)
            && !Set.of("content-type", "accept", "user-agent").contains(key.toLowerCase(java.util.Locale.ROOT));
    }
    private Map<String, String> credentials(JsonNode value) {
        var found = new java.util.TreeMap<String, String>(); collectCredentials(value, List.of(), found); return found;
    }
    private void collectCredentials(JsonNode value, List<String> path, Map<String, String> found) {
        if (value == null) return;
        if (value.isObject()) value.fields().forEachRemaining(field -> {
            var next = child(path, field.getKey());
            if (credential(path, field.getKey(), field.getValue(), value)) found.put(String.join("/", next), field.getValue().toString());
            else collectCredentials(field.getValue(), next, found);
        });
        else if (value.isArray()) for (int i = 0; i < value.size(); i++) collectCredentials(value.get(i), child(path, Integer.toString(i)), found);
    }
    private List<String> pointers(String path) {
        var result = new ArrayList<String>();
        for (String part : path.substring(1).split("/", -1)) {
            String decoded = part.replace("~1", "/").replace("~0", "~");
            if (decoded.isBlank()) throw invalid("Empty configuration path component.");
            result.add(decoded);
        }
        return result;
    }
    private String text(JsonNode node, String field) {
        if (!node.path(field).isTextual()) throw invalid("Structured change requires " + field + ".");
        return node.get(field).textValue();
    }
    private ResponseStatusException invalid(String reason) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason); }
    public record Result(ObjectNode candidate, ArrayNode diff) { }
}
