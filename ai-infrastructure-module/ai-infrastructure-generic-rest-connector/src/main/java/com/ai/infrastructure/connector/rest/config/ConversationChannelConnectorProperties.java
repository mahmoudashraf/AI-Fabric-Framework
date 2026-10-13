package com.ai.infrastructure.connector.rest.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "rest-connector.conversation-channel")
public class ConversationChannelConnectorProperties {
    private boolean enabled;
    private String bindingId;
    private String workerId = "conversation-channel-connector";
    private String runtimeBaseUrl;
    private String runtimeApiKeyHeader = "X-AIFABRIC-INTEGRATION-KEY";
    private String runtimeApiKey;
    private String runtimeCallbackSigningSecret;
    private String providerBaseUrl;
    private String providerApiKeyHeader = "X-LoomAI-Inbox-Key";
    private String providerApiKey;
    private String providerWebhookSigningSecret;
    private int leaseSeconds = 30;
    private int requestTimeoutSeconds = 10;
    private int callbackClockSkewSeconds = 300;
}
