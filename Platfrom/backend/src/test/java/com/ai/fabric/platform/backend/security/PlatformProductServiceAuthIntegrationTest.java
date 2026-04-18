package com.ai.fabric.platform.backend.security;

import com.ai.fabric.platform.backend.productservice.entity.PlatformManagedProductServiceEntity;
import com.ai.fabric.platform.backend.productservice.repository.PlatformManagedProductServiceRepository;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.shopify.entity.ShopifyStoreConnectionEntity;
import com.ai.fabric.platform.backend.shopify.repository.ShopifyStoreConnectionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "platform.auth.enabled=true",
    "platform.auth.header-name=X-PLATFORM-API-KEY",
    "platform.auth.api-key-enabled=false",
    "platform.auth.operator-api-key=operator-test-key",
    "platform.auth.admin-api-key=admin-test-key",
    "platform.bootstrap.sample-enabled=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlatformProductServiceAuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PlatformSecretService platformSecretService;

    @Autowired
    private PlatformManagedProductServiceRepository productServiceRepository;

    @Autowired
    private ShopifyStoreConnectionRepository shopifyStoreConnectionRepository;

    @Test
    void productServiceApiKeyStillWorksForScopedApisWhenGeneralApiKeyAuthIsDisabled() throws Exception {
        String serviceRef = "shopify-bridge-product-auth-disabled";
        String secretName = "MANAGED_PRODUCT_SHOPIFY_BRIDGE_PRODUCT_AUTH_DISABLED_API_KEY";
        String shopDomain = "alpha-product-auth-disabled.myshopify.com";
        String serviceSecret = "bridge-product-auth-disabled-key";

        PlatformManagedProductServiceEntity service = new PlatformManagedProductServiceEntity();
        service.setId("psv-123");
        service.setServiceRef(serviceRef);
        service.setDisplayName("Shopify Bridge Prod");
        service.setProductFamily("SHOPIFY");
        service.setServiceKind("SHOPIFY_BRIDGE_SERVICE");
        service.setDeploymentMode("SHARED_PLATFORM_SERVICE");
        service.setTenantMode("MULTI_TENANT_SHARED");
        service.setEnvironmentScope("prod");
        service.setDesiredReplicas(1);
        service.setActualReplicas(1);
        service.setMinReplicas(1);
        service.setMaxReplicas(3);
        service.setBaseUrl("https://shopify-bridge.example.com");
        service.setSecretName(secretName);
        service.setStatus("ACTIVE");
        service.setDetailsJson("{}");
        service.setCreatedAt(Instant.now());
        service.setUpdatedAt(Instant.now());
        productServiceRepository.save(service);

        platformSecretService.upsertManagedSecret(
            secretName,
            serviceSecret,
            java.util.Map.of("serviceRef", serviceRef, "purpose", "PRODUCT_SERVICE_SECRET")
        );

        ShopifyStoreConnectionEntity store = new ShopifyStoreConnectionEntity();
        store.setId("shp-123");
        store.setShopDomain(shopDomain);
        store.setDisplayName("Alpha");
        store.setProductServiceId("psv-123");
        store.setInstallStatus("INSTALLED");
        store.setSyncStatus("NOT_SYNCED");
        store.setSourceReadinessStatus("NOT_RUN");
        store.setWidgetStatus("NOT_ENABLED");
        store.setOnboardingStatus("CONNECTED");
        store.setProductsEnabled(true);
        store.setCollectionsEnabled(true);
        store.setPagesEnabled(true);
        store.setPoliciesEnabled(true);
        store.setDetailsJson("{}");
        store.setCreatedAt(Instant.now());
        store.setUpdatedAt(Instant.now());
        shopifyStoreConnectionRepository.save(store);

        mockMvc.perform(get("/api/shopify/stores")
                .header("X-PLATFORM-API-KEY", serviceSecret))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()", is(1)))
            .andExpect(jsonPath("$[0].shopDomain", is(shopDomain)));

        mockMvc.perform(get("/api/deployments")
                .header("X-PLATFORM-API-KEY", serviceSecret))
            .andExpect(status().isUnauthorized());
    }
}
