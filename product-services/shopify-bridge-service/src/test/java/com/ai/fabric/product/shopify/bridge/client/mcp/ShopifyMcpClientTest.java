package com.ai.fabric.product.shopify.bridge.client.mcp;

import com.ai.fabric.product.shopify.bridge.config.ShopifyStorefrontMcpProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withAccepted;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ShopifyMcpClientTest {

    private MockRestServiceServer server;
    private ShopifyMcpClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new ShopifyMcpClient(
            builder,
            new ObjectMapper(),
            new ShopifyStorefrontMcpProperties("2025-11-25", "https://profiles.example/ucp.json")
        );
    }

    @Test
    void initializeNegotiatesProtocolAndSendsInitializedNotification() {
        URI endpoint = URI.create("https://alpha.myshopify.com/api/mcp");
        server.expect(requestTo(endpoint))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header(ShopifyMcpClient.MCP_PROTOCOL_VERSION_HEADER, "2025-11-25"))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(content().json("""
                {
                  "jsonrpc": "2.0",
                  "method": "initialize",
                  "params": {
                    "protocolVersion": "2025-11-25",
                    "capabilities": {},
                    "clientInfo": {
                      "name": "loomai-shopify-bridge",
                      "version": "0.1.0"
                    }
                  }
                }
                """))
            .andRespond(withSuccess("""
                {
                  "jsonrpc": "2.0",
                  "id": 1,
                  "result": {
                    "protocolVersion": "2025-11-25",
                    "serverInfo": {
                      "name": "shopify-storefront-mcp"
                    }
                  }
                }
                """, MediaType.APPLICATION_JSON).header(ShopifyMcpClient.MCP_SESSION_ID_HEADER, "mcp-session-1"));
        server.expect(requestTo(endpoint))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header(ShopifyMcpClient.MCP_PROTOCOL_VERSION_HEADER, "2025-11-25"))
            .andExpect(header(ShopifyMcpClient.MCP_SESSION_ID_HEADER, "mcp-session-1"))
            .andExpect(content().json("""
                {
                  "jsonrpc": "2.0",
                  "method": "notifications/initialized"
                }
                """))
            .andRespond(withAccepted());

        ShopifyMcpClient.ShopifyMcpSession session = client.initialize(endpoint);

        assertThat(session.sessionId()).isEqualTo("mcp-session-1");
        assertThat(session.protocolVersion()).isEqualTo("2025-11-25");
        server.verify();
    }

    @Test
    void toolsCallUsesStreamableHttpHeadersAndParsesJsonResult() throws Exception {
        URI endpoint = URI.create("https://alpha.myshopify.com/api/mcp");
        JsonNode arguments = new ObjectMapper().readTree("""
            {
              "meta": {
                "ucp-agent": {
                  "profile": "https://profiles.example/ucp.json"
                }
              },
              "catalog": {
                "query": "coffee"
              }
            }
            """);
        server.expect(requestTo(endpoint))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header(ShopifyMcpClient.MCP_PROTOCOL_VERSION_HEADER, "2025-11-25"))
            .andExpect(header("Accept", "application/json, text/event-stream"))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(content().json("""
                {
                  "jsonrpc": "2.0",
                  "method": "tools/call",
                  "params": {
                    "name": "search_catalog",
                    "arguments": {
                      "catalog": {
                        "query": "coffee"
                      }
                    }
                  }
                }
                """))
            .andRespond(withSuccess("""
                {
                  "jsonrpc": "2.0",
                  "id": 1,
                  "result": {
                    "content": [
                      {
                        "type": "text",
                        "text": "Catalog result"
                      }
                    ]
                  }
                }
                """, MediaType.APPLICATION_JSON));

        JsonNode result = client.toolsCall(endpoint, "search_catalog", arguments);

        assertThat(result.path("content").get(0).path("text").asText()).isEqualTo("Catalog result");
        server.verify();
    }

    @Test
    void toolsListParsesEventStreamResult() {
        URI endpoint = URI.create("https://alpha.myshopify.com/api/mcp");
        server.expect(requestTo(endpoint))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("""
                event: message
                data: {"jsonrpc":"2.0","id":1,"result":{"tools":[{"name":"search_catalog"}]}}

                """, MediaType.TEXT_EVENT_STREAM));

        JsonNode result = client.toolsList(endpoint);

        assertThat(result.path("tools").get(0).path("name").asText()).isEqualTo("search_catalog");
        server.verify();
    }
}
