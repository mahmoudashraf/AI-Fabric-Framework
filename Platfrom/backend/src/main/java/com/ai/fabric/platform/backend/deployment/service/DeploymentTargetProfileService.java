package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.config.PlatformProvisioningProperties;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentTargetProfileEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentTargetProfileSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@Service
public class DeploymentTargetProfileService {

    private final PlatformProvisioningProperties provisioningProperties;
    private final DeploymentTargetProfileRepository targetProfileRepository;
    private final ObjectMapper objectMapper;

    public DeploymentTargetProfileService(PlatformProvisioningProperties provisioningProperties,
                                          DeploymentTargetProfileRepository targetProfileRepository,
                                          ObjectMapper objectMapper) {
        this.provisioningProperties = provisioningProperties;
        this.targetProfileRepository = targetProfileRepository;
        this.objectMapper = objectMapper;
    }

    public DeploymentTargetProfileEntity resolveDefaultRuntimeProfile() {
        DeploymentProviderType providerType = DeploymentProviderType.fromLegacyMode(provisioningProperties.mode());
        return targetProfileRepository
            .findFirstByProviderTypeAndActiveTrueAndDefaultForRuntimeTrueOrderByUpdatedAtDesc(providerType)
            .orElseGet(() -> legacyFallbackProfile(providerType));
    }

    public DeploymentTargetProfileEntity resolveForRelease(DeploymentReleaseEntity release) {
        if (release != null && StringUtils.hasText(release.getTargetProfileId())) {
            return requireActiveProfile(release.getTargetProfileId());
        }
        if (release != null && release.getProviderType() != null) {
            return targetProfileRepository
                .findFirstByProviderTypeAndActiveTrueAndDefaultForRuntimeTrueOrderByUpdatedAtDesc(release.getProviderType())
                .orElseGet(() -> legacyFallbackProfile(release.getProviderType()));
        }
        return resolveDefaultRuntimeProfile();
    }

    public DeploymentTargetProfileEntity resolveForRequest(String targetProfileId) {
        if (StringUtils.hasText(targetProfileId)) {
            return requireActiveProfile(targetProfileId);
        }
        return resolveDefaultRuntimeProfile();
    }

    public DeploymentTargetProfileEntity requireProfile(String targetProfileId) {
        if (!StringUtils.hasText(targetProfileId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Deployment target profile id is required.");
        }
        return targetProfileRepository.findById(targetProfileId.trim())
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Deployment target profile not found: " + targetProfileId
            ));
    }

    public DeploymentTargetProfileEntity requireActiveProfile(String targetProfileId) {
        DeploymentTargetProfileEntity profile = requireProfile(targetProfileId);
        if (!profile.isActive()) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Deployment target profile is not active: " + targetProfileId
            );
        }
        return profile;
    }

    public List<DeploymentTargetProfileSummary> listProfiles(DeploymentProviderType providerType) {
        List<DeploymentTargetProfileEntity> profiles = providerType == null
            ? targetProfileRepository.findAll()
            : targetProfileRepository.findByProviderTypeOrderByEnvironmentNameAscUpdatedAtDesc(providerType);
        return profiles.stream()
            .sorted((left, right) -> left.getId().compareToIgnoreCase(right.getId()))
            .map(this::toSummary)
            .toList();
    }

    public void applyProfileToRelease(DeploymentReleaseEntity release,
                                      DeploymentTargetProfileEntity targetProfile) {
        if (release == null || targetProfile == null) {
            return;
        }
        release.setTargetProfileId(targetProfile.getId());
        release.setProviderType(targetProfile.getProviderType());
        release.setProvisioningTarget(targetProfile.getProviderType().legacyTarget());
    }

    public DeploymentTargetProfileSummary toSummary(DeploymentTargetProfileEntity profile) {
        return new DeploymentTargetProfileSummary(
            profile.getId(),
            profile.getName(),
            profile.getProviderType(),
            profile.getEnvironmentName(),
            profile.getRegion(),
            profile.isActive(),
            profile.isDefaultForRuntime(),
            profile.isDefaultForRestartableServices(),
            profile.isPlatformServicesAllowed(),
            profile.getSourceStrategy(),
            profile.getCredentialRefId(),
            readJson(profile.getProviderConfigJson()),
            readJson(profile.getNetworkPolicyJson()),
            readJson(profile.getResourceDefaultsJson()),
            profile.getCreatedAt(),
            profile.getUpdatedAt()
        );
    }

    private DeploymentTargetProfileEntity legacyFallbackProfile(DeploymentProviderType providerType) {
        Instant now = Instant.now();
        DeploymentTargetProfileEntity profile = new DeploymentTargetProfileEntity();
        profile.setId("dtp-legacy-" + providerType.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-'));
        profile.setName("Legacy " + providerType.name());
        profile.setProviderType(providerType);
        profile.setEnvironmentName(provisioningProperties.environmentName());
        profile.setRegion(null);
        profile.setActive(true);
        profile.setDefaultForRuntime(true);
        profile.setDefaultForRestartableServices(true);
        profile.setPlatformServicesAllowed(true);
        profile.setSourceStrategy("GIT_SOURCE");
        profile.setCredentialRefId(null);
        profile.setProviderConfigJson("{\"legacyMode\":\"" + providerType.name() + "\"}");
        profile.setNetworkPolicyJson("{}");
        profile.setResourceDefaultsJson("{}");
        profile.setCreatedAt(now);
        profile.setUpdatedAt(now);
        return profile;
    }

    private JsonNode readJson(String json) {
        try {
            return objectMapper.readTree(json == null || json.isBlank() ? "{}" : json);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to read deployment target profile JSON.", ex);
        }
    }
}
