package com.ai.fabric.product.shopify.bridge.install.service;

import com.ai.fabric.product.shopify.bridge.auth.ShopifyMerchantSession;
import com.ai.fabric.product.shopify.bridge.config.ShopifyBridgeProperties;
import com.ai.fabric.product.shopify.bridge.install.model.ShopifyTokenExchangeMaterial;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

@Service
public class ShopifyTokenExchangeService {

    private static final String TOKEN_EXCHANGE_GRANT = "urn:ietf:params:oauth:grant-type:token-exchange";
    private static final String ID_TOKEN_TYPE = "urn:ietf:params:oauth:token-type:id_token";
    private static final String OFFLINE_TOKEN_TYPE = "urn:shopify:params:oauth:token-type:offline-access-token";

    private final ShopifyBridgeProperties properties;
    private final RestClient restClient;

    public ShopifyTokenExchangeService(ShopifyBridgeProperties properties,
                                       RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClient = restClientBuilder.build();
    }

    public ShopifyTokenExchangeMaterial exchangeExpiringOfflineToken(ShopifyMerchantSession merchantSession,
                                                                     String authorizationHeader) {
        if (properties.shopifyApiKey().isBlank() || properties.shopifyApiSecret().isBlank()) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Shopify Bridge client credentials are not configured.");
        }
        String sessionToken = extractSessionToken(authorizationHeader);
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", properties.shopifyApiKey());
        form.add("client_secret", properties.shopifyApiSecret());
        form.add("grant_type", TOKEN_EXCHANGE_GRANT);
        form.add("subject_token", sessionToken);
        form.add("subject_token_type", ID_TOKEN_TYPE);
        form.add("requested_token_type", OFFLINE_TOKEN_TYPE);
        form.add("expiring", "1");

        Map<String, Object> response = restClient.post()
            .uri("https://" + merchantSession.shopDomain() + "/admin/oauth/access_token")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .accept(MediaType.APPLICATION_JSON)
            .body(form)
            .retrieve()
            .body(new ParameterizedTypeReference<>() { });
        if (response == null) {
            throw new ResponseStatusException(BAD_GATEWAY, "Shopify token exchange returned an empty response.");
        }

        String accessToken = requireField(response, "access_token");
        String refreshToken = requireField(response, "refresh_token");
        String scopesText = normalize(response.get("scope"));
        Instant now = Instant.now();
        Instant accessTokenExpiresAt = plusSeconds(now, number(response.get("expires_in")));
        Instant refreshTokenExpiresAt = plusSeconds(now, number(response.get("refresh_token_expires_in")));
        return new ShopifyTokenExchangeMaterial(
            accessToken,
            refreshToken,
            accessTokenExpiresAt,
            refreshTokenExpiresAt,
            scopesText,
            true
        );
    }

    private String extractSessionToken(String authorizationHeader) {
        String value = normalize(authorizationHeader);
        if (value == null || !value.regionMatches(true, 0, "Bearer ", 0, 7)) {
            throw new ResponseStatusException(CONFLICT, "Missing Shopify session token for token exchange.");
        }
        String token = normalize(value.substring(7));
        if (token == null) {
            throw new ResponseStatusException(CONFLICT, "Missing Shopify session token for token exchange.");
        }
        return token;
    }

    private String requireField(Map<String, Object> response, String field) {
        String value = normalize(response.get(field));
        if (value == null) {
            throw new ResponseStatusException(BAD_GATEWAY, "Shopify token exchange response is missing " + field + ".");
        }
        return value;
    }

    private long number(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = normalize(value);
        if (text == null) {
            return 0L;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(BAD_GATEWAY, "Shopify token exchange returned an invalid numeric value.");
        }
    }

    private Instant plusSeconds(Instant base, long seconds) {
        return seconds <= 0 ? null : base.plusSeconds(seconds);
    }

    private String normalize(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }
}
