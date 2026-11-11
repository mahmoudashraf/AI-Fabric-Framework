package com.ai.fabric.platform.backend.mcp.connection;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "platform_mcp_oauth_attempts")
public class McpOAuthAttemptEntity {
    @Id private String stateHash;
    @Column(nullable = false) private String connectionId;
    @Column(nullable = false) private long connectionRevision;
    @Column(nullable = false) private String actorId;
    @Column(nullable = false) private String actorRole;
    @Column(nullable = false) private String browserBindingHash;
    @Column(nullable = false, columnDefinition = "TEXT") private String returnPath;
    @Column(columnDefinition = "TEXT") private String encryptedVerifier;
    @Column(nullable = false) private Instant expiresAt;
    private Instant consumedAt;
}
