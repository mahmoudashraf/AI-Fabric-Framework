package com.ai.fabric.platform.backend.shopify.service;

import com.ai.fabric.platform.backend.deployment.service.ManagedDeploymentProfileCatalog;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;

final class ShopifyCompanionRuntimeSecurityDefaults {

    static final String STOREFRONT_BRIDGE_PRIVATE_RUNTIME_ISSUER = "platform-consumer-bridge";

    private ShopifyCompanionRuntimeSecurityDefaults() {
    }

    static boolean apply(ObjectNode securityConfig, String deploymentId, String consumerId) {
        if (securityConfig == null) {
            return false;
        }

        boolean changed = putText(
            securityConfig,
            "authzMode",
            ManagedDeploymentProfileCatalog.AUTHZ_MODE_ALLOW_VERIFIED
        );
        changed |= ensureCsvContains(
            securityConfig,
            "privateRuntimeAcceptedIssuers",
            ManagedDeploymentProfileCatalog.DEFAULT_PRIVATE_RUNTIME_ACCEPTED_ISSUERS,
            STOREFRONT_BRIDGE_PRIVATE_RUNTIME_ISSUER
        );
        if (StringUtils.hasText(deploymentId)) {
            changed |= ensureCsvContains(securityConfig, "privateRuntimeAcceptedAudiences", deploymentId);
        }
        if (StringUtils.hasText(consumerId)) {
            changed |= ensureCsvContains(securityConfig, "privateRuntimeAcceptedAudiences", consumerId);
        }
        return changed;
    }

    private static boolean putText(ObjectNode target, String field, String value) {
        String normalizedValue = trimToNull(value);
        if (normalizedValue == null) {
            return false;
        }
        String current = trimToNull(target.path(field).asText(null));
        if (normalizedValue.equals(current)) {
            return false;
        }
        target.put(field, normalizedValue);
        return true;
    }

    private static boolean ensureCsvContains(ObjectNode target,
                                             String field,
                                             String... requiredValues) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        String existing = trimToNull(target.path(field).asText(null));
        appendCsv(merged, existing);
        for (String requiredValue : requiredValues) {
            appendCsv(merged, requiredValue);
        }
        if (merged.isEmpty()) {
            return false;
        }
        String normalized = String.join(",", merged);
        if (normalized.equals(existing)) {
            return false;
        }
        target.put(field, normalized);
        return true;
    }

    private static void appendCsv(LinkedHashSet<String> values, String rawCsv) {
        String normalizedCsv = trimToNull(rawCsv);
        if (normalizedCsv == null) {
            return;
        }
        for (String rawValue : normalizedCsv.split(",")) {
            String value = trimToNull(rawValue);
            if (value != null) {
                values.add(value);
            }
        }
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
