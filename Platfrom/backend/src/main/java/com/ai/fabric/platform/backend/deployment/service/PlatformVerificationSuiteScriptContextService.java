package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.config.PlatformAuthProperties;
import com.ai.fabric.platform.backend.config.PlatformDeliveryProperties;
import com.ai.fabric.platform.backend.config.PlatformVerificationSuiteProperties;
import com.ai.fabric.platform.backend.deployment.model.DeploymentVerificationRolloutItemSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentVerificationRolloutSummary;
import com.ai.fabric.platform.backend.deployment.model.PlatformVerificationScriptContextSummary;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
public class PlatformVerificationSuiteScriptContextService {

    public static final String SCRIPT_PLATFORM_ADMIN_REGRESSION = "platform-admin-live-regression";
    public static final String SCRIPT_MANAGED_VECTOR_PROVIDER_VERIFICATION = "managed-vector-provider-verification";
    public static final String SCRIPT_MARKETPLACE_INSTALL_FLOW = "marketplace-install-flow";
    public static final String SCRIPT_SHOPIFY_COMPANION_VERIFICATION = "shopify-companion-verification";
    public static final String SCRIPT_SHOPIFY_FIRST_PRODUCT_READINESS_AUDIT = "shopify-first-product-readiness-audit";
    public static final String SCRIPT_PARTNER_ENABLEMENT_VERIFICATION = "partner-enablement-verification";
    public static final String SCRIPT_THINKER_RESOLVER_READINESS = "thinker-resolver-readiness";

    private static final String PLATFORM_OPERATOR_API_KEY_SECRET_NAME = "PLATFORM_OPERATOR_API_KEY";
    private static final String PLATFORM_ADMIN_API_KEY_SECRET_NAME = "PLATFORM_ADMIN_API_KEY";
    private static final String PARTNER_SUPABASE_JWT_SECRET_NAME = "PARTNER_SUPABASE_JWT";
    private static final List<String> RELEASE_BLOCKING_PROVIDER_SECRET_NAMES = List.of(
        "QDRANT_CLOUD_MANAGEMENT_API_KEY",
        "QDRANT_API_KEY"
    );
    private static final List<String> SHOPIFY_OPTIONAL_SECRET_NAMES = List.of(
        "SHOPIFY_BRIDGE_ADMIN_API_KEY",
        "SHOPIFY_ADMIN_ACCESS_TOKEN",
        "SHOPIFY_MERCHANT_AUTHORIZATION"
    );

    private final PlatformVerificationSuiteProperties suiteProperties;
    private final PlatformDeliveryProperties deliveryProperties;
    private final PlatformAuthProperties platformAuthProperties;
    private final PlatformSecretService platformSecretService;
    private final DeploymentVerificationRolloutService deploymentVerificationRolloutService;

    public PlatformVerificationSuiteScriptContextService(PlatformVerificationSuiteProperties suiteProperties,
                                                         PlatformDeliveryProperties deliveryProperties,
                                                         PlatformAuthProperties platformAuthProperties,
                                                         PlatformSecretService platformSecretService,
                                                         DeploymentVerificationRolloutService deploymentVerificationRolloutService) {
        this.suiteProperties = suiteProperties;
        this.deliveryProperties = deliveryProperties;
        this.platformAuthProperties = platformAuthProperties;
        this.platformSecretService = platformSecretService;
        this.deploymentVerificationRolloutService = deploymentVerificationRolloutService;
    }

    public PlatformVerificationScriptContextSummary build(String scriptKey) {
        return build(scriptKey, Map.of());
    }

    public PlatformVerificationScriptContextSummary build(String scriptKey, Map<String, String> environmentOverrides) {
        PlatformVerificationScriptContextSummary base = switch (scriptKey) {
            case SCRIPT_PLATFORM_ADMIN_REGRESSION -> buildPlatformAdminRegression();
            case SCRIPT_MANAGED_VECTOR_PROVIDER_VERIFICATION -> buildManagedProviderVerification();
            case SCRIPT_MARKETPLACE_INSTALL_FLOW -> buildMarketplaceInstallFlow();
            case SCRIPT_SHOPIFY_COMPANION_VERIFICATION -> buildShopifyCompanionVerification();
            case SCRIPT_SHOPIFY_FIRST_PRODUCT_READINESS_AUDIT -> buildShopifyFirstProductReadinessAudit();
            case SCRIPT_PARTNER_ENABLEMENT_VERIFICATION -> buildPartnerEnablementVerification();
            case SCRIPT_THINKER_RESOLVER_READINESS -> buildThinkerResolverReadiness();
            default -> throw new ResponseStatusException(BAD_REQUEST, "Unsupported verification suite script: " + scriptKey);
        };
        if (environmentOverrides == null || environmentOverrides.isEmpty()) {
            return base;
        }
        Map<String, String> environment = new LinkedHashMap<>(base.environment());
        environmentOverrides.forEach((key, value) -> {
            if (key != null && !key.isBlank() && value != null && !value.isBlank()) {
                environment.put(key.trim(), value.trim());
            }
        });
        if (SCRIPT_PARTNER_ENABLEMENT_VERIFICATION.equals(scriptKey)) {
            environment.put("PARTNER_LIVE_STRICT", "true");
        }
        return new PlatformVerificationScriptContextSummary(
            base.scriptPath(),
            Map.copyOf(environment),
            base.secretEnvironment()
        );
    }

    private PlatformVerificationScriptContextSummary buildPlatformAdminRegression() {
        String platformUiBaseUrl = requireValue(
            suiteProperties.platformUiBaseUrl(),
            "platform.verification.suites.platform-ui-base-url must be configured for platform admin regression."
        );
        String adminTargetDeploymentId = resolveAdminTargetDeploymentId();

        Map<String, String> environment = basePlatformEnvironment();
        environment.put("PLATFORM_UI_BASE_URL", platformUiBaseUrl);
        environment.put("ADMIN_TARGET_DEPLOYMENT_ID", adminTargetDeploymentId);
        environment.put("VERIFY_CANONICAL_ROLLOUT_MUTATION", "false");
        environment.put("VERIFY_INFERENCE_SERVICE_ADMIN_MUTATION", "false");
        environment.put("INFERENCE_SERVICE_REF", PlatformVerificationSuiteCatalog.SHARED_INFERENCE_SERVICE_REF);

        return new PlatformVerificationScriptContextSummary(
            "scripts/verify-platform-admin-regression.sh",
            environment,
            basePlatformSecretEnvironment()
        );
    }

    private PlatformVerificationScriptContextSummary buildManagedProviderVerification() {
        Map<String, String> environment = new LinkedHashMap<>();
        environment.put("RUN_PINECONE", "false");
        environment.put("RUN_QDRANT", "true");
        environment.put("RUN_ZILLIZ", "false");
        environment.put("RUN_WEAVIATE", "false");
        environment.put("QDRANT_EXISTING_CLUSTER_NAME", "cluster");
        environment.put("QDRANT_CREATE_EPHEMERAL_DB_KEY", "false");
        environment.put("QDRANT_CREATE_EPHEMERAL_CLUSTER", "false");

        Map<String, String> secretEnvironment = new LinkedHashMap<>();
        for (String secretName : RELEASE_BLOCKING_PROVIDER_SECRET_NAMES) {
            String value = platformSecretService.resolveSecret(secretName);
            if (value == null || value.isBlank()) {
                throw new ResponseStatusException(BAD_REQUEST, "Missing required platform secret for provider verification: " + secretName);
            }
            secretEnvironment.put(secretName, value);
        }

        return new PlatformVerificationScriptContextSummary(
            "scripts/verify-managed-vector-providers.sh",
            environment,
            secretEnvironment
        );
    }

    private PlatformVerificationScriptContextSummary buildMarketplaceInstallFlow() {
        return new PlatformVerificationScriptContextSummary(
            "scripts/verify-marketplace-install-flow.sh",
            basePlatformEnvironment(),
            basePlatformSecretEnvironment()
        );
    }

    private PlatformVerificationScriptContextSummary buildShopifyCompanionVerification() {
        String bridgeBaseUrl = requireValue(
            suiteProperties.shopifyBridgeBaseUrl(),
            "platform.verification.suites.shopify-bridge-base-url must be configured for Shopify verification."
        );
        String shopDomain = requireValue(
            suiteProperties.shopifyShopDomain(),
            "platform.verification.suites.shopify-shop-domain must be configured for Shopify verification."
        );

        Map<String, String> environment = basePlatformEnvironment();
        environment.put("SHOPIFY_BRIDGE_BASE_URL", bridgeBaseUrl);
        environment.put("SHOP_DOMAIN", shopDomain);
        if (suiteProperties.shopifyProductServiceRef() != null && !suiteProperties.shopifyProductServiceRef().isBlank()) {
            environment.put("PRODUCT_SERVICE_REF", suiteProperties.shopifyProductServiceRef());
        }
        if (suiteProperties.shopifyEmbeddedHost() != null && !suiteProperties.shopifyEmbeddedHost().isBlank()) {
            environment.put("SHOPIFY_EMBEDDED_HOST", suiteProperties.shopifyEmbeddedHost());
        }

        Map<String, String> secretEnvironment = new LinkedHashMap<>(basePlatformSecretEnvironment());
        for (String secretName : SHOPIFY_OPTIONAL_SECRET_NAMES) {
            String value = platformSecretService.resolveSecret(secretName);
            if (value != null && !value.isBlank()) {
                secretEnvironment.put(secretName, value);
            }
        }

        return new PlatformVerificationScriptContextSummary(
            "scripts/verify-shopify-companion.sh",
            environment,
            secretEnvironment
        );
    }

    private PlatformVerificationScriptContextSummary buildShopifyFirstProductReadinessAudit() {
        String bridgeBaseUrl = requireValue(
            suiteProperties.shopifyBridgeBaseUrl(),
            "platform.verification.suites.shopify-bridge-base-url must be configured for Shopify first-product readiness audit."
        );
        String shopDomain = requireValue(
            suiteProperties.shopifyShopDomain(),
            "platform.verification.suites.shopify-shop-domain must be configured for Shopify first-product readiness audit."
        );

        Map<String, String> environment = basePlatformEnvironment();
        environment.put("SHOPIFY_BRIDGE_BASE_URL", bridgeBaseUrl);
        environment.put("SHOP_DOMAIN", shopDomain);
        environment.put("READINESS_AUDIT_LOCAL_GATES", "false");
        if (suiteProperties.shopifyProductServiceRef() != null && !suiteProperties.shopifyProductServiceRef().isBlank()) {
            environment.put("PRODUCT_SERVICE_REF", suiteProperties.shopifyProductServiceRef());
        }
        if (suiteProperties.shopifyEmbeddedHost() != null && !suiteProperties.shopifyEmbeddedHost().isBlank()) {
            environment.put("SHOPIFY_EMBEDDED_HOST", suiteProperties.shopifyEmbeddedHost());
        }

        Map<String, String> secretEnvironment = new LinkedHashMap<>(basePlatformSecretEnvironment());
        for (String secretName : SHOPIFY_OPTIONAL_SECRET_NAMES) {
            String value = platformSecretService.resolveSecret(secretName);
            if (value != null && !value.isBlank()) {
                secretEnvironment.put(secretName, value);
            }
        }

        return new PlatformVerificationScriptContextSummary(
            "scripts/verify-shopify-first-product-readiness-audit.sh",
            environment,
            secretEnvironment
        );
    }

    private PlatformVerificationScriptContextSummary buildPartnerEnablementVerification() {
        String partnerUiBaseUrl = requireValue(
            suiteProperties.partnerUiBaseUrl(),
            "platform.verification.suites.partner-ui-base-url must be configured for Partner Enablement verification."
        );
        String partnerSupabaseJwt = requireSecret(
            PARTNER_SUPABASE_JWT_SECRET_NAME,
            "Missing required platform secret for Partner Enablement verification: " + PARTNER_SUPABASE_JWT_SECRET_NAME
        );

        Map<String, String> environment = basePlatformEnvironment();
        environment.put("PARTNER_UI_BASE_URL", partnerUiBaseUrl);
        environment.put("PARTNER_LIVE_STRICT", "true");
        if (suiteProperties.shopifyShopDomain() != null && !suiteProperties.shopifyShopDomain().isBlank()) {
            environment.put("PARTNER_LIVE_SHOP_DOMAIN", suiteProperties.shopifyShopDomain().trim());
        }

        Map<String, String> secretEnvironment = new LinkedHashMap<>();
        String merchantAccessApiKey = resolvePartnerMerchantAccessApiKey();
        if (merchantAccessApiKey != null) {
            secretEnvironment.put("PLATFORM_API_KEY", merchantAccessApiKey);
        } else {
            secretEnvironment.putAll(basePlatformSecretEnvironment());
        }
        secretEnvironment.put(PARTNER_SUPABASE_JWT_SECRET_NAME, partnerSupabaseJwt);

        return new PlatformVerificationScriptContextSummary(
            "scripts/verify-partner-enablement-live.sh",
            environment,
            secretEnvironment
        );
    }

    private PlatformVerificationScriptContextSummary buildThinkerResolverReadiness() {
        String platformUiBaseUrl = requireValue(
            suiteProperties.platformUiBaseUrl(),
            "platform.verification.suites.platform-ui-base-url must be configured for Thinker Resolver readiness."
        );
        String partnerUiBaseUrl = requireValue(
            suiteProperties.partnerUiBaseUrl(),
            "platform.verification.suites.partner-ui-base-url must be configured for Thinker Resolver readiness."
        );
        String shopDomain = requireValue(
            suiteProperties.shopifyShopDomain(),
            "platform.verification.suites.shopify-shop-domain must be configured for Thinker Resolver readiness."
        );
        String partnerSupabaseJwt = requireSecret(
            PARTNER_SUPABASE_JWT_SECRET_NAME,
            "Missing required platform secret for Thinker Resolver readiness: " + PARTNER_SUPABASE_JWT_SECRET_NAME
        );

        Map<String, String> environment = basePlatformEnvironment();
        environment.put("PLATFORM_UI_BASE_URL", platformUiBaseUrl);
        environment.put("PARTNER_UI_BASE_URL", partnerUiBaseUrl);
        environment.put("THINKER_SHOP_DOMAIN", shopDomain);
        environment.put("THINKER_DEPLOYMENT_ID", resolveAdminTargetDeploymentId());
        environment.put("THINKER_EXECUTE_LOW_RISK", "false");
        environment.put("THINKER_REQUIRE_PARTNER_PROOF", "true");

        Map<String, String> secretEnvironment = new LinkedHashMap<>(basePlatformSecretEnvironment());
        secretEnvironment.put(PARTNER_SUPABASE_JWT_SECRET_NAME, partnerSupabaseJwt);

        return new PlatformVerificationScriptContextSummary(
            "scripts/verify-thinker-resolver-readiness.sh",
            environment,
            secretEnvironment
        );
    }

    private Map<String, String> basePlatformEnvironment() {
        Map<String, String> environment = new LinkedHashMap<>();
        environment.put("PLATFORM_BASE_URL", requireValue(
            deliveryProperties.publicBaseUrl(),
            "platform.delivery.public-base-url must be configured for verification suite scripts."
        ));
        return environment;
    }

    private String resolvePartnerMerchantAccessApiKey() {
        String productServiceRef = trimToNull(suiteProperties.shopifyProductServiceRef());
        if (productServiceRef == null) {
            return null;
        }
        String secretName = "MANAGED_PRODUCT_"
            + productServiceRef.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_")
            + "_API_KEY";
        return trimToNull(platformSecretService.resolveSecret(secretName));
    }

    private Map<String, String> basePlatformSecretEnvironment() {
        Map<String, String> secretEnvironment = new LinkedHashMap<>();
        String automationApiKey = resolveAutomationApiKey();
        if (automationApiKey != null && platformAuthProperties.apiKeyEnabled()) {
            secretEnvironment.put("PLATFORM_API_KEY", automationApiKey);
            return secretEnvironment;
        }
        if (platformAuthProperties.bootstrapAdminEnabled()
            && platformAuthProperties.bootstrapAdminEmail() != null
            && !platformAuthProperties.bootstrapAdminEmail().isBlank()
            && platformAuthProperties.bootstrapAdminPassword() != null
            && !platformAuthProperties.bootstrapAdminPassword().isBlank()) {
            secretEnvironment.put("PLATFORM_LOGIN_EMAIL", platformAuthProperties.bootstrapAdminEmail().trim());
            secretEnvironment.put("PLATFORM_LOGIN_PASSWORD", platformAuthProperties.bootstrapAdminPassword().trim());
            return secretEnvironment;
        }
        throw new ResponseStatusException(
            BAD_REQUEST,
            "Platform verification suite scripts require PLATFORM_ADMIN_API_KEY / PLATFORM_OPERATOR_API_KEY or bootstrap admin login."
        );
    }

    private String resolveAutomationApiKey() {
        String admin = trimToNull(platformAuthProperties.adminApiKey());
        if (admin != null) {
            return admin;
        }
        admin = trimToNull(platformSecretService.resolveSecret(PLATFORM_ADMIN_API_KEY_SECRET_NAME));
        if (admin != null) {
            return admin;
        }
        String operator = trimToNull(platformAuthProperties.operatorApiKey());
        if (operator != null) {
            return operator;
        }
        return trimToNull(platformSecretService.resolveSecret(PLATFORM_OPERATOR_API_KEY_SECRET_NAME));
    }

    private String resolveAdminTargetDeploymentId() {
        DeploymentVerificationRolloutSummary summary = deploymentVerificationRolloutService.listRollouts();
        return summary.items().stream()
            .filter(item -> "ecommerce".equalsIgnoreCase(item.key()) || "qdrant".equalsIgnoreCase(item.key()))
            .filter(item -> item.exists() && !item.archived() && item.deploymentId() != null && !item.deploymentId().isBlank())
            .sorted((left, right) -> {
                if ("ecommerce".equalsIgnoreCase(left.key())) {
                    return -1;
                }
                if ("ecommerce".equalsIgnoreCase(right.key())) {
                    return 1;
                }
                return left.key().compareToIgnoreCase(right.key());
            })
            .map(DeploymentVerificationRolloutItemSummary::deploymentId)
            .findFirst()
            .orElseThrow(() -> new ResponseStatusException(
                BAD_REQUEST,
                "Platform admin regression requires a canonical ecommerce or qdrant deployment."
            ));
    }

    private String requireValue(String value, String message) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new ResponseStatusException(BAD_REQUEST, message);
        }
        return normalized;
    }

    private String requireSecret(String secretName, String message) {
        String normalized = trimToNull(platformSecretService.resolveSecret(secretName));
        if (normalized == null) {
            throw new ResponseStatusException(BAD_REQUEST, message);
        }
        return normalized;
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
