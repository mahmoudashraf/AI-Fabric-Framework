package com.ai.fabric.platform.backend.security;

import com.ai.fabric.platform.backend.config.PlatformConsumerRuntimeAssignmentApiProperties;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;

@Component("consumerRuntimeAssignmentAccessEvaluator")
public class ConsumerRuntimeAssignmentAccessEvaluator {

    private final PlatformConsumerRuntimeAssignmentApiProperties properties;

    public ConsumerRuntimeAssignmentAccessEvaluator(PlatformConsumerRuntimeAssignmentApiProperties properties) {
        this.properties = properties;
    }

    public boolean canAccess(Authentication authentication, String consumerId) {
        PlatformPrincipal principal = principal(authentication);
        if (principal == null || principal.role() != PlatformRole.CONSUMER_RUNTIME_ASSIGNMENT_CLIENT) {
            return false;
        }
        String configuredConsumerId = normalize(firstNonBlank(properties.consumerId(), "produs-staging"));
        return properties.enabled()
            && normalize(consumerId).equals(configuredConsumerId)
            && normalize(principal.actorId()).equals("consumer-runtime-assignment:" + configuredConsumerId);
    }

    private PlatformPrincipal principal(Authentication authentication) {
        Object candidate = authentication == null ? null : authentication.getPrincipal();
        return candidate instanceof PlatformPrincipal platformPrincipal ? platformPrincipal : null;
    }

    private String firstNonBlank(String primary, String secondary) {
        if (StringUtils.hasText(primary)) {
            return primary.trim();
        }
        if (StringUtils.hasText(secondary)) {
            return secondary.trim();
        }
        return null;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
