package com.ai.fabric.product.shopify.bridge.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "shopify.bridge.admin-api-key=test-admin-key",
    "shopify.bridge.shopify-api-key=test-shopify-api-key",
    "shopify.bridge.shopify-api-secret=test-shopify-secret",
    "shopify.bridge.public-base-url=https://bridge.example.com"
})
@AutoConfigureMockMvc
class ShopifyEmbeddedAppControllerTest {

    private static Path merchantUiDirectory;

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void dynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("shopify.bridge.merchant-ui-directory", () -> merchantUiDirectory().toString());
    }

    @Test
    void servesEmbeddedAppShellAtRoot() throws Exception {
        mockMvc.perform(get("/"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("text/html"))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Shopify Bridge Admin")));
    }

    @Test
    void servesEmbeddedAppAssets() throws Exception {
        mockMvc.perform(get("/assets/index.js"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith("application/javascript"))
            .andExpect(content().string("console.log('shopify bridge test asset');"));
    }

    private static Path merchantUiDirectory() {
        if (merchantUiDirectory != null) {
            return merchantUiDirectory;
        }
        try {
            Path tempDirectory = Files.createTempDirectory("shopify-embedded-ui-test");
            Files.writeString(
                tempDirectory.resolve("index.html"),
                "<!doctype html><html><head><title>Shopify Bridge Admin</title></head><body><div id=\"root\"></div></body></html>"
            );
            Path assetsDirectory = Files.createDirectories(tempDirectory.resolve("assets"));
            Files.writeString(assetsDirectory.resolve("index.js"), "console.log('shopify bridge test asset');");
            merchantUiDirectory = tempDirectory;
            return tempDirectory;
        } catch (IOException ex) {
            throw new RuntimeException("Failed to create temporary merchant UI fixture.", ex);
        }
    }
}
