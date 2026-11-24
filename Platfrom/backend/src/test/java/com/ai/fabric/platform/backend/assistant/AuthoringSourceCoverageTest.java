package com.ai.fabric.platform.backend.assistant;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AuthoringSourceCoverageTest {
    final ObjectMapper mapper = new ObjectMapper();
    @Test void coverageIsCurrentStateBasedSupportsNoOpAndDoesNotForceNarrowRequestToAllSources() throws Exception {
        JsonNode content = mapper.readTree("""
            {"sources":[{"sourceId":"src-doc","operations":[
              {"id":"products","method":"GET","path":"/products","servers":[{"url":"https://api.example.test/v1"}],
               "parameters":[{"in":"query","name":"category","required":true}]},
              {"id":"enquiry","method":"POST","path":"/enquiries","servers":[{"url":"https://api.example.test/v1"}]}]}]}
            """);
        ObjectNode turn = (ObjectNode) mapper.readTree("{\"command\":\"PREPARE\",\"sourceIds\":[\"src-doc\"],\"operationIds\":[\"products\"]}");
        ObjectNode configuration = (ObjectNode) mapper.readTree("""
            {"actionsConfig":{"actions":[{"name":"find_products","unknownExtension":{"preserved":true}}]},
             "routingConfig":{"actions":{"find_products":{"method":"GET","url":"https://api.example.test/v1/products",
              "request":{"query":{"category":"{{params.category}}"}}}}}}
            """);
        var complete = AuthoringSourceCoverage.inspect(mapper, content, turn, configuration, "ACTIONS");
        assertThat(complete.path("required").asBoolean()).isTrue(); assertThat(complete.path("complete").asBoolean()).isTrue();
        assertThat(complete.path("total").asInt()).isEqualTo(1); assertThat(complete.at("/items/0/savedCandidates/0/actionName").asText()).isEqualTo("find_products");
        ((ObjectNode) configuration.at("/routingConfig/actions/find_products/request/query")).remove("category");
        assertThat(AuthoringSourceCoverage.inspect(mapper, content, turn, configuration, "ACTIONS").path("complete").asBoolean()).isFalse();
        turn.put("command", "");
        assertThat(AuthoringSourceCoverage.inspect(mapper, content, turn, configuration, "ACTIONS").path("required").asBoolean()).isFalse();
    }
    @Test void relativeRoutesNeedTheDocumentedHostAndRequiredRequestBodyMappings() throws Exception {
        var content = mapper.readTree("""
            {"sources":[{"sourceId":"src","operations":[{"id":"submit","method":"POST","path":"/orders",
              "servers":[{"url":"https://expected.example.test/v1"}],"requestBody":{"required":true,
               "content":{"application/json":{"schema":{"type":"object","required":["customer"],
                "properties":{"customer":{"type":"object","required":["email"]}}}}}}}]}]}
            """);
        var turn = mapper.readTree("{\"command\":\"PREPARE\",\"sourceIds\":[\"src\"],\"operationIds\":[]}");
        ObjectNode configuration = (ObjectNode) mapper.readTree("""
            {"actionsConfig":{"actions":[{"name":"submit"}]},"routingConfig":{"connector":{"upstream":{"base-url":"https://wrong.example.test/v1"}},
             "actions":{"submit":{"method":"POST","path":"/orders","request":{}}}}}
            """);
        assertThat(AuthoringSourceCoverage.inspect(mapper, content, turn, configuration, "ACTIONS").path("complete").asBoolean()).isFalse();
        configuration.withObject("/routingConfig/connector/upstream").put("base-url", "https://expected.example.test/v1");
        var missingBody = AuthoringSourceCoverage.inspect(mapper, content, turn, configuration, "ACTIONS");
        assertThat(missingBody.path("complete").asBoolean()).isFalse();
        assertThat(missingBody.at("/items/0/savedCandidates/0/missingRequiredMappings/0/name").asText()).isEqualTo("$body");
        configuration.withObject("/routingConfig/actions/submit/request").putObject("body").putObject("customer");
        assertThat(AuthoringSourceCoverage.inspect(mapper, content, turn, configuration, "ACTIONS").path("complete").asBoolean()).isFalse();
        configuration.withObject("/routingConfig/actions/submit/request/body/customer").put("email", "{{params.email}}");
        assertThat(AuthoringSourceCoverage.inspect(mapper, content, turn, configuration, "ACTIONS").path("complete").asBoolean()).isTrue();
        configuration.withObject("/routingConfig/actions/submit/request/body").put("customer", "{{params.customer}}");
        var unprovenNested = AuthoringSourceCoverage.inspect(mapper, content, turn, configuration, "ACTIONS");
        assertThat(unprovenNested.path("complete").asBoolean()).isFalse();
        assertThat(unprovenNested.at("/items/0/savedCandidates/0/missingRequiredMappings/0/name").asText()).isEqualTo("customer.email");
        var nested = ((ObjectNode) configuration.at("/actionsConfig/actions/0")).putArray("params").addObject().put("name", "customer").put("type", "OBJECT");
        nested.putArray("requiredProperties").add("email"); nested.putObject("properties").putObject("email").put("type", "STRING");
        assertThat(AuthoringSourceCoverage.inspect(mapper, content, turn, configuration, "ACTIONS").path("complete").asBoolean()).isTrue();
        configuration.withObject("/routingConfig/actions/submit/request").put("body", "{{params.payload}}");
        assertThat(AuthoringSourceCoverage.inspect(mapper, content, turn, configuration, "ACTIONS").path("complete").asBoolean()).isFalse();
        var payload = ((ObjectNode) configuration.at("/actionsConfig/actions/0")).putArray("params").addObject().put("name", "payload").put("type", "OBJECT");
        payload.putArray("requiredProperties").add("customer");
        var customer = payload.putObject("properties").putObject("customer").put("type", "OBJECT");
        customer.putArray("requiredProperties").add("email"); customer.putObject("properties").putObject("email").put("type", "STRING");
        assertThat(AuthoringSourceCoverage.inspect(mapper, content, turn, configuration, "ACTIONS").path("complete").asBoolean()).isTrue();
    }
    @Test void matchingMcpToolNameDoesNotProveConnectionServerOrRequiredInputIdentity() throws Exception {
        var content = mapper.readTree("""
            {"sources":[{"sourceId":"src","operations":[{"id":"list","kind":"MCP_TOOL","toolName":"list",
             "connectionId":"connection-good","serverRef":"server-good","inputSchema":{"required":["category"]}}]}]}
            """);
        var turn = mapper.readTree("{\"command\":\"PREPARE\",\"sourceIds\":[\"src\"],\"operationIds\":[]}");
        ObjectNode configuration = (ObjectNode) mapper.readTree("""
            {"actionsConfig":{"actions":[{"name":"list","params":[],"execution":{"adapterType":"mcp-tool","mcp":{"toolName":"list","connectionId":"connection-other","serverRef":"server-good"}}}],
              "mcpServers":[{"connectionId":"connection-other","serverRef":"server-good"}]}}
            """);
        assertThat(AuthoringSourceCoverage.inspect(mapper, content, turn, configuration, "ACTIONS").path("complete").asBoolean()).isFalse();
        ((ObjectNode) configuration.at("/actionsConfig/actions/0/execution/mcp")).put("connectionId", "connection-good");
        assertThat(AuthoringSourceCoverage.inspect(mapper, content, turn, configuration, "ACTIONS").path("complete").asBoolean()).isFalse();
        ((ObjectNode) configuration.at("/actionsConfig/mcpServers/0")).put("connectionId", "connection-good");
        assertThat(AuthoringSourceCoverage.inspect(mapper, content, turn, configuration, "ACTIONS").path("complete").asBoolean()).isFalse();
        ((com.fasterxml.jackson.databind.node.ArrayNode) configuration.at("/actionsConfig/actions/0/params")).addObject().put("name", "category").put("required", true);
        assertThat(AuthoringSourceCoverage.inspect(mapper, content, turn, configuration, "ACTIONS").path("complete").asBoolean()).isTrue();
    }
    @Test void pathVariablesAndHeadersUseNativeConnectorMappingsRatherThanOpenApiPlaceholders() throws Exception {
        var content = mapper.readTree("""
            {"sources":[{"sourceId":"src","operations":[{"id":"details","method":"GET","path":"/products/{productId}",
             "servers":[{"url":"https://api.example.test/v1"}],"parameters":[
              {"name":"productId","in":"path","required":true},{"name":"X-Request-Id","in":"header","required":true}]}]}]}
            """);
        var turn = mapper.readTree("{\"command\":\"PREPARE\",\"sourceIds\":[\"src\"],\"operationIds\":[]}");
        ObjectNode configuration = (ObjectNode) mapper.readTree("""
            {"actionsConfig":{"actions":[{"name":"details","params":[{"name":"itemId","type":"STRING","required":true}]}]},
             "routingConfig":{"connector":{"upstream":{"base-url":"https://api.example.test/v1"}},"actions":{"details":{
              "method":"GET","path":"/products/{productId}","request":{"path":{"productId":"{{params.itemId}}"},
              "headers":{"X-Request-Id":"{{params.requestId}}"}}}}}}
            """);
        assertThat(AuthoringSourceCoverage.inspect(mapper, content, turn, configuration, "ACTIONS").path("complete").asBoolean()).isFalse();
        configuration.withObject("/routingConfig/actions/details").put("path", "/products/{{params.itemId}}");
        var missingHeader = AuthoringSourceCoverage.inspect(mapper, content, turn, configuration, "ACTIONS");
        assertThat(missingHeader.path("complete").asBoolean()).isFalse();
        assertThat(missingHeader.at("/items/0/savedCandidates/0/missingRequiredMappings/0/name").asText()).isEqualTo("X-Request-Id");
        configuration.withObject("/routingConfig/actions/details").putObject("headers").put("x-request-id", "{{params.requestId}}");
        assertThat(AuthoringSourceCoverage.inspect(mapper, content, turn, configuration, "ACTIONS").path("complete").asBoolean()).isTrue();
        ((ObjectNode) configuration.at("/actionsConfig/actions/0")).putArray("params");
        assertThat(AuthoringSourceCoverage.inspect(mapper, content, turn, configuration, "ACTIONS").path("complete").asBoolean()).isFalse();
        configuration.withObject("/routingConfig/connector/upstream").put("base-url", "https://wrong.example.test/v1");
        assertThat(AuthoringSourceCoverage.inspect(mapper, content, turn, configuration, "ACTIONS").path("complete").asBoolean()).isFalse();
    }
    @Test void documentedApiKeyHeadersRequireOwnedBindingsAndEvaluateOrAlternativesWithoutGuessingLogin() throws Exception {
        ObjectNode content = (ObjectNode) mapper.readTree("""
            {"sources":[{"sourceId":"src","operations":[{"id":"private","method":"GET","path":"/private",
             "servers":[{"url":"https://api.example.test"}],"security":[{"LabKey":[]}],
             "securitySchemes":{"LabKey":{"type":"apiKey","in":"header","name":"X-Lab-Key"},
              "OAuth":{"type":"oauth2","flows":{"clientCredentials":{}}}}}]}]}
            """);
        var turn = mapper.readTree("{\"command\":\"PREPARE\",\"sourceIds\":[\"src\"],\"operationIds\":[]}");
        ObjectNode configuration = (ObjectNode) mapper.readTree("""
            {"actionsConfig":{"actions":[{"name":"private"}]},"routingConfig":{"actions":{"private":{
             "method":"GET","url":"https://api.example.test/private"}}}}
            """);
        var refs = mapper.createArrayNode().add("OWNED_API_KEY");
        assertThat(AuthoringSourceCoverage.inspect(mapper, content, turn, configuration, "ACTIONS", refs).path("complete").asBoolean()).isFalse();
        var route = configuration.withObject("/routingConfig/actions/private");
        for (String value : java.util.List.of("literal-private-key", "${FOREIGN_KEY}")) {
            route.putObject("headers").put("X-Lab-Key", value);
            var coverage = AuthoringSourceCoverage.inspect(mapper, content, turn, configuration, "ACTIONS", refs);
            assertThat(coverage.path("complete").asBoolean()).isFalse();
            assertThat(coverage.toString()).doesNotContain(value);
        }
        route.remove("headers"); route.putObject("request").putObject("headers").put("X-Lab-Key", "${OWNED_API_KEY}");
        assertThat(AuthoringSourceCoverage.inspect(mapper, content, turn, configuration, "ACTIONS", refs).path("complete").asBoolean()).isFalse();
        route.putObject("headers").put("x-lab-key", "${OWNED_API_KEY}");
        assertThat(AuthoringSourceCoverage.inspect(mapper, content, turn, configuration, "ACTIONS", refs).path("complete").asBoolean()).isTrue();
        assertThat(AuthoringSourceCoverage.inspect(mapper, content, turn, configuration, "ACTIONS").path("complete").asBoolean()).isFalse();
        route.remove("headers");
        configuration.withObject("/routingConfig/connector/upstream").putObject("auth").put("type", "API_KEY").put("header", "X-Lab-Key").put("value", "${OWNED_API_KEY}");
        assertThat(AuthoringSourceCoverage.inspect(mapper, content, turn, configuration, "ACTIONS", refs).path("complete").asBoolean()).isTrue();
        var operation = (ObjectNode) content.at("/sources/0/operations/0");
        operation.set("security", mapper.readTree("[{\"OAuth\":[]},{\"LabKey\":[]}]"));
        var alternative = AuthoringSourceCoverage.inspect(mapper, content, turn, configuration, "ACTIONS", refs);
        assertThat(alternative.path("complete").asBoolean()).isTrue();
        assertThat(alternative.at("/items/0/savedCandidates/0/securityCoverage/alternatives/0/schemes/0/state").asText()).isEqualTo("UNQUALIFIED");
        operation.set("security", mapper.readTree("[{\"OAuth\":[],\"LabKey\":[]}]"));
        assertThat(AuthoringSourceCoverage.inspect(mapper, content, turn, configuration, "ACTIONS", refs).path("complete").asBoolean()).isFalse();
        operation.set("security", mapper.readTree("[{}, {\"OAuth\":[]}]"));
        var anonymous = AuthoringSourceCoverage.inspect(mapper, content, turn, configuration, "ACTIONS", refs);
        assertThat(anonymous.path("complete").asBoolean()).isTrue();
        assertThat(anonymous.at("/items/0/savedCandidates/0/securityCoverage/required").asBoolean()).isFalse();
        operation.putArray("security");
        assertThat(AuthoringSourceCoverage.inspect(mapper, content, turn, configuration, "ACTIONS", refs).path("complete").asBoolean()).isTrue();
    }
}
