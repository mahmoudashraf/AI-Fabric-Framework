package com.ai.fabric.runtime.smartbrain;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

@Component
@ConditionalOnProperty(name = "loomai.smart-brain.enabled", havingValue = "true")
public class SmartBrainSecurity {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String CIPHER_PREFIX = "v1:";

    private final byte[] encryptionKey;
    private final byte[] fingerprintKey;

    public SmartBrainSecurity(
        @Value("${loomai.smart-brain.encryption-secret:}") String encryptionSecret,
        @Value("${loomai.smart-brain.fingerprint-secret:}") String fingerprintSecret
    ) {
        requireSecret(encryptionSecret, "LOOMAI_SMART_BRAIN_ENCRYPTION_SECRET");
        requireSecret(fingerprintSecret, "LOOMAI_SMART_BRAIN_FINGERPRINT_SECRET");
        if (encryptionSecret.equals(fingerprintSecret)) {
            throw new IllegalStateException("Smart Brain encryption and fingerprint secrets must be distinct.");
        }
        this.encryptionKey = sha256(encryptionSecret.getBytes(StandardCharsets.UTF_8));
        this.fingerprintKey = fingerprintSecret.getBytes(StandardCharsets.UTF_8);
    }

    public String encrypt(String plaintext) {
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
            throw new IllegalStateException("Smart Brain payload encryption failed.", exception);
        }
    }

    public String decrypt(String protectedValue) {
        if (protectedValue == null || !protectedValue.startsWith(CIPHER_PREFIX)) {
            throw new IllegalStateException("Smart Brain protected payload format is invalid.");
        }
        try {
            byte[] combined = Base64.getUrlDecoder().decode(protectedValue.substring(CIPHER_PREFIX.length()));
            if (combined.length < 29) {
                throw new IllegalStateException("Smart Brain protected payload is too short.");
            }
            byte[] iv = java.util.Arrays.copyOfRange(combined, 0, 12);
            byte[] ciphertext = java.util.Arrays.copyOfRange(combined, 12, combined.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(encryptionKey, "AES"), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("Smart Brain payload decryption failed.", exception);
        }
    }

    public String fingerprint(String canonicalValue) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(fingerprintKey, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(canonicalValue.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Smart Brain fingerprinting failed.", exception);
        }
    }

    private void requireSecret(String value, String name) {
        if (value == null || value.length() < 32) {
            throw new IllegalStateException(name + " must contain at least 32 characters.");
        }
    }

    private byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }
}
