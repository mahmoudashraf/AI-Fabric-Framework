package com.ai.fabric.platform.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import java.util.List;

@ConfigurationProperties("platform.auth.customer-onboarding")
public record CustomerOnboardingProperties(@DefaultValue("true") boolean enabled, List<String> templateOfferIds) {
    public CustomerOnboardingProperties {
        templateOfferIds = templateOfferIds == null ? List.of() : List.copyOf(templateOfferIds);
    }
}
