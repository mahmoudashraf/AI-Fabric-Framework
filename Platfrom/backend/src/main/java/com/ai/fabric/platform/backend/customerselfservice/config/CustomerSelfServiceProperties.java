package com.ai.fabric.platform.backend.customerselfservice.config;

import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.TargetIntent;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@ConfigurationProperties(prefix = "platform.customer-self-service")
public record CustomerSelfServiceProperties(Map<TargetIntent, TrustedServer> trustedServers,
                                            long evidenceMaxAgeSeconds) {
    public CustomerSelfServiceProperties {
        trustedServers = trustedServers == null ? Map.of() : Map.copyOf(trustedServers);
    }
    public record TrustedServer(String uuid, String address) { }
}
