package com.ai.fabric.platform.backend.customerselfservice.service;

import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.*;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentSourceArtifactEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentTargetProfileEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentSourceArtifactRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginVersionEntity;
import com.ai.fabric.platform.backend.marketplace.repository.MarketplacePluginRepository;
import com.ai.fabric.platform.backend.marketplace.repository.MarketplacePluginVersionRepository;
import com.ai.fabric.platform.backend.marketplace.service.MarketplaceManifestService;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.*;
import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.Code.*;

@Service
public class CustomerReviewedCompositionService {
    private final CustomerOfferCodec codec;
    private final Validator validator;
    private final MarketplacePluginRepository plugins;
    private final MarketplacePluginVersionRepository versions;
    private final MarketplaceManifestService manifests;
    private final DeploymentSourceArtifactRepository artifacts;
    private final DeploymentTargetProfileRepository profiles;

    public CustomerReviewedCompositionService(CustomerOfferCodec codec, Validator validator,
        MarketplacePluginRepository plugins, MarketplacePluginVersionRepository versions,
        MarketplaceManifestService manifests, DeploymentSourceArtifactRepository artifacts,
        DeploymentTargetProfileRepository profiles) {
        this.codec = codec;
        this.validator = validator;
        this.plugins = plugins;
        this.versions = versions;
        this.manifests = manifests;
        this.artifacts = artifacts;
        this.profiles = profiles;
    }

    public void validate(Definition definition) {
        if (definition == null || !validator.validate(definition).isEmpty()
            || !"1".equals(definition.schemaVersion()) || definition.artifacts().isEmpty()
            || definition.targets().isEmpty() || definition.operations().isEmpty()
            || !definition.admissionPrerequisites().containsAll(Set.of(Prerequisite.APPROVED_POLICY,
                Prerequisite.IMMUTABLE_ARTIFACTS, Prerequisite.STORAGE, Prerequisite.IDENTITY,
                Prerequisite.CAPACITY, Prerequisite.NOTIFICATIONS, Prerequisite.OPERATING_POLICY))) {
            throw invalid();
        }
        unique(definition.targets(), TargetPermission::targetProfileId);
        unique(definition.plugins(), PluginPin::pluginId);
        unique(definition.artifacts(), ArtifactPin::artifactId);
        unique(definition.settings(), SettingRule::setting);
        unique(definition.bindingSlots(), BindingSlot::id);
        if (!definition.targets().stream().map(TargetPermission::intent).collect(java.util.stream.Collectors.toSet())
            .containsAll(Set.of(TargetIntent.CUSTOMER_STAGING, TargetIntent.CUSTOMER_PRODUCTION))) throw invalid();

        MarketplacePluginVersionEntity templateVersion = requirePlugin(definition.template());
        var template = plugins.findById(definition.template().pluginId()).orElseThrow(() -> conflict(APPROVED_POLICY_REQUIRED));
        if (!"TEMPLATE".equals(template.getPluginType())) throw invalid();
        MarketplaceManifestService.ParsedMarketplaceManifest parsed;
        try { parsed = manifests.parseAndValidate(template, templateVersion); }
        catch (RuntimeException ex) { throw conflict(APPROVED_POLICY_REQUIRED); }
        if (!definition.templateId().equals(parsed.manifest().path("contributions").path("template")
            .path("templateId").asText())) throw conflict(REVIEWED_COMPOSITION_CHANGED);
        Set<String> pinnedRefs = new HashSet<>();
        for (PluginPin pin : definition.plugins()) {
            if (pin.pluginId().equals(definition.template().pluginId())) throw invalid();
            var version = requirePlugin(pin);
            pinnedRefs.add(pin.pluginId() + "@" + version.getVersion());
        }
        if (!pinnedRefs.equals(new HashSet<>(parsed.contributions().templateRequiredPluginRefs()))) {
            throw conflict(REVIEWED_COMPOSITION_CHANGED);
        }
        for (ArtifactPin pin : definition.artifacts()) {
            DeploymentSourceArtifactEntity artifact = artifacts.findById(pin.artifactId())
                .orElseThrow(() -> conflict(ARTIFACT_REQUIRED));
            if (!"DOCKER_IMAGE".equals(artifact.getArtifactType()) || artifact.getPromotedAt() == null
                || artifact.getImageDigest() == null || !artifact.getImageDigest().matches("sha256:[a-f0-9]{64}")
                || artifact.getImageRepository() == null || artifact.getImageRepository().isBlank()
                || artifact.getCapabilityManifestHash() == null || artifact.getCapabilityManifestHash().isBlank()) {
                throw conflict(ARTIFACT_REQUIRED);
            }
            if (!pin.fingerprint().equals(artifactFingerprint(artifact))) throw conflict(REVIEWED_COMPOSITION_CHANGED);
        }
        for (TargetPermission target : definition.targets()) {
            DeploymentTargetProfileEntity profile = profiles.findById(target.targetProfileId())
                .orElseThrow(() -> conflict(UNENTITLED_TARGET));
            requireProfile(profile, target);
        }
        for (SettingRule rule : definition.settings()) {
            if (rule.maxLength() > 255 || (rule.type() == ValueType.CHOICE && rule.allowedValues().isEmpty())
                || (rule.type() == ValueType.TEXT && !rule.allowedValues().isEmpty())
                || (isProfileSetting(rule.setting()) && rule.type() != ValueType.CHOICE)) throw invalid();
        }
        for (BindingSlot slot : definition.bindingSlots()) {
            if (slot.supportedProfileIds().isEmpty() || slot.supportedProfileIds().stream().anyMatch(String::isBlank)) throw invalid();
        }
        if (!definition.documentAudiences().isEmpty() && definition.bindingSlots().stream()
            .noneMatch(slot -> slot.kind() == BindingKind.CUSTOMER_S3)) throw invalid();
    }

    private MarketplacePluginVersionEntity requirePlugin(PluginPin pin) {
        var plugin = plugins.findById(pin.pluginId()).orElseThrow(() -> conflict(APPROVED_POLICY_REQUIRED));
        var version = versions.findById(pin.versionId()).orElseThrow(() -> conflict(APPROVED_POLICY_REQUIRED));
        if (!"ACTIVE".equals(plugin.getStatus()) || !"PUBLISHED".equals(version.getStatus())
            || !pin.pluginId().equals(version.getPluginId())
            || !pin.manifestSha256().equals(codec.hash(codec.tree(version.getManifestJson())))) {
            throw conflict(REVIEWED_COMPOSITION_CHANGED);
        }
        return version;
    }

    public void requireProfile(DeploymentTargetProfileEntity profile, TargetPermission permission) {
        var defaults = codec.tree(profile.getResourceDefaultsJson());
        if (!profile.isActive() || profile.getProviderType() != DeploymentProviderType.COOLIFY
            || !"IMAGE_SOURCE".equals(profile.getSourceStrategy())
            || (defaults.has("sourceStrategy") && !"IMAGE_SOURCE".equals(defaults.path("sourceStrategy").asText()))
            || !permission.profileFingerprint().equals(profileFingerprint(profile))) {
            throw conflict(REVIEWED_COMPOSITION_CHANGED);
        }
    }

    public String profileFingerprint(DeploymentTargetProfileEntity profile) {
        return codec.hash(Map.of("id", profile.getId(), "providerType", profile.getProviderType(),
            "sourceStrategy", profile.getSourceStrategy(), "credentialRefId", nonNull(profile.getCredentialRefId()),
            "providerConfig", codec.tree(profile.getProviderConfigJson()),
            "networkPolicy", codec.tree(profile.getNetworkPolicyJson()),
            "resourceDefaults", codec.tree(profile.getResourceDefaultsJson())));
    }

    public String artifactFingerprint(DeploymentSourceArtifactEntity artifact) {
        return codec.hash(Map.of("id", artifact.getId(), "serviceName", artifact.getServiceName(),
            "artifactType", artifact.getArtifactType(), "imageRepository", nonNull(artifact.getImageRepository()),
            "imageDigest", nonNull(artifact.getImageDigest()), "gitCommitSha", nonNull(artifact.getGitCommitSha()),
            "capabilityManifest", codec.tree(artifact.getCapabilityManifestJson()),
            "capabilityManifestHash", nonNull(artifact.getCapabilityManifestHash())));
    }

    private boolean isProfileSetting(Setting setting) {
        return switch (setting) {
            case LOGO_ASSET_ID, THEME_PROFILE_ID, INITIAL_VIEW, INTERACTION_PROFILE_ID, AUTH_PROFILE_ID,
                DOCUMENT_AUDIENCE -> true;
            default -> false;
        };
    }

    private <T, K> void unique(List<T> items, Function<T, K> key) {
        Set<K> keys = new HashSet<>();
        if (items.stream().anyMatch(item -> !keys.add(key.apply(item)))) throw invalid();
    }
    private String nonNull(String value) { return value == null ? "" : value; }
}
