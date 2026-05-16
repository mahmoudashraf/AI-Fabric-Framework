package com.ai.fabric.product.shopify.bridge.web;

import com.ai.fabric.product.shopify.bridge.customeraccount.model.ShopifyCustomerAccountTokenBrokerResponse;
import com.ai.fabric.product.shopify.bridge.customeraccount.service.ShopifyCustomerAccountOAuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "shopify.bridge.admin-api-key=test-admin-key",
    "shopify.bridge.shopify-api-key=test-shopify-api-key",
    "shopify.bridge.shopify-api-secret=test-shopify-secret"
})
@AutoConfigureMockMvc
class ShopifyCustomerAccountTokenBrokerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ShopifyCustomerAccountOAuthService oauthService;

    @Test
    void resolvesCustomerTokenBehindBridgeAdminKey() throws Exception {
        when(oauthService.resolveAccessToken("alpha.myshopify.com", "shopper-session-1"))
            .thenReturn(Optional.of("customer-token"));

        mockMvc.perform(post("/api/admin/customer-account/shops/alpha.myshopify.com/token/resolve")
                .header("X-BRIDGE-API-KEY", "test-admin-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"shopperSessionId":"shopper-session-1","requiredScopes":["customer-account-mcp-api:full"]}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.accessToken").value("customer-token"))
            .andExpect(jsonPath("$.tokenType").value("Bearer"));

        verify(oauthService).resolveAccessToken("alpha.myshopify.com", "shopper-session-1");
    }

    @Test
    void requiresBridgeAdminKey() throws Exception {
        mockMvc.perform(post("/api/admin/customer-account/shops/alpha.myshopify.com/token/resolve")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"shopperSessionId\":\"shopper-session-1\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void reportsMissingCustomerAccountSessionWithoutTokenLeak() throws Exception {
        when(oauthService.resolveAccessToken("alpha.myshopify.com", "shopper-session-1"))
            .thenReturn(Optional.empty());

        mockMvc.perform(post("/api/admin/customer-account/shops/alpha.myshopify.com/token/resolve")
                .header("X-BRIDGE-API-KEY", "test-admin-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"shopperSessionId\":\"shopper-session-1\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.accessToken").isEmpty())
            .andExpect(jsonPath("$.errorCode").value("CUSTOMER_ACCOUNT_AUTH_REQUIRED"));
    }

    @Test
    void masksTokenInResponseToString() {
        assertThat(ShopifyCustomerAccountTokenBrokerResponse.success("customer-token").toString())
            .contains("[REDACTED]")
            .doesNotContain("customer-token");
    }
}
