package com.ai.behavior.exception;

public class BehaviorValidationException extends RuntimeException {
    public BehaviorValidationException(String message) {
        super(message);
    }

    public BehaviorValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
