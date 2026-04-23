package com.ai.fabric.product.shopify.bridge.governedaction.service;

import com.ai.fabric.product.shopify.bridge.config.ShopifyBridgeProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

@Service
public class ShopifyStorefrontGovernedActionTokenService {

    private final ShopifyBridgeProperties properties;
    private final ObjectMapper objectMapper;

    public ShopifyStorefrontGovernedActionTokenService(ShopifyBridgeProperties properties,
                                                       ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public String issueToken(TokenClaims claims) {
        requireConfiguredSecret();
        String payload = encodeClaims(claims);
        return payload + "." + hmacHex(payload);
    }

    public TokenClaims verify(String token,
                              String expectedShopDomain,
                              String expectedShopperSessionId,
                              String expectedAuditId) {
        requireConfiguredSecret();
        if (token == null || token.isBlank()) {
            throw new ResponseStatusException(CONFLICT, "Missing Shopify governed action token.");
        }
        String[] parts = token.trim().split("\\.", 2);
        if (parts.length != 2) {
            throw new ResponseStatusException(CONFLICT, "Malformed Shopify governed action token.");
        }
        String payload = parts[0];
        if (!constantTimeEquals(hmacHex(payload), parts[1])) {
            throw new ResponseStatusException(CONFLICT, "Invalid Shopify governed action token signature.");
        }
        TokenClaims claims = decodeClaims(payload);
        String normalizedShop = normalizeShopDomain(expectedShopDomain);
        String normalizedSession = normalizeShopperSessionId(expectedShopperSessionId);
        if (!claims.shopDomain().equals(normalizedShop)) {
            throw new ResponseStatusException(CONFLICT, "Shopify governed action token does not match the requested shop.");
        }
        if (!claims.shopperSessionId().equals(normalizedSession)) {
            throw new ResponseStatusException(CONFLICT, "Shopify governed action token does not match the shopper session.");
        }
        if (expectedAuditId != null && !expectedAuditId.isBlank() && !claims.auditId().equals(expectedAuditId.trim())) {
            throw new ResponseStatusException(CONFLICT, "Shopify governed action token does not match the audit record.");
        }
        Instant now = Instant.now();
        if (claims.expiresAtEpochSecond() <= 0 || Instant.ofEpochSecond(claims.expiresAtEpochSecond()).isBefore(now)) {
            throw new ResponseStatusException(CONFLICT, "Expired Shopify governed action token.");
        }
        return claims;
    }

    public String normalizeShopDomain(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!normalized.endsWith(".myshopify.com")) {
            throw new ResponseStatusException(CONFLICT, "Invalid Shopify shop domain.");
        }
        return normalized;
    }

    public String normalizeShopperSessionId(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > 120) {
            throw new ResponseStatusException(CONFLICT, "Missing or invalid shopper session identifier.");
        }
        return normalized;
    }

    private void requireConfiguredSecret() {
        if (properties.shopifyApiSecret().isBlank()) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Shopify Bridge client secret is not configured.");
        }
    }

    private String encodeClaims(TokenClaims claims) {
        try {
            byte[] raw = objectMapper.writeValueAsBytes(claims);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to encode Shopify governed action token.", ex);
        }
    }

    private TokenClaims decodeClaims(String payload) {
        try {
            byte[] raw = Base64.getUrlDecoder().decode(payload);
            return objectMapper.readValue(raw, TokenClaims.class);
        } catch (Exception ex) {
            throw new ResponseStatusException(CONFLICT, "Malformed Shopify governed action token.");
        }
    }

    private String hmacHex(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.shopifyApiSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte current : digest) {
                hex.append(String.format("%02x", current));
            }
            return hex.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to sign Shopify governed action token.", ex);
        }
    }

    private boolean constantTimeEquals(String left, String right) {
        byte[] a = left == null ? new byte[0] : left.getBytes(StandardCharsets.UTF_8);
        byte[] b = right == null ? new byte[0] : right.getBytes(StandardCharsets.UTF_8);
        return java.security.MessageDigest.isEqual(a, b);
    }

    public record TokenClaims(
        String auditId,
        String shopDomain,
        String shopperSessionId,
        String actionType,
        String variantId,
        Integer requestedQuantity,
        Integer targetQuantity,
        String cartLineKey,
        long expiresAtEpochSecond
    ) {
    }
}
