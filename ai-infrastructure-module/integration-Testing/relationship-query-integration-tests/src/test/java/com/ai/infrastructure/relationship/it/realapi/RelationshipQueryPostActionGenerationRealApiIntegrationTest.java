package com.ai.infrastructure.relationship.it.realapi;

import com.ai.infrastructure.intent.action.ActionResult;
import com.ai.infrastructure.intent.orchestration.OrchestrationContext;
import com.ai.infrastructure.intent.orchestration.OrchestrationResult;
import com.ai.infrastructure.intent.orchestration.OrchestrationResultType;
import com.ai.infrastructure.intent.orchestration.RAGOrchestrator;
import com.ai.infrastructure.dto.RAGResponse;
import com.ai.infrastructure.relationship.it.RelationshipQueryIntegrationTestApplication;
import com.ai.infrastructure.relationship.it.config.BackendEnvTestConfiguration;
import com.ai.infrastructure.relationship.it.config.OrchestratorAllowAllPolicyTestConfiguration;
import com.ai.infrastructure.relationship.it.config.TestRelationshipQueryAccessControlPolicy;
import com.ai.infrastructure.relationship.it.entity.BrandEntity;
import com.ai.infrastructure.relationship.it.repository.BrandRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
    classes = RelationshipQueryIntegrationTestApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("realapi")
@Import({
    BackendEnvTestConfiguration.class,
    OrchestratorAllowAllPolicyTestConfiguration.class,
    TestRelationshipQueryAccessControlPolicy.class
})
@TestPropertySource(properties = {
    "ai.infrastructure.relationship.enable-orchestrator-integration=true",
    "ai.infrastructure.relationship.post-action-generation.enabled=true",
    "ai.infrastructure.relationship.post-action-generation.max-items=10",
    "ai.infrastructure.relationship.post-action-generation.max-chars=12000"
})
class RelationshipQueryPostActionGenerationRealApiIntegrationTest {

    @Autowired
    private RAGOrchestrator orchestrator;

    @Autowired
    private BrandRepository brandRepository;

    @BeforeEach
    void setUp() {
        brandRepository.deleteAllInBatch();

        BrandEntity nike = new BrandEntity();
        nike.setName("Nike");
        BrandEntity adidas = new BrandEntity();
        adidas.setName("Adidas");
        brandRepository.saveAll(List.of(nike, adidas));
    }

    @Test
    @DisplayName("Should execute relationship_query and then generate a grounded summary from the action results")
    void shouldExecuteRelationshipQueryAndGenerateSummary() {
        OrchestrationResult result = orchestrator.orchestrate(
            "relationship query: find all brands and then summarize them",
            OrchestrationContext.builder()
                .userId("realapi-post-action-user")
                .sessionId("realapi-post-action-session")
                .metadata(Map.of("channel", "realapi-test"))
                .build()
        );

        assertThat(result.getType()).isEqualTo(OrchestrationResultType.ACTION_EXECUTED);
        assertThat(result.isSuccess()).isTrue();

        assertThat(result.getMetadata()).containsEntry("relationshipQuery.executed", true);
        assertThat(result.getMetadata()).containsEntry("postActionGeneration.used", true);
        assertThat(result.getMetadata()).containsEntry("postActionGeneration.purpose", "GENERATION");

        assertThat(result.getData()).containsKey("actionResult");
        assertThat(result.getData()).containsKey("summary");

        Object summaryRaw = result.getData().get("summary");
        assertThat(summaryRaw).isInstanceOf(String.class);
        String summary = (String) summaryRaw;
        assertThat(summary).isNotBlank();

        ActionResult actionResult = (ActionResult) result.getData().get("actionResult");
        assertThat(actionResult).isNotNull();
        assertThat(actionResult.isSuccess()).isTrue();

        Object actionDataRaw = actionResult.getData();
        assertThat(actionDataRaw).isInstanceOf(Map.class);
        Map<?, ?> actionData = (Map<?, ?>) actionDataRaw;
        Object documentsRaw = actionData.get("documents");
        assertThat(documentsRaw).isInstanceOf(List.class);
        List<?> documents = (List<?>) documentsRaw;

        List<String> returnedIds = documents.stream()
            .map(doc -> {
                if (doc instanceof RAGResponse.RAGDocument ragDoc) {
                    return ragDoc.getId();
                }
                if (doc instanceof Map<?, ?> map) {
                    Object id = map.get("id");
                    return id != null ? id.toString() : null;
                }
                return null;
            })
            .filter(Objects::nonNull)
            .toList();

        assertThat(returnedIds).isNotEmpty();
        assertThat(summary)
            .as("Summary should be grounded in the action result IDs")
            .containsAnyOf(returnedIds.toArray(new String[0]));
    }
}
