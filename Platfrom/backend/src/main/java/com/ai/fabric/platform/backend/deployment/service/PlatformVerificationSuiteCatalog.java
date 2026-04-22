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
    public static final String PLATFORM_CODE_REGRESSION_SUITE_KEY = "platform-code-regression";
    public static final String PLATFORM_ADMIN_LIVE_REGRESSION_SUITE_KEY = "platform-admin-live-regression";
    public static final String MANAGED_VECTOR_PROVIDER_VERIFICATION_SUITE_KEY = "managed-vector-provider-verification";
    public static final String MARKETPLACE_INSTALL_FLOW_SUITE_KEY = "marketplace-install-flow";
    public static final String SHOPIFY_COMPANION_VERIFICATION_SUITE_KEY = "shopify-companion-verification";
    public static final String SHARED_INFERENCE_SERVICE_REF = "shared-ollama-orchestration";
    public static final String CANONICAL_FLEET_TARGET_REF = "canonical-verification-fleet";
    public static final List<String> CANONICAL_ROLLOUT_ORDER = List.of(
        "marketplace",
        "ecommerce",
        "qdrant",
        "pinecone",
        "milvus",
        "weaviate"
    );

    public List<PlatformVerificationSuiteDefinitionSummary> listDefinitions() {
        return List.of(
            fullPlatformReleaseReadiness(),
            canonicalReleaseReadiness(),
            platformCodeRegression(),
            platformAdminLiveRegression(),
            managedVectorProviderVerification(),
            marketplaceInstallFlowVerification(),
            shopifyCompanionVerification()
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
                    "Qdrant hosted verification",
                    "HOSTED_DEPLOYMENT_VERIFICATION",
                    "qdrant",
                    true,
                    "Run the vector hosted verification against the canonical qdrant deployment."
                ),
                new PlatformVerificationSuiteStageDefinitionSummary(
                    "pinecone-hosted-verification",
                    "Pinecone hosted verification",
                    "HOSTED_DEPLOYMENT_VERIFICATION",
                    "pinecone",
                    true,
                    "Run the vector hosted verification against the canonical pinecone deployment."
                ),
                new PlatformVerificationSuiteStageDefinitionSummary(
                    "milvus-hosted-verification",
                    "Milvus hosted verification",
                    "HOSTED_DEPLOYMENT_VERIFICATION",
                    "milvus",
                    true,
                    "Run the vector hosted verification against the canonical milvus deployment."
                ),
                new PlatformVerificationSuiteStageDefinitionSummary(
                    "weaviate-hosted-verification",
                    "Weaviate hosted verification",
                    "HOSTED_DEPLOYMENT_VERIFICATION",
                    "weaviate",
                    true,
                    "Run the vector hosted verification against the canonical weaviate deployment."
                )
            )
        );
    }

    private PlatformVerificationSuiteDefinitionSummary fullPlatformReleaseReadiness() {
        return new PlatformVerificationSuiteDefinitionSummary(
            FULL_PLATFORM_RELEASE_READINESS_SUITE_KEY,
            "Full platform release readiness",
            "Primary release-blocking suite that replaces the current live GitHub verification estate with one ordered control-plane run.",
            true,
            List.of(
                new PlatformVerificationSuiteStageDefinitionSummary(
                    "platform-code-regression",
                    "Platform code regression",
                    "SCRIPT_VERIFICATION",
                    PlatformVerificationSuiteScriptContextService.SCRIPT_PLATFORM_CODE_REGRESSION,
                    true,
                    "Run the platform code regression gate on the control plane before live environment verification begins."
                ),
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
                    "Run direct provider verification for Pinecone, Qdrant Cloud, Zilliz Cloud, and Weaviate."
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
                    "Qdrant hosted verification",
                    "HOSTED_DEPLOYMENT_VERIFICATION",
                    "qdrant",
                    true,
                    "Run the vector hosted verification against the canonical qdrant deployment."
                ),
                new PlatformVerificationSuiteStageDefinitionSummary(
                    "pinecone-hosted-verification",
                    "Pinecone hosted verification",
                    "HOSTED_DEPLOYMENT_VERIFICATION",
                    "pinecone",
                    true,
                    "Run the vector hosted verification against the canonical pinecone deployment."
                ),
                new PlatformVerificationSuiteStageDefinitionSummary(
                    "milvus-hosted-verification",
                    "Milvus hosted verification",
                    "HOSTED_DEPLOYMENT_VERIFICATION",
                    "milvus",
                    true,
                    "Run the vector hosted verification against the canonical milvus deployment."
                ),
                new PlatformVerificationSuiteStageDefinitionSummary(
                    "weaviate-hosted-verification",
                    "Weaviate hosted verification",
                    "HOSTED_DEPLOYMENT_VERIFICATION",
                    "weaviate",
                    true,
                    "Run the vector hosted verification against the canonical weaviate deployment."
                )
            )
        );
    }

    private PlatformVerificationSuiteDefinitionSummary platformCodeRegression() {
        return new PlatformVerificationSuiteDefinitionSummary(
            PLATFORM_CODE_REGRESSION_SUITE_KEY,
            "Platform code regression",
            "Standalone control-plane execution of the former GitHub platform code regression gate.",
            false,
            List.of(
                new PlatformVerificationSuiteStageDefinitionSummary(
                    "platform-code-regression",
                    "Platform code regression",
                    "SCRIPT_VERIFICATION",
                    PlatformVerificationSuiteScriptContextService.SCRIPT_PLATFORM_CODE_REGRESSION,
                    true,
                    "Run backend tests, product tests, targeted infrastructure tests, the platform UI build, and verification shell syntax checks."
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
}
