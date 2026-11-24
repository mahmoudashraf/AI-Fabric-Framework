package com.ai.fabric.platform.backend.assistant;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.util.*;

/** Structural catalogue/state observations, not inference about a natural-language request. */
final class AuthoringSourceCoverage {
    private static final java.util.regex.Pattern OBJECT_PARAMETER = java.util.regex.Pattern.compile("\\{\\{\\s*params\\.([A-Za-z_][A-Za-z0-9_]*)\\s*}}");
    private static final java.util.regex.Pattern DOCUMENTED_PATH_PARAMETER = java.util.regex.Pattern.compile("\\{([^{}]+)}");
    private static final String PATH_SLOT = "\u0000";
    private AuthoringSourceCoverage() { }
    static ObjectNode inspect(ObjectMapper mapper, JsonNode content, JsonNode turn, JsonNode configuration, String task) {
        return inspect(mapper, content, turn, configuration, task, mapper.createArrayNode());
    }
    static ObjectNode inspect(ObjectMapper mapper, JsonNode content, JsonNode turn, JsonNode configuration, String task, JsonNode ownedReferences) {
        var references = AuthoringSecretReferences.names(ownedReferences);
        var result = mapper.createObjectNode(); var items = result.putArray("items"); var remaining = result.putArray("remainingOperationIds"); int completed = 0;
        boolean explicitPreparation = "PREPARE".equals(turn.path("command").asText()) && Set.of("ACTIONS", "BRAIN_SETUP").contains(task);
        for (var source : content.path("sources")) {
            if (!contains(turn.path("sourceIds"), source.path("sourceId").asText())) continue;
            String version = AuthoringSourceVersion.of(source);
            for (var operation : source.path("operations")) {
                if (!turn.path("operationIds").isEmpty() && !contains(turn.path("operationIds"), operation.path("id").asText())) continue;
                var item = items.addObject().put("sourceId", source.path("sourceId").asText())
                    .put("sourceVersion", version).put("operationId", operation.path("id").asText());
                item.put("label", !operation.path("method").asText().isBlank() ? operation.path("method").asText() + " " + operation.path("path").asText()
                    : operation.path("toolName").asText(operation.path("id").asText()));
                var candidates = item.putArray("savedCandidates");
                for (var action : configuration.at("/actionsConfig/actions")) {
                    String name = action.path("name").asText();
                    if (name.isBlank()) continue;
                    var route = configuration.at("/routingConfig/actions").path(name);
                    boolean mcp = sameMcp(operation, action, configuration);
                    boolean http = !operation.path("method").asText().isBlank()
                        && !"mcp-tool".equals(action.at("/execution/adapterType").asText())
                        && operation.path("method").asText().equals(route.path("method").asText("POST").trim().toUpperCase(Locale.ROOT)) && sameTarget(operation, route, configuration);
                    if (mcp || http) {
                        var candidate = candidates.addObject().put("actionName", name).put("definitionSaved", true).put("routeSaved", http);
                        var missing = candidate.putArray("missingRequiredMappings");
                        if (http) for (var parameter : operation.path("parameters")) {
                            if (!parameter.path("required").asBoolean()) continue;
                            String location = parameter.path("in").asText();
                            String parameterName = parameter.path("name").asText();
                            boolean mapped = switch (location) {
                                case "header" -> headerMapped(parameterName, route, configuration);
                                case "query" -> route.path("request").path("query").hasNonNull(parameterName);
                                case "path" -> pathMapped(parameterName, operation, route, action);
                                default -> true;
                            };
                            if (!mapped)
                                missing.addObject().put("location", location).put("name", parameter.path("name").asText());
                        }
                        if (http) bodyFindings(operation.path("requestBody"), route.path("request").path("body"), action, missing);
                        if (http) {
                            var security = security(mapper, operation, route, configuration, references);
                            candidate.set("securityCoverage", security);
                            if (!security.path("qualified").asBoolean()) missing.addObject().put("location", "security")
                                .put("name", "$security").put("code", "DOCUMENTED_SECURITY_NOT_QUALIFIED");
                        }
                        if (mcp) for (var required : operation.at("/inputSchema/required")) {
                            boolean present = false;
                            for (var param : action.path("params")) if (required.asText().equals(param.path("name").asText())) present = true;
                            if (!present) missing.addObject().put("location", "mcp-input").put("name", required.asText());
                        }
                    }
                }
                boolean satisfied = false;
                for (var candidate : candidates) if (candidate.path("missingRequiredMappings").isEmpty()) satisfied = true;
                item.put("state", satisfied ? "SATISFIED" : "PENDING");
                if (satisfied) completed++;
                else remaining.addObject().put("sourceId", source.path("sourceId").asText()).put("operationId", operation.path("id").asText());
            }
        }
        result.put("required", explicitPreparation && !items.isEmpty()).put("completed", completed).put("total", items.size())
            .put("complete", completed == items.size()).put("evidence", "CURRENT_SAVED_CONFIGURATION")
            .put("scope", "Formal selected operations only. Structural coverage is not connection testing, publication or semantic qualification.");
        return result;
    }
    private static ObjectNode security(ObjectMapper mapper, JsonNode operation, JsonNode route, JsonNode configuration, Set<String> references) {
        var result = mapper.createObjectNode(); var alternatives = result.putArray("alternatives");
        var requirements = operation.path("security");
        if (requirements.isMissingNode() || requirements.isArray() && requirements.isEmpty())
            return result.put("required", false).put("qualified", true);
        result.put("required", true);
        boolean qualified = false; boolean anonymous = false;
        if (!requirements.isArray()) return result.put("qualified", false).put("code", "SECURITY_REQUIREMENT_UNQUALIFIED");
        for (var requirement : requirements) {
            anonymous |= requirement.isObject() && requirement.isEmpty();
            var alternative = alternatives.addObject(); var schemes = alternative.putArray("schemes");
            boolean satisfied = requirement.isObject();
            if (requirement.isObject()) {
                var fields = requirement.fields();
                while (fields.hasNext()) {
                    var declared = fields.next(); var schema = operation.path("securitySchemes").path(declared.getKey());
                    var item = schemes.addObject().put("schemeId", declared.getKey()).put("type", schema.path("type").asText())
                        .put("in", schema.path("in").asText()).put("name", schema.path("name").asText());
                    boolean supported = "apiKey".equals(schema.path("type").asText()) && "header".equals(schema.path("in").asText())
                        && !schema.path("name").asText().isBlank() && declared.getValue().isArray() && declared.getValue().isEmpty();
                    boolean bound = supported && ownedHeader(schema.path("name").asText(), route, configuration, references);
                    item.put("state", bound ? "SATISFIED" : supported ? "PENDING" : "UNQUALIFIED")
                        .put("code", bound ? "OWNED_STORED_REFERENCE_BOUND" : supported ? "OWNED_SECRET_REFERENCE_REQUIRED" : "SECURITY_SCHEME_UNQUALIFIED");
                    satisfied &= bound;
                }
            }
            alternative.put("qualified", satisfied); qualified |= satisfied;
        }
        return result.put("required", !anonymous).put("qualified", qualified);
    }
    private static boolean ownedHeader(String name, JsonNode route, JsonNode configuration, Set<String> references) {
        var headers = new LinkedHashMap<String, JsonNode>();
        route.path("headers").fields().forEachRemaining(field -> { if (name.equalsIgnoreCase(field.getKey())) headers.put(field.getKey(), field.getValue()); });
        var auth = configuration.at("/routingConfig/connector/upstream/auth");
        if ("API_KEY".equals(auth.path("type").asText()) && name.equalsIgnoreCase(auth.path("header").asText()))
            headers.put(auth.path("header").asText(), auth.path("value"));
        var bindings = new HashSet<String>();
        for (var value : headers.values()) {
            if (!AuthoringSecretReferences.owned(value, references)) return false;
            bindings.add(AuthoringSecretReferences.name(value));
        }
        return bindings.size() == 1;
    }
    private static boolean sameTarget(JsonNode operation, JsonNode route, JsonNode configuration) {
        if (operation.path("path").asText().isBlank()) return false;
        String target = route.path("url").asText();
        if (target.isBlank()) {
            String base = configuration.at("/routingConfig/connector/upstream/base-url").asText();
            if (base.isBlank() || route.path("path").asText().isBlank()) return false;
            target = join(base, OBJECT_PARAMETER.matcher(route.path("path").asText()).replaceAll(PATH_SLOT));
        }
        try {
            var uri = java.net.URI.create(target.replace(PATH_SLOT, "__authoring_path_slot__"));
            if ((!"http".equals(uri.getScheme()) && !"https".equals(uri.getScheme())) || uri.getHost() == null || uri.getUserInfo() != null) return false;
        } catch (IllegalArgumentException invalid) { return false; }
        for (var server : operation.path("servers")) {
            String base = server.path("url").asText();
            var variables = server.path("variables").fields();
            while (variables.hasNext()) { var variable = variables.next(); if (variable.getValue().hasNonNull("default"))
                base = base.replace("{" + variable.getKey() + "}", variable.getValue().path("default").asText()); }
            if (!base.isBlank() && join(base, DOCUMENTED_PATH_PARAMETER.matcher(operation.path("path").asText()).replaceAll(PATH_SLOT)).equals(target)) return true;
        }
        if (operation.path("servers").isEmpty()) {
            String documentedPath = DOCUMENTED_PATH_PARAMETER.matcher(operation.path("path").asText()).replaceAll(PATH_SLOT);
            try { return documentedPath.equals(java.net.URI.create(target.replace(PATH_SLOT, "__authoring_path_slot__")).getPath()
                .replace("__authoring_path_slot__", PATH_SLOT)); }
            catch (IllegalArgumentException invalid) { return false; }
        }
        return false;
    }
    private static boolean pathMapped(String name, JsonNode operation, JsonNode route, JsonNode action) {
        var documented = DOCUMENTED_PATH_PARAMETER.matcher(operation.path("path").asText());
        var nativePath = OBJECT_PARAMETER.matcher(route.path("path").asText());
        while (documented.find()) {
            if (!nativePath.find()) return false;
            if (name.equals(documented.group(1))) {
                for (var parameter : action.path("params")) if (nativePath.group(1).equals(parameter.path("name").asText())) return true;
                return false;
            }
        }
        return false;
    }
    private static boolean headerMapped(String name, JsonNode route, JsonNode configuration) {
        var headers = route.path("headers").fields();
        while (headers.hasNext()) { var header = headers.next(); if (name.equalsIgnoreCase(header.getKey()) && !header.getValue().isNull()) return true; }
        var auth = configuration.at("/routingConfig/connector/upstream/auth");
        return "API_KEY".equals(auth.path("type").asText()) && name.equalsIgnoreCase(auth.path("header").asText()) && !auth.path("value").asText().isBlank()
            || name.equalsIgnoreCase(route.path("idempotencyHeader").asText());
    }
    private static String join(String base, String path) { return (base.endsWith("/") ? base.substring(0, base.length() - 1) : base) + path; }
    private static boolean sameMcp(JsonNode operation, JsonNode action, JsonNode configuration) {
        var target = action.at("/execution/mcp");
        if (!"mcp-tool".equals(action.at("/execution/adapterType").asText())) return false;
        if (operation.path("toolName").asText().isBlank() || !operation.path("toolName").asText().equals(target.path("toolName").asText())) return false;
        boolean identified = false;
        for (String field : List.of("connectionId", "serverRef")) if (!operation.path(field).asText().isBlank()) {
            identified = true; if (!operation.path(field).asText().equals(target.path(field).asText())) return false;
        }
        if (!identified || target.path("serverRef").asText().isBlank()) return false;
        for (var server : configuration.at("/actionsConfig/mcpServers"))
            if (target.path("serverRef").asText().equals(server.path("serverRef").asText())
                && target.path("connectionId").asText().equals(server.path("connectionId").asText())) return true;
        return false;
    }
    private static void bodyFindings(JsonNode requestBody, JsonNode mapped, JsonNode action, ArrayNode missing) {
        if (requestBody.path("required").asBoolean() && (mapped.isMissingNode() || mapped.isNull()))
            missing.addObject().put("location", "body").put("name", "$body");
        if (mapped.isMissingNode() || mapped.isNull()) return;
        JsonNode content = requestBody.path("content");
        JsonNode media = content.path("application/json");
        if (media.isMissingNode() && content.isObject() && content.size() == 1) media = content.elements().next();
        requiredFields(media.path("schema"), mapped, action, "", missing);
    }
    private static JsonNode mappedParameter(JsonNode mapped, JsonNode action) {
        var reference = OBJECT_PARAMETER.matcher(mapped.asText());
        if (mapped.isTextual() && reference.matches()) for (var param : action.path("params"))
            if (reference.group(1).equals(param.path("name").asText())) return param;
        return null;
    }
    private static void requiredParameterFields(JsonNode schema, JsonNode parameter, String prefix, ArrayNode missing) {
        for (var required : schema.path("required")) {
            String name = required.asText();
            var property = parameter == null ? null : parameter.path("properties").get(name);
            if (property == null || !contains(parameter.path("requiredProperties"), name))
                missing.addObject().put("location", "body").put("name", prefix + name);
            else requiredParameterFields(schema.path("properties").path(name), property, prefix + name + ".", missing);
        }
    }
    private static void requiredFields(JsonNode schema, JsonNode mapped, JsonNode action, String prefix, ArrayNode missing) {
        if (!mapped.isObject()) {
            requiredParameterFields(schema, mappedParameter(mapped, action), prefix, missing);
            return;
        }
        for (var required : schema.path("required")) {
            String name = required.asText();
            if (!mapped.hasNonNull(name)) missing.addObject().put("location", "body").put("name", prefix + name);
        }
        schema.path("properties").fields().forEachRemaining(field -> {
            if (mapped.hasNonNull(field.getKey())) requiredFields(field.getValue(), mapped.get(field.getKey()), action, prefix + field.getKey() + ".", missing);
        });
    }
    private static boolean contains(JsonNode ids, String id) { for (var value : ids) if (id.equals(value.asText())) return true; return false; }
}
