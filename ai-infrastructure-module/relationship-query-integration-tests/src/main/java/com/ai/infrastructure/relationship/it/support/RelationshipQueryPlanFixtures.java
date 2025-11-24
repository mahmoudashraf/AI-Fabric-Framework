package com.ai.infrastructure.relationship.it.support;

import com.ai.infrastructure.relationship.dto.FilterCondition;
import com.ai.infrastructure.relationship.dto.FilterOperator;
import com.ai.infrastructure.relationship.dto.RelationshipDirection;
import com.ai.infrastructure.relationship.dto.RelationshipPath;
import com.ai.infrastructure.relationship.dto.RelationshipQueryPlan;
import com.ai.infrastructure.relationship.dto.QueryStrategy;
import com.ai.infrastructure.relationship.model.ReturnMode;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Static fixtures used by the deterministic planner override.
 */
public final class RelationshipQueryPlanFixtures {

    private static final String LAW_FIRM_QUERY = "Find all contracts related to John Smith in Q4 2023";
    private static final String ECOM_QUERY = "Show me blue shoes under $100 from Nike";
    private static final String FRAUD_QUERY = "List suspicious transactions over $25k from high-risk regions routed through the same counterparty";

    private static final Map<String, Supplier<RelationshipQueryPlan>> PLAN_SUPPLIERS = Map.of(
        LAW_FIRM_QUERY.toLowerCase(Locale.ROOT), RelationshipQueryPlanFixtures::lawFirmPlan,
        ECOM_QUERY.toLowerCase(Locale.ROOT), RelationshipQueryPlanFixtures::ecommercePlan,
        FRAUD_QUERY.toLowerCase(Locale.ROOT), RelationshipQueryPlanFixtures::financialFraudPlan
    );

    private RelationshipQueryPlanFixtures() {
    }

    public static RelationshipQueryPlan planFor(String query) {
        if (!StringUtils.hasText(query)) {
            return null;
        }
        Supplier<RelationshipQueryPlan> supplier = PLAN_SUPPLIERS.get(query.trim().toLowerCase(Locale.ROOT));
        return supplier != null ? supplier.get() : null;
    }

    private static RelationshipQueryPlan lawFirmPlan() {
        FilterCondition statusFilter = FilterCondition.builder()
            .field("status")
            .operator(FilterOperator.EQUALS)
            .value("ACTIVE")
            .build();

        FilterCondition quarterFilter = FilterCondition.builder()
            .field("title")
            .operator(FilterOperator.ILIKE)
            .value("%Q4 2023%")
            .build();

        RelationshipPath authorPath = RelationshipPath.builder()
            .fromEntityType("document")
            .relationshipType("author")
            .toEntityType("user")
            .direction(RelationshipDirection.FORWARD)
            .optional(false)
            .conditions(List.of(FilterCondition.builder()
                .field("fullName")
                .operator(FilterOperator.ILIKE)
                .value("%John Smith%")
                .build()))
            .build();

        return RelationshipQueryPlan.builder()
            .originalQuery(LAW_FIRM_QUERY)
            .primaryEntityType("document")
            .candidateEntityTypes(List.of("document", "user"))
            .relationshipPaths(List.of(authorPath))
            .directFilters(Map.of("document", List.of(statusFilter, quarterFilter)))
            .queryStrategy(QueryStrategy.RELATIONSHIP)
            .returnMode(ReturnMode.FULL)
            .needsSemanticSearch(false)
            .limit(5)
            .build();
    }

    private static RelationshipQueryPlan ecommercePlan() {
        FilterCondition colorFilter = FilterCondition.builder()
            .field("color")
            .operator(FilterOperator.ILIKE)
            .value("blue")
            .build();

        FilterCondition priceFilter = FilterCondition.builder()
            .field("price")
            .operator(FilterOperator.LESS_THAN_OR_EQUAL)
            .value(BigDecimal.valueOf(100))
            .build();

        RelationshipPath brandPath = RelationshipPath.builder()
            .fromEntityType("product")
            .relationshipType("brand")
            .toEntityType("brand")
            .direction(RelationshipDirection.FORWARD)
            .optional(false)
            .conditions(List.of(FilterCondition.builder()
                .field("name")
                .operator(FilterOperator.ILIKE)
                .value("%Nike%")
                .build()))
            .build();

        return RelationshipQueryPlan.builder()
            .originalQuery(ECOM_QUERY)
            .primaryEntityType("product")
            .candidateEntityTypes(List.of("product", "brand"))
            .relationshipPaths(List.of(brandPath))
            .directFilters(Map.of("product", List.of(colorFilter, priceFilter)))
            .queryStrategy(QueryStrategy.RELATIONSHIP)
            .returnMode(ReturnMode.FULL)
            .needsSemanticSearch(false)
            .limit(5)
            .build();
    }

    private static RelationshipQueryPlan financialFraudPlan() {
        FilterCondition amountFilter = FilterCondition.builder()
            .field("amount")
            .operator(FilterOperator.GREATER_THAN)
            .value(BigDecimal.valueOf(25_000))
            .build();

        FilterCondition statusFilter = FilterCondition.builder()
            .field("status")
            .operator(FilterOperator.EQUALS)
            .value("PENDING_REVIEW")
            .build();

        FilterCondition channelFilter = FilterCondition.builder()
            .field("channel")
            .operator(FilterOperator.ILIKE)
            .value("%wire%")
            .build();

        RelationshipPath destinationPath = RelationshipPath.builder()
            .fromEntityType("transaction")
            .relationshipType("destinationAccount")
            .toEntityType("destination-account")
            .direction(RelationshipDirection.FORWARD)
            .optional(false)
            .conditions(List.of(FilterCondition.builder()
                .field("region")
                .operator(FilterOperator.ILIKE)
                .value("%high-risk%")
                .build()))
            .build();

        RelationshipPath originPath = RelationshipPath.builder()
            .fromEntityType("transaction")
            .relationshipType("sourceAccount")
            .toEntityType("origin-account")
            .direction(RelationshipDirection.FORWARD)
            .optional(false)
            .conditions(List.of(FilterCondition.builder()
                .field("riskScore")
                .operator(FilterOperator.GREATER_THAN_OR_EQUAL)
                .value(BigDecimal.valueOf(0.7))
                .build()))
            .build();

        return RelationshipQueryPlan.builder()
            .originalQuery(FRAUD_QUERY)
            .primaryEntityType("transaction")
            .candidateEntityTypes(List.of("transaction", "account", "destination-account", "origin-account"))
            .relationshipPaths(List.of(destinationPath, originPath))
            .directFilters(Map.of("transaction", List.of(amountFilter, statusFilter, channelFilter)))
            .queryStrategy(QueryStrategy.RELATIONSHIP)
            .returnMode(ReturnMode.FULL)
            .needsSemanticSearch(false)
            .limit(5)
            .build();
    }
}
