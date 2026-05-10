package com.ai.fabric.product.shopify.bridge.customeraccount.service;

import com.ai.fabric.product.shopify.bridge.config.ShopifyBridgeProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

@Service
public class ShopifyCustomerAccountSecurityService {

    private static final String ENVELOPE_VERSION = "v1";
    private static final int GCM_TAG_BITS = 128;
    private static final int GCM_NONCE_BYTES = 12;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final ShopifyBridgeProperties properties;
    private final ObjectMapper objectMapper;

    public ShopifyCustomerAccountSecurityService(ShopifyBridgeProperties properties,
                                                 ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public String encryptText(String value) {
        requireSecret();
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            byte[] nonce = randomBytes(GCM_NONCE_BYTES);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(aesKey(), "AES"), new GCMParameterSpec(GCM_TAG_BITS, nonce));
            byte[] ciphertext = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return ENVELOPE_VERSION + "."
                + base64Url(nonce) + "."
                + base64Url(ciphertext);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to encrypt Shopify Customer Account token material.", ex);
        }
    }

    public String decryptText(String envelope) {
        requireSecret();
        if (!StringUtils.hasText(envelope)) {
            return null;
        }
        String[] parts = envelope.trim().split("\\.", 3);
        if (parts.length != 3 || !ENVELOPE_VERSION.equals(parts[0])) {
            throw new ResponseStatusException(CONFLICT, "Malformed Shopify Customer Account token envelope.");
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                Cipher.DECRYPT_MODE,
                new SecretKeySpec(aesKey(), "AES"),
                new GCMParameterSpec(GCM_TAG_BITS, Base64.getUrlDecoder().decode(parts[1]))
            );
            byte[] plaintext = cipher.doFinal(Base64.getUrlDecoder().decode(parts[2]));
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(CONFLICT, "Invalid Shopify Customer Account token envelope.");
        }
    }

    public String encryptJson(Object value) {
        try {
            return encryptText(objectMapper.writeValueAsString(value));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize Shopify Customer Account auth state.", ex);
        }
    }

    public <T> T decryptJson(String envelope, Class<T> type) {
        try {
            return objectMapper.readValue(decryptText(envelope), type);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(CONFLICT, "Malformed Shopify Customer Account auth state.");
        }
    }

    public String hmacHex(String value) {
        requireSecret();
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.shopifyApiSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte current : digest) {
                hex.append(String.format("%02x", current));
            }
            return hex.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to sign Shopify Customer Account session material.", ex);
        }
    }

    public String randomVerifier() {
        return base64Url(randomBytes(32));
    }

    public String randomNonce() {
        return base64Url(randomBytes(16));
    }

    public String codeChallenge(String verifier) {
        try {
            return base64Url(MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to calculate Shopify Customer Account PKCE challenge.", ex);
        }
    }

    public String shortRef(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() <= 12) {
            return normalized;
        }
        return normalized.substring(0, 6) + "..." + normalized.substring(normalized.length() - 4);
    }

    private byte[] aesKey() {
        try {
            return MessageDigest.getInstance("SHA-256").digest(properties.shopifyApiSecret().getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to derive Shopify Customer Account encryption key.", ex);
        }
    }

    private void requireSecret() {
        if (properties.shopifyApiSecret().isBlank()) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Shopify Bridge client secret is not configured.");
        }
    }

    private byte[] randomBytes(int size) {
        byte[] out = new byte[size];
        SECURE_RANDOM.nextBytes(out);
        return out;
    }

    private String base64Url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}
