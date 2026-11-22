package com.ai.fabric.platform.backend.deployment.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Executable runtime support, not a semantic or customer-access restriction. */
public final class DeploymentDecisionRecoveryConfig {
    public static final String CAPABILITY = "internal-decision-recovery-v1";
    private DeploymentDecisionRecoveryConfig() { }

    public static boolean requested(JsonNode provider) {
        return provider != null && provider.path("decisionRecovery").isObject();
    }

    public static void requireSupport(JsonNode manifest) {
        if (manifest != null && manifest.path("capabilities").isArray()) {
            for (JsonNode capability : manifest.path("capabilities"))
                if (CAPABILITY.equals(capability.asText())) return;
        }
        throw new ResponseStatusException(HttpStatus.CONFLICT,
            "The selected runtime does not support internal decision repair and context answering. Choose a compatible runtime update.");
    }
}
