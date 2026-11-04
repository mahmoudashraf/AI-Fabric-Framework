package com.ai.fabric.platform.backend.documentstorage.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(name = "platform_document_storage_uploads")
@Getter @Setter
public class DocumentStorageUploadEntity {
    @Id private String id;
    @Column(nullable = false) private String connectionId;
    @Column(nullable = false) private String filename;
    @Column(nullable = false) private String objectKey;
    @Column(nullable = false) private String prefix;
    @Column(nullable = false) private long sizeBytes;
    @Column(nullable = false) private String contentType;
    @Column(nullable = false) private String sha256;
    private String etag;
    private String providerVersionId;
    @Column(nullable = false) private String status;
    @Column(nullable = false) private String operationKey;
    private String lastError;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;
    @Version private long revision;
}
