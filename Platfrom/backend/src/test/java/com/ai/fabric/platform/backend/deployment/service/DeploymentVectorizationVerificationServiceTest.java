package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentVectorizationVerificationSummary;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationOverviewSummary;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationPlanRevisionSummary;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationPlanSummary;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationRunnerSummary;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationSourceConnectionSummary;
import com.ai.fabric.platform.backend.vectorization.service.VectorizationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeploymentVectorizationVerificationServiceTest {

    private final VectorizationService vectorizationService = mock(VectorizationService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void buildUsesEvaluatedOverviewSyncState() throws Exception {
        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId("dep-1");
        deployment.setCustomerId("cus-1");
        deployment.setTenantId("ten-1");

        VectorizationSourceConnectionSummary connection = new VectorizationSourceConnectionSummary(
            "vcn-1",
            "dep-1",
            "Commerce API",
            "REST_API",
            "NONE",
            "READY",
            json("{}"),
            json("{}"),
            json("""
                {"countsByEntityType":{"policy":20,"product":100,"review":200}}
                """),
            Instant.parse("2026-04-05T21:22:39Z"),
            Instant.parse("2026-04-05T23:01:37Z")
        );

        VectorizationPlanSummary plan = new VectorizationPlanSummary(
            "vpl-1",
            "dep-1",
            "OpenAI Weaviate Verification vectorization",
            "ACTIVE",
            "PLATFORM_MANAGED_AUTO",
            "IN_SYNC",
            List.of("IN_SYNC"),
            json("""
                {
                  "expectedCountsByEntityType":{"policy":20,"product":100,"review":200},
                  "liveCountsByEntityType":{"policy":20,"product":100,"review":200}
                }
                """),
            "hash-1",
            null,
            "vpr-1",
            "vcn-1",
            null,
            null,
            null,
            null,
            new VectorizationPlanRevisionSummary(
                "vpr-1",
                1,
                "ACTIVE",
                "vcn-1",
                json("""
                    ["policy","product","review"]
                    """),
                json("{}"),
                json("{}"),
                null,
                Instant.parse("2026-04-05T21:22:39Z"),
                Instant.parse("2026-04-05T21:22:39Z")
            ),
            Instant.parse("2026-04-05T21:22:39Z"),
            Instant.parse("2026-04-05T23:01:37Z")
        );

        VectorizationRunnerSummary runner = new VectorizationRunnerSummary(
            "vrr-1",
            "PLATFORM_MANAGED_AUTO",
            "ACTIVE",
            "CURRENT",
            "hint-1234",
            Instant.parse("2026-04-06T01:00:00Z"),
            "vectorization-runner-dep-1",
            "2026.04.track-b",
            "1",
            Instant.parse("2026-04-05T22:50:00Z"),
            Instant.parse("2026-04-05T23:00:00Z"),
            Instant.parse("2026-04-06T01:00:00Z")
        );

        when(vectorizationService.getOverviewForTrustedCaller(deployment)).thenReturn(
            new VectorizationOverviewSummary(
                "dep-1",
                "cus-1",
                "ten-1",
                "ver-1",
                "v1",
                "cfg-1",
                connection,
                plan,
                runner,
                List.of()
            )
        );

        DeploymentVectorizationVerificationService service = new DeploymentVectorizationVerificationService(vectorizationService);
        DeploymentVectorizationVerificationSummary summary = service.build(
            deployment,
            json("""
                {"ai-entities":{"product":{},"policy":{},"review":{}}}
                """)
        );

        assertThat(summary.configured()).isTrue();
        assertThat(summary.plan()).isNotNull();
        assertThat(summary.plan().syncState()).isEqualTo("IN_SYNC");
        assertThat(summary.entityScope()).containsExactly("policy", "product", "review");
        assertThat(summary.availableEntityTypes()).containsExactly("policy", "product", "review");
    }

    private JsonNode json(String value) throws Exception {
        return objectMapper.readTree(value);
    }
}
