package com.ai.fabric.platform.backend.partner.gateway;

import com.ai.fabric.platform.backend.shopify.entity.ShopifyStoreConnectionEntity;
import com.ai.fabric.platform.backend.shopify.repository.ShopifyStoreConnectionRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class ShopifyPartnerStoreAccessGateway implements PartnerStoreAccessGateway {

    private final ShopifyStoreConnectionRepository storeConnectionRepository;

    public ShopifyPartnerStoreAccessGateway(ShopifyStoreConnectionRepository storeConnectionRepository) {
        this.storeConnectionRepository = storeConnectionRepository;
    }

    @Override
    public Optional<PartnerShopifyStoreReadModel> findByShopDomain(String shopDomain) {
        return storeConnectionRepository.findByShopDomainIgnoreCase(shopDomain).map(this::toReadModel);
    }

    @Override
    public Optional<PartnerShopifyStoreReadModel> findByStoreConnectionId(String storeConnectionId) {
        if (storeConnectionId == null || storeConnectionId.isBlank()) {
            return Optional.empty();
        }
        return storeConnectionRepository.findById(storeConnectionId).map(this::toReadModel);
    }

    private PartnerShopifyStoreReadModel toReadModel(ShopifyStoreConnectionEntity entity) {
        List<String> categories = new ArrayList<>();
        if (entity.isProductsEnabled()) {
            categories.add("products");
        }
        if (entity.isCollectionsEnabled()) {
            categories.add("collections");
        }
        if (entity.isPagesEnabled()) {
            categories.add("pages");
        }
        if (entity.isPoliciesEnabled()) {
            categories.add("policies");
        }
        if (entity.isArticlesEnabled()) {
            categories.add("articles");
        }
        if (entity.isMetaobjectsEnabled()) {
            categories.add("metaobjects");
        }
        return new PartnerShopifyStoreReadModel(
            entity.getId(),
            entity.getShopDomain(),
            entity.getDisplayName(),
            entity.getInstallStatus(),
            entity.getSyncStatus(),
            entity.getSourceReadinessStatus(),
            entity.getWidgetStatus(),
            entity.getLastSyncAt(),
            entity.getLastWebhookAt(),
            categories
        );
    }
}
