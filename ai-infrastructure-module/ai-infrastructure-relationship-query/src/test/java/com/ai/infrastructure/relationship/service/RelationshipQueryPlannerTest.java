package com.ai.infrastructure.relationship.service;

import com.ai.infrastructure.core.AICoreService;
import com.ai.infrastructure.dto.AIGenerationRequest;
import com.ai.infrastructure.dto.AIGenerationResponse;
import com.ai.infrastructure.relationship.cache.QueryCache;
import com.ai.infrastructure.relationship.config.RelationshipQueryProperties;
import com.ai.infrastructure.relationship.dto.RelationshipQueryPlan;
import com.ai.infrastructure.relationship.metrics.QueryMetrics;
import com.ai.infrastructure.relationship.validation.RelationshipQueryValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RelationshipQueryPlannerTest {

    @Mock
    private AICoreService aiCoreService;
    @Mock
    private RelationshipSchemaProvider schemaProvider;
    @Mock
    private RelationshipQueryValidator validator;
    @Mock
    private QueryCache queryCache;
    @Mock
    private QueryMetrics queryMetrics;

    private RelationshipQueryPlanner planner;

    @BeforeEach
    void setUp() {
        RelationshipQueryProperties properties = new RelationshipQueryProperties();
        when(schemaProvider.getSchemaDescription(any())).thenReturn("schema");
        when(queryCache.isEnabled()).thenReturn(true);
        when(queryMetrics.isEnabled()).thenReturn(true);
        planner = new RelationshipQueryPlanner(
            aiCoreService,
            schemaProvider,
            properties,
            validator,
            queryCache,
            queryMetrics,
            new ObjectMapper()
        );
    }

    @Test
    void shouldReturnPlanFromCacheWithoutCallingLLM() {
        RelationshipQueryPlan cached = RelationshipQueryPlan.builder()
            .originalQuery("cached")
            .primaryEntityType("document")
            .build();
        when(queryCache.getPlan(anyString())).thenReturn(Optional.of(cached));

        RelationshipQueryPlan result = planner.planQuery("Find cached docs", List.of("document"));

        assertThat(result).isSameAs(cached);
        verify(aiCoreService, never()).generateContent(any(AIGenerationRequest.class));
        verify(queryMetrics).recordPlan(0, true, true);
    }

    @Test
    void shouldPlanAndCacheResultWhenCacheMisses() {
        when(queryCache.getPlan(anyString())).thenReturn(Optional.empty());
        when(aiCoreService.generateContent(any()))
            .thenReturn(AIGenerationResponse.builder().content(samplePlanJson()).build());

        RelationshipQueryPlan result = planner.planQuery("Find docs", List.of("document"));

        assertThat(result.getPrimaryEntityType()).isEqualTo("document");
        verify(validator).validate(any(RelationshipQueryPlan.class));
        verify(queryCache).putPlan(anyString(), any(RelationshipQueryPlan.class));
        verify(queryMetrics).recordPlan(any(Long.class), any(Boolean.class), any(Boolean.class));
    }

    @Test
    void shouldFallbackWhenLlmFails() {
        when(queryCache.getPlan(anyString())).thenReturn(Optional.empty());
        when(aiCoreService.generateContent(any())).thenThrow(new IllegalStateException("LLM down"));

        RelationshipQueryPlan fallback = planner.planQuery("Find docs", List.of("document"));

        assertThat(fallback.getPrimaryEntityType()).isEqualTo("document");
        assertThat(fallback.getCandidateEntityTypes()).contains("document");
        verify(queryMetrics).recordPlan(any(Long.class), any(Boolean.class), any(Boolean.class));
    }

    @Test
    void shouldUseCandidateEntitiesWhenProvided() {
        when(queryCache.getPlan(anyString())).thenReturn(Optional.empty());
        when(schemaProvider.getSchemaDescription(any())).thenReturn("schema");
        when(aiCoreService.generateContent(any()))
            .thenReturn(AIGenerationResponse.builder().content(samplePlanJson()).build());

        RelationshipQueryPlan plan = planner.planQuery("Find docs", List.of("document", "user"));

        assertThat(plan.getCandidateEntityTypes()).containsExactly("document", "user");
    }

    private String samplePlanJson() {
        return """
            {
              "originalQuery": "Find docs",
              "primaryEntityType": "document",
              "candidateEntityTypes": ["document"],
              "relationshipPaths": [],
              "directFilters": {},
              "relationshipFilters": {},
              "needsSemanticSearch": false,
              "confidence": 0.8
            }
            """;
    }
}
