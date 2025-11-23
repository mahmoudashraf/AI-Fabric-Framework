package com.ai.infrastructure.relationship.exception;

/**
 * Raised when metadata-based traversal fails.
 */
public class RelationshipTraversalException extends RelationshipQueryException {
    public RelationshipTraversalException(String message, Throwable cause) {
        super(message, cause);
    }
}
