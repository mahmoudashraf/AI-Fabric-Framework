package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentTargetProfileEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentProviderResourceHandleRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class DeploymentTargetContextService {
    private static final Set<String> ADMINISTRABLE_STATES = Set.of("VERIFYING", "APPLIED_VERIFIED", "APPLIED_VERIFICATION_FAILED");
    private static final Set<String> UNAVAILABLE_RESOURCES = Set.of("DELETED", "RETIRED", "DELETE_REQUESTED", "ORPHANED");
    private final DeploymentRepository deployments;
    private final DeploymentReleaseRepository releases;
    private final DeploymentVersionRepository versions;
    private final DeploymentTargetProfileRepository profiles;
    private final DeploymentProviderResourceHandleRepository handles;
    private final DeploymentAccessService access;
    private final DeploymentReleaseEndpointService endpoints;
    private final ObjectMapper objectMapper;
    private final DeploymentDocumentBindingResolver documentBindings;

    public DeploymentTargetContextService(DeploymentRepository deployments,
                                          DeploymentReleaseRepository releases,
                                          DeploymentVersionRepository versions,
                                          DeploymentTargetProfileRepository profiles,
                                          DeploymentProviderResourceHandleRepository handles,
                                          DeploymentAccessService access,
                                          DeploymentReleaseEndpointService endpoints,
                                          ObjectMapper objectMapper, DeploymentDocumentBindingResolver documentBindings) {
        this.deployments = deployments;
        this.releases = releases;
        this.versions = versions;
        this.profiles = profiles;
        this.handles = handles;
        this.access = access;
        this.endpoints = endpoints;
        this.objectMapper = objectMapper;
        this.documentBindings = documentBindings;
    }

    @Transactional(readOnly = true)
    public Context resolve(String deploymentId, String targetProfileId, String releaseId, boolean mutate) {
        DeploymentEntity deployment = deployments.findById(required(deploymentId, "deploymentId"))
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Deployment not found."));
        if (mutate) access.requireDeploymentOperatorAccess(deployment);
        else access.requireDeploymentAccess(deployment);
        if (deployment.getArchivedAt() != null || "ARCHIVED".equals(deployment.getStatus())) {
            throw conflict("Archived deployment cannot be administered.");
        }
        DeploymentReleaseEntity release = releases.findById(required(releaseId, "releaseId"))
            .filter(value -> deployment.getId().equals(value.getDeploymentId()))
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Deployment release not found."));
        String target = required(targetProfileId, "targetProfileId");
        if (!target.equals(release.getTargetProfileId())) throw conflict("Release does not belong to the requested target.");
        DeploymentTargetProfileEntity profile = profiles.findById(target)
            .orElseThrow(() -> conflict("Release target profile is unavailable."));
        if (!profile.isActive() || profile.getProviderType() != release.getProviderType()) {
            throw conflict("Release target is inactive or its provider evidence is inconsistent.");
        }
        if (!ADMINISTRABLE_STATES.contains(release.getStatus()) || !"ACTIVE".equals(release.getProvisioningStatus())) {
            throw conflict("Release runtime is not provisioned for administration.");
        }
        DeploymentVersionEntity version = versions.findById(release.getDeploymentVersionId())
            .filter(value -> deployment.getId().equals(value.getDeploymentId()))
            .orElseThrow(() -> conflict("Release candidate version is unavailable."));
        documentBindings.requireUnchanged(deployment, version, release);
        String handleId = requiredEvidence(release.getProviderResourceHandleId());
        var handle = handles.findById(handleId).orElseThrow(() -> conflict("Release runtime resource evidence is unavailable."));
        if (!deployment.getId().equals(handle.getDeploymentId()) || !target.equals(handle.getTargetProfileId())
            || !release.getId().equals(handle.getReleaseId()) || profile.getProviderType() != handle.getProviderType()
            || UNAVAILABLE_RESOURCES.contains(handle.getStatus())) {
            throw conflict("Release runtime resource ownership or generation is inconsistent.");
        }
        if (profile.getProviderType() == DeploymentProviderType.COOLIFY) {
            try {
                String serverId = objectMapper.readTree(profile.getProviderConfigJson()).path("serverUuid").asText();
                String applicationId = endpoints.details(release).path("runtimeApplicationUuid").asText();
                if (!StringUtils.hasText(serverId) || !serverId.equals(handle.getProviderServerUuid())
                    || !StringUtils.hasText(applicationId) || !applicationId.equals(handle.getProviderResourceUuid())
                    || !"APPLICATION".equals(handle.getResourceKind())) {
                    throw new IllegalStateException();
                }
            } catch (Exception error) {
                throw conflict("Release runtime server placement or application evidence is inconsistent.");
            }
        }
        return new Context(deployment, profile, release, version, endpoints.runtimeBaseUrl(release), endpoints.connectorBaseUrl(release));
    }

    private String required(String value, String name) {
        if (!StringUtils.hasText(value) || value.trim().length() > 64) {
            throw new ResponseStatusException(BAD_REQUEST, name + " is required and must not exceed 64 characters.");
        }
        return value.trim();
    }

    private String requiredEvidence(String value) {
        if (!StringUtils.hasText(value)) throw conflict("Release runtime resource evidence is unavailable.");
        return value;
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(CONFLICT, message);
    }

    public record Context(DeploymentEntity deployment, DeploymentTargetProfileEntity target,
                          DeploymentReleaseEntity release, DeploymentVersionEntity version,
                          String runtimeBaseUrl, String connectorBaseUrl) {
    }
}
