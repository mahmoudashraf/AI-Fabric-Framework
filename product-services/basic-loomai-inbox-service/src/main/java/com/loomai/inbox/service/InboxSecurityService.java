package com.loomai.inbox.service;

import com.loomai.inbox.config.BasicInboxProperties;
import com.loomai.inbox.model.InboxContracts.OperatorIdentity;
import jakarta.annotation.PostConstruct;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class InboxSecurityService {

    private final BasicInboxProperties properties;
    private final SecureRandom random = new SecureRandom();
    private List<OperatorCredential> operators = List.of();

    public InboxSecurityService(BasicInboxProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void validate() {
        require(properties.getBindingId(), "BASIC_INBOX_BINDING_ID");
        require(properties.getTenantId(), "BASIC_INBOX_TENANT_ID");
        require(properties.getDeploymentId(), "BASIC_INBOX_DEPLOYMENT_ID");
        requireSecret(properties.getProviderApiKey(), "BASIC_INBOX_PROVIDER_API_KEY");
        require(properties.getConnectorCallbackUrl(), "BASIC_INBOX_CONNECTOR_CALLBACK_URL");
        if (!properties.getConnectorCallbackUrl().startsWith("https://")
            && !properties.getConnectorCallbackUrl().startsWith("http://localhost")) {
            throw new IllegalStateException("BASIC_INBOX_CONNECTOR_CALLBACK_URL must use HTTPS outside localhost.");
        }
        requireSecret(properties.getCallbackSigningSecret(), "BASIC_INBOX_CALLBACK_SIGNING_SECRET");
        requireSecret(properties.getDataEncryptionSecret(), "BASIC_INBOX_DATA_ENCRYPTION_SECRET");
        if (MessageDigest.isEqual(
            properties.getCallbackSigningSecret().getBytes(StandardCharsets.UTF_8),
            properties.getDataEncryptionSecret().getBytes(StandardCharsets.UTF_8)
        )) {
            throw new IllegalStateException("Inbox signing and encryption secrets must be distinct.");
        }
        operators = parseOperators(properties.getOperators());
        if (operators.isEmpty()) {
            throw new IllegalStateException("BASIC_INBOX_OPERATORS must configure at least one operator.");
        }
    }

    public boolean providerAuthorized(String supplied) {
        return constantTime(properties.getProviderApiKey(), supplied);
    }

    public OperatorIdentity requireOperator(String apiKey) {
        return operators.stream()
            .filter(operator -> constantTime(operator.apiKey(), apiKey))
            .map(operator -> new OperatorIdentity(
                operator.operatorId(), operator.displayName(), operator.bindingIds()
            ))
            .findFirst()
            .orElseThrow(() -> new InboxSecurityException("OPERATOR_AUTH_INVALID", "Operator authentication failed."));
    }

    public void requireBinding(OperatorIdentity operator, String bindingId) {
        if (operator == null || !operator.bindingIds().contains(bindingId)) {
            throw new InboxSecurityException("OPERATOR_BINDING_DENIED", "Operator is not assigned to this inbox binding.");
        }
    }

    public String encrypt(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            byte[] iv = new byte[12];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey(), new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            ByteBuffer payload = ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted);
            return Base64.getEncoder().encodeToString(payload.array());
        } catch (Exception exception) {
            throw new IllegalStateException("Inbox data encryption failed.", exception);
        }
    }

    public String decrypt(String protectedValue) {
        if (!StringUtils.hasText(protectedValue)) {
            return null;
        }
        try {
            ByteBuffer payload = ByteBuffer.wrap(Base64.getDecoder().decode(protectedValue));
            byte[] iv = new byte[12];
            payload.get(iv);
            byte[] encrypted = new byte[payload.remaining()];
            payload.get(encrypted);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey(), new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("Inbox data decryption failed.", exception);
        }
    }

    public String sign(String timestamp, String nonce, String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                properties.getCallbackSigningSecret().getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
            ));
            return HexFormat.of().formatHex(mac.doFinal(
                (timestamp + "." + nonce + "." + body).getBytes(StandardCharsets.UTF_8)
            ));
        } catch (Exception exception) {
            throw new IllegalStateException("Inbox callback signing failed.", exception);
        }
    }

    public String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Inbox payload hashing failed.", exception);
        }
    }

    private SecretKeySpec encryptionKey() throws Exception {
        byte[] key = MessageDigest.getInstance("SHA-256")
            .digest(properties.getDataEncryptionSecret().getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(key, "AES");
    }

    private List<OperatorCredential> parseOperators(String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        List<OperatorCredential> parsed = new ArrayList<>();
        for (String entry : raw.split(";")) {
            if (!StringUtils.hasText(entry)) {
                continue;
            }
            String[] fields = entry.trim().split("\\|", -1);
            if (fields.length != 4) {
                throw new IllegalStateException(
                    "Each BASIC_INBOX_OPERATORS entry must be operatorId|displayName|apiKey|binding1,binding2."
                );
            }
            require(fields[0], "operatorId");
            require(fields[1], "operator displayName");
            requireSecret(fields[2], "operator apiKey");
            List<String> bindings = java.util.Arrays.stream(fields[3].split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
            if (bindings.isEmpty()) {
                throw new IllegalStateException("Each inbox operator requires at least one binding membership.");
            }
            parsed.add(new OperatorCredential(fields[0].trim(), fields[1].trim(), fields[2], bindings));
        }
        return List.copyOf(parsed);
    }

    private boolean constantTime(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(
            expected.getBytes(StandardCharsets.UTF_8),
            actual.getBytes(StandardCharsets.UTF_8)
        );
    }

    private void require(String value, String name) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(name + " is required.");
        }
    }

    private void requireSecret(String value, String name) {
        if (value == null || value.length() < 32) {
            throw new IllegalStateException(name + " must contain at least 32 characters.");
        }
    }

    private record OperatorCredential(
        String operatorId,
        String displayName,
        String apiKey,
        List<String> bindingIds
    ) {}

    public static final class InboxSecurityException extends RuntimeException {
        private final String code;

        public InboxSecurityException(String code, String message) {
            super(message);
            this.code = code;
        }

        public String code() { return code; }
    }
}
