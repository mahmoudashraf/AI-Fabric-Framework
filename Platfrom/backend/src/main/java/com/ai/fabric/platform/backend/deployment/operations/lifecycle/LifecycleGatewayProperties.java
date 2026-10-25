package com.ai.fabric.platform.backend.deployment.operations.lifecycle;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties("platform.deployment-lifecycle")
public class LifecycleGatewayProperties {
    private boolean enabled;
    private String apiKey = "";
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean value) { enabled = value; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String value) { apiKey = value; }
}
