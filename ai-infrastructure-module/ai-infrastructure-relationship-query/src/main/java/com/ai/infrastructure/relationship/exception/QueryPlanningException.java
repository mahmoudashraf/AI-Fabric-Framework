package com.ai.infrastructure.relationship.exception;

/**
 * Raised when the LLM planner fails to build a relationship query plan.
 */
public class QueryPlanningException extends RelationshipQueryException {
    public QueryPlanningException(String message, Throwable cause) {
        super(message, cause);
    }
}
