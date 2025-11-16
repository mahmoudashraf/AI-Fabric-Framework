package com.ai.behavior.exception;

public class BehaviorIngestionException extends BehaviorModuleException {

    public BehaviorIngestionException(String message) {
        super(message);
    }

    public BehaviorIngestionException(String message, Throwable cause) {
        super(message, cause);
    }
}
