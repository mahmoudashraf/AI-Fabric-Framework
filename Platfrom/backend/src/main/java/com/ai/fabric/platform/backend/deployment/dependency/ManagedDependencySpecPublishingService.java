package com.ai.fabric.platform.backend.deployment.dependency;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerOfferEntitlementService;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentSourceArtifactRepository;
import com.ai.fabric.platform.backend.deployment.model.DeploymentSourceArtifactRole;
import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginVersionEntity;
import com.ai.fabric.platform.backend.marketplace.repository.MarketplacePluginRepository;
import com.ai.fabric.platform.backend.marketplace.repository.MarketplacePluginVersionRepository;
import com.ai.fabric.platform.backend.security.PlatformSecurityContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

/** Publishes one immutable, reviewed dependency topology for an exact template version. */
@Service
public class ManagedDependencySpecPublishingService {
    private static final Pattern MANAGED_ENV_PLACEHOLDER =
        Pattern.compile("^\\$\\{(MANAGED_[A-Z0-9_]{1,119})}$");
    private final ManagedDependencySpecRepository specs;
    private final ManagedDependencySpecValidator validator;
    private final MarketplacePluginVersionRepository versions;
    private final MarketplacePluginRepository plugins;
    private final DeploymentSourceArtifactRepository artifacts;
    private final CustomerOfferEntitlementService staff;
    private final PlatformAuditService audit;
    private final ObjectMapper mapper;
    private final ManagedDependencyArtifactPolicy ownership;

    public ManagedDependencySpecPublishingService(ManagedDependencySpecRepository specs,
            ManagedDependencySpecValidator validator, MarketplacePluginVersionRepository versions,
            MarketplacePluginRepository plugins, DeploymentSourceArtifactRepository artifacts,
            CustomerOfferEntitlementService staff, PlatformAuditService audit, ObjectMapper mapper,
            ManagedDependencyArtifactPolicy ownership) {
        this.specs = specs;
        this.validator = validator;
        this.versions = versions;
        this.plugins = plugins;
        this.artifacts = artifacts;
        this.staff = staff;
        this.audit = audit;
        this.mapper = mapper;
        this.ownership = ownership;
    }

    public record PublicationRequest(String specId, ManagedDependencySpec spec, String reviewReference) { }
    public record PublicationSummary(String specId, String templateVersionId, String fingerprint,
                                     String reviewReference, Instant reviewedAt) { }
    public record PublicationDetails(PublicationSummary publication, ManagedDependencySpec spec) { }

    @Transactional(readOnly = true)
    public PublicationDetails current(String templateVersionId) {
        staff.requireStaffAdmin();
        if (!identifier(templateVersionId)) {
            throw conflict("DEPENDENCY_SPEC_PUBLICATION_INVALID");
        }
        var active = specs.findByTemplateVersionIdAndActiveTrue(templateVersionId);
        if (active.isEmpty()) {
            throw new ResponseStatusException(NOT_FOUND, "DEPENDENCY_SPEC_MISSING");
        }
        if (active.size() != 1) {
            throw conflict("DEPENDENCY_SPEC_INTEGRITY_INVALID");
        }
        var entity = active.getFirst();
        try {
            var spec = mapper.readValue(entity.getSpecJson(), ManagedDependencySpec.class);
            validator.validate(spec);
            if (!entity.getFingerprint().equals(validator.fingerprint(spec))) {
                throw conflict("DEPENDENCY_SPEC_DRIFT");
            }
            ownership.requireAllowed(spec);
            return new PublicationDetails(summary(entity), spec);
        } catch (JsonProcessingException error) {
            throw conflict("DEPENDENCY_SPEC_INVALID");
        }
    }

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
        validateManagedRuntimeBindings(version, request.spec());
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
        return summary(entity);
    }

    private void validateArtifact(ManagedDependencySpec.Service service) {
        var artifact = artifacts.findById(service.sourceArtifactId())
            .orElseThrow(() -> conflict("DEPENDENCY_ARTIFACT_MISSING"));
        String immutableImage = artifact.getImageRepository() == null || artifact.getImageDigest() == null
            ? null : artifact.getImageRepository() + "@" + artifact.getImageDigest();
        if (!"DOCKER_IMAGE".equals(artifact.getArtifactType())
            || !DeploymentSourceArtifactRole.MANAGED_DEPENDENCY.name().equals(artifact.getArtifactRole())
            || artifact.getPromotedAt() == null
            || !service.image().equals(immutableImage)
            || artifact.getGitCommitSha() == null || !service.sourceCommit().equals(artifact.getGitCommitSha())
            || artifact.getPromotionChannel() == null || artifact.getPromotionChannel().isBlank()) {
            throw conflict("DEPENDENCY_ARTIFACT_NOT_IMMUTABLE");
        }
        ownership.requireAllowed(service, artifact);
    }

    private void validateManagedRuntimeBindings(MarketplacePluginVersionEntity templateVersion,
                                                ManagedDependencySpec spec) {
        Set<String> required = managedEnvironmentReferences(templateVersion);
        if (required.isEmpty()) {
            return;
        }
        Set<String> provided = new HashSet<>();
        for (var binding : spec.runtimeEnvironment()) {
            if (binding.target() == ManagedDependencySpec.RuntimeTarget.CONNECTOR) {
                provided.add(binding.name());
            }
        }
        if (!provided.containsAll(required)) {
            Set<String> missing = new LinkedHashSet<>(required);
            missing.removeAll(provided);
            throw conflict("DEPENDENCY_RUNTIME_BINDING_MISSING:" + String.join(",", missing));
        }
    }

    private Set<String> managedEnvironmentReferences(MarketplacePluginVersionEntity templateVersion) {
        try {
            JsonNode template = mapper.readTree(templateVersion.getManifestJson())
                .path("contributions").path("template");
            JsonNode defaults = template.path("requiredPluginInstallDefaults");
            Set<String> required = new LinkedHashSet<>();
            if (!defaults.isObject()) {
                return required;
            }
            defaults.fields().forEachRemaining(pluginDefaults -> {
                JsonNode config = pluginDefaults.getValue().path("config");
                if (config.isObject()) {
                    config.fields().forEachRemaining(entry -> addManagedPlaceholder(required, entry.getValue()));
                }
                JsonNode secretRefs = pluginDefaults.getValue().path("secretRefs");
                if (secretRefs.isObject()) {
                    secretRefs.fields().forEachRemaining(entry -> {
                        String name = entry.getValue().asText("").trim();
                        if (name.startsWith("MANAGED_")) {
                            required.add(name);
                        }
                    });
                }
            });
            return required;
        } catch (JsonProcessingException error) {
            throw conflict("DEPENDENCY_TEMPLATE_MANIFEST_INVALID");
        }
    }

    private void addManagedPlaceholder(Set<String> required, JsonNode value) {
        if (!value.isTextual()) {
            return;
        }
        Matcher matcher = MANAGED_ENV_PLACEHOLDER.matcher(value.asText("").trim());
        if (matcher.matches()) {
            required.add(matcher.group(1));
        }
    }

    private String json(ManagedDependencySpec spec) {
        try {
            return mapper.writeValueAsString(spec);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Dependency specification cannot be serialized.", error);
        }
    }

    private PublicationSummary summary(ManagedDependencySpecEntity entity) {
        return new PublicationSummary(entity.getId(), entity.getTemplateVersionId(), entity.getFingerprint(),
            entity.getReviewReference(), entity.getReviewedAt());
    }

    private boolean identifier(String value) {
        return value != null && value.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,63}");
    }

    private ResponseStatusException conflict(String code) {
        return new ResponseStatusException(CONFLICT, code);
    }
}
