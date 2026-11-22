package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.config.PlatformDeliveryProperties;
import com.ai.fabric.platform.backend.deployment.behavior.DeploymentBehaviorCatalogService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.deployment.specialist.SpecialistAssistanceCompiler;
import com.ai.fabric.platform.backend.deployment.specialist.SpecialistAssistanceFixtures;
import com.ai.fabric.platform.backend.deployment.specialist.SpecialistBundleDelivery;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SpecialistArtifactDeliveryTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void signedArtifactIsExactImmutableBoundedContentAndRejectsMissingExpiredCrossArtifactOrTamperedSignatures() throws Exception {
        var deployments = mock(DeploymentRepository.class);
        var versions = mock(DeploymentVersionRepository.class);
        var secrets = mock(PlatformSecretService.class);
        var deployment = new DeploymentEntity(); deployment.setId("dep-test");
        var version = new DeploymentVersionEntity(); version.setId("ver-test"); version.setDeploymentId("dep-test");
        version.setPublishedAt(Instant.now());
        var behavior = new SpecialistAssistanceCompiler(mapper).compileBehavior(
            SpecialistAssistanceFixtures.behavior(mapper), SpecialistAssistanceFixtures.authority());
        version.setBehaviorConfigJson(behavior.toString());
        when(deployments.findById("dep-test")).thenReturn(Optional.of(deployment));
        when(versions.findById("ver-test")).thenReturn(Optional.of(version));
        when(secrets.resolveSecret("PLATFORM_ARTIFACT_SIGNING_KEY")).thenReturn("local-test-only-signing-key");
        var service = new DeploymentArtifactService(deployments, versions,
            new PlatformDeliveryProperties("https://platform.example", true, Duration.ofDays(1)),
            secrets, mock(PlatformAuditService.class), mock(DeploymentAccessService.class), mapper);
        var uri = URI.create(service.specialistAssistanceArtifactUrl(version));
        Map<String,String> query = Arrays.stream(uri.getRawQuery().split("&")).map(v -> v.split("=", 2))
            .collect(Collectors.toMap(v -> v[0], v -> URLDecoder.decode(v[1], StandardCharsets.UTF_8)));
        long expires = Long.parseLong(query.get("expires"));
        String signature = query.get("sig");
        assertThat(service.readSpecialistAssistanceArtifact("dep-test", "ver-test", expires, signature))
            .isEqualTo(new SpecialistAssistanceCompiler(mapper).serialized(behavior.at("/specialistAssistance/compiled")));
        assertThatThrownBy(() -> service.readSpecialistAssistanceArtifact("dep-test", "ver-test", null, null))
            .hasMessageContaining("401");
        assertThatThrownBy(() -> service.readSpecialistAssistanceArtifact("dep-test", "ver-test", 1L, signature))
            .hasMessageContaining("401");
        assertThatThrownBy(() -> service.readSpecialistAssistanceArtifact("dep-test", "ver-test", expires, signature + "x"))
            .hasMessageContaining("401");
        assertThatThrownBy(() -> service.readManifestArtifact("dep-test", "ver-test", expires, signature))
            .hasMessageContaining("401");
        ((ObjectNode) behavior.at("/specialistAssistance/compiled")).put("tampered", true);
        version.setBehaviorConfigJson(behavior.toString());
        assertThatThrownBy(() -> service.readSpecialistAssistanceArtifact("dep-test", "ver-test", expires, signature))
            .hasMessageContaining("hash mismatch");
        version.setBehaviorConfigJson("{\"type\":\"CONVERSATIONAL\"}");
        assertThatThrownBy(() -> service.readSpecialistAssistanceArtifact("dep-test", "ver-test", expires, signature))
            .hasMessageContaining("404");
    }

    @Test
    void onlyLargeBundlesRequireNewBootstrapCapabilityBeforeApply() {
        var behavior = SpecialistAssistanceFixtures.behavior(mapper);
        var compiled = behavior.with("specialistAssistance").putObject("compiled");
        compiled.put("test", "x".repeat(SpecialistBundleDelivery.MAX_INLINE_BYTES));
        var catalogue = new DeploymentBehaviorCatalogService(mapper);
        assertThat(SpecialistBundleDelivery.requiresArtifact(behavior, mapper)).isTrue();
        assertThat(catalogue.releaseRequirements(behavior).capabilities()).contains(SpecialistBundleDelivery.CAPABILITY);
        compiled.put("test", "small");
        assertThat(SpecialistBundleDelivery.requiresArtifact(behavior, mapper)).isFalse();
        assertThat(catalogue.releaseRequirements(behavior).capabilities()).doesNotContain(SpecialistBundleDelivery.CAPABILITY);
    }
}
