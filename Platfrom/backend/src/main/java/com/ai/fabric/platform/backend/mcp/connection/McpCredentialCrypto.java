package com.ai.fabric.platform.backend.mcp.connection;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/** JCA authenticated envelopes, isolated from the configuration-assistant encryption key. */
@Component
public class McpCredentialCrypto {
    private final McpConnectionProperties properties;
    private final SecureRandom random = new SecureRandom();
    public McpCredentialCrypto(McpConnectionProperties properties) { this.properties = properties; }

    public boolean configured() {
        try { return key(properties.getEncryptionKey()).length == 32 && validKeyId(properties.getEncryptionKeyId()); }
        catch (RuntimeException invalid) { return false; }
    }

    public String encrypt(String binding, String value) {
        if (!configured()) throw new IllegalStateException("MCP credential encryption is not configured.");
        try {
            byte[] iv = new byte[12]; random.nextBytes(iv);
            String keyId = properties.getEncryptionKeyId();
            var cipher = cipher(Cipher.ENCRYPT_MODE, binding, keyId, iv, properties.getEncryptionKey());
            return keyId + "." + Base64.getEncoder().encodeToString(iv) + "."
                + Base64.getEncoder().encodeToString(cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception failure) { throw new IllegalStateException("MCP credential encryption failed."); }
    }

    public String decrypt(String binding, String sealed) {
        try {
            String[] parts = sealed.split("\\.", -1);
            if (parts.length != 3) throw new IllegalArgumentException();
            String key = parts[0].equals(properties.getEncryptionKeyId()) ? properties.getEncryptionKey()
                : parts[0].equals(properties.getPreviousEncryptionKeyId()) ? properties.getPreviousEncryptionKey() : null;
            if (key == null) throw new IllegalArgumentException();
            byte[] iv = Base64.getDecoder().decode(parts[1]);
            if (iv.length != 12) throw new IllegalArgumentException();
            return new String(cipher(Cipher.DECRYPT_MODE, binding, parts[0], iv, key)
                .doFinal(Base64.getDecoder().decode(parts[2])), StandardCharsets.UTF_8);
        } catch (Exception failure) { throw new IllegalStateException("MCP credential envelope cannot be opened with the configured key."); }
    }

    private static Cipher cipher(int mode, String binding, String keyId, byte[] iv, String key) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(key(key), "AES"), new GCMParameterSpec(128, iv));
        cipher.updateAAD(("loomai-mcp:" + keyId + ":" + binding).getBytes(StandardCharsets.UTF_8));
        return cipher;
    }
    private static byte[] key(String value) {
        byte[] bytes = Base64.getDecoder().decode(value);
        if (bytes.length != 32) throw new IllegalArgumentException();
        return bytes;
    }
    private static boolean validKeyId(String id) { return id != null && id.matches("[a-zA-Z0-9_-]{1,40}"); }
}
