package com.ai.infrastructure.relationship.it.realapi;

import com.ai.infrastructure.core.AICoreService;
import com.ai.infrastructure.core.LlmPurpose;
import com.ai.infrastructure.dto.AIGenerationRequest;
import com.ai.infrastructure.dto.AIGenerationResponse;
import com.ai.infrastructure.dto.RAGResponse;
import com.ai.infrastructure.intent.action.ActionResult;
import com.ai.infrastructure.intent.orchestration.OrchestrationContext;
import com.ai.infrastructure.intent.orchestration.OrchestrationResult;
import com.ai.infrastructure.intent.orchestration.OrchestrationResultType;
import com.ai.infrastructure.intent.orchestration.RAGOrchestrator;
import com.ai.infrastructure.relationship.dto.RelationshipQueryPlan;
import com.ai.infrastructure.relationship.it.RelationshipQueryIntegrationTestApplication;
import com.ai.infrastructure.relationship.it.config.BackendEnvTestConfiguration;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(
    classes = RelationshipQueryIntegrationTestApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("realapi")
@Import({
    BackendEnvTestConfiguration.class,
    OrchestratorAccessPolicyRealApiIntegrationTest.PolicyConfig.class,
    com.ai.infrastructure.relationship.it.config.TestRelationshipQueryAccessControlPolicy.class
})
@TestPropertySource(properties = {
    "ai.infrastructure.relationship.enable-orchestrator-integration=true"
})
class RelationshipQueryQueryParamScenariosRealApiIntegrationTest {

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
    @DisplayName("Should default relationship_query actionParams.query when missing (strip hint prefixes)")
    void shouldDefaultQueryWhenMissingStrippingHintPrefix() {
        stubIntentExtractionMissingQuery();
        stubRelationshipPlanNoFilters();

        OrchestrationResult result = orchestrator.orchestrate(
            "relationship query: find all brands",
            OrchestrationContext.builder()
                .userId("entity-extraction-test-user")
                .sessionId("entity-extraction-session")
                .metadata(Map.of("channel", "test"))
                .build()
        );

        assertThat(result.isSuccess())
            .withFailMessage("Expected orchestration success but got type=%s success=%s message=%s dataKeys=%s metadataKeys=%s",
                result.getType(),
                result.isSuccess(),
                result.getMessage(),
                result.getData() != null ? result.getData().keySet() : null,
                result.getMetadata() != null ? result.getMetadata().keySet() : null)
            .isTrue();
        assertThat(result.getType()).isEqualTo(OrchestrationResultType.ACTION_EXECUTED);

        ActionResult actionResult = (ActionResult) result.getData().get("actionResult");
        assertThat(actionResult.isSuccess()).isTrue();

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) actionResult.getData();
        @SuppressWarnings("unchecked")
        List<RAGResponse.RAGDocument> documents = (List<RAGResponse.RAGDocument>) payload.get("documents");
        assertThat(documents).isNotNull().isNotEmpty();

        assertExecutedPlanOriginalQuery(payload, "find all brands");
    }

    @Test
    @DisplayName("Should use LLM-provided relational part for compound messages (avoid including unrelated tasks)")
    void shouldUseRelationalPartQueryForCompoundMessage() {
        stubIntentExtractionWithExplicitQuery("find all brands");
        stubRelationshipPlanNoFilters();

        OrchestrationResult result = orchestrator.orchestrate(
            "relationship query: find all brands and then summarize them",
            OrchestrationContext.builder()
                .userId("entity-extraction-test-user")
                .sessionId("entity-extraction-session")
                .metadata(Map.of("channel", "test"))
                .build()
        );

        assertThat(result.isSuccess())
            .withFailMessage("Expected orchestration success but got type=%s success=%s message=%s dataKeys=%s metadataKeys=%s",
                result.getType(),
                result.isSuccess(),
                result.getMessage(),
                result.getData() != null ? result.getData().keySet() : null,
                result.getMetadata() != null ? result.getMetadata().keySet() : null)
            .isTrue();
        assertThat(result.getType()).isEqualTo(OrchestrationResultType.ACTION_EXECUTED);

        ActionResult actionResult = (ActionResult) result.getData().get("actionResult");
        assertThat(actionResult.isSuccess()).isTrue();

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) actionResult.getData();
        assertExecutedPlanOriginalQuery(payload, "find all brands");
        assertExecutedPlanOriginalQueryDoesNotContain(payload, "summarize");
    }

    @Test
    @DisplayName("Should strip relationship-query hint prefix from actionParams.query when provided")
    void shouldStripHintPrefixWhenProvidedByLLM() {
        stubIntentExtractionWithExplicitQuery("relationship query: find all brands");
        stubRelationshipPlanNoFilters();

        OrchestrationResult result = orchestrator.orchestrate(
            "relationship query: find all brands",
            OrchestrationContext.builder()
                .userId("entity-extraction-test-user")
                .sessionId("entity-extraction-session")
                .metadata(Map.of("channel", "test"))
                .build()
        );

        assertThat(result.isSuccess())
            .withFailMessage("Expected orchestration success but got type=%s success=%s message=%s dataKeys=%s metadataKeys=%s",
                result.getType(),
                result.isSuccess(),
                result.getMessage(),
                result.getData() != null ? result.getData().keySet() : null,
                result.getMetadata() != null ? result.getMetadata().keySet() : null)
            .isTrue();
        assertThat(result.getType()).isEqualTo(OrchestrationResultType.ACTION_EXECUTED);

        ActionResult actionResult = (ActionResult) result.getData().get("actionResult");
        assertThat(actionResult.isSuccess()).isTrue();

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) actionResult.getData();
        assertExecutedPlanOriginalQuery(payload, "find all brands");
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

    private void stubIntentExtractionWithExplicitQuery(String query) {
        AIGenerationResponse response = AIGenerationResponse.builder().content("""
            {
              "intents": [
                {
                  "type": "ACTION",
                  "intent": "relationship_query",
                  "confidence": 0.95,
                  "action": "relationship_query",
                  "actionParams": {
                    "query": "%s",
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
            """.formatted(query)).build();

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

    private void assertExecutedPlanOriginalQuery(Map<String, Object> payload, String expectedOriginalQuery) {
        String originalQuery = extractOriginalQueryFromPayload(payload);
        assertThat(originalQuery).isEqualTo(expectedOriginalQuery);
    }

    private void assertExecutedPlanOriginalQueryDoesNotContain(Map<String, Object> payload, String unexpectedSubstring) {
        String originalQuery = extractOriginalQueryFromPayload(payload);
        assertThat(originalQuery).doesNotContainIgnoringCase(unexpectedSubstring);
    }

    private String extractOriginalQueryFromPayload(Map<String, Object> payload) {
        Object metadataRaw = payload.get("metadata");
        assertThat(metadataRaw).as("Action payload should include metadata").isInstanceOf(Map.class);

        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) metadataRaw;

        Object planRaw = metadata.get("plan");
        assertThat(planRaw).as("Action payload metadata should include plan").isNotNull();

        if (planRaw instanceof RelationshipQueryPlan plan) {
            return plan.getOriginalQuery();
        }
        if (planRaw instanceof Map<?, ?> planMap) {
            Object original = planMap.get("originalQuery");
            return original != null ? original.toString() : null;
        }

        throw new AssertionError("Unexpected plan payload type: " + planRaw.getClass().getName());
    }
}
