package com.ai.fabric.platform.backend.shopify.service;

import com.ai.fabric.platform.backend.shopify.entity.ShopifyStoreConnectionEntity;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreGovernedActionAuditSummary;
import com.ai.fabric.platform.backend.shopify.repository.ShopifyStoreConnectionRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class ShopifyStoreGovernedActionService {

    private final ShopifyStoreConnectionRepository repository;
    private final ShopifyBridgeAdminClient bridgeAdminClient;

    public ShopifyStoreGovernedActionService(ShopifyStoreConnectionRepository repository,
                                             ShopifyBridgeAdminClient bridgeAdminClient) {
        this.repository = repository;
        this.bridgeAdminClient = bridgeAdminClient;
    }

    public List<ShopifyStoreGovernedActionAuditSummary> recentActions(String shopDomain, int limit) {
        ShopifyStoreConnectionEntity store = repository.findByShopDomainIgnoreCase(normalizeShopDomain(shopDomain))
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Shopify store connection not found: " + shopDomain));
        return bridgeAdminClient.fetchRecentGovernedActions(store, limit);
    }

    private String normalizeShopDomain(String value) {
        if (!StringUtils.hasText(value)) {
            throw new ResponseStatusException(NOT_FOUND, "Shopify store connection not found.");
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
