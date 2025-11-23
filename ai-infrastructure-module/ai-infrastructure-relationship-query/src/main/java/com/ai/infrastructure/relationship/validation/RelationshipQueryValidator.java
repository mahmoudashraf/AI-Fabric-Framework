package com.ai.infrastructure.relationship.validation;

import com.ai.infrastructure.relationship.dto.RelationshipPath;
import com.ai.infrastructure.relationship.dto.RelationshipQueryPlan;
import com.ai.infrastructure.relationship.service.EntityRelationshipMapper;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

/**
 * Performs guardrail validation on {@link RelationshipQueryPlan} instances before execution.
 */
public class RelationshipQueryValidator {

    private final EntityRelationshipMapper relationshipMapper;

    public RelationshipQueryValidator(EntityRelationshipMapper relationshipMapper) {
        this.relationshipMapper = relationshipMapper;
    }

    public void validate(RelationshipQueryPlan plan) {
        if (plan == null) {
            throw new RelationshipQueryValidationException("Relationship query plan cannot be null");
        }

        if (!StringUtils.hasText(plan.getOriginalQuery())) {
            throw new RelationshipQueryValidationException("Original query text is required");
        }

        if (!StringUtils.hasText(plan.getPrimaryEntityType())) {
            throw new RelationshipQueryValidationException("Primary entity type is required");
        }

        if (!relationshipMapper.hasEntityType(plan.getPrimaryEntityType())) {
            throw new RelationshipQueryValidationException(
                "Primary entity type '%s' is not registered".formatted(plan.getPrimaryEntityType())
            );
        }

        List<String> candidateEntities = plan.getCandidateEntityTypes();
        if (!CollectionUtils.isEmpty(candidateEntities)) {
            boolean containsPrimary = candidateEntities.stream()
                .map(type -> type == null ? null : type.toLowerCase(Locale.ROOT))
                .anyMatch(type -> type != null && type.equals(plan.getPrimaryEntityType().toLowerCase(Locale.ROOT)));
            if (!containsPrimary) {
                throw new RelationshipQueryValidationException(
                    "Candidate entity list must contain the primary entity type '%s'"
                        .formatted(plan.getPrimaryEntityType())
                );
            }
        }

        validateRelationshipPaths(plan);
    }

    private void validateRelationshipPaths(RelationshipQueryPlan plan) {
        if (CollectionUtils.isEmpty(plan.getRelationshipPaths())) {
            return;
        }

        for (RelationshipPath path : plan.getRelationshipPaths()) {
            if (path == null) {
                continue;
            }
            if (!StringUtils.hasText(path.getFromEntityType()) || !StringUtils.hasText(path.getToEntityType())) {
                throw new RelationshipQueryValidationException("Relationship paths must define from/to entity types");
            }
            if (!StringUtils.hasText(path.getRelationshipType())) {
                throw new RelationshipQueryValidationException(
                    "Relationship path %s -> %s does not define a relationshipType"
                        .formatted(path.getFromEntityType(), path.getToEntityType())
                );
            }
        }
    }
}
