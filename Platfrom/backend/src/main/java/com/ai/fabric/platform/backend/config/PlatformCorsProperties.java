package com.ai.fabric.platform.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "platform.cors")
public class PlatformCorsProperties {

    private List<String> allowedOrigins = new ArrayList<>();
    private List<String> allowedOriginPatterns = new ArrayList<>();
    private boolean allowCredentials = true;

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
}
