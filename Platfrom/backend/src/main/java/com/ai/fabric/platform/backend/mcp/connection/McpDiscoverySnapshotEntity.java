package com.ai.fabric.platform.backend.mcp.connection;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "platform_mcp_discovery_snapshots")
public class McpDiscoverySnapshotEntity {
    @Id private String id;
    @Column(nullable = false, updatable = false) private String connectionId;
    @Column(nullable = false, updatable = false) private long connectionRevision;
    @Column(nullable = false, updatable = false) private Instant observedAt;
    @Column(nullable = false, updatable = false, columnDefinition = "TEXT") private String snapshotJson;
    @Column(nullable = false, updatable = false) private String contentHash;
}
