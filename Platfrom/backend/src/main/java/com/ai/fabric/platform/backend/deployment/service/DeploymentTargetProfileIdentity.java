package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentTargetProfileEntity;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.LongNode;

import java.math.BigDecimal;
import java.util.Map;

/** One security identity shared by reviewed composition, placement and capacity admission. */
public final class DeploymentTargetProfileIdentity {
    private static final BigDecimal MAX_SAFE_INTEGER = BigDecimal.valueOf(9_007_199_254_740_991L);
    private DeploymentTargetProfileIdentity() { }

    public static String fingerprint(ObjectMapper mapper, DeploymentTargetProfileEntity profile) {
        try {
            return DeploymentEvidenceFingerprint.hash(mapper, mapper.valueToTree(Map.of(
                "id", required(profile.getId()),
                "environment", required(profile.getEnvironmentName()),
                "provider", profile.getProviderType(),
                "sourceStrategy", required(profile.getSourceStrategy()),
                "credentialReference", profile.getCredentialRefId() == null ? "" : profile.getCredentialRefId(),
                "providerConfig", object(mapper, profile.getProviderConfigJson()),
                "networkPolicy", object(mapper, profile.getNetworkPolicyJson()),
                "resourceDefaults", object(mapper, profile.getResourceDefaultsJson())
            )));
        } catch (ArithmeticException error) {
            throw new IllegalArgumentException("TARGET_PROFILE_IDENTITY_NUMBER_UNSUPPORTED");
        } catch (Exception error) {
            throw new IllegalArgumentException("TARGET_PROFILE_IDENTITY_INVALID");
        }
    }

    private static String required(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("TARGET_PROFILE_IDENTITY_INVALID");
        return value;
    }

    private static JsonNode object(ObjectMapper mapper, String value) throws com.fasterxml.jackson.core.JsonProcessingException {
        JsonNode node = mapper.reader().with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).readTree(value);
        if (node == null || !node.isObject()) throw new IllegalArgumentException("TARGET_PROFILE_IDENTITY_INVALID");
        return normalizeNumbers(mapper, node);
    }

    private static JsonNode normalizeNumbers(ObjectMapper mapper, JsonNode node) {
        if (node.isNumber()) {
            // Restrict this identity to exact JS-safe integers; never round security configuration.
            if (node.isFloatingPointNumber() && !Double.isFinite(node.doubleValue())) throw new ArithmeticException();
            BigDecimal value = node.decimalValue();
            if (value.abs().compareTo(MAX_SAFE_INTEGER) > 0) throw new ArithmeticException();
            return LongNode.valueOf(value.longValueExact());
        }
        if (node.isObject()) {
            var result = mapper.createObjectNode();
            node.fields().forEachRemaining(field -> result.set(field.getKey(), normalizeNumbers(mapper, field.getValue())));
            return result;
        }
        if (node.isArray()) {
            var result = mapper.createArrayNode();
            node.forEach(value -> result.add(normalizeNumbers(mapper, value)));
            return result;
        }
        return node;
    }
}
