package com.ai.fabric.product.shopify.bridge.install.service;

import com.ai.fabric.product.shopify.bridge.auth.ShopifyMerchantSession;
import com.ai.fabric.product.shopify.bridge.config.ShopifyBridgeProperties;
import com.ai.fabric.product.shopify.bridge.install.model.ShopifyTokenExchangeMaterial;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ShopifyTokenExchangeServiceTest {

    private MockRestServiceServer server;
    private ShopifyTokenExchangeService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        service = new ShopifyTokenExchangeService(properties(), builder);
    }

    @Test
    void exchangeExpiringOfflineTokenUsesShopifyTokenExchangeContract() {
        server.expect(requestTo("https://alpha.myshopify.com/admin/oauth/access_token"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
            .andExpect(content().string(containsString("grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Atoken-exchange")))
            .andExpect(content().string(containsString("requested_token_type=urn%3Ashopify%3Aparams%3Aoauth%3Atoken-type%3Aoffline-access-token")))
            .andExpect(content().string(containsString("subject_token=session-token")))
            .andRespond(withSuccess("""
                {
                  "access_token":"shpat_access",
                  "expires_in":3600,
                  "refresh_token":"shprt_refresh",
                  "refresh_token_expires_in":7776000,
                  "scope":"read_products,read_content,read_legal_policies"
                }
                """, MediaType.APPLICATION_JSON));

        ShopifyTokenExchangeMaterial material = service.exchangeExpiringOfflineToken(session(), "Bearer session-token");

        assertThat(material.accessToken()).isEqualTo("shpat_access");
        assertThat(material.refreshToken()).isEqualTo("shprt_refresh");
        assertThat(material.scopesText()).isEqualTo("read_products,read_content,read_legal_policies");
        assertThat(material.expiring()).isTrue();
        assertThat(material.accessTokenExpiresAt()).isAfter(Instant.now());
        assertThat(material.refreshTokenExpiresAt()).isAfter(material.accessTokenExpiresAt());
        server.verify();
    }

    private ShopifyMerchantSession session() {
        return new ShopifyMerchantSession(
            "alpha.myshopify.com",
            "https://alpha.myshopify.com",
            "gid://shopify/User/1",
            Instant.parse("2026-04-18T12:00:00Z")
        );
    }

    private ShopifyBridgeProperties properties() {
        return new ShopifyBridgeProperties(
            "Bridge",
            "shopify-bridge-prod",
            "SHOPIFY",
            "SHOPIFY_BRIDGE_SERVICE",
            "prod",
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
