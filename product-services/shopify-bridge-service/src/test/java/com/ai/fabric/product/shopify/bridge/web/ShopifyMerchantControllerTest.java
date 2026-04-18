package com.ai.fabric.product.shopify.bridge.web;

import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeMerchantSessionResponse;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreBootstrapResponse;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreSummary;
import com.ai.fabric.product.shopify.bridge.store.service.ShopifyBridgeMerchantStoreService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "shopify.bridge.shopify-api-key=test-shopify-api-key",
    "shopify.bridge.shopify-api-secret=test-shopify-secret",
    "shopify.bridge.admin-api-key=test-admin-key"
})
@AutoConfigureMockMvc
class ShopifyMerchantControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ShopifyBridgeMerchantStoreService merchantStoreService;

    @Test
    void sessionRequiresBearerToken() throws Exception {
        mockMvc.perform(get("/api/app/session"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void sessionReturnsMerchantScopedStoreContext() throws Exception {
        when(merchantStoreService.session(any())).thenReturn(new ShopifyBridgeMerchantSessionResponse(
            "alpha.myshopify.com",
            "https://alpha.myshopify.com",
            "gid://shopify/User/1",
            Instant.parse("2026-04-18T12:00:00Z"),
            store()
        ));

        mockMvc.perform(get("/api/app/session").header("Authorization", "Bearer " + token()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.shopDomain").value("alpha.myshopify.com"))
            .andExpect(jsonPath("$.store.shopDomain").value("alpha.myshopify.com"));

        verify(merchantStoreService).session(any());
    }

    @Test
    void bootstrapUsesMerchantSessionContext() throws Exception {
        when(merchantStoreService.bootstrap(any())).thenReturn(new ShopifyBridgeStoreBootstrapResponse(
            "alpha.myshopify.com",
            "cust-1",
            "dep-1",
            "consumer-1",
            true,
            true,
            true,
            List.of("mkp-template-commerce-shell"),
            store()
        ));

        mockMvc.perform(post("/api/app/store/bootstrap").header("Authorization", "Bearer " + token()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deploymentId").value("dep-1"));

        verify(merchantStoreService).bootstrap(any());
    }

    private ShopifyBridgeStoreSummary store() {
        return new ShopifyBridgeStoreSummary(
            "shp-1",
            "alpha.myshopify.com",
            "Alpha",
            "shopify-bridge-prod",
            "Shopify Bridge Prod",
            "cust-1",
            "Alpha Customer",
            "dep-1",
            "Alpha Deployment",
            "DRAFT",
            "consumer-1",
            "Alpha Storefront",
            "INSTALLED",
            "NOT_SYNCED",
            "NOT_RUN",
            "NOT_ENABLED",
            "CONNECTED",
            true,
            true,
            true,
            true,
            null,
            null,
            null,
            null,
            null,
            null,
            Instant.parse("2026-04-18T00:00:00Z"),
            Instant.parse("2026-04-18T00:00:00Z")
        );
    }

    private String token() {
        try {
            String header = base64Url("""
                {"alg":"HS256","typ":"JWT"}
                """.trim());
            long now = Instant.now().getEpochSecond();
            String payload = base64Url("""
                {"iss":"https://alpha.myshopify.com/admin","dest":"https://alpha.myshopify.com","aud":"test-shopify-api-key","sub":"gid://shopify/User/1","nbf":%d,"exp":%d}
                """.formatted(now - 10, now + 120));
            String signingInput = header + "." + payload;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec("test-shopify-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String signature = Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8)));
            return signingInput + "." + signature;
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private String base64Url(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
}
