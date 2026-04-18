package com.ai.fabric.platform.backend.shopify.web;

import com.ai.fabric.platform.backend.shopify.model.BootstrapShopifyStoreRequest;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreBootstrapSummary;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreConnectionSummary;
import com.ai.fabric.platform.backend.shopify.model.UpsertShopifyStoreConnectionRequest;
import com.ai.fabric.platform.backend.shopify.service.ShopifyStoreBootstrapService;
import com.ai.fabric.platform.backend.shopify.service.ShopifyStoreConnectionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/shopify/stores")
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR')")
public class ShopifyAdminController {

    private final ShopifyStoreConnectionService shopifyStoreConnectionService;
    private final ShopifyStoreBootstrapService shopifyStoreBootstrapService;

    public ShopifyAdminController(ShopifyStoreConnectionService shopifyStoreConnectionService,
                                  ShopifyStoreBootstrapService shopifyStoreBootstrapService) {
        this.shopifyStoreConnectionService = shopifyStoreConnectionService;
        this.shopifyStoreBootstrapService = shopifyStoreBootstrapService;
    }

    @GetMapping
    public List<ShopifyStoreConnectionSummary> listStores() {
        return shopifyStoreConnectionService.listConnections();
    }

    @GetMapping("/{shopDomain}")
    public ShopifyStoreConnectionSummary getStore(@PathVariable String shopDomain) {
        return shopifyStoreConnectionService.getConnection(shopDomain);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ShopifyStoreConnectionSummary upsertStore(@Valid @RequestBody UpsertShopifyStoreConnectionRequest request) {
        return shopifyStoreConnectionService.upsertConnection(request);
    }

    @PostMapping("/{shopDomain}/bootstrap")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ShopifyStoreBootstrapSummary bootstrapStore(@PathVariable String shopDomain,
                                                       @RequestBody(required = false) BootstrapShopifyStoreRequest request) {
        return shopifyStoreBootstrapService.bootstrap(shopDomain, request);
    }
}
