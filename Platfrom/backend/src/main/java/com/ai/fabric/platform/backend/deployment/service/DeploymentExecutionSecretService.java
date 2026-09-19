package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class DeploymentExecutionSecretService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final PlatformSecretService platformSecretService;
    private final ObjectMapper objectMapper;

    public DeploymentExecutionSecretService(PlatformSecretService platformSecretService,
                                            ObjectMapper objectMapper) {
        this.platformSecretService = platformSecretService;
        this.objectMapper = objectMapper;
    }

    public void ensureRequiredSecrets(DeploymentEntity deployment, DeploymentVersionEntity version) {
        for (String secretName : requiredSecretNames(deployment, version)) {
            if (platformSecretService.resolveSecret(secretName) != null) {
                continue;
            }
            Map<String, Object> auditDetails = new LinkedHashMap<>();
            auditDetails.put("deploymentId", deployment.getId());
            auditDetails.put("deploymentVersionId", version.getId());
            auditDetails.put("purpose", purpose(secretName));
            auditDetails.put("owner", "DEPLOYMENT_RUNTIME");
            platformSecretService.upsertManagedSecret(secretName, randomSecret(), auditDetails);
        }
    }

    public Set<String> requiredSecretNames(DeploymentEntity deployment, DeploymentVersionEntity version) {
        JsonNode behavior = readJson(version.getBehaviorConfigJson());
        String behaviorType = behavior.path("type").asText(deployment.getBehaviorType());
        LinkedHashSet<String> names = new LinkedHashSet<>();
        if ("AGENTIC_SPECIALIST_TEAM".equals(behaviorType)) {
            names.add(chainEncryptionSecretName(deployment.getId()));
            names.add(chainFingerprintSecretName(deployment.getId()));
        }
        if ("SMART_BRAIN".equals(behaviorType)) {
            names.add(smartBrainJobEncryptionSecretName(deployment.getId()));
            names.add(smartBrainJobFingerprintSecretName(deployment.getId()));
            names.add(smartBrainEncryptionSecretName(deployment.getId()));
            names.add(smartBrainFingerprintSecretName(deployment.getId()));
            names.add(smartBrainDeliverySigningSecretName(deployment.getId()));
        }
        if (contains(behavior.path("executionExtensions"), "HUMAN_REVIEW")) {
            names.add(actionReceiptEncryptionSecretName(deployment.getId()));
            names.add(actionReceiptFingerprintSecretName(deployment.getId()));
            names.add(reviewEncryptionSecretName(deployment.getId()));
            names.add(reviewFingerprintSecretName(deployment.getId()));
        }
        return Set.copyOf(names);
    }

    public static String chainEncryptionSecretName(String deploymentId) {
        return managedName("SPECIALIST_CHAIN_ENCRYPTION", deploymentId);
    }

    public static String chainFingerprintSecretName(String deploymentId) {
        return managedName("SPECIALIST_CHAIN_FINGERPRINT", deploymentId);
    }

    public static String smartBrainEncryptionSecretName(String deploymentId) {
        return managedName("SMART_BRAIN_ENCRYPTION", deploymentId);
    }

    public static String smartBrainJobEncryptionSecretName(String deploymentId) {
        return managedName("SMART_BRAIN_JOB_ENCRYPTION", deploymentId);
    }

    public static String smartBrainJobFingerprintSecretName(String deploymentId) {
        return managedName("SMART_BRAIN_JOB_FINGERPRINT", deploymentId);
    }

    public static String smartBrainFingerprintSecretName(String deploymentId) {
        return managedName("SMART_BRAIN_FINGERPRINT", deploymentId);
    }

    public static String smartBrainDeliverySigningSecretName(String deploymentId) {
        return managedName("SMART_BRAIN_DELIVERY_SIGNING", deploymentId);
    }

    public static String actionReceiptEncryptionSecretName(String deploymentId) {
        return managedName("ACTION_RECEIPT_ENCRYPTION", deploymentId);
    }

    public static String actionReceiptFingerprintSecretName(String deploymentId) {
        return managedName("ACTION_RECEIPT_FINGERPRINT", deploymentId);
    }

    public static String reviewEncryptionSecretName(String deploymentId) {
        return managedName("REVIEW_ENCRYPTION", deploymentId);
    }

    public static String reviewFingerprintSecretName(String deploymentId) {
        return managedName("REVIEW_FINGERPRINT", deploymentId);
    }

    private static String managedName(String purpose, String deploymentId) {
        String normalized = deploymentId == null
            ? "UNKNOWN"
            : deploymentId.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_");
        return "MANAGED_" + purpose + "_SECRET_DEP_" + normalized;
    }

    private boolean contains(JsonNode values, String expected) {
        if (values == null || !values.isArray()) {
            return false;
        }
        for (JsonNode value : values) {
            if (expected.equals(value.asText(""))) {
                return true;
            }
        }
        return false;
    }

    private JsonNode readJson(String value) {
        if (value == null || value.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Stored deployment behavior configuration is invalid JSON.", exception);
        }
    }

    private String randomSecret() {
        byte[] bytes = new byte[48];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String purpose(String secretName) {
        return secretName
            .replaceFirst("^MANAGED_", "")
            .replaceFirst("_SECRET_DEP_.*$", "")
            .toLowerCase(Locale.ROOT);
    }
}
