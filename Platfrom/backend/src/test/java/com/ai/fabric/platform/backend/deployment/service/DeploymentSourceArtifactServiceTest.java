package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentSourceArtifactEntity;
import com.ai.fabric.platform.backend.deployment.model.CreateDeploymentSourceArtifactRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentSourceArtifactRole;
import com.ai.fabric.platform.backend.deployment.model.PromoteDeploymentSourceArtifactRequest;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentSourceArtifactRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeploymentSourceArtifactServiceTest {

    private final DeploymentSourceArtifactRepository repository = mock(DeploymentSourceArtifactRepository.class);
    private final PlatformAuditService audit = mock(PlatformAuditService.class);
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final Map<String, DeploymentSourceArtifactEntity> artifacts = new HashMap<>();
    private DeploymentSourceArtifactService service;

    @BeforeEach
    void setUp() {
        when(repository.save(any())).thenAnswer(invocation -> {
            var artifact = (DeploymentSourceArtifactEntity) invocation.getArgument(0);
            artifacts.put(artifact.getId(), artifact);
            return artifact;
        });
        when(repository.findById(any())).thenAnswer(invocation ->
            Optional.ofNullable(artifacts.get(invocation.getArgument(0))));
        service = new DeploymentSourceArtifactService(
            repository,
            audit,
            mapper,
            new DeploymentSourceCapabilityManifestService(mapper)
        );
    }

    @Test
    void promotesManagedDependencyWithoutPretendingItIsARuntime() {
        var created = service.create(request(DeploymentSourceArtifactRole.MANAGED_DEPENDENCY, null));

        var promoted = service.promote(created.id(), new PromoteDeploymentSourceArtifactRequest("staging"));

        assertThat(promoted.artifactRole()).isEqualTo("MANAGED_DEPENDENCY");
        assertThat(promoted.promotionChannel()).isEqualTo("staging");
        assertThat(promoted.promotedAt()).isNotNull();
        assertThat(promoted.capabilityManifestHash()).isNull();
    }

    @Test
    void runtimePromotionStillRequiresReviewedRuntimeCapabilities() {
        var created = service.create(request(DeploymentSourceArtifactRole.RUNTIME, null));

        assertThatThrownBy(() -> service.promote(
            created.id(), new PromoteDeploymentSourceArtifactRequest("staging")))
            .hasMessageContaining("capabilityManifest");
    }

    private CreateDeploymentSourceArtifactRequest request(DeploymentSourceArtifactRole role,
                                                           com.fasterxml.jackson.databind.JsonNode manifest) {
        return new CreateDeploymentSourceArtifactRequest(
            "service",
            "DOCKER_IMAGE",
            "registry.example.test/service",
            "build-1",
            "sha256:" + "a".repeat(64),
            "b".repeat(40),
            "run-1",
            null,
            null,
            role,
            manifest
        );
    }
}
