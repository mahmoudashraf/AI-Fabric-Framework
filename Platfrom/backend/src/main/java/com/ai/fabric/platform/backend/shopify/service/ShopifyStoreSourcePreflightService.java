package com.ai.fabric.platform.backend.shopify.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.shopify.entity.ShopifyStoreConnectionEntity;
import com.ai.fabric.platform.backend.shopify.model.RecordShopifyStoreSourcePreflightRequest;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreConnectionSummary;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreSourcePreflightCategorySummary;
import com.ai.fabric.platform.backend.shopify.repository.ShopifyStoreConnectionRepository;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class ShopifyStoreSourcePreflightService {

    private static final Set<String> SUPPORTED_CATEGORIES = Set.of("products", "collections", "pages", "policies");

    private final ShopifyStoreConnectionRepository repository;
    private final ShopifyStoreConnectionService shopifyStoreConnectionService;
    private final PlatformAuditService platformAuditService;
    private final ShopifyStoreSourcePreflightSupport support;

    public ShopifyStoreSourcePreflightService(ShopifyStoreConnectionRepository repository,
                                              ShopifyStoreConnectionService shopifyStoreConnectionService,
                                              PlatformAuditService platformAuditService,
                                              ShopifyStoreSourcePreflightSupport support) {
        this.repository = repository;
        this.shopifyStoreConnectionService = shopifyStoreConnectionService;
        this.platformAuditService = platformAuditService;
        this.support = support;
    }

    @Transactional
    public ShopifyStoreConnectionSummary record(String shopDomain, RecordShopifyStoreSourcePreflightRequest request) {
        ShopifyStoreConnectionEntity store = repository.findByShopDomainIgnoreCase(normalizeShopDomain(shopDomain))
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Shopify store connection not found: " + shopDomain));
        List<ShopifyStoreSourcePreflightCategorySummary> categories = normalizeCategories(store, request);
        String overallStatus = overallStatus(categories);
        Instant now = Instant.now();

        ObjectNode details = support.mutableDetails(store.getDetailsJson());
        ObjectNode sourcePreflight = details.putObject("sourcePreflight");
        sourcePreflight.put("overallStatus", overallStatus);
        sourcePreflight.put("checkedAt", now.toString());
        ArrayNode categoryNodes = sourcePreflight.putArray("categories");
        categories.forEach(category -> {
            ObjectNode node = categoryNodes.addObject();
            node.put("category", category.category());
            node.put("enabled", category.enabled());
            node.put("status", category.status());
            node.put("itemCount", category.itemCount());
            if (hasText(category.message())) {
                node.put("message", category.message());
            }
        });
        store.setSourceReadinessStatus(overallStatus);
        store.setLastSourcePreflightAt(now);
        if ("READY".equalsIgnoreCase(overallStatus) && "PLATFORM_BOOTSTRAPPED".equalsIgnoreCase(store.getOnboardingStatus())) {
            store.setOnboardingStatus("PREFLIGHT_READY");
        } else if ("BLOCKED".equalsIgnoreCase(overallStatus)) {
            store.setOnboardingStatus("BLOCKED");
        }
        store.setDetailsJson(support.writeJson(details));
        store.setUpdatedAt(now);
        repository.save(store);

        platformAuditService.record(
            "SHOPIFY_STORE_SOURCE_PREFLIGHT_RECORDED",
            "SHOPIFY_STORE_CONNECTION",
            store.getShopDomain(),
            Map.of(
                "shopDomain", store.getShopDomain(),
                "overallStatus", overallStatus,
                "categoryCount", Integer.toString(categories.size())
            )
        );

        return shopifyStoreConnectionService.getConnection(store.getShopDomain());
    }

    private List<ShopifyStoreSourcePreflightCategorySummary> normalizeCategories(ShopifyStoreConnectionEntity store,
                                                                                  RecordShopifyStoreSourcePreflightRequest request) {
        if (request == null || request.categories() == null || request.categories().isEmpty()) {
            throw new ResponseStatusException(CONFLICT, "At least one source preflight category is required.");
        }
        return request.categories().stream()
            .map(category -> normalizeCategory(store, category))
            .toList();
    }

    private ShopifyStoreSourcePreflightCategorySummary normalizeCategory(ShopifyStoreConnectionEntity store,
                                                                         ShopifyStoreSourcePreflightCategorySummary category) {
        String normalizedCategory = category.category() == null ? "" : category.category().trim().toLowerCase(Locale.ROOT);
        if (!SUPPORTED_CATEGORIES.contains(normalizedCategory)) {
            throw new ResponseStatusException(CONFLICT, "Unsupported Shopify source preflight category: " + category.category());
        }
        boolean expectedEnabled = switch (normalizedCategory) {
            case "products" -> store.isProductsEnabled();
            case "collections" -> store.isCollectionsEnabled();
            case "pages" -> store.isPagesEnabled();
            case "policies" -> store.isPoliciesEnabled();
            default -> false;
        };
        if (expectedEnabled != category.enabled()) {
            throw new ResponseStatusException(CONFLICT, "Preflight enabled flag does not match store toggle for category: " + normalizedCategory);
        }
        String normalizedStatus = normalizeStatus(category.status());
        return new ShopifyStoreSourcePreflightCategorySummary(
            normalizedCategory,
            category.enabled(),
            normalizedStatus,
            Math.max(category.itemCount(), 0),
            hasText(category.message()) ? category.message().trim() : null
        );
    }

    private String overallStatus(List<ShopifyStoreSourcePreflightCategorySummary> categories) {
        List<ShopifyStoreSourcePreflightCategorySummary> enabled = categories.stream()
            .filter(ShopifyStoreSourcePreflightCategorySummary::enabled)
            .toList();
        if (enabled.isEmpty()) {
            return "NOT_RUN";
        }
        if (enabled.stream().anyMatch(category -> "BLOCKED".equals(category.status()) || "FAILED".equals(category.status()))) {
            return "BLOCKED";
        }
        if (enabled.stream().allMatch(category -> "READY".equals(category.status()))) {
            return "READY";
        }
        return "PARTIAL";
    }

    private String normalizeShopDomain(String shopDomain) {
        if (!hasText(shopDomain)) {
            throw new ResponseStatusException(CONFLICT, "shopDomain is required.");
        }
        return shopDomain.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeStatus(String status) {
        if (!hasText(status)) {
            throw new ResponseStatusException(CONFLICT, "Preflight category status is required.");
        }
        return status.trim().toUpperCase(Locale.ROOT);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
