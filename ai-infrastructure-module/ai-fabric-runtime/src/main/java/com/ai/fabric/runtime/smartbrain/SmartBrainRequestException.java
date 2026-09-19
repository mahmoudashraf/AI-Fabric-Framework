package com.ai.fabric.runtime.smartbrain;

public class SmartBrainRequestException extends RuntimeException {

    private final String code;

    public SmartBrainRequestException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
