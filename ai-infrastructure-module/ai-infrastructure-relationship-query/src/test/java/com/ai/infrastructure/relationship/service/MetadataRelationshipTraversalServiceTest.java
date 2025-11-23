package com.ai.infrastructure.relationship.service;

import com.ai.infrastructure.entity.AISearchableEntity;
import com.ai.infrastructure.relationship.dto.FilterCondition;
import com.ai.infrastructure.relationship.dto.FilterOperator;
import com.ai.infrastructure.relationship.dto.JpqlQuery;
import com.ai.infrastructure.relationship.dto.RelationshipPath;
import com.ai.infrastructure.relationship.dto.RelationshipQueryPlan;
import com.ai.infrastructure.repository.AISearchableEntityRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetadataRelationshipTraversalServiceTest {

    @Mock
    private AISearchableEntityRepository entityRepository;

    private MetadataRelationshipTraversalService service;

    @BeforeEach
    void setUp() {
        service = new MetadataRelationshipTraversalService(entityRepository, new ObjectMapper());
    }

    @Test
    void shouldReturnMatchingIdsWhenMetadataSatisfiesMergedFilters() {
        when(entityRepository.findByEntityType("document")).thenReturn(List.of(
            searchableEntity("doc-1", """
                {"state":"published","creatorstatus":"approved","priority":5}
                """),
            searchableEntity("doc-2", """
                {"state":"draft","creatorstatus":"denied","priority":9}
                """)
        ));

        RelationshipQueryPlan plan = RelationshipQueryPlan.builder()
            .primaryEntityType("document")
            .directFilters(Map.of(
                "document",
                List.of(FilterCondition.builder()
                    .field("state")
                    .operator(FilterOperator.EQUALS)
                    .value("published")
                    .build())
            ))
            .relationshipPaths(List.of(RelationshipPath.builder()
                .fromEntityType("document")
                .toEntityType("user")
                .conditions(List.of(FilterCondition.builder()
                    .field("creator.status")
                    .operator(FilterOperator.EQUALS)
                    .value("approved")
                    .build()))
                .build()))
            .build();

        List<String> results = service.traverse(plan, JpqlQuery.builder().limit(5).build());

        assertThat(results).containsExactly("doc-1");
    }

    @Test
    void shouldRespectLimitWhenNoFiltersProvided() {
        when(entityRepository.findByEntityType("document")).thenReturn(List.of(
            searchableEntity("doc-1", null),
            searchableEntity("doc-2", null)
        ));

        RelationshipQueryPlan plan = RelationshipQueryPlan.builder()
            .primaryEntityType("document")
            .build();

        List<String> results = service.traverse(plan, JpqlQuery.builder().limit(1).build());

        assertThat(results).containsExactly("doc-1");
    }

    @Test
    void shouldSkipEntitiesWithInvalidMetadata() {
        when(entityRepository.findByEntityType("document")).thenReturn(List.of(
            searchableEntity("doc-1", "{ not valid json }"),
            searchableEntity("doc-2", "")
        ));

        RelationshipQueryPlan plan = RelationshipQueryPlan.builder()
            .primaryEntityType("document")
            .directFilters(Map.of(
                "document",
                List.of(FilterCondition.builder()
                    .field("status")
                    .operator(FilterOperator.EQUALS)
                    .value("active")
                    .build())
            ))
            .build();

        List<String> results = service.traverse(plan, null);

        assertThat(results).isEmpty();
    }

    private static AISearchableEntity searchableEntity(String entityId, String metadata) {
        return AISearchableEntity.builder()
            .entityType("document")
            .entityId(entityId)
            .metadata(metadata)
            .build();
    }
}
