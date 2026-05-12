package com.ai.fabric.platform.backend.shopify.service;

import com.ai.fabric.platform.backend.productservice.entity.PlatformManagedProductServiceEntity;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.shopify.entity.ShopifyStoreConnectionEntity;
import com.ai.fabric.platform.backend.shopify.model.RecordShopifyStoreBillingStateRequest;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreConnectionSummary;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreGovernedActionAuditSummary;
import com.ai.fabric.platform.backend.productservice.repository.PlatformManagedProductServiceRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriUtils;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.CONFLICT;

@Component
public class ShopifyBridgeAdminClient {

    private static final String BRIDGE_ADMIN_API_KEY_HEADER = "X-BRIDGE-API-KEY";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(15);

    private final PlatformManagedProductServiceRepository productServiceRepository;
    private final PlatformSecretService platformSecretService;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public ShopifyBridgeAdminClient(PlatformManagedProductServiceRepository productServiceRepository,
                                    PlatformSecretService platformSecretService,
                                    RestClient.Builder restClientBuilder,
                                    ObjectMapper objectMapper) {
        this.productServiceRepository = productServiceRepository;
        this.platformSecretService = platformSecretService;
        this.restClient = restClientBuilder
            .requestFactory(requestFactory())
            .build();
        this.objectMapper = objectMapper;
    }

    private SimpleClientHttpRequestFactory requestFactory() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        return requestFactory;
    }

    public ShopifyStoreVectorizationSourcePageSnapshot fetchPage(ShopifyStoreConnectionEntity store,
                                                                 String entityType,
                                                                 String cursor,
                                                                 Integer limit) {
        PlatformManagedProductServiceEntity service = requireService(store);
        String response = restClient.get()
            .uri(sourcePageUri(service, store.getShopDomain(), entityType, cursor, limit))
            .headers(headers -> headers.set(BRIDGE_ADMIN_API_KEY_HEADER, requireAdminKey(service)))
            .retrieve()
            .body(String.class);
        try {
            JsonNode root = objectMapper.readTree(defaultJson(response));
            List<ShopifyStoreVectorizationSourceRecordSnapshot> items = new ArrayList<>();
            JsonNode itemArray = root.path("items");
            if (itemArray.isArray()) {
                for (JsonNode item : itemArray) {
                    items.add(toSnapshot(item, entityType));
                }
            }
            return new ShopifyStoreVectorizationSourcePageSnapshot(
                root.path("entityType").asText(entityType),
                root.path("totalCount").asInt(items.size()),
                root.path("hasMore").asBoolean(false),
                text(root, "nextCursor"),
                List.copyOf(items)
            );
        } catch (Exception ex) {
            throw new ResponseStatusException(BAD_GATEWAY, "Failed to parse Shopify Bridge vectorization page response.", ex);
        }
    }

    public ShopifyStoreVectorizationSourceRecordSnapshot fetchRecord(ShopifyStoreConnectionEntity store,
                                                                     String sourceCategory,
                                                                     String sourceObjectId) {
        PlatformManagedProductServiceEntity service = requireService(store);
        String normalizedCategory = ShopifyStoreVectorizationConstants.normalizeSourceCategory(sourceCategory);
        if (!StringUtils.hasText(sourceObjectId)) {
            throw new ResponseStatusException(CONFLICT, "Shopify source object id is required for canonical record lookup.");
        }
        String response = restClient.get()
            .uri(recordUri(service, store.getShopDomain(), normalizedCategory, sourceObjectId))
            .headers(headers -> headers.set(BRIDGE_ADMIN_API_KEY_HEADER, requireAdminKey(service)))
            .retrieve()
            .body(String.class);
        try {
            JsonNode root = objectMapper.readTree(defaultJson(response));
            return toSnapshot(root, ShopifyStoreVectorizationConstants.entityTypeForCategory(normalizedCategory));
        } catch (Exception ex) {
            throw new ResponseStatusException(BAD_GATEWAY, "Failed to parse Shopify Bridge vectorization record response.", ex);
        }
    }

    public void runSync(ShopifyStoreConnectionEntity store) {
        PlatformManagedProductServiceEntity service = requireService(store);
        restClient.post()
            .uri(syncUri(service, store.getShopDomain()))
            .headers(headers -> headers.set(BRIDGE_ADMIN_API_KEY_HEADER, requireAdminKey(service)))
            .retrieve()
            .toBodilessEntity();
    }

    public void recordBillingState(ShopifyStoreConnectionSummary store,
                                   RecordShopifyStoreBillingStateRequest request) {
        PlatformManagedProductServiceEntity service = requireService(store);
        restClient.post()
            .uri(billingStateUri(service, store.shopDomain()))
            .headers(headers -> headers.set(BRIDGE_ADMIN_API_KEY_HEADER, requireAdminKey(service)))
            .body(Map.of(
                "tierKey", request == null || request.tierKey() == null ? "" : request.tierKey(),
                "status", request == null || request.status() == null ? "" : request.status(),
                "subscriptionId", request == null || request.subscriptionId() == null ? "" : request.subscriptionId(),
                "subscriptionName", request == null || request.subscriptionName() == null ? "" : request.subscriptionName(),
                "reason", request == null || request.reason() == null ? "" : request.reason()
            ))
            .retrieve()
            .toBodilessEntity();
    }

    public List<ShopifyStoreGovernedActionAuditSummary> fetchRecentGovernedActions(ShopifyStoreConnectionEntity store,
                                                                                   int limit) {
        PlatformManagedProductServiceEntity service = requireService(store);
        String response = restClient.get()
            .uri(recentActionsUri(service, store.getShopDomain(), limit))
            .headers(headers -> headers.set(BRIDGE_ADMIN_API_KEY_HEADER, requireAdminKey(service)))
            .retrieve()
            .body(String.class);
        try {
            JsonNode root = objectMapper.readTree(defaultJsonArray(response));
            if (!root.isArray()) {
                throw new IllegalStateException("Shopify Bridge governed action response is not an array.");
            }
            List<ShopifyStoreGovernedActionAuditSummary> items = new ArrayList<>();
            for (JsonNode item : root) {
                items.add(new ShopifyStoreGovernedActionAuditSummary(
                    text(item, "id"),
                    text(item, "actionType"),
                    text(item, "actionPackage"),
                    text(item, "surfaceId"),
                    text(item, "pageType"),
                    text(item, "productHandle"),
                    text(item, "productTitle"),
                    text(item, "variantId"),
                    integerValue(item, "requestedQuantity"),
                    integerValue(item, "targetQuantity"),
                    integerValue(item, "resultingQuantity"),
                    item.path("confirmationRequired").asBoolean(false),
                    item.path("confirmationAccepted").asBoolean(false),
                    text(item, "shopperSessionRef"),
                    text(item, "status"),
                    text(item, "message"),
                    instantValue(item, "createdAt"),
                    instantValue(item, "expiresAt"),
                    instantValue(item, "completedAt")
                ));
            }
            return List.copyOf(items);
        } catch (Exception ex) {
            throw new ResponseStatusException(BAD_GATEWAY, "Failed to parse Shopify Bridge governed action response.", ex);
        }
    }

    private ShopifyStoreVectorizationSourceRecordSnapshot toSnapshot(JsonNode node, String entityType) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        putIfPresent(values, "id", text(node, "id"));
        putIfPresent(values, "title", text(node, "title"));
        putIfPresent(values, "content", text(node, "content"));
        putIfPresent(values, "sourceCategory", text(node, "sourceCategory"));
        putIfPresent(values, "documentType", text(node, "documentType"));
        putIfPresent(values, "storefrontUrl", text(node, "storefrontUrl"));
        putIfPresent(values, "handle", text(node, "handle"));
        putIfPresent(values, "vendor", text(node, "vendor"));
        putIfPresent(values, "productType", text(node, "productType"));
        putIfPresent(values, "policyType", text(node, "policyType"));
        putIfPresent(values, "priceRange", text(node, "priceRange"));
        putIfPresent(values, "currencyCode", text(node, "currencyCode"));
        putIfPresent(values, "availability", text(node, "availability"));
        putIfPresent(values, "variantSummary", text(node, "variantSummary"));
        putIfPresent(values, "product_variant_id", text(node, "product_variant_id"));
        putIfPresent(values, "productVariantId", text(node, "productVariantId"));
        putIfPresent(values, "firstAvailableVariantId", text(node, "firstAvailableVariantId"));
        putIfPresent(values, "firstAvailableVariantTitle", text(node, "firstAvailableVariantTitle"));
        putIfPresent(values, "variantCount", integerValue(node, "variantCount"));
        putIfPresent(values, "totalInventory", integerValue(node, "totalInventory"));
        putIfPresent(values, "availableVariantCount", integerValue(node, "availableVariantCount"));
        putIfPresent(values, "updatedAt", text(node, "updatedAt"));
        return new ShopifyStoreVectorizationSourceRecordSnapshot(
            text(node, "id"),
            text(node, "sourceCategory"),
            entityType,
            text(node, "updatedAt"),
            Map.copyOf(values)
        );
    }

    private URI sourcePageUri(PlatformManagedProductServiceEntity service,
                              String shopDomain,
                              String entityType,
                              String cursor,
                              Integer limit) {
        StringBuilder uri = new StringBuilder(joinUrl(service.getBaseUrl(), "/api/admin/stores/" + encodePath(shopDomain)
            + "/vectorization-source/" + encodePath(entityType)));
        boolean hasQuery = false;
        if (StringUtils.hasText(cursor)) {
            uri.append(hasQuery ? "&" : "?").append("cursor=").append(encodeQuery(cursor));
            hasQuery = true;
        }
        if (limit != null && limit > 0) {
            uri.append(hasQuery ? "&" : "?").append("limit=").append(limit);
        }
        return URI.create(uri.toString());
    }

    private URI recordUri(PlatformManagedProductServiceEntity service,
                          String shopDomain,
                          String sourceCategory,
                          String sourceObjectId) {
        return URI.create(
            joinUrl(
                service.getBaseUrl(),
                "/api/admin/stores/" + encodePath(shopDomain)
                    + "/vectorization-source-record?sourceCategory=" + encodeQuery(sourceCategory)
                    + "&sourceObjectId=" + encodeQuery(sourceObjectId)
            )
        );
    }

    private URI syncUri(PlatformManagedProductServiceEntity service, String shopDomain) {
        return URI.create(joinUrl(service.getBaseUrl(), "/api/admin/stores/" + encodePath(shopDomain) + "/run-sync"));
    }

    private URI recentActionsUri(PlatformManagedProductServiceEntity service,
                                 String shopDomain,
                                 int limit) {
        int boundedLimit = Math.max(1, Math.min(25, limit));
        return URI.create(joinUrl(
            service.getBaseUrl(),
            "/api/admin/stores/" + encodePath(shopDomain) + "/actions/recent?limit=" + boundedLimit
        ));
    }

    private URI billingStateUri(PlatformManagedProductServiceEntity service, String shopDomain) {
        return URI.create(joinUrl(service.getBaseUrl(), "/api/admin/stores/" + encodePath(shopDomain) + "/billing-state"));
    }

    private PlatformManagedProductServiceEntity requireService(ShopifyStoreConnectionEntity store) {
        String productServiceId = store == null ? null : store.getProductServiceId();
        if (!StringUtils.hasText(productServiceId)) {
            throw new ResponseStatusException(CONFLICT, "Shopify Bridge service binding is not configured.");
        }
        return productServiceRepository.findById(productServiceId)
            .orElseThrow(() -> new ResponseStatusException(CONFLICT, "Shopify Bridge service binding could not be resolved."));
    }

    private PlatformManagedProductServiceEntity requireService(ShopifyStoreConnectionSummary store) {
        String productServiceId = store == null ? null : store.productServiceId();
        if (!StringUtils.hasText(productServiceId)) {
            throw new ResponseStatusException(CONFLICT, "Shopify Bridge service binding is not configured.");
        }
        return productServiceRepository.findById(productServiceId)
            .orElseThrow(() -> new ResponseStatusException(CONFLICT, "Shopify Bridge service binding could not be resolved."));
    }

    private String requireAdminKey(PlatformManagedProductServiceEntity service) {
        if (service == null || !StringUtils.hasText(service.getSecretName())) {
            throw new ResponseStatusException(CONFLICT, "Shopify Bridge admin secret is not configured.");
        }
        String value = platformSecretService.resolveSecret(service.getSecretName());
        if (!StringUtils.hasText(value)) {
            throw new ResponseStatusException(CONFLICT, "Shopify Bridge admin secret is not available.");
        }
        return value.trim();
    }

    private String joinUrl(String baseUrl, String path) {
        if (!StringUtils.hasText(baseUrl)) {
            throw new ResponseStatusException(CONFLICT, "Shopify Bridge base URL is not configured.");
        }
        String normalizedBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        return normalizedBase + normalizedPath;
    }

    private String encodePath(String value) {
        return UriUtils.encodePathSegment(value, StandardCharsets.UTF_8);
    }

    private String encodeQuery(String value) {
        return UriUtils.encodeQueryParam(value, StandardCharsets.UTF_8);
    }

    private String defaultJson(String value) {
        return StringUtils.hasText(value) ? value : "{}";
    }

    private String defaultJsonArray(String value) {
        return StringUtils.hasText(value) ? value : "[]";
    }

    private void putIfPresent(Map<String, Object> target, String key, String value) {
        if (value != null && !value.isBlank()) {
            target.put(key, value);
        }
    }

    private void putIfPresent(Map<String, Object> target, String key, Integer value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private String text(JsonNode node, String fieldName) {
        String value = node.path(fieldName).asText(null);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private Integer integerValue(JsonNode node, String fieldName) {
        return node.hasNonNull(fieldName) ? node.path(fieldName).asInt() : null;
    }

    private Instant instantValue(JsonNode node, String fieldName) {
        String value = text(node, fieldName);
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (Exception ex) {
            return null;
        }
    }
}
