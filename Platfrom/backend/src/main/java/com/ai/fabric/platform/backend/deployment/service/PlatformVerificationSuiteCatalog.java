package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.model.PlatformVerificationSuiteDefinitionSummary;
import com.ai.fabric.platform.backend.deployment.model.PlatformVerificationSuiteStageDefinitionSummary;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Component
public class PlatformVerificationSuiteCatalog {

    public static final String FULL_PLATFORM_RELEASE_READINESS_SUITE_KEY = "full-platform-release-readiness";
    public static final String CANONICAL_RELEASE_READINESS_SUITE_KEY = "canonical-release-readiness";
    public static final String PLATFORM_ADMIN_LIVE_REGRESSION_SUITE_KEY = "platform-admin-live-regression";
    public static final String MANAGED_VECTOR_PROVIDER_VERIFICATION_SUITE_KEY = "managed-vector-provider-verification";
    public static final String MARKETPLACE_INSTALL_FLOW_SUITE_KEY = "marketplace-install-flow";
    public static final String SHOPIFY_COMPANION_VERIFICATION_SUITE_KEY = "shopify-companion-verification";
    public static final String SHOPIFY_MCP_GATEWAY_VERIFICATION_SUITE_KEY = "shopify-mcp-gateway-verification";
    public static final String SHOPIFY_FIRST_PRODUCT_READINESS_AUDIT_SUITE_KEY = "shopify-first-product-readiness-audit";
    public static final String PARTNER_ENABLEMENT_VERIFICATION_SUITE_KEY = "partner-enablement-verification";
    public static final String THINKER_RESOLVER_READINESS_SUITE_KEY = "thinker-resolver-readiness";
    public static final String COOLIFY_PROVIDER_VERIFICATION_SUITE_KEY = "coolify-provider-verification";
    public static final String SHARED_INFERENCE_SERVICE_REF = "openai-cloud-orchestration";
    public static final String CANONICAL_FLEET_TARGET_REF = "canonical-verification-fleet";
    public static final List<String> CANONICAL_ROLLOUT_ORDER = List.of(
        "marketplace",
        "ecommerce"
    );

    public List<PlatformVerificationSuiteDefinitionSummary> listDefinitions() {
        return List.of(
            fullPlatformReleaseReadiness(),
            canonicalReleaseReadiness(),
            platformAdminLiveRegression(),
            managedVectorProviderVerification(),
            marketplaceInstallFlowVerification(),
            shopifyCompanionVerification(),
            shopifyMcpGatewayVerification(),
            shopifyFirstProductReadinessAudit(),
            partnerEnablementVerification(),
            thinkerResolverReadiness(),
            coolifyProviderVerification()
        );
    }

    public PlatformVerificationSuiteDefinitionSummary requireDefinition(String suiteKey) {
        return listDefinitions().stream()
            .filter(definition -> definition.key().equalsIgnoreCase(suiteKey))
            .findFirst()
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Verification suite not found: " + suiteKey));
    }

    private PlatformVerificationSuiteDefinitionSummary canonicalReleaseReadiness() {
        return new PlatformVerificationSuiteDefinitionSummary(
            CANONICAL_RELEASE_READINESS_SUITE_KEY,
            "Canonical release readiness",
            "Release-blocking platform suite that verifies shared inference health, canonical rollout readiness, and the hosted deployment verification fleet in fixed order.",
            true,
            List.of(
                new PlatformVerificationSuiteStageDefinitionSummary(
                    "shared-inference-health",
                    "Shared inference service health",
                    "INFERENCE_SERVICE_HEALTH",
                    SHARED_INFERENCE_SERVICE_REF,
                    true,
                    "Verify the shared orchestration inference service is healthy before any rollout verification begins."
                ),
                new PlatformVerificationSuiteStageDefinitionSummary(
                    "canonical-rollout-inventory",
                    "Canonical rollout inventory",
                    "CANONICAL_ROLLOUTS",
                    CANONICAL_FLEET_TARGET_REF,
                    true,
                    "Resolve canonical verification deployments, confirm they are present, and validate platform-visible secret readiness."
                ),
                new PlatformVerificationSuiteStageDefinitionSummary(
                    "marketplace-hosted-verification",
                    "Marketplace hosted verification",
                    "HOSTED_DEPLOYMENT_VERIFICATION",
                    "marketplace",
                    true,
                    "Run the marketplace-runtime hosted verification against the canonical marketplace deployment."
                ),
                new PlatformVerificationSuiteStageDefinitionSummary(
                    "ecommerce-hosted-verification",
                    "Ecommerce hosted verification",
                    "HOSTED_DEPLOYMENT_VERIFICATION",
                    "ecommerce",
                    true,
                    "Run the ecommerce hosted verification against the canonical ecommerce deployment."
                ),
                new PlatformVerificationSuiteStageDefinitionSummary(
                    "qdrant-hosted-verification",
                    "Qdrant hosted verification (optional)",
                    "HOSTED_DEPLOYMENT_VERIFICATION",
                    "qdrant",
                    false,
                    "Optional advanced-provider verification for the canonical Qdrant deployment. Qdrant is supported but no longer a release-blocking default."
                )
            )
        );
    }

    private PlatformVerificationSuiteDefinitionSummary fullPlatformReleaseReadiness() {
        return new PlatformVerificationSuiteDefinitionSummary(
            FULL_PLATFORM_RELEASE_READINESS_SUITE_KEY,
            "Full platform release readiness",
            "Primary release-blocking suite that replaces the current live verification estate with one ordered control-plane run.",
            true,
            List.of(
                new PlatformVerificationSuiteStageDefinitionSummary(
                    "shared-inference-health",
                    "Shared inference service health",
                    "INFERENCE_SERVICE_HEALTH",
                    SHARED_INFERENCE_SERVICE_REF,
                    true,
                    "Verify the shared orchestration inference service is healthy before any rollout verification begins."
                ),
                new PlatformVerificationSuiteStageDefinitionSummary(
                    "platform-admin-live-regression",
                    "Platform admin live regression",
                    "SCRIPT_VERIFICATION",
                    PlatformVerificationSuiteScriptContextService.SCRIPT_PLATFORM_ADMIN_REGRESSION,
                    true,
                    "Run the platform admin live regression script from the control plane."
                ),
                new PlatformVerificationSuiteStageDefinitionSummary(
                    "canonical-rollout-inventory",
                    "Canonical rollout inventory",
                    "CANONICAL_ROLLOUTS",
                    CANONICAL_FLEET_TARGET_REF,
                    true,
                    "Resolve canonical verification deployments, confirm they are present, and validate platform-visible secret readiness."
                ),
                new PlatformVerificationSuiteStageDefinitionSummary(
                    "managed-vector-provider-verification",
                    "Managed vector provider verification",
                    "SCRIPT_VERIFICATION",
                    PlatformVerificationSuiteScriptContextService.SCRIPT_MANAGED_VECTOR_PROVIDER_VERIFICATION,
                    true,
                    "Run direct provider verification for the release-blocking managed vector provider."
                ),
                new PlatformVerificationSuiteStageDefinitionSummary(
                    "coolify-provider-verification",
                    "Coolify provider verification",
                    "SCRIPT_VERIFICATION",
                    PlatformVerificationSuiteScriptContextService.SCRIPT_COOLIFY_PROVIDER_VERIFICATION,
                    true,
                    "Verify Coolify API tokens and live host health before Platform-managed product-service checks."
                ),
                new PlatformVerificationSuiteStageDefinitionSummary(
                    "marketplace-install-flow",
                    "Marketplace install flow",
                    "SCRIPT_VERIFICATION",
                    PlatformVerificationSuiteScriptContextService.SCRIPT_MARKETPLACE_INSTALL_FLOW,
                    true,
                    "Verify template bootstrap, plugin install, publish, apply, and retrieval evidence for the marketplace install flow."
                ),
                new PlatformVerificationSuiteStageDefinitionSummary(
                    "shopify-companion-verification",
                    "Shopify Companion verification",
                    "SCRIPT_VERIFICATION",
                    PlatformVerificationSuiteScriptContextService.SCRIPT_SHOPIFY_COMPANION_VERIFICATION,
                    true,
                    "Verify the shared Shopify Bridge service, store binding, storefront bootstrap, and shopper query path."
                ),
                new PlatformVerificationSuiteStageDefinitionSummary(
                    "shopify-mcp-gateway-verification",
                    "Shopify MCP Gateway verification",
                    "SCRIPT_VERIFICATION",
                    PlatformVerificationSuiteScriptContextService.SCRIPT_SHOPIFY_MCP_GATEWAY_VERIFICATION,
                    true,
                    "Verify managed MCP Gateway auth, Marketplace MCP discovery, direct gateway execution, and Shopify Bridge delegated MCP evidence."
                ),
                new PlatformVerificationSuiteStageDefinitionSummary(
                    "shopify-first-product-readiness-audit",
                    "Shopify first-product readiness audit",
                    "SCRIPT_VERIFICATION",
                    PlatformVerificationSuiteScriptContextService.SCRIPT_SHOPIFY_FIRST_PRODUCT_READINESS_AUDIT,
                    true,
                    "Run the Shopify Companion first-product readiness audit, including product truth, live verification, and query-to-answer quality."
                ),
                new PlatformVerificationSuiteStageDefinitionSummary(
                    "partner-enablement-verification",
                    "Partner Enablement verification",
                    "SCRIPT_VERIFICATION",
                    PlatformVerificationSuiteScriptContextService.SCRIPT_PARTNER_ENABLEMENT_VERIFICATION,
                    true,
                    "Verify Partner Enablement backend auth, deployed Partner UI routing, merchant approval/revoke flow, and persisted partner workflow evidence."
                ),
                new PlatformVerificationSuiteStageDefinitionSummary(
                    "thinker-resolver-readiness",
                    "Thinker Resolver readiness",
                    "SCRIPT_VERIFICATION",
                    PlatformVerificationSuiteScriptContextService.SCRIPT_THINKER_RESOLVER_READINESS,
                    true,
                    "Verify Thinker session persistence, evidence export, Resolver policy, dry-run, optional low-risk execution, partner redaction, and Shopify Thinker health."
                ),
                new PlatformVerificationSuiteStageDefinitionSummary(
                    "marketplace-hosted-verification",
                    "Marketplace hosted verification",
                    "HOSTED_DEPLOYMENT_VERIFICATION",
                    "marketplace",
                    true,
                    "Run the marketplace-runtime hosted verification against the canonical marketplace deployment."
                ),
                new PlatformVerificationSuiteStageDefinitionSummary(
                    "ecommerce-hosted-verification",
                    "Ecommerce hosted verification",
                    "HOSTED_DEPLOYMENT_VERIFICATION",
                    "ecommerce",
                    true,
                    "Run the ecommerce hosted verification against the canonical ecommerce deployment."
                ),
                new PlatformVerificationSuiteStageDefinitionSummary(
                    "qdrant-hosted-verification",
                    "Qdrant hosted verification (optional)",
                    "HOSTED_DEPLOYMENT_VERIFICATION",
                    "qdrant",
                    false,
                    "Optional advanced-provider verification for the canonical Qdrant deployment. Qdrant is supported but no longer a release-blocking default."
                )
            )
        );
    }

    private PlatformVerificationSuiteDefinitionSummary platformAdminLiveRegression() {
        return new PlatformVerificationSuiteDefinitionSummary(
            PLATFORM_ADMIN_LIVE_REGRESSION_SUITE_KEY,
            "Platform admin live regression",
            "Standalone platform-admin regression suite for auth, user directory, inference admin, rollout inventory, and destructive smoke cleanup.",
            false,
            List.of(
                new PlatformVerificationSuiteStageDefinitionSummary(
                    "platform-admin-live-regression",
                    "Platform admin live regression",
                    "SCRIPT_VERIFICATION",
                    PlatformVerificationSuiteScriptContextService.SCRIPT_PLATFORM_ADMIN_REGRESSION,
                    true,
                    "Run the platform admin live regression script from the control plane."
                )
            )
        );
    }

    private PlatformVerificationSuiteDefinitionSummary managedVectorProviderVerification() {
        return new PlatformVerificationSuiteDefinitionSummary(
            MANAGED_VECTOR_PROVIDER_VERIFICATION_SUITE_KEY,
            "Managed vector provider verification",
            "Standalone direct verification of Pinecone, Qdrant Cloud, Zilliz Cloud, and Weaviate using platform-managed secrets.",
            false,
            List.of(
                new PlatformVerificationSuiteStageDefinitionSummary(
                    "managed-vector-provider-verification",
                    "Managed vector provider verification",
                    "SCRIPT_VERIFICATION",
                    PlatformVerificationSuiteScriptContextService.SCRIPT_MANAGED_VECTOR_PROVIDER_VERIFICATION,
                    true,
                    "Run direct provider verification for Pinecone, Qdrant Cloud, Zilliz Cloud, and Weaviate."
                )
            )
        );
    }

    private PlatformVerificationSuiteDefinitionSummary marketplaceInstallFlowVerification() {
        return new PlatformVerificationSuiteDefinitionSummary(
            MARKETPLACE_INSTALL_FLOW_SUITE_KEY,
            "Marketplace install flow",
            "Standalone marketplace install-flow verification for template bootstrap, plugin install, publish, apply, and retrieval evidence.",
            false,
            List.of(
                new PlatformVerificationSuiteStageDefinitionSummary(
                    "marketplace-install-flow",
                    "Marketplace install flow",
                    "SCRIPT_VERIFICATION",
                    PlatformVerificationSuiteScriptContextService.SCRIPT_MARKETPLACE_INSTALL_FLOW,
                    true,
                    "Verify template bootstrap, plugin install, publish, apply, and retrieval evidence for the marketplace install flow."
                )
            )
        );
    }

    private PlatformVerificationSuiteDefinitionSummary shopifyCompanionVerification() {
        return new PlatformVerificationSuiteDefinitionSummary(
            SHOPIFY_COMPANION_VERIFICATION_SUITE_KEY,
            "Shopify Companion verification",
            "Standalone Shopify Companion verification for the shared bridge, store binding, storefront bootstrap, and shopper query flow.",
            false,
            List.of(
                new PlatformVerificationSuiteStageDefinitionSummary(
                    "shopify-companion-verification",
                    "Shopify Companion verification",
                    "SCRIPT_VERIFICATION",
                    PlatformVerificationSuiteScriptContextService.SCRIPT_SHOPIFY_COMPANION_VERIFICATION,
                    true,
                    "Verify the shared Shopify Bridge service, store binding, storefront bootstrap, and shopper query path."
                )
            )
        );
    }

    private PlatformVerificationSuiteDefinitionSummary shopifyMcpGatewayVerification() {
        return new PlatformVerificationSuiteDefinitionSummary(
            SHOPIFY_MCP_GATEWAY_VERIFICATION_SUITE_KEY,
            "Shopify MCP Gateway verification",
            "Standalone Plan 009 release gate for Marketplace MCP discovery, managed MCP Gateway auth, Shopify MCP tools/list/tools/call, and Bridge-delegated mcp-tool evidence.",
            false,
            List.of(
                new PlatformVerificationSuiteStageDefinitionSummary(
                    "shopify-mcp-gateway-verification",
                    "Shopify MCP Gateway verification",
                    "SCRIPT_VERIFICATION",
                    PlatformVerificationSuiteScriptContextService.SCRIPT_SHOPIFY_MCP_GATEWAY_VERIFICATION,
                    true,
                    "Run the Shopify MCP-first managed Gateway verification script."
                )
            )
        );
    }

    private PlatformVerificationSuiteDefinitionSummary shopifyFirstProductReadinessAudit() {
        return new PlatformVerificationSuiteDefinitionSummary(
            SHOPIFY_FIRST_PRODUCT_READINESS_AUDIT_SUITE_KEY,
            "Shopify first-product readiness audit",
            "Standalone Shopify Companion first-product readiness gate for product truth, entitlement posture, live verifier proof, answer quality, support collateral, and reviewable operator evidence.",
            false,
            List.of(
                new PlatformVerificationSuiteStageDefinitionSummary(
                    "shopify-first-product-readiness-audit",
                    "Shopify first-product readiness audit",
                    "SCRIPT_VERIFICATION",
                    PlatformVerificationSuiteScriptContextService.SCRIPT_SHOPIFY_FIRST_PRODUCT_READINESS_AUDIT,
                    true,
                    "Run the Shopify Companion first-product readiness audit and write the compact evidence packet."
                )
            )
        );
    }

    private PlatformVerificationSuiteDefinitionSummary partnerEnablementVerification() {
        return new PlatformVerificationSuiteDefinitionSummary(
            PARTNER_ENABLEMENT_VERIFICATION_SUITE_KEY,
            "Partner Enablement verification",
            "Standalone Partner Enablement verification for backend auth, deployed Partner UI routing, merchant approval/revoke flow, persisted verification runs, evidence bundles, templates, notes, members, profile, and support workflows.",
            false,
            List.of(
                new PlatformVerificationSuiteStageDefinitionSummary(
                    "partner-enablement-verification",
                    "Partner Enablement verification",
                    "SCRIPT_VERIFICATION",
                    PlatformVerificationSuiteScriptContextService.SCRIPT_PARTNER_ENABLEMENT_VERIFICATION,
                    true,
                    "Verify Partner Enablement backend auth, deployed Partner UI routing, merchant approval/revoke flow, and persisted partner workflow evidence."
                )
            )
        );
    }

    private PlatformVerificationSuiteDefinitionSummary thinkerResolverReadiness() {
        return new PlatformVerificationSuiteDefinitionSummary(
            THINKER_RESOLVER_READINESS_SUITE_KEY,
            "Thinker Resolver readiness",
            "Standalone governed issue-resolution verification for Thinker evidence capture, policy simulation, partner redaction, and Shopify merchant health.",
            false,
            List.of(
                new PlatformVerificationSuiteStageDefinitionSummary(
                    "thinker-resolver-readiness",
                    "Thinker Resolver readiness",
                    "SCRIPT_VERIFICATION",
                    PlatformVerificationSuiteScriptContextService.SCRIPT_THINKER_RESOLVER_READINESS,
                    true,
                    "Verify Thinker session persistence, evidence export, Resolver policy, dry-run, optional low-risk execution, partner redaction, and Shopify Thinker health."
                )
            )
        );
    }

    private PlatformVerificationSuiteDefinitionSummary coolifyProviderVerification() {
        return new PlatformVerificationSuiteDefinitionSummary(
            COOLIFY_PROVIDER_VERIFICATION_SUITE_KEY,
            "Coolify provider verification",
            "Standalone Coolify provider verification for staging and production API health plus optional disposable staging application lifecycle smoke.",
            false,
            List.of(
                new PlatformVerificationSuiteStageDefinitionSummary(
                    "coolify-provider-verification",
                    "Coolify provider verification",
                    "SCRIPT_VERIFICATION",
                    PlatformVerificationSuiteScriptContextService.SCRIPT_COOLIFY_PROVIDER_VERIFICATION,
                    true,
                    "Verify Coolify API tokens, live host health/version, application listing, and optional staging create/start/log/delete flow."
                )
            )
        );
    }
}
