package com.loomai.demo.dealership.lead;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loomai.demo.dealership.config.DealershipDemoProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;

@Component
public class PiiEncryptionService {

    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final DealershipDemoProperties properties;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    public PiiEncryptionService(DealershipDemoProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public String encrypt(Map<String, Object> value) {
        try {
            byte[] iv = new byte[IV_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(objectMapper.writeValueAsBytes(value));
            return "v1." + Base64.getUrlEncoder().withoutPadding().encodeToString(iv)
                + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(ciphertext);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not encrypt lead contact data.", ex);
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> decrypt(String envelope) {
        try {
            String[] parts = envelope.split("\\.");
            if (parts.length != 3 || !"v1".equals(parts[0])) {
                throw new IllegalArgumentException("Unsupported encrypted envelope.");
            }
            byte[] iv = Base64.getUrlDecoder().decode(parts[1]);
            byte[] ciphertext = Base64.getUrlDecoder().decode(parts[2]);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, iv));
            return objectMapper.readValue(cipher.doFinal(ciphertext), Map.class);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not decrypt lead contact data.", ex);
        }
    }

    public boolean isConfigured() {
        String value = properties.getPrivacy().getEncryptionKeyBase64();
        if (!StringUtils.hasText(value)) {
            return false;
        }
        try {
            return Base64.getDecoder().decode(value.trim()).length == 32;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private SecretKeySpec key() {
        if (!isConfigured()) {
            throw new IllegalStateException("Lead encryption is not configured.");
        }
        return new SecretKeySpec(
            Base64.getDecoder().decode(properties.getPrivacy().getEncryptionKeyBase64().trim()),
            "AES"
        );
    }
}
