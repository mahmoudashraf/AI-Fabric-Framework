package com.ai.fabric.platform.backend.shopify.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.config.ShopifyCompanionBootstrapProperties;
import com.ai.fabric.platform.backend.shopify.entity.ShopifyCompanionPackageProfileEntity;
import com.ai.fabric.platform.backend.shopify.model.ShopifyCompanionPackageProfileSummary;
import com.ai.fabric.platform.backend.shopify.model.UpdateShopifyCompanionPackageProfileStatusRequest;
import com.ai.fabric.platform.backend.shopify.model.UpsertShopifyCompanionPackageProfileRequest;
import com.ai.fabric.platform.backend.shopify.repository.ShopifyCompanionPackageProfileRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class ShopifyCompanionPackageProfileCatalogService {

    public static final String STARTER_PROFILE_KEY = "BALANCED";
    public static final String DEFAULT_PROFILE_KEY = "HIGH_QUALITY";
    private static final Pattern KEY_PATTERN = Pattern.compile("[A-Z0-9_][A-Z0-9_-]{1,63}");
    private static final Set<String> PROFILE_STATUSES = Set.of("DRAFT", "ACTIVE", "DISABLED");
    private static final Set<String> COST_POSTURES = Set.of("LOW", "STANDARD", "HIGH", "QUALITY", "ENTERPRISE");
    private static final Set<String> VECTOR_PROVISIONING_MODES = Set.of("EXTERNAL_EXISTING", "PLATFORM_MANAGED", "LOCAL_MANAGED");
    private static final Set<String> VECTOR_STORAGE_POSTURES = Set.of("SHARED", "DEDICATED", "EMBEDDED");

    private final ShopifyCompanionPackageProfileRepository repository;
    private final ShopifyCompanionBootstrapProperties bootstrapProperties;
    private final PlatformAuditService platformAuditService;
    private final ObjectMapper objectMapper;
    private final ShopifyCompanionPackageProfileOptionsService optionsService;

    public ShopifyCompanionPackageProfileCatalogService(ShopifyCompanionPackageProfileRepository repository,
                                                       ShopifyCompanionBootstrapProperties bootstrapProperties,
                                                       PlatformAuditService platformAuditService,
                                                       ObjectMapper objectMapper,
                                                       ShopifyCompanionPackageProfileOptionsService optionsService) {
        this.repository = repository;
        this.bootstrapProperties = bootstrapProperties;
        this.platformAuditService = platformAuditService;
        this.objectMapper = objectMapper;
        this.optionsService = optionsService;
    }

    @Transactional(readOnly = true)
    public List<ShopifyCompanionPackageProfileSummary> listActiveProfiles() {
        return repository.findAllByStatusIgnoreCaseOrderByProfileKeyAsc("ACTIVE").stream()
            .map(this::toSummary)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<ShopifyCompanionPackageProfileSummary> listProfiles(boolean activeOnly) {
        List<ShopifyCompanionPackageProfileEntity> profiles = activeOnly
            ? repository.findAllByStatusIgnoreCaseOrderByProfileKeyAsc("ACTIVE")
            : repository.findAllByOrderByProfileKeyAsc();
        return profiles.stream()
            .map(this::toSummary)
            .toList();
    }

    @Transactional(readOnly = true)
    public ShopifyCompanionPackageProfileSummary getProfile(String profileKey) {
        return toSummary(findProfile(profileKey));
    }

    @Transactional
    public ShopifyCompanionPackageProfileSummary upsertProfile(String profileKey,
                                                               UpsertShopifyCompanionPackageProfileRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Profile payload is required.");
        }
        String normalizedProfileKey = normalizeProfileKeyForWrite(profileKey);
        String packageKey = requiredUpper(request.packageKey(), "packageKey");
        String tierKey = requiredUpper(request.tierKey(), "tierKey");
        String runtimeProfileKey = requiredUpper(request.runtimeProfileKey(), "runtimeProfileKey");
        String vectorProfileKey = requiredUpper(request.vectorProfileKey(), "vectorProfileKey");
        String displayName = requiredText(request.displayName(), "displayName");
        String costPosture = optionalEnumUpper(request.costPosture(), "costPosture", COST_POSTURES, "STANDARD");
        String templatePluginId = requiredText(request.templatePluginId(), "templatePluginId");
        String deploymentTemplateId = requiredText(request.deploymentTemplateId(), "deploymentTemplateId");
        String inferencePluginId = requiredText(request.inferencePluginId(), "inferencePluginId");
        String vectorStrategy = requiredText(request.vectorStrategy(), "vectorStrategy").toLowerCase(Locale.ROOT);
        String vectorProvisioningMode = requiredEnumUpper(
            request.vectorProvisioningMode(),
            "vectorProvisioningMode",
            VECTOR_PROVISIONING_MODES
        );
        String vectorStoragePosture = requiredEnumUpper(
            request.vectorStoragePosture(),
            "vectorStoragePosture",
            VECTOR_STORAGE_POSTURES
        );
        String verificationPackId = requiredText(request.verificationPackId(), "verificationPackId");
        String status = optionalEnumUpper(request.status(), "status", PROFILE_STATUSES, "DRAFT");
        String detailsJson = normalizeDetailsJson(request.detailsJson());

        optionsService.validateUpsert(normalizedProfileKey, request);
        if ("ACTIVE".equals(status)) {
            enforceSingleActivePackageTier(packageKey, tierKey, normalizedProfileKey);
        }

        Instant now = Instant.now();
        ShopifyCompanionPackageProfileEntity entity = repository.findByProfileKeyIgnoreCase(normalizedProfileKey)
            .orElseGet(() -> {
                ShopifyCompanionPackageProfileEntity created = new ShopifyCompanionPackageProfileEntity();
                created.setId("scp-" + UUID.randomUUID().toString().substring(0, 8));
                created.setProfileKey(normalizedProfileKey);
                created.setCreatedAt(now);
                return created;
            });
        String previousStatus = entity.getStatus();
        entity.setPackageKey(packageKey);
        entity.setTierKey(tierKey);
        entity.setRuntimeProfileKey(runtimeProfileKey);
        entity.setVectorProfileKey(vectorProfileKey);
        entity.setDisplayName(displayName);
        entity.setDescription(blankToNull(request.description()));
        entity.setCostPosture(costPosture);
        entity.setTemplatePluginId(templatePluginId);
        entity.setTemplatePluginVersion(blankToNull(request.templatePluginVersion()));
        entity.setDeploymentTemplateId(deploymentTemplateId);
        entity.setInferencePluginId(inferencePluginId);
        entity.setVectorStrategy(vectorStrategy);
        entity.setVectorProvisioningMode(vectorProvisioningMode);
        entity.setVectorStoragePosture(vectorStoragePosture);
        entity.setVerificationPackId(verificationPackId);
        entity.setStatus(status);
        entity.setDetailsJson(detailsJson);
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(now);
        }
        entity.setUpdatedAt(now);

        ShopifyCompanionPackageProfileEntity saved = repository.save(entity);
        recordAudit("SHOPIFY_COMPANION_PACKAGE_PROFILE_UPSERTED", saved, previousStatus, request.reason());
        return toSummary(saved);
    }

    @Transactional
    public ShopifyCompanionPackageProfileSummary updateProfileStatus(String profileKey,
                                                                     UpdateShopifyCompanionPackageProfileStatusRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status payload is required.");
        }
        ShopifyCompanionPackageProfileEntity entity = findProfile(profileKey);
        String status = requiredEnumUpper(request.status(), "status", PROFILE_STATUSES);
        if ("ACTIVE".equals(status)) {
            enforceSingleActivePackageTier(entity.getPackageKey(), entity.getTierKey(), entity.getProfileKey());
        }
        String previousStatus = entity.getStatus();
        entity.setStatus(status);
        entity.setUpdatedAt(Instant.now());
        ShopifyCompanionPackageProfileEntity saved = repository.save(entity);
        recordAudit("SHOPIFY_COMPANION_PACKAGE_PROFILE_STATUS_CHANGED", saved, previousStatus, request.reason());
        return toSummary(saved);
    }

    @Transactional(readOnly = true)
    public ResolvedPackageProfile resolve(String requestedPackageKey,
                                          String requestedTierKey,
                                          String requestedRuntimeProfileKey,
                                          String requestedVectorProfileKey) {
        String profileKey = normalizeProfileKey(requestedRuntimeProfileKey);
        Optional<ShopifyCompanionPackageProfileEntity> byProfile = StringUtils.hasText(profileKey)
            ? repository.findByProfileKeyIgnoreCase(profileKey)
                .filter(this::isActiveProfile)
            : Optional.empty();
        ShopifyCompanionPackageProfileEntity entity = byProfile
            .or(() -> resolveByTier(requestedTierKey))
            .or(() -> resolveByPackage(requestedPackageKey))
            .or(() -> repository.findByProfileKeyIgnoreCase(DEFAULT_PROFILE_KEY).filter(this::isActiveProfile))
            .orElseGet(this::fallbackBalancedProfile);
        String vectorProfileKey = StringUtils.hasText(requestedVectorProfileKey)
            ? requestedVectorProfileKey.trim().toUpperCase(Locale.ROOT)
            : entity.getVectorProfileKey();
        List<String> requiredPluginIds = requiredPluginIds(entity);
        List<String> disabledPluginIds = disabledPluginIds(entity);
        String stagingTemplatePluginId = detailText(
            entity,
            "stagingTemplatePluginId",
            firstText(entity.getTemplatePluginId(), bootstrapProperties.stagingTemplatePluginId())
        );
        String productionTemplatePluginId = detailText(
            entity,
            "productionTemplatePluginId",
            bootstrapProperties.productionTemplatePluginId()
        );
        String productionTargetProfileId = detailText(
            entity,
            "productionTargetProfileId",
            bootstrapProperties.goLiveTargetProfileId()
        );
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
            requiredPluginIds,
            disabledPluginIds,
            stagingTemplatePluginId,
            productionTemplatePluginId,
            productionTargetProfileId,
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
            profile.templatePluginId(),
            profile.templatePluginVersion(),
            profile.deploymentTemplateId(),
            profile.inferencePluginId(),
            profile.vectorStrategy(),
            profile.vectorProvisioningMode(),
            profile.vectorStoragePosture(),
            profile.verificationPackId(),
            "ACTIVE",
            profileDetailsJson(profile),
            null,
            null
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
            case "FREE" -> null;
            case "STARTER" -> STARTER_PROFILE_KEY;
            case "ELITE" -> "HIGH_QUALITY";
            case "ENTERPRISE" -> "ENTERPRISE_DEDICATED";
            default -> DEFAULT_PROFILE_KEY;
        };
        if (profileKey == null) {
            return Optional.empty();
        }
        return repository.findByProfileKeyIgnoreCase(profileKey)
            .filter(this::isActiveProfile);
    }

    private boolean isActiveProfile(ShopifyCompanionPackageProfileEntity entity) {
        return entity != null && "ACTIVE".equalsIgnoreCase(entity.getStatus());
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
            entity.getTemplatePluginId(),
            blankToNull(entity.getTemplatePluginVersion()),
            entity.getDeploymentTemplateId(),
            entity.getInferencePluginId(),
            entity.getVectorStrategy(),
            entity.getVectorProvisioningMode(),
            entity.getVectorStoragePosture(),
            entity.getVerificationPackId(),
            entity.getStatus(),
            normalizePersistedDetailsJson(entity.getDetailsJson()),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }

    private ShopifyCompanionPackageProfileEntity findProfile(String profileKey) {
        String normalizedProfileKey = normalizeProfileKeyForWrite(profileKey);
        return repository.findByProfileKeyIgnoreCase(normalizedProfileKey)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Shopify Companion package profile not found: " + normalizedProfileKey
            ));
    }

    private void enforceSingleActivePackageTier(String packageKey, String tierKey, String profileKey) {
        List<ShopifyCompanionPackageProfileEntity> duplicates =
            repository.findAllByPackageKeyIgnoreCaseAndTierKeyIgnoreCaseAndStatusIgnoreCaseOrderByUpdatedAtDesc(
                packageKey,
                tierKey,
                "ACTIVE"
            );
        boolean anotherActiveProfile = duplicates.stream()
            .anyMatch(existing -> !existing.getProfileKey().equalsIgnoreCase(profileKey));
        if (anotherActiveProfile) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "An active Shopify Companion package profile already exists for package "
                    + packageKey + " and tier " + tierKey + ". Disable it before activating another mapping."
            );
        }
    }

    private void recordAudit(String action,
                             ShopifyCompanionPackageProfileEntity entity,
                             String previousStatus,
                             String reason) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("profileKey", entity.getProfileKey());
        details.put("packageKey", entity.getPackageKey());
        details.put("tierKey", entity.getTierKey());
        details.put("runtimeProfileKey", entity.getRuntimeProfileKey());
        details.put("vectorProfileKey", entity.getVectorProfileKey());
        details.put("inferencePluginId", entity.getInferencePluginId());
        details.put("deploymentTemplateId", entity.getDeploymentTemplateId());
        details.put("verificationPackId", entity.getVerificationPackId());
        details.put("previousStatus", previousStatus);
        details.put("status", entity.getStatus());
        details.put("reason", blankToNull(reason));
        platformAuditService.record(action, "SHOPIFY_COMPANION_PACKAGE_PROFILE", entity.getProfileKey(), details);
    }

    private String normalizeProfileKeyForWrite(String value) {
        String normalized = requiredUpper(value, "profileKey");
        if (!KEY_PATTERN.matcher(normalized).matches()) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "profileKey must be 2-64 characters and contain only uppercase letters, numbers, underscores, or hyphens."
            );
        }
        return normalized;
    }

    private String requiredText(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " is required.");
        }
        return value.trim();
    }

    private String requiredUpper(String value, String fieldName) {
        return requiredText(value, fieldName).toUpperCase(Locale.ROOT);
    }

    private String requiredEnumUpper(String value, String fieldName, Set<String> allowedValues) {
        String normalized = requiredUpper(value, fieldName);
        if (!allowedValues.contains(normalized)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                fieldName + " must be one of " + String.join(", ", allowedValues) + "."
            );
        }
        return normalized;
    }

    private String optionalEnumUpper(String value, String fieldName, Set<String> allowedValues, String defaultValue) {
        if (!StringUtils.hasText(value)) {
            return defaultValue;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!allowedValues.contains(normalized)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                fieldName + " must be one of " + String.join(", ", allowedValues) + "."
            );
        }
        return normalized;
    }

    private String normalizeDetailsJson(String value) {
        if (!StringUtils.hasText(value)) {
            return "{}";
        }
        try {
            JsonNode node = objectMapper.readTree(value);
            if (!node.isObject()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "detailsJson must be a JSON object.");
            }
            return objectMapper.writeValueAsString(node);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "detailsJson must be valid JSON.", ex);
        }
    }

    private String normalizePersistedDetailsJson(String value) {
        return StringUtils.hasText(value) ? value : "{}";
    }

    private ShopifyCompanionPackageProfileEntity fallbackBalancedProfile() {
        Instant now = Instant.now();
        ShopifyCompanionPackageProfileEntity entity = new ShopifyCompanionPackageProfileEntity();
        entity.setId("scp-fallback-high-quality");
        entity.setProfileKey(DEFAULT_PROFILE_KEY);
        entity.setPackageKey("ELITE");
        entity.setTierKey("ELITE");
        entity.setRuntimeProfileKey(DEFAULT_PROFILE_KEY);
        entity.setVectorProfileKey("QDRANT_SHARED");
        entity.setDisplayName("High quality");
        entity.setDescription("Default high-capability Shopify Companion profile.");
        entity.setCostPosture("QUALITY");
        entity.setTemplatePluginId(bootstrapProperties.templatePluginId());
        entity.setTemplatePluginVersion(blankToNull(bootstrapProperties.templatePluginVersion()));
        entity.setDeploymentTemplateId(bootstrapProperties.defaultTemplateId());
        entity.setInferencePluginId("mkp-inference-premium-hybrid");
        entity.setVectorStrategy("qdrant");
        entity.setVectorProvisioningMode("EXTERNAL_EXISTING");
        entity.setVectorStoragePosture("SHARED");
        entity.setVerificationPackId("shopify-companion-elite-readiness");
        entity.setStatus("ACTIVE");
        entity.setDetailsJson("""
            {"merchantVisible":true,"defaultPackage":true,"requiresBilling":false,"requiresReindexOnEmbeddingChange":true,"enableCustomerAccountMcp":true,"enableCheckoutMcp":true,"requiredPluginIds":["mkp-action-shopify-storefront-read-mcp","mkp-action-shopify-cart-mcp","mkp-action-shopify-customer-account-mcp","mkp-action-shopify-checkout-mcp","mkp-data-shopify-catalog","mkp-data-shopify-policies","mkp-inference-premium-hybrid"],"disabledPluginIds":["mkp-action-shopify-companion-read"]}
            """.trim());
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

    private List<String> requiredPluginIds(ShopifyCompanionPackageProfileEntity entity) {
        LinkedHashSet<String> pluginIds = readPluginIdArray(entity.getDetailsJson(), "requiredPluginIds", true);
        if (pluginIds.isEmpty()) {
            pluginIds.add(ShopifyCompanionPluginSelection.ACTION_STOREFRONT_READ_MCP_PLUGIN_ID);
            pluginIds.add(ShopifyCompanionPluginSelection.DATA_CATALOG_PLUGIN_ID);
            pluginIds.add(ShopifyCompanionPluginSelection.DATA_POLICIES_PLUGIN_ID);
            if (isEliteOrHigher(entity)) {
                pluginIds.add(ShopifyCompanionPluginSelection.ACTION_CART_MCP_PLUGIN_ID);
            }
        }
        if (detailFlag(entity, "enableCustomerAccountMcp")) {
            pluginIds.add(ShopifyCompanionPluginSelection.ACTION_CUSTOMER_ACCOUNT_MCP_PLUGIN_ID);
        }
        if (detailFlag(entity, "enableCheckoutMcp")) {
            pluginIds.add(ShopifyCompanionPluginSelection.ACTION_CHECKOUT_MCP_PLUGIN_ID);
        }
        pluginIds.add(entity.getInferencePluginId());
        pluginIds.removeIf(pluginId -> !StringUtils.hasText(pluginId));
        return List.copyOf(pluginIds);
    }

    private List<String> disabledPluginIds(ShopifyCompanionPackageProfileEntity entity) {
        LinkedHashSet<String> pluginIds = readPluginIdArray(entity.getDetailsJson(), "disabledPluginIds", false);
        pluginIds.add(ShopifyCompanionPluginSelection.LEGACY_ACTION_READ_PLUGIN_ID);
        if (detailFlag(entity, "enableCustomerAccountMcp")) {
            pluginIds.remove(ShopifyCompanionPluginSelection.ACTION_CUSTOMER_ACCOUNT_MCP_PLUGIN_ID);
        } else {
            pluginIds.add(ShopifyCompanionPluginSelection.ACTION_CUSTOMER_ACCOUNT_MCP_PLUGIN_ID);
        }
        if (detailFlag(entity, "enableCheckoutMcp")) {
            pluginIds.remove(ShopifyCompanionPluginSelection.ACTION_CHECKOUT_MCP_PLUGIN_ID);
        } else {
            pluginIds.add(ShopifyCompanionPluginSelection.ACTION_CHECKOUT_MCP_PLUGIN_ID);
        }
        if (!isEliteOrHigher(entity)) {
            pluginIds.add(ShopifyCompanionPluginSelection.ACTION_CART_MCP_PLUGIN_ID);
        }
        pluginIds.remove(entity.getInferencePluginId());
        pluginIds.removeIf(pluginId -> !StringUtils.hasText(pluginId));
        return List.copyOf(pluginIds);
    }

    private LinkedHashSet<String> readPluginIdArray(String detailsJson, String fieldName, boolean canonicalize) {
        LinkedHashSet<String> pluginIds = new LinkedHashSet<>();
        JsonNode root = readDetailsNode(detailsJson);
        JsonNode array = root == null ? null : root.path(fieldName);
        if (array != null && array.isArray()) {
            for (JsonNode value : array) {
                String pluginId = blankToNull(value.asText(null));
                if (pluginId != null) {
                    pluginIds.add(canonicalize ? ShopifyCompanionPluginSelection.canonicalizePluginId(pluginId) : pluginId);
                }
            }
        }
        return pluginIds;
    }

    private JsonNode readDetailsNode(String detailsJson) {
        if (!StringUtils.hasText(detailsJson)) {
            return objectMapper.createObjectNode();
        }
        try {
            JsonNode node = objectMapper.readTree(detailsJson);
        return node != null && node.isObject() ? node : objectMapper.createObjectNode();
        } catch (Exception ignored) {
            return objectMapper.createObjectNode();
        }
    }

    private boolean detailFlag(ShopifyCompanionPackageProfileEntity entity, String fieldName) {
        if (entity == null || !StringUtils.hasText(fieldName)) {
            return false;
        }
        return readDetailsNode(entity.getDetailsJson()).path(fieldName).asBoolean(false);
    }

    private String detailText(ShopifyCompanionPackageProfileEntity entity, String fieldName, String fallback) {
        if (entity == null || !StringUtils.hasText(fieldName)) {
            return blankToNull(fallback);
        }
        JsonNode value = readDetailsNode(entity.getDetailsJson()).path(fieldName);
        if (value.isTextual() && StringUtils.hasText(value.asText())) {
            return value.asText().trim();
        }
        return blankToNull(fallback);
    }

    private String firstText(String first, String second) {
        String normalizedFirst = blankToNull(first);
        return normalizedFirst != null ? normalizedFirst : blankToNull(second);
    }

    private boolean isEliteOrHigher(ShopifyCompanionPackageProfileEntity entity) {
        String tierKey = entity == null ? "" : String.valueOf(entity.getTierKey()).trim().toUpperCase(Locale.ROOT);
        String packageKey = entity == null ? "" : String.valueOf(entity.getPackageKey()).trim().toUpperCase(Locale.ROOT);
        return "ELITE".equals(tierKey)
            || "ENTERPRISE".equals(tierKey)
            || "ELITE".equals(packageKey)
            || "ENTERPRISE".equals(packageKey);
    }

    private String profileDetailsJson(ResolvedPackageProfile profile) {
        if (profile.summary() != null && StringUtils.hasText(profile.summary().detailsJson())) {
            return profile.summary().detailsJson();
        }
        try {
            com.fasterxml.jackson.databind.node.ObjectNode details = objectMapper.createObjectNode();
            details.set("requiredPluginIds", objectMapper.valueToTree(profile.requiredPluginIds()));
            details.set("disabledPluginIds", objectMapper.valueToTree(profile.disabledPluginIds()));
            details.put("stagingTemplatePluginId", profile.stagingTemplatePluginId());
            details.put("productionTemplatePluginId", profile.productionTemplatePluginId());
            details.put("productionTargetProfileId", profile.productionTargetProfileId());
            return objectMapper.writeValueAsString(details);
        } catch (Exception ignored) {
            return "{}";
        }
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
        List<String> requiredPluginIds,
        List<String> disabledPluginIds,
        String stagingTemplatePluginId,
        String productionTemplatePluginId,
        String productionTargetProfileId,
        ShopifyCompanionPackageProfileSummary summary
    ) {
    }
}
