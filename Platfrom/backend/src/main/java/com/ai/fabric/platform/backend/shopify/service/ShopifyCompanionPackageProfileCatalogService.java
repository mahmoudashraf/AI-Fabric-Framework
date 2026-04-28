package com.ai.fabric.platform.backend.shopify.service;

import com.ai.fabric.platform.backend.config.ShopifyCompanionBootstrapProperties;
import com.ai.fabric.platform.backend.shopify.entity.ShopifyCompanionPackageProfileEntity;
import com.ai.fabric.platform.backend.shopify.model.ShopifyCompanionPackageProfileSummary;
import com.ai.fabric.platform.backend.shopify.repository.ShopifyCompanionPackageProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class ShopifyCompanionPackageProfileCatalogService {

    public static final String DEFAULT_PROFILE_KEY = "BALANCED";

    private final ShopifyCompanionPackageProfileRepository repository;
    private final ShopifyCompanionBootstrapProperties bootstrapProperties;

    public ShopifyCompanionPackageProfileCatalogService(ShopifyCompanionPackageProfileRepository repository,
                                                       ShopifyCompanionBootstrapProperties bootstrapProperties) {
        this.repository = repository;
        this.bootstrapProperties = bootstrapProperties;
    }

    public List<ShopifyCompanionPackageProfileSummary> listActiveProfiles() {
        return repository.findAllByStatusIgnoreCaseOrderByProfileKeyAsc("ACTIVE").stream()
            .map(this::toSummary)
            .toList();
    }

    public ResolvedPackageProfile resolve(String requestedPackageKey,
                                          String requestedTierKey,
                                          String requestedRuntimeProfileKey,
                                          String requestedVectorProfileKey) {
        String profileKey = normalizeProfileKey(requestedRuntimeProfileKey);
        Optional<ShopifyCompanionPackageProfileEntity> byProfile = StringUtils.hasText(profileKey)
            ? repository.findByProfileKeyIgnoreCase(profileKey)
            : Optional.empty();
        ShopifyCompanionPackageProfileEntity entity = byProfile
            .or(() -> resolveByTier(requestedTierKey))
            .or(() -> resolveByPackage(requestedPackageKey))
            .or(() -> repository.findByProfileKeyIgnoreCase(DEFAULT_PROFILE_KEY))
            .orElseGet(this::fallbackBalancedProfile);
        String vectorProfileKey = StringUtils.hasText(requestedVectorProfileKey)
            ? requestedVectorProfileKey.trim().toUpperCase(Locale.ROOT)
            : entity.getVectorProfileKey();
        return new ResolvedPackageProfile(
            entity.getProfileKey(),
            entity.getPackageKey(),
            entity.getTierKey(),
            entity.getRuntimeProfileKey(),
            vectorProfileKey,
            entity.getDisplayName(),
            entity.getDescription(),
            entity.getCostPosture(),
            entity.getTemplatePluginId(),
            blankToNull(entity.getTemplatePluginVersion()),
            entity.getDeploymentTemplateId(),
            entity.getInferencePluginId(),
            entity.getVectorStrategy(),
            entity.getVectorProvisioningMode(),
            entity.getVectorStoragePosture(),
            entity.getVerificationPackId(),
            toSummary(entity)
        );
    }

    public ShopifyCompanionPackageProfileSummary toSummary(ResolvedPackageProfile profile) {
        if (profile == null) {
            return null;
        }
        return new ShopifyCompanionPackageProfileSummary(
            profile.profileKey(),
            profile.packageKey(),
            profile.tierKey(),
            profile.runtimeProfileKey(),
            profile.vectorProfileKey(),
            profile.displayName(),
            profile.description(),
            profile.costPosture(),
            profile.vectorStrategy(),
            profile.vectorProvisioningMode(),
            profile.vectorStoragePosture(),
            profile.verificationPackId(),
            "ACTIVE"
        );
    }

    private Optional<ShopifyCompanionPackageProfileEntity> resolveByTier(String tierKey) {
        String normalized = normalizeTierKey(tierKey);
        return StringUtils.hasText(normalized)
            ? repository.findFirstByTierKeyIgnoreCaseAndStatusIgnoreCaseOrderByUpdatedAtDesc(normalized, "ACTIVE")
            : Optional.empty();
    }

    private Optional<ShopifyCompanionPackageProfileEntity> resolveByPackage(String packageKey) {
        String normalized = normalizePackageKey(packageKey);
        if (!StringUtils.hasText(normalized)) {
            return Optional.empty();
        }
        String profileKey = switch (normalized) {
            case "FREE" -> "LOW_COST";
            case "STARTER" -> DEFAULT_PROFILE_KEY;
            case "ELITE" -> "HIGH_QUALITY";
            case "ENTERPRISE" -> "ENTERPRISE_DEDICATED";
            default -> DEFAULT_PROFILE_KEY;
        };
        return repository.findByProfileKeyIgnoreCase(profileKey);
    }

    private ShopifyCompanionPackageProfileSummary toSummary(ShopifyCompanionPackageProfileEntity entity) {
        return new ShopifyCompanionPackageProfileSummary(
            entity.getProfileKey(),
            entity.getPackageKey(),
            entity.getTierKey(),
            entity.getRuntimeProfileKey(),
            entity.getVectorProfileKey(),
            entity.getDisplayName(),
            entity.getDescription(),
            entity.getCostPosture(),
            entity.getVectorStrategy(),
            entity.getVectorProvisioningMode(),
            entity.getVectorStoragePosture(),
            entity.getVerificationPackId(),
            entity.getStatus()
        );
    }

    private ShopifyCompanionPackageProfileEntity fallbackBalancedProfile() {
        Instant now = Instant.now();
        ShopifyCompanionPackageProfileEntity entity = new ShopifyCompanionPackageProfileEntity();
        entity.setId("scp-fallback-balanced");
        entity.setProfileKey(DEFAULT_PROFILE_KEY);
        entity.setPackageKey("STARTER");
        entity.setTierKey("STARTER");
        entity.setRuntimeProfileKey(DEFAULT_PROFILE_KEY);
        entity.setVectorProfileKey("QDRANT_SHARED");
        entity.setDisplayName("Balanced");
        entity.setDescription("Default Shopify Companion profile.");
        entity.setCostPosture("STANDARD");
        entity.setTemplatePluginId(bootstrapProperties.templatePluginId());
        entity.setTemplatePluginVersion(blankToNull(bootstrapProperties.templatePluginVersion()));
        entity.setDeploymentTemplateId(bootstrapProperties.defaultTemplateId());
        entity.setInferencePluginId("mkp-inference-shared-embeddings");
        entity.setVectorStrategy("qdrant");
        entity.setVectorProvisioningMode("EXTERNAL_EXISTING");
        entity.setVectorStoragePosture("SHARED");
        entity.setVerificationPackId("starter-launch-readiness");
        entity.setStatus("ACTIVE");
        entity.setDetailsJson("{}");
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return entity;
    }

    private String normalizeProfileKey(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "LOW", "LOW_COST", "FREE" -> "LOW_COST";
            case "BALANCED", "STANDARD", "STARTER" -> DEFAULT_PROFILE_KEY;
            case "HIGH", "HIGH_QUALITY", "PREMIUM", "ELITE" -> "HIGH_QUALITY";
            case "ENTERPRISE", "ENTERPRISE_DEDICATED", "DEDICATED" -> "ENTERPRISE_DEDICATED";
            default -> value.trim().toUpperCase(Locale.ROOT);
        };
    }

    private String normalizeTierKey(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "FREE" -> "FREE";
            case "STARTER" -> "STARTER";
            case "ELITE" -> "ELITE";
            case "ENTERPRISE" -> "ENTERPRISE";
            default -> value.trim().toUpperCase(Locale.ROOT);
        };
    }

    private String normalizePackageKey(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    public record ResolvedPackageProfile(
        String profileKey,
        String packageKey,
        String tierKey,
        String runtimeProfileKey,
        String vectorProfileKey,
        String displayName,
        String description,
        String costPosture,
        String templatePluginId,
        String templatePluginVersion,
        String deploymentTemplateId,
        String inferencePluginId,
        String vectorStrategy,
        String vectorProvisioningMode,
        String vectorStoragePosture,
        String verificationPackId,
        ShopifyCompanionPackageProfileSummary summary
    ) {
    }
}
