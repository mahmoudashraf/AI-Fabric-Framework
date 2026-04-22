package com.ai.fabric.product.shopify.bridge.web;

import com.ai.fabric.product.shopify.bridge.install.service.ShopifyBridgeInstallCredentialService;
import com.ai.fabric.product.shopify.bridge.store.service.ShopifyBridgeStoreLifecycleService;
import com.ai.fabric.product.shopify.bridge.webhook.service.ShopifyWebhookVerificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "shopify.bridge.webhook-shared-secret=super-secret",
    "shopify.bridge.admin-api-key=test-admin-key"
})
@AutoConfigureMockMvc
class ShopifyWebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ShopifyWebhookVerificationService verificationService;

    @MockBean
    private ShopifyBridgeStoreLifecycleService lifecycleService;

    @MockBean
    private ShopifyBridgeInstallCredentialService installCredentialService;

    @Test
    void invalidWebhookHmacReturnsUnauthorized() throws Exception {
        mockMvc.perform(
                post("/api/webhooks/shopify")
                    .header("X-Shopify-Hmac-Sha256", "bad-hmac")
                    .header("X-Shopify-Topic", "app/uninstalled")
                    .header("X-Shopify-Shop-Domain", "alpha.myshopify.com")
                    .contentType("application/json")
                    .content("{\"myshopify_domain\":\"alpha.myshopify.com\"}")
            )
            .andExpect(status().isUnauthorized());

        verifyNoInteractions(lifecycleService);
    }

    @Test
    void validAppUninstalledWebhookTriggersLifecycleUpdate() throws Exception {
        String payload = "{\"myshopify_domain\":\"alpha.myshopify.com\"}";
        String hmac = verificationService.compute(payload);

        mockMvc.perform(
                post("/api/webhooks/shopify")
                    .header("X-Shopify-Hmac-Sha256", hmac)
                    .header("X-Shopify-Topic", "app/uninstalled")
                    .header("X-Shopify-Shop-Domain", "alpha.myshopify.com")
                    .contentType("application/json")
                    .content(payload)
            )
            .andExpect(status().isOk());

        verify(lifecycleService).markUninstalled("alpha.myshopify.com");
        verify(installCredentialService).clearLocalPersistedCredentials("alpha.myshopify.com");
    }

    @Test
    void validShopRedactWebhookTriggersLifecycleCleanup() throws Exception {
        String payload = "{\"myshopify_domain\":\"alpha.myshopify.com\"}";
        String hmac = verificationService.compute(payload);

        mockMvc.perform(
                post("/api/webhooks/shopify")
                    .header("X-Shopify-Hmac-Sha256", hmac)
                    .header("X-Shopify-Topic", "shop/redact")
                    .header("X-Shopify-Shop-Domain", "alpha.myshopify.com")
                    .contentType("application/json")
                    .content(payload)
            )
            .andExpect(status().isOk());

        verify(lifecycleService).markUninstalled("alpha.myshopify.com");
        verify(installCredentialService).clearLocalPersistedCredentials("alpha.myshopify.com");
    }
}
