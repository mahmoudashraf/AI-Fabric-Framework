package com.ai.fabric.platform.backend.mcp.connection;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "platform_mcp_connections")
public class McpConnectionEntity {
    @Id private String id;
    @Column(nullable = false) private String deploymentId;
    private String customerId;
    @Column(nullable = false) private String targetProfileId;
    @Column(nullable = false, columnDefinition = "TEXT") private String targetBindingsJson = "[]";
    @Column(nullable = false) private String serverRef;
    @Column(nullable = false) private String name;
    @Column(nullable = false, columnDefinition = "TEXT") private String endpointUrl;
    @Column(nullable = false) private String connectionModel;
    @Column(nullable = false) private String transport;
    @Column(nullable = false, columnDefinition = "TEXT") private String authConfigJson;
    @Column(nullable = false) private long revision;
    @Version private long lockVersion;
    @Column(nullable = false) private String status;
    @Column(nullable = false) private String authStatus;
    @Column(columnDefinition = "TEXT") private String oauthMetadataJson;
    @Column(columnDefinition = "TEXT") private String encryptedTokens;
    private Instant tokenExpiresAt;
    private String lastDiscoveryId;
    @Column(columnDefinition = "TEXT") private String lastVerificationJson;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;
}
