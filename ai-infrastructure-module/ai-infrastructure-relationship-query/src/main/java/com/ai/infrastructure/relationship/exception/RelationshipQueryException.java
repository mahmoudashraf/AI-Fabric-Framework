package com.ai.infrastructure.relationship.exception;

/**
 * Base runtime exception for all relationship query failures.
 */
public class RelationshipQueryException extends RuntimeException {
    public RelationshipQueryException(String message) {
        super(message);
    }

    public RelationshipQueryException(String message, Throwable cause) {
        super(message, cause);
    }
}
