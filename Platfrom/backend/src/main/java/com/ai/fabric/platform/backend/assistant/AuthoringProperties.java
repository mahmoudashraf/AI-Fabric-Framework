package com.ai.fabric.platform.backend.assistant;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "platform.assistant")
public class AuthoringProperties {
    private boolean enabled;
    private boolean documentFetchEnabled;
    private boolean validationFeedbackEnabled = true;
    private int validationFeedbackMaxCorrections = 2;
    private String runtimeUrl = "";
    private String encryptionKey = "";
    private String apiKeySecretRef = "MANAGED_CONFIGURATION_ASSISTANT_API_KEY";
    private String signingKeySecretRef = "MANAGED_CONFIGURATION_ASSISTANT_SIGNING_KEY";
    private String deploymentId = "loomai-configuration-assistant";
    private String customerId = "loomai-internal";
    private String tenantId = "loomai-platform";
    private String issuer = "loomai-platform-authoring";
    private String audience = "loomai-configuration-authoring";
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean value) { enabled = value; }
    public boolean isDocumentFetchEnabled() { return documentFetchEnabled; }
    public void setDocumentFetchEnabled(boolean value) { documentFetchEnabled = value; }
    public boolean isValidationFeedbackEnabled() { return validationFeedbackEnabled; }
    public void setValidationFeedbackEnabled(boolean value) { validationFeedbackEnabled = value; }
    public int getValidationFeedbackMaxCorrections() { return validationFeedbackMaxCorrections; }
    public void setValidationFeedbackMaxCorrections(int value) { validationFeedbackMaxCorrections = value; }
    public String getRuntimeUrl() { return runtimeUrl; }
    public void setRuntimeUrl(String value) { runtimeUrl = value; }
    public String getEncryptionKey() { return encryptionKey; }
    public void setEncryptionKey(String value) { encryptionKey = value; }
    public String getApiKeySecretRef() { return apiKeySecretRef; }
    public void setApiKeySecretRef(String value) { apiKeySecretRef = value; }
    public String getSigningKeySecretRef() { return signingKeySecretRef; }
    public void setSigningKeySecretRef(String value) { signingKeySecretRef = value; }
    public String getDeploymentId() { return deploymentId; }
    public void setDeploymentId(String value) { deploymentId = value; }
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String value) { customerId = value; }
    public String getTenantId() { return tenantId; }
    public void setTenantId(String value) { tenantId = value; }
    public String getIssuer() { return issuer; }
    public void setIssuer(String value) { issuer = value; }
    public String getAudience() { return audience; }
    public void setAudience(String value) { audience = value; }
}
