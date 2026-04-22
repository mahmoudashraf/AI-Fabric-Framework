package com.ai.fabric.platform.backend.shopify.service;

import com.ai.fabric.platform.backend.productservice.entity.PlatformManagedProductServiceEntity;
import com.ai.fabric.platform.backend.productservice.repository.PlatformManagedProductServiceRepository;
import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.ai.fabric.platform.backend.shopify.entity.ShopifyStoreConnectionEntity;
import com.ai.fabric.platform.backend.shopify.model.UpsertShopifyStoreConnectionRequest;
import com.ai.fabric.platform.backend.shopify.repository.ShopifyStoreConnectionRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("shopifyStorePlatformAccessEvaluator")
public class ShopifyStorePlatformAccessEvaluator {

    private final ShopifyStoreConnectionRepository storeRepository;
    private final PlatformManagedProductServiceRepository productServiceRepository;

    public ShopifyStorePlatformAccessEvaluator(ShopifyStoreConnectionRepository storeRepository,
                                               PlatformManagedProductServiceRepository productServiceRepository) {
        this.storeRepository = storeRepository;
        this.productServiceRepository = productServiceRepository;
    }

    public boolean canList(Authentication authentication) {
        PlatformPrincipal principal = principal(authentication);
        return isPrivileged(principal) || isProductService(principal);
    }

    public boolean canAccess(Authentication authentication, String shopDomain) {
        PlatformPrincipal principal = principal(authentication);
        if (isPrivileged(principal)) {
            return true;
        }
        if (!isProductService(principal)) {
            return false;
        }
        ShopifyStoreConnectionEntity store = storeRepository.findByShopDomainIgnoreCase(normalize(shopDomain)).orElse(null);
        return store != null && sameService(principal.actorId(), store.getProductServiceId());
    }

    public boolean canUpsert(Authentication authentication, UpsertShopifyStoreConnectionRequest request) {
        PlatformPrincipal principal = principal(authentication);
        if (isPrivileged(principal)) {
            return true;
        }
        if (!isProductService(principal) || request == null) {
            return false;
        }
        return equalsIgnoreCase(principal.actorId(), request.productServiceRef());
    }

    public boolean canAccessConsumer(Authentication authentication, String consumerId) {
        PlatformPrincipal principal = principal(authentication);
        if (isPrivileged(principal)) {
            return true;
        }
        if (!isProductService(principal)) {
            return false;
        }
        ShopifyStoreConnectionEntity store = storeRepository.findByConsumerIdIgnoreCase(normalize(consumerId)).orElse(null);
        return store != null && sameService(principal.actorId(), store.getProductServiceId());
    }

    private boolean sameService(String serviceRef, String productServiceId) {
        PlatformManagedProductServiceEntity service = productServiceRepository.findByServiceRefIgnoreCase(normalize(serviceRef)).orElse(null);
        return service != null && equalsIgnoreCase(service.getId(), productServiceId);
    }

    private PlatformPrincipal principal(Authentication authentication) {
        Object candidate = authentication == null ? null : authentication.getPrincipal();
        return candidate instanceof PlatformPrincipal platformPrincipal ? platformPrincipal : null;
    }

    private boolean isPrivileged(PlatformPrincipal principal) {
        return principal != null
            && (principal.role() == PlatformRole.PLATFORM_ADMIN || principal.role() == PlatformRole.PLATFORM_OPERATOR);
    }

    private boolean isProductService(PlatformPrincipal principal) {
        return principal != null && principal.role() == PlatformRole.PLATFORM_PRODUCT_SERVICE;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private boolean equalsIgnoreCase(String left, String right) {
        return normalize(left).equals(normalize(right));
    }
}
