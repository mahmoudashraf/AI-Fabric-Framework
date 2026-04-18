package com.ai.fabric.product.shopify.bridge.client.shopify;

import com.ai.fabric.product.shopify.bridge.config.ShopifyBridgeProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

@Component
public class ShopifyAdminGraphqlClient {

    private final ShopifyBridgeProperties properties;
    private final RestClient restClient;

    public ShopifyAdminGraphqlClient(ShopifyBridgeProperties properties,
                                     RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClient = restClientBuilder.build();
    }

    public Map<String, Object> execute(String shopDomain,
                                       String accessToken,
                                       String query) {
        if (properties.shopifyAdminApiVersion().isBlank()) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Shopify admin API version is not configured.");
        }
        Map<String, Object> response = restClient.post()
            .uri("https://" + shopDomain + "/admin/api/" + properties.shopifyAdminApiVersion() + "/graphql.json")
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .header("X-Shopify-Access-Token", accessToken)
            .body(Map.of("query", query))
            .retrieve()
            .body(new ParameterizedTypeReference<>() { });
        if (response == null) {
            throw new ResponseStatusException(BAD_GATEWAY, "Shopify Admin API returned an empty response.");
        }
        return response;
    }
}
