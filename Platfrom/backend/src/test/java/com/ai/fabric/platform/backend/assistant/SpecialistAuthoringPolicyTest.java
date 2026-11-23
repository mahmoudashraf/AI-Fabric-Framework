package com.ai.fabric.platform.backend.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class SpecialistAuthoringPolicyTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test void supportedManagerEditPreservesUnknownAndCompiledFields() throws Exception {
        var before = fixture(); var after = before.deepCopy();
        ((ObjectNode) after.at("/behaviorConfig/specialistAssistance/resolutions/0")).put("instructions", "Use approved facts.");
        assertThatCode(() -> SpecialistAuthoringPolicy.requireSupportedEdits(before, after)).doesNotThrowAnyException();
    }

    @Test void newUndeclaredFieldsAreRejectedEvenWhileDisabledWithoutEchoingValues() throws Exception {
        for (String pointer : new String[] {"/behaviorConfig/specialistAssistance", "/behaviorConfig/specialistAssistance/resolutions/0",
            "/behaviorConfig/specialistAssistance/areas/0", "/behaviorConfig/specialistAssistance/limits"}) {
            var before = fixture(); var after = before.deepCopy();
            ((ObjectNode) after.at(pointer)).put("inventedField", "private-value-canary");
            assertThatThrownBy(() -> SpecialistAuthoringPolicy.requireSupportedEdits(before, after))
                .hasMessageContaining("SPECIALIST_AUTHORING_FIELD_UNSUPPORTED").hasMessageNotContaining("private-value-canary");
        }
    }

    @Test void existingUnknownOrServerMetadataCannotBeChangedOrSilentlyDropped() throws Exception {
        for (String name : new String[] {"future", "compiled", "contentHash"}) {
            var before = fixture(); var changed = before.deepCopy();
            ((ObjectNode) changed.at("/behaviorConfig/specialistAssistance")).put(name, "changed");
            assertThatThrownBy(() -> SpecialistAuthoringPolicy.requireSupportedEdits(before, changed)).hasMessageContaining("FIELD_UNSUPPORTED");
            var removed = before.deepCopy(); ((ObjectNode) removed.at("/behaviorConfig/specialistAssistance")).remove(name);
            assertThatThrownBy(() -> SpecialistAuthoringPolicy.requireSupportedEdits(before, removed)).hasMessageContaining("FIELD_UNSUPPORTED");
        }
    }

    @Test void freshConfigurationCannotSupplyCompiledMetadata() throws Exception {
        var before = fixture(); ((ObjectNode) before.at("/behaviorConfig/specialistAssistance")).remove("compiled");
        var after = before.deepCopy(); ((ObjectNode) after.at("/behaviorConfig/specialistAssistance")).putObject("compiled");
        assertThatThrownBy(() -> SpecialistAuthoringPolicy.requireSupportedEdits(before, after)).hasMessageContaining("FIELD_UNSUPPORTED");
    }

    @Test void explicitWholeFeatureRemovalIsLeftToTheExistingEnablementAndAuthorityChecks() throws Exception {
        var before = fixture(); var after = before.deepCopy(); ((ObjectNode) after.path("behaviorConfig")).remove("specialistAssistance");
        assertThatCode(() -> SpecialistAuthoringPolicy.requireSupportedEdits(before, after)).doesNotThrowAnyException();
    }

    private ObjectNode fixture() throws Exception {
        return (ObjectNode) mapper.readTree("""
            {"behaviorConfig":{"specialistAssistance":{"enabled":false,"future":{"keep":true},"compiled":{},"contentHash":"original",
              "areas":[{"id":"knowledge","instructions":"Keep","readActions":[],"vectorSpaces":[]}],
              "resolutions":[{"id":"setup","objective":"Inspect","instructions":"Original","areaIds":["knowledge"],"allowParallel":false}],
              "limits":{"maxDecisions":6,"maxWorkerInvocations":4,"maxParallelWorkers":2,"deadlineSeconds":75}}}}
            """);
    }
}
