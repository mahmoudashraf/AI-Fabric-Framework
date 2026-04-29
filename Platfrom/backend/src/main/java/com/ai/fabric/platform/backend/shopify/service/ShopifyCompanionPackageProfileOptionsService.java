package com.ai.fabric.platform.backend.shopify.service;

import com.ai.fabric.platform.backend.config.ShopifyCompanionBootstrapProperties;
import com.ai.fabric.platform.backend.deployment.model.DeploymentTemplateSummary;
import com.ai.fabric.platform.backend.deployment.model.PlatformVerificationSuiteDefinitionSummary;
import com.ai.fabric.platform.backend.deployment.service.DeploymentService;
import com.ai.fabric.platform.backend.deployment.service.PlatformVerificationSuiteService;
import com.ai.fabric.platform.backend.marketplace.model.MarketplacePluginSummary;
import com.ai.fabric.platform.backend.marketplace.service.MarketplaceCatalogService;
import com.ai.fabric.platform.backend.shopify.entity.ShopifyCompanionPackageProfileEntity;
import com.ai.fabric.platform.backend.shopify.model.ShopifyCompanionPackageProfileBlueprintSummary;
import com.ai.fabric.platform.backend.shopify.model.ShopifyCompanionPackageProfileChoiceSummary;
import com.ai.fabric.platform.backend.shopify.model.ShopifyCompanionPackageProfileCompatibilityRuleSummary;
import com.ai.fabric.platform.backend.shopify.model.ShopifyCompanionPackageProfileOptionsSummary;
import com.ai.fabric.platform.backend.shopify.model.UpsertShopifyCompanionPackageProfileRequest;
import com.ai.fabric.platform.backend.shopify.repository.ShopifyCompanionPackageProfileRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class ShopifyCompanionPackageProfileOptionsService {

    private static final String SOURCE_PLATFORM_CATALOG = "PLATFORM_CATALOG";
    private static final String SOURCE_PROFILE_CATALOG = "PROFILE_CATALOG";
    private static final String SOURCE_MARKETPLACE = "MARKETPLACE";
    private static final String SOURCE_DEPLOYMENT_TEMPLATE = "DEPLOYMENT_TEMPLATE";
    private static final String SOURCE_VERIFICATION_SUITE = "VERIFICATION_SUITE";
    private static final Set<String> PROFILE_STATUSES = Set.of("DRAFT", "ACTIVE", "DISABLED");
    private static final Set<String> COST_POSTURES = Set.of("LOW", "STANDARD", "QUALITY", "HIGH", "ENTERPRISE");

    private final ShopifyCompanionPackageProfileRepository repository;
    private final ShopifyCompanionBootstrapProperties bootstrapProperties;
    private final DeploymentService deploymentService;
    private final MarketplaceCatalogService marketplaceCatalogService;
    private final PlatformVerificationSuiteService verificationSuiteService;

    public ShopifyCompanionPackageProfileOptionsService(ShopifyCompanionPackageProfileRepository repository,
                                                        ShopifyCompanionBootstrapProperties bootstrapProperties,
                                                        DeploymentService deploymentService,
                                                        MarketplaceCatalogService marketplaceCatalogService,
                                                        PlatformVerificationSuiteService verificationSuiteService) {
        this.repository = repository;
        this.bootstrapProperties = bootstrapProperties;
        this.deploymentService = deploymentService;
        this.marketplaceCatalogService = marketplaceCatalogService;
        this.verificationSuiteService = verificationSuiteService;
    }

    @Transactional(readOnly = true)
    public ShopifyCompanionPackageProfileOptionsSummary options() {
        List<ShopifyCompanionPackageProfileEntity> profiles = repository.findAllByOrderByProfileKeyAsc();
        List<DeploymentTemplateSummary> templates = safeDeploymentTemplates();
        List<MarketplacePluginSummary> plugins = safeMarketplacePlugins();
        List<PlatformVerificationSuiteDefinitionSummary> verificationSuites = safeVerificationSuites();
        List<PackageDefinition> definitions = packageDefinitions();

        Map<String, ShopifyCompanionPackageProfileChoiceSummary> profileKeys = new LinkedHashMap<>();
        Map<String, ShopifyCompanionPackageProfileChoiceSummary> packages = new LinkedHashMap<>();
        Map<String, ShopifyCompanionPackageProfileChoiceSummary> tiers = new LinkedHashMap<>();
        Map<String, ShopifyCompanionPackageProfileChoiceSummary> statuses = new LinkedHashMap<>();
        Map<String, ShopifyCompanionPackageProfileChoiceSummary> costPostures = new LinkedHashMap<>();
        Map<String, ShopifyCompanionPackageProfileChoiceSummary> runtimeProfiles = new LinkedHashMap<>();
        Map<String, ShopifyCompanionPackageProfileChoiceSummary> vectorProfiles = new LinkedHashMap<>();
        Map<String, ShopifyCompanionPackageProfileChoiceSummary> templatePlugins = new LinkedHashMap<>();
        Map<String, ShopifyCompanionPackageProfileChoiceSummary> templateVersions = new LinkedHashMap<>();
        Map<String, ShopifyCompanionPackageProfileChoiceSummary> deploymentTemplates = new LinkedHashMap<>();
        Map<String, ShopifyCompanionPackageProfileChoiceSummary> inferencePlugins = new LinkedHashMap<>();
        Map<String, ShopifyCompanionPackageProfileChoiceSummary> vectorStrategies = new LinkedHashMap<>();
        Map<String, ShopifyCompanionPackageProfileChoiceSummary> vectorProvisioningModes = new LinkedHashMap<>();
        Map<String, ShopifyCompanionPackageProfileChoiceSummary> vectorStoragePostures = new LinkedHashMap<>();
        Map<String, ShopifyCompanionPackageProfileChoiceSummary> verificationPacks = new LinkedHashMap<>();

        PROFILE_STATUSES.forEach(status -> put(statuses, choice(status, status, "Profile lifecycle state.", SOURCE_PLATFORM_CATALOG, null, false)));
        COST_POSTURES.forEach(posture -> put(costPostures, choice(posture, posture, "Commercial cost posture.", SOURCE_PLATFORM_CATALOG, null, false)));

        for (PackageDefinition definition : definitions) {
            put(packages, choice(definition.packageKey(), definition.packageKey(), definition.label(), SOURCE_PLATFORM_CATALOG, null, true));
            put(tiers, choice(definition.tierKey(), definition.tierKey(), definition.label(), SOURCE_PLATFORM_CATALOG, null, true));
            put(profileKeys, choice(definition.profileKey(), definition.profileKey(), definition.label(), SOURCE_PLATFORM_CATALOG, null, true));
            put(runtimeProfiles, choice(definition.runtimeProfileKey(), definition.runtimeProfileKey(), definition.label(), SOURCE_PLATFORM_CATALOG, null, true));
            put(vectorProfiles, choice(definition.vectorProfileKey(), definition.vectorProfileKey(), definition.vectorDescription(), SOURCE_PLATFORM_CATALOG, null, true));
            put(inferencePlugins, choice(definition.inferencePluginId(), definition.inferencePluginId(), definition.label(), SOURCE_PLATFORM_CATALOG, null, true));
            put(vectorStrategies, choice(definition.vectorStrategy(), definition.vectorStrategy(), "Vector backend strategy.", SOURCE_PLATFORM_CATALOG, null, true));
            put(vectorProvisioningModes, choice(definition.vectorProvisioningMode(), definition.vectorProvisioningMode(), "Vector resource provisioning mode.", SOURCE_PLATFORM_CATALOG, null, true));
            put(vectorStoragePostures, choice(definition.vectorStoragePosture(), definition.vectorStoragePosture(), "Vector storage isolation posture.", SOURCE_PLATFORM_CATALOG, null, true));
            put(verificationPacks, choice(definition.verificationPackId(), definition.verificationPackId(), definition.label(), SOURCE_PLATFORM_CATALOG, null, true));
            put(templatePlugins, choice(definition.templatePluginId(), definition.templatePluginId(), definition.label(), SOURCE_PLATFORM_CATALOG, null, true));
            put(deploymentTemplates, choice(definition.deploymentTemplateId(), definition.deploymentTemplateId(), definition.label(), SOURCE_PLATFORM_CATALOG, null, true));
        }

        for (DeploymentTemplateSummary template : templates) {
            put(deploymentTemplates, choice(
                template.id(),
                template.name() + " (" + template.id() + ")",
                template.runtimeProfile() + " · " + template.vectorStrategy(),
                SOURCE_DEPLOYMENT_TEMPLATE,
                null,
                true
            ));
            put(runtimeProfiles, choice(template.runtimeProfile().toUpperCase(Locale.ROOT), template.runtimeProfile().toUpperCase(Locale.ROOT), template.name(), SOURCE_DEPLOYMENT_TEMPLATE, null, false));
            put(vectorStrategies, choice(template.vectorStrategy().toLowerCase(Locale.ROOT), template.vectorStrategy().toLowerCase(Locale.ROOT), template.name(), SOURCE_DEPLOYMENT_TEMPLATE, null, false));
        }

        for (MarketplacePluginSummary plugin : plugins) {
            if (isTemplatePlugin(plugin)) {
                put(templatePlugins, pluginChoice(plugin));
            }
            if (isInferencePlugin(plugin)) {
                put(inferencePlugins, pluginChoice(plugin));
            }
            if (StringUtils.hasText(plugin.latestVersion())) {
                put(templateVersions, choice(plugin.latestVersion(), plugin.latestVersion(), "Published marketplace plugin version.", SOURCE_MARKETPLACE, plugin.status(), false));
            }
        }

        for (PlatformVerificationSuiteDefinitionSummary suite : verificationSuites) {
            if (isShopifyVerificationSuite(suite)) {
                put(verificationPacks, choice(
                    suite.key(),
                    suite.label() + " (" + suite.key() + ")",
                    suite.description(),
                    SOURCE_VERIFICATION_SUITE,
                    suite.releaseBlocking() ? "RELEASE_BLOCKING" : "NON_BLOCKING",
                    true
                ));
            }
        }

        for (ShopifyCompanionPackageProfileEntity profile : profiles) {
            put(profileKeys, choice(profile.getProfileKey(), profile.getProfileKey(), profile.getDisplayName(), SOURCE_PROFILE_CATALOG, profile.getStatus(), "ACTIVE".equalsIgnoreCase(profile.getStatus())));
            put(packages, choice(profile.getPackageKey(), profile.getPackageKey(), profile.getDisplayName(), SOURCE_PROFILE_CATALOG, profile.getStatus(), false));
            put(tiers, choice(profile.getTierKey(), profile.getTierKey(), profile.getDisplayName(), SOURCE_PROFILE_CATALOG, profile.getStatus(), false));
            put(runtimeProfiles, choice(profile.getRuntimeProfileKey(), profile.getRuntimeProfileKey(), profile.getDisplayName(), SOURCE_PROFILE_CATALOG, profile.getStatus(), false));
            put(vectorProfiles, choice(profile.getVectorProfileKey(), profile.getVectorProfileKey(), profile.getDisplayName(), SOURCE_PROFILE_CATALOG, profile.getStatus(), false));
            put(templatePlugins, choice(profile.getTemplatePluginId(), profile.getTemplatePluginId(), profile.getDisplayName(), SOURCE_PROFILE_CATALOG, profile.getStatus(), false));
            put(templateVersions, choice(profile.getTemplatePluginVersion(), profile.getTemplatePluginVersion(), profile.getDisplayName(), SOURCE_PROFILE_CATALOG, profile.getStatus(), false));
            put(deploymentTemplates, choice(profile.getDeploymentTemplateId(), profile.getDeploymentTemplateId(), profile.getDisplayName(), SOURCE_PROFILE_CATALOG, profile.getStatus(), false));
            put(inferencePlugins, choice(profile.getInferencePluginId(), profile.getInferencePluginId(), profile.getDisplayName(), SOURCE_PROFILE_CATALOG, profile.getStatus(), false));
            put(vectorStrategies, choice(profile.getVectorStrategy(), profile.getVectorStrategy(), profile.getDisplayName(), SOURCE_PROFILE_CATALOG, profile.getStatus(), false));
            put(vectorProvisioningModes, choice(profile.getVectorProvisioningMode(), profile.getVectorProvisioningMode(), profile.getDisplayName(), SOURCE_PROFILE_CATALOG, profile.getStatus(), false));
            put(vectorStoragePostures, choice(profile.getVectorStoragePosture(), profile.getVectorStoragePosture(), profile.getDisplayName(), SOURCE_PROFILE_CATALOG, profile.getStatus(), false));
            put(verificationPacks, choice(profile.getVerificationPackId(), profile.getVerificationPackId(), profile.getDisplayName(), SOURCE_PROFILE_CATALOG, profile.getStatus(), false));
        }

        List<ShopifyCompanionPackageProfileCompatibilityRuleSummary> rules = definitions.stream()
            .map(definition -> compatibilityRule(definition, profiles, templates))
            .toList();

        return new ShopifyCompanionPackageProfileOptionsSummary(
            values(profileKeys),
            values(packages),
            values(tiers),
            values(statuses),
            values(costPostures),
            values(runtimeProfiles),
            values(vectorProfiles),
            values(templatePlugins),
            values(templateVersions),
            values(deploymentTemplates),
            values(inferencePlugins),
            values(vectorStrategies),
            values(vectorProvisioningModes),
            values(vectorStoragePostures),
            values(verificationPacks),
            definitions.stream().map(PackageDefinition::toBlueprint).toList(),
            rules,
            Instant.now()
        );
    }

    @Transactional(readOnly = true)
    public void validateUpsert(String profileKey, UpsertShopifyCompanionPackageProfileRequest request) {
        ShopifyCompanionPackageProfileOptionsSummary options = options();
        String packageKey = upper(request.packageKey());
        String tierKey = upper(request.tierKey());
        String runtimeProfileKey = upper(request.runtimeProfileKey());
        String vectorProfileKey = upper(request.vectorProfileKey());
        String templatePluginId = text(request.templatePluginId());
        String templatePluginVersion = text(request.templatePluginVersion());
        String deploymentTemplateId = text(request.deploymentTemplateId());
        String inferencePluginId = text(request.inferencePluginId());
        String vectorStrategy = lower(request.vectorStrategy());
        String vectorProvisioningMode = upper(request.vectorProvisioningMode());
        String vectorStoragePosture = upper(request.vectorStoragePosture());
        String verificationPackId = text(request.verificationPackId());

        requireChoice(options.packages(), packageKey, "packageKey");
        requireChoice(options.tiers(), tierKey, "tierKey");
        requireChoice(options.costPostures(), upper(request.costPosture()), "costPosture");
        requireChoice(options.templatePlugins(), templatePluginId, "templatePluginId");
        if (StringUtils.hasText(templatePluginVersion)) {
            requireChoice(options.templateVersions(), templatePluginVersion, "templatePluginVersion");
        }

        ShopifyCompanionPackageProfileCompatibilityRuleSummary rule = options.compatibilityRules().stream()
            .filter(candidate -> candidate.packageKey().equalsIgnoreCase(packageKey) && candidate.tierKey().equalsIgnoreCase(tierKey))
            .findFirst()
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Package/tier combination is not approved for Shopify Companion profiles: " + packageKey + "/" + tierKey + "."
            ));

        requireAllowed(rule.allowedRuntimeProfileKeys(), runtimeProfileKey, "runtimeProfileKey", rule);
        requireAllowed(rule.allowedVectorProfileKeys(), vectorProfileKey, "vectorProfileKey", rule);
        requireAllowed(rule.allowedInferencePluginIds(), inferencePluginId, "inferencePluginId", rule);
        requireAllowed(rule.allowedVectorStrategies(), vectorStrategy, "vectorStrategy", rule);
        requireAllowed(rule.allowedVectorProvisioningModes(), vectorProvisioningMode, "vectorProvisioningMode", rule);
        requireAllowed(rule.allowedVectorStoragePostures(), vectorStoragePosture, "vectorStoragePosture", rule);
        requireAllowed(rule.allowedDeploymentTemplateIds(), deploymentTemplateId, "deploymentTemplateId", rule);
        requireAllowed(rule.allowedVerificationPackIds(), verificationPackId, "verificationPackId", rule);
    }

    private ShopifyCompanionPackageProfileCompatibilityRuleSummary compatibilityRule(PackageDefinition definition,
                                                                                     List<ShopifyCompanionPackageProfileEntity> profiles,
                                                                                     List<DeploymentTemplateSummary> templates) {
        List<ShopifyCompanionPackageProfileEntity> matchingProfiles = profiles.stream()
            .filter(profile -> definition.packageKey().equalsIgnoreCase(profile.getPackageKey()))
            .filter(profile -> definition.tierKey().equalsIgnoreCase(profile.getTierKey()))
            .toList();
        List<String> allowedVectorStrategies = union(definition.allowedVectorStrategies(), matchingProfiles.stream().map(ShopifyCompanionPackageProfileEntity::getVectorStrategy).toList());
        List<String> allowedDeploymentTemplates = union(
            List.of(definition.deploymentTemplateId(), bootstrapProperties.defaultTemplateId()),
            templates.stream()
                .filter(template -> containsIgnoreCase(allowedVectorStrategies, template.vectorStrategy()))
                .map(DeploymentTemplateSummary::id)
                .toList(),
            matchingProfiles.stream().map(ShopifyCompanionPackageProfileEntity::getDeploymentTemplateId).toList()
        );
        return new ShopifyCompanionPackageProfileCompatibilityRuleSummary(
            definition.packageKey(),
            definition.tierKey(),
            definition.costPosture(),
            definition.profileKey(),
            definition.runtimeProfileKey(),
            definition.vectorProfileKey(),
            definition.inferencePluginId(),
            definition.vectorStrategy(),
            definition.vectorProvisioningMode(),
            definition.vectorStoragePosture(),
            definition.deploymentTemplateId(),
            definition.verificationPackId(),
            union(definition.allowedRuntimeProfileKeys(), matchingProfiles.stream().map(ShopifyCompanionPackageProfileEntity::getRuntimeProfileKey).toList()),
            union(definition.allowedVectorProfileKeys(), matchingProfiles.stream().map(ShopifyCompanionPackageProfileEntity::getVectorProfileKey).toList()),
            union(definition.allowedInferencePluginIds(), matchingProfiles.stream().map(ShopifyCompanionPackageProfileEntity::getInferencePluginId).toList()),
            allowedVectorStrategies,
            union(definition.allowedVectorProvisioningModes(), matchingProfiles.stream().map(ShopifyCompanionPackageProfileEntity::getVectorProvisioningMode).toList()),
            union(definition.allowedVectorStoragePostures(), matchingProfiles.stream().map(ShopifyCompanionPackageProfileEntity::getVectorStoragePosture).toList()),
            allowedDeploymentTemplates,
            union(definition.allowedVerificationPackIds(), matchingProfiles.stream().map(ShopifyCompanionPackageProfileEntity::getVerificationPackId).toList())
        );
    }

    private List<PackageDefinition> packageDefinitions() {
        String templatePluginId = defaultText(bootstrapProperties.templatePluginId(), "mkp-template-shopify-companion");
        String templateVersion = defaultText(bootstrapProperties.templatePluginVersion(), "");
        String deploymentTemplateId = defaultText(bootstrapProperties.defaultTemplateId(), "dev-openai-qdrant");
        return List.of(
            new PackageDefinition(
                "FREE_LOW_COST",
                "Free / low cost shared Qdrant",
                "Entry package profile using shared inference and shared Qdrant.",
                "LOW_COST",
                "FREE",
                "FREE",
                "LOW_COST",
                "QDRANT_SHARED",
                "Low cost",
                "LOW",
                templatePluginId,
                templateVersion,
                deploymentTemplateId,
                "mkp-inference-shared-embeddings",
                "qdrant",
                "EXTERNAL_EXISTING",
                "SHARED",
                "starter-launch-readiness",
                List.of("LOW_COST"),
                List.of("QDRANT_SHARED"),
                List.of("mkp-inference-shared-embeddings"),
                List.of("qdrant"),
                List.of("EXTERNAL_EXISTING"),
                List.of("SHARED"),
                List.of("starter-launch-readiness"),
                "Shared vector storage for the free package."
            ),
            new PackageDefinition(
                "STARTER_BALANCED",
                "Starter / balanced shared Qdrant",
                "Starter profile for managed Shopify Companion launch readiness.",
                ShopifyCompanionPackageProfileCatalogService.DEFAULT_PROFILE_KEY,
                "STARTER",
                "STARTER",
                "BALANCED",
                "QDRANT_SHARED",
                "Balanced",
                "STANDARD",
                templatePluginId,
                templateVersion,
                deploymentTemplateId,
                "mkp-inference-shared-embeddings",
                "qdrant",
                "EXTERNAL_EXISTING",
                "SHARED",
                "shopify-companion-starter-readiness",
                List.of("BALANCED"),
                List.of("QDRANT_SHARED"),
                List.of("mkp-inference-shared-embeddings"),
                List.of("qdrant"),
                List.of("EXTERNAL_EXISTING"),
                List.of("SHARED"),
                List.of("starter-launch-readiness", "shopify-companion-starter-readiness"),
                "Shared Qdrant posture for Starter."
            ),
            new PackageDefinition(
                "ELITE_HIGH_QUALITY",
                "Elite / high quality shared Qdrant",
                "Elite Shopify Companion profile with premium inference and shared managed vector storage.",
                "HIGH_QUALITY",
                "ELITE",
                "ELITE",
                "HIGH_QUALITY",
                "QDRANT_SHARED",
                "High quality",
                "QUALITY",
                templatePluginId,
                templateVersion,
                deploymentTemplateId,
                "mkp-inference-premium-hybrid",
                "qdrant",
                "EXTERNAL_EXISTING",
                "SHARED",
                "shopify-companion-elite-readiness",
                List.of("HIGH_QUALITY", "ENTERPRISE_DEDICATED"),
                List.of("QDRANT_SHARED", "QDRANT_DEDICATED", "PINECONE_SHARED", "WEAVIATE_SHARED"),
                List.of("mkp-inference-premium-hybrid", "mkp-inference-shared-embeddings"),
                List.of("qdrant", "pinecone", "weaviate"),
                List.of("EXTERNAL_EXISTING", "PLATFORM_MANAGED"),
                List.of("SHARED", "DEDICATED"),
                List.of("shopify-companion-elite-readiness", "shopify-companion-verification"),
                "High quality profile using shared or dedicated managed vector options."
            ),
            new PackageDefinition(
                "ENTERPRISE_DEDICATED",
                "Enterprise / dedicated vector posture",
                "Enterprise package profile for dedicated vector isolation and higher quality inference.",
                "ENTERPRISE_DEDICATED",
                "ENTERPRISE",
                "ENTERPRISE",
                "ENTERPRISE_DEDICATED",
                "QDRANT_DEDICATED",
                "Enterprise dedicated",
                "ENTERPRISE",
                templatePluginId,
                templateVersion,
                deploymentTemplateId,
                "mkp-inference-premium-hybrid",
                "qdrant",
                "PLATFORM_MANAGED",
                "DEDICATED",
                "shopify-companion-elite-readiness",
                List.of("ENTERPRISE_DEDICATED", "HIGH_QUALITY"),
                List.of("QDRANT_DEDICATED", "ZILLIZ_DEDICATED", "PINECONE_SHARED", "WEAVIATE_SHARED"),
                List.of("mkp-inference-premium-hybrid", "mkp-inference-dedicated-embedding-worker"),
                List.of("qdrant", "milvus", "pinecone", "weaviate"),
                List.of("PLATFORM_MANAGED", "EXTERNAL_EXISTING"),
                List.of("DEDICATED", "SHARED"),
                List.of("shopify-companion-elite-readiness", "shopify-companion-verification"),
                "Dedicated vector posture for enterprise isolation."
            )
        );
    }

    private List<DeploymentTemplateSummary> safeDeploymentTemplates() {
        try {
            List<DeploymentTemplateSummary> templates = deploymentService.listTemplates();
            return templates == null ? List.of() : templates;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private List<MarketplacePluginSummary> safeMarketplacePlugins() {
        try {
            List<MarketplacePluginSummary> plugins = marketplaceCatalogService.listPlugins();
            return plugins == null ? List.of() : plugins;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private List<PlatformVerificationSuiteDefinitionSummary> safeVerificationSuites() {
        try {
            List<PlatformVerificationSuiteDefinitionSummary> suites = verificationSuiteService.listDefinitions();
            return suites == null ? List.of() : suites;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private ShopifyCompanionPackageProfileChoiceSummary pluginChoice(MarketplacePluginSummary plugin) {
        return choice(
            plugin.id(),
            plugin.displayName() + " (" + plugin.id() + ")",
            plugin.shortDescription(),
            SOURCE_MARKETPLACE,
            plugin.status(),
            true
        );
    }

    private boolean isTemplatePlugin(MarketplacePluginSummary plugin) {
        String type = defaultText(plugin.pluginType(), "").toUpperCase(Locale.ROOT);
        String haystack = (plugin.id() + " " + plugin.displayName()).toLowerCase(Locale.ROOT);
        return "TEMPLATE".equals(type)
            || haystack.contains("template")
            || haystack.contains("companion")
            || (plugin.contributions() != null && StringUtils.hasText(plugin.contributions().templateCuratedModuleId()));
    }

    private boolean isInferencePlugin(MarketplacePluginSummary plugin) {
        String type = defaultText(plugin.pluginType(), "").toUpperCase(Locale.ROOT);
        String haystack = (plugin.id() + " " + plugin.displayName()).toLowerCase(Locale.ROOT);
        return "INFERENCE_PROFILE".equals(type)
            || haystack.contains("inference")
            || (plugin.contributions() != null && (
                !safeList(plugin.contributions().inferenceProfileIds()).isEmpty()
                    || !safeList(plugin.contributions().inferenceEndpointProfileRefs()).isEmpty()
                    || !safeList(plugin.contributions().inferenceManagedServiceRefs()).isEmpty()
            ));
    }

    private boolean isShopifyVerificationSuite(PlatformVerificationSuiteDefinitionSummary suite) {
        String haystack = (suite.key() + " " + suite.label() + " " + suite.description()).toLowerCase(Locale.ROOT);
        return haystack.contains("shopify") || haystack.contains("companion");
    }

    private void requireChoice(List<ShopifyCompanionPackageProfileChoiceSummary> choices, String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        if (choices.stream().noneMatch(choice -> choice.value().equalsIgnoreCase(value))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " is not an approved Shopify Companion package profile choice: " + value + ".");
        }
    }

    private void requireAllowed(List<String> allowedValues,
                                String value,
                                String fieldName,
                                ShopifyCompanionPackageProfileCompatibilityRuleSummary rule) {
        if (!containsIgnoreCase(allowedValues, value)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                fieldName + " " + value + " is not approved for package/tier " + rule.packageKey() + "/" + rule.tierKey() + "."
            );
        }
    }

    private ShopifyCompanionPackageProfileChoiceSummary choice(String value,
                                                              String label,
                                                              String description,
                                                              String source,
                                                              String status,
                                                              boolean recommended) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return new ShopifyCompanionPackageProfileChoiceSummary(
            value.trim(),
            StringUtils.hasText(label) ? label.trim() : value.trim(),
            StringUtils.hasText(description) ? description.trim() : null,
            source,
            StringUtils.hasText(status) ? status.trim() : null,
            recommended,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of()
        );
    }

    private void put(Map<String, ShopifyCompanionPackageProfileChoiceSummary> choices,
                     ShopifyCompanionPackageProfileChoiceSummary choice) {
        if (choice == null || !StringUtils.hasText(choice.value())) {
            return;
        }
        String key = choice.value().toLowerCase(Locale.ROOT);
        ShopifyCompanionPackageProfileChoiceSummary existing = choices.get(key);
        if (existing == null || (!existing.recommended() && choice.recommended())) {
            choices.put(key, choice);
        }
    }

    private List<ShopifyCompanionPackageProfileChoiceSummary> values(Map<String, ShopifyCompanionPackageProfileChoiceSummary> choices) {
        return choices.values().stream()
            .sorted(Comparator
                .comparing(ShopifyCompanionPackageProfileChoiceSummary::recommended).reversed()
                .thenComparing(ShopifyCompanionPackageProfileChoiceSummary::value, String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    @SafeVarargs
    private List<String> union(Collection<String>... groups) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (Collection<String> group : groups) {
            if (group == null) {
                continue;
            }
            for (String value : group) {
                if (StringUtils.hasText(value)) {
                    values.add(value.trim());
                }
            }
        }
        return List.copyOf(values);
    }

    private boolean containsIgnoreCase(Collection<String> values, String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        return values.stream().anyMatch(candidate -> candidate.equalsIgnoreCase(value.trim()));
    }

    private List<String> safeList(List<String> values) {
        return values == null ? List.of() : values;
    }

    private String upper(String value) {
        return text(value).toUpperCase(Locale.ROOT);
    }

    private String lower(String value) {
        return text(value).toLowerCase(Locale.ROOT);
    }

    private String text(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private record PackageDefinition(
        String key,
        String label,
        String description,
        String profileKey,
        String packageKey,
        String tierKey,
        String runtimeProfileKey,
        String vectorProfileKey,
        String displayName,
        String costPosture,
        String templatePluginId,
        String templatePluginVersion,
        String deploymentTemplateId,
        String inferencePluginId,
        String vectorStrategy,
        String vectorProvisioningMode,
        String vectorStoragePosture,
        String verificationPackId,
        List<String> allowedRuntimeProfileKeys,
        List<String> allowedVectorProfileKeys,
        List<String> allowedInferencePluginIds,
        List<String> allowedVectorStrategies,
        List<String> allowedVectorProvisioningModes,
        List<String> allowedVectorStoragePostures,
        List<String> allowedVerificationPackIds,
        String vectorDescription
    ) {
        ShopifyCompanionPackageProfileBlueprintSummary toBlueprint() {
            return new ShopifyCompanionPackageProfileBlueprintSummary(
                key,
                label,
                description,
                profileKey,
                packageKey,
                tierKey,
                runtimeProfileKey,
                vectorProfileKey,
                displayName,
                costPosture,
                templatePluginId,
                templatePluginVersion,
                deploymentTemplateId,
                inferencePluginId,
                vectorStrategy,
                vectorProvisioningMode,
                vectorStoragePosture,
                verificationPackId
            );
        }
    }
}
