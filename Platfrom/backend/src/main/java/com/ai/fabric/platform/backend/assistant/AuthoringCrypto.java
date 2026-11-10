package com.ai.fabric.platform.backend.assistant;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class AuthoringCrypto {
    private final AuthoringProperties properties;
    private final SecureRandom random = new SecureRandom();
    public AuthoringCrypto(AuthoringProperties properties) { this.properties = properties; }
    public boolean configured() {
        try { return Base64.getDecoder().decode(properties.getEncryptionKey()).length == 32; }
        catch (IllegalArgumentException invalid) { return false; }
    }
    public String encrypt(String sessionId, String plaintext) {
        try {
            byte[] iv = new byte[12]; random.nextBytes(iv);
            var cipher = cipher(Cipher.ENCRYPT_MODE, sessionId, iv);
            return Base64.getEncoder().encodeToString(iv) + "."
                + Base64.getEncoder().encodeToString(cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception failure) { throw new IllegalStateException("AUTHORING_CONTENT_ENCRYPTION_FAILED"); }
    }
    public String decrypt(String sessionId, String sealed) {
        try {
            String[] parts = sealed.split("\\.", -1);
            if (parts.length != 2) throw new IllegalArgumentException();
            return new String(cipher(Cipher.DECRYPT_MODE, sessionId, Base64.getDecoder().decode(parts[0]))
                .doFinal(Base64.getDecoder().decode(parts[1])), StandardCharsets.UTF_8);
        } catch (Exception failure) { throw new IllegalStateException("AUTHORING_CONTENT_DECRYPTION_FAILED"); }
    }
    private Cipher cipher(int mode, String sessionId, byte[] iv) throws Exception {
        if (!configured()) throw new IllegalStateException("AUTHORING_ENCRYPTION_NOT_CONFIGURED");
        var cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(Base64.getDecoder().decode(properties.getEncryptionKey()), "AES"), new GCMParameterSpec(128, iv));
        cipher.updateAAD(sessionId.getBytes(StandardCharsets.UTF_8));
        return cipher;
    }
}
