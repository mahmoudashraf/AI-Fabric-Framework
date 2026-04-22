package com.ai.fabric.product.shopify.bridge.install.service;

import com.ai.fabric.product.shopify.bridge.config.ShopifyBridgeProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

@Service
public class ShopifyInstallStateService {

    private static final Duration STATE_TTL = Duration.ofMinutes(15);
    private static final Pattern SHOP_DOMAIN_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9-]*\\.myshopify\\.com$");

    private final ShopifyBridgeProperties properties;

    public ShopifyInstallStateService(ShopifyBridgeProperties properties) {
        this.properties = properties;
    }

    public String issueState(String shopDomain) {
        requireConfiguredSecret();
        String normalizedShop = normalizeShopDomain(shopDomain);
        long issuedAt = Instant.now().getEpochSecond();
        String nonce = UUID.randomUUID().toString().replace("-", "");
        String payload = normalizedShop + "|" + issuedAt + "|" + nonce;
        String signature = hmacHex(payload);
        String token = payload + "|" + signature;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(token.getBytes(StandardCharsets.UTF_8));
    }

    public void verifyState(String state, String expectedShopDomain) {
        requireConfiguredSecret();
        String normalizedShop = normalizeShopDomain(expectedShopDomain);
        String decoded = decodeState(state);
        String[] parts = decoded.split("\\|");
        if (parts.length != 4) {
            throw new ResponseStatusException(CONFLICT, "Invalid Shopify install state.");
        }
        String shop = normalizeShopDomain(parts[0]);
        if (!shop.equals(normalizedShop)) {
            throw new ResponseStatusException(CONFLICT, "Shopify install state does not match the requested shop.");
        }
        long issuedAt;
        try {
            issuedAt = Long.parseLong(parts[1]);
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(CONFLICT, "Invalid Shopify install state timestamp.");
        }
        Instant issued = Instant.ofEpochSecond(issuedAt);
        Instant now = Instant.now();
        if (issued.isAfter(now.plusSeconds(60)) || issued.plus(STATE_TTL).isBefore(now)) {
            throw new ResponseStatusException(CONFLICT, "Expired Shopify install state.");
        }
        String expectedSignature = hmacHex(parts[0] + "|" + parts[1] + "|" + parts[2]);
        if (!constantTimeEquals(expectedSignature, parts[3])) {
            throw new ResponseStatusException(CONFLICT, "Invalid Shopify install state signature.");
        }
    }

    public String normalizeShopDomain(String shopDomain) {
        String normalized = shopDomain == null ? "" : shopDomain.trim().toLowerCase(Locale.ROOT);
        if (!SHOP_DOMAIN_PATTERN.matcher(normalized).matches()) {
            throw new ResponseStatusException(CONFLICT, "Invalid Shopify shop domain.");
        }
        return normalized;
    }

    private void requireConfiguredSecret() {
        if (properties.shopifyApiSecret().isBlank()) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Shopify Bridge client secret is not configured.");
        }
    }

    private String decodeState(String state) {
        if (state == null || state.isBlank()) {
            throw new ResponseStatusException(CONFLICT, "Missing Shopify install state.");
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(state.trim());
            return new String(decoded, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(CONFLICT, "Malformed Shopify install state.");
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
            throw new IllegalStateException("Failed to sign Shopify install state.", ex);
        }
    }

    private boolean constantTimeEquals(String left, String right) {
        byte[] a = left == null ? new byte[0] : left.getBytes(StandardCharsets.UTF_8);
        byte[] b = right == null ? new byte[0] : right.getBytes(StandardCharsets.UTF_8);
        return java.security.MessageDigest.isEqual(a, b);
    }
}
