package com.ai.fabric.platform.backend.mcp.connection;

import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

class McpToolCatalogueTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final McpToolCatalogue catalogue = new McpToolCatalogue(mapper, new McpConnectionProperties());
    @Test void nativeSchemasWithoutRootTypeAndNonObjectOutputAreNotRejected() throws Exception {
        var discovery = mapper.readTree("""
            {"tools":[{"name":"lookup","schemaHash":"h","inputSchema":{"properties":{"q":{"type":["string","null"]}}},
             "outputSchema":{"type":"array","items":{"type":"number","minimum":0.5}}},
             {"name":"empty","schemaHash":"e","inputSchema":{},"outputSchema":{}}]}
            """);
        var tools = catalogue.read(discovery);
        assertThat(tools).allMatch(McpConnectionContracts.Tool::compatible);
        assertThat(tools.getFirst().inputSchema()).isEqualTo(discovery.path("tools").get(0).get("inputSchema"));
    }
    @Test void localReferenceAndUnionConstraintsArePreservedExactly() throws Exception {
        var discovery = mapper.readTree("""
            {"tools":[{"name":"lookup","schemaHash":"h","inputSchema":{"type":"object","$defs":{"Choice":{"oneOf":[{"type":"number","enum":[1,2.5]},{"type":"null"}]}},
             "properties":{"choice":{"$ref":"#/$defs/Choice"}},"additionalProperties":false}}]}
            """);
        var tool = catalogue.read(discovery).getFirst();
        assertThat(tool.compatible()).isTrue(); assertThat(tool.inputSchema()).isEqualTo(discovery.path("tools").get(0).get("inputSchema"));
    }
    @Test void externalReferenceTaskRequiredAndExplicitNonObjectInputRemainVisibleIncompatible() throws Exception {
        var discovery = mapper.readTree("""
            {"tools":[{"name":"external","schemaHash":"h","inputSchema":{"type":"object","properties":{"q":{"$ref":"https://private.example/schema"}}}},
             {"name":"task","schemaHash":"h","inputSchema":{},"execution":{"taskSupport":"required"}},
             {"name":"string","schemaHash":"h","inputSchema":{"type":"string"}}]}
            """);
        var tools = catalogue.read(discovery);
        assertThat(tools).allMatch(t -> !t.compatible()); assertThat(tools).allMatch(t -> !t.findings().isEmpty());
    }
    @Test void duplicateNamesAndCataloguesOverTheBoundAreRejectedBeforeImport() throws Exception {
        var duplicate = mapper.readTree("{\"tools\":[{\"name\":\"x\",\"inputSchema\":{},\"schemaHash\":\"h\"},{\"name\":\"x\",\"inputSchema\":{},\"schemaHash\":\"h\"}]}");
        assertThatThrownBy(() -> catalogue.read(duplicate)).isInstanceOf(IllegalArgumentException.class);
        var properties = new McpConnectionProperties(); properties.setMaxTools(1);
        assertThatThrownBy(() -> new McpToolCatalogue(mapper, properties).read(duplicate)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void annotationHintsNeverBecomeAccessPolicyAndTaskOptionalStaysMetadata() throws Exception {
        var discovery = mapper.readTree("""
            {"tools":[{"name":"read","schemaHash":"h","inputSchema":{},"annotations":{"readOnlyHint":true,"destructiveHint":false},
             "execution":{"taskSupport":"optional"}}]}
            """);
        var tool = catalogue.read(discovery).getFirst();
        assertThat(tool.compatible()).isTrue(); assertThat(tool.execution().path("taskSupport").asText()).isEqualTo("optional");
        assertThat(mapper.valueToTree(tool).has("accessMode")).isFalse();
    }
    @Test void literalDefaultsAndPropertiesNamedLikeSchemaKeywordsAreNotMisclassifiedAsReferences() throws Exception {
        var discovery = mapper.readTree("""
            {"tools":[{"name":"literal","schemaHash":"h","inputSchema":{"type":"object","properties":{"$ref":{"type":"string"},"payload":{"type":"object",
              "default":{"$ref":"https://this-is-literal-data.example","type":"not-a-schema-type"}}},
              "examples":[{"$ref":"file:///literal-example"}]}}]}
            """);
        assertThat(catalogue.read(discovery).getFirst().compatible()).isTrue();
    }
}
