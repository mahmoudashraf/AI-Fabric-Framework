package com.ai.fabric.platform.backend.thinker.web;

import com.ai.fabric.platform.backend.thinker.model.ThinkerResolverModels.ShopifyThinkerHealthSummary;
import com.ai.fabric.platform.backend.thinker.service.ThinkerResolverService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/shopify/stores")
public class ThinkerResolverShopifyController {

    private final ThinkerResolverService service;

    public ThinkerResolverShopifyController(ThinkerResolverService service) {
        this.service = service;
    }

    @GetMapping("/{shopDomain}/thinker-health")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR','PLATFORM_PRODUCT_SERVICE')")
    public ShopifyThinkerHealthSummary thinkerHealth(@PathVariable String shopDomain) {
        return service.shopifyThinkerHealth(shopDomain);
    }
}
