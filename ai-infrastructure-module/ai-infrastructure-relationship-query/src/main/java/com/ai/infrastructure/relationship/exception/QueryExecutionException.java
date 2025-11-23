package com.ai.infrastructure.relationship.exception;

/**
 * Raised when executing a relationship query plan fails.
 */
public class QueryExecutionException extends RelationshipQueryException {
    public QueryExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
