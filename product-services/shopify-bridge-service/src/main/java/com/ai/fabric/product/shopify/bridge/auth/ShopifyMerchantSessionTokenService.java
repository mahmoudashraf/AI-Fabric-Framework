package com.ai.fabric.product.shopify.bridge.auth;

import com.ai.fabric.product.shopify.bridge.config.ShopifyBridgeProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class ShopifyMerchantSessionTokenService {

    private static final Pattern MYSHOPIFY_DOMAIN = Pattern.compile("^[a-z0-9][a-z0-9-]*\\.myshopify\\.com$");

    private final ShopifyBridgeProperties properties;
    private final ObjectMapper objectMapper;

    public ShopifyMerchantSessionTokenService(ShopifyBridgeProperties properties,
                                              ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public ShopifyMerchantSession verify(String authorizationHeader) {
        requireMerchantAuthConfigured();
        String token = extractBearerToken(authorizationHeader);
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw unauthorized("Malformed Shopify session token.");
        }
        verifySignature(parts, token);
        JsonNode payload = decodePayload(parts[1]);
        verifyAudience(payload);
        verifyWindow(payload);
        String destination = requiredText(payload, "dest", "Shopify session token destination is missing.");
        String shopDomain = normalizeShopDomain(destination);
        verifyIssuer(payload, shopDomain);
        String userId = requiredText(payload, "sub", "Shopify session token subject is missing.");
        Instant expiresAt = Instant.ofEpochSecond(requiredLong(payload, "exp", "Shopify session token expiry is missing."));
        return new ShopifyMerchantSession(shopDomain, destination, userId, expiresAt);
    }

    public boolean merchantAuthConfigured() {
        return !properties.shopifyApiKey().isBlank() && !properties.shopifyApiSecret().isBlank();
    }

    private void requireMerchantAuthConfigured() {
        if (!merchantAuthConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Shopify merchant session token verification is not configured.");
        }
    }

    private String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw unauthorized("Missing Shopify session token.");
        }
        String token = authorizationHeader.substring("Bearer ".length()).trim();
        if (token.isBlank()) {
            throw unauthorized("Missing Shopify session token.");
        }
        return token;
    }

    private void verifySignature(String[] parts, String token) {
        try {
            JsonNode header = objectMapper.readTree(base64UrlDecode(parts[0]));
            String algorithm = header.path("alg").asText("");
            if (!"HS256".equalsIgnoreCase(algorithm)) {
                throw unauthorized("Unsupported Shopify session token algorithm.");
            }
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.shopifyApiSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String material = parts[0] + "." + parts[1];
            byte[] digest = mac.doFinal(material.getBytes(StandardCharsets.UTF_8));
            byte[] expected = Base64.getUrlEncoder().withoutPadding().encode(digest);
            byte[] presented = parts[2].getBytes(StandardCharsets.UTF_8);
            if (!MessageDigest.isEqual(expected, presented)) {
                throw unauthorized("Invalid Shopify session token signature.");
            }
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw unauthorized("Failed to verify Shopify session token.");
        }
    }

    private JsonNode decodePayload(String payloadPart) {
        try {
            return objectMapper.readTree(base64UrlDecode(payloadPart));
        } catch (Exception ex) {
            throw unauthorized("Malformed Shopify session token payload.");
        }
    }

    private void verifyAudience(JsonNode payload) {
        String audience = requiredText(payload, "aud", "Shopify session token audience is missing.");
        if (!properties.shopifyApiKey().equals(audience)) {
            throw unauthorized("Invalid Shopify session token audience.");
        }
    }

    private void verifyWindow(JsonNode payload) {
        Instant now = Instant.now();
        long exp = requiredLong(payload, "exp", "Shopify session token expiry is missing.");
        if (!Instant.ofEpochSecond(exp).isAfter(now.minusSeconds(5))) {
            throw unauthorized("Expired Shopify session token.");
        }
        if (payload.hasNonNull("nbf")) {
            Instant notBefore = Instant.ofEpochSecond(requiredLong(payload, "nbf", "Shopify session token not-before is invalid."));
            if (notBefore.isAfter(now.plusSeconds(5))) {
                throw unauthorized("Shopify session token is not yet valid.");
            }
        }
    }

    private String normalizeShopDomain(String destination) {
        String host = normalizedHost(destination, "Shopify session token destination is invalid.");
        if (!MYSHOPIFY_DOMAIN.matcher(host).matches()) {
            throw unauthorized("Shopify session token destination is invalid.");
        }
        return host;
    }

    private void verifyIssuer(JsonNode payload, String shopDomain) {
        String issuer = requiredText(payload, "iss", "Shopify session token issuer is missing.");
        String issuerHost = normalizedHost(issuer, "Shopify session token issuer is invalid.");
        if (!shopDomain.equals(issuerHost)) {
            throw unauthorized("Shopify session token issuer does not match destination.");
        }
        String issuerPath = issuerPath(issuer);
        if (issuerPath != null && !"/admin".equals(issuerPath)) {
            throw unauthorized("Shopify session token issuer is invalid.");
        }
    }

    private String normalizedHost(String value, String invalidMessage) {
        String candidate = value == null ? "" : value.trim();
        if (candidate.isBlank()) {
            throw unauthorized(invalidMessage);
        }
        try {
            URI uri = URI.create(candidate);
            if (uri.getScheme() != null && !"https".equalsIgnoreCase(uri.getScheme())) {
                throw unauthorized(invalidMessage);
            }
            String host = uri.getHost();
            if (host == null && uri.getScheme() == null && !candidate.contains("@") && !candidate.contains(":")) {
                int slash = candidate.indexOf('/');
                host = slash >= 0 ? candidate.substring(0, slash) : candidate;
            }
            if (host == null || host.isBlank()) {
                throw unauthorized(invalidMessage);
            }
            return host.trim().toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException ex) {
            throw unauthorized(invalidMessage);
        }
    }

    private String issuerPath(String issuer) {
        String candidate = issuer == null ? "" : issuer.trim();
        try {
            URI uri = URI.create(candidate);
            if (uri.getScheme() != null) {
                return uri.getPath();
            }
            int slash = candidate.indexOf('/');
            return slash >= 0 ? candidate.substring(slash) : null;
        } catch (IllegalArgumentException ex) {
            throw unauthorized("Shopify session token issuer is invalid.");
        }
    }

    private byte[] base64UrlDecode(String value) {
        try {
            return Base64.getUrlDecoder().decode(value);
        } catch (IllegalArgumentException ex) {
            throw unauthorized("Malformed Shopify session token.");
        }
    }

    private String requiredText(JsonNode node, String field, String message) {
        JsonNode value = node.path(field);
        if (!value.isTextual()) {
            throw unauthorized(message);
        }
        String text = value.asText("").trim();
        if (text.isBlank()) {
            throw unauthorized(message);
        }
        return text;
    }

    private long requiredLong(JsonNode node, String field, String message) {
        JsonNode value = node.path(field);
        if (!value.canConvertToLong()) {
            throw unauthorized(message);
        }
        return value.longValue();
    }

    private ResponseStatusException unauthorized(String message) {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, message);
    }
}
