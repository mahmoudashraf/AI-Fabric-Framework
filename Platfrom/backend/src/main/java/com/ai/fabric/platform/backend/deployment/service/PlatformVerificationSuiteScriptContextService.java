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
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
public class PlatformVerificationSuiteScriptContextService {

    public static final String SCRIPT_PLATFORM_ADMIN_REGRESSION = "platform-admin-live-regression";
    public static final String SCRIPT_PLATFORM_CODE_REGRESSION = "platform-code-regression";
    public static final String SCRIPT_MANAGED_VECTOR_PROVIDER_VERIFICATION = "managed-vector-provider-verification";
    public static final String SCRIPT_MARKETPLACE_INSTALL_FLOW = "marketplace-install-flow";
    public static final String SCRIPT_SHOPIFY_COMPANION_VERIFICATION = "shopify-companion-verification";

    private static final String PLATFORM_OPERATOR_API_KEY_SECRET_NAME = "PLATFORM_OPERATOR_API_KEY";
    private static final String PLATFORM_ADMIN_API_KEY_SECRET_NAME = "PLATFORM_ADMIN_API_KEY";
    private static final List<String> PROVIDER_SECRET_NAMES = List.of(
        "PINECONE_API_KEY",
        "QDRANT_CLOUD_MANAGEMENT_API_KEY",
        "QDRANT_API_KEY",
        "ZILLIZ_CLOUD_API_KEY",
        "WEAVIATE_API_KEY"
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
        return switch (scriptKey) {
            case SCRIPT_PLATFORM_ADMIN_REGRESSION -> buildPlatformAdminRegression();
            case SCRIPT_PLATFORM_CODE_REGRESSION -> buildPlatformCodeRegression();
            case SCRIPT_MANAGED_VECTOR_PROVIDER_VERIFICATION -> buildManagedProviderVerification();
            case SCRIPT_MARKETPLACE_INSTALL_FLOW -> buildMarketplaceInstallFlow();
            case SCRIPT_SHOPIFY_COMPANION_VERIFICATION -> buildShopifyCompanionVerification();
            default -> throw new ResponseStatusException(BAD_REQUEST, "Unsupported verification suite script: " + scriptKey);
        };
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

        return new PlatformVerificationScriptContextSummary(
            "scripts/verify-platform-admin-regression.sh",
            environment,
            basePlatformSecretEnvironment()
        );
    }

    private PlatformVerificationScriptContextSummary buildPlatformCodeRegression() {
        Map<String, String> environment = new LinkedHashMap<>();
        environment.put("BACKEND_TESTS", "true");
        environment.put("PRODUCT_TESTS", "true");
        environment.put("INFRASTRUCTURE_TESTS", "true");
        environment.put("UI_BUILD", "true");
        environment.put("SHELL_SYNTAX_CHECKS", "true");

        return new PlatformVerificationScriptContextSummary(
            "scripts/verify-platform-code-regression.sh",
            environment,
            Map.of(),
            suiteProperties.codeRegressionScriptTimeout(),
            suiteProperties.codeRegressionMaxLogCharacters()
        );
    }

    private PlatformVerificationScriptContextSummary buildManagedProviderVerification() {
        String weaviateHost = requireValue(
            suiteProperties.weaviateHost(),
            "platform.verification.suites.weaviate-host must be configured for managed provider verification."
        );

        Map<String, String> environment = new LinkedHashMap<>();
        environment.put("WEAVIATE_HOST", weaviateHost);

        Map<String, String> secretEnvironment = new LinkedHashMap<>();
        for (String secretName : PROVIDER_SECRET_NAMES) {
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

    private Map<String, String> basePlatformEnvironment() {
        Map<String, String> environment = new LinkedHashMap<>();
        environment.put("PLATFORM_BASE_URL", requireValue(
            deliveryProperties.publicBaseUrl(),
            "platform.delivery.public-base-url must be configured for verification suite scripts."
        ));
        return environment;
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

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
