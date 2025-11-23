package com.ai.infrastructure.relationship.validation;

import com.ai.infrastructure.annotation.AICapable;
import com.ai.infrastructure.relationship.dto.RelationshipQueryPlan;
import com.ai.infrastructure.relationship.dto.QueryStrategy;
import com.ai.infrastructure.relationship.service.EntityRelationshipMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RelationshipQueryValidatorTest {

    private RelationshipQueryValidator validator;
    private EntityRelationshipMapper mapper;

    @BeforeEach
    void setup() {
        mapper = new EntityRelationshipMapper();
        mapper.registerEntityType(DocumentEntity.class);
        validator = new RelationshipQueryValidator(mapper);
    }

    @Test
    void shouldRejectMissingPrimaryEntity() {
        RelationshipQueryPlan plan = RelationshipQueryPlan.builder()
            .originalQuery("test")
            .build();

        assertThatThrownBy(() -> validator.validate(plan))
            .isInstanceOf(RelationshipQueryValidationException.class)
            .hasMessageContaining("Primary entity type is required");
    }

    @Test
    void shouldValidateWellFormedPlan() {
        RelationshipQueryPlan plan = RelationshipQueryPlan.builder()
            .originalQuery("Find docs")
            .primaryEntityType("document")
            .candidateEntityTypes(List.of("document"))
            .queryStrategy(QueryStrategy.RELATIONSHIP)
            .build();

        assertThatCode(() -> validator.validate(plan)).doesNotThrowAnyException();
    }

    @AICapable(entityType = "document")
    private static class DocumentEntity { }
}
