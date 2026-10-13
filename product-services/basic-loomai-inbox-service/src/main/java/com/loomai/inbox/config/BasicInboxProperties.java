package com.loomai.inbox.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "loomai.basic-inbox")
public class BasicInboxProperties {

    private String bindingId;
    private String tenantId;
    private String deploymentId;
    private String providerApiKey;
    private String connectorCallbackUrl;
    private String callbackSigningSecret;
    private String dataEncryptionSecret;
    private String operators;
    private Duration requestTimeout = Duration.ofSeconds(10);
    private int maxCallbackAttempts = 8;
    private Duration callbackRetryDelay = Duration.ofSeconds(5);
    private Duration retention = Duration.ofDays(30);

    public String getBindingId() { return bindingId; }
    public void setBindingId(String bindingId) { this.bindingId = bindingId; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
    public String getDeploymentId() { return deploymentId; }
    public void setDeploymentId(String deploymentId) { this.deploymentId = deploymentId; }
    public String getProviderApiKey() { return providerApiKey; }
    public void setProviderApiKey(String providerApiKey) { this.providerApiKey = providerApiKey; }
    public String getConnectorCallbackUrl() { return connectorCallbackUrl; }
    public void setConnectorCallbackUrl(String connectorCallbackUrl) { this.connectorCallbackUrl = connectorCallbackUrl; }
    public String getCallbackSigningSecret() { return callbackSigningSecret; }
    public void setCallbackSigningSecret(String callbackSigningSecret) { this.callbackSigningSecret = callbackSigningSecret; }
    public String getDataEncryptionSecret() { return dataEncryptionSecret; }
    public void setDataEncryptionSecret(String dataEncryptionSecret) { this.dataEncryptionSecret = dataEncryptionSecret; }
    public String getOperators() { return operators; }
    public void setOperators(String operators) { this.operators = operators; }
    public Duration getRequestTimeout() { return requestTimeout; }
    public void setRequestTimeout(Duration requestTimeout) { this.requestTimeout = requestTimeout; }
    public int getMaxCallbackAttempts() { return maxCallbackAttempts; }
    public void setMaxCallbackAttempts(int maxCallbackAttempts) { this.maxCallbackAttempts = maxCallbackAttempts; }
    public Duration getCallbackRetryDelay() { return callbackRetryDelay; }
    public void setCallbackRetryDelay(Duration callbackRetryDelay) { this.callbackRetryDelay = callbackRetryDelay; }
    public Duration getRetention() { return retention; }
    public void setRetention(Duration retention) { this.retention = retention; }
}
