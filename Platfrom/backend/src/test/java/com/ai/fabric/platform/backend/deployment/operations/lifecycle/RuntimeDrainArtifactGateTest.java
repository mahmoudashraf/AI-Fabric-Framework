package com.ai.fabric.platform.backend.deployment.operations.lifecycle;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class RuntimeDrainArtifactGateTest {
    private final ObjectMapper mapper = new ObjectMapper();
    @Test void singletonGenerationArtifactsCannotEnableTheNewGate() throws Exception {
        assertThatThrownBy(() -> RuntimeDrainArtifactGate.require(mapper.readTree("""
            {"capabilities":["runtime-cold-maintenance-generation-adoption-v1"],
             "endpointClasses":["runtime-cold-maintenance-generation-adoption"],"migrationIds":[]}
            """))).hasMessageContaining("RELEASE_SCOPED_ARTIFACT_REQUIRED");
    }
    @Test void allThreeExactStructuredCapabilitiesAreRequired() throws Exception {
        var manifest = mapper.readTree("""
            {"capabilities":["runtime-release-scoped-drain-v1"],
             "endpointClasses":["runtime-release-scoped-drain"],"migrationIds":["runtime-release-scoped-retirement-v24"]}
            """);
        RuntimeDrainArtifactGate.require(manifest);
        for (String field : java.util.List.of("capabilities", "endpointClasses", "migrationIds")) {
            var changed = manifest.deepCopy(); ((com.fasterxml.jackson.databind.node.ObjectNode) changed).putArray(field);
            assertThatThrownBy(() -> RuntimeDrainArtifactGate.require(changed)).hasMessageContaining("RELEASE_SCOPED_ARTIFACT_REQUIRED");
        }
    }
}
