package com.ai.fabric.platform.backend.deployment.dependency;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerOfferEntitlementService;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentSourceArtifactRepository;
import com.ai.fabric.platform.backend.marketplace.repository.MarketplacePluginRepository;
import com.ai.fabric.platform.backend.marketplace.repository.MarketplacePluginVersionRepository;
import com.ai.fabric.platform.backend.security.PlatformSecurityContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Map;

import static org.springframework.http.HttpStatus.CONFLICT;

/** Publishes one immutable, reviewed dependency topology for an exact template version. */
@Service
public class ManagedDependencySpecPublishingService {
    private final ManagedDependencySpecRepository specs;
    private final ManagedDependencySpecValidator validator;
    private final MarketplacePluginVersionRepository versions;
    private final MarketplacePluginRepository plugins;
    private final DeploymentSourceArtifactRepository artifacts;
    private final CustomerOfferEntitlementService staff;
    private final PlatformAuditService audit;
    private final ObjectMapper mapper;

    public ManagedDependencySpecPublishingService(ManagedDependencySpecRepository specs,
            ManagedDependencySpecValidator validator, MarketplacePluginVersionRepository versions,
            MarketplacePluginRepository plugins, DeploymentSourceArtifactRepository artifacts,
            CustomerOfferEntitlementService staff, PlatformAuditService audit, ObjectMapper mapper) {
        this.specs = specs;
        this.validator = validator;
        this.versions = versions;
        this.plugins = plugins;
        this.artifacts = artifacts;
        this.staff = staff;
        this.audit = audit;
        this.mapper = mapper;
    }

    public record PublicationRequest(String specId, ManagedDependencySpec spec, String reviewReference) { }
    public record PublicationSummary(String specId, String templateVersionId, String fingerprint,
                                     String reviewReference, Instant reviewedAt) { }

    @Transactional
    public PublicationSummary publish(String templateVersionId, PublicationRequest request) {
        staff.requireStaffAdmin();
        if (!identifier(templateVersionId) || request == null || !identifier(request.specId())
            || request.reviewReference() == null || request.reviewReference().isBlank()
            || request.reviewReference().length() > 255
            || !request.reviewReference().equals(request.reviewReference().trim())) {
            throw conflict("DEPENDENCY_SPEC_PUBLICATION_INVALID");
        }
        validator.validate(request.spec());
        var version = versions.findById(templateVersionId)
            .orElseThrow(() -> conflict("DEPENDENCY_TEMPLATE_VERSION_MISSING"));
        var plugin = plugins.findById(version.getPluginId())
            .orElseThrow(() -> conflict("DEPENDENCY_TEMPLATE_MISSING"));
        if (!"TEMPLATE".equals(plugin.getPluginType()) || !"ACTIVE".equals(plugin.getStatus())
            || !"PUBLISHED".equals(version.getStatus()) || version.getPublishedAt() == null) {
            throw conflict("DEPENDENCY_TEMPLATE_NOT_PUBLISHED");
        }
        if (specs.existsById(request.specId())
            || !specs.findByTemplateVersionIdAndActiveTrue(templateVersionId).isEmpty()) {
            throw conflict("DEPENDENCY_SPEC_IMMUTABLE");
        }
        for (var service : request.spec().services()) validateArtifact(service);

        Instant reviewedAt = Instant.now();
        String fingerprint = validator.fingerprint(request.spec());
        var entity = new ManagedDependencySpecEntity();
        entity.setId(request.specId());
        entity.setTemplateVersionId(templateVersionId);
        entity.setFingerprint(fingerprint);
        entity.setSpecJson(json(request.spec()));
        entity.setReviewedBy(PlatformSecurityContext.currentPrincipal().actorId());
        entity.setReviewedAt(reviewedAt);
        entity.setReviewReference(request.reviewReference());
        entity.setActive(true);
        specs.saveAndFlush(entity);
        audit.record("MANAGED_DEPENDENCY_SPEC_REVIEWED", "MARKETPLACE_TEMPLATE_VERSION", templateVersionId,
            Map.of("specId", entity.getId(), "fingerprint", fingerprint,
                "reviewReference", entity.getReviewReference()));
        return new PublicationSummary(entity.getId(), templateVersionId, fingerprint,
            entity.getReviewReference(), reviewedAt);
    }

    private void validateArtifact(ManagedDependencySpec.Service service) {
        var artifact = artifacts.findById(service.sourceArtifactId())
            .orElseThrow(() -> conflict("DEPENDENCY_ARTIFACT_MISSING"));
        String immutableImage = artifact.getImageRepository() == null || artifact.getImageDigest() == null
            ? null : artifact.getImageRepository() + "@" + artifact.getImageDigest();
        if (!"DOCKER_IMAGE".equals(artifact.getArtifactType()) || artifact.getPromotedAt() == null
            || !service.image().equals(immutableImage)
            || artifact.getGitCommitSha() == null || !service.sourceCommit().equals(artifact.getGitCommitSha())
            || artifact.getCapabilityManifestHash() == null || artifact.getCapabilityManifestHash().isBlank()) {
            throw conflict("DEPENDENCY_ARTIFACT_NOT_IMMUTABLE");
        }
    }

    private String json(ManagedDependencySpec spec) {
        try {
            return mapper.writeValueAsString(spec);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Dependency specification cannot be serialized.", error);
        }
    }

    private boolean identifier(String value) {
        return value != null && value.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,63}");
    }

    private ResponseStatusException conflict(String code) {
        return new ResponseStatusException(CONFLICT, code);
    }
}
