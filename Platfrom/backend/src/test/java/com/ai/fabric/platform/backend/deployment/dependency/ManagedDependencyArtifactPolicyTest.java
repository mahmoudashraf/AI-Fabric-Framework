package com.ai.fabric.platform.backend.deployment.dependency;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentSourceArtifactEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentSourceArtifactRepository;
import com.ai.fabric.platform.backend.deployment.service.DeploymentSourceCapabilityManifestService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ManagedDependencyArtifactPolicyTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final DeploymentSourceArtifactRepository artifacts = mock(DeploymentSourceArtifactRepository.class);
    private final DeploymentSourceCapabilityManifestService manifests = new DeploymentSourceCapabilityManifestService(mapper);
    private final ManagedDependencyArtifactPolicy policy = new ManagedDependencyArtifactPolicy(artifacts, manifests);
    private final ManagedDependencySpec.Service service = ManagedDependencyFixtures.http("ai-service", true);

    @Test
    void allowsReviewedPinnedAiServiceWithoutImageNameConventions() {
        policy.requireAllowed(service, ManagedDependencyFixtures.ownedAiArtifact(service));
    }

    @ParameterizedTest
    @CsvSource({
        "purpose,CUSTOMER_BUSINESS_SYSTEM,CUSTOMER_DEPENDENCY_OWNERSHIP_FORBIDDEN",
        "purpose,EXTERNAL_PROVIDER_FIXTURE,CUSTOMER_DEPENDENCY_OWNERSHIP_FORBIDDEN",
        "lifecycleOwnership,EXTERNAL_RETAINED,CUSTOMER_DEPENDENCY_OWNERSHIP_FORBIDDEN",
        "persistence,CUSTOMER_BUSINESS_DATA,CUSTOMER_DEPENDENCY_OWNERSHIP_FORBIDDEN",
        "persistence,NONE,DEPENDENCY_ARTIFACT_PERSISTENCE_UNPROVEN",
        "initializationContract,another-contract/v1,DEPENDENCY_ARTIFACT_CONTRACT_MISMATCH",
        "serviceContract,BASIC_INBOX_KEYS_V1,DEPENDENCY_ARTIFACT_CONTRACT_MISMATCH"
    })
    void rejectsIncompatibleReviewedContracts(String field, String value, String code) {
        var artifact = ManagedDependencyFixtures.ownedAiArtifact(service);
        rewrite(artifact, field, value);
        assertThatThrownBy(() -> policy.requireAllowed(service, artifact)).hasMessageContaining(code);
    }

    @Test
    void changingDeclarationWithoutNewContentHashCannotGrantOwnership() {
        var artifact = ManagedDependencyFixtures.ownedAiArtifact(service);
        var node = (ObjectNode) manifests.read(artifact.getCapabilityManifestJson());
        ((ObjectNode) node.path("managedDependencyContract")).put("initializationContract", "forged/v1");
        artifact.setCapabilityManifestJson(node.toString());
        assertThatThrownBy(() -> policy.requireAllowed(service, artifact))
            .hasMessageContaining("DEPENDENCY_ARTIFACT_OWNERSHIP_UNPROVEN");
    }

    @Test
    void promotedBusinessArtifactCannotBeHiddenByAnAiSlotLabel() {
        var artifact = ManagedDependencyFixtures.ownedAiArtifact(service);
        rewrite(artifact, "purpose", "CUSTOMER_BUSINESS_SYSTEM");
        when(artifacts.findById(service.sourceArtifactId())).thenReturn(Optional.of(artifact));
        assertThatThrownBy(() -> policy.requireAllowed(new ManagedDependencySpec("managed-dependencies/v1",
            java.util.List.of(service)))).hasMessageContaining("CUSTOMER_DEPENDENCY_OWNERSHIP_FORBIDDEN");
    }

    @Test
    void oldRealCustomerFixtureHasNoImplicitOwnershipGrandfathering() throws Exception {
        try (var input = getClass().getResourceAsStream(
            "/managed-dependency/simulated-dealership-experience-v1-6.template.json")) {
            var oldSpec = mapper.readValue(input, ManagedDependencySpec.class);
            for (var dependency : oldSpec.services()) {
                var artifact = ManagedDependencyFixtures.ownedAiArtifact(dependency);
                artifact.setCapabilityManifestJson("{}");
                artifact.setCapabilityManifestHash(null);
                when(artifacts.findById(dependency.sourceArtifactId())).thenReturn(Optional.of(artifact));
            }
            assertThatThrownBy(() -> policy.requireAllowed(oldSpec))
                .hasMessageContaining("DEPENDENCY_ARTIFACT_OWNERSHIP_UNPROVEN");
        }
    }

    @Test
    void normalizesManagedContractWithoutClaimingFrameworkRuntimeCapabilities() {
        var artifact = ManagedDependencyFixtures.ownedAiArtifact(service);
        var normalized = manifests.normalize(manifests.read(artifact.getCapabilityManifestJson()));
        assertThat(normalized.manifest().has("aiFabricVersion")).isFalse();
        assertThat(normalized.hash()).isEqualTo(artifact.getCapabilityManifestHash());
    }

    @Test
    void unknownFieldsAndCoercedValuesAreRejectedRatherThanIgnored() {
        var artifact = ManagedDependencyFixtures.ownedAiArtifact(service);
        var node = (ObjectNode) manifests.read(artifact.getCapabilityManifestJson());
        ((ObjectNode) node.path("managedDependencyContract")).put("allowBusinessData", true);
        assertThatThrownBy(() -> manifests.normalize(node))
            .hasMessageContaining("DEPENDENCY_ARTIFACT_CONTRACT_INVALID");
        ((ObjectNode) node.path("managedDependencyContract")).remove("allowBusinessData");
        ((ObjectNode) node.path("managedDependencyContract")).put("persistence", true);
        assertThatThrownBy(() -> manifests.normalize(node))
            .hasMessageContaining("DEPENDENCY_ARTIFACT_CONTRACT_INVALID");
    }

    private void rewrite(DeploymentSourceArtifactEntity artifact, String field, String value) {
        var node = (ObjectNode) manifests.read(artifact.getCapabilityManifestJson());
        ((ObjectNode) node.path("managedDependencyContract")).put(field, value);
        var normalized = manifests.normalize(node);
        artifact.setCapabilityManifestJson(normalized.manifest().toString());
        artifact.setCapabilityManifestHash(normalized.hash());
    }
}
