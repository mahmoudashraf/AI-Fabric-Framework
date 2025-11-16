package com.ai.behavior.exception;

public class BehaviorModuleException extends RuntimeException {

    public BehaviorModuleException(String message) {
        super(message);
    }

    public BehaviorModuleException(String message, Throwable cause) {
        super(message, cause);
    }
}
