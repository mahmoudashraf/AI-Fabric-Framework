package com.ai.fabric.platform.backend.externalintegration;

import com.fasterxml.jackson.databind.JsonNode;

/** The immutable compiler marker, not a plugin name or endpoint heuristic, activates binding checks. */
public final class ExternalIntegrationRequirement {
    private ExternalIntegrationRequirement() { }

    public static boolean required(JsonNode provenance) {
        JsonNode profiles = provenance == null ? null : provenance.get("externalIntegrationProfiles");
        if (profiles == null) return false;
        if (!profiles.isArray()) throw ExternalIntegrationPolicy.conflict("COMPILER_EVIDENCE_INVALID");
        return !profiles.isEmpty();
    }
}
