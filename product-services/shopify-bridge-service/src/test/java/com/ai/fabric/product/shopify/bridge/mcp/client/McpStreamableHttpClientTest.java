package com.ai.fabric.product.shopify.bridge.mcp.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class McpStreamableHttpClientTest {

    private MockRestServiceServer server;
    private McpStreamableHttpClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new McpStreamableHttpClient(builder, new ObjectMapper());
    }

    @Test
    void toolsCallUsesStreamableHttpHeadersAndCustomAuthHeaders() throws Exception {
        URI endpoint = URI.create("https://inventory.example/mcp");
        JsonNode arguments = new ObjectMapper().readTree("""
            {"query": "coffee"}
            """);

        server.expect(requestTo(endpoint))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header(McpStreamableHttpClient.MCP_PROTOCOL_VERSION_HEADER, "2025-11-25"))
            .andExpect(header("X-MCP-API-Key", "secret-value"))
            .andExpect(header("Accept", "application/json, text/event-stream"))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(content().json("""
                {
                  "jsonrpc": "2.0",
                  "method": "tools/call",
                  "params": {
                    "name": "inventory.search",
                    "arguments": {
                      "query": "coffee"
                    }
                  }
                }
                """))
            .andRespond(withSuccess("""
                {
                  "jsonrpc": "2.0",
                  "id": 1,
                  "result": {
                    "content": [{"type": "text", "text": "ok"}]
                  }
                }
                """, MediaType.APPLICATION_JSON));

        JsonNode result = client.toolsCall(
            endpoint,
            "inventory.search",
            arguments,
            McpStreamableHttpClient.McpRequestOptions.withHeaders(Map.of("X-MCP-API-Key", "secret-value"))
        );

        assertThat(result.path("content").get(0).path("text").asText()).isEqualTo("ok");
        server.verify();
    }

    @Test
    void toolsListParsesEventStreamJsonRpcResult() {
        URI endpoint = URI.create("https://inventory.example/mcp");
        server.expect(requestTo(endpoint))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("""
                event: message
                data: {"jsonrpc":"2.0","id":1,"result":{"tools":[{"name":"inventory.search"}]}}

                """, MediaType.TEXT_EVENT_STREAM));

        JsonNode result = client.toolsList(endpoint, McpStreamableHttpClient.McpRequestOptions.none());

        assertThat(result.path("tools").get(0).path("name").asText()).isEqualTo("inventory.search");
        server.verify();
    }
}
