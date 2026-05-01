package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.config.PlatformProvisioningProperties;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentTargetProfileEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;

@Service
public class DeploymentTargetProfileService {

    private final PlatformProvisioningProperties provisioningProperties;
    private final DeploymentTargetProfileRepository targetProfileRepository;

    public DeploymentTargetProfileService(PlatformProvisioningProperties provisioningProperties,
                                          DeploymentTargetProfileRepository targetProfileRepository) {
        this.provisioningProperties = provisioningProperties;
        this.targetProfileRepository = targetProfileRepository;
    }

    public DeploymentTargetProfileEntity resolveDefaultRuntimeProfile() {
        DeploymentProviderType providerType = DeploymentProviderType.fromLegacyMode(provisioningProperties.mode());
        return targetProfileRepository
            .findFirstByProviderTypeAndActiveTrueAndDefaultForRuntimeTrueOrderByUpdatedAtDesc(providerType)
            .orElseGet(() -> legacyFallbackProfile(providerType));
    }

    public DeploymentTargetProfileEntity resolveForRelease(DeploymentReleaseEntity release) {
        if (release != null && StringUtils.hasText(release.getTargetProfileId())) {
            return targetProfileRepository.findById(release.getTargetProfileId())
                .orElseThrow(() -> new IllegalStateException(
                    "Deployment target profile not found: " + release.getTargetProfileId()
                ));
        }
        if (release != null && release.getProviderType() != null) {
            return targetProfileRepository
                .findFirstByProviderTypeAndActiveTrueAndDefaultForRuntimeTrueOrderByUpdatedAtDesc(release.getProviderType())
                .orElseGet(() -> legacyFallbackProfile(release.getProviderType()));
        }
        return resolveDefaultRuntimeProfile();
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
}
