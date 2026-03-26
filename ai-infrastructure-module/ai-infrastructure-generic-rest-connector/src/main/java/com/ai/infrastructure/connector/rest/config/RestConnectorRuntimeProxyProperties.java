package com.ai.infrastructure.connector.rest.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Optional runtime proxy settings.
 *
 * <p>When enabled, the connector exposes a small set of "alias" endpoints that forward to
 * an AI Fabric Runtime instance. This is mainly useful for demo setups where you want a single
 * base URL for actions + indexing (Data Sync push API).</p>
 */
@Data
@ConfigurationProperties(prefix = "rest-connector.runtime-proxy")
public class RestConnectorRuntimeProxyProperties {

    /**
     * Enable proxy endpoints. When false, proxy endpoints are not registered (404).
     */
    private boolean enabled = false;

    /**
     * Base URL of the runtime (must be absolute http(s) URL).
     * Example: https://runtime-prod2.up.railway.app
     */
    private String baseUrl;

    /**
     * Optional API key forwarded to runtime (useful if runtime is behind a gateway).
     */
    private String apiKey;

    /**
     * Header name to send {@link #apiKey} in.
     */
    private String apiKeyHeader = "X-ADMIN-API-KEY";

    /**
     * Proxy request timeout (milliseconds).
     */
    private Integer timeoutMs = 8000;
}

