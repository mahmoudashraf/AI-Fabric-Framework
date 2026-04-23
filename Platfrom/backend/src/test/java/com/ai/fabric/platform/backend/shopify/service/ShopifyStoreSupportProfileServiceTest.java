package com.ai.fabric.platform.backend.shopify.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.shopify.entity.ShopifyStoreConnectionEntity;
import com.ai.fabric.platform.backend.shopify.model.UpdateShopifyStoreSupportProfileRequest;
import com.ai.fabric.platform.backend.shopify.repository.ShopifyStoreConnectionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ShopifyStoreSupportProfileServiceTest {

    @Test
    void updatePersistsMerchantSupportProfile() {
        ShopifyStoreConnectionRepository repository = mock(ShopifyStoreConnectionRepository.class);
        PlatformAuditService auditService = mock(PlatformAuditService.class);
        ShopifyStoreSourcePreflightSupport support = new ShopifyStoreSourcePreflightSupport(new ObjectMapper());

        ShopifyStoreConnectionEntity store = store();

        when(repository.findByShopDomainIgnoreCase("demo.myshopify.com")).thenReturn(Optional.of(store));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ShopifyStoreSupportProfileService service =
            new ShopifyStoreSupportProfileService(repository, support, auditService);

        var result = service.update(
            "demo.myshopify.com",
            new UpdateShopifyStoreSupportProfileRequest(
                "support@demo.test",
                "https://demo.test/contact",
                "https://demo.test/help",
                "/pages/order-help",
                "Refund approvals and address changes still require the merchant support team."
            )
        );

        assertThat(result.merchantHandoffConfigured()).isTrue();
        assertThat(result.contactEmail()).isEqualTo("support@demo.test");
        assertThat(result.contactUrl()).isEqualTo("https://demo.test/contact");
        assertThat(result.helpCenterUrl()).isEqualTo("https://demo.test/help");
        assertThat(result.orderLookupPageUrl()).isEqualTo("/pages/order-help");
        assertThat(store.getDetailsJson()).contains("\"contactEmail\":\"support@demo.test\"");
        assertThat(store.getDetailsJson()).contains("\"orderLookupPageUrl\":\"/pages/order-help\"");
    }

    private ShopifyStoreConnectionEntity store() {
        ShopifyStoreConnectionEntity entity = new ShopifyStoreConnectionEntity();
        entity.setId("shp-123");
        entity.setShopDomain("demo.myshopify.com");
        entity.setDisplayName("Demo Shop");
        entity.setProductServiceId("psv-123");
        entity.setInstallStatus("INSTALLED");
        entity.setSyncStatus("SYNCED");
        entity.setSourceReadinessStatus("READY");
        entity.setWidgetStatus("ENABLED");
        entity.setOnboardingStatus("LIVE");
        entity.setProductsEnabled(true);
        entity.setCollectionsEnabled(true);
        entity.setPagesEnabled(true);
        entity.setPoliciesEnabled(true);
        entity.setArticlesEnabled(true);
        entity.setMetaobjectsEnabled(true);
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        return entity;
    }
}
