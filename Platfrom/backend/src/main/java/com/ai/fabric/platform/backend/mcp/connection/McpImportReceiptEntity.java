package com.ai.fabric.platform.backend.mcp.connection;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "platform_mcp_import_receipts")
public class McpImportReceiptEntity {
    @Id private String id;
    @Column(nullable = false, updatable = false) private String connectionId;
    @Column(nullable = false, updatable = false) private String snapshotId;
    @Column(nullable = false, updatable = false) private String draftId;
    @Column(nullable = false, updatable = false) private Instant createdAt;
    @Column(nullable = false, updatable = false, columnDefinition = "TEXT") private String receiptJson;
    @Column(nullable = false, updatable = false, columnDefinition = "TEXT") private String beforeActionsJson;
    @Column(nullable = false, updatable = false) private String afterActionsHash;
    private Instant undoneAt;
    private Integer undoDraftRevision;
}
