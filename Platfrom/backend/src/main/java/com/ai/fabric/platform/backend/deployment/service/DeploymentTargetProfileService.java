package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.config.PlatformProvisioningProperties;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentTargetProfileEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentTargetProfileSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import com.ai.fabric.platform.backend.deployment.model.PatchDeploymentTargetProfileRequest;
import com.ai.fabric.platform.backend.deployment.model.UpdateDeploymentTargetProfilePlacementRequest;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class DeploymentTargetProfileService {

    private final PlatformProvisioningProperties provisioningProperties;
    private final DeploymentTargetProfileRepository targetProfileRepository;
    private final ObjectMapper objectMapper;
    private final PlatformAuditService platformAuditService;

    public DeploymentTargetProfileService(PlatformProvisioningProperties provisioningProperties,
                                          DeploymentTargetProfileRepository targetProfileRepository,
                                          ObjectMapper objectMapper,
                                          PlatformAuditService platformAuditService) {
        this.provisioningProperties = provisioningProperties;
        this.targetProfileRepository = targetProfileRepository;
        this.objectMapper = objectMapper;
        this.platformAuditService = platformAuditService;
    }

    public DeploymentTargetProfileEntity resolveDefaultRuntimeProfile() {
        DeploymentProviderType providerType = DeploymentProviderType.fromLegacyMode(provisioningProperties.mode());
        return targetProfileRepository
            .findFirstByActiveTrueAndDefaultForRuntimeTrueOrderByUpdatedAtDesc()
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

    @Transactional
    public DeploymentTargetProfileSummary patchProfile(String targetProfileId, PatchDeploymentTargetProfileRequest request) {
        DeploymentTargetProfileEntity profile = requireProfile(targetProfileId);
        if (request == null) {
            return toSummary(profile);
        }

        Instant now = Instant.now();
        if (request.active() != null) {
            profile.setActive(request.active());
        }
        if (request.defaultForRuntime() != null) {
            if (request.defaultForRuntime() && !profile.isActive()) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Deployment target profile must be active before it can be the runtime default: " + targetProfileId
                );
            }
            if (request.defaultForRuntime()) {
                unsetOtherRuntimeDefaults(profile, now);
            }
            profile.setDefaultForRuntime(request.defaultForRuntime());
        }
        if (request.defaultForRestartableServices() != null) {
            if (request.defaultForRestartableServices() && !profile.isActive()) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Deployment target profile must be active before it can be the restartable-services default: " + targetProfileId
                );
            }
            if (request.defaultForRestartableServices()) {
                unsetOtherRestartableDefaults(profile, now);
            }
            profile.setDefaultForRestartableServices(request.defaultForRestartableServices());
        }
        if (request.platformServicesAllowed() != null) {
            profile.setPlatformServicesAllowed(request.platformServicesAllowed());
        }
        profile.setUpdatedAt(now);
        return toSummary(targetProfileRepository.save(profile));
    }

    @Transactional
    public DeploymentTargetProfileSummary updatePlacement(
        String targetProfileId,
        UpdateDeploymentTargetProfilePlacementRequest request
    ) {
        DeploymentTargetProfileEntity profile = requireProfile(targetProfileId);
        if (profile.getProviderType() != DeploymentProviderType.COOLIFY) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Placement updates are supported only for Coolify target profiles: " + targetProfileId
            );
        }
        if (profile.isActive()) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Deactivate the target profile before changing its Coolify placement: " + targetProfileId
            );
        }
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Coolify placement is required.");
        }

        String serverUuid = requirePlacementValue(request.serverUuid(), "serverUuid");
        String destinationUuid = requirePlacementValue(request.destinationUuid(), "destinationUuid");
        JsonNode currentConfig = readJson(profile.getProviderConfigJson());
        if (!currentConfig.isObject()) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Coolify target profile provider configuration must be a JSON object: " + targetProfileId
            );
        }
        String currentServerUuid = text(currentConfig, "serverUuid");
        String currentDestinationUuid = text(currentConfig, "destinationUuid");
        requireExpectedPlacement(
            request.expectedCurrentServerUuid(),
            currentServerUuid,
            "serverUuid",
            targetProfileId
        );
        requireExpectedPlacement(
            request.expectedCurrentDestinationUuid(),
            currentDestinationUuid,
            "destinationUuid",
            targetProfileId
        );

        ObjectNode updatedConfig = ((ObjectNode) currentConfig).deepCopy();
        updatedConfig.put("serverUuid", serverUuid);
        updatedConfig.put("destinationUuid", destinationUuid);
        profile.setProviderConfigJson(writeJson(updatedConfig));
        profile.setUpdatedAt(Instant.now());
        DeploymentTargetProfileEntity saved = targetProfileRepository.save(profile);
        platformAuditService.record(
            "DEPLOYMENT_TARGET_PROFILE_PLACEMENT_UPDATED",
            "DEPLOYMENT_TARGET_PROFILE",
            targetProfileId,
            Map.of(
                "previousServerUuid", valueOrEmpty(currentServerUuid),
                "previousDestinationUuid", valueOrEmpty(currentDestinationUuid),
                "serverUuid", serverUuid,
                "destinationUuid", destinationUuid
            )
        );
        return toSummary(saved);
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

    private void unsetOtherRuntimeDefaults(DeploymentTargetProfileEntity selected, Instant now) {
        for (DeploymentTargetProfileEntity profile : targetProfileRepository.findAll()) {
            if (!selected.getId().equals(profile.getId()) && profile.isDefaultForRuntime()) {
                profile.setDefaultForRuntime(false);
                profile.setUpdatedAt(now);
                targetProfileRepository.save(profile);
            }
        }
    }

    private void unsetOtherRestartableDefaults(DeploymentTargetProfileEntity selected, Instant now) {
        for (DeploymentTargetProfileEntity profile : targetProfileRepository.findAll()) {
            if (!selected.getId().equals(profile.getId()) && profile.isDefaultForRestartableServices()) {
                profile.setDefaultForRestartableServices(false);
                profile.setUpdatedAt(now);
                targetProfileRepository.save(profile);
            }
        }
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

    private String writeJson(JsonNode json) {
        try {
            return objectMapper.writeValueAsString(json);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to write deployment target profile JSON.", ex);
        }
    }

    private String requirePlacementValue(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Coolify placement " + field + " is required.");
        }
        return value.trim();
    }

    private void requireExpectedPlacement(String expected,
                                          String actual,
                                          String field,
                                          String targetProfileId) {
        if (StringUtils.hasText(expected) && !expected.trim().equals(actual)) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Coolify placement " + field + " changed before this update for target profile " + targetProfileId + "."
            );
        }
    }

    private String text(JsonNode node, String field) {
        String value = node == null ? null : node.path(field).asText(null);
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }
}
