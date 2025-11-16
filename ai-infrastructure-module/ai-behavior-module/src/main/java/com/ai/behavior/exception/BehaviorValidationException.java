package com.ai.behavior.exception;

public class BehaviorValidationException extends BehaviorModuleException {

    public BehaviorValidationException(String message) {
        super(message);
    }

    public BehaviorValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
