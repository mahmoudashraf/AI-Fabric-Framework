package com.ai.fabric.runtime.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Runtime-only developer defaults.
 *
 * <p>These defaults exist to make the self-hosted runtime usable out-of-the-box for local development.
 * Production deployments should disable these defaults and provide strict policies.</p>
 */
@Data
@Validated
@ConfigurationProperties(prefix = "ai.fabric.runtime.dev-defaults")
public class RuntimeDevDefaultsProperties {

    /**
     * When true, the runtime provides permissive dev-only implementations for required policy hooks
     * such as {@code EntityAccessPolicy}.
     */
    private boolean enabled = false;
}
