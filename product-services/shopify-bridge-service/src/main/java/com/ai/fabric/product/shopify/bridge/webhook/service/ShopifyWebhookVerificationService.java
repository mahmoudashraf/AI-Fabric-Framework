package com.ai.fabric.product.shopify.bridge.webhook.service;

import com.ai.fabric.product.shopify.bridge.config.ShopifyBridgeProperties;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

@Service
public class ShopifyWebhookVerificationService {

    private final ShopifyBridgeProperties properties;

    public ShopifyWebhookVerificationService(ShopifyBridgeProperties properties) {
        this.properties = properties;
    }

    public boolean verify(String rawBody, String presentedHmac) {
        if (properties.webhookSharedSecret().isBlank() || presentedHmac == null || presentedHmac.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
            compute(rawBody == null ? "" : rawBody).getBytes(StandardCharsets.UTF_8),
            presentedHmac.trim().getBytes(StandardCharsets.UTF_8)
        );
    }

    public String compute(String rawBody) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.webhookSharedSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal((rawBody == null ? "" : rawBody).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to compute Shopify webhook HMAC.", ex);
        }
    }
}
