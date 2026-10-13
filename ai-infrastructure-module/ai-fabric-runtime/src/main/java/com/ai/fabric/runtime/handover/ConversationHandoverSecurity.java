package com.ai.fabric.runtime.handover;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

final class ConversationHandoverSecurity {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String CIPHER_PREFIX = "v1:";
    private final byte[] encryptionKey;
    private final byte[] signingKey;
    private final Clock clock;
    private final Duration clockSkew;

    ConversationHandoverSecurity(ConversationHandoverProperties properties, Clock clock) {
        this.clock = clock;
        this.clockSkew = properties.getCallbackClockSkew();
        requireSecret(properties.getEncryptionSecret(), "LOOMAI_CONVERSATION_HANDOVER_ENCRYPTION_SECRET");
        requireSecret(properties.getCallbackSigningSecret(), "LOOMAI_CONVERSATION_HANDOVER_CALLBACK_SIGNING_SECRET");
        if (properties.getEncryptionSecret().equals(properties.getCallbackSigningSecret())) {
            throw new IllegalStateException("Conversation handover encryption and callback secrets must be distinct.");
        }
        this.encryptionKey = sha256(properties.getEncryptionSecret().getBytes(StandardCharsets.UTF_8));
        this.signingKey = properties.getCallbackSigningSecret().getBytes(StandardCharsets.UTF_8);
    }

    String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(encryptionKey, "AES"), new GCMParameterSpec(128, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
            return CIPHER_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(combined);
        } catch (Exception exception) {
            throw new IllegalStateException("Conversation handover payload encryption failed.", exception);
        }
    }

    String decrypt(String protectedValue) {
        if (protectedValue == null || !protectedValue.startsWith(CIPHER_PREFIX)) {
            throw new IllegalStateException("Conversation handover protected payload format is invalid.");
        }
        try {
            byte[] combined = Base64.getUrlDecoder().decode(protectedValue.substring(CIPHER_PREFIX.length()));
            if (combined.length < 29) {
                throw new IllegalStateException("Conversation handover protected payload is too short.");
            }
            byte[] iv = Arrays.copyOfRange(combined, 0, 12);
            byte[] ciphertext = Arrays.copyOfRange(combined, 12, combined.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(encryptionKey, "AES"), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("Conversation handover payload decryption failed.", exception);
        }
    }

    String sign(String timestamp, String nonce, String body) {
        return hmac(timestamp + "." + nonce + "." + body);
    }

    void verify(String timestamp, String nonce, String body, String signature) {
        try {
            Instant sentAt = Instant.ofEpochSecond(Long.parseLong(timestamp));
            Duration age = Duration.between(sentAt, clock.instant()).abs();
            if (age.compareTo(clockSkew) > 0) {
                throw new SecurityException("Conversation channel callback timestamp is outside the accepted window.");
            }
        } catch (NumberFormatException exception) {
            throw new SecurityException("Conversation channel callback timestamp is invalid.");
        }
        String expected = sign(timestamp, nonce, body);
        if (signature == null || !MessageDigest.isEqual(
            expected.getBytes(StandardCharsets.US_ASCII),
            signature.getBytes(StandardCharsets.US_ASCII)
        )) {
            throw new SecurityException("Conversation channel callback signature is invalid.");
        }
    }

    String fingerprint(String value) {
        return hmac(value);
    }

    private String hmac(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingKey, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Conversation handover signing failed.", exception);
        }
    }

    private static void requireSecret(String value, String name) {
        if (value == null || value.length() < 32) {
            throw new IllegalStateException(name + " must contain at least 32 characters.");
        }
    }

    private static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }
}
