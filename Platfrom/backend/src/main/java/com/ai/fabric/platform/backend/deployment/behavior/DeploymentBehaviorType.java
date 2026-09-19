package com.ai.fabric.platform.backend.deployment.behavior;

import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

public enum DeploymentBehaviorType {
    CONVERSATIONAL,
    AGENTIC_SPECIALIST_TEAM,
    SMART_BRAIN;

    public static DeploymentBehaviorType require(String value) {
        if (!StringUtils.hasText(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "behaviorType is required.");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Unsupported behaviorType: " + value
            );
        }
    }
}
