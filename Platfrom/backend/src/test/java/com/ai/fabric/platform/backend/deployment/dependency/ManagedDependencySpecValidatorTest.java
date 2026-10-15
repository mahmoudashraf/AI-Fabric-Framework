package com.ai.fabric.platform.backend.deployment.dependency;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
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
    @Test void lifecycleIdentityEnvironmentCannotBeOmittedFromAReviewedService() {
        var original = ManagedDependencyFixtures.http("simulator", false);
        var environment = new TreeMap<>(original.environment());
        environment.remove("LOOMAI_MANAGED_DEPENDENCY_VERSION_ID");
        var invalid = new ManagedDependencySpec.Service(original.slot(), original.contract(), original.image(),
            original.sourceArtifactId(), original.sourceCommit(), original.port(), original.healthPath(),
            original.capacity(), original.secretPurposes(), Map.copyOf(environment), original.database(),
            original.volumes(), original.dependsOn(), original.initializationContract());
        assertThatThrownBy(() -> validator.validate(new ManagedDependencySpec("managed-dependencies/v1", List.of(invalid))))
            .hasMessageContaining("MANAGED_DEPENDENCY_LIFECYCLE_CONTRACT_INVALID");
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

    @Test void serviceHostIsAllowedOnlyForAReviewedDependencyService() {
        var spec = ManagedDependencyFixtures.topology();
        validator.validate(spec);

        var invalid = new ManagedDependencySpec(spec.schemaVersion(), spec.services(),
            List.of(new ManagedDependencySpec.RuntimeEnvironmentBinding(
                ManagedDependencySpec.RuntimeTarget.CONNECTOR,
                "UNTRUSTED_HOST",
                new ManagedDependencySpec.Value(ManagedDependencySpec.ValueKind.SERVICE_HOST, "missing"))));
        assertThatThrownBy(() -> validator.validate(invalid)).hasMessageContaining("RUNTIME_BINDING_INVALID");
    }

    @Test void serviceUrlPathMustReferenceAReviewedServiceAndRelativePath() {
        var original = ManagedDependencyFixtures.topology();
        var valid = new ManagedDependencySpec(original.schemaVersion(), original.services(),
            List.of(new ManagedDependencySpec.RuntimeEnvironmentBinding(
                ManagedDependencySpec.RuntimeTarget.CONNECTOR,
                "MANAGED_RECEIVER_ACTION_URL",
                new ManagedDependencySpec.Value(
                    ManagedDependencySpec.ValueKind.SERVICE_URL_PATH,
                    "/api/internal/actions/execute",
                    "receiver"
                ))));
        assertThatNoException().isThrownBy(() -> validator.validate(valid));

        var invalid = new ManagedDependencySpec(original.schemaVersion(), original.services(),
            List.of(new ManagedDependencySpec.RuntimeEnvironmentBinding(
                ManagedDependencySpec.RuntimeTarget.CONNECTOR,
                "MANAGED_RECEIVER_ACTION_URL",
                new ManagedDependencySpec.Value(
                    ManagedDependencySpec.ValueKind.SERVICE_URL_PATH,
                    "https://unreviewed.example/actions",
                    "receiver"
                ))));
        assertThatThrownBy(() -> validator.validate(invalid)).hasMessageContaining("RUNTIME_BINDING_INVALID");

        var traversal = new ManagedDependencySpec(original.schemaVersion(), original.services(),
            List.of(new ManagedDependencySpec.RuntimeEnvironmentBinding(
                ManagedDependencySpec.RuntimeTarget.CONNECTOR,
                "MANAGED_RECEIVER_ACTION_URL",
                new ManagedDependencySpec.Value(
                    ManagedDependencySpec.ValueKind.SERVICE_URL_PATH,
                    "/api/../internal/actions/execute",
                    "receiver"
                ))));
        assertThatThrownBy(() -> validator.validate(traversal)).hasMessageContaining("RUNTIME_BINDING_INVALID");
    }

    @Test void targetSecretRuntimeBindingMustUseAReviewedAuthorityPurposeWithoutAServiceReference() {
        var topology = ManagedDependencyFixtures.topology();
        assertThatNoException().isThrownBy(() -> validator.validate(topology));
        assertThat(topology.runtimeEnvironment()).anySatisfy(binding -> {
            assertThat(binding.name()).isEqualTo("MANAGED_INBOX_CALLBACK_SIGNING_SECRET");
            assertThat(binding.value().kind()).isEqualTo(ManagedDependencySpec.ValueKind.TARGET_SECRET);
            assertThat(binding.value().reference()).isEqualTo("conversation-handover-callback-signing");
        });

        var invalid = new ManagedDependencySpec.RuntimeEnvironmentBinding(
            ManagedDependencySpec.RuntimeTarget.CONNECTOR,
            "MANAGED_INVALID_TARGET_SECRET",
            new ManagedDependencySpec.Value(
                ManagedDependencySpec.ValueKind.TARGET_SECRET,
                "conversation-handover-callback-signing",
                "inbox"
            )
        );
        var spec = new ManagedDependencySpec(topology.schemaVersion(), topology.services(),
            java.util.stream.Stream.concat(topology.runtimeEnvironment().stream(), java.util.stream.Stream.of(invalid)).toList());
        assertThatThrownBy(() -> validator.validate(spec)).hasMessageContaining("RUNTIME_BINDING_INVALID");
    }
}
