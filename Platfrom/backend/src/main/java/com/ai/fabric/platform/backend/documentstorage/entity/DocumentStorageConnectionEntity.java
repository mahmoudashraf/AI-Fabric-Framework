package com.ai.fabric.platform.backend.documentstorage.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(name = "platform_document_storage_connections")
@Getter @Setter
public class DocumentStorageConnectionEntity {
    @Id private String id;
    @Column(nullable = false) private String customerId;
    @Column(nullable = false) private String tenantId;
    @Column(nullable = false) private String name;
    @Column(nullable = false) private String mode;
    @Column(nullable = false) private String bucket;
    @Column(nullable = false) private String region;
    @Column(nullable = false) private String rootPrefix;
    @Column(nullable = false) private String status;
    @Column(nullable = false) private String operationKey;
    private String lastError;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;
    @Version private long revision;
}
