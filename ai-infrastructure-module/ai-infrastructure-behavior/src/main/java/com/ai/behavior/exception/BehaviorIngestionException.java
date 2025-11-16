package com.ai.behavior.exception;

public class BehaviorIngestionException extends RuntimeException {
    public BehaviorIngestionException(String message) {
        super(message);
    }

    public BehaviorIngestionException(String message, Throwable cause) {
        super(message, cause);
    }
}
