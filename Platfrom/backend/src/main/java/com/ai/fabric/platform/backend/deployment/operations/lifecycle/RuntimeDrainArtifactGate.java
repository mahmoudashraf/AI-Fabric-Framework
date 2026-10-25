package com.ai.fabric.platform.backend.deployment.operations.lifecycle;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.CONFLICT;

/** Optional drain must not be enabled on an older singleton-generation runtime image. */
public final class RuntimeDrainArtifactGate {
    private RuntimeDrainArtifactGate() { }
    public static void require(JsonNode manifest) {
        if (!contains(manifest.path("capabilities"), "runtime-release-scoped-drain-v1")
            || !contains(manifest.path("endpointClasses"), "runtime-release-scoped-drain")
            || !contains(manifest.path("migrationIds"), "runtime-release-scoped-retirement-v24"))
            throw new ResponseStatusException(CONFLICT, "OPERATIONAL_DRAIN_RELEASE_SCOPED_ARTIFACT_REQUIRED");
    }
    private static boolean contains(JsonNode values, String expected) {
        if (!values.isArray()) return false;
        for (JsonNode value : values) if (value.isTextual() && expected.equals(value.textValue())) return true;
        return false;
    }
}
