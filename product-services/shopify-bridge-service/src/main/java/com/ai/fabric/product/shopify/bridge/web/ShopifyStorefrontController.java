package com.ai.fabric.product.shopify.bridge.web;

import com.ai.fabric.product.shopify.bridge.storefront.model.ShopifyStorefrontBootstrapResponse;
import com.ai.fabric.product.shopify.bridge.storefront.service.ShopifyStorefrontBootstrapService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/storefront/shops")
public class ShopifyStorefrontController {

    private final ShopifyStorefrontBootstrapService storefrontBootstrapService;

    public ShopifyStorefrontController(ShopifyStorefrontBootstrapService storefrontBootstrapService) {
        this.storefrontBootstrapService = storefrontBootstrapService;
    }

    @GetMapping("/{shopDomain}/bootstrap")
    public ShopifyStorefrontBootstrapResponse bootstrap(@PathVariable String shopDomain) {
        return storefrontBootstrapService.bootstrap(shopDomain);
    }
}
