package com.ai.fabric.runtime.tasks;

import static com.ai.fabric.runtime.tasks.ManagedTaskFixtures.*;
import static org.assertj.core.api.Assertions.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ManagedTasksBundleTest {
    @Test void acceptsTheActualMainCompilerOutputAndTextProjectionContract() {
        var content = fixture();
        var bundle = bundle(content);
        assertThat(bundle.enabled()).isTrue();
        assertThat(bundle.catalogue()).isEqualTo(content.path("tasks"));
        assertThat(bundle.tasks()).hasSize(1);
        assertThat(bundle(textFixture()).tasks().getFirst().outputMode()).isEqualTo("TEXT");
    }
    @Test void absenceDisablesWithoutAHashButDanglingHashIsRejected() {
        assertThat(new ManagedTasksBundle(MAPPER, "", "").enabled()).isFalse();
        assertThatThrownBy(() -> new ManagedTasksBundle(MAPPER, "", "sha256:x")).isInstanceOf(IllegalStateException.class);
    }
    @Test void rejectsTamperingAndOversizeBeforeParsing() throws Exception {
        var bytes = MAPPER.writeValueAsBytes(fixture());
        assertThatThrownBy(() -> new ManagedTasksBundle(MAPPER, Base64.getEncoder().encodeToString(bytes), "sha256:wrong"))
            .isInstanceOf(IllegalStateException.class).hasMessageNotContaining("constraints");
        byte[] huge = new byte[65_537];
        assertThatThrownBy(() -> new ManagedTasksBundle(MAPPER, Base64.getEncoder().encodeToString(huge), hash(huge)))
            .isInstanceOf(IllegalStateException.class);
    }
    @Test void rejectsDuplicateTasksAndRequestOwnedOverrides() {
        var content = fixture();
        ((com.fasterxml.jackson.databind.node.ArrayNode) content.path("tasks")).add(content.at("/tasks/0").deepCopy());
        assertThatThrownBy(() -> bundle(content)).isInstanceOf(IllegalStateException.class);
        var overridden = fixture();
        ((ObjectNode) overridden.at("/tasks/0")).put("prompt", "do something else");
        assertThatThrownBy(() -> bundle(overridden)).isInstanceOf(IllegalStateException.class);
    }
    @Test void rejectsWriteDelegationAndInteractiveManifestContracts() {
        var content = fixture();
        ((ObjectNode) resource(content, "Specialist").at("/spec/execution")).put("writePolicy", "PROPOSE_ONLY");
        assertThatThrownBy(() -> bundle(content)).isInstanceOf(IllegalStateException.class);
        var delegated = fixture();
        ((ObjectNode) resource(delegated, "Specialist").path("spec")).putObject("delegation").putArray("targets").add("other@1");
        assertThatThrownBy(() -> bundle(delegated)).isInstanceOf(IllegalStateException.class);
    }
    @ParameterizedTest @ValueSource(strings = {"https://private.invalid/schema", "file:///etc/passwd"})
    void rejectsExternalSchemaReferences(String ref) {
        var content = fixture();
        ((ObjectNode) content.at("/tasks/0/inputSchema")).put("$ref", ref);
        assertThatThrownBy(() -> bundle(content)).isInstanceOf(IllegalStateException.class);
    }
    @Test void validatesRelocatedLocalReferencesWithoutChangingThePublicSchema() throws Exception {
        var content = fixture();
        var input = MAPPER.readTree("""
            {"type":"object","$defs":{"value":{"type":"string"}},"properties":{"label":{"$ref":"#/$defs/value"}}}
            """);
        ((ObjectNode) content.at("/tasks/0")).set("inputSchema", input);
        var relocated = input.deepCopy();
        ((ObjectNode) relocated.at("/properties/label")).put("$ref", "#/properties/taskInput/$defs/value");
        ((ObjectNode) resource(content, "SpecialistSchema").at("/spec/schema/properties")).set("taskInput", relocated);
        var bundle = bundle(content);
        bundle.validateInput(bundle.tasks().getFirst(), MAPPER.createObjectNode().put("label", "valid"));
        assertThatThrownBy(() -> bundle.validateInput(bundle.tasks().getFirst(), MAPPER.createObjectNode().put("label", 3)))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
