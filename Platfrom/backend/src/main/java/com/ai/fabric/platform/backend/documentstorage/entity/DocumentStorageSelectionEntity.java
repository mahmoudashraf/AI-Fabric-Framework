package com.ai.fabric.platform.backend.documentstorage.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(name = "platform_document_storage_selections")
@Getter @Setter
public class DocumentStorageSelectionEntity {
    @Id private String id;
    @Column(nullable = false) private String uploadId;
    @Column(nullable = false) private String deploymentId;
    @Column(nullable = false) private String targetProfileId;
    @Column(nullable = false) private String releaseId;
    @Column(nullable = false) private String datasetId;
    @Column(nullable = false) private boolean publicApproved;
    private String sourceId;
    @Column(nullable = false) private String status;
    private String lastError;
    @Column(nullable = false) private String operationKey;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;
    @Version private long revision;
}
