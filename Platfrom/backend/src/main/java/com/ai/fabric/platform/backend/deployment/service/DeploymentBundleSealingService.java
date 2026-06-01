package com.ai.fabric.platform.backend.deployment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.interfaces.RSAPrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
public class DeploymentBundleSealingService {

    private static final String AES_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String RSA_TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_BYTES = 12;

    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    public DeploymentBundleSealingService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public SealedPayload seal(JsonNode secretPayload,
                              String recipientPublicKeyPem,
                              Map<String, String> aad) {
        if (!StringUtils.hasText(recipientPublicKeyPem)) {
            throw new ResponseStatusException(BAD_REQUEST, "Sealed backup export requires an operator public key.");
        }
        try {
            PublicKey publicKey = readPublicKey(recipientPublicKeyPem);
            KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
            keyGenerator.init(256, secureRandom);
            SecretKey dataKey = keyGenerator.generateKey();

            byte[] iv = new byte[IV_BYTES];
            secureRandom.nextBytes(iv);

            Cipher aesCipher = Cipher.getInstance(AES_TRANSFORMATION);
            aesCipher.init(Cipher.ENCRYPT_MODE, dataKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] aadBytes = canonicalBytes(aadNode(aad));
            aesCipher.updateAAD(aadBytes);
            byte[] ciphertext = aesCipher.doFinal(canonicalBytes(secretPayload));

            Cipher rsaCipher = Cipher.getInstance(RSA_TRANSFORMATION);
            rsaCipher.init(Cipher.WRAP_MODE, publicKey);
            byte[] wrappedDataKey = rsaCipher.wrap(dataKey);

            ObjectNode envelope = objectMapper.createObjectNode();
            envelope.put("format", "loomai.sealed-secrets.v1");
            envelope.put("algorithm", "AES-256-GCM");
            envelope.put("keyWrapAlgorithm", "RSA-OAEP-SHA256");
            envelope.put("recipientType", "OPERATOR_PUBLIC_KEY");
            envelope.put("recipientKeyId", keyId(publicKey.getEncoded()));
            envelope.put("iv", b64(iv));
            envelope.put("ciphertext", b64(ciphertext));
            envelope.put("wrappedDataKey", b64(wrappedDataKey));
            envelope.set("aad", aadNode(aad));
            return new SealedPayload(envelope, sha256(envelope));
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(BAD_REQUEST, "Failed to seal deployment backup secrets.");
        }
    }

    public JsonNode unseal(JsonNode envelope, String privateKeyPem) {
        if (!StringUtils.hasText(privateKeyPem)) {
            throw new ResponseStatusException(BAD_REQUEST, "Sealed bundle import requires the recipient private key.");
        }
        if (envelope == null || !envelope.isObject()) {
            throw new ResponseStatusException(BAD_REQUEST, "Bundle does not contain a valid sealed secret envelope.");
        }
        try {
            RSAPrivateKey privateKey = readPrivateKey(privateKeyPem);
            byte[] wrappedDataKey = b64decode(requiredText(envelope, "wrappedDataKey"));
            Cipher rsaCipher = Cipher.getInstance(RSA_TRANSFORMATION);
            rsaCipher.init(Cipher.UNWRAP_MODE, privateKey);
            SecretKey dataKey = (SecretKey) rsaCipher.unwrap(wrappedDataKey, "AES", Cipher.SECRET_KEY);

            byte[] iv = b64decode(requiredText(envelope, "iv"));
            byte[] ciphertext = b64decode(requiredText(envelope, "ciphertext"));
            Cipher aesCipher = Cipher.getInstance(AES_TRANSFORMATION);
            aesCipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(dataKey.getEncoded(), "AES"), new GCMParameterSpec(GCM_TAG_BITS, iv));
            aesCipher.updateAAD(canonicalBytes(envelope.path("aad")));
            byte[] plaintext = aesCipher.doFinal(ciphertext);
            return objectMapper.readTree(plaintext);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(BAD_REQUEST, "Sealed bundle secrets could not be decrypted.");
        }
    }

    public String sha256(JsonNode node) {
        return sha256(canonicalBytes(node));
    }

    public String sha256(byte[] value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return "sha256:" + Base64.getUrlEncoder().withoutPadding().encodeToString(digest.digest(value));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to hash deployment bundle content.", ex);
        }
    }

    public byte[] canonicalBytes(JsonNode node) {
        try {
            return objectMapper.writeValueAsBytes(node);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize deployment bundle content.", ex);
        }
    }

    private ObjectNode aadNode(Map<String, String> aad) {
        ObjectNode node = objectMapper.createObjectNode();
        aad.forEach(node::put);
        return node;
    }

    private PublicKey readPublicKey(String pem) throws Exception {
        String base64 = normalizePem(pem, "PUBLIC KEY");
        return KeyFactory.getInstance("RSA")
            .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(base64)));
    }

    private RSAPrivateKey readPrivateKey(String pem) throws Exception {
        String base64 = normalizePem(pem, "PRIVATE KEY");
        return (RSAPrivateKey) KeyFactory.getInstance("RSA")
            .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));
    }

    private String normalizePem(String pem, String expectedLabel) {
        if (!StringUtils.hasText(pem)) {
            throw new ResponseStatusException(BAD_REQUEST, "Missing PEM key material.");
        }
        String trimmed = pem.trim();
        if (!trimmed.contains("BEGIN " + expectedLabel)) {
            throw new ResponseStatusException(BAD_REQUEST, "Expected PKCS#8 PEM " + expectedLabel + " material.");
        }
        return trimmed
            .replace("-----BEGIN " + expectedLabel + "-----", "")
            .replace("-----END " + expectedLabel + "-----", "")
            .replaceAll("\\s", "");
    }

    private String keyId(byte[] keyBytes) {
        return sha256(keyBytes);
    }

    private String requiredText(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        if (!StringUtils.hasText(value)) {
            throw new ResponseStatusException(BAD_REQUEST, "Sealed bundle envelope is missing " + field + ".");
        }
        return value;
    }

    private String b64(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private byte[] b64decode(String value) {
        return Base64.getUrlDecoder().decode(value.getBytes(StandardCharsets.UTF_8));
    }

    public record SealedPayload(ObjectNode envelope, String envelopeHash) {
    }
}
