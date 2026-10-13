package com.ai.infrastructure.connector.rest.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class ConversationChannelConnectorSecurity {

    private ConversationChannelConnectorSecurity() {}

    public static String sign(String secret, String timestamp, String nonce, String body) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("Conversation channel signing secret must contain at least 32 characters.");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(
                (timestamp + "." + nonce + "." + body).getBytes(StandardCharsets.UTF_8)
            ));
        } catch (Exception exception) {
            throw new IllegalStateException("Conversation channel payload signing failed.", exception);
        }
    }

    public static boolean verify(String secret, String timestamp, String nonce, String body, String signature) {
        if (signature == null || timestamp == null || nonce == null || nonce.isBlank()) {
            return false;
        }
        try {
            return MessageDigest.isEqual(
                sign(secret, timestamp, nonce, body).getBytes(StandardCharsets.US_ASCII),
                signature.getBytes(StandardCharsets.US_ASCII)
            );
        } catch (RuntimeException exception) {
            return false;
        }
    }

    public static boolean timestampWithinSkew(String timestamp, Instant now, int allowedSkewSeconds) {
        try {
            long seconds = Long.parseLong(timestamp);
            long skew = Math.abs(now.getEpochSecond() - seconds);
            return allowedSkewSeconds > 0 && skew <= allowedSkewSeconds;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    public static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (Exception exception) {
            throw new IllegalStateException("Conversation channel payload hashing failed.", exception);
        }
    }
}
