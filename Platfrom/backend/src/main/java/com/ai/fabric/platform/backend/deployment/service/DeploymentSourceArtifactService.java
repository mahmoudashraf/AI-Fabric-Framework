package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentSourceArtifactEntity;
import com.ai.fabric.platform.backend.deployment.model.CreateDeploymentSourceArtifactRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentSourceArtifactSummary;
import com.ai.fabric.platform.backend.deployment.model.PromoteDeploymentSourceArtifactRequest;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentSourceArtifactRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class DeploymentSourceArtifactService {

    private static final String DEFAULT_PROMOTION_CHANNEL = "staging";
    private static final Pattern IMAGE_DIGEST = Pattern.compile("sha256:[a-f0-9]{64}");

    private final DeploymentSourceArtifactRepository sourceArtifactRepository;
    private final PlatformAuditService platformAuditService;
    private final ObjectMapper objectMapper;
    private final DeploymentSourceCapabilityManifestService capabilityManifestService;

    public DeploymentSourceArtifactService(DeploymentSourceArtifactRepository sourceArtifactRepository,
                                           PlatformAuditService platformAuditService,
                                           ObjectMapper objectMapper,
                                           DeploymentSourceCapabilityManifestService capabilityManifestService) {
        this.sourceArtifactRepository = sourceArtifactRepository;
        this.platformAuditService = platformAuditService;
        this.objectMapper = objectMapper;
        this.capabilityManifestService = capabilityManifestService;
    }

    public List<DeploymentSourceArtifactSummary> list(String serviceName) {
        if (StringUtils.hasText(serviceName)) {
            return sourceArtifactRepository.findByServiceNameOrderByCreatedAtDesc(serviceName.trim()).stream()
                .map(this::toSummary)
                .toList();
        }
        return sourceArtifactRepository.findAll().stream()
            .sorted((left, right) -> right.getCreatedAt().compareTo(left.getCreatedAt()))
            .map(this::toSummary)
            .toList();
    }

    public DeploymentSourceArtifactEntity require(String artifactId) {
        if (!StringUtils.hasText(artifactId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Source artifact id is required.");
        }
        return sourceArtifactRepository.findById(artifactId.trim())
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Deployment source artifact not found: " + artifactId
            ));
    }

    public DeploymentSourceArtifactEntity latestPromoted(String serviceName, String promotionChannel) {
        String normalizedServiceName = requireText(serviceName, "serviceName");
        String normalizedChannel = normalizePromotionChannel(promotionChannel);
        return sourceArtifactRepository
            .findFirstByServiceNameAndPromotionChannelOrderByPromotedAtDesc(normalizedServiceName, normalizedChannel)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "No promoted source artifact found for " + normalizedServiceName + " on channel " + normalizedChannel + "."
            ));
    }

    @Transactional
    public DeploymentSourceArtifactSummary create(CreateDeploymentSourceArtifactRequest request) {
        String artifactType = requireText(request == null ? null : request.artifactType(), "artifactType")
            .toUpperCase(Locale.ROOT);
        if (!"DOCKER_IMAGE".equals(artifactType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only DOCKER_IMAGE source artifacts are supported for Coolify.");
        }

        DeploymentSourceArtifactEntity artifact = new DeploymentSourceArtifactEntity();
        artifact.setId("dsa-" + UUID.randomUUID().toString().substring(0, 8));
        artifact.setServiceName(requireText(request.serviceName(), "serviceName"));
        artifact.setArtifactType(artifactType);
        artifact.setImageRepository(requireText(request.imageRepository(), "imageRepository"));
        artifact.setImageTag(requireText(request.imageTag(), "imageTag"));
        String imageDigest = trimToNull(request.imageDigest());
        if (imageDigest != null && !IMAGE_DIGEST.matcher(imageDigest).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "imageDigest must be a lowercase sha256 digest.");
        }
        if (StringUtils.hasText(request.promotionChannel())) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Register the artifact first, then use the promote operation after review."
            );
        }
        artifact.setImageDigest(imageDigest);
        artifact.setGitCommitSha(trimToNull(request.gitCommitSha()));
        artifact.setBuildRunId(trimToNull(request.buildRunId()));
        artifact.setSbomRef(trimToNull(request.sbomRef()));
        artifact.setPromotionChannel(null);
        DeploymentSourceCapabilityManifestService.NormalizedCapabilityManifest capabilityManifest =
            capabilityManifestService.normalize(request.capabilityManifest());
        artifact.setCapabilityManifestJson(writeJson(capabilityManifest.manifest()));
        artifact.setCapabilityManifestHash(capabilityManifest.hash());
        Instant now = Instant.now();
        artifact.setCreatedAt(now);
        artifact.setPromotedAt(null);
        sourceArtifactRepository.save(artifact);

        platformAuditService.record(
            "DEPLOYMENT_SOURCE_ARTIFACT_CREATED",
            "DEPLOYMENT_SOURCE_ARTIFACT",
            artifact.getId(),
            Map.of(
                "serviceName", artifact.getServiceName(),
                "artifactType", artifact.getArtifactType(),
                "promotionChannel", artifact.getPromotionChannel() == null ? "" : artifact.getPromotionChannel()
            )
        );
        return toSummary(artifact);
    }

    @Transactional
    public DeploymentSourceArtifactSummary promote(String artifactId, PromoteDeploymentSourceArtifactRequest request) {
        DeploymentSourceArtifactEntity artifact = require(artifactId);
        if (!StringUtils.hasText(artifact.getImageDigest())) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Source artifact promotion requires an immutable imageDigest."
            );
        }
        JsonNode manifest = capabilityManifestService.read(artifact.getCapabilityManifestJson());
        DeploymentSourceCapabilityManifestService.NormalizedCapabilityManifest normalized =
            capabilityManifestService.normalize(manifest);
        if (!StringUtils.hasText(artifact.getCapabilityManifestHash())
            || !artifact.getCapabilityManifestHash().equals(normalized.hash())) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Source artifact promotion requires a valid content-hashed capability manifest."
            );
        }
        if (manifest.path("supportedBehaviorTypes").isEmpty()) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Source artifact promotion requires at least one supported behavior type."
            );
        }
        String promotionChannel = normalizePromotionChannel(request == null ? null : request.promotionChannel());
        artifact.setPromotionChannel(promotionChannel);
        artifact.setPromotedAt(Instant.now());
        sourceArtifactRepository.save(artifact);
        platformAuditService.record(
            "DEPLOYMENT_SOURCE_ARTIFACT_PROMOTED",
            "DEPLOYMENT_SOURCE_ARTIFACT",
            artifact.getId(),
            Map.of("serviceName", artifact.getServiceName(), "promotionChannel", promotionChannel)
        );
        return toSummary(artifact);
    }

    public DeploymentSourceArtifactSummary toSummary(DeploymentSourceArtifactEntity artifact) {
        return new DeploymentSourceArtifactSummary(
            artifact.getId(),
            artifact.getServiceName(),
            artifact.getArtifactType(),
            artifact.getImageRepository(),
            artifact.getImageTag(),
            artifact.getImageDigest(),
            imageReference(artifact),
            artifact.getGitCommitSha(),
            artifact.getBuildRunId(),
            artifact.getSbomRef(),
            artifact.getPromotionChannel(),
            capabilityManifestService.read(artifact.getCapabilityManifestJson()),
            artifact.getCapabilityManifestHash(),
            artifact.getCreatedAt(),
            artifact.getPromotedAt()
        );
    }

    private String imageReference(DeploymentSourceArtifactEntity artifact) {
        if (StringUtils.hasText(artifact.getImageDigest())) {
            return artifact.getImageRepository() + "@" + artifact.getImageDigest();
        }
        return artifact.getImageRepository() + ":" + artifact.getImageTag();
    }

    private String normalizePromotionChannel(String promotionChannel) {
        String normalized = trimToNull(promotionChannel);
        return normalized == null ? DEFAULT_PROMOTION_CHANNEL : normalized;
    }

    private String requireText(String value, String fieldName) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " is required.");
        }
        return normalized;
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String writeJson(com.fasterxml.jackson.databind.JsonNode value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to store source capability manifest.", ex);
        }
    }
}
