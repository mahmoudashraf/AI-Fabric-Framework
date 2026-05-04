package com.ai.fabric.product.shopify.bridge.action.service;

import com.ai.fabric.product.shopify.bridge.config.ShopifyCheckoutMcpProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.CONFLICT;

@Service
public class ShopifyCheckoutMcpTokenService {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final ShopifyCheckoutMcpProperties properties;
    private final Clock clock;
    private volatile CachedToken cachedToken;

    @Autowired
    public ShopifyCheckoutMcpTokenService(RestClient.Builder restClientBuilder,
                                          ObjectMapper objectMapper,
                                          ShopifyCheckoutMcpProperties properties) {
        this(restClientBuilder.build(), objectMapper, properties, Clock.systemUTC());
    }

    ShopifyCheckoutMcpTokenService(RestClient restClient,
                                   ObjectMapper objectMapper,
                                   ShopifyCheckoutMcpProperties properties,
                                   Clock clock) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.clock = clock;
    }

    public String accessToken() {
        if (!properties.configured()) {
            throw new ResponseStatusException(
                CONFLICT,
                "Shopify Checkout MCP client credentials are not configured."
            );
        }
        Instant now = clock.instant();
        CachedToken current = cachedToken;
        if (current != null && current.expiresAt().isAfter(now.plus(properties.tokenRefreshSkew()))) {
            return current.token();
        }
        synchronized (this) {
            current = cachedToken;
            if (current != null && current.expiresAt().isAfter(now.plus(properties.tokenRefreshSkew()))) {
                return current.token();
            }
            cachedToken = fetchToken(now);
            return cachedToken.token();
        }
    }

    private CachedToken fetchToken(Instant requestedAt) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("grant_type", "client_credentials");
        body.put("client_id", properties.clientId());
        body.put("client_secret", properties.clientSecret());
        try {
            JsonNode response = restClient.post()
                .uri(properties.tokenUrl())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
            String token = response == null ? null : response.path("access_token").asText(null);
            if (!StringUtils.hasText(token)) {
                throw new ResponseStatusException(BAD_GATEWAY, "Shopify Checkout MCP token response did not include an access token.");
            }
            long expiresInSeconds = response.path("expires_in").asLong(3600L);
            if (expiresInSeconds < 60L) {
                expiresInSeconds = 60L;
            }
            return new CachedToken(token.trim(), requestedAt.plusSeconds(expiresInSeconds));
        } catch (RestClientResponseException ex) {
            throw new ResponseStatusException(
                BAD_GATEWAY,
                "Shopify Checkout MCP token endpoint returned HTTP " + ex.getStatusCode().value() + ".",
                ex
            );
        }
    }

    private record CachedToken(String token, Instant expiresAt) {
    }
}
