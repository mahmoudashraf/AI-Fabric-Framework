package com.ai.behavior.exception;

public class BehaviorStorageException extends RuntimeException {
    public BehaviorStorageException(String message) {
        super(message);
    }

    public BehaviorStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
