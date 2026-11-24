package com.ai.fabric.platform.backend.deployment;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentSourceArtifactEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentTargetProfileEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentSourceArtifactRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.ai.fabric.platform.backend.deployment.service.DeploymentSourceCapabilityManifestService;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

final class ReviewedRuntimeApplyFixture {
    record Selection(String targetProfileId, String sourceArtifactId) {}

    static Selection create(ObjectMapper mapper, DeploymentTargetProfileRepository profiles,
                            DeploymentSourceArtifactRepository artifacts) throws Exception {
        return create(mapper, profiles, artifacts, "test-target-" + UUID.randomUUID());
    }

    static Selection create(ObjectMapper mapper, DeploymentTargetProfileRepository profiles,
                            DeploymentSourceArtifactRepository artifacts, String targetId) throws Exception {
        Instant now = Instant.now();
        String suffix = UUID.randomUUID().toString();
        var target = new DeploymentTargetProfileEntity();
        target.setId(targetId);
        target.setName("Reviewed runtime contract fixture");
        target.setProviderType(DeploymentProviderType.COOLIFY);
        target.setEnvironmentName("staging");
        target.setActive(true);
        target.setSourceStrategy("IMAGE_SOURCE");
        target.setProviderConfigJson("{}");
        target.setNetworkPolicyJson("{}");
        target.setResourceDefaultsJson("{}");
        target.setCreatedAt(now);
        target.setUpdatedAt(now);
        profiles.saveAndFlush(target);

        Path declaration = Path.of(System.getProperty("basedir", "."))
            .resolve("../../ai-infrastructure-module/ai-fabric-runtime/src/main/resources/META-INF/loomai/runtime-capabilities.json");
        var manifest = new DeploymentSourceCapabilityManifestService(mapper)
            .normalize(mapper.readTree(Files.readString(declaration)));
        var artifact = new DeploymentSourceArtifactEntity();
        artifact.setId("test-artifact-" + suffix);
        artifact.setServiceName("ai-fabric-runtime");
        artifact.setArtifactType("DOCKER_IMAGE");
        artifact.setImageRepository("registry.test.invalid/reviewed-runtime");
        artifact.setImageDigest("sha256:" + "a".repeat(64));
        artifact.setGitCommitSha("b".repeat(40));
        artifact.setBuildRunId("synthetic-reviewed-contract");
        artifact.setPromotionChannel("staging");
        artifact.setCapabilityManifestJson(manifest.manifest().toString());
        artifact.setCapabilityManifestHash(manifest.hash());
        artifact.setCreatedAt(now);
        artifact.setPromotedAt(now);
        artifacts.saveAndFlush(artifact);
        return new Selection(target.getId(), artifact.getId());
    }
}
