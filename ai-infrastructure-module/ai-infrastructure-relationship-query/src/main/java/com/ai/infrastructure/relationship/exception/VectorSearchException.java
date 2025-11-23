package com.ai.infrastructure.relationship.exception;

/**
 * Raised when vector search fallback fails.
 */
public class VectorSearchException extends RelationshipQueryException {
    public VectorSearchException(String message, Throwable cause) {
        super(message, cause);
    }
}
