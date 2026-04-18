package com.ai.fabric.product.shopify.bridge.client.platform;

import com.ai.fabric.product.shopify.bridge.config.ShopifyBridgeProperties;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreBootstrapResponse;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeRecordSourcePreflightRequest;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeRecordSyncStatusRequest;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeRecordWidgetStatusRequest;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeUpsertStoreRequest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

@Component
public class PlatformShopifyStoreClient {

    private static final ParameterizedTypeReference<List<ShopifyBridgeStoreSummary>> STORE_LIST_TYPE =
        new ParameterizedTypeReference<>() { };

    private final ShopifyBridgeProperties properties;
    private final RestClient restClient;

    public PlatformShopifyStoreClient(RestClient.Builder restClientBuilder,
                                      ShopifyBridgeProperties properties) {
        this.properties = properties;
        this.restClient = restClientBuilder.build();
    }

    public List<ShopifyBridgeStoreSummary> listStores() {
        return restClient.get()
            .uri(requirePlatformBaseUrl() + "/api/shopify/stores")
            .headers(headers -> headers.set(properties.platformAdminApiKeyHeader(), requirePlatformAdminApiKey()))
            .retrieve()
            .body(STORE_LIST_TYPE);
    }

    public ShopifyBridgeStoreSummary getStore(String shopDomain) {
        return restClient.get()
            .uri(requirePlatformBaseUrl() + "/api/shopify/stores/" + encodePath(shopDomain))
            .headers(headers -> headers.set(properties.platformAdminApiKeyHeader(), requirePlatformAdminApiKey()))
            .retrieve()
            .body(ShopifyBridgeStoreSummary.class);
    }

    public ShopifyBridgeStoreSummary upsertStore(ShopifyBridgeUpsertStoreRequest request) {
        return restClient.post()
            .uri(requirePlatformBaseUrl() + "/api/shopify/stores")
            .headers(headers -> headers.set(properties.platformAdminApiKeyHeader(), requirePlatformAdminApiKey()))
            .body(request)
            .retrieve()
            .body(ShopifyBridgeStoreSummary.class);
    }

    public ShopifyBridgeStoreBootstrapResponse bootstrap(String shopDomain) {
        return restClient.post()
            .uri(requirePlatformBaseUrl() + "/api/shopify/stores/" + encodePath(shopDomain) + "/bootstrap")
            .headers(headers -> headers.set(properties.platformAdminApiKeyHeader(), requirePlatformAdminApiKey()))
            .retrieve()
            .body(ShopifyBridgeStoreBootstrapResponse.class);
    }

    public ShopifyBridgeStoreSummary recordSourcePreflight(String shopDomain, ShopifyBridgeRecordSourcePreflightRequest request) {
        return restClient.post()
            .uri(requirePlatformBaseUrl() + "/api/shopify/stores/" + encodePath(shopDomain) + "/source-preflight")
            .headers(headers -> headers.set(properties.platformAdminApiKeyHeader(), requirePlatformAdminApiKey()))
            .body(request)
            .retrieve()
            .body(ShopifyBridgeStoreSummary.class);
    }

    public ShopifyBridgeStoreSummary recordSyncStatus(String shopDomain, ShopifyBridgeRecordSyncStatusRequest request) {
        return restClient.post()
            .uri(requirePlatformBaseUrl() + "/api/shopify/stores/" + encodePath(shopDomain) + "/sync-status")
            .headers(headers -> headers.set(properties.platformAdminApiKeyHeader(), requirePlatformAdminApiKey()))
            .body(request)
            .retrieve()
            .body(ShopifyBridgeStoreSummary.class);
    }

    public ShopifyBridgeStoreSummary recordWidgetStatus(String shopDomain, ShopifyBridgeRecordWidgetStatusRequest request) {
        return restClient.post()
            .uri(requirePlatformBaseUrl() + "/api/shopify/stores/" + encodePath(shopDomain) + "/widget-status")
            .headers(headers -> headers.set(properties.platformAdminApiKeyHeader(), requirePlatformAdminApiKey()))
            .body(request)
            .retrieve()
            .body(ShopifyBridgeStoreSummary.class);
    }

    private String requirePlatformBaseUrl() {
        if (properties.platformBaseUrl().isBlank()) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Shopify Bridge platform base URL is not configured.");
        }
        return properties.platformBaseUrl().endsWith("/")
            ? properties.platformBaseUrl().substring(0, properties.platformBaseUrl().length() - 1)
            : properties.platformBaseUrl();
    }

    private String requirePlatformAdminApiKey() {
        if (properties.platformAdminApiKey().isBlank()) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Shopify Bridge platform admin API key is not configured.");
        }
        return properties.platformAdminApiKey();
    }

    private String encodePath(String value) {
        return UriUtils.encodePathSegment(value == null ? "" : value.trim(), StandardCharsets.UTF_8);
    }
}
