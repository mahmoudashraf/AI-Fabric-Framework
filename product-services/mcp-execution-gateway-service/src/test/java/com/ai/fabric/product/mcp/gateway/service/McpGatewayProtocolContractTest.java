package com.ai.fabric.product.mcp.gateway.service;

import com.ai.fabric.product.mcp.gateway.client.McpStreamableHttpClient;
import com.ai.fabric.product.mcp.gateway.config.McpGatewayProperties;
import com.ai.fabric.product.mcp.gateway.model.McpGatewayContracts.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class McpGatewayProtocolContractTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final McpGatewayProperties properties = properties(500, 100, 8 * 1024 * 1024, Duration.ofSeconds(60));
    private final McpStreamableHttpClient client = mock(McpStreamableHttpClient.class);
    private final McpGatewayExecutionService service = new McpGatewayExecutionService(client, mapper, properties);
    private final McpStreamableHttpClient.McpSession session = new McpStreamableHttpClient.McpSession(
        URI.create("https://example.com/mcp"), "2025-11-25", "session", mapper.createObjectNode());

    @Test
    void discoveryKeepsNativeSchemasAnnotationsAndTaskRequirementsAcrossPages() throws Exception {
        when(client.initialize(any(), any())).thenReturn(session);
        JsonNode first = mapper.readTree("""
            {"tools":[{"name":"Search.Doc","inputSchema":{"type":"object","$defs":{"q":{"type":["string","null"]}},
              "properties":{"query":{"$ref":"#/$defs/q"}}},
              "outputSchema":{"type":"object","additionalProperties":{"type":"number"}},
              "annotations":{"readOnlyHint":true,"futureHint":"preserve"},"execution":{"taskSupport":"optional"}}],
             "nextCursor":" opaque cursor "}
            """);
        JsonNode second = mapper.readTree("""
            {"tools":[{"name":"Start.Job","inputSchema":{"oneOf":[{"type":"object"},{"type":"null"}]},
              "execution":{"taskSupport":"required"}}]}
            """);
        when(client.toolsList(eq(session), isNull(), any(), any(), any())).thenReturn(first);
        when(client.toolsList(eq(session), eq(" opaque cursor "), any(), any(), any())).thenReturn(second);

        DiscoveryResponse response = discover(service);

        assertThat(response.ready()).isTrue();
        assertThat(response.tools()).extracting(McpToolSummary::name).containsExactly("Search.Doc", "Start.Job");
        assertThat(response.tools().get(0).inputSchema()).isEqualTo(first.path("tools").get(0).path("inputSchema"));
        assertThat(response.tools().get(0).outputSchema()).isEqualTo(first.path("tools").get(0).path("outputSchema"));
        assertThat(response.tools().get(0).annotations().path("futureHint").asText()).isEqualTo("preserve");
        assertThat(response.tools().get(1).execution().path("taskSupport").asText()).isEqualTo("required");
        verify(client, times(2)).toolsList(any(), any(), any(), any(), any());
    }

    @Test
    void schemaVerificationUsesTheSameCompleteTraversal() throws Exception {
        when(client.initialize(any(), any())).thenReturn(session);
        when(client.toolsList(eq(session), isNull(), any(), any(), any()))
            .thenReturn(mapper.readTree("{\"tools\":[],\"nextCursor\":\"second\"}"));
        when(client.toolsList(eq(session), eq("second"), any(), any(), any()))
            .thenReturn(mapper.readTree("{\"tools\":[{\"name\":\"target\",\"inputSchema\":{\"type\":\"object\"}}]}"));
        DiscoveryResponse discovery = discover(service);
        Map<String, Object> config = config();
        @SuppressWarnings("unchecked") Map<String, Object> execution = (Map<String, Object>) config.get("execution");
        execution.put("mcp", Map.of("serverRef", "server", "toolName", "target",
            "toolSchemaHash", discovery.tools().get(0).schemaHash()));
        when(client.toolsCall(eq(session), eq("target"), any(), any())).thenReturn(mapper.readTree("{}"));

        assertThat(execute(config, Map.of()).success()).isTrue();
        verify(client, times(2)).toolsList(eq(session), eq("second"), any(), any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"repeated-cursor", "duplicate-tool", "missing-tools", "wrong-cursor"})
    void incompleteCataloguesAreNotReportedAsReady(String mode) throws Exception {
        when(client.initialize(any(), any())).thenReturn(session);
        JsonNode first = mapper.readTree("{\"tools\":[{\"name\":\"a\"}],\"nextCursor\":\"next\"}");
        JsonNode second = switch (mode) {
            case "repeated-cursor" -> mapper.readTree("{\"tools\":[],\"nextCursor\":\"next\"}");
            case "duplicate-tool" -> mapper.readTree("{\"tools\":[{\"name\":\"a\"}]}");
            case "wrong-cursor" -> mapper.readTree("{\"tools\":[],\"nextCursor\":123}");
            default -> mapper.readTree("{}");
        };
        when(client.toolsList(eq(session), any(), any(), any(), any())).thenReturn(first, second);

        DiscoveryResponse response = discover(service);

        assertThat(response.ready()).isFalse();
        assertThat(response.errorCode()).isEqualTo("MCP_DISCOVERY_INCOMPLETE");
        assertThat(response.tools()).isEmpty();
        verify(client, times(2)).toolsList(any(), any(), any(), any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"tools", "pages", "bytes", "deadline"})
    void discoveryEnforcesFiniteLimits(String mode) throws Exception {
        McpGatewayProperties limits = properties("tools".equals(mode) ? 1 : 500, "pages".equals(mode) ? 1 : 100,
            "bytes".equals(mode) ? 1 : 8 * 1024 * 1024, "deadline".equals(mode) ? Duration.ofNanos(1) : Duration.ofSeconds(60));
        McpGatewayExecutionService limited = new McpGatewayExecutionService(client, mapper, limits);
        when(client.initialize(any(), any())).thenReturn(session);
        when(client.toolsList(any(), any(), any(), any(), any()))
            .thenReturn(mapper.readTree("{\"tools\":[{\"name\":\"a\"},{\"name\":\"b\"}],\"nextCursor\":\"more\"}"));

        DiscoveryResponse response = discover(limited);

        assertThat(response.ready()).isFalse();
        assertThat(response.errorCode()).isEqualTo("MCP_DISCOVERY_INCOMPLETE");
        assertThat(response.tools()).isEmpty();
    }

    @Test
    void publicConnectionDoesNotResolveAnySecretAndMissingBusinessSecretDoesNotDowngrade() throws Exception {
        when(client.initialize(any(), any())).thenReturn(session);
        when(client.toolsList(any(), any(), any(), any(), any())).thenReturn(mapper.readTree("{\"tools\":[]}"));
        ArgumentCaptor<McpStreamableHttpClient.McpRequestOptions> options = ArgumentCaptor.forClass(McpStreamableHttpClient.McpRequestOptions.class);
        assertThat(discover(service).ready()).isTrue();
        verify(client).initialize(any(), options.capture());
        assertThat(options.getValue().headers()).isEmpty();
        clearInvocations(client);

        DiscoveryResponse missing = service.discover(new DiscoveryRequest("server", Map.of("endpointUrl", "https://example.com/mcp",
            "auth", Map.of("mode", "BEARER_TOKEN_SECRET_REF", "secretRef", "CUSTOMER_TEST_TOKEN")), Map.of(), List.of()));
        assertThat(missing.ready()).isFalse();
        verify(client, never()).initialize(any(), any());
    }

    @Test
    void resolvesBusinessBearerThroughExistingSecretBoundary() throws Exception {
        when(client.initialize(any(), any())).thenReturn(session);
        when(client.toolsList(any(), any(), any(), any(), any())).thenReturn(mapper.readTree("{\"tools\":[]}"));
        DiscoveryResponse response = service.discover(new DiscoveryRequest("server", Map.of("endpointUrl", "https://example.com/mcp",
            "auth", Map.of("mode", "BEARER_TOKEN_SECRET_REF", "secretRef", "CUSTOMER_BUSINESS_TOKEN")),
            Map.of("mcpSecretValues", Map.of("CUSTOMER_BUSINESS_TOKEN", "business-token")), List.of()));
        assertThat(response.ready()).isTrue();
        ArgumentCaptor<McpStreamableHttpClient.McpRequestOptions> options = ArgumentCaptor.forClass(McpStreamableHttpClient.McpRequestOptions.class);
        verify(client).initialize(any(), options.capture());
        assertThat(options.getValue().headers()).containsExactlyEntriesOf(Map.of("Authorization", "Bearer business-token"));
        assertThat(mapper.writeValueAsString(response)).doesNotContain("business-token");
    }

    @Test
    void multipleHeaderAuthResolvesOnlyReferencedValues() throws Exception {
        when(client.initialize(any(), any())).thenReturn(session);
        when(client.toolsList(any(), any(), any(), any(), any())).thenReturn(mapper.readTree("{\"tools\":[]}"));
        DiscoveryResponse response = service.discover(new DiscoveryRequest("server", Map.of("endpointUrl", "https://example.com/mcp", "auth", Map.of(
            "mode", "MULTIPLE_HEADERS_SECRET_REFS", "headers", List.of(Map.of("headerName", "X-API-KEY", "secretRef", "KEY_ONE"),
                Map.of("headerName", "X-MCP-API-KEY", "secretRef", "KEY_TWO")))),
            Map.of("mcpSecretValues", Map.of("KEY_ONE", "first", "KEY_TWO", "second", "NOT_REFERENCED", "private")), List.of()));
        assertThat(response.ready()).isTrue();
        ArgumentCaptor<McpStreamableHttpClient.McpRequestOptions> options = ArgumentCaptor.forClass(McpStreamableHttpClient.McpRequestOptions.class);
        verify(client).initialize(any(), options.capture());
        assertThat(options.getValue().headers()).containsExactlyInAnyOrderEntriesOf(Map.of("X-API-KEY", "first", "X-MCP-API-KEY", "second"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Host", "Content-Type", "X-MCP-GATEWAY-API-KEY", "X-Not-Allowed"})
    void multipleHeadersCannotOverrideProtocolOrInternalAuthority(String header) {
        DiscoveryResponse response = service.discover(new DiscoveryRequest("server", Map.of("endpointUrl", "https://example.com/mcp", "auth", Map.of(
            "mode", "MULTIPLE_HEADERS_SECRET_REFS", "headers", List.of(Map.of("headerName", header, "secretRef", "KEY_ONE")))),
            Map.of("mcpSecretValues", Map.of("KEY_ONE", "secret")), List.of()));
        assertThat(response.ready()).isFalse();
        verify(client, never()).initialize(any(), any());
    }

    @Test
    void nativeInputPreservesNullableUnionNumericEnumsAndLocalReferences() throws Exception {
        Map<String, Object> config = config();
        config.put("inputSchema", mapper.readTree("""
            {"type":"object","required":["query","amount","mode"],"additionalProperties":false,
             "$defs":{"q":{"type":["string","null"]}},
             "properties":{"query":{"$ref":"#/$defs/q"},"amount":{"type":"number","minimum":0.5},
                "mode":{"enum":[1,2]},"filter":{"oneOf":[{"type":"null"},{"type":"object"}]}}}
            """));
        config.put("params", List.of(Map.of("name", "mode", "type", "string")));
        when(client.initialize(any(), any())).thenReturn(session);
        when(client.toolsCall(any(McpStreamableHttpClient.McpSession.class), anyString(), any(), any())).thenReturn(mapper.readTree("{}"));
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("query", null);
        args.put("amount", 0.75);
        args.put("mode", 2);
        args.put("filter", null);
        assertThat(execute(config, args).success()).isTrue();

        args.put("amount", 0.25);
        assertThat(execute(config, args).errorCode()).isEqualTo("INVALID_MCP_ACTION_ARGUMENTS");
        verify(client, times(1)).toolsCall(any(McpStreamableHttpClient.McpSession.class), anyString(), any(), any());
    }

    @Test
    void nativeOutputIsValidatedWithoutReplayingMutation() throws Exception {
        Map<String, Object> config = config();
        config.put("accessMode", "WRITE_ONLY");
        config.put("outputSchema", mapper.readTree("{\"type\":\"object\",\"required\":[\"receipt\"],\"properties\":{\"receipt\":{\"type\":\"string\"}}}"));
        when(client.initialize(any(), any())).thenReturn(session);
        when(client.toolsCall(any(McpStreamableHttpClient.McpSession.class), anyString(), any(), any()))
            .thenReturn(mapper.readTree("{\"structuredContent\":{\"receipt\":123}}"));
        ActionExecuteResponse result = execute(config, Map.of());
        assertThat(result.success()).isFalse();
        assertThat(result.errorCode()).isEqualTo("MCP_TOOL_OUTPUT_SCHEMA_MISMATCH");
        assertThat(result.message()).doesNotContain("123");
        assertThat(result.message()).contains("operation may have completed", "Check the target system before retrying");
        verify(client, times(1)).toolsCall(any(McpStreamableHttpClient.McpSession.class), anyString(), any(), any());
    }

    @Test
    void externalReferencesFailBeforeAnyCall() {
        Map<String, Object> config = config();
        config.put("inputSchema", Map.of("$ref", "https://169.254.169.254/latest"));
        assertThat(execute(config, Map.of()).errorCode()).isEqualTo("INVALID_MCP_ACTION_CONFIG");
        verifyNoInteractions(client);
    }

    @Test
    void unresolvedLocalOutputReferenceFailsBeforeMutation() {
        Map<String, Object> config = config();
        config.put("outputSchema", Map.of("type", "object", "properties", Map.of("receipt", Map.of("$ref", "#/$defs/missing"))));
        assertThat(execute(config, Map.of()).errorCode()).isEqualTo("INVALID_MCP_ACTION_CONFIG");
        verifyNoInteractions(client);
    }

    @Test
    void requiredUnqualifiedVocabularyIsNotSilentlyIgnored() {
        Map<String, Object> config = config();
        config.put("inputSchema", Map.of("type", "object", "$vocabulary", Map.of("https://example.com/custom-execution-constraints", true)));
        assertThat(execute(config, Map.of()).errorCode()).isEqualTo("INVALID_MCP_ACTION_CONFIG");
        verifyNoInteractions(client);
    }

    @Test
    void draftSevenSchemaIsSupportedAndMalformedSchemaIsNotAccepted() throws Exception {
        Map<String, Object> config = config();
        config.put("inputSchema", mapper.readTree("""
            {"$schema":"http://json-schema.org/draft-07/schema#","type":"object",
             "required":["tuple"],"properties":{"tuple":{"type":"array","items":[{"type":"string"},{"type":"number"}],"additionalItems":false}}}
            """));
        when(client.initialize(any(), any())).thenReturn(session);
        when(client.toolsCall(any(McpStreamableHttpClient.McpSession.class), anyString(), any(), any())).thenReturn(mapper.readTree("{}"));
        assertThat(execute(config, Map.of("tuple", List.of("id", 0.5))).success()).isTrue();
        assertThat(execute(config, Map.of("tuple", List.of("id", "invalid"))).errorCode()).isEqualTo("INVALID_MCP_ACTION_ARGUMENTS");
        config.put("inputSchema", Map.of("type", 123));
        assertThat(execute(config, Map.of()).errorCode()).isEqualTo("INVALID_MCP_ACTION_CONFIG");
        verify(client, times(1)).toolsCall(any(McpStreamableHttpClient.McpSession.class), anyString(), any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"duplicate", "missing", "empty", "over-limit"})
    void invalidMultipleHeaderBindingsNeverStartARequest(String mode) {
        List<Map<String, String>> headers = switch (mode) {
            case "duplicate" -> List.of(Map.of("headerName", "X-API-KEY", "secretRef", "KEY_ONE"),
                Map.of("headerName", "x-api-key", "secretRef", "KEY_ONE"));
            case "empty" -> List.of();
            case "over-limit" -> java.util.Collections.nCopies(9, Map.of("headerName", "X-API-KEY", "secretRef", "KEY_ONE"));
            default -> List.of(Map.of("headerName", "X-API-KEY", "secretRef", "KEY_MISSING"));
        };
        DiscoveryResponse response = service.discover(new DiscoveryRequest("server", Map.of("endpointUrl", "https://example.com/mcp",
            "auth", Map.of("mode", "MULTIPLE_HEADERS_SECRET_REFS", "headers", headers)),
            Map.of("mcpSecretValues", Map.of("KEY_ONE", "one")), List.of()));
        assertThat(response.ready()).isFalse();
        verifyNoInteractions(client);
    }

    @Test
    void schemaHashDoesNotDiscardPropertiesNamedLikeSchemaAnnotations() throws Exception {
        when(client.initialize(any(), any())).thenReturn(session);
        when(client.toolsList(any(), any(), any(), any(), any()))
            .thenReturn(mapper.readTree("{\"tools\":[{\"name\":\"a\",\"inputSchema\":{\"properties\":{\"title\":{\"type\":\"string\"}}}}]}"),
                mapper.readTree("{\"tools\":[{\"name\":\"a\",\"inputSchema\":{\"properties\":{\"title\":{\"type\":\"number\"}}}}]}"));
        String first = discover(service).tools().get(0).schemaHash();
        assertThat(discover(service).tools().get(0).schemaHash()).isNotEqualTo(first);
    }

    @Test
    void taskRequiredToolIsVisibleButNotExecutableSynchronously() throws Exception {
        when(client.initialize(any(), any())).thenReturn(session);
        when(client.toolsList(any(), any(), any(), any(), any()))
            .thenReturn(mapper.readTree("{\"tools\":[{\"name\":\"target\",\"execution\":{\"taskSupport\":\"required\"}}]}"));
        ServerVerificationResponse verification = service.verify(new ServerVerificationRequest("server",
            Map.of("endpointUrl", "https://example.com/mcp"), Map.of(), List.of(new ExpectedTool("target", null, "WARN_ONLY"))));
        assertThat(verification.ready()).isFalse();
        assertThat(verification.tools().get(0).status()).isEqualTo("TASK_EXECUTION_REQUIRED");
        verify(client, never()).toolsCall(any(McpStreamableHttpClient.McpSession.class), anyString(), any(), any());
    }

    private Map<String, Object> config() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("execution", new LinkedHashMap<>(Map.of("adapterType", "mcp-tool",
            "mcp", Map.of("serverRef", "server", "toolName", "target"))));
        config.put("mcpServers", Map.of("server", Map.of("endpointUrl", "https://example.com/mcp", "auth", Map.of("mode", "NONE"))));
        return config;
    }

    private ActionExecuteResponse execute(Map<String, Object> config, Map<String, Object> args) {
        return service.executeAction(new ActionExecuteRequest("test", args, "idempotency-is-not-deduplication", Map.of(), config));
    }

    private DiscoveryResponse discover(McpGatewayExecutionService target) {
        return target.discover(new DiscoveryRequest("server", Map.of("endpointUrl", "https://example.com/mcp", "auth", Map.of("mode", "NONE")), Map.of(), List.of()));
    }

    private static McpGatewayProperties properties(int tools, int pages, int bytes, Duration deadline) {
        return new McpGatewayProperties("test", "test", "internal", "X-MCP-GATEWAY-API-KEY", "2025-11-25", List.of(), List.of(),
            false, "MCP_SECRET_", Duration.ofSeconds(1), Duration.ofSeconds(3), 2 * 1024 * 1024, tools, pages, bytes, deadline, 8);
    }
}
