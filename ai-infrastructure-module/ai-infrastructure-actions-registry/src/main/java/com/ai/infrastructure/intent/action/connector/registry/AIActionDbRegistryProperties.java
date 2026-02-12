package com.ai.infrastructure.intent.action.connector.registry;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "ai.actions.db")
public class AIActionDbRegistryProperties {

    /**
     * Enables DB-backed connector action registration and loading.
     */
    private boolean enabled = false;
}

