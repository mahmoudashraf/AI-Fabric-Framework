package com.ai.infrastructure.relationship.it;

import com.ai.infrastructure.core.AICoreService;
import com.ai.infrastructure.core.LlmPurpose;
import com.ai.infrastructure.dto.AIGenerationResponse;
import com.ai.infrastructure.intent.action.ActionResult;
import com.ai.infrastructure.intent.orchestration.OrchestrationContext;
import com.ai.infrastructure.intent.orchestration.OrchestrationResult;
import com.ai.infrastructure.intent.orchestration.OrchestrationResultType;
import com.ai.infrastructure.intent.orchestration.RAGOrchestrator;
import com.ai.infrastructure.relationship.it.config.BackendEnvTestConfiguration;
import com.ai.infrastructure.relationship.it.config.OrchestratorAllowAllPolicyTestConfiguration;
import com.ai.infrastructure.relationship.it.config.TestRelationshipQueryAccessControlPolicy;
import com.ai.infrastructure.relationship.it.entity.BrandEntity;
import com.ai.infrastructure.relationship.it.repository.BrandRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(
    classes = RelationshipQueryIntegrationTestApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@Import({
    BackendEnvTestConfiguration.class,
    OrchestratorAllowAllPolicyTestConfiguration.class,
    TestRelationshipQueryAccessControlPolicy.class
})
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:relationship_query_post_action_generation;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "ai-infrastructure.storage.strategy=SINGLE_TABLE",
    "ai.providers.enabled=false",
    "ai.infrastructure.relationship.enable-orchestrator-integration=true",
    "ai.infrastructure.relationship.schema.auto-discover=false",
    "ai.infrastructure.relationship.post-action-generation.enabled=true",
    "ai.infrastructure.relationship.post-action-generation.max-items=10",
    "ai.infrastructure.relationship.post-action-generation.max-chars=12000"
})
class RelationshipQueryPostActionGenerationIT {

    @Autowired
    private RAGOrchestrator orchestrator;

    @Autowired
    private BrandRepository brandRepository;

    @MockBean
    private AICoreService aiCoreService;

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
    @DisplayName("Should chain relationship_query action result into a generation summary when user requests 'then summarize'")
    void shouldChainRelationshipQueryToPostActionSummary() {
        stubIntentExtractionMissingQuery();
        stubRelationshipPlanNoFilters();
        stubPostActionSummary("Summary based on returned brands.");

        OrchestrationResult result = orchestrator.orchestrate(
            "relationship query: find all brands and then summarize them",
            OrchestrationContext.builder()
                .userId("it-user")
                .sessionId("it-session")
                .metadata(Map.of("channel", "test"))
                .build()
        );

        assertThat(result.getType())
            .as("Unexpected result type (message=%s errorCode=%s dataKeys=%s metadataKeys=%s)",
                result.getMessage(),
                result.getErrorCode(),
                result.getData() != null ? result.getData().keySet() : null,
                result.getMetadata() != null ? result.getMetadata().keySet() : null)
            .isEqualTo(OrchestrationResultType.ACTION_EXECUTED);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).containsKey("actionResult");
        assertThat(result.getData()).containsEntry("summary", "Summary based on returned brands.");
        assertThat(result.getMetadata()).containsEntry("relationshipQuery.executed", true);
        assertThat(result.getMetadata()).containsEntry("postActionGeneration.used", true);
        assertThat(result.getMetadata()).containsEntry("postActionGeneration.purpose", "GENERATION");

        ActionResult actionResult = (ActionResult) result.getData().get("actionResult");
        assertThat(actionResult).isNotNull();
        assertThat(actionResult.isSuccess()).isTrue();

        ArgumentCaptor<com.ai.infrastructure.dto.AIGenerationRequest> summaryCaptor =
            ArgumentCaptor.forClass(com.ai.infrastructure.dto.AIGenerationRequest.class);
        verify(aiCoreService, atLeastOnce()).generateContent(summaryCaptor.capture(), eq(LlmPurpose.GENERATION));
        assertThat(summaryCaptor.getValue().getGenerationType()).isEqualTo("relationship_query_post_action_summary");
        assertThat(summaryCaptor.getValue().getPrompt()).contains("FACTS_JSON");
    }

    private void stubIntentExtractionMissingQuery() {
        AIGenerationResponse response = AIGenerationResponse.builder().content("""
            {
              "intents": [
                {
                  "type": "ACTION",
                  "intent": "relationship_query",
                  "confidence": 0.95,
                  "action": "relationship_query",
                  "actionParams": {
                    "entityTypes": ["brand"],
                    "limit": 20
                  },
                  "requiresRetrieval": true,
                  "requiresGeneration": false
                }
              ],
              "isCompound": false,
              "orchestrationStrategy": "DIRECT_ACTION"
            }
            """).build();

        when(aiCoreService.generateContent(argThat(req ->
            req != null && "intent_extraction".equals(req.getGenerationType())
        ), eq(LlmPurpose.ORCHESTRATION))).thenReturn(response);

        when(aiCoreService.generateContent(argThat(req ->
            req != null && "intent_extraction".equals(req.getGenerationType())
        ))).thenReturn(response);
    }

    private void stubRelationshipPlanNoFilters() {
        AIGenerationResponse response = AIGenerationResponse.builder().content("""
            {
              "primaryEntityType": "brand",
              "candidateEntityTypes": ["brand"],
              "relationshipPaths": [],
              "directFilters": {},
              "relationshipFilters": {},
              "metadataFilters": {},
              "queryStrategy": "RELATIONSHIP",
              "needsSemanticSearch": false,
              "confidence": 0.9
            }
            """).build();

        when(aiCoreService.generateContent(argThat(req ->
            req != null && "planning".equals(req.getGenerationType()) && "relationship-query".equals(req.getEntityType())
        ))).thenReturn(response);

        when(aiCoreService.generateContent(argThat(req ->
            req != null && "planning".equals(req.getGenerationType()) && "relationship-query".equals(req.getEntityType())
        ), eq(LlmPurpose.ORCHESTRATION))).thenReturn(response);
    }

    private void stubPostActionSummary(String summary) {
        AIGenerationResponse response = AIGenerationResponse.builder().content(summary).model("stub-model").build();

        when(aiCoreService.generateContent(argThat(req ->
            req != null && "relationship_query_post_action_summary".equals(req.getGenerationType())
        ), eq(LlmPurpose.GENERATION))).thenReturn(response);
    }
}
