package com.ai.fabric.product.shopify.bridge.client.shopify;

import com.ai.fabric.product.shopify.bridge.config.ShopifyBridgeProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ShopifyAdminGraphqlClientTest {

    private MockRestServiceServer server;
    private ShopifyAdminGraphqlClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new ShopifyAdminGraphqlClient(properties(), builder);
    }

    @Test
    void executeUsesConfiguredVersionAndAccessToken() {
        server.expect(requestTo("https://alpha.myshopify.com/admin/api/2026-04/graphql.json"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("X-Shopify-Access-Token", "shpat_access"))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(content().json("""
                {"query":"query ShopifyCompanionProductsPreflight { productsCount(limit: null) { count } }"}
                """))
            .andRespond(withSuccess("""
                {
                  "data": {
                    "productsCount": {
                      "count": 42
                    }
                  }
                }
                """, MediaType.APPLICATION_JSON));

        Map<String, Object> response = client.execute(
            "alpha.myshopify.com",
            "shpat_access",
            "query ShopifyCompanionProductsPreflight { productsCount(limit: null) { count } }"
        );

        assertThat(response).containsKey("data");
        server.verify();
    }

    private ShopifyBridgeProperties properties() {
        return new ShopifyBridgeProperties(
            "Bridge",
            "shopify-bridge-test",
            "SHOPIFY",
            "SHOPIFY_BRIDGE_SERVICE",
            "test",
            "https://bridge.example.com",
            "2026-04",
            "shopify-api-key",
            "shopify-secret",
            "https://platform.example.com",
            "platform-admin-key",
            "X-PLATFORM-API-KEY",
            "webhook-secret",
            "bridge-admin-key",
            "X-BRIDGE-API-KEY"
        );
    }
}
