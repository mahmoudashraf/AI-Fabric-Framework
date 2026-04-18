package com.ai.fabric.product.shopify.bridge.web;

import com.ai.fabric.product.shopify.bridge.install.service.ShopifyInstallFlowService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "shopify.bridge.admin-api-key=test-admin-key",
    "shopify.bridge.shopify-api-key=test-shopify-api-key",
    "shopify.bridge.shopify-api-secret=test-shopify-secret",
    "shopify.bridge.public-base-url=https://bridge.example.com"
})
@AutoConfigureMockMvc
class ShopifyInstallControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ShopifyInstallFlowService installFlowService;

    @Test
    void installRedirectsWithoutAuthentication() throws Exception {
        when(installFlowService.beginInstall("alpha.myshopify.com"))
            .thenReturn(URI.create("https://alpha.myshopify.com/admin/oauth/authorize?state=test"));

        mockMvc.perform(get("/auth/shopify/install").param("shop", "alpha.myshopify.com"))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", "https://alpha.myshopify.com/admin/oauth/authorize?state=test"));

        verify(installFlowService).beginInstall("alpha.myshopify.com");
    }

    @Test
    void callbackRedirectsBackIntoEmbeddedApp() throws Exception {
        when(installFlowService.completeInstall(
            eq("alpha.myshopify.com"),
            eq("temp-code"),
            eq("encoded-host"),
            eq("signed-state"),
            eq("shop=alpha.myshopify.com&code=temp-code&host=encoded-host&state=signed-state")
        )).thenReturn(URI.create("https://bridge.example.com?shop=alpha.myshopify.com&host=encoded-host"));

        mockMvc.perform(
                get("/auth/shopify/callback")
                    .queryParam("shop", "alpha.myshopify.com")
                    .queryParam("code", "temp-code")
                    .queryParam("host", "encoded-host")
                    .queryParam("state", "signed-state")
            )
            .andExpect(status().isFound())
            .andExpect(header().string("Location", "https://bridge.example.com?shop=alpha.myshopify.com&host=encoded-host"));
    }
}
