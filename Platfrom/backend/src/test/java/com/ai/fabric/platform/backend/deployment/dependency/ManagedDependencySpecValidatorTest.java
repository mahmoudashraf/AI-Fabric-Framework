package com.ai.fabric.platform.backend.deployment.dependency;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class ManagedDependencySpecValidatorTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final ManagedDependencySpecValidator validator = new ManagedDependencySpecValidator(mapper);
    @Test void accountsForCompleteTopologyAndRoundTripFingerprint() throws Exception {
        var spec = ManagedDependencyFixtures.topology(); validator.validate(spec);
        assertThat(spec.totalCapacity().resources()).isEqualTo(4);
        assertThat(spec.totalCapacity().diskMiB()).isEqualTo(6528);
        assertThat(validator.fingerprint(mapper.readValue(mapper.writeValueAsString(spec), ManagedDependencySpec.class)))
            .isEqualTo(validator.fingerprint(spec));
    }
    @Test void mutableTagsAndFakeDigestsAreRejected() {
        for (String image : List.of("repo:latest", "repo:v1", "repo@sha256:abc")) {
            assertThatThrownBy(() -> ManagedDependencySpecValidator.immutableImage(image)).hasMessageContaining("IMMUTABLE_IMAGE_REQUIRED");
        }
    }
    @Test void unknownSchemaAndEmptyCompositionAreRejected() {
        assertThatThrownBy(() -> validator.validate(new ManagedDependencySpec("old", List.of()))).hasMessageContaining("SPEC_INVALID");
    }
    @Test void commandIdentityRejectsPayloadSubstitution() {
        var state = new ManagedDependencyState();
        assertThat(ManagedDependencyLifecycleService.command(state, "key", "fp", "result")).isTrue();
        assertThat(ManagedDependencyLifecycleService.command(state, "key", "fp", "result")).isFalse();
        assertThatThrownBy(() -> ManagedDependencyLifecycleService.command(state, "key", "different", "result"))
            .hasMessageContaining("DEPENDENCY_OPERATION_KEY_CONFLICT");
    }
    @Test void generatedKeysAndManagedNamesMeetExistingSecretContract() {
        assertThat(ManagedDependencyLifecycleService.credential()).hasSize(43).doesNotContain("|", ";", ",");
        assertThatThrownBy(() -> ManagedDependencyLifecycleService.https("http://production.example.test/callback"))
            .hasMessageContaining("DEPENDENCY_TRUSTED_HTTPS_REQUIRED");
    }
}
