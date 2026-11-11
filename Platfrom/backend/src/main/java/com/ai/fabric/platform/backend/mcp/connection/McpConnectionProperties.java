package com.ai.fabric.platform.backend.mcp.connection;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "platform.mcp")
public class McpConnectionProperties {
    private boolean enabled = true;
    private String encryptionKey = "";
    private String encryptionKeyId = "v1";
    private String previousEncryptionKey = "";
    private String previousEncryptionKeyId = "";
    private String callbackUrl = "";
    private String consoleBaseUrl = "";
    private String clientMetadataUrl = "";
    private int maxConnectionsPerDeployment = 32;
    private int maxTools = 500;
    private int maxSnapshotBytes = 8 * 1024 * 1024;
}
