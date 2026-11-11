package com.ai.fabric.platform.backend.mcp.connection;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.networknt.schema.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class McpToolCatalogue {
    private final ObjectMapper mapper;
    private final McpConnectionProperties properties;
    public McpToolCatalogue(ObjectMapper mapper, McpConnectionProperties properties) { this.mapper = mapper; this.properties = properties; }

    List<McpConnectionContracts.Tool> read(JsonNode discovery) {
        if (!discovery.path("tools").isArray() || discovery.path("tools").size() > properties.getMaxTools())
            throw new IllegalArgumentException("MCP discovery did not return a bounded tool catalogue.");
        var result = new ArrayList<McpConnectionContracts.Tool>();
        var names = new HashSet<String>();
        for (JsonNode tool : discovery.path("tools")) {
            String name = tool.path("name").asText();
            if (name.isBlank() || name.length() > 128 || !names.add(name))
                throw new IllegalArgumentException("MCP discovery returned a missing, duplicate or oversized tool name.");
            var findings = new ArrayList<String>();
            JsonNode input = tool.path("inputSchema");
            JsonNode output = tool.get("outputSchema");
            schemaFindings(input, "inputSchema", true, findings);
            if (output != null && !output.isNull()) schemaFindings(output, "outputSchema", false, findings);
            if ("required".equals(tool.path("execution").path("taskSupport").asText()))
                findings.add("This tool requires MCP task execution, which is not supported by synchronous actions.");
            String schemaHash = tool.path("schemaHash").asText();
            if (schemaHash.isBlank() || schemaHash.length() > 128) findings.add("The gateway did not provide a usable schema hash.");
            if (tool.has("compatible") && !tool.path("compatible").asBoolean()) {
                findings.add("The gateway marked this tool incompatible with the qualified execution contract.");
                for (JsonNode finding : tool.path("findings")) if (finding.isTextual()) findings.add(bounded(finding.asText(), 2000));
            }
            result.add(new McpConnectionContracts.Tool(name, bounded(tool.path("title").asText(null), 512),
                bounded(tool.path("description").asText(null), 16_384), input.deepCopy(),
                output == null || output.isNull() ? null : output.deepCopy(),
                tool.path("annotations").isObject() ? tool.path("annotations").deepCopy() : null,
                tool.path("execution").isObject() ? tool.path("execution").deepCopy() : null,
                schemaHash, findings.isEmpty(), List.copyOf(findings)));
        }
        return List.copyOf(result);
    }

    private void schemaFindings(JsonNode schema, String field, boolean input, List<String> findings) {
        try {
            if (!schema.isObject() || schema.toString().length() > 262144)
                throw new IllegalArgumentException();
            if (input && schema.has("type") && !("object".equals(schema.path("type").asText())
                || schema.path("type").isArray() && java.util.stream.StreamSupport.stream(schema.path("type").spliterator(), false)
                    .anyMatch(t -> "object".equals(t.asText())))) throw new IllegalArgumentException();
            safeReferences(schema, 0);
            SpecificationVersion version = switch (schema.path("$schema").asText("https://json-schema.org/draft/2020-12/schema")) {
                case "https://json-schema.org/draft/2020-12/schema" -> SpecificationVersion.DRAFT_2020_12;
                case "http://json-schema.org/draft-07/schema#", "http://json-schema.org/draft-07/schema",
                     "https://json-schema.org/draft-07/schema#", "https://json-schema.org/draft-07/schema" -> SpecificationVersion.DRAFT_7;
                default -> throw new IllegalArgumentException();
            };
            var registry = SchemaRegistry.withDefaultDialect(version, builder -> builder.resourceLoaders(loaders -> loaders.values(values -> {
                values.clear(); values.add(iri -> { throw new IllegalArgumentException("External schema resources are disabled."); });
            })));
            if (!registry.getSchema(SchemaLocation.of(version.getDialectId())).validate(schema, OutputFormat.BOOLEAN)) throw new IllegalArgumentException();
            registry.getSchema(schema).initializeValidators();
        } catch (RuntimeException invalid) {
            findings.add(field + " must be a valid, bounded self-contained JSON schema with local references only. Input must accept an argument object.");
        }
    }

    private void safeReferences(JsonNode node, int depth) {
        if (depth > 64) throw new IllegalArgumentException();
        if (node.isObject()) {
            String dialect = node.path("$schema").asText("");
            if (!dialect.isEmpty() && !Set.of("https://json-schema.org/draft/2020-12/schema", "http://json-schema.org/draft-07/schema#",
                "https://json-schema.org/draft-07/schema#", "https://json-schema.org/draft-07/schema", "http://json-schema.org/draft-07/schema").contains(dialect)) throw new IllegalArgumentException();
            for (String key : List.of("$ref", "$dynamicRef", "$recursiveRef"))
                if (node.has(key) && (!node.path(key).isTextual() || !node.path(key).asText().startsWith("#"))) throw new IllegalArgumentException();
            node.path("$vocabulary").fields().forEachRemaining(entry -> {
                if (entry.getValue().asBoolean(false) && !Set.of("https://json-schema.org/draft/2020-12/vocab/core",
                    "https://json-schema.org/draft/2020-12/vocab/applicator", "https://json-schema.org/draft/2020-12/vocab/unevaluated",
                    "https://json-schema.org/draft/2020-12/vocab/validation", "https://json-schema.org/draft/2020-12/vocab/meta-data",
                    "https://json-schema.org/draft/2020-12/vocab/format-annotation", "https://json-schema.org/draft/2020-12/vocab/content").contains(entry.getKey()))
                    throw new IllegalArgumentException();
            });
        }
        for (String keyword : List.of("properties", "patternProperties", "$defs", "definitions", "dependentSchemas"))
            node.path(keyword).forEach(child -> safeReferences(child, depth + 1));
        for (String keyword : List.of("allOf", "anyOf", "oneOf", "prefixItems")) node.path(keyword).forEach(child -> safeReferences(child, depth + 1));
        for (String keyword : List.of("items", "additionalItems", "additionalProperties", "unevaluatedProperties", "unevaluatedItems",
            "contains", "propertyNames", "not", "if", "then", "else", "contentSchema")) {
            JsonNode child = node.path(keyword);
            if (child.isArray()) child.forEach(item -> safeReferences(item, depth + 1));
            else if (child.isObject()) safeReferences(child, depth + 1);
        }
        node.path("dependencies").forEach(child -> { if (child.isObject()) safeReferences(child, depth + 1); });
    }

    ArrayNode parameters(JsonNode schema) {
        var result = mapper.createArrayNode();
        if (!schema.path("properties").isObject()) return result;
        Set<String> required = new HashSet<>();
        schema.path("required").forEach(n -> required.add(n.asText()));
        schema.path("properties").fields().forEachRemaining(entry -> {
            String type = entry.getValue().path("type").asText();
            if (Set.of("string", "integer", "number", "boolean", "array", "object").contains(type)) {
                var param = result.addObject().put("name", entry.getKey()).put("type", type)
                    .put("required", required.contains(entry.getKey()));
                if (entry.getValue().path("description").isTextual()) param.set("description", entry.getValue().get("description"));
            }
        });
        return result;
    }

    static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception unavailable) { throw new IllegalStateException(unavailable); }
    }
    private static String bounded(String value, int max) {
        if (value == null) return null;
        if (value.length() > max) throw new IllegalArgumentException("MCP metadata exceeded its field size limit.");
        return value;
    }
}
