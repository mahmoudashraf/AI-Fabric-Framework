package com.ai.infrastructure.relationship.exception;

/**
 * Raised when every fallback strategy has failed.
 */
public class FallbackExhaustedException extends RelationshipQueryException {
    public FallbackExhaustedException(String message) {
        super(message);
    }

    public FallbackExhaustedException(String message, Throwable cause) {
        super(message, cause);
    }
}
