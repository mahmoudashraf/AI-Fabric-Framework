package com.ai.fabric.platform.backend.deployment.operations;

import java.time.Duration;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties("platform.deployment-operations")
public class DeploymentOperationsProperties {
    public enum Mode { DISABLED, OBSERVE, ENFORCE }

    private Mode mode = Mode.DISABLED;
    private String serviceUrl = "";
    private String apiKey = "";
    private Duration timeout = Duration.ofSeconds(1);
    private boolean allowInsecureHttp;
    private String providerUsageMode = "FUNDED";
    private Set<String> teamAuthorizedTargets = Set.of();

    public Mode getMode() { return mode; }
    public void setMode(Mode value) { mode = value; }
    public String getServiceUrl() { return serviceUrl; }
    public void setServiceUrl(String value) { serviceUrl = value; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String value) { apiKey = value; }
    public Duration getTimeout() { return timeout; }
    public void setTimeout(Duration value) { timeout = value; }
    public boolean isAllowInsecureHttp() { return allowInsecureHttp; }
    public void setAllowInsecureHttp(boolean value) { allowInsecureHttp = value; }
    public String getProviderUsageMode() { return providerUsageMode; }
    public void setProviderUsageMode(String value) { providerUsageMode = value; }
    public Set<String> getTeamAuthorizedTargets() { return teamAuthorizedTargets; }
    public void setTeamAuthorizedTargets(Set<String> value) {
        teamAuthorizedTargets = value == null ? Set.of() : Set.copyOf(value);
    }
}
