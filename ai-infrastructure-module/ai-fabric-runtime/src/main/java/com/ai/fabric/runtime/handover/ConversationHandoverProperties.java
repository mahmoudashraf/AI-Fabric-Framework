package com.ai.fabric.runtime.handover;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "loomai.conversation-handover")
public class ConversationHandoverProperties {

    private boolean enabled;
    private String channelBindingId;
    private String encryptionSecret;
    private String callbackSigningSecret;
    private Duration retention = Duration.ofDays(30);
    private Duration intakeRetention = Duration.ofDays(7);
    private Duration queueTimeout = Duration.ofMinutes(15);
    private Duration maintenanceInterval = Duration.ofSeconds(30);
    private Duration aiTurnLease = Duration.ofMinutes(5);
    private Duration callbackClockSkew = Duration.ofMinutes(5);
    private int dispatchMaxAttempts = 5;
    private boolean anonymousAllowed = true;
    private boolean contactIntakeEnabled = true;
    private String intakeSchemaJson;
    private List<String> requiredIntakeFields = new ArrayList<>();
    private int sseCoreThreads = 4;
    private int sseMaxThreads = 32;
    private int sseQueueCapacity = 128;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getChannelBindingId() { return channelBindingId; }
    public void setChannelBindingId(String channelBindingId) { this.channelBindingId = channelBindingId; }
    public String getEncryptionSecret() { return encryptionSecret; }
    public void setEncryptionSecret(String encryptionSecret) { this.encryptionSecret = encryptionSecret; }
    public String getCallbackSigningSecret() { return callbackSigningSecret; }
    public void setCallbackSigningSecret(String callbackSigningSecret) { this.callbackSigningSecret = callbackSigningSecret; }
    public Duration getRetention() { return retention; }
    public void setRetention(Duration retention) { this.retention = retention; }
    public Duration getIntakeRetention() { return intakeRetention; }
    public void setIntakeRetention(Duration intakeRetention) { this.intakeRetention = intakeRetention; }
    public Duration getQueueTimeout() { return queueTimeout; }
    public void setQueueTimeout(Duration queueTimeout) { this.queueTimeout = queueTimeout; }
    public Duration getMaintenanceInterval() { return maintenanceInterval; }
    public void setMaintenanceInterval(Duration maintenanceInterval) { this.maintenanceInterval = maintenanceInterval; }
    public Duration getAiTurnLease() { return aiTurnLease; }
    public void setAiTurnLease(Duration aiTurnLease) { this.aiTurnLease = aiTurnLease; }
    public Duration getCallbackClockSkew() { return callbackClockSkew; }
    public void setCallbackClockSkew(Duration callbackClockSkew) { this.callbackClockSkew = callbackClockSkew; }
    public int getDispatchMaxAttempts() { return dispatchMaxAttempts; }
    public void setDispatchMaxAttempts(int dispatchMaxAttempts) {
        if (dispatchMaxAttempts < 1 || dispatchMaxAttempts > 8) {
            throw new IllegalArgumentException("Conversation handover dispatch attempts must be between 1 and 8.");
        }
        this.dispatchMaxAttempts = dispatchMaxAttempts;
    }
    public boolean isAnonymousAllowed() { return anonymousAllowed; }
    public void setAnonymousAllowed(boolean anonymousAllowed) { this.anonymousAllowed = anonymousAllowed; }
    public boolean isContactIntakeEnabled() { return contactIntakeEnabled; }
    public void setContactIntakeEnabled(boolean contactIntakeEnabled) { this.contactIntakeEnabled = contactIntakeEnabled; }
    public String getIntakeSchemaJson() { return intakeSchemaJson; }
    public void setIntakeSchemaJson(String intakeSchemaJson) { this.intakeSchemaJson = intakeSchemaJson; }
    public List<String> getRequiredIntakeFields() { return requiredIntakeFields; }
    public void setRequiredIntakeFields(List<String> requiredIntakeFields) {
        this.requiredIntakeFields = requiredIntakeFields == null ? new ArrayList<>() : new ArrayList<>(requiredIntakeFields);
    }
    public int getSseCoreThreads() { return sseCoreThreads; }
    public void setSseCoreThreads(int sseCoreThreads) { this.sseCoreThreads = sseCoreThreads; }
    public int getSseMaxThreads() { return sseMaxThreads; }
    public void setSseMaxThreads(int sseMaxThreads) { this.sseMaxThreads = sseMaxThreads; }
    public int getSseQueueCapacity() { return sseQueueCapacity; }
    public void setSseQueueCapacity(int sseQueueCapacity) { this.sseQueueCapacity = sseQueueCapacity; }
}
