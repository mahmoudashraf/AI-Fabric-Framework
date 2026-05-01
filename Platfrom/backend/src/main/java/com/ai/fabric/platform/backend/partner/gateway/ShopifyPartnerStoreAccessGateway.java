package com.ai.fabric.platform.backend.partner.gateway;

import com.ai.fabric.platform.backend.partner.model.PartnerProductPackageSummary;
import com.ai.fabric.platform.backend.shopify.entity.ShopifyStoreConnectionEntity;
import com.ai.fabric.platform.backend.shopify.repository.ShopifyStoreConnectionRepository;
import com.ai.fabric.platform.backend.shopify.service.ShopifyStoreSourcePreflightSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Component
public class ShopifyPartnerStoreAccessGateway implements PartnerStoreAccessGateway {

    private static final List<String> DEFAULT_STORE_ENABLED_SURFACES = List.of(
        "ai-search",
        "contextual-pill",
        "product-insight",
        "policy-strip",
        "product-faq",
        "comparison"
    );

    private final ShopifyStoreConnectionRepository storeConnectionRepository;
    private final ShopifyStoreSourcePreflightSupport sourcePreflightSupport;
    private final ObjectMapper objectMapper;

    public ShopifyPartnerStoreAccessGateway(ShopifyStoreConnectionRepository storeConnectionRepository,
                                            ShopifyStoreSourcePreflightSupport sourcePreflightSupport,
                                            ObjectMapper objectMapper) {
        this.storeConnectionRepository = storeConnectionRepository;
        this.sourcePreflightSupport = sourcePreflightSupport;
        this.objectMapper = objectMapper;
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

    @Override
    public List<PartnerShopifyStoreReadModel> listInstalledStores(String query, int limit) {
        String normalizedQuery = StringUtils.hasText(query) ? query.trim().toLowerCase(Locale.ROOT) : null;
        int boundedLimit = Math.max(1, Math.min(limit, 100));
        return storeConnectionRepository.findAllByOrderByCreatedAtDesc().stream()
            .filter(store -> "INSTALLED".equalsIgnoreCase(store.getInstallStatus()))
            .filter(store -> normalizedQuery == null
                || containsIgnoreCase(store.getShopDomain(), normalizedQuery)
                || containsIgnoreCase(store.getDisplayName(), normalizedQuery))
            .sorted(Comparator.comparing(ShopifyStoreConnectionEntity::getShopDomain, String.CASE_INSENSITIVE_ORDER))
            .limit(boundedLimit)
            .map(this::toReadModel)
            .toList();
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
            categories,
            enabledSurfaces(entity),
            packageProfile(entity.getDetailsJson())
        );
    }

    private List<String> enabledSurfaces(ShopifyStoreConnectionEntity entity) {
        var widget = sourcePreflightSupport.summarizeWidget(entity.getDetailsJson());
        if (widget != null
            && widget.settings() != null
            && widget.settings().enabledSurfaces() != null
            && !widget.settings().enabledSurfaces().isEmpty()) {
            return widget.settings().enabledSurfaces();
        }
        return DEFAULT_STORE_ENABLED_SURFACES;
    }

    private PartnerProductPackageSummary packageProfile(String detailsJson) {
        if (!StringUtils.hasText(detailsJson)) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(detailsJson);
            JsonNode state = root.path("packageState");
            if (!state.isObject()) {
                return null;
            }
            return new PartnerProductPackageSummary(
                text(state, "profileKey"),
                text(state, "packageKey"),
                text(state, "tierKey"),
                text(state, "displayName"),
                text(state, "costPosture"),
                text(state, "verificationPackId"),
                parseInstant(text(state, "lastReconciledAt"))
            );
        } catch (Exception ex) {
            return null;
        }
    }

    private Instant parseInstant(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Instant.parse(value.trim());
        } catch (Exception ex) {
            return null;
        }
    }

    private String text(JsonNode node, String field) {
        if (node == null || !node.path(field).isValueNode()) {
            return null;
        }
        String value = node.path(field).asText("");
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private boolean containsIgnoreCase(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }
}
