package com.ai.fabric.product.mcp.gateway.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.Schema;
import com.networknt.schema.OutputFormat;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;

import java.util.List;
import java.util.Set;

final class McpNativeSchemaValidator {
    private static final Set<String> MAP_KEYWORDS = Set.of(
        "properties", "patternProperties", "$defs", "definitions", "dependentSchemas"
    );
    private static final Set<String> SCHEMA_KEYWORDS = Set.of(
        "items", "additionalItems", "additionalProperties", "unevaluatedProperties", "unevaluatedItems",
        "contains", "propertyNames", "not", "if", "then", "else", "contentSchema"
    );
    private static final Set<String> ARRAY_KEYWORDS = Set.of("allOf", "anyOf", "oneOf", "prefixItems");
    private static final Set<String> SUPPORTED_VOCABULARIES = Set.of(
        "https://json-schema.org/draft/2020-12/vocab/core",
        "https://json-schema.org/draft/2020-12/vocab/applicator",
        "https://json-schema.org/draft/2020-12/vocab/unevaluated",
        "https://json-schema.org/draft/2020-12/vocab/validation",
        "https://json-schema.org/draft/2020-12/vocab/meta-data",
        "https://json-schema.org/draft/2020-12/vocab/format-annotation",
        "https://json-schema.org/draft/2020-12/vocab/content"
    );

    void validate(JsonNode schema, JsonNode value, String contract) {
        Schema compiled = compile(schema);
        try {
            if (!compiled.validate(value, OutputFormat.BOOLEAN)) {
                // Do not echo schema messages containing confidential argument or result values.
                throw new ContractException(contract, "MCP " + contract + " does not match its native JSON schema.");
            }
        } catch (ContractException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw new ContractException("schema", "MCP native schema could not be evaluated safely.");
        }
    }

    Schema compile(JsonNode schema) {
        try {
            if (schema == null || (!schema.isObject() && !schema.isBoolean())) {
                throw new IllegalArgumentException();
            }
            safeSchema(schema, 0);
            String dialect = schema.path("$schema").asText("https://json-schema.org/draft/2020-12/schema");
            SpecificationVersion version = switch (dialect) {
                case "https://json-schema.org/draft/2020-12/schema" -> SpecificationVersion.DRAFT_2020_12;
                case "http://json-schema.org/draft-07/schema#", "http://json-schema.org/draft-07/schema",
                     "https://json-schema.org/draft-07/schema#", "https://json-schema.org/draft-07/schema" -> SpecificationVersion.DRAFT_7;
                default -> throw new IllegalArgumentException();
            };
            SchemaRegistry registry = SchemaRegistry.withDefaultDialect(version, builder -> builder.resourceLoaders(
                loaders -> loaders.values(values -> {
                    values.clear();
                    values.add(absoluteIri -> { throw new IllegalArgumentException("External schema resources are disabled."); });
                })
            ));
            if (!registry.getSchema(SchemaLocation.of(version.getDialectId())).validate(schema, OutputFormat.BOOLEAN)) {
                throw new IllegalArgumentException();
            }
            Schema compiled = registry.getSchema(schema);
            compiled.initializeValidators();
            return compiled;
        } catch (RuntimeException failure) {
            throw new ContractException("schema", "MCP native schema is invalid or requires unsupported external resources.");
        }
    }

    private void safeSchema(JsonNode node, int depth) {
        if (depth > 64) {
            throw new IllegalArgumentException();
        }
        if (!node.isObject()) {
            return;
        }
        for (String keyword : List.of("$ref", "$dynamicRef", "$recursiveRef")) {
            JsonNode reference = node.get(keyword);
            if (reference != null && (!reference.isTextual() || !reference.asText().startsWith("#"))) {
                throw new IllegalArgumentException();
            }
        }
        node.path("$vocabulary").fields().forEachRemaining(entry -> {
            if (entry.getValue().asBoolean(false) && !SUPPORTED_VOCABULARIES.contains(entry.getKey())) {
                throw new IllegalArgumentException();
            }
        });
        for (String keyword : MAP_KEYWORDS) {
            node.path(keyword).forEach(child -> safeSchema(child, depth + 1));
        }
        for (String keyword : SCHEMA_KEYWORDS) {
            JsonNode child = node.path(keyword);
            if (child.isArray()) {
                child.forEach(item -> safeSchema(item, depth + 1));
            } else {
                safeSchema(child, depth + 1);
            }
        }
        for (String keyword : ARRAY_KEYWORDS) {
            node.path(keyword).forEach(child -> safeSchema(child, depth + 1));
        }
        // Draft-07 dependencies may contain either schemas or lists of property names.
        node.path("dependencies").forEach(child -> { if (child.isObject()) safeSchema(child, depth + 1); });
    }

    static final class ContractException extends IllegalArgumentException {
        private final String contract;

        ContractException(String contract, String message) {
            super(message);
            this.contract = contract;
        }

        String contract() {
            return contract;
        }
    }
}
