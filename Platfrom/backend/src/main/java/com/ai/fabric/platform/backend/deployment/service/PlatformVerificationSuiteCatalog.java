package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.model.PlatformVerificationSuiteDefinitionSummary;
import com.ai.fabric.platform.backend.deployment.model.PlatformVerificationSuiteStageDefinitionSummary;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Component
public class PlatformVerificationSuiteCatalog {

    public static final String CANONICAL_RELEASE_READINESS_SUITE_KEY = "canonical-release-readiness";
    public static final String SHARED_INFERENCE_SERVICE_REF = "shared-ollama-orchestration";
    public static final List<String> CANONICAL_ROLLOUT_ORDER = List.of(
        "marketplace",
        "ecommerce",
        "qdrant",
        "pinecone",
        "milvus",
        "weaviate"
    );

    public List<PlatformVerificationSuiteDefinitionSummary> listDefinitions() {
        return List.of(canonicalReleaseReadiness());
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
                    "canonical-verification-fleet",
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
}
