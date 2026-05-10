package com.ai.fabric.platform.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "platform.cors")
public class PlatformCorsProperties {

    private List<String> allowedOrigins = new ArrayList<>();
    private List<String> allowedOriginPatterns = new ArrayList<>();
    private List<String> allowedHeaders = new ArrayList<>(List.of(
        "Accept",
        "Authorization",
        "Content-Type",
        "Origin",
        "X-Bridge-API-KEY",
        "X-MCP-GATEWAY-API-KEY",
        "X-Platform-API-Key",
        "X-Platform-Public-API-Key",
        "X-Requested-With",
        "X-Shopify-Hmac-Sha256",
        "X-Shopify-Shop-Domain"
    ));
    private boolean allowCredentials = false;

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins != null ? allowedOrigins : new ArrayList<>();
    }

    public List<String> getAllowedOriginPatterns() {
        return allowedOriginPatterns;
    }

    public void setAllowedOriginPatterns(List<String> allowedOriginPatterns) {
        this.allowedOriginPatterns = allowedOriginPatterns != null ? allowedOriginPatterns : new ArrayList<>();
    }

    public boolean isAllowCredentials() {
        return allowCredentials;
    }

    public void setAllowCredentials(boolean allowCredentials) {
        this.allowCredentials = allowCredentials;
    }

    public List<String> getAllowedHeaders() {
        return allowedHeaders;
    }

    public void setAllowedHeaders(List<String> allowedHeaders) {
        this.allowedHeaders = allowedHeaders != null ? allowedHeaders : new ArrayList<>();
    }
}
