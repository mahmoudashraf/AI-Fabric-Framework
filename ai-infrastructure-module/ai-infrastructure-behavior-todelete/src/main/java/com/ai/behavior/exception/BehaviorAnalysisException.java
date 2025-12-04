package com.ai.behavior.exception;

public class BehaviorAnalysisException extends RuntimeException {
    public BehaviorAnalysisException(String message) {
        super(message);
    }

    public BehaviorAnalysisException(String message, Throwable cause) {
        super(message, cause);
    }
}
