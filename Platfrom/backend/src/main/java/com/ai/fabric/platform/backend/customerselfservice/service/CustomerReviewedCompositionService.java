package com.ai.fabric.platform.backend.customerselfservice.service;

import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.*;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentSourceArtifactEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentTargetProfileEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentSourceArtifactRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.ai.fabric.platform.backend.deployment.service.DeploymentTargetProfileIdentity;
import com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencyArtifactPolicy;
import com.ai.fabric.platform.backend.externalintegration.ExternalIntegrationPolicy;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginVersionEntity;
import com.ai.fabric.platform.backend.marketplace.repository.MarketplacePluginRepository;
import com.ai.fabric.platform.backend.marketplace.repository.MarketplacePluginVersionRepository;
import com.ai.fabric.platform.backend.marketplace.service.MarketplaceManifestService;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.*;
import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.Code.*;

@Service
public class CustomerReviewedCompositionService {
    @org.springframework.beans.factory.annotation.Autowired
    private com.ai.fabric.platform.backend.deployment.operations.DeploymentOperationsProperties operationsProperties;
    private final CustomerOfferCodec codec;
    private final ObjectMapper mapper;
    private final Validator validator;
    private final MarketplacePluginRepository plugins;
    private final MarketplacePluginVersionRepository versions;
    private final MarketplaceManifestService manifests;
    private final DeploymentSourceArtifactRepository artifacts;
    private final DeploymentTargetProfileRepository profiles;
    private final CustomerWorkspaceProfileValidator workspaceProfiles;
    private final ManagedDependencyArtifactPolicy dependencyOwnership;
    private final ExternalIntegrationPolicy externalIntegrations;

    public CustomerReviewedCompositionService(CustomerOfferCodec codec, Validator validator,
        MarketplacePluginRepository plugins, MarketplacePluginVersionRepository versions,
        MarketplaceManifestService manifests, DeploymentSourceArtifactRepository artifacts,
        DeploymentTargetProfileRepository profiles, CustomerWorkspaceProfileValidator workspaceProfiles, ObjectMapper mapper,
        ManagedDependencyArtifactPolicy dependencyOwnership, ExternalIntegrationPolicy externalIntegrations) {
        this.codec = codec;
        this.mapper = mapper;
        this.validator = validator;
        this.plugins = plugins;
        this.versions = versions;
        this.manifests = manifests;
        this.artifacts = artifacts;
        this.profiles = profiles;
        this.workspaceProfiles = workspaceProfiles;
        this.dependencyOwnership = dependencyOwnership;
        this.externalIntegrations = externalIntegrations;
    }

    public void validate(Definition definition) {
        if (definition == null || !validator.validate(definition).isEmpty()
            || !"1".equals(definition.schemaVersion()) || definition.artifacts().isEmpty()
            || definition.targets().isEmpty() || definition.operations().isEmpty()
            || !definition.admissionPrerequisites().containsAll(operationsProperties == null
                ? Set.of(Prerequisite.APPROVED_POLICY, Prerequisite.IMMUTABLE_ARTIFACTS, Prerequisite.STORAGE,
                    Prerequisite.IDENTITY, Prerequisite.CAPACITY, Prerequisite.NOTIFICATIONS, Prerequisite.OPERATING_POLICY)
                : Set.of(Prerequisite.APPROVED_POLICY, Prerequisite.IMMUTABLE_ARTIFACTS))) {
            throw invalid();
        }
        unique(definition.targets(), TargetPermission::targetProfileId);
        unique(definition.plugins(), PluginPin::pluginId);
        unique(definition.artifacts(), ArtifactPin::artifactId);
        unique(definition.settings(), SettingRule::setting);
        unique(definition.bindingSlots(), BindingSlot::id);
        workspaceProfiles.validate(definition);
        try { externalIntegrations.validateDefinition(definition); }
        catch (RuntimeException error) { throw conflict(APPROVED_POLICY_REQUIRED); }
        requireTopologyWithinLimits(definition.topology(), definition.limits());
        Set<TargetIntent> intents = definition.targets().stream().map(TargetPermission::intent)
            .collect(java.util.stream.Collectors.toSet());
        if (!intents.contains(TargetIntent.CUSTOMER_STAGING)
            || ((definition.operations().contains(Operation.PROMOTION_PLAN)
                || definition.operations().contains(Operation.PROMOTION_APPLY))
                && !intents.contains(TargetIntent.CUSTOMER_PRODUCTION))) throw invalid();

        MarketplacePluginVersionEntity templateVersion = requirePlugin(definition.template());
        var template = plugins.findById(definition.template().pluginId()).orElseThrow(() -> conflict(APPROVED_POLICY_REQUIRED));
        if (!"TEMPLATE".equals(template.getPluginType())) throw invalid();
        MarketplaceManifestService.ParsedMarketplaceManifest parsed;
        try { parsed = manifests.parseAndValidate(template, templateVersion); }
        catch (RuntimeException ex) { throw conflict(APPROVED_POLICY_REQUIRED); }
        if (!definition.templateId().equals(parsed.manifest().path("contributions").path("template")
            .path("templateId").asText())) throw conflict(REVIEWED_COMPOSITION_CHANGED);
        Set<String> pinnedRefs = new HashSet<>();
        Map<String, MarketplaceManifestService.ParsedMarketplaceManifest> pinnedManifests = new HashMap<>();
        for (PluginPin pin : definition.plugins()) {
            if (pin.pluginId().equals(definition.template().pluginId())) throw invalid();
            var version = requirePlugin(pin);
            pinnedRefs.add(pin.pluginId() + "@" + version.getVersion());
            var pinnedPlugin = plugins.findById(pin.pluginId()).orElseThrow(() -> conflict(APPROVED_POLICY_REQUIRED));
            try {
                pinnedManifests.put(pin.pluginId(), manifests.parseAndValidate(pinnedPlugin, version));
            } catch (RuntimeException ex) {
                throw conflict(APPROVED_POLICY_REQUIRED);
            }
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
            if ("MANAGED_DEPENDENCY".equals(artifact.getArtifactRole())) {
                try {
                    dependencyOwnership.requireCustomerArtifact(artifact);
                } catch (RuntimeException error) {
                    throw conflict(APPROVED_POLICY_REQUIRED);
                }
            }
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
            if (rule.setting() == Setting.AI_STATE_STORAGE_MODE && rule.allowedValues().stream().anyMatch(value -> {
                try { com.ai.fabric.platform.backend.deployment.model.AiStateStorageMode.valueOf(value); return false; }
                catch (IllegalArgumentException error) { return true; }
            })) throw invalid();
        }
        var storageRule = definition.settings().stream()
            .filter(rule -> rule.setting() == Setting.AI_STATE_STORAGE_MODE).findFirst();
        if (storageRule.isPresent()) {
            if (!storageRule.get().allowedValues().equals(definition.aiStateStorageTopologies().keySet().stream()
                .map(Enum::name).collect(java.util.stream.Collectors.toSet()))) throw invalid();
            for (ReviewedTopology storageTopology : definition.aiStateStorageTopologies().values()) {
                if (!validator.validate(storageTopology).isEmpty()) throw invalid();
                requireTopologyWithinLimits(storageTopology, definition.limits());
                if (!withinTopology(storageTopology, definition.topology())) throw invalid();
            }
            if (storageRule.get().allowedValues().contains("CUSTOMER_POSTGRES")
                && definition.bindingSlots().stream().filter(slot -> slot.kind() == BindingKind.CUSTOMER_AI_STATE
                    && slot.pluginId() == null && slot.installConfigField() == null
                    && slot.supportedProfileIds().equals(Set.of("CUSTOMER_POSTGRES"))).count() != 1) throw invalid();
        } else if (!definition.aiStateStorageTopologies().isEmpty()) throw invalid();
        for (BindingSlot slot : definition.bindingSlots()) {
            if (slot.supportedProfileIds().isEmpty() || slot.supportedProfileIds().stream().anyMatch(String::isBlank)) throw invalid();
            boolean pluginMapped = slot.pluginId() != null && !slot.pluginId().isBlank();
            boolean fieldMapped = slot.installConfigField() != null && !slot.installConfigField().isBlank();
            if (slot.kind() == BindingKind.EXTERNAL_SERVICE) {
                if (!pluginMapped || fieldMapped) throw invalid();
                continue;
            }
            if (pluginMapped != fieldMapped) throw invalid();
            if (pluginMapped) {
                var manifest = pinnedManifests.get(slot.pluginId());
                if (manifest == null || slot.installConfigField().length() > 128
                    || manifest.installForm().stream().noneMatch(field ->
                        slot.installConfigField().equals(field.id()) && "text".equals(field.type()))) {
                    throw invalid();
                }
            }
        }
        if (!definition.documentAudiences().isEmpty() && definition.bindingSlots().stream()
            .noneMatch(slot -> slot.kind() == BindingKind.CUSTOMER_S3)) throw invalid();
    }

    /** Resolve the sole reviewed runtime image without exposing private artifact identifiers to customer clients. */
    public String runtimeArtifactId(Definition definition) {
        validate(definition);
        var runtimeArtifacts = definition.artifacts().stream().filter(pin -> artifacts.findById(pin.artifactId())
            .filter(artifact -> "RUNTIME".equals(artifact.getArtifactRole()))
            .isPresent()).toList();
        if (runtimeArtifacts.size() != 1) throw conflict(ARTIFACT_REQUIRED);
        return runtimeArtifacts.getFirst().artifactId();
    }

    /** Require an immutable deployment version to contain exactly the reviewed template and plugin pins. */
    public void requireVersion(Definition definition, DeploymentVersionEntity version) {
        if (definition == null || version == null) throw deny(REVIEWED_COMPOSITION_CHANGED);
        var provenance = codec.tree(version.getCompositionProvenanceJson());
        try { externalIntegrations.requireCompilerEvidence(definition, provenance); }
        catch (RuntimeException error) { throw deny(REVIEWED_COMPOSITION_CHANGED); }
        var expected = new HashSet<>(definition.plugins());
        expected.add(definition.template());
        var actual = new HashSet<PluginPin>();
        var installs = provenance.path("marketplaceInstalls");
        if (!"PUBLISHED".equalsIgnoreCase(version.getStatus())
            || !definition.templateId().equals(provenance.path("templateId").asText()) || !installs.isArray()) {
            throw deny(REVIEWED_COMPOSITION_CHANGED);
        }
        for (var install : installs) {
            var match = expected.stream().filter(pin -> pin.pluginId().equals(install.path("pluginId").asText())
                && pin.versionId().equals(install.path("pluginVersionId").asText())
                && ("sha256:" + pin.manifestSha256()).equals(install.path("manifestSha256").asText())).findFirst();
            if (match.isEmpty() || !actual.add(match.get())) throw deny(REVIEWED_COMPOSITION_CHANGED);
        }
        if (!expected.equals(actual)) throw deny(REVIEWED_COMPOSITION_CHANGED);
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
        try { return DeploymentTargetProfileIdentity.fingerprint(mapper, profile); }
        catch (IllegalArgumentException ex) { throw invalid(); }
    }

    public String artifactFingerprint(DeploymentSourceArtifactEntity artifact) {
        return codec.hash(Map.of("id", artifact.getId(), "serviceName", artifact.getServiceName(),
            "artifactType", artifact.getArtifactType(), "artifactRole", nonNull(artifact.getArtifactRole()),
            "imageRepository", nonNull(artifact.getImageRepository()),
            "imageDigest", nonNull(artifact.getImageDigest()), "gitCommitSha", nonNull(artifact.getGitCommitSha()),
            "capabilityManifest", codec.tree(artifact.getCapabilityManifestJson()),
            "capabilityManifestHash", nonNull(artifact.getCapabilityManifestHash())));
    }

    private boolean isProfileSetting(Setting setting) {
        return switch (setting) {
            case LOGO_ASSET_ID, THEME_PROFILE_ID, INITIAL_VIEW, INTERACTION_PROFILE_ID, AUTH_PROFILE_ID,
                DOCUMENT_AUDIENCE, AI_STATE_STORAGE_MODE -> true;
            default -> false;
        };
    }

    private boolean withinTopology(ReviewedTopology value, ReviewedTopology maximum) {
        return value.replicas() <= maximum.replicas() && value.cpuMillis() <= maximum.cpuMillis()
            && value.memoryMiB() <= maximum.memoryMiB() && value.diskMiB() <= maximum.diskMiB()
            && value.databases() <= maximum.databases() && value.indexes() <= maximum.indexes()
            && value.dependencies() <= maximum.dependencies()
            && value.concurrentApplies() <= maximum.concurrentApplies();
    }

    private void requireTopologyWithinLimits(ReviewedTopology topology, ReviewedLimits limits) {
        if (topology == null || limits == null
            || topology.replicas() > limits.replicas()
            || topology.cpuMillis() > limits.cpuMillis()
            || topology.memoryMiB() > limits.memoryMiB()
            || topology.diskMiB() > limits.diskMiB()
            || topology.databases() > limits.databases()
            || topology.indexes() > limits.indexes()
            || topology.dependencies() > limits.dependencies()
            || topology.concurrentApplies() > limits.concurrentApplies()) {
            throw invalid();
        }
    }

    private <T, K> void unique(List<T> items, Function<T, K> key) {
        Set<K> keys = new HashSet<>();
        if (items.stream().anyMatch(item -> !keys.add(key.apply(item)))) throw invalid();
    }
    private String nonNull(String value) { return value == null ? "" : value; }
}
